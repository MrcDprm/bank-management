package com.mrcdprm.bank.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Vadeli hesap seçenekleri ve basit faiz hesabı. Oranlar demo amaçlı sabittir (yıllık, brüt). */
public final class Interest {

    /** Vade seçeneği: gün sayısı ve yıllık faiz oranı (0.4000 = %40). */
    public record Term(int days, BigDecimal annualRate) {
    }

    public static final List<Term> TERMS = List.of(
            new Term(32, new BigDecimal("0.4000")),
            new Term(92, new BigDecimal("0.3800")),
            new Term(181, new BigDecimal("0.3500")),
            new Term(365, new BigDecimal("0.3200")));

    /** Vadeli hesap için en az tutar: 1.000 TL. */
    public static final long MIN_PRINCIPAL = 100_000;

    private Interest() {
    }

    public static Term term(int days) {
        return TERMS.stream().filter(t -> t.days() == days).findFirst()
                .orElseThrow(() -> new BankException("invalidTerm"));
    }

    /** Basit faiz: anapara x yıllık oran x gün / 365, kuruşa aşağı yuvarlanır. */
    public static long simple(long principal, BigDecimal annualRate, int days) {
        return BigDecimal.valueOf(principal)
                .multiply(annualRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 0, RoundingMode.DOWN)
                .longValueExact();
    }
}
