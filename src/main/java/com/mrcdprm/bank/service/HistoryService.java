package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Transaction;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hesap hareketleri: listeleme, arama, kategori, dekont ve harcama analizi. */
public final class HistoryService {

    /** Hareket ve ait olduğu hesabın para birimi (ekranda doğru simge için). */
    public record Movement(Transaction tx, Currency currency, String accountIban) {
    }

    /** Ay bazında gelir ve gider (TL karşılığı, kuruş). */
    public record MonthTotal(YearMonth month, long income, long expense) {
    }

    public static final int MAX_ROWS = 2000;

    private final Bank bank;

    HistoryService(Bank bank) {
        this.bank = bank;
    }

    /**
     * Bir hesabın hareketleri, yeniden eskiye. Tarihler dahil; metin açıklama ve karşı taraf adında aranır.
     * Personel her hesabı, müşteri sadece kendi hesabını görür.
     */
    public List<Transaction> list(Session session, long accountId, LocalDate from, LocalDate to, String text) {
        final Account account = bank.accounts().get(session, accountId);
        final String q = text == null ? "" : text.strip();
        return bank.db().read(c -> {
            final StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE account_id = ?");
            if (from != null)
                sql.append(" AND created_at >= ?");
            if (to != null)
                sql.append(" AND created_at < ?");
            if (!q.isEmpty())
                sql.append(" AND (description LIKE ? ESCAPE '\\' OR counterparty_name LIKE ? ESCAPE '\\' OR ref = ?)");
            sql.append(" ORDER BY created_at DESC, id DESC LIMIT ").append(MAX_ROWS);
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i = 1;
                ps.setLong(i++, account.id());
                if (from != null)
                    ps.setString(i++, Records.ts(from.atStartOfDay()));
                if (to != null)
                    ps.setString(i++, Records.ts(to.plusDays(1).atStartOfDay()));
                if (!q.isEmpty()) {
                    ps.setString(i++, Validation.likePattern(q));
                    ps.setString(i++, Validation.likePattern(q));
                    ps.setString(i, q.toUpperCase(java.util.Locale.ROOT));
                }
                final List<Transaction> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(Transaction.from(rs));
                }
                return out;
            }
        });
    }

    /** Müşterinin bütün hesaplarındaki son hareketler (ana sayfa için). */
    public List<Movement> recent(Session session, long customerId, int limit) {
        Guard.staffOrSelf(session, customerId);
        return bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT t.*, a.currency AS acc_currency, a.iban AS acc_iban"
                    + " FROM transactions t JOIN accounts a ON a.id = t.account_id WHERE a.customer_id = ?"
                    + " ORDER BY t.created_at DESC, t.id DESC LIMIT ?")) {
                ps.setLong(1, customerId);
                ps.setInt(2, Math.min(limit, MAX_ROWS));
                final List<Movement> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new Movement(Transaction.from(rs), Currency.valueOf(rs.getString("acc_currency")),
                                rs.getString("acc_iban")));
                }
                return out;
            }
        });
    }

    /** Müşteri kendi hareketinin kategorisini değiştirir (kendi hesapları arası hareketler hariç). */
    public void setCategory(Session session, long transactionId, Category category) {
        Guard.customer(session);
        if (!Category.selectable().contains(category))
            throw new BankException("forbidden");
        final Transaction tx = find(transactionId);
        bank.accounts().get(session, tx.accountId()); // sahiplik kontrolü
        if (tx.internal())
            throw new BankException("internalCategory");
        bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE transactions SET category = ? WHERE id = ?")) {
                ps.setString(1, category.name());
                ps.setLong(2, transactionId);
                return ps.executeUpdate();
            }
        });
    }

    /** Bir hareketin dekontu (aynı numaralı karşı hareketle birlikte). */
    public AccountService.Receipt receipt(Session session, long transactionId) {
        final Transaction tx = find(transactionId);
        final Account account = bank.accounts().get(session, tx.accountId());
        final String owner = bank.customers().find(account.customerId()).fullName();
        // Nakit ve faiz hareketlerinin karşı tarafı yok: dekont sadece bu hesabı gösterir
        if (tx.type() == TxType.DEPOSIT || tx.type() == TxType.WITHDRAWAL || tx.type() == TxType.INTEREST) {
            final boolean in = tx.type().incoming();
            return new AccountService.Receipt(tx.ref(), tx.createdAt(), tx.type(), in ? null : account.iban(),
                    in ? null : owner, in ? account.iban() : null, in ? owner : null, tx.amount(), account.currency(),
                    tx.amount(), account.currency(), tx.description());
        }
        final Transaction other = bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM transactions WHERE ref = ? AND id <> ?"
                    + " AND type <> 'INTEREST' ORDER BY id LIMIT 1")) {
                ps.setString(1, tx.ref());
                ps.setLong(2, tx.id());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Transaction.from(rs) : null;
                }
            }
        });
        final Currency otherCurrency = other == null ? account.currency() : bank.accounts().find(other.accountId()).currency();
        final boolean out = !tx.type().incoming();
        if (out) {
            return new AccountService.Receipt(tx.ref(), tx.createdAt(), tx.type(), account.iban(), owner,
                    tx.counterpartyIban(), tx.counterpartyName(), tx.amount(), account.currency(),
                    other == null ? tx.amount() : other.amount(), otherCurrency, tx.description());
        }
        return new AccountService.Receipt(tx.ref(), tx.createdAt(), tx.type(), tx.counterpartyIban(),
                tx.counterpartyName(), account.iban(), owner, other == null ? tx.amount() : other.amount(),
                otherCurrency, tx.amount(), account.currency(), tx.description());
    }

    private Transaction find(long transactionId) {
        return bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM transactions WHERE id = ?")) {
                ps.setLong(1, transactionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        throw new BankException("notFound");
                    return Transaction.from(rs);
                }
            }
        });
    }

    /**
     * Son n ayın gelir/gider toplamları. Kendi hesapları arası hareketler (tasarruf, döviz) sayılmaz;
     * yoksa vadeli hesaba para koymak "gider" görünürdü.
     */
    public List<MonthTotal> monthly(Session session, long customerId, int months) {
        Guard.staffOrSelf(session, customerId);
        final YearMonth last = YearMonth.from(bank.today());
        final YearMonth first = last.minusMonths(months - 1L);
        final Map<YearMonth, long[]> totals = new LinkedHashMap<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1))
            totals.put(m, new long[2]);
        for (Movement m : external(customerId, first.atDay(1), last.atEndOfMonth())) {
            final long[] t = totals.get(YearMonth.from(m.tx().createdAt()));
            if (t != null)
                t[m.tx().type().incoming() ? 0 : 1] += Rates.toTry(m.tx().amount(), m.currency());
        }
        final List<MonthTotal> out = new ArrayList<>();
        totals.forEach((month, t) -> out.add(new MonthTotal(month, t[0], t[1])));
        return out;
    }

    /** Bir aydaki giderlerin kategorilere dağılımı (TL karşılığı), büyükten küçüğe. */
    public Map<Category, Long> spendingByCategory(Session session, long customerId, YearMonth month) {
        Guard.staffOrSelf(session, customerId);
        final Map<Category, Long> totals = new EnumMap<>(Category.class);
        for (Movement m : external(customerId, month.atDay(1), month.atEndOfMonth())) {
            if (!m.tx().type().incoming() && m.tx().type() != TxType.EXCHANGE_OUT)
                totals.merge(m.tx().category(), Rates.toTry(m.tx().amount(), m.currency()), Long::sum);
        }
        final Map<Category, Long> sorted = new LinkedHashMap<>();
        totals.entrySet().stream()
                .sorted(Map.Entry.<Category, Long>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private List<Movement> external(long customerId, LocalDate from, LocalDate to) {
        return bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT t.*, a.currency AS acc_currency, a.iban AS acc_iban"
                    + " FROM transactions t JOIN accounts a ON a.id = t.account_id WHERE a.customer_id = ?"
                    + " AND t.internal = 0 AND t.created_at >= ? AND t.created_at < ?")) {
                ps.setLong(1, customerId);
                ps.setString(2, Records.ts(from.atStartOfDay()));
                ps.setString(3, Records.ts(to.plusDays(1).atStartOfDay()));
                final List<Movement> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(new Movement(Transaction.from(rs), Currency.valueOf(rs.getString("acc_currency")),
                                rs.getString("acc_iban")));
                }
                return out;
            }
        });
    }
}
