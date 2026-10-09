package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.PasswordHasher;
import com.mrcdprm.bank.core.PasswordPolicy;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.core.TcKimlik;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Giriş, kilitlenme ve şifre değiştirme. 5 hatalı denemede hesap 15 dakika kilitlenir.
 * Hata mesajı kullanıcı adının mı şifrenin mi yanlış olduğunu söylemez.
 */
public final class AuthService {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;

    /** Olmayan kullanıcıda da aynı süre harcansın diye karşılaştırılan sahte özet (zamanlama farkı ele vermesin). */
    private static volatile String dummyHash;

    private final Bank bank;

    AuthService(Bank bank) {
        this.bank = bank;
    }

    /** İlk açılışta yönetici hesabı var mı? */
    public boolean hasStaff() {
        return bank.db().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM staff")) {
                return rs.next() && rs.getInt(1) > 0;
            }
        });
    }

    /** Sadece hiç personel yokken çalışır: ilk yöneticiyi oluşturur ve oturum açar. */
    public Session createFirstAdmin(String username, String fullName, String password) {
        final String user = Validation.username(username).orElseThrow(() -> new BankException("invalidUsername"));
        final String name = Validation.name(fullName).orElseThrow(() -> new BankException("invalidName"));
        requireStrong(password, user);
        final String hash = PasswordHasher.hash(password);
        final long id = bank.db().transaction(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM staff")) {
                if (rs.next() && rs.getInt(1) > 0)
                    throw new BankException("forbidden");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO staff(username, full_name, role, password_hash, created_at) VALUES (?, ?, 'ADMIN', ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user);
                ps.setString(2, name);
                ps.setString(3, hash);
                ps.setString(4, Records.ts(bank.now()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
        return new Session(Role.ADMIN, id, user, name, false);
    }

    public Session loginStaff(String username, String password) {
        final String user = username == null ? "" : username.strip();
        final Row row = bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, username, full_name, role, password_hash, must_change, active, failed_attempts, locked_until"
                            + " FROM staff WHERE username = ?")) {
                ps.setString(1, user);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return null;
                    return new Row(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"),
                            Role.valueOf(rs.getString("role")), rs.getString("password_hash"),
                            rs.getInt("must_change") == 1, rs.getInt("active") == 1, rs.getInt("failed_attempts"),
                            rs.getString("locked_until"));
                }
            }
        });
        return check(row, password, "staff");
    }

    public Session loginCustomer(String tckn, String password) {
        final String id = tckn == null ? "" : tckn.strip();
        if (!TcKimlik.isValid(id)) {
            fakeVerify(password);
            throw new BankException("invalidCredentials");
        }
        final Row row = bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tckn, first_name, last_name, password_hash, must_change, failed_attempts, locked_until"
                            + " FROM customers WHERE tckn = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return null;
                    return new Row(rs.getLong("id"), rs.getString("tckn"),
                            rs.getString("first_name") + " " + rs.getString("last_name"), Role.CUSTOMER,
                            rs.getString("password_hash"), rs.getInt("must_change") == 1, true,
                            rs.getInt("failed_attempts"), rs.getString("locked_until"));
                }
            }
        });
        final Session session = check(row, password, "customers");
        bank.accounts().processMaturities(); // vadesi gelen mevduatlar girişte hesaba geçer
        return session;
    }

    /** Ortak kontrol: kilit, şifre, deneme sayacı. */
    private Session check(Row row, String password, String table) {
        if (row == null) {
            fakeVerify(password);
            throw new BankException("invalidCredentials");
        }
        final LocalDateTime now = bank.now();
        if (row.lockedUntil != null && LocalDateTime.parse(row.lockedUntil).isAfter(now))
            throw new BankException("locked", LocalDateTime.parse(row.lockedUntil));
        if (!PasswordHasher.verify(password, row.hash)) {
            final int attempts = row.failedAttempts + 1;
            final boolean lock = attempts >= MAX_ATTEMPTS;
            bank.db().transaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE " + table + " SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
                    ps.setInt(1, lock ? 0 : attempts);
                    ps.setString(2, lock ? Records.ts(now.plusMinutes(LOCK_MINUTES)) : null);
                    ps.setLong(3, row.id);
                    return ps.executeUpdate();
                }
            });
            if (lock)
                throw new BankException("locked", now.plusMinutes(LOCK_MINUTES));
            throw new BankException("invalidCredentials");
        }
        if (!row.active)
            throw new BankException("inactive");
        bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + table + " SET failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
                ps.setLong(1, row.id);
                return ps.executeUpdate();
            }
        });
        return new Session(row.role, row.id, row.login, row.name, row.mustChange);
    }

    /**
     * Şifre değiştirme. Geçici şifreyle giren kullanıcı da bunu kullanır (mustChangePassword açıkken başka
     * hiçbir servis çalışmaz). Yeni oturum nesnesi döner.
     */
    public Session changePassword(Session session, String current, String next) {
        if (session == null)
            throw new BankException("forbidden");
        final String table = session.isCustomer() ? "customers" : "staff";
        if (!verifyPassword(session, current))
            throw new BankException("wrongPassword");
        if (next.equals(current))
            throw new BankException("samePassword");
        requireStrong(next, session.login());
        final String hash = PasswordHasher.hash(next);
        bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + table + " SET password_hash = ?, must_change = 0 WHERE id = ?")) {
                ps.setString(1, hash);
                ps.setLong(2, session.userId());
                return ps.executeUpdate();
            }
        });
        return session.withPasswordChanged();
    }

    /** Büyük transferlerde ek onay ve şifre değiştirmede kullanılan doğrulama. */
    public boolean verifyPassword(Session session, String password) {
        final String table = session.isCustomer() ? "customers" : "staff";
        final String hash = bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM " + table + " WHERE id = ?")) {
                ps.setLong(1, session.userId());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
        return hash != null && PasswordHasher.verify(password, hash);
    }

    static void requireStrong(String password, String login) {
        final List<String> problems = PasswordPolicy.violations(password, login);
        if (!problems.isEmpty())
            throw new BankException("weakPassword", String.join(",", problems));
    }

    private static void fakeVerify(String password) {
        if (dummyHash == null)
            dummyHash = PasswordHasher.hash("dummy-password-for-timing");
        PasswordHasher.verify(password == null ? "" : password, dummyHash);
    }

    private record Row(long id, String login, String name, Role role, String hash, boolean mustChange, boolean active,
                       int failedAttempts, String lockedUntil) {
    }
}
