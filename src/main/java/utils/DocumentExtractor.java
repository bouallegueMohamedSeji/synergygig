package utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Utility for extracting text content from PDF and Excel files.
 * Used by the AI chat room to analyze uploaded documents.
 */
public class DocumentExtractor {

    /**
     * Extract text from a PDF file.
     *
     * @param file the PDF file
     * @return extracted text content
     */
    public static String extractPDF(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            int pageCount = document.getNumberOfPages();

            StringBuilder sb = new StringBuilder();
            sb.append("📄 PDF Document: ").append(file.getName()).append("\n");
            sb.append("📑 Pages: ").append(pageCount).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            if (text != null && !text.trim().isEmpty()) {
                // Limit to first 3000 chars for display
                String trimmed = text.trim();
                if (trimmed.length() > 3000) {
                    sb.append(trimmed, 0, 3000);
                    sb.append("\n\n... [truncated — showing first 3000 of ").append(trimmed.length()).append(" characters]");
                } else {
                    sb.append(trimmed);
                }
            } else {
                sb.append("(No extractable text found — document may contain only images/scans)");
            }

            return sb.toString();
        }
    }

    /**
     * Extract text from an Excel file (.xlsx or .xls).
     *
     * @param file the Excel file
     * @return extracted text content as a formatted table
     */
    public static String extractExcel(File file) throws IOException {
        String name = file.getName().toLowerCase();
        boolean isXlsx = name.endsWith(".xlsx");

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = isXlsx ? new XSSFWorkbook(fis) : new HSSFWorkbook(fis)) {

            StringBuilder sb = new StringBuilder();
            sb.append("📊 Excel Document: ").append(file.getName()).append("\n");
            sb.append("📑 Sheets: ").append(workbook.getNumberOfSheets()).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            int totalRows = 0;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                sb.append("📋 Sheet: ").append(sheet.getSheetName()).append("\n");

                int rowCount = 0;
                for (Row row : sheet) {
                    if (totalRows > 200) {
                        sb.append("\n... [truncated — too many rows]\n");
                        break;
                    }
                    StringBuilder rowStr = new StringBuilder();
                    boolean hasContent = false;
                    for (Cell cell : row) {
                        String val = getCellValueAsString(cell);
                        if (!val.isEmpty()) hasContent = true;
                        if (rowStr.length() > 0) rowStr.append(" | ");
                        rowStr.append(val);
                    }
                    if (hasContent) {
                        sb.append(rowStr).append("\n");
                        rowCount++;
                        totalRows++;
                    }
                }
                sb.append("  → ").append(rowCount).append(" rows\n\n");

                if (totalRows > 200) break;
            }

            return sb.toString();
        }
    }

    /**
     * Auto-detect file type and extract content.
     */
    /**
     * Extract text from a DOCX file.
     */
    public static String extractDocx(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument doc = new XWPFDocument(fis)) {
            StringBuilder sb = new StringBuilder();
            sb.append("📄 Word Document: ").append(file.getName()).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append("\n");
                }
            }
            String result = sb.toString().trim();
            if (result.length() > 5000) {
                return result.substring(0, 5000) + "\n\n... [truncated — showing first 5000 of " + result.length() + " characters]";
            }
            return result;
        }
    }

    public static String extract(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            return extractPDF(file);
        } else if (name.endsWith(".docx")) {
            return extractDocx(file);
        } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return extractExcel(file);
        } else {
            throw new IOException("Unsupported file type: " + name);
        }
    }

    /**
     * Generate a brief analysis/summary of extracted text.
     */
    public static String analyzeText(String extractedText, String fileName) {
        String lower = extractedText.toLowerCase();
        int wordCount = extractedText.split("\\s+").length;
        int lineCount = extractedText.split("\n").length;

        StringBuilder analysis = new StringBuilder();
        analysis.append("🔍 Document Analysis\n");
        analysis.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        analysis.append("📄 File: ").append(fileName).append("\n");
        analysis.append("📝 Words: ").append(wordCount).append("\n");
        analysis.append("📊 Lines: ").append(lineCount).append("\n\n");

        // Detect document category
        List<String> categories = new ArrayList<>();
        if (lower.contains("invoice") || lower.contains("payment") || lower.contains("total") || lower.contains("amount"))
            categories.add("💰 Financial/Invoice");
        if (lower.contains("resume") || lower.contains("experience") || lower.contains("education") || lower.contains("skills"))
            categories.add("👤 Resume/CV");
        if (lower.contains("contract") || lower.contains("agreement") || lower.contains("terms") || lower.contains("clause"))
            categories.add("📜 Contract/Legal");
        if (lower.contains("report") || lower.contains("quarterly") || lower.contains("annual") || lower.contains("summary"))
            categories.add("📊 Report");
        if (lower.contains("salary") || lower.contains("payroll") || lower.contains("employee") || lower.contains("department"))
            categories.add("🏢 HR Document");
        if (lower.contains("schedule") || lower.contains("meeting") || lower.contains("agenda") || lower.contains("date"))
            categories.add("📅 Schedule/Planning");
        if (lower.contains("project") || lower.contains("task") || lower.contains("milestone") || lower.contains("deadline"))
            categories.add("🎯 Project Management");

        if (!categories.isEmpty()) {
            analysis.append("🏷️ Detected Categories:\n");
            for (String cat : categories) {
                analysis.append("  • ").append(cat).append("\n");
            }
        } else {
            analysis.append("🏷️ Category: General Document\n");
        }

        // Extract potential key numbers
        java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("\\$[\\d,]+\\.?\\d*|\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?")
                .matcher(extractedText);
        List<String> numbers = new ArrayList<>();
        while (numMatcher.find() && numbers.size() < 5) {
            numbers.add(numMatcher.group());
        }
        if (!numbers.isEmpty()) {
            analysis.append("\n💵 Key Numbers Found:\n");
            for (String num : numbers) {
                analysis.append("  • ").append(num).append("\n");
            }
        }

        // Extract potential email addresses
        java.util.regex.Matcher emailMatcher = java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+")
                .matcher(extractedText);
        List<String> emails = new ArrayList<>();
        while (emailMatcher.find() && emails.size() < 5) {
            emails.add(emailMatcher.group());
        }
        if (!emails.isEmpty()) {
            analysis.append("\n📧 Email Addresses:\n");
            for (String email : emails) {
                analysis.append("  • ").append(email).append("\n");
            }
        }

        return analysis.toString();
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:  return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return cell.getStringCellValue(); }
                catch (Exception e) {
                    try { return String.valueOf(cell.getNumericCellValue()); }
                    catch (Exception e2) { return cell.getCellFormula(); }
                }
            default: return "";
        }
    }
}
