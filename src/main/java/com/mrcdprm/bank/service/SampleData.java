package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.core.Interest;
import com.mrcdprm.bank.core.PasswordHasher;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.core.TcKimlik;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.data.Database;
import com.mrcdprm.bank.data.Records;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * İlk açılışta isteğe bağlı örnek veri: 60 müşteri, hesaplar ve son 6 ayın hareketleri.
 * Sabit tohumlu rastgele sayı üreteci kullanılır; aynı tarihte her yüklemede aynı veri çıkar.
 * Hareketler zaman sırasıyla uygulanır, bakiye hiçbir anda eksiye düşmez (yetmeyen harcama atlanır).
 * Bütün yükleme tek işlemdir: yarıda hata olursa hiçbir şey yazılmaz.
 */
public final class SampleData {

    public static final String TELLER_USERNAME = "veznedar";
    public static final String TELLER_PASSWORD = "Sube.Demo2026";
    public static final String CUSTOMER_TCKN = TcKimlik.withCheckDigits("123456789");
    public static final String CUSTOMER_PASSWORD = "Musteri.2026";

    private static final int CUSTOMERS = 60;
    private static final int MONTHS = 6;

    private static final String[] FIRST_NAMES = {"Ahmet", "Mehmet", "Mustafa", "Ali", "Hüseyin", "Emre", "Burak",
        "Can", "Murat", "Okan", "Serkan", "Kerem", "Barış", "Onur", "Tolga", "Ayşe", "Fatma", "Zeynep", "Elif",
        "Merve", "Selin", "Ece", "Deniz", "Gizem", "Büşra", "Esra", "Derya", "Cansu", "İrem", "Özge"};
    private static final String[] LAST_NAMES = {"Yılmaz", "Kaya", "Demir", "Şahin", "Çelik", "Yıldız", "Yıldırım",
        "Öztürk", "Aydın", "Özdemir", "Arslan", "Doğan", "Kılıç", "Aslan", "Çetin", "Kara", "Koç", "Kurt",
        "Özkan", "Şimşek", "Polat", "Korkmaz", "Erdoğan", "Güneş", "Aksoy"};
    private static final String[] EMPLOYERS = {"Atlas Yazılım A.Ş.", "Marmara Lojistik A.Ş.", "Ege Tekstil Ltd.",
        "Anadolu Sağlık Grubu", "Boğaz Danışmanlık", "Kuzey İnşaat A.Ş.", "Toros Gıda San.", "Liman Enerji A.Ş."};

    /** Harcama kategorisi, karşı taraf adları, açıklama ve tutar aralığı (TL). */
    private record Spend(Category category, String[] merchants, String description, int minTl, int maxTl) {
    }

    private static final Spend[] SPENDING = {
        new Spend(Category.GROCERIES, new String[] {"Bereket Market", "Güneş Süpermarket", "Mahalle Manavı",
            "Taze Fırın"}, "Market alışverişi", 180, 2600),
        new Spend(Category.DINING, new String[] {"Lezzet Durağı", "Köşe Kahve", "Ocakbaşı Kebap", "Deniz Pide"},
            "Restoran", 150, 1400),
        new Spend(Category.TRANSPORT, new String[] {"Yol Akaryakıt", "Şehir Kart Dolum", "Taksi"}, "Ulaşım", 100, 2200),
        new Spend(Category.SHOPPING, new String[] {"Moda Giyim", "Tekno Mağaza", "Ev Dekor", "Kitap Kırtasiye"},
            "Alışveriş", 300, 6500),
        new Spend(Category.ENTERTAINMENT, new String[] {"Sinema Salonu", "Konser Bileti", "Dijital Abonelik"},
            "Eğlence", 120, 1800),
        new Spend(Category.HEALTH, new String[] {"Şifa Eczanesi", "Gülüş Diş Kliniği"}, "Sağlık", 200, 4500),
    };

    private static final Spend[] BILLS = {
        new Spend(Category.BILLS, new String[] {"Şehir Elektrik Dağıtım"}, "Elektrik faturası", 450, 2200),
        new Spend(Category.BILLS, new String[] {"Su ve Kanalizasyon İdaresi"}, "Su faturası", 200, 700),
        new Spend(Category.BILLS, new String[] {"Doğalgaz Dağıtım"}, "Doğalgaz faturası", 300, 3500),
        new Spend(Category.BILLS, new String[] {"FiberNet İnternet"}, "İnternet faturası", 450, 650),
    };

    private enum Kind { EXTERNAL_IN, EXTERNAL_OUT, CASH_IN, CASH_OUT, P2P, EXCHANGE, DEPOSIT_OPEN }

    /** Uygulanacak tek bir para hareketi. */
    private record Event(LocalDateTime at, Kind kind, Acc from, Acc to, long amount, Category category,
                         String description, String counterparty) {
    }

    /** Üretim sırasında hesabın bellekteki hâli. */
    private static final class Acc {
        long id;
        String iban;
        Currency currency;
        Cust owner;
        long balance;
    }

    private static final class Cust {
        long id;
        String name;
        Acc tryAccount;
        Acc usd;
        Acc eur;
        long salary;
        LocalDate since;
    }

    private SampleData() {
    }

    /** Örnek veri yüklendi mi? (Giriş ekranındaki demo düğmeleri için.) */
    public static boolean isLoaded(Database db) {
        return db.read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT 1 FROM meta WHERE key = 'sample_data'")) {
                return rs.next();
            }
        });
    }

    /** Hiç müşteri yokken yüklenebilir; gerçek verinin üstüne yazılmaz. */
    public static boolean canLoad(Database db) {
        return db.read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM customers")) {
                return rs.next() && rs.getInt(1) == 0;
            }
        });
    }

    public static void load(Bank bank) {
        if (!canLoad(bank.db()))
            return;
        final Random random = new Random(2026);
        final String tellerHash = PasswordHasher.hash(TELLER_PASSWORD);
        final String demoHash = PasswordHasher.hash(CUSTOMER_PASSWORD);
        // Diğer müşterilerin şifresi kimsenin bilmediği rastgele bir değer; şubeden sıfırlanarak girilebilir
        final String lockedHash = PasswordHasher.hash(java.util.UUID.randomUUID() + "Aa1");
        bank.db().transaction(c -> {
            new Generator(bank, c, random, tellerHash, demoHash, lockedHash).run();
            return null;
        });
    }

    /** Tek bir yükleme işinin durumu. */
    private static final class Generator {
        private final Bank bank;
        private final Connection c;
        private final Random random;
        private final String tellerHash;
        private final String demoHash;
        private final String lockedHash;
        private final LocalDateTime now;
        private final LocalDate start;
        private final Set<String> ibans = new HashSet<>();
        private final List<Cust> customers = new ArrayList<>();
        private final List<Event> events = new ArrayList<>();
        private long tellerId;

        Generator(Bank bank, Connection c, Random random, String tellerHash, String demoHash, String lockedHash) {
            this.bank = bank;
            this.c = c;
            this.random = random;
            this.tellerHash = tellerHash;
            this.demoHash = demoHash;
            this.lockedHash = lockedHash;
            this.now = bank.now();
            this.start = YearMonth.from(now).minusMonths(MONTHS - 1L).atDay(1);
        }

        void run() throws SQLException {
            tellerId = insertTeller();
            final Set<String> tckns = new HashSet<>();
            tckns.add(CUSTOMER_TCKN);
            for (int i = 0; i < CUSTOMERS; i++) {
                final boolean demo = i == 0;
                String tckn = CUSTOMER_TCKN;
                if (!demo) {
                    do {
                        tckn = TcKimlik.random(random);
                    } while (!tckns.add(tckn));
                }
                final String first = demo ? "Elif" : FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
                final String last = demo ? "Demir" : LAST_NAMES[random.nextInt(LAST_NAMES.length)];
                customers.add(insertCustomer(tckn, first, last, demo));
            }
            for (Cust cust : customers)
                plan(cust, cust == customers.get(0));
            events.sort(Comparator.comparing(Event::at));
            for (Event e : events)
                apply(e);
            saveBalances();
            insertRecipients(customers.get(0));
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO meta(key, value) VALUES ('sample_data', '1')")) {
                ps.executeUpdate();
            }
        }

        private long insertTeller() throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO staff(username, full_name, role, password_hash,"
                    + " created_at) VALUES (?, 'Deniz Aydın', 'TELLER', ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, TELLER_USERNAME);
                ps.setString(2, tellerHash);
                ps.setString(3, Records.ts(now));
                ps.executeUpdate();
                return key(ps);
            }
        }

        private Cust insertCustomer(String tckn, String first, String last, boolean demo) throws SQLException {
            final Cust cust = new Cust();
            cust.name = first + " " + last;
            cust.since = demo ? start.minusYears(2) : start.minusDays(30 + random.nextInt(900));
            final String email = ascii(first + "." + last) + (demo ? "" : random.nextInt(100)) + "@example.com";
            final String phone = "5" + (30 + random.nextInt(25)) + String.format("%07d", random.nextInt(10_000_000));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO customers(tckn, first_name, last_name, phone,"
                    + " email, password_hash, must_change, daily_limit, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, tckn);
                ps.setString(2, first);
                ps.setString(3, last);
                ps.setString(4, phone);
                ps.setString(5, email);
                ps.setString(6, demo ? demoHash : lockedHash);
                ps.setInt(7, 0);
                ps.setLong(8, CustomerService.DEFAULT_DAILY_LIMIT);
                ps.setString(9, Records.ts(cust.since.atTime(10, 0)));
                ps.executeUpdate();
                cust.id = key(ps);
            }
            cust.salary = demo ? 8_500_000 : (28_000 + random.nextInt(92_000)) * 100L;
            cust.tryAccount = insertAccount(cust, Currency.TRY, cust.since);
            if (demo || random.nextInt(100) < 30)
                cust.usd = insertAccount(cust, Currency.USD, cust.since);
            if (demo || random.nextInt(100) < 15)
                cust.eur = insertAccount(cust, Currency.EUR, cust.since);
            return cust;
        }

        private Acc insertAccount(Cust owner, Currency currency, LocalDate opened) throws SQLException {
            final Acc acc = new Acc();
            acc.owner = owner;
            acc.currency = currency;
            do {
                acc.iban = Iban.random(random);
            } while (!ibans.add(acc.iban));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(customer_id, iban, type, currency,"
                    + " opened_at) VALUES (?, ?, 'CURRENT', ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, owner.id);
                ps.setString(2, acc.iban);
                ps.setString(3, currency.name());
                ps.setString(4, Records.ts(opened.atTime(10, 0)));
                ps.executeUpdate();
                acc.id = key(ps);
            }
            return acc;
        }

        /** Bir müşterinin 6 aylık hareket planı. */
        private void plan(Cust cust, boolean demo) throws SQLException {
            final Acc acc = cust.tryAccount;
            final long opening = demo ? 25_000_000 : (5_000 + random.nextInt(75_000)) * 100L;
            add(at(start, 0), Kind.CASH_IN, null, acc, opening, Category.CASH, "Nakit yatırma", null);
            if (cust.usd != null)
                add(at(start, 1), Kind.CASH_IN, null, cust.usd, (300 + random.nextInt(4_700)) * 100L, Category.CASH,
                        "Nakit yatırma", null);
            if (cust.eur != null)
                add(at(start, 2), Kind.CASH_IN, null, cust.eur, (200 + random.nextInt(2_800)) * 100L, Category.CASH,
                        "Nakit yatırma", null);

            final String employer = demo ? EMPLOYERS[0] : EMPLOYERS[random.nextInt(EMPLOYERS.length)];
            final int payday = 1 + random.nextInt(5);
            final boolean renter = demo || random.nextInt(100) < 60;
            final Cust landlord = renter && (demo || random.nextBoolean()) ? other(cust) : null;
            final long rent = renter ? Math.max(8_000, Math.round(cust.salary / 100.0 * 0.3 / 500) * 500) * 100L : 0;
            final double scale = cust.salary / 6_000_000.0; // gelire göre harcama ölçeği

            for (YearMonth m = YearMonth.from(start); !m.isAfter(YearMonth.from(now)); m = m.plusMonths(1)) {
                final String monthName = MONTH_NAMES[m.getMonthValue() - 1];
                add(day(m, payday, 9), Kind.EXTERNAL_IN, null, acc, cust.salary, Category.SALARY,
                        monthName + " maaşı", employer);
                if (renter) {
                    if (landlord != null)
                        add(day(m, payday + 2, 11), Kind.P2P, acc, landlord.tryAccount, rent, Category.RENT,
                                monthName + " kirası", null);
                    else
                        add(day(m, payday + 2, 11), Kind.EXTERNAL_OUT, acc, null, rent, Category.RENT,
                                monthName + " kirası", "Güven Emlak");
                }
                for (Spend bill : BILLS)
                    add(day(m, 10 + random.nextInt(15), 14), Kind.EXTERNAL_OUT, acc, null,
                            amount(bill, Math.sqrt(scale)), bill.category(), bill.description(), bill.merchants()[0]);
                spend(m, acc, SPENDING[0], 5 + random.nextInt(5), scale);
                spend(m, acc, SPENDING[1], 2 + random.nextInt(5), scale);
                spend(m, acc, SPENDING[2], 3 + random.nextInt(4), scale);
                spend(m, acc, SPENDING[3], 1 + random.nextInt(3), scale);
                spend(m, acc, SPENDING[4], random.nextInt(3), scale);
                if (random.nextInt(100) < 25)
                    spend(m, acc, SPENDING[5], 1, scale);
                for (int i = random.nextInt(3); i > 0; i--)
                    add(day(m, 1 + random.nextInt(28), 12), Kind.CASH_OUT, acc, null,
                            (5 + random.nextInt(40)) * 10_000L, Category.CASH, "Nakit çekme", null);
                if (random.nextInt(100) < 40) {
                    final Cust friend = other(cust);
                    add(day(m, 1 + random.nextInt(28), 19), Kind.P2P, acc, friend.tryAccount,
                            (2 + random.nextInt(30)) * 10_000L, Category.TRANSFER, "Borç ödemesi", null);
                }
                if (demo && cust.usd != null)
                    add(day(m, 20, 15), Kind.EXCHANGE, acc, cust.usd, (8_000 + random.nextInt(12_000)) * 100L,
                            Category.EXCHANGE, null, null);
            }
            if (demo && cust.eur != null)
                add(now.minusDays(45).withHour(16), Kind.EXCHANGE, acc, cust.eur, 2_500_000, Category.EXCHANGE, null, null);

            // Vadeli hesap: demo müşteride 2 gün sonra vadesi dolan 32 günlük, diğerlerinde %20 ihtimalle
            if (demo)
                add(now.minusDays(30).withHour(10), Kind.DEPOSIT_OPEN, acc, insertDeposit(cust, 32,
                        now.minusDays(30).withHour(10)), 15_000_000, Category.SAVINGS, null, null);
            else if (random.nextInt(100) < 20) {
                final LocalDateTime opened = now.minusDays(5 + random.nextInt(55)).withHour(11);
                final int days = random.nextBoolean() ? 92 : 181;
                add(opened, Kind.DEPOSIT_OPEN, acc, insertDeposit(cust, days, opened),
                        (10 + random.nextInt(90)) * 1_000_000L, Category.SAVINGS, null, null);
            }
        }

        private static final String[] MONTH_NAMES = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz",
            "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

        private void spend(YearMonth m, Acc acc, Spend spend, int count, double scale) {
            for (int i = 0; i < count; i++) {
                final String merchant = spend.merchants()[random.nextInt(spend.merchants().length)];
                add(day(m, 1 + random.nextInt(28), 10 + random.nextInt(11)), Kind.EXTERNAL_OUT, acc, null,
                        amount(spend, scale), spend.category(), "", merchant);
            }
        }

        private long amount(Spend spend, double scale) {
            final double tl = spend.minTl() + random.nextDouble() * (spend.maxTl() - spend.minTl());
            return Math.max(100, Math.round(tl * Math.max(0.6, Math.min(scale, 1.8)) * 100));
        }

        private Acc insertDeposit(Cust owner, int days, LocalDateTime opened) throws SQLException {
            final Interest.Term term = Interest.term(days);
            final Acc acc = new Acc();
            acc.owner = owner;
            acc.currency = Currency.TRY;
            do {
                acc.iban = Iban.random(random);
            } while (!ibans.add(acc.iban));
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(customer_id, iban, type, currency,"
                    + " opened_at, term_days, annual_rate, principal, maturity_date, linked_account_id)"
                    + " VALUES (?, ?, 'TIME_DEPOSIT', 'TRY', ?, ?, ?, 0, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, owner.id);
                ps.setString(2, acc.iban);
                ps.setString(3, Records.ts(opened));
                ps.setInt(4, days);
                ps.setString(5, term.annualRate().toPlainString());
                ps.setString(6, opened.toLocalDate().plusDays(days).toString());
                ps.setLong(7, owner.tryAccount.id);
                ps.executeUpdate();
                acc.id = key(ps);
            }
            return acc;
        }

        private void add(LocalDateTime at, Kind kind, Acc from, Acc to, long amount, Category category,
                         String description, String counterparty) {
            if (at.isAfter(now))
                return;
            events.add(new Event(at, kind, from, to, amount, category, description, counterparty));
        }

        /** Olayı uygular; bakiye yetmiyorsa atlar (gerçek hayatta işlem reddedilirdi). */
        private void apply(Event e) throws SQLException {
            final String ref = bank.newRef();
            switch (e.kind()) {
                case CASH_IN -> {
                    e.to().balance += e.amount();
                    tx(e.to(), ref, TxType.DEPOSIT, e.amount(), e.description(), null, null, e.category(), false, tellerId, e.at());
                }
                case EXTERNAL_IN -> {
                    e.to().balance += e.amount();
                    tx(e.to(), ref, TxType.TRANSFER_IN, e.amount(), e.description(), e.counterparty(), null,
                            e.category(), false, null, e.at());
                }
                case CASH_OUT, EXTERNAL_OUT -> {
                    if (e.from().balance < e.amount())
                        return;
                    e.from().balance -= e.amount();
                    tx(e.from(), ref, e.kind() == Kind.CASH_OUT ? TxType.WITHDRAWAL : TxType.TRANSFER_OUT, e.amount(),
                            e.description(), e.counterparty(), null, e.category(), false,
                            e.kind() == Kind.CASH_OUT ? tellerId : null, e.at());
                }
                case P2P -> {
                    if (e.from().balance < e.amount())
                        return;
                    e.from().balance -= e.amount();
                    e.to().balance += e.amount();
                    tx(e.from(), ref, TxType.TRANSFER_OUT, e.amount(), e.description(), e.to().owner.name, e.to().iban,
                            e.category(), false, null, e.at());
                    tx(e.to(), ref, TxType.TRANSFER_IN, e.amount(), e.description(), e.from().owner.name, e.from().iban,
                            e.category() == Category.RENT ? Category.RENT : Category.TRANSFER, false, null, e.at());
                }
                case EXCHANGE -> {
                    if (e.from().balance < e.amount())
                        return;
                    final long converted = Rates.convert(e.amount(), e.from().currency, e.to().currency);
                    final String text = e.from().currency + " → " + e.to().currency;
                    e.from().balance -= e.amount();
                    e.to().balance += converted;
                    tx(e.from(), ref, TxType.EXCHANGE_OUT, e.amount(), text, e.from().owner.name, e.to().iban,
                            Category.EXCHANGE, true, null, e.at());
                    tx(e.to(), ref, TxType.EXCHANGE_IN, converted, text, e.from().owner.name, e.from().iban,
                            Category.EXCHANGE, true, null, e.at());
                }
                case DEPOSIT_OPEN -> {
                    final long amount = Math.min(e.amount(), e.from().balance / 2 / 100_000 * 100_000);
                    if (amount < Interest.MIN_PRINCIPAL) {
                        close(e.to());
                        return;
                    }
                    e.from().balance -= amount;
                    e.to().balance += amount;
                    try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET principal = ? WHERE id = ?")) {
                        ps.setLong(1, amount);
                        ps.setLong(2, e.to().id);
                        ps.executeUpdate();
                    }
                    tx(e.from(), ref, TxType.TRANSFER_OUT, amount, "", e.from().owner.name, e.to().iban,
                            Category.SAVINGS, true, null, e.at());
                    tx(e.to(), ref, TxType.TRANSFER_IN, amount, "", e.from().owner.name, e.from().iban,
                            Category.SAVINGS, true, null, e.at());
                }
                default -> throw new IllegalStateException();
            }
        }

        /** Parası yetmediği için açılamayan vadeli hesap kaydı silinir. */
        private void close(Acc deposit) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
                ps.setLong(1, deposit.id);
                ps.executeUpdate();
            }
        }

        private void tx(Acc acc, String ref, TxType type, long amount, String description, String counterparty,
                        String counterpartyIban, Category category, boolean internal, Long staffId, LocalDateTime at)
                throws SQLException {
            AccountService.insertTx(c, acc.id, ref, type, amount, acc.balance, description == null ? "" : description,
                    counterparty, counterpartyIban, category, internal, staffId, at);
        }

        private void saveBalances() throws SQLException {
            final Map<Long, Long> balances = new HashMap<>();
            for (Event e : events) {
                if (e.from() != null)
                    balances.put(e.from().id, e.from().balance);
                if (e.to() != null)
                    balances.put(e.to().id, e.to().balance);
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE accounts SET balance = ? WHERE id = ?")) {
                for (Map.Entry<Long, Long> entry : balances.entrySet()) {
                    ps.setLong(1, entry.getValue());
                    ps.setLong(2, entry.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }

        /** Demo müşterinin kayıtlı alıcıları: para gönderdiği ilk 3 kişi. */
        private void insertRecipients(Cust demo) throws SQLException {
            final Set<Long> added = new HashSet<>();
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO recipients(customer_id, name, iban)"
                    + " VALUES (?, ?, ?)")) {
                for (Event e : events) {
                    if (e.kind() != Kind.P2P || e.from().owner != demo || !added.add(e.to().id) || added.size() > 3)
                        continue;
                    ps.setLong(1, demo.id);
                    ps.setString(2, e.category() == Category.RENT ? "Ev sahibi" : e.to().owner.name);
                    ps.setString(3, e.to().iban);
                    ps.executeUpdate();
                }
            }
        }

        private Cust other(Cust self) {
            Cust other;
            do {
                other = customers.get(random.nextInt(customers.size()));
            } while (other == self);
            return other;
        }

        private LocalDateTime day(YearMonth m, int day, int hour) {
            return m.atDay(Math.min(day, m.lengthOfMonth())).atTime(hour, random.nextInt(60), random.nextInt(60));
        }

        /** Açılış yatırımları 6 aylık pencereden önceki güne yazılır; grafiklerde ilk ay şişmesin. */
        private LocalDateTime at(LocalDate date, int minute) {
            return date.minusDays(1).atTime(9, 30 + minute);
        }

        private static String ascii(String text) {
            return java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT).replace("ı", "i"),
                    java.text.Normalizer.Form.NFD).replaceAll("[^a-z.]", "");
        }

        private static long key(PreparedStatement ps) throws SQLException {
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}
