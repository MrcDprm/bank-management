package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.AccountType;
import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Iban;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Transaction;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Hesap ve hareket gösterimiyle ilgili ortak parçalar. */
public final class Views {

    private Views() {
    }

    /** "TL Vadesiz Hesap", "USD Vadesiz Hesap", "Vadeli Hesap · 32 gün". */
    public static String accountName(Account a) {
        if (a.type() == AccountType.TIME_DEPOSIT)
            return I18n.t("account.timeDeposit", a.termDays());
        return I18n.t("account.current", currencyCode(a.currency()));
    }

    public static String currencyCode(Currency c) {
        return c == Currency.TRY ? I18n.t("currency.tl") : c.name();
    }

    /** Hesap seçici: ad, IBAN'ın son 4 hanesi ve bakiye. */
    public static ComboBox<Account> accountCombo(List<Account> accounts) {
        final ComboBox<Account> combo = new ComboBox<>(FXCollections.observableArrayList(accounts));
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account a) {
                return a == null ? "" : accountName(a) + "  ·  " + lastDigits(a.iban()) + "  ·  "
                        + I18n.money(a.balance(), a.currency());
            }

            @Override
            public Account fromString(String s) {
                return null;
            }
        });
        if (!accounts.isEmpty())
            combo.getSelectionModel().selectFirst();
        return combo;
    }

    public static String lastDigits(String iban) {
        return "•••• " + iban.substring(iban.length() - 4);
    }

    /** Hesap durumu rozeti (aktifse boş). */
    public static Label statusBadge(AccountStatus status) {
        final Label badge = new Label(I18n.of(status));
        badge.getStyleClass().addAll("badge", "badge-" + status.name().toLowerCase(java.util.Locale.ROOT));
        badge.setVisible(status != AccountStatus.ACTIVE);
        badge.managedProperty().bind(badge.visibleProperty());
        return badge;
    }

    /** Hareketin ekranda görünen başlığı: karşı taraf ya da işlem türü, altında açıklama. */
    public static String title(Transaction t) {
        if (t.type() == TxType.INTEREST)
            return I18n.t("tx.interest");
        if (t.type() == TxType.DEPOSIT)
            return I18n.t("tx.cashDeposit");
        if (t.type() == TxType.WITHDRAWAL)
            return I18n.t("tx.cashWithdrawal");
        if (t.type() == TxType.EXCHANGE_IN || t.type() == TxType.EXCHANGE_OUT)
            return I18n.t("tx.exchange");
        if (t.counterpartyName() != null && !t.counterpartyName().isBlank())
            return t.counterpartyName();
        return I18n.of(t.type());
    }

    public static String detail(Transaction t) {
        final String kind = switch (t.type()) {
            case TRANSFER_IN -> I18n.t(t.internal() ? "tx.ownIn" : "tx.in");
            case TRANSFER_OUT -> I18n.t(t.internal() ? "tx.ownOut" : "tx.out");
            default -> "";
        };
        if (t.description() == null || t.description().isBlank())
            return kind;
        return kind.isEmpty() ? t.description() : kind + " · " + t.description();
    }

    /** Hareket tablosu: tarih, açıklama, kategori, tutar (gelen yeşil, giden kırmızı), bakiye. */
    public static TableView<Transaction> transactionTable(Function<Transaction, Currency> currency) {
        final TableView<Transaction> table = new TableView<>();
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.muted(I18n.t("history.empty")));

        final TableColumn<Transaction, String> date = new TableColumn<>(I18n.t("col.date"));
        date.setCellValueFactory(c -> new ReadOnlyStringWrapper(I18n.time(c.getValue().createdAt())));
        date.setPrefWidth(140);
        date.setMinWidth(120);

        final TableColumn<Transaction, Transaction> what = new TableColumn<>(I18n.t("col.description"));
        what.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        what.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) {
                    setGraphic(null);
                    return;
                }
                final Label title = new Label(title(t));
                title.getStyleClass().add(Styles.TEXT_BOLD);
                final Label sub = new Label(detail(t));
                sub.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
                final FontIcon icon = new FontIcon(t.type().incoming() ? Feather.ARROW_DOWN_LEFT : Feather.ARROW_UP_RIGHT);
                icon.getStyleClass().add(t.type().incoming() ? "tx-icon-in" : "tx-icon-out");
                final HBox row = new HBox(10, icon, sub.getText().isEmpty() ? new VBox(title) : new VBox(1, title, sub));
                row.setAlignment(Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });
        what.setPrefWidth(320);

        final TableColumn<Transaction, String> category = new TableColumn<>(I18n.t("col.category"));
        category.setCellValueFactory(c -> new ReadOnlyStringWrapper(I18n.of(c.getValue().category())));
        category.setPrefWidth(120);

        final TableColumn<Transaction, Transaction> amount = new TableColumn<>(I18n.t("col.amount"));
        amount.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        amount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Transaction t, boolean empty) {
                super.updateItem(t, empty);
                getStyleClass().removeAll("amount-in", "amount-out");
                if (empty || t == null) {
                    setText(null);
                    return;
                }
                setText(com.mrcdprm.bank.core.Money.formatSigned(t.amount(), t.type().incoming(), currency.apply(t),
                        I18n.locale()));
                getStyleClass().add(t.type().incoming() ? "amount-in" : "amount-out");
            }
        });
        amount.setStyle("-fx-alignment: CENTER-RIGHT;");
        amount.setPrefWidth(140);

        final TableColumn<Transaction, String> balance = new TableColumn<>(I18n.t("col.balance"));
        balance.setCellValueFactory(c -> new ReadOnlyStringWrapper(I18n.money(c.getValue().balanceAfter(),
                currency.apply(c.getValue()))));
        balance.setStyle("-fx-alignment: CENTER-RIGHT;");
        balance.setPrefWidth(140);

        table.getColumns().addAll(List.of(date, what, category, amount, balance));
        table.setFixedCellSize(52);
        return table;
    }

    public static String iban(String iban) {
        return iban == null ? "" : Iban.format(iban);
    }
}
