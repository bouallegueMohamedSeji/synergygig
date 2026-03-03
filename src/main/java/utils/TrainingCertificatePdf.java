package utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Generates professional training certificate PDFs using Apache PDFBox.
 */
public class TrainingCertificatePdf {

    // ── Colors ──
    private static final int[] DARK_NAVY   = {25, 25, 60};
    private static final int[] ACCENT_BLUE = {44, 102, 110};   // teal from app theme
    private static final int[] GOLD        = {180, 150, 50};
    private static final int[] MUTED       = {100, 100, 120};
    private static final int[] LIGHT_MUTED = {150, 150, 165};
    private static final int[] BORDER_DARK = {44, 102, 110};
    private static final int[] BORDER_GOLD = {200, 175, 80};

    /**
     * Export a training certificate to a PDF file (landscape A4).
     * Overload for backward compatibility (no signature).
     */
    public static void export(String filePath, String recipientName, String courseName,
                              double hours, String certificateNumber, Timestamp issuedAt)
            throws IOException {
        export(filePath, recipientName, courseName, hours, certificateNumber, issuedAt, null, null);
    }

    /**
     * Export a training certificate to a PDF file (landscape A4), with optional drawn signature.
     *
     * @param signatureBase64  base64-encoded PNG of the HR/Admin drawn signature (nullable)
     * @param signerName       name of the person who signed (nullable)
     */
    public static void export(String filePath, String recipientName, String courseName,
                              double hours, String certificateNumber, Timestamp issuedAt,
                              String signatureBase64, String signerName)
            throws IOException {

        try (PDDocument doc = new PDDocument()) {
            // Landscape orientation
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);

            float w = page.getMediaBox().getWidth();
            float h = page.getMediaBox().getHeight();

            // If there is a signature, create the image object from base64 BEFORE opening the content stream
            PDImageXObject sigImage = null;
            if (signatureBase64 != null && !signatureBase64.isEmpty()) {
                try {
                    byte[] sigBytes = Base64.getDecoder().decode(signatureBase64);
                    sigImage = PDImageXObject.createFromByteArray(doc, sigBytes, "signature.png");
                } catch (Exception ignored) { /* invalid base64 — skip */ }
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                drawBackground(cs, w, h);
                drawBorders(cs, w, h);
                drawCornerOrnaments(cs, w, h);
                drawContent(cs, w, h, recipientName, courseName, hours, certificateNumber, issuedAt,
                        sigImage, signerName);
            }

            doc.save(filePath);
        }
    }

    // ── Background & visual elements ──

    private static void drawBackground(PDPageContentStream cs, float w, float h) throws IOException {
        // Subtle cream/off-white background
        cs.setNonStrokingColor(252, 250, 245);
        cs.addRect(0, 0, w, h);
        cs.fill();
    }

    private static void drawBorders(PDPageContentStream cs, float w, float h) throws IOException {
        // Outer border - teal
        float m = 20;
        cs.setStrokingColor(BORDER_DARK[0], BORDER_DARK[1], BORDER_DARK[2]);
        cs.setLineWidth(4);
        cs.addRect(m, m, w - 2 * m, h - 2 * m);
        cs.stroke();

        // Inner border - gold
        float m2 = 30;
        cs.setStrokingColor(BORDER_GOLD[0], BORDER_GOLD[1], BORDER_GOLD[2]);
        cs.setLineWidth(1.5f);
        cs.addRect(m2, m2, w - 2 * m2, h - 2 * m2);
        cs.stroke();

        // Inner-inner thin border
        float m3 = 34;
        cs.setStrokingColor(BORDER_GOLD[0], BORDER_GOLD[1], BORDER_GOLD[2]);
        cs.setLineWidth(0.5f);
        cs.addRect(m3, m3, w - 2 * m3, h - 2 * m3);
        cs.stroke();
    }

    private static void drawCornerOrnaments(PDPageContentStream cs, float w, float h) throws IOException {
        cs.setStrokingColor(BORDER_GOLD[0], BORDER_GOLD[1], BORDER_GOLD[2]);
        cs.setLineWidth(1.5f);
        float m = 34;
        float len = 30;

        // Top-left corner
        drawCorner(cs, m, h - m, len, -1, 1);
        // Top-right corner
        drawCorner(cs, w - m, h - m, len, 1, 1);
        // Bottom-left corner
        drawCorner(cs, m, m, len, -1, -1);
        // Bottom-right corner
        drawCorner(cs, w - m, m, len, 1, -1);
    }

    private static void drawCorner(PDPageContentStream cs, float x, float y,
                                    float len, int flipX, int flipY) throws IOException {
        // Small decorative L-shaped corner
        cs.moveTo(x + flipX * len, y);
        cs.lineTo(x, y);
        cs.lineTo(x, y - flipY * len);
        cs.stroke();
    }

    // ── Text content ──

    private static void drawContent(PDPageContentStream cs, float w, float h,
                                     String recipientName, String courseName,
                                     double hours, String certNumber, Timestamp issuedAt,
                                     PDImageXObject signatureImage, String signerName)
            throws IOException {

        float cy = h - 75;

        // ── Company name ──
        drawCenteredText(cs, PDType1Font.HELVETICA_BOLD, 13, ACCENT_BLUE, "S Y N E R G Y G I G", w, cy);
        cy -= 8;

        // Gold line under company name
        cs.setStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        cs.setLineWidth(0.8f);
        cs.moveTo(w / 2 - 70, cy);
        cs.lineTo(w / 2 + 70, cy);
        cs.stroke();
        cy -= 38;

        // ── Title ──
        drawCenteredText(cs, PDType1Font.HELVETICA_BOLD, 36, DARK_NAVY, "Certificate of Completion", w, cy);
        cy -= 16;

        // Decorative divider with gold center
        cs.setStrokingColor(ACCENT_BLUE[0], ACCENT_BLUE[1], ACCENT_BLUE[2]);
        cs.setLineWidth(1.2f);
        cs.moveTo(w / 2 - 150, cy);
        cs.lineTo(w / 2 - 20, cy);
        cs.stroke();
        cs.moveTo(w / 2 + 20, cy);
        cs.lineTo(w / 2 + 150, cy);
        cs.stroke();
        // Gold diamond centre
        cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        float dx = w / 2, dy = cy;
        cs.moveTo(dx, dy + 5);
        cs.lineTo(dx + 5, dy);
        cs.lineTo(dx, dy - 5);
        cs.lineTo(dx - 5, dy);
        cs.closePath();
        cs.fill();
        cy -= 35;

        // ── "This is to certify that" ──
        drawCenteredText(cs, PDType1Font.HELVETICA, 13, MUTED, "This is to certify that", w, cy);
        cy -= 42;

        // ── Recipient name ──
        drawCenteredText(cs, PDType1Font.HELVETICA_BOLD, 30, DARK_NAVY, recipientName, w, cy);
        cy -= 14;

        // Underline for name
        float nameWidth = getTextWidth(recipientName, PDType1Font.HELVETICA_BOLD, 30);
        cs.setStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
        cs.setLineWidth(1);
        cs.moveTo((w - nameWidth) / 2, cy);
        cs.lineTo((w + nameWidth) / 2, cy);
        cs.stroke();
        cy -= 30;

        // ── "has successfully completed" ──
        drawCenteredText(cs, PDType1Font.HELVETICA, 13, MUTED, "has successfully completed the training course", w, cy);
        cy -= 40;

        // ── Course name ──
        // Handle long course names by scaling font
        int courseFontSize = courseName.length() > 40 ? 18 : (courseName.length() > 30 ? 20 : 24);
        drawCenteredText(cs, PDType1Font.HELVETICA_BOLD, courseFontSize, ACCENT_BLUE, courseName, w, cy);
        cy -= 30;

        // ── Duration ──
        String durationStr = "Duration: " + (hours == (int) hours ? String.valueOf((int) hours) : String.valueOf(hours)) + " hours";
        drawCenteredText(cs, PDType1Font.HELVETICA, 11, LIGHT_MUTED, durationStr, w, cy);
        cy -= 45;

        // ── Issue date & Signature line ──
        String dateStr = issuedAt != null
                ? issuedAt.toLocalDateTime().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"))
                : "N/A";

        // Left side: Date
        float colLeft = w * 0.25f;
        float colRight = w * 0.75f;

        // Date label
        drawCenteredTextAt(cs, PDType1Font.HELVETICA, 10, LIGHT_MUTED, "Date of Issue", colLeft, cy - 15);
        cs.setStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
        cs.setLineWidth(0.5f);
        cs.moveTo(colLeft - 60, cy);
        cs.lineTo(colLeft + 60, cy);
        cs.stroke();
        drawCenteredTextAt(cs, PDType1Font.HELVETICA, 12, DARK_NAVY, dateStr, colLeft, cy + 10);

        // Right side: Authorized signature (drawn image or fallback text)
        if (signatureImage != null) {
            // Render the drawn signature image (scaled to fit above the line)
            float sigImgW = 120;
            float sigImgH = 40;
            float sigImgX = colRight - sigImgW / 2;
            float sigImgY = cy + 5;
            cs.drawImage(signatureImage, sigImgX, sigImgY, sigImgW, sigImgH);

            // Signer name label under the line
            String sigLabel = signerName != null && !signerName.isEmpty() ? signerName : "Training Director";
            drawCenteredTextAt(cs, PDType1Font.HELVETICA, 10, LIGHT_MUTED, sigLabel, colRight, cy - 15);
        } else {
            drawCenteredTextAt(cs, PDType1Font.HELVETICA, 10, LIGHT_MUTED, "Training Director", colRight, cy - 15);
        }
        cs.setStrokingColor(MUTED[0], MUTED[1], MUTED[2]);
        cs.setLineWidth(0.5f);
        cs.moveTo(colRight - 60, cy);
        cs.lineTo(colRight + 60, cy);
        cs.stroke();
        if (signatureImage == null) {
            drawCenteredTextAt(cs, PDType1Font.HELVETICA_BOLD, 12, DARK_NAVY, "SynergyGig HR", colRight, cy + 10);
        }

        // ── Certificate ID at bottom ──
        drawCenteredText(cs, PDType1Font.COURIER, 8, LIGHT_MUTED,
                "Certificate ID: " + certNumber, w, 50);

        // ── Footer ──
        drawCenteredText(cs, PDType1Font.HELVETICA, 8, LIGHT_MUTED,
                "SynergyGig Training & Development Platform  |  This certificate verifies course completion", w, 38);
    }

    // ── Helper methods ──

    private static void drawCenteredText(PDPageContentStream cs, PDType1Font font, int size,
                                          int[] color, String text, float pageWidth, float y)
            throws IOException {
        float textWidth = getTextWidth(text, font, size);
        float x = (pageWidth - textWidth) / 2;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static void drawCenteredTextAt(PDPageContentStream cs, PDType1Font font, int size,
                                            int[] color, String text, float centerX, float y)
            throws IOException {
        float textWidth = getTextWidth(text, font, size);
        float x = centerX - textWidth / 2;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color[0], color[1], color[2]);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    /** Calculate actual text width using font metrics. */
    private static float getTextWidth(String text, PDType1Font font, int size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }
}
