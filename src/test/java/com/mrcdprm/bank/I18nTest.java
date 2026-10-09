package com.mrcdprm.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mrcdprm.bank.core.AccountStatus;
import com.mrcdprm.bank.core.Category;
import com.mrcdprm.bank.core.Role;
import com.mrcdprm.bank.core.TxType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** İki dil dosyası aynı anahtarlara sahip mi ve kodda kullanılan her metin anahtarı tanımlı mı? */
class I18nTest {

    private static Properties load(String lang) throws IOException {
        final Properties p = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream("/com/mrcdprm/bank/i18n/messages_" + lang + ".properties")) {
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(new TreeSet<>(load("tr").stringPropertyNames()), new TreeSet<>(load("en").stringPropertyNames()));
    }

    @Test
    void everyKeyUsedInCodeExists() throws IOException {
        final Properties tr = load("tr");
        final Set<String> used = new TreeSet<>();
        // I18n.t("a.b") ve I18n.t(x ? "a.b" : "c.d") içindeki bütün "a.b" biçimli metinler
        final Pattern call = Pattern.compile("I18n\\.t\\(([^;]*?)\\)");
        final Pattern literal = Pattern.compile("\"([a-z]+\\.[a-zA-Z0-9.]+)\"");
        final Pattern error = Pattern.compile("BankException\\(\"([a-zA-Z]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                final String code = Files.readString(file);
                final Matcher m = call.matcher(code);
                while (m.find()) {
                    final Matcher l = literal.matcher(m.group(1));
                    while (l.find())
                        used.add(l.group(1));
                }
                final Matcher e = error.matcher(code);
                while (e.find())
                    used.add("error." + e.group(1));
            }
        }
        for (Enum<?>[] values : List.<Enum<?>[]>of(Role.values(), AccountStatus.values(), TxType.values(), Category.values()))
            for (Enum<?> v : values)
                used.add("enum." + v.getDeclaringClass().getSimpleName() + "." + v.name());
        for (String rule : List.of("length", "tooLong", "upper", "lower", "digit", "containsLogin"))
            used.add("password.rule." + rule);
        for (String nav : List.of("overview", "transfer", "history", "exchange", "deposits", "insights", "recipients",
                "profile", "customers", "staff"))
            used.add("nav." + nav);

        final List<String> missing = new ArrayList<>();
        for (String key : used)
            if (!tr.containsKey(key) && !key.endsWith("."))
                missing.add(key);
        assertTrue(missing.isEmpty(), "Eksik anahtarlar: " + missing);
    }
}
