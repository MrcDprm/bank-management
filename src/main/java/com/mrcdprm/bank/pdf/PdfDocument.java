package com.mrcdprm.bank.pdf;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

/**
 * PDFBox üzerinde küçük bir yazma katmanı: A4 sayfa, yukarıdan aşağı ilerleyen imleç (y), metin, çizgi, kutu.
 * Türkçe karakterler için Noto Sans yazı tipi gömülür (PDF'in standart Helvetica'sında ş, ğ, ı yok).
 */
final class PdfDocument implements AutoCloseable {

    static final float MARGIN = 50;
    static final float WIDTH = PDRectangle.A4.getWidth();
    static final float HEIGHT = PDRectangle.A4.getHeight();
    static final Color TEXT = new Color(0x1f, 0x23, 0x28);
    static final Color MUTED = new Color(0x65, 0x6d, 0x76);
    static final Color LINE = new Color(0xd0, 0xd7, 0xde);
    static final Color ACCENT = new Color(0x09, 0x69, 0xda);
    static final Color GREEN = new Color(0x1a, 0x7f, 0x37);
    static final Color RED = new Color(0xcf, 0x22, 0x2e);

    final PDDocument doc = new PDDocument();
    final PDFont regular;
    final PDFont bold;
    private final Map<Integer, Boolean> glyphCache = new HashMap<>();
    private PDPageContentStream out;
    float y;

    PdfDocument() {
        try {
            regular = load("NotoSans-Regular.ttf");
            bold = load("NotoSans-Bold.ttf");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PDFont load(String name) throws IOException {
        try (InputStream in = PdfDocument.class.getResourceAsStream("/com/mrcdprm/bank/fonts/" + name)) {
            return PDType0Font.load(doc, in); // sadece kullanılan karakterler gömülür (subset)
        }
    }

    void newPage() throws IOException {
        if (out != null)
            out.close();
        final PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        out = new PDPageContentStream(doc, page);
        y = HEIGHT - MARGIN;
    }

    void text(String text, float x, float yPos, PDFont font, float size, Color color) throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.setNonStrokingColor(color);
        out.newLineAtOffset(x, yPos);
        out.showText(safe(text, font));
        out.endText();
    }

    /** Sağa yaslı metin (tutar sütunları için). */
    void textRight(String text, float right, float yPos, PDFont font, float size, Color color) throws IOException {
        text(text, right - width(text, font, size), yPos, font, size, color);
    }

    float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(safe(text, font)) / 1000 * size;
    }

    /** Sığmayan metni "…" ile kısaltır. */
    String fit(String text, PDFont font, float size, float maxWidth) throws IOException {
        if (width(text, font, size) <= maxWidth)
            return text;
        String s = text;
        while (s.length() > 1 && width(s + "…", font, size) > maxWidth)
            s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    void line(float x1, float yPos, float x2, Color color, float thickness) throws IOException {
        out.setStrokingColor(color);
        out.setLineWidth(thickness);
        out.moveTo(x1, yPos);
        out.lineTo(x2, yPos);
        out.stroke();
    }

    void box(float x, float yPos, float w, float h, Color fill) throws IOException {
        out.setNonStrokingColor(fill);
        out.addRect(x, yPos, w, h);
        out.fill();
    }

    /** Sayfanın ortasına çapraz, soluk "ÖRNEK" damgası: belge gerçek banka belgesi sanılmasın. */
    void watermark(String text) throws IOException {
        final PDExtendedGraphicsState faint = new PDExtendedGraphicsState();
        faint.setNonStrokingAlphaConstant(0.07f);
        out.saveGraphicsState();
        out.setGraphicsStateParameters(faint);
        out.beginText();
        out.setFont(bold, 96);
        out.setNonStrokingColor(Color.GRAY);
        final float w = width(text, bold, 96);
        out.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), WIDTH / 2 - w * 0.41f, HEIGHT / 2 - w * 0.29f));
        out.showText(safe(text, bold));
        out.endText();
        out.restoreGraphicsState();
    }

    /** Yazı tipinde karşılığı olmayan karakterleri (emoji, kontrol karakteri) "?" yapar; PDFBox hata vermesin. */
    String safe(String text, PDFont font) {
        if (text == null)
            return "";
        final StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp)) {
                sb.append(' ');
                return;
            }
            final boolean ok = glyphCache.computeIfAbsent(cp, c -> {
                try {
                    font.encode(new String(Character.toChars(c)));
                    return true;
                } catch (IOException | IllegalArgumentException e) {
                    return false;
                }
            });
            if (ok)
                sb.append(Character.toChars(cp));
            else if (cp == '→')
                sb.append("->"); // Noto Sans'ta ok işareti yok
            else if (cp == '≈')
                sb.append('~');
            else
                sb.append('?');
        });
        return sb.toString();
    }

    /** Açık sayfanın yazma akışını kapatır. */
    void finishPage() throws IOException {
        if (out != null) {
            out.close();
            out = null;
        }
    }

    void save(Path file) throws IOException {
        finishPage();
        doc.save(file.toFile());
    }

    @Override
    public void close() throws IOException {
        if (out != null)
            out.close();
        doc.close();
    }
}
