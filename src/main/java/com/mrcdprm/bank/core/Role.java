package com.mrcdprm.bank.core;

/** Oturum açan kişinin rolü. Yetki kontrolleri servis katmanında bu role göre yapılır. */
public enum Role {
    ADMIN,
    TELLER,
    CUSTOMER;

    public boolean isStaff() {
        return this != CUSTOMER;
    }
}
