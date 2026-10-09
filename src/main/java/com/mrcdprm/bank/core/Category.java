package com.mrcdprm.bank.core;

import java.util.List;

/** Harcama analizi için hareket kategorisi. Müşteri sonradan değiştirebilir. */
public enum Category {
    SALARY,
    RENT,
    BILLS,
    GROCERIES,
    SHOPPING,
    DINING,
    TRANSPORT,
    HEALTH,
    EDUCATION,
    ENTERTAINMENT,
    CASH,
    TRANSFER,
    SAVINGS,
    EXCHANGE,
    INTEREST,
    OTHER;

    /** Müşterinin bir harekete elle verebileceği kategoriler (sistemin koyduğu tasarruf/döviz/faiz hariç). */
    public static List<Category> selectable() {
        return List.of(SALARY, RENT, BILLS, GROCERIES, SHOPPING, DINING, TRANSPORT, HEALTH, EDUCATION,
                ENTERTAINMENT, CASH, TRANSFER, OTHER);
    }
}
