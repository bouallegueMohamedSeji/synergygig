package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import services.ZAIService;
import utils.AppThreadPool;
import utils.DialogHelper;
import utils.SoundManager;

public class MeetingSummarizerController implements Stoppable {

    @FXML private TextArea transcriptArea;
    @FXML private Button summarizeBtn;
    @FXML private Label charCount;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private VBox inputSection, resultSection;
    @FXML private Label summaryContent;

    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private String lastSummary;

    @FXML
    private void initialize() {
        transcriptArea.textProperty().addListener((o, ov, nv) ->
            charCount.setText(nv.length() + " characters"));
    }

    @FXML
    private void handleSummarize() {
        String text = transcriptArea.getText().trim();
        if (text.length() < 30) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please enter at least 30 characters of meeting content.", ButtonType.OK);
            DialogHelper.theme(alert);
            alert.showAndWait();
            return;
        }
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        summarizeBtn.setDisable(true);
        statusBar.setVisible(true);
        statusBar.setManaged(true);
        statusLabel.setText("AI is analyzing your meeting notes...");

        AppThreadPool.io(() -> {
            String result = ai.summarizeMeeting(text);
            if (cancelled) return;
            Platform.runLater(() -> {
                lastSummary = result;
                summaryContent.setText(result);
                statusBar.setVisible(false);
                statusBar.setManaged(false);
                resultSection.setVisible(true);
                resultSection.setManaged(true);
                summarizeBtn.setDisable(false);
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    @FXML
    private void handleClear() {
        transcriptArea.clear();
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    @FXML
    private void handleCopy() {
        if (lastSummary == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastSummary);
        Clipboard.getSystemClipboard().setContent(cc);
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
    }

    @FXML
    private void handleNew() {
        resultSection.setVisible(false);
        resultSection.setManaged(false);
        transcriptArea.clear();
        lastSummary = null;
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/ProjectManagement.fxml");
    }

    @Override
    public void stop() {
        cancelled = true;
    }
}
