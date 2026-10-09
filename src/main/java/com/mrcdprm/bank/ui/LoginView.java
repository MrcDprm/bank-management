package com.mrcdprm.bank.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.service.SampleData;
import com.mrcdprm.bank.service.Session;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Giriş ekranı: "Bireysel" (müşteri, T.C. kimlik no ile) ve "Şube" (personel, kullanıcı adıyla) sekmeleri.
 * Örnek veri yüklüyse demo hesaplarla tek tıkla giriş düğmeleri görünür.
 */
public final class LoginView extends BorderPane {

    private final AppContext ctx;
    private final ToggleButton customerTab = new ToggleButton(I18n.t("login.customerTab"));
    private final ToggleButton staffTab = new ToggleButton(I18n.t("login.staffTab"));
    private final Label idLabel = new Label();
    private final TextField id = new TextField();
    private final PasswordTextField password = new PasswordTextField();
    private final Label error = Ui.errorLabel();
    private final Button signIn = Ui.button(I18n.t("login.signIn"), Feather.LOG_IN, Styles.ACCENT);

    public LoginView(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("auth-background");

        final HBox content = new HBox(brandPanel(), formPanel());
        HBox.setHgrow(content.getChildren().get(1), Priority.ALWAYS);
        setCenter(content);
    }

    /** Sol taraf: uygulama adı, kısa tanıtım ve öne çıkan özellikler. */
    private VBox brandPanel() {
        final ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/com/mrcdprm/bank/icon.png")));
        logo.setFitWidth(56);
        logo.setFitHeight(56);
        final Label name = new Label(I18n.t("app.name"));
        name.getStyleClass().add("brand-title");
        final Label tagline = new Label(I18n.t("login.tagline"));
        tagline.getStyleClass().add("brand-tagline");
        tagline.setWrapText(true);

        final VBox features = new VBox(14);
        features.getChildren().addAll(
                feature(Feather.SHIELD, I18n.t("login.feature1")),
                feature(Feather.REPEAT, I18n.t("login.feature2")),
                feature(Feather.PIE_CHART, I18n.t("login.feature3")),
                feature(Feather.FILE_TEXT, I18n.t("login.feature4")));

        final Label demoNote = new Label(I18n.t("login.demoNote"));
        demoNote.getStyleClass().add("brand-note");
        demoNote.setWrapText(true);

        final VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        final VBox panel = new VBox(18, logo, name, tagline, features, spacer, demoNote);
        panel.getStyleClass().add("brand-panel");
        panel.setPadding(new Insets(48, 40, 32, 40));
        panel.setPrefWidth(420);
        panel.setMinWidth(360);
        return panel;
    }

    private static HBox feature(Feather icon, String text) {
        final FontIcon i = new FontIcon(icon);
        i.getStyleClass().add("brand-icon");
        final Label label = new Label(text);
        label.getStyleClass().add("brand-feature");
        label.setWrapText(true);
        final HBox row = new HBox(12, i, label);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private BorderPane formPanel() {
        final ToggleGroup group = new ToggleGroup();
        customerTab.setToggleGroup(group);
        staffTab.setToggleGroup(group);
        customerTab.getStyleClass().add(Styles.LEFT_PILL);
        staffTab.getStyleClass().add(Styles.RIGHT_PILL);
        customerTab.setGraphic(new FontIcon(Feather.USER));
        staffTab.setGraphic(new FontIcon(Feather.BRIEFCASE));
        customerTab.setMaxWidth(Double.MAX_VALUE);
        staffTab.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(customerTab, Priority.ALWAYS);
        HBox.setHgrow(staffTab, Priority.ALWAYS);
        // seçili sekmeye tekrar tıklanınca seçim kalkmasın
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null)
                group.selectToggle(old);
            else
                updateMode();
        });
        customerTab.setSelected(true);

        final Label title = new Label(I18n.t("login.title"));
        title.getStyleClass().add(Styles.TITLE_2);
        final Label subtitle = Ui.muted(I18n.t("login.subtitle"));

        id.textProperty().addListener((obs, old, text) -> {
            // T.C. kimlik no alanına sadece rakam, en fazla 11 basamak
            if (customerTab.isSelected() && !text.matches("\\d{0,11}"))
                id.setText(old);
        });
        signIn.setDefaultButton(true);
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setOnAction(e -> login(customerTab.isSelected(), id.getText(), password.getPassword()));

        final VBox idField = new VBox(4, idLabel, id);
        idLabel.getStyleClass().add("field-label");
        idLabel.setLabelFor(id);
        final VBox form = new VBox(14, title, subtitle, new HBox(customerTab, staffTab), idField,
                Ui.field(I18n.t("field.password"), password), error, signIn);
        demoBox().ifPresent(form.getChildren()::add);
        form.getStyleClass().add("auth-card");
        form.setPadding(new Insets(30));
        form.setMaxWidth(420);
        form.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        updateMode();

        final StackPane center = new StackPane(form);
        center.setPadding(new Insets(24));
        final ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("transparent-scroll");

        final BorderPane side = new BorderPane(scroll);
        final TopTools tools = new TopTools(ctx);
        tools.setPadding(new Insets(10, 14, 0, 0));
        side.setTop(tools);
        return side;
    }

    private void updateMode() {
        final boolean customer = customerTab.isSelected();
        idLabel.setText(I18n.t(customer ? "field.tckn" : "field.username"));
        id.setPromptText(customer ? I18n.t("login.tcknPrompt") : "");
        id.clear();
        password.setText("");
        Ui.showError(error, null);
        id.requestFocus();
    }

    /** Örnek veri yüklüyse demo hesap kutusu. */
    private Optional<VBox> demoBox() {
        if (!SampleData.isLoaded(ctx.db()))
            return Optional.empty();
        final Button demoCustomer = Ui.button(I18n.t("login.demoCustomer"), Feather.USER, Styles.SMALL);
        final Button demoTeller = Ui.button(I18n.t("login.demoTeller"), Feather.BRIEFCASE, Styles.SMALL);
        demoCustomer.setOnAction(e -> {
            customerTab.setSelected(true);
            id.setText(SampleData.CUSTOMER_TCKN);
            password.setText(SampleData.CUSTOMER_PASSWORD);
            login(true, SampleData.CUSTOMER_TCKN, SampleData.CUSTOMER_PASSWORD);
        });
        demoTeller.setOnAction(e -> {
            staffTab.setSelected(true);
            id.setText(SampleData.TELLER_USERNAME);
            password.setText(SampleData.TELLER_PASSWORD);
            login(false, SampleData.TELLER_USERNAME, SampleData.TELLER_PASSWORD);
        });
        final Label label = Ui.muted(I18n.t("login.demoTitle"));
        final HBox buttons = new HBox(8, demoCustomer, demoTeller);
        final VBox box = new VBox(8, label, buttons);
        box.getStyleClass().add("demo-box");
        box.setPadding(new Insets(12));
        return Optional.of(box);
    }

    /** Şifre doğrulaması bilerek yavaştır (Argon2); arayüz donmasın diye arka planda yapılır. */
    private void login(boolean customer, String login, String pass) {
        Ui.showError(error, null);
        if (login.isBlank() || pass.isEmpty()) {
            Ui.showError(error, I18n.t("login.missing"));
            return;
        }
        Ui.background(this, () -> customer ? ctx.bank().auth().loginCustomer(login, pass)
                : ctx.bank().auth().loginStaff(login, pass), this::opened, ex -> {
                    password.setText("");
                    Ui.showError(error, Ui.message(ex));
                });
    }

    private void opened(Session session) {
        if (!session.mustChangePassword()) {
            ctx.nav().showHome(session);
            return;
        }
        // Geçici şifreyle giriş: yeni şifre belirlenmeden içeri girilmez
        new PasswordDialog(ctx, session, true).showAndWait().ifPresentOrElse(ctx.nav()::showHome, () -> {
            password.setText("");
            Ui.showError(error, I18n.t("login.mustChange"));
        });
    }
}
