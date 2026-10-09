package com.mrcdprm.bank.service;

import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Recipient;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Müşterinin kayıtlı alıcıları (sık para gönderdiği IBAN'lar). */
public final class RecipientService {

    public static final int MAX_RECIPIENTS = 50;

    private final Bank bank;

    RecipientService(Bank bank) {
        this.bank = bank;
    }

    public List<Recipient> list(Session session) {
        Guard.customer(session);
        return bank.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM recipients WHERE customer_id = ? ORDER BY name")) {
                ps.setLong(1, session.userId());
                final List<Recipient> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        out.add(Recipient.from(rs));
                }
                return out;
            }
        });
    }

    /** Takma adla alıcı ekler. Aynı IBAN ikinci kez eklenmez. */
    public void add(Session session, String name, String iban) {
        Guard.customer(session);
        final String label = Validation.description(name);
        if (label.length() < 2 || label.length() > 60)
            throw new BankException("invalidRecipientName");
        if (!Iban.isValid(iban))
            throw new BankException("invalidIban");
        if (!Iban.isOurs(iban))
            throw new BankException("externalIban");
        bank.db().transaction(c -> {
            try (PreparedStatement count = c.prepareStatement("SELECT COUNT(*) FROM recipients WHERE customer_id = ?")) {
                count.setLong(1, session.userId());
                try (ResultSet rs = count.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= MAX_RECIPIENTS)
                        throw new BankException("tooManyRecipients", MAX_RECIPIENTS);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT OR IGNORE INTO recipients(customer_id, name, iban) VALUES (?, ?, ?)")) {
                ps.setLong(1, session.userId());
                ps.setString(2, label);
                ps.setString(3, Iban.normalize(iban));
                if (ps.executeUpdate() == 0)
                    throw new BankException("recipientExists");
            }
            return null;
        });
    }

    public void delete(Session session, long recipientId) {
        Guard.customer(session);
        bank.db().transaction(c -> {
            // customer_id koşulu: başka müşterinin kaydı silinemez
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM recipients WHERE id = ? AND customer_id = ?")) {
                ps.setLong(1, recipientId);
                ps.setLong(2, session.userId());
                return ps.executeUpdate();
            }
        });
    }

    /** Bu IBAN zaten kayıtlı mı? (Transfer sonrası "alıcıyı kaydet" önerisi için.) */
    public boolean contains(Session session, String iban) {
        return list(session).stream().anyMatch(r -> r.iban().equals(Iban.normalize(iban)));
    }
}
