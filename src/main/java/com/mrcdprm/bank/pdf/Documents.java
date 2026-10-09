package com.mrcdprm.bank.pdf;

import com.mrcdprm.bank.core.Currency;
import com.mrcdprm.bank.core.Money;
import com.mrcdprm.bank.core.Rates;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.data.Records.Account;
import com.mrcdprm.bank.data.Records.Customer;
import com.mrcdprm.bank.data.Records.Transaction;
import com.mrcdprm.bank.service.AccountService.Receipt;
import com.mrcdprm.bank.ui.I18n;
import com.mrcdprm.bank.ui.Views;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/** Dekont ve hesap özeti PDF'leri. Metinler seçili dilde, tutarlar seçili dilin sayı biçiminde yazılır. */
public final class Documents {

    private Documents() {
    }

    /** Dosya adı önerisi: "dekont-K7M2Q9XW3H4P.pdf". */
    public static String receiptFileName(Receipt r) {
        return I18n.t("pdf.receiptFile") + "-" + r.ref() + ".pdf";
    }

    public static String statementFileName(Account a, LocalDate from, LocalDate to) {
        return I18n.t("pdf.statementFile") + "-" + a.iban().substring(a.iban().length() - 4) + "-" + from + "_" + to + ".pdf";
    }

    public static void receipt(Receipt r, Path file) throws IOException {
        try (PdfDocument pdf = new PdfDocument()) {
            pdf.newPage();
            header(pdf, I18n.t("pdf.receiptTitle"), I18n.t("pdf.receiptNo", r.ref()));

            final float left = PdfDocument.MARGIN;
            pdf.y -= 10;
            row(pdf, I18n.t("pdf.date"), I18n.time(r.createdAt()));
            row(pdf, I18n.t("pdf.kind"), kind(r.kind()));
            pdf.y -= 8;
            if (r.fromIban() != null || r.fromName() != null) {
                section(pdf, I18n.t("pdf.sender"));
                row(pdf, I18n.t("pdf.name"), nvl(r.fromName()));
                if (r.fromIban() != null)
                    row(pdf, "IBAN", Views.iban(r.fromIban()));
            }
            if (r.toIban() != null || r.toName() != null) {
                section(pdf, I18n.t("pdf.recipient"));
                row(pdf, I18n.t("pdf.name"), nvl(r.toName()));
                if (r.toIban() != null)
                    row(pdf, "IBAN", Views.iban(r.toIban()));
            }
            section(pdf, I18n.t("pdf.details"));
            row(pdf, I18n.t("pdf.amount"), money(r.amount(), r.currency()));
            if (r.currency() != r.creditedCurrency()) {
                row(pdf, I18n.t("pdf.credited"), money(r.creditedAmount(), r.creditedCurrency()));
                row(pdf, I18n.t("pdf.rate"), rate(r));
            }
            if (r.description() != null && !r.description().isBlank() && r.currency() == r.creditedCurrency())
                row(pdf, I18n.t("pdf.description"), r.description());

            pdf.y -= 24;
            pdf.box(left, pdf.y - 34, PdfDocument.WIDTH - 2 * left, 44, new java.awt.Color(0xf6, 0xf8, 0xfa));
            pdf.text(I18n.t("pdf.total"), left + 14, pdf.y - 17, pdf.regular, 11, PdfDocument.MUTED);
            pdf.textRight(money(r.amount(), r.currency()), PdfDocument.WIDTH - left - 14, pdf.y - 18, pdf.bold, 16,
                    PdfDocument.TEXT);
            pdf.watermark(I18n.t("pdf.watermark"));
            footer(pdf);
            pdf.save(file);
        }
    }

    /**
     * Hesap özeti: dönem başı ve sonu bakiyesi, toplam giriş/çıkış ve dönemdeki hareketler (eskiden yeniye).
     * Uzun dönemlerde yeni sayfaya geçilir, tablo başlığı her sayfada tekrar yazılır.
     */
    public static void statement(Account account, Customer owner, List<Transaction> newestFirst, LocalDate from,
                                 LocalDate to, LocalDateTime createdAt, Path file) throws IOException {
        final List<Transaction> rows = newestFirst.reversed();
        final Currency currency = account.currency();
        long in = 0;
        long out = 0;
        for (Transaction t : rows) {
            if (t.type().incoming())
                in += t.amount();
            else
                out += t.amount();
        }
        final long closing = rows.isEmpty() ? -1 : rows.getLast().balanceAfter();
        final long opening = rows.isEmpty() ? -1
                : rows.getFirst().balanceAfter() + (rows.getFirst().type().incoming() ? -rows.getFirst().amount()
                        : rows.getFirst().amount());

        try (PdfDocument pdf = new PdfDocument()) {
            pdf.newPage();
            header(pdf, I18n.t("pdf.statementTitle"), I18n.t("pdf.period", I18n.date(from), I18n.date(to)));
            pdf.y -= 10;
            row(pdf, I18n.t("pdf.customer"), owner.fullName());
            row(pdf, I18n.t("pdf.account"), Views.accountName(account));
            row(pdf, "IBAN", Views.iban(account.iban()));
            row(pdf, I18n.t("pdf.createdAt"), I18n.time(createdAt));
            pdf.y -= 12;

            // Özet kutuları
            final float left = PdfDocument.MARGIN;
            final float boxWidth = (PdfDocument.WIDTH - 2 * left - 30) / 4;
            final String[][] summary = {
                {I18n.t("pdf.opening"), opening < 0 ? "-" : money(opening, currency)},
                {I18n.t("pdf.totalIn"), money(in, currency)},
                {I18n.t("pdf.totalOut"), money(out, currency)},
                {I18n.t("pdf.closing"), closing < 0 ? "-" : money(closing, currency)},
            };
            for (int i = 0; i < 4; i++) {
                final float x = left + i * (boxWidth + 10);
                pdf.box(x, pdf.y - 46, boxWidth, 50, new java.awt.Color(0xf6, 0xf8, 0xfa));
                pdf.text(summary[i][0], x + 10, pdf.y - 14, pdf.regular, 8.5f, PdfDocument.MUTED);
                pdf.text(pdf.fit(summary[i][1], pdf.bold, 11, boxWidth - 20), x + 10, pdf.y - 34, pdf.bold, 11,
                        PdfDocument.TEXT);
            }
            pdf.y -= 72;

            tableHeader(pdf);
            if (rows.isEmpty())
                pdf.text(I18n.t("history.empty"), left, pdf.y - 14, pdf.regular, 10, PdfDocument.MUTED);
            for (Transaction t : rows) {
                if (pdf.y < PdfDocument.MARGIN + 60) {
                    pdf.newPage();
                    tableHeader(pdf);
                }
                final float yRow = pdf.y - 14;
                pdf.text(I18n.time(t.createdAt()), left, yRow, pdf.regular, 8.5f, PdfDocument.TEXT);
                final String desc = Views.title(t) + (Views.detail(t).isEmpty() ? "" : " · " + Views.detail(t));
                pdf.text(pdf.fit(desc, pdf.regular, 8.5f, 230), left + 92, yRow, pdf.regular, 8.5f, PdfDocument.TEXT);
                pdf.textRight(Money.formatSigned(t.amount(), t.type().incoming(), currency, I18n.locale()),
                        left + 415, yRow, pdf.regular, 8.5f, t.type().incoming() ? PdfDocument.GREEN : PdfDocument.RED);
                pdf.textRight(money(t.balanceAfter(), currency), PdfDocument.WIDTH - left, yRow, pdf.regular, 8.5f,
                        PdfDocument.TEXT);
                pdf.line(left, pdf.y - 20, PdfDocument.WIDTH - left, PdfDocument.LINE, 0.4f);
                pdf.y -= 20;
            }
            pdf.finishPage(); // sayfa altlıkları toplam sayfa sayısı belli olunca ayrı akışla eklenir
            pageFooters(pdf);
            pdf.save(file);
        }
    }

    private static void tableHeader(PdfDocument pdf) throws IOException {
        final float left = PdfDocument.MARGIN;
        pdf.text(I18n.t("col.date"), left, pdf.y - 12, pdf.bold, 8.5f, PdfDocument.MUTED);
        pdf.text(I18n.t("col.description"), left + 92, pdf.y - 12, pdf.bold, 8.5f, PdfDocument.MUTED);
        pdf.textRight(I18n.t("col.amount"), left + 415, pdf.y - 12, pdf.bold, 8.5f, PdfDocument.MUTED);
        pdf.textRight(I18n.t("col.balance"), PdfDocument.WIDTH - left, pdf.y - 12, pdf.bold, 8.5f, PdfDocument.MUTED);
        pdf.line(left, pdf.y - 18, PdfDocument.WIDTH - left, PdfDocument.TEXT, 0.8f);
        pdf.y -= 20;
    }

    /** Her sayfaya "Sayfa 2 / 5" ve uyarı satırı (toplam sayfa sayısı ancak sonda bilindiği için sonradan). */
    private static void pageFooters(PdfDocument pdf) throws IOException {
        final int total = pdf.doc.getNumberOfPages();
        int index = 1;
        for (PDPage page : pdf.doc.getPages()) {
            try (PDPageContentStream s = new PDPageContentStream(pdf.doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                final String text = I18n.t("pdf.page", index++, total);
                s.beginText();
                s.setFont(pdf.regular, 8);
                s.setNonStrokingColor(PdfDocument.MUTED);
                s.newLineAtOffset(PdfDocument.WIDTH - PdfDocument.MARGIN - pdf.width(text, pdf.regular, 8), 28);
                s.showText(pdf.safe(text, pdf.regular));
                s.endText();
                s.beginText();
                s.setFont(pdf.regular, 8);
                s.newLineAtOffset(PdfDocument.MARGIN, 28);
                s.showText(pdf.safe(I18n.t("pdf.disclaimer"), pdf.regular));
                s.endText();
            }
        }
    }

    private static void header(PdfDocument pdf, String title, String subtitle) throws IOException {
        final float left = PdfDocument.MARGIN;
        pdf.text(I18n.t("app.name"), left, pdf.y - 6, pdf.bold, 13, PdfDocument.ACCENT);
        pdf.text(I18n.t("pdf.bankLine"), left, pdf.y - 20, pdf.regular, 8.5f, PdfDocument.MUTED);
        pdf.textRight(title, PdfDocument.WIDTH - left, pdf.y - 6, pdf.bold, 15, PdfDocument.TEXT);
        pdf.textRight(subtitle, PdfDocument.WIDTH - left, pdf.y - 21, pdf.regular, 9, PdfDocument.MUTED);
        pdf.y -= 32;
        pdf.line(left, pdf.y, PdfDocument.WIDTH - left, PdfDocument.ACCENT, 1.5f);
        pdf.y -= 8;
    }

    private static void section(PdfDocument pdf, String title) throws IOException {
        pdf.y -= 10;
        pdf.text(title.toUpperCase(I18n.locale()), PdfDocument.MARGIN, pdf.y - 12, pdf.bold, 9, PdfDocument.ACCENT);
        pdf.y -= 18;
    }

    private static void row(PdfDocument pdf, String label, String value) throws IOException {
        pdf.text(label, PdfDocument.MARGIN, pdf.y - 13, pdf.regular, 10, PdfDocument.MUTED);
        pdf.text(pdf.fit(value, pdf.regular, 10, 330), PdfDocument.MARGIN + 150, pdf.y - 13, pdf.regular, 10,
                PdfDocument.TEXT);
        pdf.y -= 19;
    }

    private static void footer(PdfDocument pdf) throws IOException {
        pdf.text(I18n.t("pdf.disclaimer"), PdfDocument.MARGIN, 28, pdf.regular, 8, PdfDocument.MUTED);
    }

    private static String kind(TxType type) {
        return switch (type) {
            case TRANSFER_OUT, TRANSFER_IN -> I18n.t("pdf.kindTransfer");
            case EXCHANGE_OUT, EXCHANGE_IN -> I18n.t("pdf.kindExchange");
            case DEPOSIT -> I18n.t("tx.cashDeposit");
            case WITHDRAWAL -> I18n.t("tx.cashWithdrawal");
            case INTEREST -> I18n.t("tx.interest");
        };
    }

    /**
     * Döviz işleminde uygulanan kur (bankanın kur tablosundan): döviz alırken satış kuru, satarken alış kuru.
     * Döviz-döviz işlemde iki kurun oranı yazılır: "1 EUR = 1,1446 USD".
     */
    private static String rate(Receipt r) {
        final Currency from = r.currency();
        final Currency to = r.creditedCurrency();
        final Currency foreign = from == Currency.TRY ? to : from;
        final Currency quote = from == Currency.TRY || to == Currency.TRY ? Currency.TRY : to;
        final BigDecimal value = from == Currency.TRY ? Rates.sell(to)
                : to == Currency.TRY ? Rates.buy(from)
                : Rates.buy(from).divide(Rates.sell(to), 4, RoundingMode.DOWN);
        final String number = value.setScale(4, RoundingMode.DOWN).toPlainString().replace('.', I18n.isTurkish() ? ',' : '.');
        return "1 " + foreign.name() + " = " + number + " " + Views.currencyCode(quote);
    }

    private static String money(long minor, Currency c) {
        return Money.format(minor, c, I18n.locale());
    }

    private static String nvl(String s) {
        return s == null ? "-" : s;
    }
}
