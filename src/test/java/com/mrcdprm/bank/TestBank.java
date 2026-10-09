package com.mrcdprm.bank;

import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.data.Database;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.service.Bank;
import com.mrcdprm.bank.service.CustomerService;
import com.mrcdprm.bank.service.Session;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Testler için bellekte veritabanı, ileri alınabilen saat ve hazır kullanıcılar. */
final class TestBank {

    static final String PASSWORD = "Test.Sifre2026";

    /** Elle ileri alınabilen saat: vade ve günlük limit testleri için. */
    static final class MovableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneId.of("Europe/Istanbul");

        MovableClock(LocalDateTime start) {
            instant = start.atZone(zone).toInstant();
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    final MovableClock clock = new MovableClock(LocalDateTime.of(2026, 10, 9, 12, 0));
    final Database db = Database.inMemory();
    final Bank bank = new Bank(db, clock);
    final Session admin = bank.auth().createFirstAdmin("yonetici", "Yönetici Kişi", PASSWORD);
    final Session teller;

    TestBank() {
        final String temp = bank.staff().create(admin, "vezne", "Vezne Kişi", Role.TELLER);
        teller = bank.auth().changePassword(bank.auth().loginStaff("vezne", temp), temp, PASSWORD);
    }

    /** Müşteri açar, geçici şifreyi değiştirir ve oturum döner. */
    Session customer(String tckn, String first, String last) {
        final CustomerService.Created created = bank.customers().create(teller, tckn, first, last, "05321234567",
                "test@example.com");
        final Session first0 = bank.auth().loginCustomer(tckn, created.temporaryPassword());
        return bank.auth().changePassword(first0, created.temporaryPassword(), PASSWORD);
    }

    Account tryAccount(Session customer) {
        return bank.accounts().listForCustomer(customer, customer.userId()).stream()
                .filter(a -> a.currency() == Currency.TRY).findFirst().orElseThrow();
    }

    long balance(long accountId) {
        return bank.accounts().get(admin, accountId).balance();
    }
}
