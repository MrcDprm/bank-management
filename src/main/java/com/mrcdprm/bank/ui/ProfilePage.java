package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.service.CustomerService;
import com.mrcdprm.bank.service.Session;
import java.util.OptionalLong;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** Profil: kişisel bilgiler, iletişim bilgisi güncelleme, günlük transfer limiti ve şifre. */
final class ProfilePage extends ScrollPane {

    ProfilePage(AppContext ctx) {
        final Session session = ctx.session();
        final Customer me = ctx.bank().customers().get(session, session.userId());

        final GridPane info = new GridPane();
        info.setHgap(24);
        info.setVgap(8);
        info.addRow(0, Ui.muted(I18n.t("field.fullName")), new Label(me.fullName()));
        info.addRow(1, Ui.muted(I18n.t("field.tckn")), new Label(maskTckn(me.tckn())));
        info.addRow(2, Ui.muted(I18n.t("profile.customerSince")), new Label(I18n.date(me.createdAt().toLocalDate())));
        final VBox personal = Ui.card(Ui.section(I18n.t("profile.personal")), info,
                Ui.muted(I18n.t("profile.personalHint")));

        // İletişim
        final TextField phone = new TextField(Validation.formatPhone(me.phone()));
        final TextField email = new TextField(me.email());
        final Label contactError = Ui.errorLabel();
        final Button saveContact = Ui.button(I18n.t("action.save"), Feather.SAVE);
        saveContact.setOnAction(e -> {
            Ui.showError(contactError, null);
            try {
                ctx.bank().customers().updateContact(session, me.id(), phone.getText(), email.getText());
                Ui.info(ctx.window(), I18n.t("profile.saved"));
            } catch (RuntimeException ex) {
                Ui.showError(contactError, Ui.message(ex));
            }
        });
        final VBox contact = Ui.card(Ui.section(I18n.t("profile.contact")), Ui.field(I18n.t("field.phone"), phone),
                Ui.field(I18n.t("field.email"), email), contactError, new HBox(saveContact));

        // Limit
        final long used = ctx.bank().accounts().usedToday(session, me.id());
        final Label current = new Label(I18n.t("profile.limitNow", I18n.money(me.dailyLimit(), Currency.TRY),
                I18n.money(used, Currency.TRY)));
        current.setWrapText(true);
        final TextField limit = Ui.amountField();
        final Label limitError = Ui.errorLabel();
        final Button saveLimit = Ui.button(I18n.t("profile.updateLimit"), Feather.SLIDERS);
        saveLimit.setOnAction(e -> {
            Ui.showError(limitError, null);
            final OptionalLong value = Ui.amount(limit);
            if (value.isEmpty()) {
                Ui.showError(limitError, I18n.t("error.invalidAmount"));
                return;
            }
            try {
                ctx.bank().customers().setDailyLimit(session, me.id(), value.getAsLong());
                Ui.info(ctx.window(), I18n.t("profile.limitSaved"));
                ctx.nav().rebuild();
            } catch (RuntimeException ex) {
                Ui.showError(limitError, Ui.message(ex));
            }
        });
        final VBox limits = Ui.card(Ui.section(I18n.t("profile.limit")), current,
                Ui.muted(I18n.t("profile.limitRange", I18n.money(CustomerService.MIN_DAILY_LIMIT, Currency.TRY),
                        I18n.money(CustomerService.MAX_DAILY_LIMIT, Currency.TRY))),
                Ui.field(I18n.t("profile.newLimit"), limit), limitError, new HBox(saveLimit));

        // Güvenlik
        final Button password = Ui.button(I18n.t("shell.changePassword"), Feather.KEY, Styles.ACCENT);
        password.setOnAction(e -> new PasswordDialog(ctx, session, false).showAndWait()
                .ifPresent(s -> Ui.info(ctx.window(), I18n.t("password.changed"))));
        final VBox security = Ui.card(Ui.section(I18n.t("profile.security")), Ui.muted(I18n.t("profile.securityHint")),
                new HBox(password));

        final GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        grid.add(personal, 0, 0);
        grid.add(contact, 1, 0);
        grid.add(limits, 0, 1);
        grid.add(security, 1, 1);
        final javafx.scene.layout.ColumnConstraints half = new javafx.scene.layout.ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);
        grid.setMaxWidth(980);

        final VBox page = new VBox(18, Ui.header(I18n.t("nav.profile"), null), grid);
        page.setPadding(new Insets(4, 4, 20, 0));
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
    }

    /** "12345678950" -> "123******50": kimlik no ekranda tam gösterilmez. */
    static String maskTckn(String tckn) {
        return tckn.substring(0, 3) + "******" + tckn.substring(9);
    }
}
