package com.mrcdprm.bank.ui;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Validation;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.service.CustomerService;
import com.mrcdprm.bank.service.Session;
import java.time.LocalDateTime;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Şube: solda müşteri arama ve listesi, sağda seçili müşterinin bilgileri, hesapları ve işlem düğmeleri.
 * Düğmeler seçili hesabın durumuna göre açılıp kapanır; kurallar yine de servis katmanında kontrol edilir.
 */
final class CustomersPage extends VBox {

    private final AppContext ctx;
    private final Session session;
    private final CustomTextField search = new CustomTextField();
    private final ListView<Customer> list = new ListView<>();
    private final StackPane detail = new StackPane();
    private final HBox stats = new HBox(12);

    CustomersPage(AppContext ctx) {
        super(14);
        this.ctx = ctx;
        this.session = ctx.session();

        search.setPromptText(I18n.t("customers.search"));
        search.setLeft(new FontIcon(Feather.SEARCH));
        final PauseTransition debounce = new PauseTransition(Duration.millis(250));
        debounce.setOnFinished(e -> reload(null));
        search.textProperty().addListener((obs, old, text) -> debounce.playFromStart());
        final Button add = Ui.button(I18n.t("customers.new"), Feather.USER_PLUS, Styles.ACCENT);
        add.setOnAction(e -> StaffDialogs.newCustomer(ctx).showAndWait().ifPresent(created -> {
            StaffDialogs.temporaryPassword(ctx, created.customer().fullName(), created.temporaryPassword());
            search.clear();
            reload(created.customer().id());
        }));

        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setGraphic(null);
                    return;
                }
                final Label name = new Label(c.fullName());
                name.getStyleClass().add(Styles.TEXT_BOLD);
                final Label sub = Ui.muted(c.tckn() + " · " + Validation.formatPhone(c.phone()));
                sub.getStyleClass().add(Styles.TEXT_SMALL);
                setGraphic(new VBox(1, name, sub));
            }
        });
        list.getStyleClass().add(Tweaks.EDGE_TO_EDGE);
        list.setPlaceholder(Ui.muted(I18n.t("customers.none")));
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, c) -> showDetail(c));
        VBox.setVgrow(list, Priority.ALWAYS);

        final VBox left = new VBox(10, search, add, list);
        left.getStyleClass().add("panel");
        left.setPadding(new Insets(12));
        left.setPrefWidth(320);
        left.setMinWidth(280);
        add.setMaxWidth(Double.MAX_VALUE);

        detail.setAlignment(Pos.TOP_LEFT);
        final ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detailScroll.getStyleClass().add("transparent-scroll");
        HBox.setHgrow(detailScroll, Priority.ALWAYS);

        final HBox body = new HBox(16, left, detailScroll);
        VBox.setVgrow(body, Priority.ALWAYS);
        getChildren().addAll(Ui.header(I18n.t("nav.customers"), I18n.t("customers.subtitle")), stats, body);
        reload(null);
    }

    private void reloadStats() {
        final CustomerService.Stats s = ctx.bank().customers().stats(session);
        stats.getChildren().setAll(
                stat(Feather.USERS, I18n.t("stats.customers"), String.valueOf(s.customers())),
                stat(Feather.CREDIT_CARD, I18n.t("stats.accounts"), String.valueOf(s.openAccounts())),
                stat(Feather.DATABASE, I18n.t("stats.deposits"), I18n.money(s.totalDepositsTry(), Currency.TRY)),
                stat(Feather.ACTIVITY, I18n.t("stats.today"), String.valueOf(s.transactionsToday())));
    }

    private static HBox stat(Feather icon, String label, String value) {
        final FontIcon i = new FontIcon(icon);
        i.getStyleClass().add("stat-icon");
        final Label v = new Label(value);
        v.getStyleClass().add("tile-value-small");
        final HBox box = new HBox(12, i, new VBox(0, Ui.muted(label), v));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().addAll("panel", "stat-tile");
        box.setPadding(new Insets(10, 16, 10, 16));
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Listeyi arama metnine göre yeniler; verilen müşteriyi (ya da öncekini) seçili bırakır. */
    private void reload(Long select) {
        final Customer selected = list.getSelectionModel().getSelectedItem();
        final long keep = select != null ? select : selected == null ? -1 : selected.id();
        try {
            list.setItems(FXCollections.observableArrayList(ctx.bank().customers().search(session, search.getText())));
            reloadStats();
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
            return;
        }
        list.getItems().stream().filter(c -> c.id() == keep).findFirst().ifPresentOrElse(c -> {
            list.getSelectionModel().select(c);
            list.scrollTo(c);
        }, () -> {
            if (!list.getItems().isEmpty())
                list.getSelectionModel().selectFirst();
            else
                showDetail(null);
        });
        if (select != null)
            showDetail(list.getSelectionModel().getSelectedItem());
    }

    private void showDetail(Customer listed) {
        if (listed == null) {
            final Label empty = Ui.muted(I18n.t("customers.select"));
            empty.setPadding(new Insets(20));
            detail.getChildren().setAll(empty);
            return;
        }
        final Customer c = ctx.bank().customers().get(session, listed.id()); // güncel hâli
        final List<Account> accounts = ctx.bank().accounts().listForCustomer(session, c.id());

        // Başlık
        final Label avatar = new Label(Shell.initials(c.fullName()));
        avatar.getStyleClass().addAll("avatar", "avatar-large");
        avatar.setMinSize(52, 52);
        avatar.setAlignment(Pos.CENTER);
        final Label name = new Label(c.fullName());
        name.getStyleClass().add(Styles.TITLE_3);
        final Label meta = Ui.muted(I18n.t("field.tckn") + ": " + c.tckn() + "   ·   " + Validation.formatPhone(c.phone())
                + "   ·   " + c.email());
        final FlowPane badges = new FlowPane(6, 6);
        if (c.lockedUntil() != null && c.lockedUntil().isAfter(LocalDateTime.now()))
            badges.getChildren().add(badge(I18n.t("customers.locked", I18n.time(c.lockedUntil())), "badge-frozen"));
        if (c.mustChange())
            badges.getChildren().add(badge(I18n.t("customers.mustChange"), "badge-info"));
        badges.getChildren().add(badge(I18n.t("customers.limitBadge", I18n.money(c.dailyLimit(), Currency.TRY)), "badge-muted"));
        final HBox head = new HBox(14, avatar, new VBox(4, name, meta, badges));
        head.setAlignment(Pos.CENTER_LEFT);

        // Müşteri düğmeleri
        final MenuButton open = new MenuButton(I18n.t("customers.openAccount"), new FontIcon(Feather.PLUS_CIRCLE));
        for (Currency cur : Currency.values()) {
            final MenuItem item = new MenuItem(I18n.t("account.current", Views.currencyCode(cur)));
            item.setOnAction(e -> run(() -> ctx.bank().accounts().openCurrent(session, c.id(), cur), c));
            open.getItems().add(item);
        }
        final Button reset = Ui.button(I18n.t("customers.resetPassword"), Feather.KEY);
        reset.setOnAction(e -> {
            if (!Ui.confirm(ctx.window(), I18n.t("customers.resetQuestion", c.fullName()), I18n.t("customers.resetPassword")))
                return;
            try {
                StaffDialogs.temporaryPassword(ctx, c.fullName(), ctx.bank().customers().resetPassword(session, c.id()));
                showDetail(c);
            } catch (RuntimeException ex) {
                Ui.error(ctx.window(), ex);
            }
        });
        final Button contact = Ui.button(I18n.t("customers.contact"), Feather.EDIT_2);
        contact.setOnAction(e -> StaffDialogs.contact(ctx, c).showAndWait().ifPresent(ok -> reload(c.id())));
        final Button limit = Ui.button(I18n.t("customers.limit"), Feather.SLIDERS);
        limit.setOnAction(e -> StaffDialogs.limit(ctx, c).showAndWait().ifPresent(ok -> showDetail(c)));
        final FlowPane customerActions = new FlowPane(8, 8, open, reset, contact, limit);

        // Hesap tablosu ve hesap düğmeleri
        final TableView<Account> table = accountTable(accounts);
        final Button deposit = Ui.button(I18n.t("cash.deposit"), Feather.PLUS, Styles.SUCCESS);
        final Button withdraw = Ui.button(I18n.t("cash.withdraw"), Feather.MINUS);
        final Button freeze = Ui.button(I18n.t("accounts.freeze"), Feather.LOCK);
        final Button close = Ui.button(I18n.t("accounts.close"), Feather.X_CIRCLE, Styles.DANGER);
        final Button history = Ui.button(I18n.t("accounts.history"), Feather.LIST);
        close.setVisible(session.isAdmin());
        close.managedProperty().bind(close.visibleProperty());

        final Runnable updateButtons = () -> {
            final Account a = table.getSelectionModel().getSelectedItem();
            final boolean current = a != null && a.type() == AccountType.CURRENT;
            deposit.setDisable(!current || !a.isActive());
            withdraw.setDisable(!current || !a.isActive() || a.balance() == 0);
            freeze.setDisable(a == null || a.status() == AccountStatus.CLOSED);
            freeze.setText(I18n.t(a != null && a.status() == AccountStatus.FROZEN ? "accounts.unfreeze" : "accounts.freeze"));
            freeze.setGraphic(new FontIcon(a != null && a.status() == AccountStatus.FROZEN ? Feather.UNLOCK : Feather.LOCK));
            close.setDisable(a == null || a.status() == AccountStatus.CLOSED);
            history.setDisable(a == null);
        };
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, a) -> updateButtons.run());
        if (!accounts.isEmpty())
            table.getSelectionModel().selectFirst();
        updateButtons.run();

        deposit.setOnAction(e -> cash(c, table.getSelectionModel().getSelectedItem(), true));
        withdraw.setOnAction(e -> cash(c, table.getSelectionModel().getSelectedItem(), false));
        freeze.setOnAction(e -> {
            final Account a = table.getSelectionModel().getSelectedItem();
            final boolean frozen = a.status() == AccountStatus.FROZEN;
            if (!frozen && !Ui.confirm(ctx.window(), I18n.t("accounts.freezeQuestion", Views.iban(a.iban())),
                    I18n.t("accounts.freeze")))
                return;
            run(() -> ctx.bank().accounts().setFrozen(session, a.id(), !frozen), c);
        });
        close.setOnAction(e -> {
            final Account a = table.getSelectionModel().getSelectedItem();
            if (Ui.confirm(ctx.window(), I18n.t("accounts.closeQuestion", Views.iban(a.iban())), I18n.t("accounts.close")))
                run(() -> ctx.bank().accounts().close(session, a.id()), c);
        });
        history.setOnAction(e -> StaffDialogs.history(ctx, c, accounts, table.getSelectionModel().getSelectedItem()));
        table.setRowFactory(tv -> {
            final TableRow<Account> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    StaffDialogs.history(ctx, c, accounts, row.getItem());
            });
            return row;
        });

        final FlowPane accountActions = new FlowPane(8, 8, deposit, withdraw, freeze, close, history);
        final VBox accountsCard = Ui.card(Ui.section(I18n.t("customers.accounts")), accountActions, table);

        final VBox box = new VBox(16, Ui.card(head, customerActions), accountsCard);
        detail.getChildren().setAll(box);
    }

    private static Label badge(String text, String style) {
        final Label badge = new Label(text);
        badge.getStyleClass().addAll("badge", style);
        return badge;
    }

    private void cash(Customer c, Account a, boolean deposit) {
        if (a == null)
            return;
        StaffDialogs.cash(ctx, a, deposit).showAndWait().ifPresent(receipt -> {
            new ReceiptDialog(ctx, receipt, true).showAndWait();
            showDetail(c);
            reloadStats();
        });
    }

    /** Servis çağrısı; başarılıysa detayı yeniler, hata varsa gösterir. */
    private void run(Runnable action, Customer c) {
        try {
            action.run();
            showDetail(c);
            reloadStats();
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
        }
    }

    private static TableView<Account> accountTable(List<Account> accounts) {
        final TableView<Account> table = new TableView<>(FXCollections.observableArrayList(accounts));
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted(I18n.t("customers.noAccounts")));
        final TableColumn<Account, String> name = new TableColumn<>(I18n.t("col.account"));
        name.setCellValueFactory(x -> new ReadOnlyStringWrapper(Views.accountName(x.getValue())));
        name.setPrefWidth(165);
        final TableColumn<Account, String> iban = new TableColumn<>("IBAN");
        iban.setCellValueFactory(x -> new ReadOnlyStringWrapper(Views.iban(x.getValue().iban())));
        iban.setPrefWidth(270);
        iban.setMinWidth(262);
        final TableColumn<Account, String> balance = new TableColumn<>(I18n.t("col.balance"));
        balance.setCellValueFactory(x -> new ReadOnlyStringWrapper(I18n.money(x.getValue().balance(), x.getValue().currency())));
        balance.setStyle("-fx-alignment: CENTER-RIGHT;");
        balance.setPrefWidth(130);
        final TableColumn<Account, AccountStatus> status = new TableColumn<>(I18n.t("col.status"));
        status.setCellValueFactory(x -> new ReadOnlyObjectWrapper<>(x.getValue().status()));
        status.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(AccountStatus s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    return;
                }
                final Label l = new Label(I18n.of(s));
                l.getStyleClass().addAll("badge", "badge-" + s.name().toLowerCase(java.util.Locale.ROOT));
                setGraphic(l);
            }
        });
        status.setPrefWidth(90);
        table.getColumns().addAll(List.of(name, iban, balance, status));
        table.setFixedCellSize(40);
        table.setPrefHeight(40 * Math.max(3, Math.min(accounts.size(), 8)) + 36);
        return table;
    }
}
