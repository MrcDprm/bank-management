package com.mrcdprm.bank.ui;

import atlantafx.base.controls.PasswordTextField;
import com.mrcdprm.bank.core.PasswordPolicy;
import java.util.List;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Şifre yazılırken kuralları canlı gösterir: sağlanan kural yeşil tik, sağlanmayan gri daire.
 * Gerçek şifre passwordProperty()'den okunur; PasswordTextField'ın text'i maske karakterleridir (•••),
 * onunla büyük harf / küçük harf / rakam kuralları hiç sağlanmış görünmezdi.
 */
public final class PasswordRulesView extends VBox {

    private static final List<String> RULES = List.of("length", "upper", "lower", "digit", "containsLogin");

    /** @param login kullanıcı adı ya da kimlik no; değişince (ör. kurulum ekranında) kurallar da yenilenir */
    public PasswordRulesView(PasswordTextField password, ObservableStringValue login) {
        super(3);
        getStyleClass().add("password-rules");
        final Runnable update = () -> {
            getChildren().clear();
            final String value = password.getPassword();
            final List<String> failing = PasswordPolicy.violations(value, login.get());
            for (String rule : RULES) {
                final boolean ok = !failing.contains(rule) && !value.isEmpty();
                final Label label = new Label(I18n.t("password.rule." + rule),
                        new FontIcon(ok ? Feather.CHECK_CIRCLE : Feather.CIRCLE));
                label.getStyleClass().add(ok ? "rule-ok" : "rule-pending");
                getChildren().add(label);
            }
        };
        password.passwordProperty().addListener((obs, old, text) -> update.run());
        login.addListener((obs, old, text) -> update.run());
        update.run();
    }
}
