package com.mrcdprm.bank.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * SQLite bağlantısı ve şema. Masaüstü uygulaması tek kullanıcılı olduğu için tek bağlantı paylaşılır;
 * aynı anda iki iş parçacığı yazmasın diye bütün işlemler bu nesne üzerinden sırayla (synchronized) yapılır.
 */
public final class Database implements AutoCloseable {

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private static final int SCHEMA_VERSION = 1;

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS meta (
            key   TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS staff (
            id              INTEGER PRIMARY KEY,
            username        TEXT NOT NULL UNIQUE COLLATE NOCASE,
            full_name       TEXT NOT NULL,
            role            TEXT NOT NULL CHECK (role IN ('ADMIN', 'TELLER')),
            password_hash   TEXT NOT NULL,
            must_change     INTEGER NOT NULL DEFAULT 0,
            active          INTEGER NOT NULL DEFAULT 1,
            failed_attempts INTEGER NOT NULL DEFAULT 0,
            locked_until    TEXT,
            created_at      TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS customers (
            id              INTEGER PRIMARY KEY,
            tckn            TEXT NOT NULL UNIQUE,
            first_name      TEXT NOT NULL,
            last_name       TEXT NOT NULL,
            phone           TEXT NOT NULL,
            email           TEXT NOT NULL,
            password_hash   TEXT NOT NULL,
            must_change     INTEGER NOT NULL DEFAULT 1,
            failed_attempts INTEGER NOT NULL DEFAULT 0,
            locked_until    TEXT,
            daily_limit     INTEGER NOT NULL CHECK (daily_limit > 0),
            created_at      TEXT NOT NULL
        )""",
        """
        CREATE TABLE IF NOT EXISTS accounts (
            id                INTEGER PRIMARY KEY,
            customer_id       INTEGER NOT NULL REFERENCES customers(id),
            iban              TEXT NOT NULL UNIQUE,
            type              TEXT NOT NULL CHECK (type IN ('CURRENT', 'TIME_DEPOSIT')),
            currency          TEXT NOT NULL CHECK (currency IN ('TRY', 'USD', 'EUR')),
            balance           INTEGER NOT NULL DEFAULT 0 CHECK (balance >= 0),
            status            TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
            opened_at         TEXT NOT NULL,
            closed_at         TEXT,
            term_days         INTEGER,
            annual_rate       TEXT,
            principal         INTEGER,
            maturity_date     TEXT,
            linked_account_id INTEGER REFERENCES accounts(id)
        )""",
        "CREATE INDEX IF NOT EXISTS accounts_customer ON accounts(customer_id)",
        """
        CREATE TABLE IF NOT EXISTS transactions (
            id                INTEGER PRIMARY KEY,
            account_id        INTEGER NOT NULL REFERENCES accounts(id),
            ref               TEXT NOT NULL,
            type              TEXT NOT NULL,
            amount            INTEGER NOT NULL CHECK (amount > 0),
            balance_after     INTEGER NOT NULL CHECK (balance_after >= 0),
            description       TEXT NOT NULL DEFAULT '',
            counterparty_name TEXT,
            counterparty_iban TEXT,
            category          TEXT NOT NULL,
            internal          INTEGER NOT NULL DEFAULT 0,
            staff_id          INTEGER REFERENCES staff(id),
            created_at        TEXT NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS transactions_account_date ON transactions(account_id, created_at)",
        "CREATE INDEX IF NOT EXISTS transactions_ref ON transactions(ref)",
        """
        CREATE TABLE IF NOT EXISTS recipients (
            id          INTEGER PRIMARY KEY,
            customer_id INTEGER NOT NULL REFERENCES customers(id),
            name        TEXT NOT NULL,
            iban        TEXT NOT NULL,
            UNIQUE (customer_id, iban)
        )""",
    };

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Dosyadaki veritabanını açar; klasör ve tablolar yoksa oluşturur. */
    public static Database open(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return connect("jdbc:sqlite:" + file.toAbsolutePath());
    }

    /** Testler için bellekte geçici veritabanı. */
    public static Database inMemory() {
        return connect("jdbc:sqlite::memory:");
    }

    private static Database connect(String url) {
        try {
            final Connection connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
                for (String sql : SCHEMA)
                    st.execute(sql);
                st.execute("INSERT OR IGNORE INTO meta(key, value) VALUES ('schema_version', '" + SCHEMA_VERSION + "')");
            }
            return new Database(connection);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /** Okuma: otomatik commit açık, tek ifade. */
    public synchronized <T> T read(Work<T> work) {
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    /**
     * Yazma işlemi: içindeki bütün ifadeler ya hep birlikte kaydedilir ya da hiçbiri (atomik).
     * Arada bir istisna olursa geri alınır; para bir hesaptan çıkıp diğerine girmeden kalamaz.
     */
    public synchronized <T> T transaction(Work<T> work) {
        try {
            connection.setAutoCommit(false);
            try {
                final T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // kapanırken hata önemli değil
        }
    }
}
