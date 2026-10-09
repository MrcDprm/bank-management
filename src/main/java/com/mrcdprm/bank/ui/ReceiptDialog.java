package com.mrcdprm.bank.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.bank.core.TxType;
import com.mrcdprm.bank.pdf.Documents;
import com.mrcdprm.bank.service.AccountService.Receipt;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** İşlem özeti ve "PDF dekont kaydet" düğmesi. İşlemden hemen sonra ya da hareket listesinden açılır. */
public final class ReceiptDialog extends Dialog<Void> {

    public ReceiptDialog(AppContext ctx, Receipt r, boolean justDone) {
        initOwner(ctx.window());
        setTitle(I18n.t("receipt.title"));
        Theme.style(getDialogPane());

        final VBox top = new VBox(6);
        top.setAlignment(Pos.CENTER);
        if (justDone) {
            final FontIcon ok = new FontIcon(Feather.CHECK_CIRCLE);
            ok.getStyleClass().add("success-icon");
            top.getChildren().addAll(ok, new Label(I18n.t("receipt.done")));
        }
        final Label amount = new Label(I18n.money(r.amount(), r.currency()));
        amount.getStyleClass().add(Styles.TITLE_1);
        top.getChildren().add(amount);
        if (r.currency() != r.creditedCurrency())
            top.getChildren().add(Ui.muted("→ " + I18n.money(r.creditedAmount(), r.creditedCurrency())));

        final GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(8);
        int row = 0;
        if (r.fromName() != null)
            row = add(grid, row, I18n.t("pdf.sender"), r.fromName() + (r.fromIban() == null ? "" : "\n" + Views.iban(r.fromIban())));
        if (r.toName() != null)
            row = add(grid, row, I18n.t("pdf.recipient"), r.toName() + (r.toIban() == null ? "" : "\n" + Views.iban(r.toIban())));
        row = add(grid, row, I18n.t("pdf.date"), I18n.time(r.createdAt()));
        if (r.description() != null && !r.description().isBlank() && r.kind() != TxType.EXCHANGE_OUT)
            row = add(grid, row, I18n.t("pdf.description"), r.description());
        add(grid, row, I18n.t("receipt.ref"), r.ref());

        final VBox content = new VBox(18, top, grid);
        content.setPadding(new Insets(10, 10, 0, 10));
        content.setPrefWidth(440);
        getDialogPane().setContent(content);

        final ButtonType pdf = new ButtonType(I18n.t("receipt.savePdf"), ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(pdf, new ButtonType(I18n.t("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        final Node pdfButton = getDialogPane().lookupButton(pdf);
        pdfButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume(); // pencere açık kalsın
            PdfSaver.save(ctx, pdfButton, Documents.receiptFileName(r), file -> Documents.receipt(r, file));
        });
    }

    private static int add(GridPane grid, int row, String label, String value) {
        final Label l = Ui.muted(label);
        final Label v = new Label(value);
        v.setWrapText(true);
        grid.addRow(row, l, v);
        return row + 1;
    }
}
