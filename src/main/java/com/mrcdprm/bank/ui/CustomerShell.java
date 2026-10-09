package com.mrcdprm.bank.ui;

import java.util.List;
import org.kordamp.ikonli.feather.Feather;

/** Müşteri paneli: menü ve sayfalar arası paylaşılan seçimler. */
public final class CustomerShell extends Shell {

    /** Sayfalar arası geçişte önceden seçilecek hesap / alıcı (ör. kartta "Hareketler"e basınca). */
    Long preselectedAccount;
    String preselectedIban;

    public CustomerShell(AppContext ctx) {
        super(ctx);
        build(List.of(
                new Page("overview", Feather.HOME, () -> new OverviewPage(ctx, this)),
                new Page("transfer", Feather.SEND, () -> new TransferPage(ctx, this)),
                new Page("history", Feather.LIST, () -> new HistoryPage(ctx, this)),
                new Page("exchange", Feather.REFRESH_CW, () -> new ExchangePage(ctx, this)),
                new Page("deposits", Feather.TRENDING_UP, () -> new DepositsPage(ctx, this)),
                new Page("insights", Feather.PIE_CHART, () -> new InsightsPage(ctx)),
                new Page("recipients", Feather.USERS, () -> new RecipientsPage(ctx, this)),
                new Page("profile", Feather.SETTINGS, () -> new ProfilePage(ctx))));
    }

    void openAccount(String page, long accountId) {
        preselectedAccount = accountId;
        open(page);
    }

    void sendTo(String iban) {
        preselectedIban = iban;
        open("transfer");
    }

    /** Ön seçimi bir kez kullanır ve temizler. */
    Long takeAccount() {
        final Long id = preselectedAccount;
        preselectedAccount = null;
        return id;
    }

    String takeIban() {
        final String iban = preselectedIban;
        preselectedIban = null;
        return iban;
    }
}
