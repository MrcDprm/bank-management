package com.mrcdprm.bank.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sabit demo döviz kurları (1 birim döviz kaç TL). Banka dövizi müşteriden "alış" kuruyla alır,
 * müşteriye "satış" kuruyla satar; aradaki fark bankanın kazancıdır (makas). Canlı kur kullanılmaz.
 */
public final class Rates {

    private Rates() {
    }

    /** Bankanın dövizi müşteriden aldığı kur. */
    public static BigDecimal buy(Currency currency) {
        return switch (currency) {
            case TRY -> BigDecimal.ONE;
            case USD -> new BigDecimal("41.2500");
            case EUR -> new BigDecimal("47.9000");
        };
    }

    /** Bankanın dövizi müşteriye sattığı kur. */
    public static BigDecimal sell(Currency currency) {
        return switch (currency) {
            case TRY -> BigDecimal.ONE;
            case USD -> new BigDecimal("41.8500");
            case EUR -> new BigDecimal("48.6000");
        };
    }

    /**
     * "from" hesabından çıkan tutar karşılığında "to" hesabına geçecek tutar (en küçük birimde).
     * Döviz -> döviz işlemi TL üzerinden yapılır. Küsurat aşağı yuvarlanır; banka fazladan para vermez.
     */
    public static long convert(long amountMinor, Currency from, Currency to) {
        if (from == to)
            return amountMinor;
        final BigDecimal lira = BigDecimal.valueOf(amountMinor).multiply(buy(from));
        return lira.divide(sell(to), 0, RoundingMode.DOWN).longValueExact();
    }

    /** Toplam varlık ve limit hesabı için TL karşılığı (alış kuruyla, kuruş). */
    public static long toTry(long amountMinor, Currency currency) {
        return BigDecimal.valueOf(amountMinor).multiply(buy(currency)).setScale(0, RoundingMode.DOWN).longValueExact();
    }
}
