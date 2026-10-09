package com.mrcdprm.bank.core;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Para tutarları: bütün hesaplar kuruş/cent cinsinden long ile yapılır.
 * double kullanılmaz; 0.1 + 0.2 gibi sayılar ikilik sistemde tam gösterilemez ve kuruş kaybolur.
 */
public final class Money {

    /** Tek seferde girilebilecek en büyük tutar: 10 milyar (yazım hatasına ve taşmaya karşı). */
    public static final long MAX_AMOUNT = 1_000_000_000_000L;

    private Money() {
    }

    /** 123456 kuruş -> "1.234,56 ₺" (Türkçe) ya da "1,234.56 ₺" (İngilizce). */
    public static String format(long minor, Currency currency, Locale locale) {
        return formatNumber(minor, locale) + " " + currency.symbol();
    }

    /** Sadece sayı kısmı, para birimi simgesi olmadan. */
    public static String formatNumber(long minor, Locale locale) {
        final DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(locale));
        return format.format(BigDecimal.valueOf(minor, 2));
    }

    /** İşaretli gösterim: gelen "+1.234,56 ₺", giden "-1.234,56 ₺". */
    public static String formatSigned(long minor, boolean incoming, Currency currency, Locale locale) {
        return (incoming ? "+" : "-") + format(minor, currency, locale);
    }

    /**
     * Kullanıcının yazdığı tutarı kuruşa çevirir. "1.234,56", "1234,56", "1234.56", "1,234.56" kabul edilir.
     * Hem nokta hem virgül varsa sondaki ondalık ayırıcıdır. Tek ayırıcı varsa ve ardından tam 3 basamaklı
     * gruplar geliyorsa dile göre binlik sayılır (Türkçede "1.500", İngilizcede "1,500" = bin beş yüz);
     * aksi hâlde ondalık ayırıcıdır.
     * Geçersiz, sıfır, negatif, 2'den fazla ondalık basamaklı ya da çok büyük tutarlarda boş döner.
     */
    public static OptionalLong parse(String text, Locale locale) {
        if (text == null)
            return OptionalLong.empty();
        String s = text.strip().replace(" ", "").replace(" ", "");
        for (Currency c : Currency.values())
            s = s.replace(c.symbol(), "");
        if (s.isEmpty() || !s.matches("[0-9.,]+"))
            return OptionalLong.empty();

        final boolean turkish = "tr".equals(locale.getLanguage());
        final char localGrouping = turkish ? '.' : ',';
        final int lastDot = s.lastIndexOf('.');
        final int lastComma = s.lastIndexOf(',');
        final String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            final char decimal = lastDot > lastComma ? '.' : ',';
            normalized = groupedToPlain(s, decimal == '.' ? ',' : '.', decimal);
        } else if (lastDot >= 0 || lastComma >= 0) {
            final char separator = lastDot >= 0 ? '.' : ',';
            final String quoted = Pattern.quote(String.valueOf(separator));
            if (separator == localGrouping && s.matches("\\d{1,3}(" + quoted + "\\d{3})+"))
                normalized = s.replace(String.valueOf(separator), "");
            else if (s.indexOf(separator) == s.lastIndexOf(separator))
                normalized = s.replace(separator, '.');
            else
                normalized = null; // "1.2.3" gibi
        } else {
            normalized = s;
        }
        if (normalized == null || normalized.startsWith(".") || normalized.endsWith("."))
            return OptionalLong.empty();

        try {
            final BigDecimal value = new BigDecimal(normalized);
            if (value.stripTrailingZeros().scale() > 2)
                return OptionalLong.empty();
            final long minor = value.movePointRight(2).setScale(0).longValueExact();
            if (minor <= 0 || minor > MAX_AMOUNT)
                return OptionalLong.empty();
            return OptionalLong.of(minor);
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** "1.234.567,89" -> "1234567.89"; binlik gruplar 3 basamak değilse null. */
    private static String groupedToPlain(String s, char grouping, char decimal) {
        final int decimalIndex = s.lastIndexOf(decimal);
        final String integerPart = s.substring(0, decimalIndex);
        final String fraction = s.substring(decimalIndex + 1);
        if (fraction.isEmpty() || !fraction.matches("\\d+") || integerPart.indexOf(decimal) >= 0)
            return null;
        if (!integerPart.matches("\\d{1,3}(" + Pattern.quote(String.valueOf(grouping)) + "\\d{3})*"))
            return null;
        return integerPart.replace(String.valueOf(grouping), "") + "." + fraction;
    }
}
