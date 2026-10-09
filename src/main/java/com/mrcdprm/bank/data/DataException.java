package com.mrcdprm.bank.data;

import java.sql.SQLException;

/** Beklenmeyen veritabanı hatası. Arayüz ayrıntısını göstermez, genel bir mesaj verir. */
public final class DataException extends RuntimeException {

    public DataException(SQLException cause) {
        super(cause.getMessage(), cause);
    }
}
