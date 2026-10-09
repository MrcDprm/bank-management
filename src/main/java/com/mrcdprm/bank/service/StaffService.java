package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.PasswordHasher;
import com.mrcdprm.bank.core.PasswordPolicy;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.StaffUser;
import com.mrcdprm.bank.data.Records;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Şube personeli yönetimi. Hepsi sadece yöneticiye açık. */
public final class StaffService {

    private final Bank bank;

    StaffService(Bank bank) {
        this.bank = bank;
    }

    public List<StaffUser> list(Session session) {
        Guard.admin(session);
        return bank.db().read(c -> {
            final List<StaffUser> out = new ArrayList<>();
            try (ResultSet rs = c.createStatement().executeQuery("SELECT * FROM staff ORDER BY active DESC, full_name")) {
                while (rs.next())
                    out.add(StaffUser.from(rs));
            }
            return out;
        });
    }

    /** Yeni personel; geçici şifre döner, ilk girişte değiştirilir. */
    public String create(Session session, String username, String fullName, Role role) {
        Guard.admin(session);
        final String user = Validation.username(username).orElseThrow(() -> new BankException("invalidUsername"));
        final String name = Validation.name(fullName).orElseThrow(() -> new BankException("invalidName"));
        if (role == null || !role.isStaff())
            throw new BankException("forbidden");
        final String temporary = PasswordPolicy.temporary(bank.random());
        final String hash = PasswordHasher.hash(temporary);
        bank.db().transaction(c -> {
            try (PreparedStatement check = c.prepareStatement("SELECT 1 FROM staff WHERE username = ?")) {
                check.setString(1, user);
                if (check.executeQuery().next())
                    throw new BankException("usernameTaken");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO staff(username, full_name, role, password_hash, must_change, created_at)"
                            + " VALUES (?, ?, ?, ?, 1, ?)")) {
                ps.setString(1, user);
                ps.setString(2, name);
                ps.setString(3, role.name());
                ps.setString(4, hash);
                ps.setString(5, Records.ts(bank.now()));
                return ps.executeUpdate();
            }
        });
        return temporary;
    }

    /** Geçici şifre verir, kilidi açar. */
    public String resetPassword(Session session, long staffId) {
        Guard.admin(session);
        final String temporary = PasswordPolicy.temporary(bank.random());
        final String hash = PasswordHasher.hash(temporary);
        update(staffId, "UPDATE staff SET password_hash = ?, must_change = 1, failed_attempts = 0, locked_until = NULL"
                + " WHERE id = ?", hash);
        return temporary;
    }

    /** Yönetici kendini pasif yapamaz ve rolünü düşüremez; aksi hâlde sistemde yönetici kalmayabilir. */
    public void setActive(Session session, long staffId, boolean active) {
        Guard.admin(session);
        if (staffId == session.userId() && !active)
            throw new BankException("cannotChangeSelf");
        update(staffId, "UPDATE staff SET active = ? WHERE id = ?", active ? 1 : 0);
    }

    public void setRole(Session session, long staffId, Role role) {
        Guard.admin(session);
        if (role == null || !role.isStaff())
            throw new BankException("forbidden");
        if (staffId == session.userId() && role != Role.ADMIN)
            throw new BankException("cannotChangeSelf");
        update(staffId, "UPDATE staff SET role = ? WHERE id = ?", role.name());
    }

    private void update(long staffId, String sql, Object value) {
        final int changed = bank.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, value);
                ps.setLong(2, staffId);
                return ps.executeUpdate();
            }
        });
        if (changed == 0)
            throw new BankException("notFound");
    }
}
