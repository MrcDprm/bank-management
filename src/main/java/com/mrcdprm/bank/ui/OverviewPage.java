package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Interest;
import com.mrcdprm.bank.core.Money;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.service.HistoryService.Movement;
import com.mrcdprm.bank.service.Session;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ana sayfa: toplam varlık, hesap kartları, kısayollar ve son hareketler. */
final class OverviewPage extends ScrollPane {

    OverviewPage(AppContext ctx, CustomerShell shell) {
        final Session s = ctx.session();
        final List<Account> accounts = ctx.bank().accounts().listForCustomer(s, s.userId()).stream()
                .filter(a -> a.status() != AccountStatus.CLOSED).toList();
        final long total = accounts.stream().mapToLong(a -> Rates.toTry(a.balance(), a.currency())).sum();

        final Label totalLabel = Ui.muted(I18n.t("overview.total"));
        final Label totalValue = new Label(I18n.money(total, com.mrcdprm.bank.core.Currency.TRY));
        totalValue.getStyleClass().add("total-value");
        final Label totalHint = Ui.muted(I18n.t("overview.totalHint"));
        final VBox totalBox = new VBox(2, totalLabel, totalValue, totalHint);

        final Button send = Ui.button(I18n.t("nav.transfer"), Feather.SEND, Styles.ACCENT);
        send.setOnAction(e -> shell.open("transfer"));
        final Button exchange = Ui.button(I18n.t("nav.exchange"), Feather.REFRESH_CW);
        exchange.setOnAction(e -> shell.open("exchange"));
        final Button deposit = Ui.button(I18n.t("overview.newDeposit"), Feather.TRENDING_UP);
        deposit.setOnAction(e -> shell.open("deposits"));
        final HBox actions = new HBox(8, send, exchange, deposit);
        actions.setAlignment(Pos.BOTTOM_RIGHT);
        final HBox top = new HBox(Ui.header(I18n.t("nav.overview"), null), Ui.spacer(), actions);
        top.setAlignment(Pos.TOP_LEFT);

        final FlowPane cards = new FlowPane(14, 14);
        for (Account a : accounts)
            cards.getChildren().add(card(ctx, shell, a));

        final VBox recent = new VBox(0);
        recent.getStyleClass().add("panel");
        final List<Movement> movements = ctx.bank().history().recent(s, s.userId(), 8);
        if (movements.isEmpty())
            recent.getChildren().add(padded(Ui.muted(I18n.t("history.empty"))));
        for (Movement m : movements)
            recent.getChildren().add(movementRow(m));
        final Button all = Ui.button(I18n.t("overview.allHistory"), Feather.ARROW_RIGHT, Styles.FLAT);
        all.setOnAction(e -> shell.open("history"));
        final HBox recentHeader = new HBox(Ui.section(I18n.t("overview.recent")), Ui.spacer(), all);
        recentHeader.setAlignment(Pos.CENTER_LEFT);

        final VBox page = new VBox(18, top, totalBox, cards, recentHeader, recent);
        page.setPadding(new Insets(4, 4, 20, 0));
        setContent(page);
        setFitToWidth(true);
        getStyleClass().add("transparent-scroll");
    }

    private static VBox card(AppContext ctx, CustomerShell shell, Account a) {
        final Label name = new Label(Views.accountName(a));
        name.getStyleClass().add(Styles.TEXT_BOLD);
        final HBox title = new HBox(8, name, Views.statusBadge(a.status()));
        title.setAlignment(Pos.CENTER_LEFT);
        final Label iban = Ui.muted(Views.iban(a.iban()));
        iban.getStyleClass().add("mono");
        iban.setWrapText(false);
        iban.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        final Button copy = Ui.iconButton(Feather.COPY, I18n.t("overview.copyIban"));
        copy.getStyleClass().add(Styles.SMALL);
        copy.setOnAction(e -> {
            Ui.copy(a.iban());
            copy.setGraphic(new FontIcon(Feather.CHECK));
        });
        final HBox ibanRow = new HBox(4, iban, copy);
        ibanRow.setAlignment(Pos.CENTER_LEFT);

        final Label balance = new Label(I18n.money(a.balance(), a.currency()));
        balance.getStyleClass().add("card-balance");

        final VBox card = new VBox(6, title, ibanRow, balance);
        if (a.type() == AccountType.TIME_DEPOSIT) {
            final long interest = Interest.simple(a.principal(), a.annualRate(), a.termDays());
            card.getChildren().add(Ui.muted(I18n.t("overview.maturity", I18n.date(a.maturityDate()),
                    Money.format(interest, a.currency(), I18n.locale()))));
        }
        final Button history = Ui.button(I18n.t("nav.history"), Feather.LIST, Styles.SMALL, Styles.FLAT);
        history.setOnAction(e -> shell.openAccount("history", a.id()));
        final HBox buttons = new HBox(4, history);
        if (a.type() == AccountType.CURRENT && a.isActive()) {
            final Button send = Ui.button(I18n.t("overview.send"), Feather.SEND, Styles.SMALL, Styles.FLAT);
            send.setOnAction(e -> shell.openAccount("transfer", a.id()));
            buttons.getChildren().add(send);
        }
        card.getChildren().add(buttons);
        card.getStyleClass().addAll("panel", "account-card", a.type() == AccountType.TIME_DEPOSIT ? "deposit-card"
                : "currency-" + a.currency().name().toLowerCase(java.util.Locale.ROOT));
        card.setPadding(new Insets(16));
        card.setPrefWidth(330);
        return card;
    }

    private static HBox movementRow(Movement m) {
        final FontIcon icon = new FontIcon(m.tx().type().incoming() ? Feather.ARROW_DOWN_LEFT : Feather.ARROW_UP_RIGHT);
        icon.getStyleClass().add(m.tx().type().incoming() ? "tx-icon-in" : "tx-icon-out");
        final Label title = new Label(Views.title(m.tx()));
        title.getStyleClass().add(Styles.TEXT_BOLD);
        final Label sub = Ui.muted(I18n.time(m.tx().createdAt()) + " · " + I18n.of(m.tx().category()));
        sub.getStyleClass().add(Styles.TEXT_SMALL);
        final Label amount = new Label(Money.formatSigned(m.tx().amount(), m.tx().type().incoming(), m.currency(),
                I18n.locale()));
        amount.getStyleClass().add(m.tx().type().incoming() ? "amount-in" : "amount-out");
        final HBox row = new HBox(12, icon, new VBox(1, title, sub), Ui.spacer(), amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        row.setPadding(new Insets(10, 16, 10, 16));
        return row;
    }

    private static HBox padded(Label label) {
        final HBox box = new HBox(label);
        box.setPadding(new Insets(16));
        return box;
    }
}
