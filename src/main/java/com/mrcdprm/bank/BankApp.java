package com.mrcdprm.bank;

import com.mrcdprm.bank.data.Database;
import com.mrcdprm.bank.service.Bank;
import com.mrcdprm.bank.service.Session;
import com.mrcdprm.bank.ui.AppContext;
import com.mrcdprm.bank.ui.CustomerShell;
import com.mrcdprm.bank.ui.I18n;
import com.mrcdprm.bank.ui.LoginView;
import com.mrcdprm.bank.ui.SetupView;
import com.mrcdprm.bank.ui.Settings;
import com.mrcdprm.bank.ui.StaffShell;
import com.mrcdprm.bank.ui.Theme;
import com.mrcdprm.bank.ui.Ui;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Uygulama girişi: veritabanını açar, ilk açılışta kurulum ekranını, sonra giriş ekranını gösterir.
 * Oturum açıkken 5 dakika boyunca fare/klavye hareketi olmazsa oturum kapanır (ortak şube bilgisayarı için).
 */
public class BankApp extends Application implements AppContext.Navigator {

    public static final String VERSION = "1.0.0";
    public static final String REPO_URL = "https://github.com/MrcDprm/bank-management";
    private static final Duration IDLE_LIMIT = Duration.minutes(5);

    private Stage stage;
    private Scene scene;
    private Database db;
    private AppContext ctx;
    private Settings settings;
    private final PauseTransition idle = new PauseTransition(IDLE_LIMIT);

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        final Path folder = Settings.dataFolder();
        settings = new Settings(folder);
        I18n.setLocale(Locale.forLanguageTag(settings.language()));
        Theme.apply(settings.darkTheme());
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/mrcdprm/bank/icon.png")));
        stage.setTitle(I18n.t("app.name"));

        try {
            db = Database.open(folder.resolve("bank.db"));
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, I18n.t("error.databaseOpen", folder)).showAndWait();
            Platform.exit();
            return;
        }
        final Bank bank = new Bank(db, Clock.systemDefaultZone());
        ctx = new AppContext(bank, db, settings, this, url -> getHostServices().showDocument(url));
        try {
            bank.accounts().processMaturities(); // uygulama kapalıyken vadesi gelen mevduatlar
        } catch (RuntimeException e) {
            System.getLogger("bank").log(System.Logger.Level.ERROR, "Vade ödemeleri yapılamadı", e);
        }

        scene = new Scene(new javafx.scene.layout.StackPane(), 1240, 780);
        Theme.addStylesheet(scene);
        scene.addEventFilter(InputEvent.ANY, e -> idle.playFromStart());
        idle.setOnFinished(e -> {
            // Açık bir diyalog varken (kullanıcı onun içinde yazıyor olabilir) oturum kapatılmaz, süre yeniden başlar
            if (javafx.stage.Window.getWindows().size() > 1) {
                idle.playFromStart();
                return;
            }
            if (ctx.session() != null) {
                logout();
                Ui.info(stage, I18n.t("login.idleLogout"));
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(660);
        if (bank.auth().hasStaff())
            showLogin();
        else
            show(new SetupView(ctx));
        stage.show();
    }

    private void show(Parent root) {
        scene.setRoot(root);
        ctx.setWindow(stage);
    }

    @Override
    public void showLogin() {
        ctx.setSession(null);
        ctx.setPage(null);
        idle.stop();
        stage.setTitle(I18n.t("app.name"));
        show(new LoginView(ctx));
    }

    @Override
    public void showHome(Session session) {
        ctx.setSession(session);
        idle.playFromStart();
        stage.setTitle(I18n.t("app.name") + " - " + session.displayName());
        show(session.isCustomer() ? new CustomerShell(ctx) : new StaffShell(ctx));
    }

    @Override
    public void rebuild() {
        Theme.apply(settings.darkTheme());
        if (ctx.session() != null)
            showHome(ctx.session());
        else if (ctx.bank().auth().hasStaff())
            showLogin();
        else
            show(new SetupView(ctx));
    }

    @Override
    public void logout() {
        showLogin();
    }

    @Override
    public void stop() {
        if (db != null)
            db.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
