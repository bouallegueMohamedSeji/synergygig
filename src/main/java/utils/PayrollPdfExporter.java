package utils;

import entities.Payroll;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates professional payroll PDF documents using Apache PDFBox.
 * Supports single-employee pay slips and full payroll reports for HR.
 */
public class PayrollPdfExporter {

    private static final float MARGIN = 50;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float USABLE_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    // ─── Colors (RGB 0-1) ───
    private static final float[] COLOR_PRIMARY   = {0.20f, 0.25f, 0.55f};  // dark blue
    private static final float[] COLOR_ACCENT    = {0.34f, 0.34f, 1.0f};   // bright blue
    private static final float[] COLOR_TEXT       = {0.12f, 0.12f, 0.20f};  // near-black
    private static final float[] COLOR_MUTED      = {0.45f, 0.45f, 0.55f};  // gray
    private static final float[] COLOR_ROW_ALT    = {0.95f, 0.95f, 0.98f};  // light stripe
    private static final float[] COLOR_WHITE      = {1f, 1f, 1f};
    private static final float[] COLOR_GREEN      = {0.18f, 0.62f, 0.34f};
    private static final float[] COLOR_ORANGE     = {0.85f, 0.55f, 0.20f};

    /**
     * Export a single employee's payroll as a pay slip PDF.
     */
    public static void exportEmployeePayslip(File outputFile, Payroll payroll, String employeeName) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = PAGE_HEIGHT - MARGIN;

                // ─── Header Banner ───
                cs.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
                cs.addRect(0, PAGE_HEIGHT - 100, PAGE_WIDTH, 100);
                cs.fill();

                cs.setNonStrokingColor(COLOR_WHITE[0], COLOR_WHITE[1], COLOR_WHITE[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 24);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 55);
                cs.showText("SynergyGig");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 75);
                cs.showText("Employee Pay Slip");
                cs.endText();

                // Date on right side
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                String dateStr = "Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                float dateWidth = PDType1Font.HELVETICA.getStringWidth(dateStr) / 1000 * 10;
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - dateWidth, PAGE_HEIGHT - 55);
                cs.showText(dateStr);
                cs.endText();

                y = PAGE_HEIGHT - 130;

                // ─── Employee Info Box ───
                cs.setNonStrokingColor(COLOR_ROW_ALT[0], COLOR_ROW_ALT[1], COLOR_ROW_ALT[2]);
                cs.addRect(MARGIN, y - 55, USABLE_WIDTH, 55);
                cs.fill();

                cs.setNonStrokingColor(COLOR_TEXT[0], COLOR_TEXT[1], COLOR_TEXT[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(MARGIN + 15, y - 22);
                cs.showText("Employee: " + sanitize(employeeName));
                cs.endText();

                String monthStr = payroll.getMonth() != null
                        ? payroll.getMonth().toLocalDate().format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                        : "N/A";
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(MARGIN + 15, y - 42);
                cs.showText("Pay Period: " + monthStr + "   |   Status: " + payroll.getStatus());
                cs.endText();

                y -= 80;

                // ─── Earnings Table ───
                y = drawSectionTitle(cs, "Earnings", y);

                String[][] earningsRows = {
                        {"Base Salary", fmt(payroll.getBaseSalary())},
                        {"Bonus", fmt(payroll.getBonus())},
                        {"Hours Worked", String.format("%.1f hrs @ %s/hr", payroll.getTotalHoursWorked(), fmt(payroll.getHourlyRate()))}
                };
                y = drawKeyValueTable(cs, earningsRows, y);

                y -= 15;

                // ─── Deductions Table ───
                y = drawSectionTitle(cs, "Deductions", y);

                String[][] deductionRows = {
                        {"Total Deductions", fmt(payroll.getDeductions())}
                };
                y = drawKeyValueTable(cs, deductionRows, y);

                y -= 15;

                // ─── Net Pay Highlight Box ───
                cs.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
                cs.addRect(MARGIN, y - 50, USABLE_WIDTH, 50);
                cs.fill();

                cs.setNonStrokingColor(COLOR_WHITE[0], COLOR_WHITE[1], COLOR_WHITE[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(MARGIN + 20, y - 32);
                cs.showText("NET PAY:  " + fmt(payroll.getNetSalary()) + " TND");
                cs.endText();

                y -= 75;

                // ─── Footer ───
                cs.setNonStrokingColor(COLOR_MUTED[0], COLOR_MUTED[1], COLOR_MUTED[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 8);
                cs.newLineAtOffset(MARGIN, MARGIN);
                cs.showText("This is a computer-generated document from SynergyGig HR Module. No signature required.");
                cs.endText();
            }

            doc.save(outputFile);
        }
    }

    /**
     * Export all payroll records as a comprehensive HR report PDF.
     */
    public static void exportAllPayroll(File outputFile, List<Payroll> payrolls, Map<Integer, String> userNameMap) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            // ─── Page 1: Cover + Summary ───
            PDPage coverPage = new PDPage(PDRectangle.A4);
            doc.addPage(coverPage);

            try (PDPageContentStream cs = new PDPageContentStream(doc, coverPage)) {
                float y = PAGE_HEIGHT - MARGIN;

                // Header
                cs.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
                cs.addRect(0, PAGE_HEIGHT - 100, PAGE_WIDTH, 100);
                cs.fill();

                cs.setNonStrokingColor(COLOR_WHITE[0], COLOR_WHITE[1], COLOR_WHITE[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 26);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 55);
                cs.showText("SynergyGig");
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 78);
                cs.showText("Complete Payroll Report");
                cs.endText();

                String dateStr = "Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                float dw = PDType1Font.HELVETICA.getStringWidth(dateStr) / 1000 * 10;
                cs.newLineAtOffset(PAGE_WIDTH - MARGIN - dw, PAGE_HEIGHT - 55);
                cs.showText(dateStr);
                cs.endText();

                y = PAGE_HEIGHT - 130;

                // Summary Stats
                double totalBase = payrolls.stream().mapToDouble(Payroll::getBaseSalary).sum();
                double totalBonus = payrolls.stream().mapToDouble(Payroll::getBonus).sum();
                double totalDed = payrolls.stream().mapToDouble(Payroll::getDeductions).sum();
                double totalNet = payrolls.stream().mapToDouble(Payroll::getNetSalary).sum();
                long paidCount = payrolls.stream().filter(p -> "PAID".equals(p.getStatus())).count();
                long pendingCount = payrolls.stream().filter(p -> "PENDING".equals(p.getStatus())).count();

                y = drawSectionTitle(cs, "Summary", y);

                String[][] summaryRows = {
                        {"Total Records", String.valueOf(payrolls.size())},
                        {"Paid / Pending", paidCount + " paid, " + pendingCount + " pending"},
                        {"Total Base Salaries", fmt(totalBase) + " TND"},
                        {"Total Bonuses", fmt(totalBonus) + " TND"},
                        {"Total Deductions", fmt(totalDed) + " TND"},
                        {"Total Net Pay", fmt(totalNet) + " TND"}
                };
                y = drawKeyValueTable(cs, summaryRows, y);

                y -= 25;

                // ─── Payroll Table Header ───
                y = drawSectionTitle(cs, "Payroll Details", y);
                y = drawTableHeader(cs, y);

                // Rows
                int rowIndex = 0;
                for (Payroll p : payrolls) {
                    if (y < MARGIN + 40) {
                        // New page
                        PDPage newPage = new PDPage(PDRectangle.A4);
                        doc.addPage(newPage);
                        cs.close();
                        PDPageContentStream newCs = new PDPageContentStream(doc, newPage);
                        y = PAGE_HEIGHT - MARGIN;
                        y = drawTableHeader(newCs, y);
                        y = drawPayrollRow(newCs, p, userNameMap, rowIndex, y);
                        // We can't reassign cs due to try-with-resources, so we handle pages below
                        rowIndex++;
                        newCs.close();
                        continue;
                    }
                    y = drawPayrollRow(cs, p, userNameMap, rowIndex, y);
                    rowIndex++;
                }

                // Footer
                cs.setNonStrokingColor(COLOR_MUTED[0], COLOR_MUTED[1], COLOR_MUTED[2]);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 8);
                cs.newLineAtOffset(MARGIN, MARGIN);
                cs.showText("SynergyGig Payroll Report - Confidential - Page 1");
                cs.endText();
            }

            // If there are many payroll entries that spill to more pages, add them
            if (payrolls.size() > 15) {
                int startIdx = 15; // First page fits about 15 rows
                while (startIdx < payrolls.size()) {
                    PDPage extraPage = new PDPage(PDRectangle.A4);
                    doc.addPage(extraPage);

                    try (PDPageContentStream cs = new PDPageContentStream(doc, extraPage)) {
                        float y = PAGE_HEIGHT - MARGIN;

                        cs.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
                        cs.addRect(0, PAGE_HEIGHT - 50, PAGE_WIDTH, 50);
                        cs.fill();
                        cs.setNonStrokingColor(COLOR_WHITE[0], COLOR_WHITE[1], COLOR_WHITE[2]);
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                        cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 35);
                        cs.showText("SynergyGig - Payroll Report (continued)");
                        cs.endText();

                        y = PAGE_HEIGHT - 70;
                        y = drawTableHeader(cs, y);

                        int endIdx = Math.min(startIdx + 25, payrolls.size());
                        for (int i = startIdx; i < endIdx; i++) {
                            if (y < MARGIN + 30) break;
                            y = drawPayrollRow(cs, payrolls.get(i), userNameMap, i, y);
                        }

                        cs.setNonStrokingColor(COLOR_MUTED[0], COLOR_MUTED[1], COLOR_MUTED[2]);
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 8);
                        cs.newLineAtOffset(MARGIN, MARGIN);
                        int pageNum = (startIdx / 25) + 2;
                        cs.showText("SynergyGig Payroll Report - Confidential - Page " + pageNum);
                        cs.endText();

                        startIdx = endIdx;
                    }
                }
            }

            doc.save(outputFile);
        }
    }

    // ═════════════════════════════════════════════════
    //  DRAWING HELPERS
    // ═════════════════════════════════════════════════

    private static float drawSectionTitle(PDPageContentStream cs, String title, float y) throws IOException {
        cs.setNonStrokingColor(COLOR_ACCENT[0], COLOR_ACCENT[1], COLOR_ACCENT[2]);
        cs.addRect(MARGIN, y - 2, USABLE_WIDTH, 2);
        cs.fill();

        cs.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
        cs.newLineAtOffset(MARGIN, y - 20);
        cs.showText(title);
        cs.endText();

        return y - 30;
    }

    private static float drawKeyValueTable(PDPageContentStream cs, String[][] rows, float y) throws IOException {
        for (int i = 0; i < rows.length; i++) {
            if (i % 2 == 0) {
                cs.setNonStrokingColor(COLOR_ROW_ALT[0], COLOR_ROW_ALT[1], COLOR_ROW_ALT[2]);
                cs.addRect(MARGIN, y - 18, USABLE_WIDTH, 22);
                cs.fill();
            }

            cs.setNonStrokingColor(COLOR_TEXT[0], COLOR_TEXT[1], COLOR_TEXT[2]);
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 10);
            cs.newLineAtOffset(MARGIN + 10, y - 13);
            cs.showText(sanitize(rows[i][0]));
            cs.endText();

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
            cs.newLineAtOffset(MARGIN + USABLE_WIDTH / 2, y - 13);
            cs.showText(sanitize(rows[i][1]));
            cs.endText();

            y -= 22;
        }
        return y;
    }

    private static float drawTableHeader(PDPageContentStream cs, float y) throws IOException {
        cs.setNonStrokingColor(COLOR_PRIMARY[0], COLOR_PRIMARY[1], COLOR_PRIMARY[2]);
        cs.addRect(MARGIN, y - 20, USABLE_WIDTH, 20);
        cs.fill();

        cs.setNonStrokingColor(COLOR_WHITE[0], COLOR_WHITE[1], COLOR_WHITE[2]);
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
        float x = MARGIN + 5;
        cs.newLineAtOffset(x, y - 14);
        cs.showText("Employee");
        cs.endText();

        String[] headers = {"Month", "Base", "Bonus", "Deduct.", "Net Pay", "Hours", "Status"};
        float[] offsets = {120, 190, 250, 300, 365, 430, 470};

        for (int i = 0; i < headers.length; i++) {
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
            cs.newLineAtOffset(MARGIN + offsets[i], y - 14);
            cs.showText(headers[i]);
            cs.endText();
        }

        return y - 22;
    }

    private static float drawPayrollRow(PDPageContentStream cs, Payroll p, Map<Integer, String> nameMap, int index, float y) throws IOException {
        if (index % 2 == 0) {
            cs.setNonStrokingColor(COLOR_ROW_ALT[0], COLOR_ROW_ALT[1], COLOR_ROW_ALT[2]);
            cs.addRect(MARGIN, y - 16, USABLE_WIDTH, 18);
            cs.fill();
        }

        String empName = nameMap.getOrDefault(p.getUserId(), "User #" + p.getUserId());
        if (empName.length() > 18) empName = empName.substring(0, 16) + "..";

        String monthStr = p.getMonth() != null
                ? p.getMonth().toLocalDate().format(DateTimeFormatter.ofPattern("MMM yy"))
                : "N/A";

        cs.setNonStrokingColor(COLOR_TEXT[0], COLOR_TEXT[1], COLOR_TEXT[2]);

        drawCell(cs, sanitize(empName), MARGIN + 5, y - 12, PDType1Font.HELVETICA, 8);
        drawCell(cs, monthStr, MARGIN + 120, y - 12, PDType1Font.HELVETICA, 8);
        drawCell(cs, fmt(p.getBaseSalary()), MARGIN + 190, y - 12, PDType1Font.HELVETICA, 8);
        drawCell(cs, fmt(p.getBonus()), MARGIN + 250, y - 12, PDType1Font.HELVETICA, 8);
        drawCell(cs, fmt(p.getDeductions()), MARGIN + 300, y - 12, PDType1Font.HELVETICA, 8);
        drawCell(cs, fmt(p.getNetSalary()), MARGIN + 365, y - 12, PDType1Font.HELVETICA_BOLD, 8);
        drawCell(cs, String.format("%.1f", p.getTotalHoursWorked()), MARGIN + 430, y - 12, PDType1Font.HELVETICA, 8);

        // Status with color
        if ("PAID".equals(p.getStatus())) {
            cs.setNonStrokingColor(COLOR_GREEN[0], COLOR_GREEN[1], COLOR_GREEN[2]);
        } else {
            cs.setNonStrokingColor(COLOR_ORANGE[0], COLOR_ORANGE[1], COLOR_ORANGE[2]);
        }
        drawCell(cs, p.getStatus(), MARGIN + 470, y - 12, PDType1Font.HELVETICA_BOLD, 8);

        return y - 20;
    }

    private static void drawCell(PDPageContentStream cs, String text, float x, float y, PDType1Font font, float size) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static String fmt(double value) {
        if (value == 0) return "0.00";
        return String.format("%,.2f", value);
    }

    /** Remove characters that PDFBox PDType1Font can't encode. */
    private static String sanitize(String text) {
        if (text == null) return "";
        // PDType1Font only supports WinAnsiEncoding — strip anything outside
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= 0x20 && c <= 0x7E) {  // basic printable ASCII
                sb.append(c);
            } else if (c >= 0xA0 && c <= 0xFF) {  // Latin-1 supplement
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
