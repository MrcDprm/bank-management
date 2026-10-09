package com.mrcdprm.bank.ui;

import com.mrcdprm.bank.data.Database;
import com.mrcdprm.bank.service.Bank;
import com.mrcdprm.bank.service.Session;
import java.util.function.Consumer;
import javafx.stage.Window;

/** Ekranların ortak ihtiyaçları: servisler, ayarlar, oturum ve uygulama düzeyindeki geçişler. */
public final class AppContext {

    public interface Navigator {
        void showLogin();

        void showHome(Session session);

        /** Dil ya da tema değişince mevcut ekranı baştan kurar (oturum korunur). */
        void rebuild();

        void logout();
    }

    private final Bank bank;
    private final Database db;
    private final Settings settings;
    private final Navigator navigator;
    private final Consumer<String> openUrl;
    private Window window;
    private Session session;
    private String page;

    public AppContext(Bank bank, Database db, Settings settings, Navigator navigator, Consumer<String> openUrl) {
        this.bank = bank;
        this.db = db;
        this.settings = settings;
        this.navigator = navigator;
        this.openUrl = openUrl;
    }

    public Bank bank() {
        return bank;
    }

    public Database db() {
        return db;
    }

    public Settings settings() {
        return settings;
    }

    public Navigator nav() {
        return navigator;
    }

    public Window window() {
        return window;
    }

    public void setWindow(Window window) {
        this.window = window;
    }

    public Session session() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /** Son açık sayfa; dil/tema değişince ekran baştan kurulurken aynı sayfaya dönülür. */
    public String page() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public void openUrl(String url) {
        openUrl.accept(url);
    }
}
