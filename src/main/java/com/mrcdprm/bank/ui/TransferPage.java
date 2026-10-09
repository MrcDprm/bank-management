package com.mrcdprm.bank.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.data.Records.Recipient;
import com.mrcdprm.bank.service.AccountService;
import com.mrcdprm.bank.service.AccountService.IbanOwner;
import com.mrcdprm.bank.service.Session;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Para transferi (IBAN ile, banka içi). IBAN yazıldıkça alıcı adı maskeli gösterilir; böylece yanlış kişiye
 * gönderme riski azalır. Gönder'e basınca özet onay penceresi açılır, büyük tutarlarda şifre istenir.
 */
final class TransferPage extends ScrollPane {

    private final AppContext ctx;
    private final CustomerShell shell;
    private final Session session;
    private final ComboBox<Account> from;
    private final TextField iban = new TextField();
    private final Label owner = new Label();
    private final TextField amount = Ui.amountField();
    private final Label currencyLabel = new Label();
    private final TextField description = new TextField();
    private final ComboBox<Category> category = new ComboBox<>(FXCollections.observableArrayList(Category.selectable()));
    private final CheckBox saveRecipient = new CheckBox(I18n.t("transfer.saveRecipient"));
    private final TextField alias = new TextField();
    private final Label error = Ui.errorLabel();
    private final Label limitInfo = Ui.muted("");
    private IbanOwner target;

    TransferPage(AppContext ctx, CustomerShell shell) {
        this.ctx = ctx;
        this.shell = shell;
        this.session = ctx.session();
        final List<Account> accounts = ctx.bank().accounts().listForCustomer(session, session.userId()).stream()
                .filter(a -> a.type() == AccountType.CURRENT && a.isActive()).toList();
        from = Views.accountCombo(accounts);
        final Long pre = shell.takeAccount();
        accounts.stream().filter(a -> pre != null && a.id() == pre).findFirst().ifPresent(from.getSelectionModel()::select);

        iban.setPromptText("TR00 0000 0000 0000 0000 0000 00");
        iban.getStyleClass().add("mono");
        iban.textProperty().addListener((obs, old, text) -> lookupOwner());
        owner.getStyleClass().add(Styles.TEXT_SMALL);
        owner.setWrapText(true);
        owner.managedProperty().bind(owner.textProperty().isNotEmpty());
        final MenuButton pick = new MenuButton(null, new FontIcon(Feather.USERS));
        pick.setAccessibleText(I18n.t("transfer.pick"));
        pick.setTooltip(new javafx.scene.control.Tooltip(I18n.t("transfer.pick")));
        fillPicker(pick, accounts);
        final HBox ibanRow = new HBox(6, iban, pick);
        HBox.setHgrow(iban, Priority.ALWAYS);

        description.setPromptText(I18n.t("transfer.descriptionPrompt"));
        description.textProperty().addListener((obs, old, text) -> {
            if (text.length() > Validation.DESCRIPTION_MAX)
                description.setText(old);
        });
        category.setConverter(enumConverter());
        category.setValue(Category.TRANSFER);
        category.setMaxWidth(Double.MAX_VALUE);

        from.valueProperty().addListener((obs, old, a) -> updateCurrency());
        updateCurrency();
        final HBox amountRow = new HBox(8, amount, currencyLabel);
        amountRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(amount, Priority.ALWAYS);

        final Button send = Ui.button(I18n.t("transfer.continue"), Feather.ARROW_RIGHT, Styles.ACCENT);
        send.setDefaultButton(true);
        send.setOnAction(e -> review());
        if (accounts.isEmpty()) {
            send.setDisable(true);
            Ui.showError(error, I18n.t("transfer.noAccount"));
        }

        final String preIban = shell.takeIban();
        if (preIban != null)
            iban.setText(Iban.format(preIban));

        final GridPane two = new GridPane();
        two.setHgap(14);
        two.add(Ui.field(I18n.t("field.amount"), amountRow), 0, 0);
        two.add(Ui.field(I18n.t("field.category"), category), 1, 0);
        GridPane.setHgrow(two.getChildren().get(0), Priority.ALWAYS);
        two.getColumnConstraints().addAll(percent(55), percent(45));

        final VBox form = Ui.card(
                Ui.field(I18n.t("transfer.from"), from),
                Ui.field(I18n.t("transfer.toIban"), ibanRow), owner,
                two,
                Ui.field(I18n.t("field.description"), description),
                saveRecipient, alias, error, send);
        form.setMaxWidth(560);
        saveRecipient.managedProperty().bind(saveRecipient.visibleProperty());
        saveRecipient.setVisible(false);
        alias.setPromptText(I18n.t("transfer.aliasPrompt"));
        alias.visibleProperty().bind(saveRecipient.visibleProperty().and(saveRecipient.selectedProperty()));
        alias.managedProperty().bind(alias.visibleProperty());

        final Customer me = ctx.bank().customers().get(session, session.userId());
        final long used = ctx.bank().accounts().usedToday(session, session.userId());
        limitInfo.setText(I18n.t("transfer.limit", I18n.money(Math.max(0, me.dailyLimit() - used), Currency.TRY),
                I18n.money(me.dailyLimit(), Currency.TRY)));
        final VBox info = Ui.card(Ui.section(I18n.t("transfer.infoTitle")), limitInfo,
                Ui.muted(I18n.t("transfer.infoConfirm", I18n.money(AccountService.CONFIRM_THRESHOLD, Currency.TRY))),
                Ui.muted(I18n.t("transfer.infoInternal")),
                Ui.muted(I18n.t("transfer.infoOwn")));
        info.setMaxWidth(320);
        info.setMinWidth(260);

        final HBox columns = new HBox(18, form, info);
        HBox.setHgrow(form, Priority.ALWAYS);
        final VBox page = new VBox(18, Ui.header(I18n.t("nav.transfer"), I18n.t("transfer.subtitle")), columns);
        page.setPadding(new Insets(4, 4, 20, 0));
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
    }

    private void fillPicker(MenuButton pick, List<Account> own) {
        final List<Recipient> saved = ctx.bank().recipients().list(session);
        for (Recipient r : saved) {
            final MenuItem item = new MenuItem(r.name() + "  ·  " + Views.lastDigits(r.iban()));
            item.setOnAction(e -> iban.setText(Iban.format(r.iban())));
            pick.getItems().add(item);
        }
        if (!saved.isEmpty() && own.size() > 1)
            pick.getItems().add(new SeparatorMenuItem());
        for (Account a : own) {
            final MenuItem item = new MenuItem(I18n.t("transfer.ownAccount", Views.accountName(a), Views.lastDigits(a.iban())));
            item.setOnAction(e -> iban.setText(Iban.format(a.iban())));
            pick.getItems().add(item);
        }
        if (pick.getItems().isEmpty()) {
            final MenuItem none = new MenuItem(I18n.t("transfer.noRecipients"));
            none.setDisable(true);
            pick.getItems().add(none);
        }
    }

    private void updateCurrency() {
        final Account a = from.getValue();
        currencyLabel.setText(a == null ? "" : Views.currencyCode(a.currency()));
        lookupOwner();
    }

    /** IBAN tamamlanınca alıcıyı bulur; geçersiz/başka banka/bulunamadı durumlarını hemen söyler. */
    private void lookupOwner() {
        target = null;
        owner.getStyleClass().removeAll("owner-ok", Styles.DANGER);
        saveRecipient.setVisible(false);
        final String text = Iban.normalize(iban.getText());
        if (text.length() < Iban.LENGTH) {
            owner.setText("");
            owner.setGraphic(null);
            return;
        }
        String problem = null;
        if (!Iban.isValid(text))
            problem = I18n.t("error.invalidIban");
        else if (!Iban.isOurs(text))
            problem = I18n.t("error.externalIban");
        else {
            final Optional<IbanOwner> found = ctx.bank().accounts().owner(session, text);
            if (found.isEmpty())
                problem = I18n.t("error.ibanNotFound");
            else if (!found.get().active())
                problem = I18n.t("error.targetNotActive");
            else if (from.getValue() != null && found.get().currency() != from.getValue().currency())
                problem = I18n.t("error.currencyMismatch");
            else if (found.get().type() != AccountType.CURRENT)
                problem = I18n.t("error.notCurrentAccount");
            else if (from.getValue() != null && Iban.normalize(from.getValue().iban()).equals(text))
                problem = I18n.t("error.sameAccount");
            else
                target = found.get();
        }
        if (problem != null) {
            owner.setText(problem);
            owner.setGraphic(new FontIcon(Feather.ALERT_CIRCLE));
            owner.getStyleClass().add(Styles.DANGER);
            return;
        }
        owner.setText(target.own() ? I18n.t("transfer.ownTarget", target.name()) : target.name());
        owner.setGraphic(new FontIcon(Feather.CHECK_CIRCLE));
        owner.getStyleClass().add("owner-ok");
        category.setDisable(target.own());
        saveRecipient.setVisible(!target.own() && !ctx.bank().recipients().contains(session, text));
    }

    /** Onay penceresi: özet, gerekiyorsa şifre. Onaylanınca transfer arka planda yapılır. */
    private void review() {
        Ui.showError(error, null);
        final Account source = from.getValue();
        final OptionalLong value = Ui.amount(amount);
        if (source == null)
            return;
        if (target == null) {
            Ui.showError(error, owner.getText().isEmpty() ? I18n.t("transfer.enterIban") : owner.getText());
            return;
        }
        if (value.isEmpty()) {
            Ui.showError(error, I18n.t("error.invalidAmount"));
            return;
        }
        if (value.getAsLong() > source.balance()) {
            Ui.showError(error, I18n.t("error.insufficientFunds"));
            return;
        }
        final boolean needsPassword = !target.own()
                && Rates.toTry(value.getAsLong(), source.currency()) >= AccountService.CONFIRM_THRESHOLD;

        final Dialog<String> confirm = new Dialog<>();
        confirm.initOwner(ctx.window());
        confirm.setTitle(I18n.t("transfer.confirmTitle"));
        Theme.style(confirm.getDialogPane());
        final Label big = new Label(I18n.money(value.getAsLong(), source.currency()));
        big.getStyleClass().add(Styles.TITLE_2);
        final GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(8);
        grid.addRow(0, Ui.muted(I18n.t("transfer.from")), new Label(Views.accountName(source) + " · " + Views.lastDigits(source.iban())));
        grid.addRow(1, Ui.muted(I18n.t("pdf.recipient")), new Label(target.name() + "\n" + Iban.format(target.iban())));
        if (!description.getText().isBlank())
            grid.addRow(2, Ui.muted(I18n.t("field.description")), new Label(Validation.description(description.getText())));
        final VBox content = new VBox(14, big, grid);
        final PasswordTextField password = new PasswordTextField();
        if (needsPassword) {
            content.getChildren().addAll(Ui.muted(I18n.t("transfer.passwordNeeded")),
                    Ui.field(I18n.t("field.password"), password));
        }
        content.setPadding(new Insets(6));
        content.setPrefWidth(420);
        confirm.getDialogPane().setContent(content);
        final ButtonType ok = new ButtonType(I18n.t("transfer.send"), ButtonBar.ButtonData.OK_DONE);
        confirm.getDialogPane().getButtonTypes().addAll(ok,
                new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        confirm.setResultConverter(b -> b == ok ? password.getPassword() : null);
        if (needsPassword)
            javafx.application.Platform.runLater(password::requestFocus);

        confirm.showAndWait().ifPresent(pass -> {
            final String toIban = target.iban();
            final String text = description.getText();
            final Category cat = category.getValue();
            final boolean save = saveRecipient.isVisible() && saveRecipient.isSelected();
            final String recipientName = alias.getText().isBlank() ? target.name() : alias.getText();
            Ui.background(this, () -> ctx.bank().accounts().transfer(session, source.id(), toIban, value.getAsLong(),
                    text, cat, needsPassword ? pass : null), receipt -> {
                        if (save) {
                            try {
                                ctx.bank().recipients().add(session, recipientName, toIban);
                            } catch (RuntimeException ex) {
                                Ui.error(ctx.window(), ex);
                            }
                        }
                        new ReceiptDialog(ctx, receipt, true).showAndWait();
                        shell.refresh();
                    }, ex -> Ui.showError(error, Ui.message(ex)));
        });
    }

    private static javafx.scene.layout.ColumnConstraints percent(double p) {
        final javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
        c.setPercentWidth(p);
        return c;
    }

    static <E extends Enum<E>> StringConverter<E> enumConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(E value) {
                return value == null ? "" : I18n.of(value);
            }

            @Override
            public E fromString(String s) {
                return null;
            }
        };
    }
}
