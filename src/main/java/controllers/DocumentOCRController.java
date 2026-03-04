package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import services.ZAIService;
import utils.AppThreadPool;
import utils.DialogHelper;
import utils.DocumentExtractor;
import utils.SoundManager;

import java.io.File;

public class DocumentOCRController implements Stoppable {

    @FXML private VBox dropZone;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private VBox resultSection;
    @FXML private Label fileNameLabel, fileSizeLabel, categoryLabel, categoryDesc, analysisContent;
    @FXML private TextArea extractedText;

    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private String lastExtractedText;

    @FXML
    private void handleUpload() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Document");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = fc.showOpenDialog(dropZone.getScene().getWindow());
        if (file == null) return;

        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        statusBar.setVisible(true);
        statusBar.setManaged(true);
        statusLabel.setText("Extracting text from " + file.getName() + "...");

        AppThreadPool.io(() -> {
            try {
                String text = DocumentExtractor.extract(file);
                String analysis = DocumentExtractor.analyzeText(text, file.getName());

                Platform.runLater(() -> statusLabel.setText("AI is analyzing the document..."));

                String aiAnalysis = ai.chat(
                    "You are a document analyst. Analyze this document and provide:\n" +
                    "1. Document type/category (e.g. Invoice, Resume, Contract, Report, HR Document, etc.)\n" +
                    "2. Key information summary (3-5 bullet points)\n" +
                    "3. Suggested filing folder\n" +
                    "4. Any important dates, amounts, or names found\n" +
                    "Be concise and structured.",
                    "Document: " + file.getName() + "\n\n" + text.substring(0, Math.min(text.length(), 3000))
                );

                if (cancelled) return;
                Platform.runLater(() -> {
                    lastExtractedText = text;
                    fileNameLabel.setText(file.getName());
                    fileSizeLabel.setText(formatSize(file.length()));

                    // Parse category from built-in analysis
                    String basicAnalysis = analysis;
                    if (basicAnalysis.contains("Resume/CV")) categoryLabel.setText("👤 Resume / CV");
                    else if (basicAnalysis.contains("Financial")) categoryLabel.setText("💰 Financial");
                    else if (basicAnalysis.contains("Contract")) categoryLabel.setText("📜 Contract / Legal");
                    else if (basicAnalysis.contains("HR Document")) categoryLabel.setText("🏢 HR Document");
                    else if (basicAnalysis.contains("Report")) categoryLabel.setText("📊 Report");
                    else if (basicAnalysis.contains("Schedule")) categoryLabel.setText("📅 Schedule");
                    else if (basicAnalysis.contains("Project")) categoryLabel.setText("🎯 Project Management");
                    else categoryLabel.setText("📄 General Document");

                    categoryDesc.setText("Auto-detected from document content");
                    analysisContent.setText(aiAnalysis);
                    extractedText.setText(text);

                    statusBar.setVisible(false);
                    statusBar.setManaged(false);
                    dropZone.setVisible(false);
                    dropZone.setManaged(false);
                    resultSection.setVisible(true);
                    resultSection.setManaged(true);
                    SoundManager.getInstance().play(SoundManager.LOGIN_SUCCESS);
                });
            } catch (Exception e) {
                if (!cancelled) Platform.runLater(() -> {
                    statusBar.setVisible(false);
                    statusBar.setManaged(false);
                    SoundManager.getInstance().play(SoundManager.ERROR);
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage(), ButtonType.OK);
                    DialogHelper.theme(alert);
                    alert.showAndWait();
                });
            }
        });
    }

    @FXML
    private void handleCopyText() {
        if (lastExtractedText == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastExtractedText);
        Clipboard.getSystemClipboard().setContent(cc);
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
    }

    @FXML
    private void handleReset() {
        dropZone.setVisible(true);
        dropZone.setManaged(true);
        resultSection.setVisible(false);
        resultSection.setManaged(false);
        lastExtractedText = null;
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/HRModule.fxml");
    }

    @Override
    public void stop() {
        cancelled = true;
    }
}
