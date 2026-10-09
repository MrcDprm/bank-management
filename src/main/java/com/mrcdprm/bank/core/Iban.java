package com.mrcdprm.bank.core;

import java.math.BigInteger;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Türkiye IBAN'ı: TR + 2 kontrol basamağı + 5 basamak banka kodu + 1 rezerv (0) + 16 basamak hesap no = 26 karakter.
 * Kontrol basamakları ISO 13616 mod-97 yöntemiyle hesaplanır.
 */
public final class Iban {

    /** Bu demo bankanın kurgusal banka kodu. */
    public static final String BANK_CODE = "00987";
    public static final int LENGTH = 26;

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** 16 basamaklı hesap numarasından kontrol basamakları doğru bir IBAN üretir. */
    public static String fromAccountNumber(String accountNumber) {
        if (!accountNumber.matches("\\d{16}"))
            throw new IllegalArgumentException("Hesap numarası 16 basamak olmalı");
        final String bban = BANK_CODE + "0" + accountNumber;
        final int check = 98 - mod97(bban + "TR00");
        return "TR" + (check < 10 ? "0" : "") + check + bban;
    }

    /** Rastgele 16 basamaklı hesap numarasıyla yeni bir IBAN. Tekrar kontrolü veritabanında yapılır. */
    public static String random(RandomGenerator random) {
        final StringBuilder number = new StringBuilder(16);
        number.append(1 + random.nextInt(9));
        for (int i = 1; i < 16; i++)
            number.append(random.nextInt(10));
        return fromAccountNumber(number.toString());
    }

    /** Boşlukları atar ve büyük harfe çevirir: "tr12 0098 ..." -> "TR120098...". */
    public static String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    /** Biçim (TR + 24 rakam) ve mod-97 kontrolü. Boşluklu yazım kabul edilir. */
    public static boolean isValid(String text) {
        final String iban = normalize(text);
        if (iban.length() != LENGTH || !iban.matches("TR\\d{24}"))
            return false;
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
    }

    /** IBAN bu bankaya mı ait (banka kodu)? */
    public static boolean isOurs(String text) {
        final String iban = normalize(text);
        return isValid(iban) && iban.substring(4, 9).equals(BANK_CODE);
    }

    /** Okunaklı yazım: "TR12 0098 7012 3456 7890 1234 56". */
    public static String format(String text) {
        final String iban = normalize(text);
        final StringBuilder out = new StringBuilder(iban.length() + 6);
        for (int i = 0; i < iban.length(); i++) {
            if (i > 0 && i % 4 == 0)
                out.append(' ');
            out.append(iban.charAt(i));
        }
        return out.toString();
    }

    /** Harfleri sayıya çevirir (A=10 ... Z=35) ve 97'ye bölümden kalanı verir. */
    private static int mod97(String text) {
        final StringBuilder digits = new StringBuilder(text.length() + 4);
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c))
                digits.append(c);
            else
                digits.append(c - 'A' + 10);
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue();
    }
}
