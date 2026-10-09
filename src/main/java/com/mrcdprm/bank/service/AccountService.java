package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.core.Interest;
import com.mrcdprm.bank.core.Money;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Customer;
import java.sql.Connection;
import com.mrcdprm.bank.data.Records;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hesaplar ve para hareketleri: hesap açma/dondurma/kapatma, şubede para yatırma/çekme,
 * IBAN ile transfer, döviz alım-satım, vadeli hesap.
 * Her para hareketi tek bir veritabanı işleminde (transaction) yapılır: ya hepsi olur ya hiçbiri.
 */
public final class AccountService {

    /** Bu tutar ve üstündeki (TL karşılığı) başka kişiye transferde şifre ile ek onay istenir: 10.000 TL. */
    public static final long CONFIRM_THRESHOLD = 1_000_000;
    /** Bir müşterinin aynı anda açık tutabileceği en fazla hesap. */
    public static final int MAX_OPEN_ACCOUNTS = 10;

    /** Dekont bilgisi: transfer, döviz ve vadeli işlemlerin özeti. */
    public record Receipt(String ref, LocalDateTime createdAt, TxType kind, String fromIban, String fromName,
                          String toIban, String toName, long amount, Currency currency, long creditedAmount,
                          Currency creditedCurrency, String description) {
    }

    /** IBAN'ın sahibi: müşteriye karşı tarafın adı maskeli gösterilir. */
    public record IbanOwner(String iban, String name, Currency currency, AccountType type, boolean own,
                            boolean active) {
    }

    private final Bank bank;

    AccountService(Bank bank) {
        this.bank = bank;
    }

    // ---------------------------------------------------------------- okuma

    public List<Account> listForCustomer(Session session, long customerId) {
        Guard.staffOrSelf(session, customerId);
        return bank.db().read(c -> {
            final List<Account> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE customer_id = ?"
                    + " ORDER BY status = 'CLOSED', type,"
                    + " CASE currency WHEN 'TRY' THEN 0 WHEN 'USD' THEN 1 ELSE 2 END, id")) {
                ps.setLong(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(Account.from(rs));
                }
            }
            return out;
        });
    }

    public Account get(Session session, long accountId) {
        final Account account = find(accountId);
        Guard.staffOrSelf(session, account.customerId());
        return account;
    }

    Account find(long accountId) {
        return bank.db().read(c -> find(c, accountId));
    }

    private static Account find(Connection c, long accountId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE id = ?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new BankException("notFound");
                return Account.from(rs);
            }
        }
    }

    private static Optional<Account> findByIban(Connection c, String iban) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE iban = ?")) {
            ps.setString(1, Iban.normalize(iban));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(Account.from(rs)) : Optional.empty();
            }
        }
    }

    /** Transfer ekranında IBAN yazılınca alıcıyı göstermek için. Bulunamazsa boş. */
    public Optional<IbanOwner> owner(Session session, String iban) {
        if (session == null || session.mustChangePassword())
            throw new BankException("forbidden");
        if (!Iban.isOurs(iban))
            return Optional.empty();
        return bank.db().read(c -> {
            final Optional<Account> account = findByIban(c, iban);
            if (account.isEmpty())
                return Optional.empty();
            final Account a = account.get();
            final Customer owner = bank.customers().find(a.customerId());
            final boolean own = session.isCustomer() && session.userId() == a.customerId();
            final String name = own || !session.isCustomer() ? owner.fullName() : Validation.maskName(owner.fullName());
            return Optional.of(new IbanOwner(a.iban(), name, a.currency(), a.type(), own, a.isActive()));
        });
    }

    /** Bugün başka kişilere yapılan transferlerin TL karşılığı toplamı (günlük limit için). */
    public long usedToday(Session session, long customerId) {
        Guard.staffOrSelf(session, customerId);
        return bank.db().read(c -> usedToday(c, customerId));
    }

    private long usedToday(Connection c, long customerId) throws SQLException {
        long total = 0;
        try (PreparedStatement ps = c.prepareStatement("SELECT t.amount, a.currency FROM transactions t"
                + " JOIN accounts a ON a.id = t.account_id WHERE a.customer_id = ? AND t.type = 'TRANSFER_OUT'"
                + " AND t.internal = 0 AND t.created_at >= ?")) {
            ps.setLong(1, customerId);
            ps.setString(2, Records.ts(bank.today().atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    total += Rates.toTry(rs.getLong(1), Currency.valueOf(rs.getString(2)));
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- hesap açma / durum

    /** Vadesiz hesap: personel herhangi bir müşteriye, müşteri kendine açabilir. */
    public Account openCurrent(Session session, long customerId, Currency currency) {
        Guard.staffOrSelf(session, customerId);
        final long id = bank.db().transaction(c -> {
            requireCapacity(c, customerId);
            return insertCurrent(c, customerId, currency);
        });
        return find(id);
    }

    /** Yeni vadesiz hesap satırı; IBAN rastgele üretilir, çakışırsa yenisi denenir. */
    long insertCurrent(Connection c, long customerId, Currency currency) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(customer_id, iban, type, currency, opened_at)"
                + " VALUES (?, ?, 'CURRENT', ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, customerId);
            ps.setString(2, uniqueIban(c));
            ps.setString(3, currency.name());
            ps.setString(4, Records.ts(bank.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private String uniqueIban(Connection c) throws SQLException {
        while (true) {
            final String iban = Iban.random(bank.random());
            if (findByIban(c, iban).isEmpty())
                return iban;
        }
    }

    private static void requireCapacity(Connection c, long customerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM accounts WHERE customer_id = ? AND status <> 'CLOSED'")) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= MAX_OPEN_ACCOUNTS)
                    throw new BankException("tooManyAccounts", MAX_OPEN_ACCOUNTS);
            }
        }
    }

    /** Dondurma / tekrar açma (personel). Kapalı hesap geri açılmaz. */
    public void setFrozen(Session session, long accountId, boolean frozen) {
        Guard.staff(session);
        bank.db().transaction(c -> {
            final Account a = find(c, accountId);
            if (a.status() == AccountStatus.CLOSED)
                throw new BankException("accountClosed");
            setStatus(c, accountId, frozen ? AccountStatus.FROZEN : AccountStatus.ACTIVE);
            return null;
        });
    }

    /** Hesap kapatma (yönetici): bakiye sıfır olmalı, müşterinin son açık hesabı kapatılamaz. */
    public void close(Session session, long accountId) {
        Guard.admin(session);
        bank.db().transaction(c -> {
            final Account a = find(c, accountId);
            if (a.status() == AccountStatus.CLOSED)
                throw new BankException("accountClosed");
            if (a.balance() != 0)
                throw new BankException("balanceNotZero");
            if (fundsActiveDeposit(c, a))
                throw new BankException("linkedDeposit");
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE accounts SET status = 'CLOSED', closed_at = ? WHERE id = ?")) {
                ps.setString(1, Records.ts(bank.now()));
                ps.setLong(2, accountId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Bu hesap, açık bir vadeli hesabın vade sonu ödemesinin yapılacağı hesap mı? */
    private static boolean fundsActiveDeposit(Connection c, Account a) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM accounts WHERE linked_account_id = ?"
                + " AND type = 'TIME_DEPOSIT' AND status <> 'CLOSED' LIMIT 1")) {
            ps.setLong(1, a.id());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void setStatus(Connection c, long accountId, AccountStatus status) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setLong(2, accountId);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- şube: nakit

    public Receipt depositCash(Session session, long accountId, long amount, String description) {
        Guard.staff(session);
        return cash(session, accountId, amount, description, TxType.DEPOSIT);
    }

    public Receipt withdrawCash(Session session, long accountId, long amount, String description) {
        Guard.staff(session);
        return cash(session, accountId, amount, description, TxType.WITHDRAWAL);
    }

    private Receipt cash(Session session, long accountId, long amount, String description, TxType type) {
        requireAmount(amount);
        final String text = Validation.description(description);
        final String ref = bank.newRef();
        final LocalDateTime now = bank.now();
        return bank.db().transaction(c -> {
            final Account a = find(c, accountId);
            if (a.type() != AccountType.CURRENT)
                throw new BankException("notCurrentAccount");
            requireActive(a);
            final long balance = type.incoming() ? credit(c, a, amount) : debit(c, a, amount);
            insertTx(c, a.id(), ref, type, amount, balance, text, null, null, Category.CASH, false, session.userId(), now);
            final String owner = bank.customers().find(a.customerId()).fullName();
            return new Receipt(ref, now, type, a.iban(), owner, null, null, amount, a.currency(), amount, a.currency(), text);
        });
    }

    // ---------------------------------------------------------------- müşteri: transfer

    /**
     * IBAN ile transfer. Kurallar: kaynak müşterinin kendi aktif vadesiz hesabı olmalı; alıcı bu bankada,
     * aktif, vadesiz ve aynı para biriminde olmalı. Başka kişiye giden transferler günlük limite sayılır
     * ve 10.000 TL üstünde şifre onayı ister. Kendi hesapları arası transferde limit ve onay yok.
     */
    public Receipt transfer(Session session, long fromAccountId, String toIban, long amount, String description,
                            Category category, String confirmPassword) {
        Guard.customer(session);
        requireAmount(amount);
        if (!Iban.isValid(toIban))
            throw new BankException("invalidIban");
        if (!Iban.isOurs(toIban))
            throw new BankException("externalIban");
        final String text = Validation.description(description);
        final Category cat = category == null || !Category.selectable().contains(category) ? Category.TRANSFER : category;

        // Şifre doğrulaması yavaştır (Argon2); veritabanı kilidi dışında yapılır
        final Account from = find(fromAccountId);
        Guard.ownAccount(session, from);
        final Account preview = bank.db().read(c -> findByIban(c, toIban)).orElseThrow(() -> new BankException("ibanNotFound"));
        final boolean internal = preview.customerId() == from.customerId();
        if (!internal && Rates.toTry(amount, from.currency()) >= CONFIRM_THRESHOLD) {
            if (confirmPassword == null || confirmPassword.isEmpty())
                throw new BankException("confirmationRequired");
            if (!bank.auth().verifyPassword(session, confirmPassword))
                throw new BankException("wrongPassword");
        }

        final String ref = bank.newRef();
        final LocalDateTime now = bank.now();
        return bank.db().transaction(c -> {
            final Account source = find(c, fromAccountId);
            final Account target = findByIban(c, toIban).orElseThrow(() -> new BankException("ibanNotFound"));
            if (source.id() == target.id())
                throw new BankException("sameAccount");
            if (source.type() != AccountType.CURRENT || target.type() != AccountType.CURRENT)
                throw new BankException("notCurrentAccount");
            requireActive(source);
            if (!target.isActive())
                throw new BankException("targetNotActive");
            if (source.currency() != target.currency())
                throw new BankException("currencyMismatch");
            if (!internal) {
                final Customer customer = bank.customers().find(source.customerId());
                final long used = usedToday(c, source.customerId());
                final long wanted = Rates.toTry(amount, source.currency());
                if (used + wanted > customer.dailyLimit())
                    throw new BankException("dailyLimitExceeded", Math.max(0, customer.dailyLimit() - used));
            }
            final String sourceName = bank.customers().find(source.customerId()).fullName();
            final String targetName = bank.customers().find(target.customerId()).fullName();
            final long sourceBalance = debit(c, source, amount);
            final long targetBalance = credit(c, target, amount);
            final Category inCategory = internal ? Category.TRANSFER : cat == Category.SALARY ? Category.SALARY : Category.TRANSFER;
            insertTx(c, source.id(), ref, TxType.TRANSFER_OUT, amount, sourceBalance, text, targetName, target.iban(),
                    internal ? Category.TRANSFER : cat, internal, null, now);
            insertTx(c, target.id(), ref, TxType.TRANSFER_IN, amount, targetBalance, text, sourceName, source.iban(),
                    inCategory, internal, null, now);
            return new Receipt(ref, now, TxType.TRANSFER_OUT, source.iban(), sourceName, target.iban(), targetName,
                    amount, source.currency(), amount, target.currency(), text);
        });
    }

    // ---------------------------------------------------------------- müşteri: döviz

    /** Kendi iki hesabı arasında döviz alım-satımı; tutar kaynak hesabın para biriminde girilir. */
    public Receipt exchange(Session session, long fromAccountId, long toAccountId, long amount) {
        Guard.customer(session);
        requireAmount(amount);
        final String ref = bank.newRef();
        final LocalDateTime now = bank.now();
        return bank.db().transaction(c -> {
            final Account source = find(c, fromAccountId);
            final Account target = find(c, toAccountId);
            Guard.ownAccount(session, source);
            Guard.ownAccount(session, target);
            if (source.type() != AccountType.CURRENT || target.type() != AccountType.CURRENT)
                throw new BankException("notCurrentAccount");
            if (source.currency() == target.currency())
                throw new BankException("sameCurrency");
            requireActive(source);
            requireActive(target);
            final long converted = Rates.convert(amount, source.currency(), target.currency());
            if (converted <= 0)
                throw new BankException("amountTooSmall");
            final String text = source.currency() + " → " + target.currency();
            final String owner = bank.customers().find(source.customerId()).fullName();
            final long sourceBalance = debit(c, source, amount);
            final long targetBalance = credit(c, target, converted);
            insertTx(c, source.id(), ref, TxType.EXCHANGE_OUT, amount, sourceBalance, text, owner, target.iban(),
                    Category.EXCHANGE, true, null, now);
            insertTx(c, target.id(), ref, TxType.EXCHANGE_IN, converted, targetBalance, text, owner, source.iban(),
                    Category.EXCHANGE, true, null, now);
            return new Receipt(ref, now, TxType.EXCHANGE_OUT, source.iban(), owner, target.iban(), owner, amount,
                    source.currency(), converted, target.currency(), text);
        });
    }

    // ---------------------------------------------------------------- müşteri: vadeli hesap

    /** TL vadesiz hesaptan vadeli hesap açar. Para vade sonuna kadar vadeli hesapta kalır. */
    public Account openTimeDeposit(Session session, long fromAccountId, long amount, int termDays) {
        Guard.customer(session);
        requireAmount(amount);
        final Interest.Term term = Interest.term(termDays);
        if (amount < Interest.MIN_PRINCIPAL)
            throw new BankException("belowMinimum", Interest.MIN_PRINCIPAL);
        final String ref = bank.newRef();
        final LocalDateTime now = bank.now();
        final long id = bank.db().transaction(c -> {
            final Account source = find(c, fromAccountId);
            Guard.ownAccount(session, source);
            if (source.type() != AccountType.CURRENT || source.currency() != Currency.TRY)
                throw new BankException("depositNeedsTry");
            requireActive(source);
            requireCapacity(c, source.customerId());
            final long depositId;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(customer_id, iban, type, currency,"
                    + " opened_at, term_days, annual_rate, principal, maturity_date, linked_account_id)"
                    + " VALUES (?, ?, 'TIME_DEPOSIT', 'TRY', ?, ?, ?, ?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, source.customerId());
                ps.setString(2, uniqueIban(c));
                ps.setString(3, Records.ts(now));
                ps.setInt(4, term.days());
                ps.setString(5, term.annualRate().toPlainString());
                ps.setLong(6, amount);
                ps.setString(7, now.toLocalDate().plusDays(term.days()).toString());
                ps.setLong(8, source.id());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    depositId = keys.getLong(1);
                }
            }
            final Account deposit = find(c, depositId);
            final String owner = bank.customers().find(source.customerId()).fullName();
            final long sourceBalance = debit(c, source, amount);
            final long depositBalance = credit(c, deposit, amount);
            insertTx(c, source.id(), ref, TxType.TRANSFER_OUT, amount, sourceBalance, "", owner, deposit.iban(),
                    Category.SAVINGS, true, null, now);
            insertTx(c, deposit.id(), ref, TxType.TRANSFER_IN, amount, depositBalance, "", owner, source.iban(),
                    Category.SAVINGS, true, null, now);
            return depositId;
        });
        return find(id);
    }

    /** Vadeyi bozma: anapara faizsiz olarak bağlı hesaba döner, vadeli hesap kapanır. */
    public Receipt breakTimeDeposit(Session session, long depositId) {
        Guard.customer(session);
        return bank.db().transaction(c -> {
            final Account deposit = find(c, depositId);
            Guard.ownAccount(session, deposit);
            if (deposit.type() != AccountType.TIME_DEPOSIT)
                throw new BankException("notTimeDeposit");
            requireActive(deposit);
            return payOut(c, deposit, 0, bank.newRef(), bank.now());
        });
    }

    /**
     * Vadesi gelen bütün vadeli hesapları öder: faiz eklenir, anapara + faiz bağlı hesaba geçer, vadeli hesap kapanır.
     * Kullanıcıya bağlı değildir; uygulama açılışında ve müşteri girişinde çalışır. Ödenen hesap sayısını döner.
     */
    public int processMaturities() {
        final LocalDate today = bank.today();
        final List<Account> due = bank.db().read(c -> {
            final List<Account> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE type = 'TIME_DEPOSIT'"
                    + " AND status = 'ACTIVE' AND maturity_date <= ?")) {
                ps.setString(1, today.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(Account.from(rs));
                }
            }
            return out;
        });
        int paid = 0;
        for (Account deposit : due) {
            final long interest = Interest.simple(deposit.principal(), deposit.annualRate(), deposit.termDays());
            // Faiz vade gününün saatine yazılır; uygulama günlerce açılmamış olsa da tarih doğru görünür
            final LocalDateTime at = deposit.maturityDate().atTime(9, 0);
            try {
                final boolean done = bank.db().transaction(c -> {
                    final Account fresh = find(c, deposit.id());
                    if (!fresh.isActive())
                        return false; // donmuş hesaba dokunulmaz
                    payOut(c, fresh, interest, bank.newRef(), at);
                    return true;
                });
                if (done)
                    paid++;
            } catch (BankException e) {
                // Ödenecek aktif TL hesabı yok: mevduat açık kalır, hesap açılınca bir sonraki çalışmada ödenir.
                // Bir müşterinin sorunu uygulamanın açılmasını ya da başka müşterilerin girişini engellemez.
            }
        }
        return paid;
    }

    /** Vadeli hesabı kapatıp parayı bağlı hesaba (o kapalıysa müşterinin başka bir aktif TL hesabına) aktarır. */
    private Receipt payOut(Connection c, Account deposit, long interest, String ref, LocalDateTime at) throws SQLException {
        final String owner = bank.customers().find(deposit.customerId()).fullName();
        long balance = deposit.balance();
        if (interest > 0) {
            balance = credit(c, deposit, interest);
            insertTx(c, deposit.id(), ref, TxType.INTEREST, interest, balance, "", null, null, Category.INTEREST,
                    false, null, at);
        }
        final Account target = payoutTarget(c, deposit);
        final long depositBalance = debit(c, deposit, balance);
        final long targetBalance = credit(c, target, balance);
        insertTx(c, deposit.id(), ref, TxType.TRANSFER_OUT, balance, depositBalance, "", owner, target.iban(),
                Category.SAVINGS, true, null, at);
        insertTx(c, target.id(), ref, TxType.TRANSFER_IN, balance, targetBalance, "", owner, deposit.iban(),
                Category.SAVINGS, true, null, at);
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET status = 'CLOSED', closed_at = ? WHERE id = ?")) {
            ps.setString(1, Records.ts(at));
            ps.setLong(2, deposit.id());
            ps.executeUpdate();
        }
        return new Receipt(ref, at, TxType.TRANSFER_OUT, deposit.iban(), owner, target.iban(), owner, balance,
                Currency.TRY, balance, Currency.TRY, "");
    }

    private Account payoutTarget(Connection c, Account deposit) throws SQLException {
        if (deposit.linkedAccountId() != null) {
            final Account linked = find(c, deposit.linkedAccountId());
            if (linked.isActive())
                return linked;
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM accounts WHERE customer_id = ? AND type = 'CURRENT'"
                + " AND currency = 'TRY' AND status = 'ACTIVE' ORDER BY id LIMIT 1")) {
            ps.setLong(1, deposit.customerId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return Account.from(rs);
            }
        }
        throw new BankException("noPayoutAccount");
    }

    // ---------------------------------------------------------------- yardımcılar

    private static void requireAmount(long amount) {
        if (amount <= 0 || amount > Money.MAX_AMOUNT)
            throw new BankException("invalidAmount");
    }

    private static void requireActive(Account a) {
        if (a.status() == AccountStatus.FROZEN)
            throw new BankException("accountFrozen");
        if (a.status() == AccountStatus.CLOSED)
            throw new BankException("accountClosed");
    }

    /**
     * Bakiyeden düşer. Koşul UPDATE'in içinde: bakiye yetmiyorsa satır değişmez (0 satır) ve işlem durur.
     * Önce okuyup sonra yazmak yerine böyle yapmak, arada bakiyenin değişme ihtimalini ortadan kaldırır.
     */
    private static long debit(Connection c, Account a, long amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = balance - ?"
                + " WHERE id = ? AND status = 'ACTIVE' AND balance >= ?")) {
            ps.setLong(1, amount);
            ps.setLong(2, a.id());
            ps.setLong(3, amount);
            if (ps.executeUpdate() == 0)
                throw new BankException("insufficientFunds");
        }
        return balanceOf(c, a.id());
    }

    private static long credit(Connection c, Account a, long amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = balance + ?"
                + " WHERE id = ? AND status = 'ACTIVE'")) {
            ps.setLong(1, amount);
            ps.setLong(2, a.id());
            if (ps.executeUpdate() == 0)
                throw new BankException("targetNotActive");
        }
        return balanceOf(c, a.id());
    }

    private static long balanceOf(Connection c, long accountId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM accounts WHERE id = ?")) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    static void insertTx(Connection c, long accountId, String ref, TxType type, long amount, long balanceAfter,
                         String description, String counterpartyName, String counterpartyIban, Category category,
                         boolean internal, Long staffId, LocalDateTime at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO transactions(account_id, ref, type, amount,"
                + " balance_after, description, counterparty_name, counterparty_iban, category, internal, staff_id,"
                + " created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, accountId);
            ps.setString(2, ref);
            ps.setString(3, type.name());
            ps.setLong(4, amount);
            ps.setLong(5, balanceAfter);
            ps.setString(6, description);
            ps.setString(7, counterpartyName);
            ps.setString(8, counterpartyIban);
            ps.setString(9, category.name());
            ps.setInt(10, internal ? 1 : 0);
            ps.setObject(11, staffId);
            ps.setString(12, Records.ts(at));
            ps.executeUpdate();
        }
    }
}
