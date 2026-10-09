package com.mrcdprm.bank.data;

import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.core.TxType;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Veritabanı satırlarının değişmez (record) karşılıkları ve ResultSet'ten okuma. */
public final class Records {

    private Records() {
    }

    public record StaffUser(long id, String username, String fullName, Role role, boolean active, boolean mustChange,
                            LocalDateTime lockedUntil) {

        public static StaffUser from(ResultSet rs) throws SQLException {
            return new StaffUser(rs.getLong("id"), rs.getString("username"), rs.getString("full_name"),
                    Role.valueOf(rs.getString("role")), rs.getInt("active") == 1, rs.getInt("must_change") == 1,
                    dateTime(rs.getString("locked_until")));
        }
    }

    public record Customer(long id, String tckn, String firstName, String lastName, String phone, String email,
                           boolean mustChange, LocalDateTime lockedUntil, long dailyLimit, LocalDateTime createdAt) {

        public String fullName() {
            return firstName + " " + lastName;
        }

        public static Customer from(ResultSet rs) throws SQLException {
            return new Customer(rs.getLong("id"), rs.getString("tckn"), rs.getString("first_name"),
                    rs.getString("last_name"), rs.getString("phone"), rs.getString("email"),
                    rs.getInt("must_change") == 1, dateTime(rs.getString("locked_until")),
                    rs.getLong("daily_limit"), dateTime(rs.getString("created_at")));
        }
    }

    public record Account(long id, long customerId, String iban, AccountType type, Currency currency, long balance,
                          AccountStatus status, LocalDateTime openedAt, Integer termDays, BigDecimal annualRate,
                          Long principal, LocalDate maturityDate, Long linkedAccountId) {

        public boolean isActive() {
            return status == AccountStatus.ACTIVE;
        }

        public static Account from(ResultSet rs) throws SQLException {
            final String rate = rs.getString("annual_rate");
            final String maturity = rs.getString("maturity_date");
            return new Account(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("iban"),
                    AccountType.valueOf(rs.getString("type")), Currency.valueOf(rs.getString("currency")),
                    rs.getLong("balance"), AccountStatus.valueOf(rs.getString("status")),
                    dateTime(rs.getString("opened_at")), nullableInt(rs, "term_days"),
                    rate == null ? null : new BigDecimal(rate), nullableLong(rs, "principal"),
                    maturity == null ? null : LocalDate.parse(maturity),
                    nullableLong(rs, "linked_account_id"));
        }
    }

    public record Transaction(long id, long accountId, String ref, TxType type, long amount, long balanceAfter,
                              String description, String counterpartyName, String counterpartyIban, Category category,
                              boolean internal, LocalDateTime createdAt) {

        public static Transaction from(ResultSet rs) throws SQLException {
            return new Transaction(rs.getLong("id"), rs.getLong("account_id"), rs.getString("ref"),
                    TxType.valueOf(rs.getString("type")), rs.getLong("amount"), rs.getLong("balance_after"),
                    rs.getString("description"), rs.getString("counterparty_name"),
                    rs.getString("counterparty_iban"), Category.valueOf(rs.getString("category")),
                    rs.getInt("internal") == 1, dateTime(rs.getString("created_at")));
        }
    }

    public record Recipient(long id, long customerId, String name, String iban) {

        public static Recipient from(ResultSet rs) throws SQLException {
            return new Recipient(rs.getLong("id"), rs.getLong("customer_id"), rs.getString("name"), rs.getString("iban"));
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        final long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        final int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** Veritabanına yazılan zaman: saniye her zaman yazılır ("...T09:00:00"), böylece metin sıralaması tarih sırasıdır. */
    public static String ts(LocalDateTime time) {
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    static LocalDateTime dateTime(String text) {
        return text == null ? null : LocalDateTime.parse(text);
    }
}
