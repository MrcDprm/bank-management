package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.service.AccountService.Receipt;
import com.mrcdprm.bank.service.CustomerService;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.feather.Feather;

/** Şube ekranlarının diyalogları: yeni müşteri, nakit işlem, iletişim, limit, geçici şifre, hareketler. */
final class StaffDialogs {

    private StaffDialogs() {
    }

    /**
     * "Kaydet"e basınca işi yapar; hata olursa pencere açık kalır ve hata alanın altında görünür.
     * Başarılı olunca sonucu döner.
     */
    private static <T> Dialog<T> form(AppContext ctx, String title, String okText, VBox content, Label error,
                                      Supplier<T> action) {
        final Dialog<T> dialog = new Dialog<>();
        dialog.initOwner(ctx.window());
        dialog.setTitle(title);
        Theme.style(dialog.getDialogPane());
        content.getChildren().add(error);
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        final ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE));
        final Object[] result = new Object[1];
        final Node okButton = dialog.getDialogPane().lookupButton(ok);
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                result[0] = action.get();
            } catch (RuntimeException ex) {
                Ui.showError(error, Ui.message(ex));
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
                e.consume();
            }
        });
        @SuppressWarnings("unchecked")
        final javafx.util.Callback<ButtonType, T> converter = b -> b == ok ? (T) result[0] : null;
        dialog.setResultConverter(converter);
        return dialog;
    }

    static Dialog<CustomerService.Created> newCustomer(AppContext ctx) {
        final TextField tckn = new TextField();
        tckn.textProperty().addListener((obs, old, text) -> {
            if (!text.matches("\\d{0,11}"))
                tckn.setText(old);
        });
        final TextField first = new TextField();
        final TextField last = new TextField();
        final TextField phone = new TextField();
        phone.setPromptText("05XX XXX XX XX");
        final TextField email = new TextField();
        email.setPromptText("ad@ornek.com");
        final HBox names = new HBox(10, Ui.field(I18n.t("field.firstName"), first), Ui.field(I18n.t("field.lastName"), last));
        HBox.setHgrow(names.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(names.getChildren().get(1), Priority.ALWAYS);
        final VBox content = new VBox(10, Ui.field(I18n.t("field.tckn"), tckn), names,
                Ui.field(I18n.t("field.phone"), phone), Ui.field(I18n.t("field.email"), email),
                Ui.muted(I18n.t("customers.newHint")));
        return form(ctx, I18n.t("customers.new"), I18n.t("action.save"), content, Ui.errorLabel(),
                () -> ctx.bank().customers().create(ctx.session(), tckn.getText(), first.getText(), last.getText(),
                        phone.getText(), email.getText()));
    }

    static Dialog<Receipt> cash(AppContext ctx, Account account, boolean deposit) {
        final TextField amount = Ui.amountField();
        final TextField description = new TextField();
        final Label balance = Ui.muted(I18n.t("cash.balance", I18n.money(account.balance(), account.currency())));
        final Label currency = new Label(Views.currencyCode(account.currency()));
        final HBox row = new HBox(8, amount, currency);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(amount, Priority.ALWAYS);
        final VBox content = new VBox(10, new Label(Views.accountName(account) + " · " + Views.iban(account.iban())),
                balance, Ui.field(I18n.t("field.amount"), row), Ui.field(I18n.t("field.description"), description));
        return form(ctx, I18n.t(deposit ? "cash.depositTitle" : "cash.withdrawTitle"),
                I18n.t(deposit ? "cash.deposit" : "cash.withdraw"), content, Ui.errorLabel(), () -> {
                    final OptionalLong value = Ui.amount(amount);
                    if (value.isEmpty())
                        throw new com.mrcdprm.bank.core.BankException("invalidAmount");
                    return deposit
                            ? ctx.bank().accounts().depositCash(ctx.session(), account.id(), value.getAsLong(), description.getText())
                            : ctx.bank().accounts().withdrawCash(ctx.session(), account.id(), value.getAsLong(), description.getText());
                });
    }

    static Dialog<Boolean> contact(AppContext ctx, Customer customer) {
        final TextField phone = new TextField(Validation.formatPhone(customer.phone()));
        final TextField email = new TextField(customer.email());
        final VBox content = new VBox(10, Ui.field(I18n.t("field.phone"), phone), Ui.field(I18n.t("field.email"), email));
        return form(ctx, I18n.t("customers.contact"), I18n.t("action.save"), content, Ui.errorLabel(), () -> {
            ctx.bank().customers().updateContact(ctx.session(), customer.id(), phone.getText(), email.getText());
            return true;
        });
    }

    static Dialog<Boolean> limit(AppContext ctx, Customer customer) {
        final TextField limit = Ui.amountField();
        limit.setText(com.mrcdprm.bank.core.Money.formatNumber(customer.dailyLimit(), I18n.locale()));
        final VBox content = new VBox(10, Ui.field(I18n.t("profile.newLimit"), limit),
                Ui.muted(I18n.t("profile.limitRange", I18n.money(CustomerService.MIN_DAILY_LIMIT, com.mrcdprm.bank.core.Currency.TRY),
                        I18n.money(CustomerService.MAX_DAILY_LIMIT, com.mrcdprm.bank.core.Currency.TRY))));
        return form(ctx, I18n.t("customers.limit"), I18n.t("action.save"), content, Ui.errorLabel(), () -> {
            final OptionalLong value = Ui.amount(limit);
            if (value.isEmpty())
                throw new com.mrcdprm.bank.core.BankException("invalidAmount");
            ctx.bank().customers().setDailyLimit(ctx.session(), customer.id(), value.getAsLong());
            return true;
        });
    }

    /** Geçici şifreyi bir kez gösterir (kopyalanabilir); şifre hiçbir yerde düz metin saklanmaz. */
    static void temporaryPassword(AppContext ctx, String who, String password) {
        final Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(ctx.window());
        dialog.setTitle(I18n.t("temp.title"));
        Theme.style(dialog.getDialogPane());
        final Label value = new Label(password);
        value.getStyleClass().addAll("temp-password", "mono");
        final Button copy = Ui.button(I18n.t("temp.copy"), Feather.COPY, Styles.SMALL);
        copy.setOnAction(e -> {
            Ui.copy(password);
            copy.setText(I18n.t("temp.copied"));
        });
        final HBox row = new HBox(12, value, copy);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        final VBox content = new VBox(12, new Label(I18n.t("temp.intro", who)), row, Ui.muted(I18n.t("temp.hint")));
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(I18n.t("action.ok"), ButtonBar.ButtonData.OK_DONE));
        dialog.showAndWait();
    }

    /** Müşterinin hesap hareketleri ayrı pencerede (hesap özeti PDF'i dahil). */
    static void history(AppContext ctx, Customer customer, List<Account> accounts, Account selected) {
        final Stage stage = new Stage();
        stage.initOwner(ctx.window());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(I18n.t("customers.historyTitle", customer.fullName()));
        stage.getIcons().addAll(((Stage) ctx.window()).getIcons());
        final HistoryView view = new HistoryView(ctx, accounts, selected == null ? null : selected.id(), false);
        view.setPadding(new Insets(16));
        final javafx.scene.Scene scene = new javafx.scene.Scene(view, 1080, 640);
        Theme.addStylesheet(scene);
        stage.setScene(scene);
        // bu pencereden açılan dekont/kaydet pencereleri bunun önünde açılsın
        final javafx.stage.Window main = ctx.window();
        ctx.setWindow(stage);
        stage.showAndWait();
        ctx.setWindow(main);
    }
}
