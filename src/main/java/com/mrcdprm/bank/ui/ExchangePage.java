package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.service.Session;
import java.math.BigDecimal;
import java.util.List;
import java.util.OptionalLong;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/**
 * Döviz alım-satımı: müşterinin kendi iki hesabı arasında, sabit demo kurlarıyla.
 * Tutar yazıldıkça karşı hesaba geçecek tutar hesaplanır; dövizi olmayan müşteri buradan hesap açabilir.
 */
final class ExchangePage extends ScrollPane {

    private final AppContext ctx;
    private final CustomerShell shell;
    private final Session session;
    private final Label preview = new Label();
    private final Label error = Ui.errorLabel();

    ExchangePage(AppContext ctx, CustomerShell shell) {
        this.ctx = ctx;
        this.shell = shell;
        this.session = ctx.session();
        final List<Account> accounts = ctx.bank().accounts().listForCustomer(session, session.userId()).stream()
                .filter(a -> a.type() == AccountType.CURRENT && a.isActive()).toList();

        final ComboBox<Account> from = Views.accountCombo(accounts);
        final ComboBox<Account> to = Views.accountCombo(accounts);
        accounts.stream().filter(a -> a.currency() != Currency.TRY).findFirst().ifPresent(to.getSelectionModel()::select);
        final TextField amount = Ui.amountField();
        final Label currency = new Label();
        final HBox amountRow = new HBox(8, amount, currency);
        amountRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(amount, Priority.ALWAYS);
        preview.getStyleClass().add(Styles.TEXT_BOLD);
        preview.setWrapText(true);

        final Runnable update = () -> {
            final Account a = from.getValue();
            final Account b = to.getValue();
            currency.setText(a == null ? "" : Views.currencyCode(a.currency()));
            final OptionalLong value = Ui.amount(amount);
            final boolean same = a != null && b != null && a.currency() == b.currency();
            preview.getStyleClass().remove(Styles.DANGER);
            if (same)
                preview.getStyleClass().add(Styles.DANGER);
            if (a == null || b == null || same) {
                preview.setText(same ? I18n.t("error.sameCurrency") : "");
                return;
            }
            preview.setText(value.isEmpty() ? ""
                    : I18n.t("exchange.preview", I18n.money(Rates.convert(value.getAsLong(), a.currency(), b.currency()),
                            b.currency())));
        };
        from.valueProperty().addListener((o, x, y) -> update.run());
        to.valueProperty().addListener((o, x, y) -> update.run());
        amount.textProperty().addListener((o, x, y) -> update.run());
        update.run();

        final Button swap = Ui.iconButton(Feather.REPEAT, I18n.t("exchange.swap"));
        swap.setOnAction(e -> {
            final Account a = from.getValue();
            from.setValue(to.getValue());
            to.setValue(a);
        });

        final Button confirm = Ui.button(I18n.t("exchange.confirm"), Feather.CHECK, Styles.ACCENT);
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> {
            Ui.showError(error, null);
            final OptionalLong value = Ui.amount(amount);
            if (from.getValue() == null || to.getValue() == null)
                return;
            if (value.isEmpty()) {
                Ui.showError(error, I18n.t("error.invalidAmount"));
                return;
            }
            final Account a = from.getValue();
            final Account b = to.getValue();
            final String question = I18n.t("exchange.question", I18n.money(value.getAsLong(), a.currency()),
                    I18n.money(Rates.convert(value.getAsLong(), a.currency(), b.currency()), b.currency()));
            if (!Ui.confirm(ctx.window(), question, I18n.t("exchange.confirm")))
                return;
            try {
                final var receipt = ctx.bank().accounts().exchange(session, a.id(), b.id(), value.getAsLong());
                new ReceiptDialog(ctx, receipt, true).showAndWait();
                shell.refresh();
            } catch (RuntimeException ex) {
                Ui.showError(error, Ui.message(ex));
            }
        });

        final HBox swapRow = new HBox(swap);
        swapRow.setAlignment(Pos.CENTER);
        final VBox form = Ui.card(Ui.field(I18n.t("exchange.from"), from), swapRow, Ui.field(I18n.t("exchange.to"), to),
                Ui.field(I18n.t("field.amount"), amountRow), preview, error, confirm);
        form.setMaxWidth(600);
        HBox.setHgrow(form, Priority.ALWAYS);

        final VBox side = new VBox(14, ratesCard(), openCard(accounts));
        side.setMinWidth(280);
        side.setMaxWidth(320);
        final HBox columns = new HBox(18, form, side);
        final VBox page = new VBox(18, Ui.header(I18n.t("nav.exchange"), I18n.t("exchange.subtitle")), columns);
        page.setPadding(new Insets(4, 4, 20, 0));
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
    }

    private VBox ratesCard() {
        final GridPane grid = new GridPane();
        grid.setHgap(22);
        grid.setVgap(8);
        grid.addRow(0, new Label(), Ui.muted(I18n.t("exchange.buy")), Ui.muted(I18n.t("exchange.sell")));
        int row = 1;
        for (Currency c : List.of(Currency.USD, Currency.EUR)) {
            final Label code = new Label(c.name() + " " + c.symbol());
            code.getStyleClass().add(Styles.TEXT_BOLD);
            grid.addRow(row++, code, rate(Rates.buy(c)), rate(Rates.sell(c)));
        }
        for (javafx.scene.Node n : grid.getChildren())
            GridPane.setHalignment(n, HPos.RIGHT);
        final Label note = Ui.muted(I18n.t("exchange.ratesNote"));
        note.getStyleClass().add(Styles.TEXT_SMALL);
        return Ui.card(Ui.section(I18n.t("exchange.rates")), grid, note);
    }

    private static Label rate(BigDecimal value) {
        final Label l = new Label(com.mrcdprm.bank.core.Money.formatNumber(value.movePointRight(2).longValue(), I18n.locale())
                + " " + I18n.t("currency.tl"));
        l.getStyleClass().add("mono");
        return l;
    }

    /** Eksik döviz hesabı için "hesap aç" kısayolu. */
    private VBox openCard(List<Account> accounts) {
        final VBox card = Ui.card(Ui.section(I18n.t("exchange.openTitle")));
        for (Currency c : Currency.values()) {
            if (accounts.stream().anyMatch(a -> a.currency() == c))
                continue;
            final Button open = Ui.button(I18n.t("exchange.open", Views.currencyCode(c)), Feather.PLUS, Styles.SMALL);
            open.setOnAction(e -> {
                try {
                    ctx.bank().accounts().openCurrent(session, session.userId(), c);
                    shell.refresh();
                } catch (RuntimeException ex) {
                    Ui.error(ctx.window(), ex);
                }
            });
            card.getChildren().add(open);
        }
        if (card.getChildren().size() == 1)
            card.getChildren().add(Ui.muted(I18n.t("exchange.allOpen")));
        return card;
    }
}
