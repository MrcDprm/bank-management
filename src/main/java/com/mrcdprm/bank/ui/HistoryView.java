package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.data.Records.Transaction;
import com.mrcdprm.bank.pdf.Documents;
import com.mrcdprm.bank.service.HistoryService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Hesap hareketleri: hesap seçimi, tarih aralığı, arama, tablo ve hesap özeti PDF'i.
 * Müşteri sayfasında ve şubede müşteri detayında aynı bileşen kullanılır; kategori değiştirme sadece müşteriye açık.
 */
final class HistoryView extends VBox {

    private final AppContext ctx;
    private final boolean customerMode;
    private final ComboBox<Account> account;
    private final DatePicker from = datePicker();
    private final DatePicker to = datePicker();
    private final TextField search = new TextField();
    private final TableView<Transaction> table;
    private final Label summary = Ui.muted("");

    HistoryView(AppContext ctx, List<Account> accounts, Long preselect, boolean customerMode) {
        super(12);
        this.ctx = ctx;
        this.customerMode = customerMode;
        account = Views.accountCombo(accounts);
        account.setPrefWidth(300);
        accounts.stream().filter(a -> preselect != null && a.id() == preselect).findFirst()
                .ifPresent(account.getSelectionModel()::select);

        final LocalDate today = ctx.bank().today();
        from.setValue(today.minusMonths(3));
        to.setValue(today);
        search.setPromptText(I18n.t("history.search"));
        search.setPrefWidth(200);

        final MenuButton range = new MenuButton(I18n.t("history.range"), new FontIcon(Feather.CALENDAR));
        range.getItems().addAll(
                rangeItem(I18n.t("history.thisMonth"), today.withDayOfMonth(1), today),
                rangeItem(I18n.t("history.last3"), today.minusMonths(3), today),
                rangeItem(I18n.t("history.last6"), today.minusMonths(6), today),
                rangeItem(I18n.t("history.thisYear"), today.withDayOfYear(1), today));

        final Button pdf = Ui.button(I18n.t("history.statement"), Feather.DOWNLOAD);
        pdf.setOnAction(e -> statement(pdf));

        table = Views.transactionTable(t -> account.getValue().currency());
        table.setRowFactory(tv -> {
            final TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    showReceipt(row.getItem());
            });
            row.itemProperty().addListener((obs, old, t) -> row.setContextMenu(t == null ? null : menu(t)));
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        // Aramada her tuşta değil, yazma bitince (300 ms) yenile
        final PauseTransition debounce = new PauseTransition(Duration.millis(300));
        debounce.setOnFinished(e -> reload());
        search.textProperty().addListener((obs, old, text) -> debounce.playFromStart());
        account.valueProperty().addListener((obs, old, a) -> reload());
        from.valueProperty().addListener((obs, old, d) -> reload());
        to.valueProperty().addListener((obs, old, d) -> reload());

        final FlowPane filters = new FlowPane(8, 8, account, from, new Label("–"), to, range, search);
        filters.setAlignment(Pos.CENTER_LEFT);
        final Label hint = Ui.muted(I18n.t(customerMode ? "history.hintCustomer" : "history.hintStaff"));
        hint.getStyleClass().add(Styles.TEXT_SMALL);
        hint.setWrapText(false);
        summary.setWrapText(false);
        final HBox footer = new HBox(16, new VBox(2, summary, hint), Ui.spacer(), pdf);
        footer.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(filters, table, footer);
        reload();
    }

    private MenuItem rangeItem(String text, LocalDate start, LocalDate end) {
        final MenuItem item = new MenuItem(text);
        item.setOnAction(e -> {
            from.setValue(start);
            to.setValue(end);
        });
        return item;
    }

    private void reload() {
        final Account a = account.getValue();
        if (a == null)
            return;
        LocalDate start = from.getValue();
        LocalDate end = to.getValue();
        if (start != null && end != null && start.isAfter(end)) {
            final LocalDate swap = start;
            start = end;
            end = swap;
        }
        try {
            final List<Transaction> rows = ctx.bank().history().list(ctx.session(), a.id(), start, end, search.getText());
            table.setItems(FXCollections.observableArrayList(rows));
            long in = 0;
            long out = 0;
            for (Transaction t : rows) {
                if (t.type().incoming())
                    in += t.amount();
                else
                    out += t.amount();
            }
            summary.setText(I18n.t(rows.size() >= HistoryService.MAX_ROWS ? "history.summaryMax" : "history.summary",
                    rows.size(), I18n.money(in, a.currency()), I18n.money(out, a.currency())));
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
        }
    }

    private ContextMenu menu(Transaction t) {
        final MenuItem receipt = new MenuItem(I18n.t("history.receipt"), new FontIcon(Feather.FILE_TEXT));
        receipt.setOnAction(e -> showReceipt(t));
        final ContextMenu menu = new ContextMenu(receipt);
        if (customerMode && !t.internal()) {
            final Menu category = new Menu(I18n.t("history.changeCategory"), new FontIcon(Feather.TAG));
            for (Category c : Category.selectable()) {
                final MenuItem item = new MenuItem(I18n.of(c));
                item.setDisable(c == t.category());
                item.setOnAction(e -> {
                    try {
                        ctx.bank().history().setCategory(ctx.session(), t.id(), c);
                        reload();
                    } catch (RuntimeException ex) {
                        Ui.error(ctx.window(), ex);
                    }
                });
                category.getItems().add(item);
            }
            menu.getItems().add(category);
        }
        return menu;
    }

    private void showReceipt(Transaction t) {
        try {
            new ReceiptDialog(ctx, ctx.bank().history().receipt(ctx.session(), t.id()), false).showAndWait();
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
        }
    }

    /** Seçili tarih aralığının hesap özeti (arama metni dikkate alınmaz; özet dönemin tamamıdır). */
    private void statement(Button busy) {
        final Account a = account.getValue();
        if (a == null)
            return;
        final LocalDate start = from.getValue() != null ? from.getValue() : a.openedAt().toLocalDate();
        final LocalDate end = to.getValue() != null ? to.getValue() : ctx.bank().today();
        final List<Transaction> rows = ctx.bank().history().list(ctx.session(), a.id(), start, end, "");
        final Customer owner = ctx.bank().customers().get(ctx.session(), a.customerId());
        PdfSaver.save(ctx, busy, Documents.statementFileName(a, start, end),
                file -> Documents.statement(a, owner, rows, start, end, java.time.LocalDateTime.now(), file));
    }

    /** Tarih seçici seçili dilin biçimiyle (gg.aa.yyyy) yazar ve okur. */
    private static DatePicker datePicker() {
        final DatePicker picker = new DatePicker();
        final DateTimeFormatter format = DateTimeFormatter.ofPattern(I18n.isTurkish() ? "dd.MM.yyyy" : "MM/dd/yyyy");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(format);
            }

            @Override
            public LocalDate fromString(String text) {
                try {
                    return text == null || text.isBlank() ? null : LocalDate.parse(text.strip(), format);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
        picker.setPrefWidth(140);
        return picker;
    }
}
