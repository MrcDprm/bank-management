package com.mrcdprm.bank.core;

/** Hesap hareketinin türü. Gelen hareketlerde bakiye artar, giden hareketlerde azalır. */
public enum TxType {
    DEPOSIT(true),
    WITHDRAWAL(false),
    TRANSFER_IN(true),
    TRANSFER_OUT(false),
    EXCHANGE_IN(true),
    EXCHANGE_OUT(false),
    INTEREST(true);

    private final boolean incoming;

    TxType(boolean incoming) {
        this.incoming = incoming;
    }

    public boolean incoming() {
        return incoming;
    }
}
