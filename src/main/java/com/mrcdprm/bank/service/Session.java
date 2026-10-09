package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.Role;

/**
 * Oturum açmış kişi. Her servis metodu bunu alır ve yetkiyi kendisi kontrol eder;
 * arayüzde bir düğmeyi gizlemek tek başına güvenlik sayılmaz.
 *
 * @param userId  personelse staff.id, müşteriyse customers.id
 * @param login   kullanıcı adı ya da T.C. kimlik no
 */
public record Session(Role role, long userId, String login, String displayName, boolean mustChangePassword) {

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    Session withPasswordChanged() {
        return new Session(role, userId, login, displayName, false);
    }
}
