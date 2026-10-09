package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Interest;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.service.Session;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalLong;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** Vadeli hesaplar: açık mevduatlar (ilerleme çubuğuyla) ve faiz önizlemeli yeni vadeli hesap formu. */
final class DepositsPage extends ScrollPane {

    private final AppContext ctx;
    private final CustomerShell shell;
    private final Session session;

    DepositsPage(AppContext ctx, CustomerShell shell) {
        this.ctx = ctx;
        this.shell = shell;
        this.session = ctx.session();
        final List<Account> all = ctx.bank().accounts().listForCustomer(session, session.userId());
        final List<Account> deposits = all.stream().filter(a -> a.type() == AccountType.TIME_DEPOSIT && a.isActive()).toList();
        final List<Account> sources = all.stream()
                .filter(a -> a.type() == AccountType.CURRENT && a.currency() == Currency.TRY && a.isActive()).toList();

        final FlowPane list = new FlowPane(14, 14);
        for (Account d : deposits)
            list.getChildren().add(depositCard(d));
        if (deposits.isEmpty())
            list.getChildren().add(Ui.muted(I18n.t("deposits.none")));

        final VBox page = new VBox(18, Ui.header(I18n.t("nav.deposits"), I18n.t("deposits.subtitle")),
                newDepositForm(sources), Ui.section(I18n.t("deposits.active")), list);
        page.setPadding(new Insets(4, 4, 20, 0));
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
    }

    private VBox depositCard(Account d) {
        final long interest = Interest.simple(d.principal(), d.annualRate(), d.termDays());
        final LocalDate start = d.maturityDate().minusDays(d.termDays());
        final long passed = ChronoUnit.DAYS.between(start, ctx.bank().today());
        final ProgressBar progress = new ProgressBar(Math.min(1, Math.max(0, passed / (double) d.termDays())));
        progress.setMaxWidth(Double.MAX_VALUE);
        final Label title = new Label(Views.accountName(d) + " · " + percent(d.annualRate()));
        title.getStyleClass().add(Styles.TEXT_BOLD);
        final Label principal = new Label(I18n.money(d.principal(), Currency.TRY));
        principal.getStyleClass().add("card-balance");
        final Label dates = Ui.muted(I18n.t("deposits.dates", I18n.date(start), I18n.date(d.maturityDate()),
                Math.max(0, d.termDays() - passed)));
        final Label expected = new Label(I18n.t("deposits.expected", I18n.money(interest, Currency.TRY),
                I18n.money(d.principal() + interest, Currency.TRY)));
        expected.setWrapText(true);
        final Button breakIt = Ui.button(I18n.t("deposits.break"), Feather.X_CIRCLE, Styles.SMALL, Styles.FLAT, Styles.DANGER);
        breakIt.setOnAction(e -> {
            if (!Ui.confirm(ctx.window(), I18n.t("deposits.breakQuestion", I18n.money(interest, Currency.TRY)),
                    I18n.t("deposits.break")))
                return;
            try {
                new ReceiptDialog(ctx, ctx.bank().accounts().breakTimeDeposit(session, d.id()), true).showAndWait();
                shell.refresh();
            } catch (RuntimeException ex) {
                Ui.error(ctx.window(), ex);
            }
        });
        final VBox card = Ui.card(title, principal, progress, dates, expected, breakIt);
        card.getStyleClass().addAll("account-card", "deposit-card");
        card.setPrefWidth(330);
        return card;
    }

    private VBox newDepositForm(List<Account> sources) {
        final ComboBox<Account> from = Views.accountCombo(sources);
        final TextField amount = Ui.amountField();
        final ToggleGroup terms = new ToggleGroup();
        final HBox termButtons = new HBox(6);
        for (Interest.Term term : Interest.TERMS) {
            final ToggleButton b = new ToggleButton(I18n.t("deposits.term", term.days(), percent(term.annualRate())));
            b.setUserData(term);
            b.setToggleGroup(terms);
            termButtons.getChildren().add(b);
        }
        terms.selectToggle(terms.getToggles().get(0));
        terms.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null)
                terms.selectToggle(old);
        });
        final Label preview = new Label();
        preview.setWrapText(true);
        preview.getStyleClass().add(Styles.TEXT_BOLD);
        final Label error = Ui.errorLabel();

        final Runnable update = () -> {
            final OptionalLong value = Ui.amount(amount);
            final Interest.Term term = (Interest.Term) terms.getSelectedToggle().getUserData();
            if (value.isEmpty()) {
                preview.setText(I18n.t("deposits.minimum", I18n.money(Interest.MIN_PRINCIPAL, Currency.TRY)));
                return;
            }
            final long interest = Interest.simple(value.getAsLong(), term.annualRate(), term.days());
            preview.setText(I18n.t("deposits.preview", I18n.date(ctx.bank().today().plusDays(term.days())),
                    I18n.money(interest, Currency.TRY), I18n.money(value.getAsLong() + interest, Currency.TRY)));
        };
        amount.textProperty().addListener((o, x, y) -> update.run());
        terms.selectedToggleProperty().addListener((o, x, y) -> update.run());
        update.run();

        final Button open = Ui.button(I18n.t("deposits.open"), Feather.TRENDING_UP, Styles.ACCENT);
        open.setOnAction(e -> {
            Ui.showError(error, null);
            final OptionalLong value = Ui.amount(amount);
            if (from.getValue() == null)
                return;
            if (value.isEmpty()) {
                Ui.showError(error, I18n.t("error.invalidAmount"));
                return;
            }
            final Interest.Term term = (Interest.Term) terms.getSelectedToggle().getUserData();
            if (!Ui.confirm(ctx.window(), I18n.t("deposits.question", I18n.money(value.getAsLong(), Currency.TRY),
                    term.days()), I18n.t("deposits.open")))
                return;
            try {
                ctx.bank().accounts().openTimeDeposit(session, from.getValue().id(), value.getAsLong(), term.days());
                Ui.info(ctx.window(), I18n.t("deposits.opened"));
                shell.refresh();
            } catch (RuntimeException ex) {
                Ui.showError(error, Ui.message(ex));
            }
        });
        if (sources.isEmpty()) {
            open.setDisable(true);
            Ui.showError(error, I18n.t("deposits.noSource"));
        }
        final HBox row = new HBox(14, Ui.field(I18n.t("deposits.from"), from), Ui.field(I18n.t("field.amount"), amount));
        HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        final VBox form = Ui.card(Ui.section(I18n.t("deposits.new")), row,
                Ui.field(I18n.t("deposits.termLabel"), termButtons), preview, error, open);
        form.setMaxWidth(720);
        form.setAlignment(Pos.TOP_LEFT);
        return form;
    }

    /** 0.4000 -> "%40" (Türkçe) / "40%" (İngilizce). */
    static String percent(BigDecimal rate) {
        final String n = rate.movePointRight(2).stripTrailingZeros().toPlainString().replace('.', I18n.isTurkish() ? ',' : '.');
        return I18n.isTurkish() ? "%" + n : n + "%";
    }
}
