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

public class CodeReviewController implements Stoppable {

    @FXML private ComboBox<String> langCombo;
    @FXML private TextArea codeArea;
    @FXML private Button reviewBtn;
    @FXML private Label lineCount;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private VBox resultSection;
    @FXML private Label reviewContent;

    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private String lastReview;

    @FXML
    private void initialize() {
        langCombo.setItems(FXCollections.observableArrayList(
            "Java", "Python", "JavaScript", "TypeScript", "C#", "C++", "Go", "Rust",
            "PHP", "Ruby", "Swift", "Kotlin", "SQL", "HTML/CSS", "Other"
        ));
        langCombo.getSelectionModel().selectFirst();

        codeArea.textProperty().addListener((o, ov, nv) -> {
            long lines = nv.isEmpty() ? 0 : nv.lines().count();
            lineCount.setText(lines + " lines");
        });
    }

    @FXML
    private void handleReview() {
        String code = codeArea.getText().trim();
        if (code.length() < 10) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please paste at least a few lines of code.", ButtonType.OK);
            DialogHelper.theme(alert);
            alert.showAndWait();
            return;
        }
        String lang = langCombo.getValue() != null ? langCombo.getValue() : "Unknown";
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        reviewBtn.setDisable(true);
        statusBar.setVisible(true);
        statusBar.setManaged(true);
        statusLabel.setText("AI is analyzing your " + lang + " code...");

        AppThreadPool.io(() -> {
            String system = """
                You are a senior software engineer performing a thorough code review.
                Review the provided %s code and provide:
                
                1. **Overall Quality** (1-10 rating with brief justification)
                2. **Bugs & Issues** — any logic errors, null safety issues, edge cases
                3. **Security Concerns** — injection, data exposure, auth issues
                4. **Performance** — inefficiencies, N+1 queries, unnecessary allocations
                5. **Code Style & Best Practices** — naming, SOLID principles, readability
                6. **Suggestions** — concrete improvements with code snippets where helpful
                
                Be constructive and specific. Use markdown formatting for readability.
                """.formatted(lang);

            String result = ai.chat(system, "Review this code:\n\n```%s\n%s\n```".formatted(lang.toLowerCase(), code));
            if (cancelled) return;
            Platform.runLater(() -> {
                lastReview = result;
                reviewContent.setText(result);
                statusBar.setVisible(false);
                statusBar.setManaged(false);
                resultSection.setVisible(true);
                resultSection.setManaged(true);
                reviewBtn.setDisable(false);
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    @FXML
    private void handleClear() {
        codeArea.clear();
        resultSection.setVisible(false);
        resultSection.setManaged(false);
        lastReview = null;
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    @FXML
    private void handleCopy() {
        if (lastReview == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastReview);
        Clipboard.getSystemClipboard().setContent(cc);
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
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
