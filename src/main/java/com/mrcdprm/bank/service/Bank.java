package com.mrcdprm.bank.service;

import com.mrcdprm.bank.data.Database;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Bütün servisleri bir araya getirir. Saat (Clock) dışarıdan verilir; testler sabit bir saatle
 * "bugün" ve "şimdi"yi kontrol eder, böylece vade ve günlük limit testleri her gün aynı sonucu verir.
 */
public final class Bank {

    private final Database db;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private final AuthService auth;
    private final StaffService staff;
    private final CustomerService customers;
    private final AccountService accounts;
    private final HistoryService history;
    private final RecipientService recipients;

    public Bank(Database db, Clock clock) {
        this.db = db;
        this.clock = clock;
        this.auth = new AuthService(this);
        this.staff = new StaffService(this);
        this.customers = new CustomerService(this);
        this.accounts = new AccountService(this);
        this.history = new HistoryService(this);
        this.recipients = new RecipientService(this);
    }

    public AuthService auth() {
        return auth;
    }

    public StaffService staff() {
        return staff;
    }

    public CustomerService customers() {
        return customers;
    }

    public AccountService accounts() {
        return accounts;
    }

    public HistoryService history() {
        return history;
    }

    public RecipientService recipients() {
        return recipients;
    }

    Database db() {
        return db;
    }

    SecureRandom random() {
        return random;
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 12 karakterlik işlem/dekont numarası (karışabilecek harfler yok). */
    String newRef() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        final StringBuilder out = new StringBuilder(12);
        for (int i = 0; i < 12; i++)
            out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return out.toString();
    }
}
