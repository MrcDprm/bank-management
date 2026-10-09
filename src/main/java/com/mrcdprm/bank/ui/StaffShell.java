package com.mrcdprm.bank.ui;

import java.util.ArrayList;
import java.util.List;
import org.kordamp.ikonli.feather.Feather;

/** Şube paneli: müşteriler (veznedar ve yönetici), personel yönetimi (sadece yönetici). */
public final class StaffShell extends Shell {

    public StaffShell(AppContext ctx) {
        super(ctx);
        final List<Page> pages = new ArrayList<>();
        pages.add(new Page("customers", Feather.USERS, () -> new CustomersPage(ctx)));
        if (ctx.session().isAdmin())
            pages.add(new Page("staff", Feather.SHIELD, () -> new StaffPage(ctx, this)));
        build(pages);
    }
}
