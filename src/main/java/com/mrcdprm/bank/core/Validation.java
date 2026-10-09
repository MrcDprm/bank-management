package com.mrcdprm.bank.core;

import java.util.Locale;
import java.util.Optional;

/** Formlardan gelen metinlerin doğrulanması ve temizlenmesi. Servisler kayıttan önce bunları çağırır. */
public final class Validation {

    public static final int DESCRIPTION_MAX = 140;

    private Validation() {
    }

    /** Ad / soyad: harf, boşluk, kesme ve tire; 2-50 karakter. Fazla boşluklar teke indirilir. */
    public static Optional<String> name(String text) {
        if (text == null)
            return Optional.empty();
        final String s = text.strip().replaceAll("\\s+", " ");
        return s.matches("[\\p{L}][\\p{L} '’\\-.]{1,49}") ? Optional.of(s) : Optional.empty();
    }

    /** Cep telefonu: "0532 123 45 67", "+90 532...", "5321234567" -> "5321234567". */
    public static Optional<String> phone(String text) {
        if (text == null)
            return Optional.empty();
        String digits = text.replaceAll("[\\s()\\-+]", "");
        if (!digits.matches("\\d+"))
            return Optional.empty();
        if (digits.startsWith("90") && digits.length() == 12)
            digits = digits.substring(2);
        if (digits.startsWith("0") && digits.length() == 11)
            digits = digits.substring(1);
        return digits.matches("5\\d{9}") ? Optional.of(digits) : Optional.empty();
    }

    /** "5321234567" -> "0532 123 45 67". */
    public static String formatPhone(String digits) {
        if (digits == null || digits.length() != 10)
            return digits;
        return "0" + digits.substring(0, 3) + " " + digits.substring(3, 6) + " " + digits.substring(6, 8) + " "
                + digits.substring(8);
    }

    public static Optional<String> email(String text) {
        if (text == null)
            return Optional.empty();
        final String s = text.strip().toLowerCase(Locale.ROOT);
        return s.length() <= 120 && s.matches("[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}") ? Optional.of(s) : Optional.empty();
    }

    /** Açıklama: kontrol karakterleri atılır, boşluklar sadeleşir, en fazla 140 karakter. */
    public static String description(String text) {
        if (text == null)
            return "";
        final String s = text.replaceAll("\\p{Cntrl}", " ").strip().replaceAll("\\s+", " ");
        return s.length() > DESCRIPTION_MAX ? s.substring(0, DESCRIPTION_MAX) : s;
    }

    /** Personel kullanıcı adı: küçük harf, rakam, nokta, alt çizgi; 3-32 karakter. */
    public static Optional<String> username(String text) {
        if (text == null)
            return Optional.empty();
        final String s = text.strip().toLowerCase(Locale.ROOT);
        return s.matches("[a-z0-9._]{3,32}") ? Optional.of(s) : Optional.empty();
    }

    /** "Ayşe Yılmaz" -> "Ay** Yı****": IBAN sahibi doğrulamasında karşı tarafın adını tam göstermemek için. */
    public static String maskName(String fullName) {
        final StringBuilder out = new StringBuilder();
        for (String part : fullName.split(" ")) {
            if (part.isEmpty())
                continue;
            if (!out.isEmpty())
                out.append(' ');
            final int visible = Math.min(2, part.length());
            out.append(part, 0, visible).append("*".repeat(part.length() - visible));
        }
        return out.toString();
    }

    /** SQL LIKE araması için %, _ ve \ karakterlerini kaçışlar (ESCAPE '\' ile kullanılır). */
    public static String likePattern(String text) {
        final String escaped = text.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
