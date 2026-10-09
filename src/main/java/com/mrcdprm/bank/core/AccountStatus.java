package com.mrcdprm.bank.core;

public enum AccountStatus {
    ACTIVE,
    /** Dondurulmuş: para girmez, çıkmaz. Şube tekrar açabilir. */
    FROZEN,
    /** Kapalı: geri açılmaz, sadece geçmiş hareketleri görünür. */
    CLOSED
}
