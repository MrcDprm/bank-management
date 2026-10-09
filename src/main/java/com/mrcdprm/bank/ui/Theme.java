package com.mrcdprm.bank.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/** AtlantaFX Primer teması (açık/koyu) ve uygulamanın kendi stil dosyası. */
public final class Theme {

    private static final String CSS = Theme.class.getResource("/com/mrcdprm/bank/app.css").toExternalForm();

    private Theme() {
    }

    public static void apply(boolean dark) {
        Application.setUserAgentStylesheet(dark ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    public static void addStylesheet(Scene scene) {
        scene.getStylesheets().add(CSS);
    }

    /** Kendi diyaloglarımızda da aynı sınıflar (panel, field-label vb.) çalışsın. */
    public static void style(DialogPane pane) {
        pane.getStylesheets().add(CSS);
    }
}
