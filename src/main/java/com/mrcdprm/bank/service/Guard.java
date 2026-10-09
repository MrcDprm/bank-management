package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.data.Records.Account;

/** Servis katmanındaki yetki kontrolleri. Yetkisiz istek "forbidden" hatasıyla durur. */
final class Guard {

    private Guard() {
    }

    static void staff(Session session) {
        if (session == null || !session.role().isStaff() || session.mustChangePassword())
            throw new BankException("forbidden");
    }

    static void admin(Session session) {
        if (session == null || !session.isAdmin() || session.mustChangePassword())
            throw new BankException("forbidden");
    }

    static void customer(Session session) {
        if (session == null || !session.isCustomer() || session.mustChangePassword())
            throw new BankException("forbidden");
    }

    /** Personel herkesin, müşteri sadece kendi kaydını görebilir. */
    static void staffOrSelf(Session session, long customerId) {
        if (session == null || session.mustChangePassword())
            throw new BankException("forbidden");
        if (session.isCustomer() && session.userId() != customerId)
            throw new BankException("forbidden");
    }

    /** Müşteri işlemi: hesap bu müşterinin olmalı. */
    static void ownAccount(Session session, Account account) {
        customer(session);
        if (account.customerId() != session.userId())
            throw new BankException("forbidden");
    }
}
