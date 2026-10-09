package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.PasswordHasher;
import com.mrcdprm.bank.core.PasswordPolicy;
import com.mrcdprm.bank.core.TcKimlik;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.data.Records;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Müşteri kaydı, arama, iletişim bilgileri, şifre sıfırlama ve günlük transfer limiti. */
public final class CustomerService {

    /** Varsayılan günlük transfer limiti: 50.000 TL. */
    public static final long DEFAULT_DAILY_LIMIT = 5_000_000;
    public static final long MIN_DAILY_LIMIT = 100_000;
    /** Bankanın izin verdiği en yüksek günlük limit: 250.000 TL. */
    public static final long MAX_DAILY_LIMIT = 25_000_000;

    /** Yeni müşteri ve ona verilecek geçici şifre. */
    public record Created(Customer customer, String temporaryPassword) {
    }

    private final Bank bank;

    CustomerService(Bank bank) {
        this.bank = bank;
    }

    /** Şube ekranının üstündeki özet: müşteri ve açık hesap sayısı, toplam mevduat (TL karşılığı), bugünkü işlemler. */
    public record Stats(int customers, int openAccounts, long totalDepositsTry, int transactionsToday) {
    }

    public Stats stats(Session session) {
        Guard.staff(session);
        return bank.db().read(c -> {
            int customers = 0;
            int accounts = 0;
            long total = 0;
            int today = 0;
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM customers")) {
                if (rs.next())
                    customers = rs.getInt(1);
            }
            try (ResultSet rs = c.createStatement().executeQuery(
                    "SELECT currency, COUNT(*), SUM(balance) FROM accounts WHERE status <> 'CLOSED' GROUP BY currency")) {
                while (rs.next()) {
                    accounts += rs.getInt(2);
                    total += com.mrcdprm.bank.core.Rates.toTry(rs.getLong(3), Currency.valueOf(rs.getString(1)));
                }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(DISTINCT ref) FROM transactions WHERE created_at >= ?")) {
                ps.setString(1, Records.ts(bank.today().atStartOfDay()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        today = rs.getInt(1);
                }
            }
            return new Stats(customers, accounts, total, today);
        });
    }

    /** Ad, soyad, T.C. kimlik no ya da telefona göre arama (boş arama en son eklenenleri getirir). */
    public List<Customer> search(Session session, String query) {
        Guard.staff(session);
        final String q = query == null ? "" : query.strip();
        return bank.db().read(c -> {
            final List<Customer> out = new ArrayList<>();
            final String sql = q.isEmpty()
                    ? "SELECT * FROM customers ORDER BY id DESC LIMIT 500"
                    : "SELECT * FROM customers WHERE (first_name || ' ' || last_name) LIKE ? ESCAPE '\\'"
                            + " OR tckn LIKE ? ESCAPE '\\' OR phone LIKE ? ESCAPE '\\'"
                            + " ORDER BY first_name, last_name LIMIT 500";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (!q.isEmpty()) {
                    final String like = Validation.likePattern(q);
                    ps.setString(1, like);
                    ps.setString(2, like);
                    // telefon "0532 123..." diye aranabilir; kayıtta boşluksuz ve baştaki 0 olmadan durur
                    final String digits = q.replaceAll("\\D", "").replaceFirst("^0", "");
                    ps.setString(3, digits.length() < 3 ? "-" : Validation.likePattern(digits));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(Customer.from(rs));
                }
            }
            return out;
        });
    }

    public Customer get(Session session, long customerId) {
        Guard.staffOrSelf(session, customerId);
        return find(customerId);
    }

    Customer find(long customerId) {
        return bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM customers WHERE id = ?")) {
                ps.setLong(1, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        throw new BankException("notFound");
                    return Customer.from(rs);
                }
            }
        });
    }

    /** Müşteri kaydı: bilgiler doğrulanır, bir TL vadesiz hesap açılır, geçici şifre üretilir. */
    public Created create(Session session, String tckn, String firstName, String lastName, String phone, String email) {
        Guard.staff(session);
        final String id = tckn == null ? "" : tckn.strip();
        if (!TcKimlik.isValid(id))
            throw new BankException("invalidTckn");
        final String first = Validation.name(firstName).orElseThrow(() -> new BankException("invalidName"));
        final String last = Validation.name(lastName).orElseThrow(() -> new BankException("invalidName"));
        final String tel = Validation.phone(phone).orElseThrow(() -> new BankException("invalidPhone"));
        final String mail = Validation.email(email).orElseThrow(() -> new BankException("invalidEmail"));
        final String temporary = PasswordPolicy.temporary(bank.random());
        final String hash = PasswordHasher.hash(temporary);

        final long customerId = bank.db().transaction(c -> {
            try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM customers WHERE tckn = ?")) {
                check.setString(1, id);
                if (check.executeQuery().next())
                    throw new BankException("tcknTaken");
            }
            final long newId;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO customers(tckn, first_name, last_name, phone, email, password_hash, must_change,"
                            + " daily_limit, created_at) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, id);
                ps.setString(2, first);
                ps.setString(3, last);
                ps.setString(4, tel);
                ps.setString(5, mail);
                ps.setString(6, hash);
                ps.setLong(7, DEFAULT_DAILY_LIMIT);
                ps.setString(8, Records.ts(bank.now()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    newId = keys.getLong(1);
                }
            }
            bank.accounts().insertCurrent(c, newId, Currency.TRY);
            return newId;
        });
        return new Created(find(customerId), temporary);
    }

    public void updateContact(Session session, long customerId, String phone, String email) {
        Guard.staffOrSelf(session, customerId);
        final String tel = Validation.phone(phone).orElseThrow(() -> new BankException("invalidPhone"));
        final String mail = Validation.email(email).orElseThrow(() -> new BankException("invalidEmail"));
        bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET phone = ?, email = ? WHERE id = ?")) {
                ps.setString(1, tel);
                ps.setString(2, mail);
                ps.setLong(3, customerId);
                return ps.executeUpdate();
            }
        });
    }

    /** Şubede şifre sıfırlama: geçici şifre, kilit açılır, ilk girişte değiştirilmesi zorunlu. */
    public String resetPassword(Session session, long customerId) {
        Guard.staff(session);
        final String temporary = PasswordPolicy.temporary(bank.random());
        final String hash = PasswordHasher.hash(temporary);
        final int changed = bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET password_hash = ?, must_change = 1,"
                    + " failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
                ps.setString(1, hash);
                ps.setLong(2, customerId);
                return ps.executeUpdate();
            }
        });
        if (changed == 0)
            throw new BankException("notFound");
        return temporary;
    }

    /** Günlük transfer limiti: müşteri kendisi için, personel herkes için; 1.000 - 250.000 TL arası. */
    public void setDailyLimit(Session session, long customerId, long limit) {
        Guard.staffOrSelf(session, customerId);
        if (limit < MIN_DAILY_LIMIT || limit > MAX_DAILY_LIMIT)
            throw new BankException("limitOutOfRange");
        bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE customers SET daily_limit = ? WHERE id = ?")) {
                ps.setLong(1, limit);
                ps.setLong(2, customerId);
                return ps.executeUpdate();
            }
        });
    }
}
