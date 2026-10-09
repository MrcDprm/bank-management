package com.mrcdprm.bank.ui;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Arayüz metinleri (Türkçe / İngilizce). Metinler i18n/messages_*.properties dosyalarındadır.
 * {0}, {1} yer tutucuları basit metin değiştirmeyle doldurulur; MessageFormat kullanılmaz çünkü
 * Türkçedeki kesme işaretini ("TL'ye") özel karakter sayıp metni bozar.
 */
public final class I18n {

    public static final Locale TR = Locale.forLanguageTag("tr");
    public static final Locale EN = Locale.ENGLISH;

    private static Locale locale = TR;
    private static ResourceBundle bundle = load(TR);

    private I18n() {
    }

    public static void setLocale(Locale newLocale) {
        locale = "en".equals(newLocale.getLanguage()) ? EN : TR;
        bundle = load(locale);
    }

    public static Locale locale() {
        return locale;
    }

    public static boolean isTurkish() {
        return locale == TR;
    }

    private static ResourceBundle load(Locale l) {
        return ResourceBundle.getBundle("com.mrcdprm.bank.i18n.messages", l, ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /** Anahtarın metni; eksik anahtar ekranda !anahtar! olarak görünür (geliştirirken hemen fark edilsin). */
    public static String t(String key, Object... args) {
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        for (int i = 0; i < args.length; i++)
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        return text;
    }

    /** Enum değerinin metni: "enum.Category.RENT" gibi. */
    public static String of(Enum<?> value) {
        return t("enum." + value.getDeclaringClass().getSimpleName() + "." + value.name());
    }

    /** Servis hatasını okunur cümleye çevirir; tutar ve tarih argümanları biçimlenir. */
    public static String error(BankException e) {
        final Object[] args = e.args();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof LocalDateTime time)
                args[i] = time(time);
            else if (args[i] instanceof Long amount && !"tooManyAccounts".equals(e.code())
                    && !"tooManyRecipients".equals(e.code()))
                args[i] = money(amount, Currency.TRY);
        }
        if ("weakPassword".equals(e.code()) && args.length == 1)
            return t("error.weakPassword") + "\n" + passwordRules(String.valueOf(args[0]));
        return t("error." + e.code(), args);
    }

    /** "length,upper" -> her kural ayrı satırda, madde imli. */
    public static String passwordRules(String codes) {
        final StringBuilder out = new StringBuilder();
        for (String code : codes.split(",")) {
            if (code.isBlank())
                continue;
            if (!out.isEmpty())
                out.append('\n');
            out.append("• ").append(t("password.rule." + code));
        }
        return out.toString();
    }

    public static String money(long minor, Currency currency) {
        return Money.format(minor, currency, locale);
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern(isTurkish() ? "dd.MM.yyyy" : "MMM d, yyyy", locale));
    }

    public static String time(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern(isTurkish() ? "dd.MM.yyyy HH:mm" : "MMM d, yyyy HH:mm", locale));
    }
}
