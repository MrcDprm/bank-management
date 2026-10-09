package com.mrcdprm.bank.core;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Güçlü şifre kuralları ve geçici şifre üretimi. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    /** Karışabilecek karakterler (0/O, 1/l/I) geçici şifrede yok; şifre müşteriye kâğıtla verilir. */
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*?";

    private PasswordPolicy() {
    }

    /**
     * Şifrenin uymadığı kuralların kodları; boş liste şifrenin geçerli olduğunu gösterir.
     * Kodlar arayüzde "password.rule.<kod>" anahtarıyla metne çevrilir.
     */
    public static List<String> violations(String password, String loginName) {
        final String p = password == null ? "" : password;
        final List<String> rules = new ArrayList<>();
        if (p.length() < MIN_LENGTH)
            rules.add("length");
        if (p.length() > MAX_LENGTH)
            rules.add("tooLong");
        if (p.chars().noneMatch(Character::isUpperCase))
            rules.add("upper");
        if (p.chars().noneMatch(Character::isLowerCase))
            rules.add("lower");
        if (p.chars().noneMatch(Character::isDigit))
            rules.add("digit");
        if (loginName != null && loginName.strip().length() >= 3
                && p.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT)))
            rules.add("containsLogin");
        return rules;
    }

    /** 12 karakterlik, her kuralı sağlayan geçici şifre. */
    public static String temporary(SecureRandom random) {
        final String all = UPPER + LOWER + DIGITS + SYMBOLS;
        final List<Character> chars = new ArrayList<>();
        chars.add(UPPER.charAt(random.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(random.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        chars.add(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        while (chars.size() < 12)
            chars.add(all.charAt(random.nextInt(all.length())));
        Collections.shuffle(chars, random);
        final StringBuilder out = new StringBuilder();
        chars.forEach(out::append);
        return out.toString();
    }
}
