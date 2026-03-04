package controllers;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import services.ZAIService;
import utils.AppThreadPool;
import utils.DialogHelper;
import utils.SoundManager;

public class EmailComposerController implements Stoppable {

    @FXML private TextField recipientField, purposeField;
    @FXML private TextArea keyPointsArea;
    @FXML private ComboBox<String> toneCombo;
    @FXML private Button composeBtn;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private VBox resultSection;
    @FXML private Label emailContent;

    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private String lastEmail;

    @FXML
    private void initialize() {
        toneCombo.setItems(FXCollections.observableArrayList(
            "Professional", "Friendly", "Formal", "Casual", "Urgent", "Diplomatic"
        ));
        toneCombo.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleCompose() {
        String recipient = recipientField.getText().trim();
        String purpose = purposeField.getText().trim();
        if (purpose.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please describe the email purpose.", ButtonType.OK);
            DialogHelper.theme(alert);
            alert.showAndWait();
            return;
        }
        doCompose(recipient, purpose);
    }

    @FXML
    private void handleRewrite() {
        String recipient = recipientField.getText().trim();
        String purpose = purposeField.getText().trim();
        if (purpose.isEmpty()) return;
        doCompose(recipient, purpose);
    }

    private void doCompose(String recipient, String purpose) {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        composeBtn.setDisable(true);
        statusBar.setVisible(true);
        statusBar.setManaged(true);
        statusLabel.setText("AI is composing your email...");

        String tone = toneCombo.getValue() != null ? toneCombo.getValue() : "Professional";
        String keyPoints = keyPointsArea.getText().trim();

        AppThreadPool.io(() -> {
            String system = """
                You are a professional email writer. Draft an email with the following requirements:
                - Tone: %s
                - Recipient: %s
                - Purpose: %s
                
                Format the output as:
                Subject: [clear subject line]
                
                [email body]
                
                Keep it concise and effective. Include a proper greeting and sign-off.
                %s
                """.formatted(tone, recipient.isEmpty() ? "Colleague" : recipient, purpose,
                    keyPoints.isEmpty() ? "" : "Key points to include: " + keyPoints);

            String result = ai.chat(system, "Draft this email now.");
            if (cancelled) return;
            Platform.runLater(() -> {
                lastEmail = result;
                emailContent.setText(result);
                statusBar.setVisible(false);
                statusBar.setManaged(false);
                resultSection.setVisible(true);
                resultSection.setManaged(true);
                composeBtn.setDisable(false);
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    @FXML
    private void handleCopy() {
        if (lastEmail == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastEmail);
        Clipboard.getSystemClipboard().setContent(cc);
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/Chat.fxml");
    }

    @Override
    public void stop() {
        cancelled = true;
    }
}
