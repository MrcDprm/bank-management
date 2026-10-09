package com.mrcdprm.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Interest;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.core.TcKimlik;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Transaction;
import com.mrcdprm.bank.service.AccountService;
import com.mrcdprm.bank.service.AuthService;
import com.mrcdprm.bank.service.SampleData;
import com.mrcdprm.bank.service.Session;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ServiceTest {

    private static final String AYSE = TcKimlik.withCheckDigits("100000001");
    private static final String MEHMET = TcKimlik.withCheckDigits("200000002");

    private static String code(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(BankException.class, action).code();
    }

    // ------------------------------------------------------------ giriş

    @Test
    void firstAdminOnlyOnce() {
        final TestBank t = new TestBank();
        assertTrue(t.bank.auth().hasStaff());
        assertEquals("forbidden", code(() -> t.bank.auth().createFirstAdmin("ikinci", "İkinci Yönetici", TestBank.PASSWORD)));
    }

    @Test
    void weakPasswordRejected() {
        final TestBank t = new TestBank();
        final BankException e = assertThrows(BankException.class,
                () -> t.bank.auth().changePassword(t.admin, TestBank.PASSWORD, "kisa"));
        assertEquals("weakPassword", e.code());
    }

    @Test
    void wrongPasswordLocksAfterFiveAttempts() {
        final TestBank t = new TestBank();
        for (int i = 0; i < AuthService.MAX_ATTEMPTS - 1; i++)
            assertEquals("invalidCredentials", code(() -> t.bank.auth().loginStaff("vezne", "Yanlis.Sifre1")));
        assertEquals("locked", code(() -> t.bank.auth().loginStaff("vezne", "Yanlis.Sifre1")));
        // doğru şifre bile kilit süresince işe yaramaz
        assertEquals("locked", code(() -> t.bank.auth().loginStaff("vezne", TestBank.PASSWORD)));
        t.clock.advance(Duration.ofMinutes(AuthService.LOCK_MINUTES + 1));
        assertEquals(Role.TELLER, t.bank.auth().loginStaff("vezne", TestBank.PASSWORD).role());
    }

    @Test
    void unknownUserGetsSameMessage() {
        final TestBank t = new TestBank();
        assertEquals("invalidCredentials", code(() -> t.bank.auth().loginStaff("yok", TestBank.PASSWORD)));
        assertEquals("invalidCredentials", code(() -> t.bank.auth().loginCustomer(AYSE, TestBank.PASSWORD)));
        assertEquals("invalidCredentials", code(() -> t.bank.auth().loginCustomer("' OR 1=1 --", "x")));
    }

    @Test
    void temporaryPasswordMustBeChangedFirst() {
        final TestBank t = new TestBank();
        final var created = t.bank.customers().create(t.teller, AYSE, "Ayşe", "Yılmaz", "05321234567", "a@example.com");
        final Session first = t.bank.auth().loginCustomer(AYSE, created.temporaryPassword());
        assertTrue(first.mustChangePassword());
        // şifre değiştirilmeden hiçbir işlem yapılamaz
        assertEquals("forbidden", code(() -> t.bank.accounts().listForCustomer(first, first.userId())));
        final Session changed = t.bank.auth().changePassword(first, created.temporaryPassword(), TestBank.PASSWORD);
        assertFalse(changed.mustChangePassword());
        assertEquals(1, t.bank.accounts().listForCustomer(changed, changed.userId()).size());
    }

    @Test
    void resetPasswordUnlocksCustomer() {
        final TestBank t = new TestBank();
        t.customer(AYSE, "Ayşe", "Yılmaz");
        for (int i = 0; i < AuthService.MAX_ATTEMPTS; i++)
            assertThrows(BankException.class, () -> t.bank.auth().loginCustomer(AYSE, "Yanlis.Sifre1"));
        final long id = t.bank.customers().search(t.teller, AYSE).get(0).id();
        final String temp = t.bank.customers().resetPassword(t.teller, id);
        assertTrue(t.bank.auth().loginCustomer(AYSE, temp).mustChangePassword());
    }

    // ------------------------------------------------------------ yetki

    @Test
    void customerCannotSeeOrMoveOthersMoney() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account mehmetAccount = t.tryAccount(mehmet);
        t.bank.accounts().depositCash(t.teller, mehmetAccount.id(), 100_000, "");

        assertEquals("forbidden", code(() -> t.bank.accounts().listForCustomer(ayse, mehmet.userId())));
        assertEquals("forbidden", code(() -> t.bank.accounts().get(ayse, mehmetAccount.id())));
        assertEquals("forbidden", code(() -> t.bank.history().list(ayse, mehmetAccount.id(), null, null, "")));
        assertEquals("forbidden", code(() -> t.bank.accounts().transfer(ayse, mehmetAccount.id(),
                t.tryAccount(ayse).iban(), 1_000, "", null, null)));
        assertEquals(100_000, t.balance(mehmetAccount.id()));
    }

    @Test
    void roleChecksInServiceLayer() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account account = t.tryAccount(ayse);
        assertEquals("forbidden", code(() -> t.bank.accounts().depositCash(ayse, account.id(), 1_000, "")));
        assertEquals("forbidden", code(() -> t.bank.customers().search(ayse, "")));
        assertEquals("forbidden", code(() -> t.bank.staff().list(t.teller)));
        assertEquals("forbidden", code(() -> t.bank.accounts().close(t.teller, account.id())));
        assertEquals("forbidden", code(() -> t.bank.accounts().transfer(t.teller, account.id(), account.iban(), 1, "", null, null)));
        assertEquals("cannotChangeSelf", code(() -> t.bank.staff().setActive(t.admin, t.admin.userId(), false)));
        assertEquals("cannotChangeSelf", code(() -> t.bank.staff().setRole(t.admin, t.admin.userId(), Role.TELLER)));
    }

    @Test
    void inactiveStaffCannotLogIn() {
        final TestBank t = new TestBank();
        t.bank.staff().setActive(t.admin, t.teller.userId(), false);
        assertEquals("inactive", code(() -> t.bank.auth().loginStaff("vezne", TestBank.PASSWORD)));
    }

    // ------------------------------------------------------------ müşteri ve hesap

    @Test
    void customerValidation() {
        final TestBank t = new TestBank();
        assertEquals("invalidTckn", code(() -> t.bank.customers().create(t.teller, "12345678901", "Ayşe", "Yılmaz",
                "05321234567", "a@example.com")));
        assertEquals("invalidPhone", code(() -> t.bank.customers().create(t.teller, AYSE, "Ayşe", "Yılmaz",
                "123", "a@example.com")));
        t.customer(AYSE, "Ayşe", "Yılmaz");
        assertEquals("tcknTaken", code(() -> t.bank.customers().create(t.teller, AYSE, "Ayşe", "Yılmaz",
                "05321234567", "a@example.com")));
    }

    @Test
    void searchEscapesWildcards() {
        final TestBank t = new TestBank();
        t.customer(AYSE, "Ayşe", "Yılmaz");
        assertEquals(1, t.bank.customers().search(t.teller, "ayşe").size());
        assertEquals(0, t.bank.customers().search(t.teller, "%").size());
        assertEquals(0, t.bank.customers().search(t.teller, "_").size());
        assertEquals(1, t.bank.customers().search(t.teller, "0532 123").size());
    }

    @Test
    void cashDepositAndWithdrawal() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account a = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, a.id(), 50_000, "Maaş");
        t.bank.accounts().withdrawCash(t.teller, a.id(), 20_000, "");
        assertEquals(30_000, t.balance(a.id()));
        assertEquals("insufficientFunds", code(() -> t.bank.accounts().withdrawCash(t.teller, a.id(), 30_001, "")));
        assertEquals(30_000, t.balance(a.id()));
        final List<Transaction> history = t.bank.history().list(ayse, a.id(), null, null, "");
        assertEquals(List.of(TxType.WITHDRAWAL, TxType.DEPOSIT), history.stream().map(Transaction::type).toList());
        assertEquals(30_000, history.get(0).balanceAfter());
    }

    @Test
    void frozenAndClosedAccounts() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account a = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, a.id(), 10_000, "");
        t.bank.accounts().setFrozen(t.teller, a.id(), true);
        assertEquals("accountFrozen", code(() -> t.bank.accounts().withdrawCash(t.teller, a.id(), 1_000, "")));
        t.bank.accounts().setFrozen(t.teller, a.id(), false);
        assertEquals("balanceNotZero", code(() -> t.bank.accounts().close(t.admin, a.id())));
        t.bank.accounts().withdrawCash(t.teller, a.id(), 10_000, "");
        t.bank.accounts().close(t.admin, a.id());
        assertEquals(AccountStatus.CLOSED, t.bank.accounts().get(t.admin, a.id()).status());
        assertEquals("accountClosed", code(() -> t.bank.accounts().setFrozen(t.teller, a.id(), false)));
    }

    // ------------------------------------------------------------ transfer

    @Test
    void transferMovesMoneyAtomically() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account from = t.tryAccount(ayse);
        final Account to = t.tryAccount(mehmet);
        t.bank.accounts().depositCash(t.teller, from.id(), 100_000, "");

        final var receipt = t.bank.accounts().transfer(ayse, from.id(), to.iban(), 25_050, "Kira", Category.RENT, null);
        assertEquals(74_950, t.balance(from.id()));
        assertEquals(25_050, t.balance(to.id()));
        assertEquals("Mehmet Kaya", receipt.toName());
        // iki hareket aynı dekont numarasını taşır
        final Transaction out = t.bank.history().list(ayse, from.id(), null, null, "").get(0);
        final Transaction in = t.bank.history().list(mehmet, to.id(), null, null, "").get(0);
        assertEquals(out.ref(), in.ref());
        assertEquals(Category.RENT, out.category());

        assertEquals("insufficientFunds", code(() -> t.bank.accounts().transfer(ayse, from.id(), to.iban(), 74_951, "", null, null)));
        assertEquals(74_950, t.balance(from.id()));
        assertEquals(25_050, t.balance(to.id()));
    }

    @Test
    void transferRules() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account from = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, from.id(), 100_000, "");
        final Account usd = t.bank.accounts().openCurrent(mehmet, mehmet.userId(), Currency.USD);

        assertEquals("invalidIban", code(() -> t.bank.accounts().transfer(ayse, from.id(), "TR00", 1_000, "", null, null)));
        assertEquals("externalIban", code(() -> t.bank.accounts().transfer(ayse, from.id(),
                "TR330006100519786457841326", 1_000, "", null, null)));
        assertEquals("sameAccount", code(() -> t.bank.accounts().transfer(ayse, from.id(), from.iban(), 1_000, "", null, null)));
        assertEquals("currencyMismatch", code(() -> t.bank.accounts().transfer(ayse, from.id(), usd.iban(), 1_000, "", null, null)));
        assertEquals("invalidAmount", code(() -> t.bank.accounts().transfer(ayse, from.id(), t.tryAccount(mehmet).iban(), 0, "", null, null)));
        assertEquals("invalidAmount", code(() -> t.bank.accounts().transfer(ayse, from.id(), t.tryAccount(mehmet).iban(), -5, "", null, null)));
        t.bank.accounts().setFrozen(t.teller, t.tryAccount(mehmet).id(), true);
        assertEquals("targetNotActive", code(() -> t.bank.accounts().transfer(ayse, from.id(), t.tryAccount(mehmet).iban(), 1_000, "", null, null)));
        assertEquals(100_000, t.balance(from.id()));
    }

    @Test
    void largeTransferNeedsPasswordAndDailyLimitApplies() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account from = t.tryAccount(ayse);
        final String to = t.tryAccount(mehmet).iban();
        t.bank.accounts().depositCash(t.teller, from.id(), 20_000_000, "");
        t.bank.customers().setDailyLimit(ayse, ayse.userId(), 3_000_000); // 30.000 TL

        final long big = AccountService.CONFIRM_THRESHOLD;
        assertEquals("confirmationRequired", code(() -> t.bank.accounts().transfer(ayse, from.id(), to, big, "", null, null)));
        assertEquals("wrongPassword", code(() -> t.bank.accounts().transfer(ayse, from.id(), to, big, "", null, "Yanlis.1234")));
        t.bank.accounts().transfer(ayse, from.id(), to, big, "", null, TestBank.PASSWORD);
        t.bank.accounts().transfer(ayse, from.id(), to, big, "", null, TestBank.PASSWORD);
        assertEquals(2_000_000, t.bank.accounts().usedToday(ayse, ayse.userId()));
        final BankException limit = assertThrows(BankException.class,
                () -> t.bank.accounts().transfer(ayse, from.id(), to, 1_000_001, "", null, TestBank.PASSWORD));
        assertEquals("dailyLimitExceeded", limit.code());
        assertEquals(1_000_000L, limit.args()[0]);

        // kendi hesapları arası transfer limite sayılmaz
        final Account second = t.bank.accounts().openCurrent(ayse, ayse.userId(), Currency.TRY);
        t.bank.accounts().transfer(ayse, from.id(), second.iban(), 5_000_000, "", null, null);
        // ertesi gün limit yenilenir
        t.clock.advance(Duration.ofDays(1));
        t.bank.accounts().transfer(ayse, from.id(), to, 1_000_001, "", null, TestBank.PASSWORD);
        assertEquals("limitOutOfRange", code(() -> t.bank.customers().setDailyLimit(ayse, ayse.userId(), 999_999_999)));
    }

    @Test
    void parallelTransfersNeverOverdraw() throws Exception {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account from = t.tryAccount(ayse);
        final String to = t.tryAccount(mehmet).iban();
        t.bank.accounts().depositCash(t.teller, from.id(), 100_000, "");
        final ExecutorService pool = Executors.newFixedThreadPool(8);
        final List<Future<?>> results = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++)
            results.add(pool.submit(() -> t.bank.accounts().transfer(ayse, from.id(), to, 10_000, "", null, null)));
        int ok = 0;
        for (Future<?> f : results) {
            try {
                f.get();
                ok++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertEquals("insufficientFunds", ((BankException) e.getCause()).code());
            }
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(10, ok);
        assertEquals(0, t.balance(from.id()));
        assertEquals(100_000, t.balance(t.tryAccount(mehmet).id()));
    }

    @Test
    void ownerPreviewMasksOtherCustomers() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        assertEquals("Me**** Ka**", t.bank.accounts().owner(ayse, t.tryAccount(mehmet).iban()).orElseThrow().name());
        assertEquals("Ayşe Yılmaz", t.bank.accounts().owner(ayse, t.tryAccount(ayse).iban()).orElseThrow().name());
        assertEquals("Mehmet Kaya", t.bank.accounts().owner(t.teller, t.tryAccount(mehmet).iban()).orElseThrow().name());
        assertTrue(t.bank.accounts().owner(ayse, "TR330006100519786457841326").isEmpty());
    }

    // ------------------------------------------------------------ döviz ve vadeli

    @Test
    void exchangeBetweenOwnAccounts() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account tl = t.tryAccount(ayse);
        final Account usd = t.bank.accounts().openCurrent(ayse, ayse.userId(), Currency.USD);
        t.bank.accounts().depositCash(t.teller, tl.id(), 1_000_000, "");
        final var receipt = t.bank.accounts().exchange(ayse, tl.id(), usd.id(), 418_500); // 4.185 TL -> 100 USD
        assertEquals(10_000, receipt.creditedAmount());
        assertEquals(581_500, t.balance(tl.id()));
        assertEquals(10_000, t.balance(usd.id()));
        assertEquals("sameCurrency", code(() -> t.bank.accounts().exchange(ayse, tl.id(), tl.id(), 100)));
        assertEquals("amountTooSmall", code(() -> t.bank.accounts().exchange(ayse, tl.id(), usd.id(), 1)));
    }

    @Test
    void timeDepositPaysInterestAtMaturity() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account tl = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, tl.id(), 20_000_000, "");
        assertEquals("belowMinimum", code(() -> t.bank.accounts().openTimeDeposit(ayse, tl.id(), 50_000, 32)));
        assertEquals("invalidTerm", code(() -> t.bank.accounts().openTimeDeposit(ayse, tl.id(), 10_000_000, 33)));
        final Account deposit = t.bank.accounts().openTimeDeposit(ayse, tl.id(), 10_000_000, 32);
        assertEquals(AccountType.TIME_DEPOSIT, deposit.type());
        assertEquals(10_000_000, deposit.balance());
        assertEquals(10_000_000, t.balance(tl.id()));
        assertEquals("notCurrentAccount", code(() -> t.bank.accounts().transfer(ayse, deposit.id(), tl.iban(), 1_000, "", null, null)));

        assertEquals(0, t.bank.accounts().processMaturities()); // vade gelmedi
        t.clock.advance(Duration.ofDays(32));
        assertEquals(1, t.bank.accounts().processMaturities());
        final long interest = Interest.simple(10_000_000, new java.math.BigDecimal("0.4000"), 32);
        assertEquals(20_000_000 + interest, t.balance(tl.id()));
        assertEquals(AccountStatus.CLOSED, t.bank.accounts().get(ayse, deposit.id()).status());
        assertEquals(0, t.bank.accounts().processMaturities()); // ikinci kez ödenmez
    }

    @Test
    void maturityWaitsWhenPayoutAccountIsFrozen() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account tl = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, tl.id(), 10_000_000, "");
        final Account deposit = t.bank.accounts().openTimeDeposit(ayse, tl.id(), 10_000_000, 32);
        // ödeme hesabı açık vadeli hesaba bağlıyken kapatılamaz
        assertEquals("linkedDeposit", code(() -> t.bank.accounts().close(t.admin, tl.id())));

        t.bank.accounts().setFrozen(t.teller, tl.id(), true);
        t.clock.advance(Duration.ofDays(40));
        // ödenecek aktif hesap yok: hata fırlatmaz, mevduat açık kalır (uygulama açılışı ve girişler bozulmaz)
        assertEquals(0, t.bank.accounts().processMaturities());
        assertEquals(AccountStatus.ACTIVE, t.bank.accounts().get(t.admin, deposit.id()).status());
        t.bank.auth().loginCustomer(AYSE, TestBank.PASSWORD);

        t.bank.accounts().setFrozen(t.teller, tl.id(), false);
        assertEquals(1, t.bank.accounts().processMaturities());
        final Transaction interest = t.bank.history().list(t.admin, deposit.id(), null, null, "").stream()
                .filter(x -> x.type() == TxType.INTEREST).findFirst().orElseThrow();
        // faiz dekontu sadece faiz tutarını gösterir
        assertEquals(interest.amount(), t.bank.history().receipt(ayse, interest.id()).amount());
    }

    @Test
    void breakingDepositReturnsPrincipalOnly() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account tl = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, tl.id(), 10_000_000, "");
        final Account deposit = t.bank.accounts().openTimeDeposit(ayse, tl.id(), 10_000_000, 92);
        t.clock.advance(Duration.ofDays(40));
        t.bank.accounts().breakTimeDeposit(ayse, deposit.id());
        assertEquals(10_000_000, t.balance(tl.id()));
        assertEquals(AccountStatus.CLOSED, t.bank.accounts().get(ayse, deposit.id()).status());
    }

    // ------------------------------------------------------------ geçmiş, alıcılar, analiz

    @Test
    void historyFiltersAndCategories() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account from = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, from.id(), 1_000_000, "");
        t.bank.accounts().transfer(ayse, from.id(), t.tryAccount(mehmet).iban(), 10_000, "Ekim kirası", Category.RENT, null);
        t.clock.advance(Duration.ofDays(40));
        t.bank.accounts().transfer(ayse, from.id(), t.tryAccount(mehmet).iban(), 20_000, "Market", Category.GROCERIES, null);

        assertEquals(1, t.bank.history().list(ayse, from.id(), null, null, "kira").size());
        assertEquals(1, t.bank.history().list(ayse, from.id(), t.bank.today(), t.bank.today(), "").size());
        assertEquals(3, t.bank.history().list(ayse, from.id(), null, null, "").size());

        final Transaction market = t.bank.history().list(ayse, from.id(), null, null, "Market").get(0);
        t.bank.history().setCategory(ayse, market.id(), Category.SHOPPING);
        final Map<Category, Long> spending = t.bank.history().spendingByCategory(ayse, ayse.userId(), YearMonth.from(t.bank.today()));
        assertEquals(Map.of(Category.SHOPPING, 20_000L), spending);
        assertEquals("forbidden", code(() -> t.bank.history().setCategory(mehmet, market.id(), Category.OTHER)));
    }

    @Test
    void monthlyTotalsSkipOwnTransfers() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Account tl = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, tl.id(), 20_000_000, "");
        t.bank.accounts().openTimeDeposit(ayse, tl.id(), 10_000_000, 32);
        final var months = t.bank.history().monthly(ayse, ayse.userId(), 6);
        assertEquals(6, months.size());
        final var current = months.get(5);
        assertEquals(20_000_000, current.income());
        assertEquals(0, current.expense());
    }

    @Test
    void recipients() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final String iban = t.tryAccount(mehmet).iban();
        t.bank.recipients().add(ayse, "Mehmet", iban);
        assertEquals("recipientExists", code(() -> t.bank.recipients().add(ayse, "Mehmet 2", iban)));
        assertTrue(t.bank.recipients().contains(ayse, iban));
        final long id = t.bank.recipients().list(ayse).get(0).id();
        t.bank.recipients().delete(mehmet, id); // başkasının kaydı silinmez
        assertEquals(1, t.bank.recipients().list(ayse).size());
        t.bank.recipients().delete(ayse, id);
        assertEquals(0, t.bank.recipients().list(ayse).size());
    }

    @Test
    void receiptShowsBothSides() {
        final TestBank t = new TestBank();
        final Session ayse = t.customer(AYSE, "Ayşe", "Yılmaz");
        final Session mehmet = t.customer(MEHMET, "Mehmet", "Kaya");
        final Account from = t.tryAccount(ayse);
        t.bank.accounts().depositCash(t.teller, from.id(), 100_000, "");
        t.bank.accounts().transfer(ayse, from.id(), t.tryAccount(mehmet).iban(), 5_000, "Hediye", null, null);
        final Transaction in = t.bank.history().list(mehmet, t.tryAccount(mehmet).id(), null, null, "").get(0);
        final var receipt = t.bank.history().receipt(mehmet, in.id());
        assertEquals("Ayşe Yılmaz", receipt.fromName());
        assertEquals("Mehmet Kaya", receipt.toName());
        assertEquals(5_000, receipt.amount());
    }

    // ------------------------------------------------------------ örnek veri

    @Test
    void sampleDataIsConsistent() throws Exception {
        final TestBank t = new TestBank();
        SampleData.load(t.bank);
        assertTrue(SampleData.isLoaded(t.db));
        assertFalse(SampleData.canLoad(t.db));
        // her hesabın bakiyesi = gelenler - gidenler ve son hareketin bakiyesi
        t.db.read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT a.id, a.balance,"
                    + " COALESCE(SUM(CASE WHEN t.type IN ('DEPOSIT','TRANSFER_IN','EXCHANGE_IN','INTEREST') THEN t.amount ELSE -t.amount END), 0)"
                    + " FROM accounts a LEFT JOIN transactions t ON t.account_id = a.id GROUP BY a.id")) {
                int accounts = 0;
                while (rs.next()) {
                    assertEquals(rs.getLong(2), rs.getLong(3), "hesap " + rs.getLong(1));
                    accounts++;
                }
                assertTrue(accounts >= 60);
            }
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM transactions")) {
                rs.next();
                assertTrue(rs.getInt(1) > 5_000, "hareket sayısı " + rs.getInt(1));
            }
            return null;
        });
        // demo hesaplarla giriş yapılabilir
        assertEquals(Role.TELLER, t.bank.auth().loginStaff(SampleData.TELLER_USERNAME, SampleData.TELLER_PASSWORD).role());
        final Session demo = t.bank.auth().loginCustomer(SampleData.CUSTOMER_TCKN, SampleData.CUSTOMER_PASSWORD);
        assertFalse(demo.mustChangePassword());
        final List<Account> accounts = t.bank.accounts().listForCustomer(demo, demo.userId());
        assertTrue(accounts.stream().anyMatch(a -> a.type() == AccountType.TIME_DEPOSIT && a.isActive()));
        assertTrue(accounts.stream().anyMatch(a -> a.currency() == Currency.USD));
        assertFalse(t.bank.recipients().list(demo).isEmpty());
        // örnek veri tekrar yüklenmez
        SampleData.load(t.bank);
    }
}
