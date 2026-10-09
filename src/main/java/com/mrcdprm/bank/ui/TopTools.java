package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Sağ üstteki dil, tema ve Hakkında düğmeleri. Her ekranda aynı yerde durur. */
public final class TopTools extends HBox {

    public TopTools(AppContext ctx) {
        super(4);
        setAlignment(Pos.CENTER_RIGHT);

        final Button language = new Button(I18n.isTurkish() ? "EN" : "TR");
        language.getStyleClass().addAll(Styles.FLAT, "lang-button");
        language.setAccessibleText(I18n.t("tools.language"));
        language.setTooltip(new javafx.scene.control.Tooltip(I18n.t("tools.language")));
        language.setOnAction(e -> {
            final String next = I18n.isTurkish() ? "en" : "tr";
            ctx.settings().setLanguage(next);
            I18n.setLocale(Locale.forLanguageTag(next));
            ctx.nav().rebuild();
        });

        final boolean dark = ctx.settings().darkTheme();
        final Button theme = Ui.iconButton(dark ? Feather.SUN : Feather.MOON,
                I18n.t(dark ? "tools.lightTheme" : "tools.darkTheme"));
        theme.setOnAction(e -> {
            ctx.settings().setDarkTheme(!dark);
            ctx.nav().rebuild();
        });

        final Button about = Ui.iconButton(Feather.INFO, I18n.t("about.title"));
        about.setOnAction(e -> new AboutDialog(ctx).showAndWait());

        getChildren().addAll(language, theme, about);
        language.setGraphic(new FontIcon(Feather.GLOBE));
    }
}
