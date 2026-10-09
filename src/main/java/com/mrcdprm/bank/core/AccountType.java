package com.mrcdprm.bank.core;

public enum AccountType {
    /** Vadesiz hesap: para yatırma, çekme, transfer. */
    CURRENT,
    /** Vadeli hesap: vade sonuna kadar para çıkmaz, vade sonunda faiziyle bağlı hesaba döner. */
    TIME_DEPOSIT
}
