package com.mrcdprm.bank.core;

/** Hesap para birimleri. Tutarlar her zaman en küçük birimde (kuruş, cent) tutulur. */
public enum Currency {
    TRY("₺"),
    USD("$"),
    EUR("€");

    private final String symbol;

    Currency(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
