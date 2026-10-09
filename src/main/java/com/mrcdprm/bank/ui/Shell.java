package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.service.Session;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Oturum açıldıktan sonraki iskelet: solda menü ve kullanıcı kutusu, sağda seçili sayfa.
 * Sayfa her açılışta yeniden kurulur; böylece başka sayfada yapılan işlem (ör. transfer) hemen görünür.
 */
public abstract class Shell extends BorderPane {

    /** Menüdeki bir sayfa. */
    protected record Page(String key, Ikon icon, Supplier<Node> factory) {
    }

    protected final AppContext ctx;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final StackPane content = new StackPane();

    protected Shell(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("shell");
    }

    /** Alt sınıf sayfaları verdikten sonra çağırır. */
    protected final void build(List<Page> list) {
        final ToggleGroup group = new ToggleGroup();
        final VBox nav = new VBox(2);
        for (Page page : list) {
            pages.put(page.key(), page);
            final ToggleButton button = new ToggleButton(I18n.t("nav." + page.key()), new FontIcon(page.icon()));
            button.setToggleGroup(group);
            button.getStyleClass().add("nav-item");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(e -> open(page.key()));
            buttons.put(page.key(), button);
            nav.getChildren().add(button);
        }

        final ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/com/mrcdprm/bank/icon.png")));
        logo.setFitWidth(30);
        logo.setFitHeight(30);
        final Label appName = new Label(I18n.t("app.name"));
        appName.getStyleClass().add("sidebar-title");
        final Label side = Ui.muted(I18n.t(ctx.session().isCustomer() ? "shell.customerSide" : "shell.staffSide"));
        final VBox titles = new VBox(0, appName, side);
        final HBox brand = new HBox(10, logo, titles);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(4, 6, 14, 6));

        final VBox grow = new VBox();
        VBox.setVgrow(grow, Priority.ALWAYS);
        final VBox sidebar = new VBox(6, brand, nav, grow, userBox());
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 12, 14, 12));
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        setLeft(sidebar);

        final Label greeting = new Label(I18n.t("shell.greeting", firstName(ctx.session().displayName())));
        greeting.getStyleClass().add(Styles.TEXT_MUTED);
        final HBox top = new HBox(greeting, Ui.spacer(), new TopTools(ctx));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 16, 4, 28));
        content.setPadding(new Insets(6, 28, 24, 28));
        content.setAlignment(Pos.TOP_LEFT);
        final BorderPane main = new BorderPane(content);
        main.setTop(top);
        setCenter(main);

        final String start = ctx.page() != null && pages.containsKey(ctx.page()) ? ctx.page() : list.get(0).key();
        open(start);
    }

    /** Sayfayı açar; başka sayfalardan da çağrılabilir (ör. "Transfer yap" kısayolu). */
    public final void open(String key) {
        final Page page = pages.get(key);
        if (page == null)
            return;
        ctx.setPage(key);
        buttons.get(key).setSelected(true);
        try {
            content.getChildren().setAll(page.factory().get());
        } catch (RuntimeException e) {
            content.getChildren().setAll(Ui.muted(Ui.message(e)));
        }
    }

    /** Sayfayı yeniden kurar (veri değişince). */
    public final void refresh() {
        if (ctx.page() != null)
            open(ctx.page());
    }

    private VBox userBox() {
        final Session session = ctx.session();
        final Label avatar = new Label(initials(session.displayName()));
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(36, 36);
        avatar.setMaxSize(36, 36);
        avatar.setAlignment(Pos.CENTER);
        final Label name = new Label(session.displayName());
        name.getStyleClass().add(Styles.TEXT_BOLD);
        final Label role = Ui.muted(I18n.of(session.role()));
        final VBox who = new VBox(0, name, role);
        final HBox person = new HBox(10, avatar, who);
        person.setAlignment(Pos.CENTER_LEFT);

        final Button password = Ui.button(I18n.t("shell.changePassword"), Feather.KEY, Styles.FLAT, Styles.SMALL);
        password.setOnAction(e -> new PasswordDialog(ctx, session, false).showAndWait()
                .ifPresent(s -> Ui.info(ctx.window(), I18n.t("password.changed"))));
        final Button logout = Ui.button(I18n.t("shell.logout"), Feather.LOG_OUT, Styles.FLAT, Styles.SMALL, Styles.DANGER);
        logout.setOnAction(e -> ctx.nav().logout());
        password.setMaxWidth(Double.MAX_VALUE);
        logout.setMaxWidth(Double.MAX_VALUE);
        password.setAlignment(Pos.CENTER_LEFT);
        logout.setAlignment(Pos.CENTER_LEFT);

        final VBox box = new VBox(8, person, password, logout);
        box.getStyleClass().add("user-box");
        box.setPadding(new Insets(12, 8, 4, 8));
        return box;
    }

    static String initials(String name) {
        final StringBuilder out = new StringBuilder();
        for (String part : name.split(" ")) {
            if (!part.isEmpty() && out.length() < 2)
                out.append(part.substring(0, 1).toUpperCase(I18n.locale()));
        }
        return out.toString();
    }

    private static String firstName(String name) {
        final int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }
}
