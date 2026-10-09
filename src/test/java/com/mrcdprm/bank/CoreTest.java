package com.mrcdprm.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.core.Interest;
import com.mrcdprm.bank.core.Money;
import com.mrcdprm.bank.core.PasswordHasher;
import com.mrcdprm.bank.core.PasswordPolicy;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.core.TcKimlik;
import com.mrcdprm.bank.core.Validation;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CoreTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    @Test
    void ibanKnownExampleIsValid() {
        // Türkiye için yayımlanmış örnek IBAN (ISO 13616 kayıt örneği)
        assertTrue(Iban.isValid("TR33 0006 1005 1978 6457 8413 26"));
        assertFalse(Iban.isValid("TR34 0006 1005 1978 6457 8413 26"));
    }

    @Test
    void generatedIbansPassMod97AndBelongToBank() {
        final SecureRandom random = new SecureRandom();
        for (int i = 0; i < 500; i++) {
            final String iban = Iban.random(random);
            assertEquals(26, iban.length());
            assertTrue(Iban.isValid(iban), iban);
            assertTrue(Iban.isOurs(iban), iban);
        }
    }

    @Test
    void ibanRejectsTyposAndWrongShapes() {
        final String iban = Iban.fromAccountNumber("1234567890123456");
        assertTrue(Iban.isValid(iban));
        final char[] typo = iban.toCharArray();
        typo[10] = typo[10] == '9' ? '8' : (char) (typo[10] + 1);
        assertFalse(Iban.isValid(new String(typo)));
        assertFalse(Iban.isValid(""));
        assertFalse(Iban.isValid(null));
        assertFalse(Iban.isValid("DE89370400440532013000"));
        assertFalse(Iban.isValid(iban + "0"));
        assertTrue(Iban.isValid(Iban.format(iban).toLowerCase(Locale.ROOT)));
    }

    @Test
    void ibanFormatGroupsOfFour() {
        assertEquals("TR33 0006 1005 1978 6457 8413 26", Iban.format("TR330006100519786457841326"));
    }

    @Test
    void tcKimlikChecksDigits() {
        assertEquals("12345678950", TcKimlik.withCheckDigits("123456789"));
        assertTrue(TcKimlik.isValid("12345678950"));
        assertFalse(TcKimlik.isValid("12345678951"));
        assertFalse(TcKimlik.isValid("02345678950"));
        assertFalse(TcKimlik.isValid("1234567895"));
        assertFalse(TcKimlik.isValid("1234567895a"));
        final Random random = new Random(1);
        for (int i = 0; i < 500; i++)
            assertTrue(TcKimlik.isValid(TcKimlik.random(random)));
    }

    @Test
    void moneyParsesTurkishAndEnglishInput() {
        assertEquals(OptionalLong.of(123456), Money.parse("1.234,56", TR));
        assertEquals(OptionalLong.of(123456), Money.parse("1234,56", TR));
        assertEquals(OptionalLong.of(123456), Money.parse("1234.56", TR));
        assertEquals(OptionalLong.of(150000), Money.parse("1.500", TR));
        assertEquals(OptionalLong.of(150), Money.parse("1.50", TR));
        assertEquals(OptionalLong.of(150000), Money.parse("1,500", Locale.ENGLISH));
        assertEquals(OptionalLong.of(123456), Money.parse("1,234.56", Locale.ENGLISH));
        assertEquals(OptionalLong.of(1000), Money.parse(" 10 ₺", TR));
        assertEquals(OptionalLong.of(1050), Money.parse("10,5", TR));
    }

    @Test
    void moneyRejectsBadInput() {
        for (String bad : new String[] {"", " ", "abc", "-5", "0", "0,00", "1,234", "12,345,6", "1.2.3", "1,999",
            "10,555", "1e5", ",5", "5,", "99999999999"})
            assertTrue(Money.parse(bad, TR).isEmpty(), bad);
    }

    @Test
    void moneyFormatsByLocale() {
        assertEquals("1.234,56 ₺", Money.format(123456, Currency.TRY, TR));
        assertEquals("1,234.56 $", Money.format(123456, Currency.USD, Locale.ENGLISH));
        assertEquals("-0,05 €", Money.formatSigned(5, false, Currency.EUR, TR));
    }

    @Test
    void passwordPolicyListsEveryProblem() {
        assertEquals(List.of(), PasswordPolicy.violations("Guclu.Sifre2026", "ayse"));
        assertEquals(List.of("length", "upper", "digit"), PasswordPolicy.violations("kisa", null));
        assertTrue(PasswordPolicy.violations("Veznedar.2026x", "veznedar").contains("containsLogin"));
        final SecureRandom random = new SecureRandom();
        for (int i = 0; i < 200; i++)
            assertEquals(List.of(), PasswordPolicy.violations(PasswordPolicy.temporary(random), "x"));
    }

    @Test
    void argon2HashVerifiesAndRejects() {
        final String hash = PasswordHasher.hash("Dogru.Sifre1");
        assertTrue(hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(PasswordHasher.verify("Dogru.Sifre1", hash));
        assertFalse(PasswordHasher.verify("Yanlis.Sifre1", hash));
        // aynı şifre farklı tuzla farklı özet verir
        assertFalse(hash.equals(PasswordHasher.hash("Dogru.Sifre1")));
    }

    @Test
    void argon2RejectsTamperedParameters() {
        final String hash = PasswordHasher.hash("Dogru.Sifre1");
        assertFalse(PasswordHasher.verify("Dogru.Sifre1", hash.replace("m=19456", "m=99999999")));
        assertFalse(PasswordHasher.verify("Dogru.Sifre1", hash.replace("t=2", "t=500")));
        assertFalse(PasswordHasher.verify("Dogru.Sifre1", "düz metin"));
        assertFalse(PasswordHasher.verify("Dogru.Sifre1", null));
    }

    @Test
    void exchangeRoundsInBanksFavour() {
        // 100 USD -> 100 x 41,25 = 4.125 TL
        assertEquals(412_500, Rates.convert(10_000, Currency.USD, Currency.TRY));
        // 1.000 TL -> 1000 / 41,85 = 23,894... USD -> 23,89 (aşağı yuvarlanır)
        assertEquals(2_389, Rates.convert(100_000, Currency.TRY, Currency.USD));
        // döviz -> döviz TL üzerinden: 100 EUR -> 4.790 TL -> 114,45 USD
        assertEquals(11_445, Rates.convert(10_000, Currency.EUR, Currency.USD));
        // al-sat aynı tutara dönmez (makas)
        assertTrue(Rates.convert(Rates.convert(100_000, Currency.TRY, Currency.USD), Currency.USD, Currency.TRY) < 100_000);
    }

    @Test
    void simpleInterest() {
        // 100.000 TL, %40, 32 gün: 100000 x 0,40 x 32 / 365 = 3.506,84 TL
        assertEquals(350_684, Interest.simple(10_000_000, new BigDecimal("0.4000"), 32));
        assertEquals(0, Interest.simple(1, new BigDecimal("0.4000"), 32));
    }

    @Test
    void validationHelpers() {
        assertEquals("5321234567", Validation.phone("0532 123 45 67").orElseThrow());
        assertEquals("5321234567", Validation.phone("+90 (532) 123-45-67").orElseThrow());
        assertTrue(Validation.phone("0212 123 45 67").isEmpty());
        assertEquals("0532 123 45 67", Validation.formatPhone("5321234567"));
        assertEquals("Ayşe Nur", Validation.name("  Ayşe   Nur ").orElseThrow());
        assertTrue(Validation.name("<script>").isEmpty());
        assertTrue(Validation.email("a@b").isEmpty());
        assertEquals("Ay** Yı****", Validation.maskName("Ayşe Yılmaz"));
        assertEquals("%50\\%\\_%", Validation.likePattern("50%_"));
        assertEquals("satır", Validation.description("  satır\n\t "));
    }
}
