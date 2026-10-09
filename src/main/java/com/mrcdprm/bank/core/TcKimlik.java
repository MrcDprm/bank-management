package com.mrcdprm.bank.core;

import java.util.random.RandomGenerator;

/**
 * T.C. kimlik numarası: 11 basamak, ilki 0 olamaz.
 * 10. basamak = ((1, 3, 5, 7, 9. basamakların toplamı) x 7 - (2, 4, 6, 8. basamakların toplamı)) mod 10
 * 11. basamak = (ilk 10 basamağın toplamı) mod 10
 */
public final class TcKimlik {

    private TcKimlik() {
    }

    public static boolean isValid(String text) {
        if (text == null || !text.matches("[1-9]\\d{10}"))
            return false;
        final int[] d = new int[11];
        for (int i = 0; i < 11; i++)
            d[i] = text.charAt(i) - '0';
        final int[] check = checkDigits(d);
        return check[0] == d[9] && check[1] == d[10];
    }

    /** Örnek veri için geçerli rastgele kimlik numarası. */
    public static String random(RandomGenerator random) {
        final int[] d = new int[11];
        d[0] = 1 + random.nextInt(9);
        for (int i = 1; i < 9; i++)
            d[i] = random.nextInt(10);
        final int[] check = checkDigits(d);
        d[9] = check[0];
        d[10] = check[1];
        final StringBuilder out = new StringBuilder(11);
        for (int digit : d)
            out.append(digit);
        return out.toString();
    }

    /** İlk 9 basamağa kontrol basamaklarını ekler: "123456789" -> "12345678950". */
    public static String withCheckDigits(String first9) {
        if (first9 == null || !first9.matches("[1-9]\\d{8}"))
            throw new IllegalArgumentException("9 basamak gerekli");
        final int[] d = new int[11];
        for (int i = 0; i < 9; i++)
            d[i] = first9.charAt(i) - '0';
        final int[] check = checkDigits(d);
        return first9 + check[0] + check[1];
    }

    /** İlk 9 basamaktan 10. ve 11. basamakları hesaplar. */
    private static int[] checkDigits(int[] d) {
        final int odd = d[0] + d[2] + d[4] + d[6] + d[8];
        final int even = d[1] + d[3] + d[5] + d[7];
        final int tenth = Math.floorMod(odd * 7 - even, 10);
        int sum = tenth;
        for (int i = 0; i < 9; i++)
            sum += d[i];
        return new int[] {tenth, sum % 10};
    }
}
