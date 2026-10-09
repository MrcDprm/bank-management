package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.data.Records.StaffUser;
import com.mrcdprm.bank.service.Session;
import java.time.LocalDateTime;
import java.util.List;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** Personel yönetimi (sadece yönetici): yeni personel, rol, aktif/pasif, şifre sıfırlama. */
final class StaffPage extends VBox {

    private final AppContext ctx;
    private final StaffShell shell;
    private final Session session;

    StaffPage(AppContext ctx, StaffShell shell) {
        super(14);
        this.ctx = ctx;
        this.shell = shell;
        this.session = ctx.session();

        final List<StaffUser> users = ctx.bank().staff().list(session);
        final TableView<StaffUser> table = new TableView<>(FXCollections.observableArrayList(users));
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(List.of(
                column(I18n.t("field.fullName"), u -> u.fullName(), 200),
                column(I18n.t("field.username"), u -> u.username(), 150),
                column(I18n.t("col.role"), u -> I18n.of(u.role()), 120),
                column(I18n.t("col.status"), this::status, 260)));
        VBox.setVgrow(table, Priority.ALWAYS);

        final Button add = Ui.button(I18n.t("staff.new"), Feather.USER_PLUS, Styles.ACCENT);
        add.setOnAction(e -> newStaff());
        final Button reset = Ui.button(I18n.t("customers.resetPassword"), Feather.KEY);
        final Button toggle = Ui.button(I18n.t("staff.deactivate"), Feather.USER_X);
        final Button role = Ui.button(I18n.t("staff.changeRole"), Feather.SHIELD);

        final Runnable update = () -> {
            final StaffUser u = table.getSelectionModel().getSelectedItem();
            final boolean self = u != null && u.id() == session.userId();
            reset.setDisable(u == null || self);
            toggle.setDisable(u == null || self);
            role.setDisable(u == null || self);
            toggle.setText(I18n.t(u != null && !u.active() ? "staff.activate" : "staff.deactivate"));
        };
        table.getSelectionModel().selectedItemProperty().addListener((o, x, y) -> update.run());
        update.run();

        reset.setOnAction(e -> {
            final StaffUser u = table.getSelectionModel().getSelectedItem();
            if (!Ui.confirm(ctx.window(), I18n.t("customers.resetQuestion", u.fullName()), I18n.t("customers.resetPassword")))
                return;
            try {
                StaffDialogs.temporaryPassword(ctx, u.fullName(), ctx.bank().staff().resetPassword(session, u.id()));
                shell.refresh();
            } catch (RuntimeException ex) {
                Ui.error(ctx.window(), ex);
            }
        });
        toggle.setOnAction(e -> {
            final StaffUser u = table.getSelectionModel().getSelectedItem();
            run(() -> ctx.bank().staff().setActive(session, u.id(), !u.active()));
        });
        role.setOnAction(e -> {
            final StaffUser u = table.getSelectionModel().getSelectedItem();
            final Role next = u.role() == Role.ADMIN ? Role.TELLER : Role.ADMIN;
            if (Ui.confirm(ctx.window(), I18n.t("staff.roleQuestion", u.fullName(), I18n.of(next)), I18n.t("staff.changeRole")))
                run(() -> ctx.bank().staff().setRole(session, u.id(), next));
        });

        final FlowPane actions = new FlowPane(8, 8, add, reset, toggle, role);
        final VBox card = Ui.card(actions, table);
        VBox.setVgrow(card, Priority.ALWAYS);
        getChildren().addAll(Ui.header(I18n.t("nav.staff"), I18n.t("staff.subtitle")), card);
    }

    private String status(StaffUser u) {
        final StringBuilder s = new StringBuilder(I18n.t(u.active() ? "staff.active" : "staff.inactive"));
        if (u.lockedUntil() != null && u.lockedUntil().isAfter(LocalDateTime.now()))
            s.append(" · ").append(I18n.t("customers.locked", I18n.time(u.lockedUntil())));
        if (u.mustChange())
            s.append(" · ").append(I18n.t("customers.mustChange"));
        return s.toString();
    }

    private static TableColumn<StaffUser, String> column(String title, java.util.function.Function<StaffUser, String> value,
                                                         double width) {
        final TableColumn<StaffUser, String> c = new TableColumn<>(title);
        c.setCellValueFactory(x -> new ReadOnlyStringWrapper(value.apply(x.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private void run(Runnable action) {
        try {
            action.run();
            shell.refresh();
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
        }
    }

    private void newStaff() {
        final Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(ctx.window());
        dialog.setTitle(I18n.t("staff.new"));
        Theme.style(dialog.getDialogPane());
        final TextField name = new TextField();
        final TextField username = new TextField();
        final ComboBox<Role> role = new ComboBox<>(FXCollections.observableArrayList(Role.TELLER, Role.ADMIN));
        role.setConverter(TransferPage.enumConverter());
        role.setValue(Role.TELLER);
        role.setMaxWidth(Double.MAX_VALUE);
        final Label error = Ui.errorLabel();
        final VBox content = new VBox(10, Ui.field(I18n.t("field.fullName"), name),
                Ui.field(I18n.t("field.username"), username), Ui.field(I18n.t("col.role"), role),
                Ui.muted(I18n.t("staff.usernameHint")), error);
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);
        final ButtonType ok = new ButtonType(I18n.t("action.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"),
                ButtonBar.ButtonData.CANCEL_CLOSE));
        final String[] password = new String[1];
        final Node okButton = dialog.getDialogPane().lookupButton(ok);
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                password[0] = ctx.bank().staff().create(session, username.getText(), name.getText(), role.getValue());
            } catch (RuntimeException ex) {
                Ui.showError(error, Ui.message(ex));
                dialog.getDialogPane().getScene().getWindow().sizeToScene();
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ok ? password[0] : null);
        dialog.showAndWait().ifPresent(temp -> {
            StaffDialogs.temporaryPassword(ctx, name.getText().strip(), temp);
            shell.refresh();
        });
    }
}
