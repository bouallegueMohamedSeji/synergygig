package utils;

import entities.Contract;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates professional PDF contracts with QR code verification,
 * company branding, digital signature area, and blockchain hash.
 */
public class ContractPdfGenerator {

    private static final float MARGIN = 50;
    private static final float LINE_HEIGHT = 14;
    private static final float BODY_SIZE = 10;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    // Brand colors (teal/dark theme)
    private static final float[] TEAL = {0.173f, 0.400f, 0.431f};       // #2C666E
    private static final float[] DARK_TEAL = {0.027f, 0.224f, 0.235f};  // #07393C
    private static final float[] GOLD = {0.706f, 0.600f, 0.196f};       // #B49932
    private static final float[] LIGHT_BG = {0.988f, 0.976f, 0.949f};   // #FCF9F2
    private static final float[] DARK_BG = {0.039f, 0.035f, 0.047f};    // #0A090C

    /**
     * Generate a professional PDF contract file.
     * @param contract The contract entity
     * @param offerTitle Title of the associated offer
     * @param ownerName Name of the contract owner (company/HR)
     * @param applicantName Name of the contractor
     * @return Path to the generated PDF file
     */
    public static File generatePdf(Contract contract, String offerTitle,
                                    String ownerName, String applicantName) throws Exception {
        Path tempDir = Files.createTempDirectory("synergygig_contracts");
        File pdfFile = tempDir.resolve("contract_" + contract.getId() + ".pdf").toFile();

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float y = PAGE_HEIGHT;

            // ══════════════════════════════════════════════════════
            //  HEADER BANNER (teal gradient area)
            // ══════════════════════════════════════════════════════
            cs.setNonStrokingColor(DARK_TEAL[0], DARK_TEAL[1], DARK_TEAL[2]);
            cs.addRect(0, PAGE_HEIGHT - 120, PAGE_WIDTH, 120);
            cs.fill();
            // Accent stripe
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.addRect(0, PAGE_HEIGHT - 124, PAGE_WIDTH, 4);
            cs.fill();
            // Gold thin line
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.addRect(0, PAGE_HEIGHT - 126, PAGE_WIDTH, 2);
            cs.fill();

            // Logo text
            y = PAGE_HEIGHT - 45;
            cs.beginText();
            cs.setNonStrokingColor(0.565f, 0.867f, 0.941f); // #90DDF0
            cs.setFont(PDType1Font.HELVETICA_BOLD, 28);
            float logoW = PDType1Font.HELVETICA_BOLD.getStringWidth("SYNERGYGIG") / 1000f * 28;
            cs.newLineAtOffset((PAGE_WIDTH - logoW) / 2, y);
            cs.showText("SYNERGYGIG");
            cs.endText();

            y -= 28;
            cs.beginText();
            cs.setNonStrokingColor(0.941f, 0.929f, 0.933f); // #F0EDEE
            cs.setFont(PDType1Font.HELVETICA, 12);
            String subtitle = "Official Contract Agreement";
            float stW = PDType1Font.HELVETICA.getStringWidth(subtitle) / 1000f * 12;
            cs.newLineAtOffset((PAGE_WIDTH - stW) / 2, y);
            cs.showText(subtitle);
            cs.endText();

            y -= 22;
            cs.beginText();
            cs.setNonStrokingColor(0.565f, 0.867f, 0.941f);
            cs.setFont(PDType1Font.HELVETICA, 9);
            String tagline = "Secure HR Platform  \u2022  Blockchain Verified";
            float tgW = PDType1Font.HELVETICA.getStringWidth(tagline) / 1000f * 9;
            cs.newLineAtOffset((PAGE_WIDTH - tgW) / 2, y);
            cs.showText(tagline);
            cs.endText();

            // ══════════════════════════════════════════════════════
            //  BODY BACKGROUND
            // ══════════════════════════════════════════════════════
            cs.setNonStrokingColor(LIGHT_BG[0], LIGHT_BG[1], LIGHT_BG[2]);
            cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT - 126);
            cs.fill();

            // Reset text color
            cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);

            // ══════════════════════════════════════════════════════
            //  CONTRACT REFERENCE BAR
            // ══════════════════════════════════════════════════════
            y = PAGE_HEIGHT - 150;
            cs.setNonStrokingColor(0.95f, 0.95f, 0.93f);
            cs.addRect(MARGIN, y - 30, CONTENT_WIDTH, 30);
            cs.fill();
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.addRect(MARGIN, y - 30, 3, 30);
            cs.fill();
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 9);
            cs.newLineAtOffset(MARGIN + 12, y - 20);
            cs.showText("Contract Ref: SG-" + LocalDate.now().getYear() + "-" + String.format("%05d", contract.getId()));
            cs.setFont(PDType1Font.HELVETICA, 9);
            String dateStr = "  |  Issued: " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM dd, yyyy"));
            cs.showText(dateStr);
            cs.endText();

            // ══════════════════════════════════════════════════════
            //  PARTIES SECTION
            // ══════════════════════════════════════════════════════
            y -= 55;
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("PARTIES TO THE AGREEMENT");
            cs.endText();
            y -= 5;
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
            y -= 18;

            // Two-column party boxes
            float boxW = (CONTENT_WIDTH - 20) / 2;
            // Owner box
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(MARGIN, y - 65, boxW, 65);
            cs.fill();
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.setLineWidth(0.5f);
            cs.addRect(MARGIN, y - 65, boxW, 65);
            cs.stroke();
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.addRect(MARGIN, y - 2, boxW, 2);
            cs.fill();
            cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
            drawTextAt(cs, "EMPLOYER / OWNER", MARGIN + 10, y - 18, 8, PDType1Font.HELVETICA_BOLD);
            cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
            drawTextAt(cs, ownerName, MARGIN + 10, y - 34, 11, PDType1Font.HELVETICA_BOLD);
            drawTextAt(cs, "SynergyGig Platform", MARGIN + 10, y - 50, 9, PDType1Font.HELVETICA);

            // Applicant box
            float box2X = MARGIN + boxW + 20;
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(box2X, y - 65, boxW, 65);
            cs.fill();
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.addRect(box2X, y - 65, boxW, 65);
            cs.stroke();
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.addRect(box2X, y - 2, boxW, 2);
            cs.fill();
            cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
            drawTextAt(cs, "CONTRACTOR / APPLICANT", box2X + 10, y - 18, 8, PDType1Font.HELVETICA_BOLD);
            cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
            drawTextAt(cs, applicantName, box2X + 10, y - 34, 11, PDType1Font.HELVETICA_BOLD);
            drawTextAt(cs, "Independent Contractor", box2X + 10, y - 50, 9, PDType1Font.HELVETICA);

            // ══════════════════════════════════════════════════════
            //  CONTRACT DETAILS
            // ══════════════════════════════════════════════════════
            y -= 90;
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("CONTRACT DETAILS");
            cs.endText();
            y -= 5;
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
            y -= 20;

            cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
            y = drawFieldRow(cs, "Offer Title:", offerTitle, y);
            y = drawFieldRow(cs, "Compensation:", String.format("%s %.2f", contract.getCurrency(), contract.getAmount()), y);
            y = drawFieldRow(cs, "Contract Status:", contract.getStatus(), y);
            if (contract.getStartDate() != null)
                y = drawFieldRow(cs, "Start Date:", contract.getStartDate().toString(), y);
            if (contract.getEndDate() != null)
                y = drawFieldRow(cs, "End Date:", contract.getEndDate().toString(), y);
            if (contract.getRiskScore() != null) {
                String riskLevel = contract.getRiskScore() <= 30 ? "Low Risk" : contract.getRiskScore() <= 70 ? "Medium Risk" : "High Risk";
                y = drawFieldRow(cs, "Risk Assessment:", riskLevel + " (" + contract.getRiskScore() + "/100)", y);
            }

            // ══════════════════════════════════════════════════════
            //  CONTRACT TERMS
            // ══════════════════════════════════════════════════════
            y -= 15;
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("TERMS & CONDITIONS");
            cs.endText();
            y -= 5;
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
            y -= 15;

            cs.setNonStrokingColor(0.15f, 0.15f, 0.15f);
            String terms = contract.getTerms() != null ? contract.getTerms()
                    : "Terms will be generated upon contract activation. Both parties agree to the standard "
                    + "SynergyGig Terms of Service, including confidentiality obligations, payment schedules, "
                    + "and dispute resolution procedures.";
            y = drawWrappedText(cs, doc, terms, MARGIN, y, BODY_SIZE, PDType1Font.HELVETICA);

            // ══════════════════════════════════════════════════════
            //  BLOCKCHAIN VERIFICATION + QR CODE  (page 2 if needed)
            // ══════════════════════════════════════════════════════
            if (contract.getBlockchainHash() != null && !contract.getBlockchainHash().isEmpty()) {
                if (y < 280) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    // Light bg on new page too
                    cs.setNonStrokingColor(LIGHT_BG[0], LIGHT_BG[1], LIGHT_BG[2]);
                    cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                    cs.fill();
                    y = PAGE_HEIGHT - MARGIN;
                }

                y -= 25;
                cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                cs.newLineAtOffset(MARGIN, y);
                cs.showText("BLOCKCHAIN VERIFICATION");
                cs.endText();
                y -= 5;
                cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
                drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
                y -= 20;

                // Verification box
                float vboxH = 140;
                cs.setNonStrokingColor(1f, 1f, 1f);
                cs.addRect(MARGIN, y - vboxH, CONTENT_WIDTH, vboxH);
                cs.fill();
                cs.setStrokingColor(0.85f, 0.85f, 0.85f);
                cs.setLineWidth(0.5f);
                cs.addRect(MARGIN, y - vboxH, CONTENT_WIDTH, vboxH);
                cs.stroke();

                // QR Code
                float qrSize = 110;
                float qrX = MARGIN + 15;
                float qrY = y - vboxH + 15;
                try {
                    byte[] qrBytes = fetchQrCode(contract.getBlockchainHash());
                    if (qrBytes != null) {
                        BufferedImage qrImage = ImageIO.read(new ByteArrayInputStream(qrBytes));
                        if (qrImage != null) {
                            File qrTemp = tempDir.resolve("qr_" + contract.getId() + ".png").toFile();
                            ImageIO.write(qrImage, "PNG", qrTemp);
                            PDImageXObject pdImage = PDImageXObject.createFromFile(qrTemp.getAbsolutePath(), doc);
                            cs.drawImage(pdImage, qrX, qrY, qrSize, qrSize);
                        }
                    }
                } catch (Exception e) {
                    cs.setNonStrokingColor(0.6f, 0.6f, 0.6f);
                    drawTextAt(cs, "(QR unavailable)", qrX + 10, qrY + 50, 9, PDType1Font.HELVETICA_OBLIQUE);
                }

                // Hash details to the right of QR
                float detailX = qrX + qrSize + 25;
                float detailY = y - 20;
                cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
                drawTextAt(cs, "DIGITAL VERIFICATION", detailX, detailY, 10, PDType1Font.HELVETICA_BOLD);
                detailY -= 18;
                cs.setNonStrokingColor(0.3f, 0.3f, 0.3f);
                drawTextAt(cs, "Scan the QR code to verify contract integrity.", detailX, detailY, 9, PDType1Font.HELVETICA);
                detailY -= 20;
                cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                drawTextAt(cs, "SHA-256 Hash:", detailX, detailY, 8, PDType1Font.HELVETICA_BOLD);
                detailY -= 14;
                cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
                String hash = contract.getBlockchainHash();
                // Split hash into two lines if long
                if (hash.length() > 40) {
                    drawTextAt(cs, hash.substring(0, 40), detailX, detailY, 7, PDType1Font.COURIER);
                    detailY -= 12;
                    drawTextAt(cs, hash.substring(40), detailX, detailY, 7, PDType1Font.COURIER);
                } else {
                    drawTextAt(cs, hash, detailX, detailY, 7, PDType1Font.COURIER);
                }
                detailY -= 18;
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                drawTextAt(cs, "Verified by SynergyGig Blockchain Ledger", detailX, detailY, 8, PDType1Font.HELVETICA_OBLIQUE);

                y -= (vboxH + 15);
            }

            // ══════════════════════════════════════════════════════
            //  SIGNATURES SECTION
            // ══════════════════════════════════════════════════════
            if (y < 200) {
                cs.close();
                page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                cs = new PDPageContentStream(doc, page);
                cs.setNonStrokingColor(LIGHT_BG[0], LIGHT_BG[1], LIGHT_BG[2]);
                cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                cs.fill();
                y = PAGE_HEIGHT - MARGIN;
            }

            y -= 25;
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText("AUTHORIZED SIGNATURES");
            cs.endText();
            y -= 5;
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            drawLine(cs, MARGIN, y, PAGE_WIDTH - MARGIN, y);
            y -= 40;

            float sigBoxW = (CONTENT_WIDTH - 40) / 2;

            // Owner signature box
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(MARGIN, y - 80, sigBoxW, 80);
            cs.fill();
            cs.setStrokingColor(0.85f, 0.85f, 0.85f);
            cs.addRect(MARGIN, y - 80, sigBoxW, 80);
            cs.stroke();

            // Company stamp effect
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            drawTextAt(cs, "SYNERGYGIG", MARGIN + 15, y - 25, 14, PDType1Font.HELVETICA_BOLD);
            cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
            drawTextAt(cs, "Authorized Signatory", MARGIN + 15, y - 42, 9, PDType1Font.HELVETICA_OBLIQUE);
            cs.setStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.setLineWidth(1.5f);
            drawLine(cs, MARGIN + 15, y - 52, MARGIN + sigBoxW - 15, y - 52);
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            drawTextAt(cs, ownerName, MARGIN + 15, y - 68, 10, PDType1Font.HELVETICA_BOLD);

            // Contractor signature box
            float sig2X = MARGIN + sigBoxW + 40;
            cs.setNonStrokingColor(1f, 1f, 1f);
            cs.addRect(sig2X, y - 80, sigBoxW, 80);
            cs.fill();
            cs.setStrokingColor(0.85f, 0.85f, 0.85f);
            cs.addRect(sig2X, y - 80, sigBoxW, 80);
            cs.stroke();

            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            drawTextAt(cs, "CONTRACTOR", sig2X + 15, y - 25, 14, PDType1Font.HELVETICA_BOLD);
            cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
            drawTextAt(cs, "Signature Required", sig2X + 15, y - 42, 9, PDType1Font.HELVETICA_OBLIQUE);
            cs.setStrokingColor(GOLD[0], GOLD[1], GOLD[2]);
            cs.setLineWidth(1.5f);
            drawLine(cs, sig2X + 15, y - 52, sig2X + sigBoxW - 15, y - 52);
            cs.setNonStrokingColor(0.2f, 0.2f, 0.2f);
            drawTextAt(cs, applicantName, sig2X + 15, y - 68, 10, PDType1Font.HELVETICA_BOLD);

            // ══════════════════════════════════════════════════════
            //  FOOTER
            // ══════════════════════════════════════════════════════
            cs.setLineWidth(0.5f);
            // Footer bar
            cs.setNonStrokingColor(DARK_TEAL[0], DARK_TEAL[1], DARK_TEAL[2]);
            cs.addRect(0, 0, PAGE_WIDTH, 35);
            cs.fill();
            cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
            cs.addRect(0, 35, PAGE_WIDTH, 2);
            cs.fill();

            cs.setNonStrokingColor(0.565f, 0.867f, 0.941f);
            String footer = "SynergyGig Platform  \u2022  Blockchain-Secured  \u2022  " + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            float fW = PDType1Font.HELVETICA.getStringWidth(footer) / 1000f * 8;
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 8);
            cs.newLineAtOffset((PAGE_WIDTH - fW) / 2, 13);
            cs.showText(footer);
            cs.endText();

            cs.close();
            doc.save(pdfFile);
        }

        return pdfFile;
    }

    // ==================== Drawing helpers ====================

    private static void drawTextAt(PDPageContentStream cs, String text, float x, float y,
                                    float fontSize, PDType1Font font) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static float drawFieldRow(PDPageContentStream cs, String label, String value, float y) throws IOException {
        // Alternating row background
        cs.setNonStrokingColor(0.97f, 0.97f, 0.95f);
        cs.addRect(MARGIN, y - 4, CONTENT_WIDTH, LINE_HEIGHT + 4);
        cs.fill();
        cs.setNonStrokingColor(TEAL[0], TEAL[1], TEAL[2]);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, BODY_SIZE);
        cs.newLineAtOffset(MARGIN + 8, y);
        cs.showText(label);
        cs.endText();
        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, BODY_SIZE);
        cs.newLineAtOffset(MARGIN + 130, y);
        cs.showText(value != null ? value : "N/A");
        cs.endText();
        return y - LINE_HEIGHT - 6;
    }

    private static float drawWrappedText(PDPageContentStream cs, PDDocument doc, String text,
                                          float x, float y, float fontSize, PDType1Font font) throws IOException {
        String[] paragraphs = text.split("\n");
        for (String para : paragraphs) {
            if (para.trim().isEmpty()) {
                y -= LINE_HEIGHT;
                continue;
            }
            String[] words = para.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String test = line.length() > 0 ? line + " " + word : word;
                float testWidth = font.getStringWidth(test) / 1000 * fontSize;
                if (testWidth > CONTENT_WIDTH && line.length() > 0) {
                    if (y < MARGIN + 50) {
                        cs.close();
                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        cs.setNonStrokingColor(LIGHT_BG[0], LIGHT_BG[1], LIGHT_BG[2]);
                        cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                        cs.fill();
                        y = PAGE_HEIGHT - MARGIN;
                    }
                    cs.setNonStrokingColor(0.15f, 0.15f, 0.15f);
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.newLineAtOffset(x, y);
                    cs.showText(line.toString());
                    cs.endText();
                    y -= LINE_HEIGHT;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(test);
                }
            }
            if (line.length() > 0) {
                if (y < MARGIN + 50) {
                    cs.close();
                    PDPage page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.setNonStrokingColor(LIGHT_BG[0], LIGHT_BG[1], LIGHT_BG[2]);
                    cs.addRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);
                    cs.fill();
                    y = PAGE_HEIGHT - MARGIN;
                }
                cs.setNonStrokingColor(0.15f, 0.15f, 0.15f);
                cs.beginText();
                cs.setFont(font, fontSize);
                cs.newLineAtOffset(x, y);
                cs.showText(line.toString());
                cs.endText();
                y -= LINE_HEIGHT;
            }
        }
        return y;
    }

    private static void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    // ==================== QR Code (goqr.me free API) ====================

    /**
     * Fetch a QR code PNG from goqr.me API.
     * @param data The data to encode (blockchain hash)
     * @return PNG byte array, or null on failure
     */
    public static byte[] fetchQrCode(String data) {
        try {
            String encoded = URLEncoder.encode(data, StandardCharsets.UTF_8);
            String url = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=" + encoded;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            System.err.println("QR code fetch failed: " + e.getMessage());
            return null;
        }
    }
}
