package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.data.Records.Recipient;
import com.mrcdprm.bank.service.AccountService.IbanOwner;
import com.mrcdprm.bank.service.Session;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** Kayıtlı alıcılar: liste (gönder / sil) ve takma adla yeni alıcı ekleme. */
final class RecipientsPage extends ScrollPane {

    private final AppContext ctx;
    private final CustomerShell shell;
    private final Session session;

    RecipientsPage(AppContext ctx, CustomerShell shell) {
        this.ctx = ctx;
        this.shell = shell;
        this.session = ctx.session();

        final VBox list = new VBox(0);
        list.getStyleClass().add("panel");
        final List<Recipient> recipients = ctx.bank().recipients().list(session);
        for (Recipient r : recipients)
            list.getChildren().add(row(r));
        if (recipients.isEmpty()) {
            final Label none = Ui.muted(I18n.t("recipients.none"));
            none.setPadding(new Insets(16));
            list.getChildren().add(none);
        }

        final VBox page = new VBox(18, Ui.header(I18n.t("nav.recipients"), I18n.t("recipients.subtitle")),
                addForm(), list);
        page.setPadding(new Insets(4, 4, 20, 0));
        page.setMaxWidth(820);
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
    }

    private HBox row(Recipient r) {
        final Label avatar = new Label(Shell.initials(r.name()));
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(36, 36);
        avatar.setAlignment(Pos.CENTER);
        final Label name = new Label(r.name());
        name.getStyleClass().add(Styles.TEXT_BOLD);
        final Label iban = Ui.muted(Iban.format(r.iban()));
        iban.getStyleClass().add("mono");
        final Button send = Ui.button(I18n.t("overview.send"), Feather.SEND, Styles.SMALL);
        send.setOnAction(e -> shell.sendTo(r.iban()));
        final Button delete = Ui.iconButton(Feather.TRASH_2, I18n.t("action.delete"));
        delete.getStyleClass().add(Styles.DANGER);
        delete.setOnAction(e -> {
            if (Ui.confirm(ctx.window(), I18n.t("recipients.deleteQuestion", r.name()), I18n.t("action.delete"))) {
                ctx.bank().recipients().delete(session, r.id());
                shell.refresh();
            }
        });
        final HBox row = new HBox(12, avatar, new VBox(1, name, iban), Ui.spacer(), send, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        row.setPadding(new Insets(10, 16, 10, 16));
        return row;
    }

    private VBox addForm() {
        final TextField name = new TextField();
        name.setPromptText(I18n.t("transfer.aliasPrompt"));
        final TextField iban = new TextField();
        iban.setPromptText("TR00 0000 0000 0000 0000 0000 00");
        iban.getStyleClass().add("mono");
        final Label owner = new Label();
        owner.getStyleClass().add(Styles.TEXT_SMALL);
        owner.managedProperty().bind(owner.textProperty().isNotEmpty());
        iban.textProperty().addListener((obs, old, text) -> {
            final String n = Iban.normalize(text);
            if (n.length() < Iban.LENGTH) {
                owner.setText("");
                return;
            }
            final Optional<IbanOwner> found = Iban.isOurs(n) ? ctx.bank().accounts().owner(session, n) : Optional.empty();
            owner.setText(found.map(IbanOwner::name).orElse(
                    I18n.t(!Iban.isValid(n) ? "error.invalidIban" : !Iban.isOurs(n) ? "error.externalIban" : "error.ibanNotFound")));
            if (found.isPresent() && name.getText().isBlank())
                name.setPromptText(found.get().name());
        });
        final Label error = Ui.errorLabel();
        final Button add = Ui.button(I18n.t("recipients.add"), Feather.USER_PLUS, Styles.ACCENT);
        add.setOnAction(e -> {
            Ui.showError(error, null);
            final String n = Iban.normalize(iban.getText());
            if (Iban.isOurs(n) && ctx.bank().accounts().owner(session, n).isEmpty()) {
                Ui.showError(error, I18n.t("error.ibanNotFound"));
                return;
            }
            final String label = name.getText().isBlank()
                    ? ctx.bank().accounts().owner(session, n).map(IbanOwner::name).orElse("") : name.getText();
            try {
                ctx.bank().recipients().add(session, label, iban.getText());
                shell.refresh();
            } catch (RuntimeException ex) {
                Ui.showError(error, Ui.message(ex));
            }
        });
        final HBox fields = new HBox(12, Ui.field(I18n.t("recipients.name"), name), Ui.field("IBAN", iban));
        HBox.setHgrow(fields.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(fields.getChildren().get(1), Priority.ALWAYS);
        ((VBox) fields.getChildren().get(1)).getChildren().add(owner);
        final HBox actions = new HBox(add);
        return Ui.card(Ui.section(I18n.t("recipients.new")), fields, error, actions);
    }
}
