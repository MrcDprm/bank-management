package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.BankException;
import com.mrcdprm.bank.core.Money;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/** Ortak arayüz yardımcıları: mesaj kutuları, arka plan işleri, düğmeler, başlıklar. */
public final class Ui {

    private static final System.Logger LOG = System.getLogger("bank");

    private Ui() {
    }

    // ---------------------------------------------------------------- mesaj kutuları

    public static void info(Window owner, String text) {
        alert(owner, Alert.AlertType.INFORMATION, I18n.t("dialog.info"), text).showAndWait();
    }

    /**
     * Hata kutusu. İş kuralı hataları kendi cümlesiyle gösterilir; beklenmeyen hatalarda ayrıntı
     * (stack trace, SQL mesajı) kullanıcıya gösterilmez, sadece günlüğe yazılır.
     */
    public static void error(Window owner, Throwable error) {
        alert(owner, Alert.AlertType.ERROR, I18n.t("dialog.error"), message(error)).showAndWait();
    }

    public static String message(Throwable error) {
        if (error instanceof BankException e)
            return I18n.error(e);
        LOG.log(System.Logger.Level.ERROR, "Beklenmeyen hata", error);
        return I18n.t("error.unexpected");
    }

    /** Evet/hayır sorusu; düğmeler seçili dilde. */
    public static boolean confirm(Window owner, String text, String yesLabel) {
        final ButtonType yes = new ButtonType(yesLabel, ButtonBar.ButtonData.OK_DONE);
        final ButtonType no = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        final Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, I18n.t("dialog.confirm"), text);
        alert.getButtonTypes().setAll(yes, no);
        return alert.showAndWait().filter(yes::equals).isPresent();
    }

    private static Alert alert(Window owner, Alert.AlertType type, String title, String text) {
        final Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        // Label kullanıcı verisini HTML/biçim olarak yorumlamaz; metin olduğu gibi görünür
        final Label content = new Label(text);
        content.setWrapText(true);
        content.setMaxWidth(420);
        alert.getDialogPane().setContent(content);
        if (type != Alert.AlertType.CONFIRMATION)
            alert.getButtonTypes().setAll(new ButtonType(I18n.t("action.ok"), ButtonBar.ButtonData.OK_DONE));
        return alert;
    }

    // ---------------------------------------------------------------- arka plan işleri

    /**
     * Yavaş bir işi (Argon2 şifre doğrulama, PDF, örnek veri) arka planda çalıştırır; arayüz donmaz.
     * Bu sırada bekleme imleci görünür ve "busy" düğümü devre dışı kalır (çift tıklamaya karşı).
     */
    public static <T> void background(Node busy, Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        final Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        final Scene scene = busy.getScene();
        busy.setDisable(true);
        if (scene != null)
            scene.setCursor(Cursor.WAIT);
        task.setOnSucceeded(e -> {
            busy.setDisable(false);
            if (scene != null)
                scene.setCursor(Cursor.DEFAULT);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(e -> {
            busy.setDisable(false);
            if (scene != null)
                scene.setCursor(Cursor.DEFAULT);
            onError.accept(task.getException());
        });
        final Thread thread = new Thread(task, "bank-task");
        thread.setDaemon(true);
        thread.start();
    }

    // ---------------------------------------------------------------- bileşenler

    public static Button button(String text, Ikon icon, String... styles) {
        final Button button = new Button(text);
        if (icon != null)
            button.setGraphic(new FontIcon(icon));
        button.getStyleClass().addAll(styles);
        return button;
    }

    public static Button iconButton(Ikon icon, String tooltip) {
        final Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }

    /** Sayfa başlığı ve altında soluk açıklama. */
    public static VBox header(String title, String subtitle) {
        final Label t = new Label(title);
        t.getStyleClass().add(Styles.TITLE_2);
        final VBox box = new VBox(2, t);
        if (subtitle != null && !subtitle.isEmpty()) {
            final Label s = new Label(subtitle);
            s.getStyleClass().add(Styles.TEXT_MUTED);
            s.setWrapText(true);
            box.getChildren().add(s);
        }
        return box;
    }

    public static Label muted(String text) {
        final Label label = new Label(text);
        label.getStyleClass().add(Styles.TEXT_MUTED);
        label.setWrapText(true);
        return label;
    }

    public static Label section(String text) {
        final Label label = new Label(text);
        label.getStyleClass().add(Styles.TITLE_4);
        return label;
    }

    /** Kenarlıklı, yuvarlak köşeli kutu. */
    public static VBox card(Node... children) {
        final VBox box = new VBox(10, children);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(16));
        return box;
    }

    public static Region spacer() {
        final Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox row(double gap, Node... children) {
        final HBox box = new HBox(gap, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Form satırı: üstte etiket, altta alan. */
    public static VBox field(String label, Node input) {
        final Label l = new Label(label);
        l.getStyleClass().add("field-label");
        l.setLabelFor(input);
        final VBox box = new VBox(4, l, input);
        if (input instanceof Region region)
            region.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Tutar alanı: kullanıcının yazdığı metni kuruşa çevirir, geçersizse boş döner. */
    public static TextField amountField() {
        final TextField field = new TextField();
        field.setPromptText(I18n.isTurkish() ? "0,00" : "0.00");
        field.getStyleClass().add("amount-field");
        field.textProperty().addListener((obs, old, text) -> {
            if (text.length() > 20)
                field.setText(old);
        });
        return field;
    }

    public static OptionalLong amount(TextField field) {
        return Money.parse(field.getText(), I18n.locale());
    }

    public static void copy(String text) {
        final ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Alan altındaki satır içi hata metni. */
    public static Label errorLabel() {
        final Label label = new Label();
        label.getStyleClass().add(Styles.DANGER);
        label.setWrapText(true);
        label.managedProperty().bind(label.visibleProperty());
        label.setVisible(false);
        return label;
    }

    public static void showError(Label label, String text) {
        label.setText(text);
        label.setVisible(text != null && !text.isEmpty());
    }
}
