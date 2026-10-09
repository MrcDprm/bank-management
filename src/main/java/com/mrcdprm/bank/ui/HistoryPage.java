package com.mrcdprm.bank.ui;

import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.service.Session;
import java.util.List;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Müşterinin hesap hareketleri sayfası (kapalı hesapların geçmişi de görülebilir). */
final class HistoryPage extends VBox {

    HistoryPage(AppContext ctx, CustomerShell shell) {
        super(16);
        final Session s = ctx.session();
        final List<Account> accounts = ctx.bank().accounts().listForCustomer(s, s.userId());
        final HistoryView view = new HistoryView(ctx, accounts, shell.takeAccount(), true);
        VBox.setVgrow(view, Priority.ALWAYS);
        getChildren().addAll(Ui.header(I18n.t("nav.history"), I18n.t("history.subtitle")), view);
    }
}
