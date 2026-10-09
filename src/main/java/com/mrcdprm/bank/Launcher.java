package com.mrcdprm.bank;

/**
 * Paketlenmiş jar'ın giriş noktası. Ana sınıf Application'dan türeyince JavaFX modül yolunda
 * aranır ve "JavaFX runtime components are missing" hatası verir; ara sınıf bunu önler.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        BankApp.main(args);
    }
}
