package com.mrcdprm.bank.ui;

import atlantafx.base.controls.PasswordTextField;
import com.mrcdprm.bank.service.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Şifre değiştirme. "forced" modunda geçici şifreyle giren kullanıcı içindir: başlık ve açıklama değişir,
 * vazgeçilirse oturum açılmaz. Başarılı olunca yeni oturum nesnesi döner.
 */
public final class PasswordDialog extends Dialog<Session> {

    public PasswordDialog(AppContext ctx, Session session, boolean forced) {
        initOwner(ctx.window());
        setTitle(I18n.t(forced ? "password.forcedTitle" : "password.title"));
        Theme.style(getDialogPane());

        final PasswordTextField current = new PasswordTextField();
        final PasswordTextField next = new PasswordTextField();
        final PasswordTextField repeat = new PasswordTextField();
        final PasswordRulesView rules = new PasswordRulesView(next, new SimpleStringProperty(session.login()));
        final Label error = Ui.errorLabel();

        final VBox content = new VBox(10);
        if (forced)
            content.getChildren().add(Ui.muted(I18n.t("password.forcedIntro")));
        content.getChildren().addAll(
                Ui.field(I18n.t(forced ? "field.temporaryPassword" : "field.currentPassword"), current),
                Ui.field(I18n.t("field.newPassword"), next), rules,
                Ui.field(I18n.t("field.passwordRepeat"), repeat), error);
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(380);
        getDialogPane().setContent(content);

        final ButtonType save = new ButtonType(I18n.t("action.save"), ButtonBar.ButtonData.OK_DONE);
        final ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(save, cancel);

        final Session[] result = new Session[1];
        final Node saveButton = getDialogPane().lookupButton(save);
        // Hata olursa pencere kapanmasın: olayı tüketip hatayı gösteriyoruz
        saveButton.addEventFilter(ActionEvent.ACTION, e -> {
            if (!next.getPassword().equals(repeat.getPassword())) {
                Ui.showError(error, I18n.t("error.passwordMismatch"));
                e.consume();
                return;
            }
            try {
                result[0] = ctx.bank().auth().changePassword(session, current.getPassword(), next.getPassword());
            } catch (RuntimeException ex) {
                Ui.showError(error, Ui.message(ex));
                getDialogPane().getScene().getWindow().sizeToScene();
                e.consume();
            }
        });
        setResultConverter(button -> button == save ? result[0] : null);
    }
}
