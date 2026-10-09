package com.mrcdprm.bank.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Kullanıcı tercihleri (dil, tema). Dosyadan okunan değere güvenilmez: bilinmeyen değer yerine varsayılan kullanılır.
 * Şifre ya da oturum bilgisi asla saklanmaz.
 */
public final class Settings {

    private final Path file;
    private final Properties props = new Properties();

    public Settings(Path folder) {
        this.file = folder.resolve("settings.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException | IllegalArgumentException ignored) {
                // bozuk dosya: varsayılanlarla devam
            }
        }
    }

    /** Veri klasörü: %APPDATA%\MrcDprm\BankManager (program klasörüne yazılmaz). */
    public static Path dataFolder() {
        final String override = System.getProperty("bank.dataDir"); // geliştirme ve ekran görüntüleri için
        if (override != null && !override.isBlank())
            return Path.of(override);
        final String appData = System.getenv("APPDATA");
        final Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("MrcDprm").resolve("BankManager");
    }

    public String language() {
        return "en".equals(props.getProperty("language")) ? "en" : "tr";
    }

    public void setLanguage(String language) {
        props.setProperty("language", "en".equals(language) ? "en" : "tr");
        save();
    }

    public boolean darkTheme() {
        return "dark".equals(props.getProperty("theme"));
    }

    public void setDarkTheme(boolean dark) {
        props.setProperty("theme", dark ? "dark" : "light");
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Bank Manager");
            }
        } catch (IOException ignored) {
            // tercih kaydedilemezse uygulama yine çalışır
        }
    }
}
