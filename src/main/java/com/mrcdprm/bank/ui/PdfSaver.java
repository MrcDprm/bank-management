package com.mrcdprm.bank.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Node;
import javafx.stage.FileChooser;

/** PDF kaydetme: dosya seçme penceresi, arka planda üretim, bitince dosyayı varsayılan uygulamada açma. */
public final class PdfSaver {

    @FunctionalInterface
    public interface Writer {
        void write(Path file) throws Exception;
    }

    private static File lastFolder;

    private PdfSaver() {
    }

    public static void save(AppContext ctx, Node busy, String suggestedName, Writer writer) {
        final FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("pdf.saveTitle"));
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        final File folder = lastFolder != null ? lastFolder : defaultFolder();
        if (folder != null && folder.isDirectory())
            chooser.setInitialDirectory(folder);
        // pencere, düğmenin bulunduğu pencerenin (ör. dekont) önünde açılsın
        final javafx.stage.Window owner = busy.getScene() != null ? busy.getScene().getWindow() : ctx.window();
        final File chosen = chooser.showSaveDialog(owner);
        if (chosen == null)
            return;
        lastFolder = chosen.getParentFile();
        final Path file = chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf") ? chosen.toPath()
                : chosen.toPath().resolveSibling(chosen.getName() + ".pdf");
        Ui.background(busy, () -> {
            writer.write(file);
            return file;
        }, saved -> ctx.openUrl(saved.toUri().toString()), ex -> Ui.error(ctx.window(), ex));
    }

    private static File defaultFolder() {
        final Path documents = Path.of(System.getProperty("user.home"), "Documents");
        return Files.isDirectory(documents) ? documents.toFile() : null;
    }
}
