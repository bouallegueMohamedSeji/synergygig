package controllers;

import com.google.gson.*;
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

public class ResumeParserController implements Stoppable {

    @FXML private VBox dropZone;
    @FXML private Label dropIcon, dropLabel;
    @FXML private Button uploadBtn;
    @FXML private HBox statusBar;
    @FXML private ProgressIndicator spinner;
    @FXML private Label statusLabel;
    @FXML private VBox resultContainer;

    // Identity
    @FXML private Label resName, resEmail, resPhone, resLocation;
    // Summary
    @FXML private Label resSummary;
    // Skills
    @FXML private FlowPane skillsFlow;
    // Experience, Education, Certs, Languages
    @FXML private VBox experienceBox, educationBox, certsBox, languagesBox;

    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private String lastParsedJson;

    @FXML
    private void initialize() {
        // nothing special needed on init
    }

    @FXML
    private void handleUpload() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Resume / CV");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Resume Files", "*.pdf", "*.docx"),
            new FileChooser.ExtensionFilter("PDF", "*.pdf"),
            new FileChooser.ExtensionFilter("Word DOCX", "*.docx")
        );
        File file = fc.showOpenDialog(dropZone.getScene().getWindow());
        if (file == null) return;

        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        showStatus("Extracting text from " + file.getName() + "...");

        AppThreadPool.io(() -> {
            try {
                String text = DocumentExtractor.extract(file);
                if (text == null || text.trim().length() < 30) {
                    Platform.runLater(() -> showError("Could not extract meaningful text from the file."));
                    return;
                }
                Platform.runLater(() -> showStatus("AI is parsing your resume..."));

                String json = ai.parseResume(text);
                if (cancelled) return;
                Platform.runLater(() -> renderResult(json));
            } catch (Exception e) {
                if (!cancelled) Platform.runLater(() -> showError("Error: " + e.getMessage()));
            }
        });
    }

    private void renderResult(String raw) {
        try {
            // Strip markdown code fences if present
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            lastParsedJson = new GsonBuilder().setPrettyPrinting().create().toJson(obj);

            resName.setText(str(obj, "name"));
            resEmail.setText(str(obj, "email"));
            resPhone.setText(str(obj, "phone"));
            resLocation.setText(str(obj, "location"));
            resSummary.setText(str(obj, "summary"));

            // Skills
            skillsFlow.getChildren().clear();
            if (obj.has("skills") && obj.get("skills").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("skills")) {
                    Label pill = new Label(el.getAsString());
                    pill.getStyleClass().add("skill-pill");
                    pill.setStyle("-fx-background-color: rgba(144,221,240,0.15); -fx-text-fill: #90DDF0; " +
                        "-fx-padding: 4 12; -fx-background-radius: 12; -fx-font-size: 12;");
                    skillsFlow.getChildren().add(pill);
                }
            }

            // Experience
            experienceBox.getChildren().clear();
            if (obj.has("experience") && obj.get("experience").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("experience")) {
                    JsonObject exp = el.getAsJsonObject();
                    VBox card = new VBox(4);
                    Label title = new Label(str(exp, "title") + "  •  " + str(exp, "company"));
                    title.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");
                    title.setWrapText(true);
                    Label period = new Label(str(exp, "period"));
                    period.setStyle("-fx-font-size: 11; -fx-opacity: 0.6;");
                    Label desc = new Label(str(exp, "description"));
                    desc.setWrapText(true);
                    desc.setStyle("-fx-font-size: 12;");
                    card.getChildren().addAll(title, period, desc);
                    experienceBox.getChildren().add(card);
                }
            }

            // Education
            educationBox.getChildren().clear();
            if (obj.has("education") && obj.get("education").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("education")) {
                    JsonObject edu = el.getAsJsonObject();
                    Label lbl = new Label(str(edu, "degree") + "  —  " + str(edu, "institution") + "  (" + str(edu, "year") + ")");
                    lbl.setWrapText(true);
                    lbl.setStyle("-fx-font-size: 13;");
                    educationBox.getChildren().add(lbl);
                }
            }

            // Certifications
            certsBox.getChildren().clear();
            if (obj.has("certifications") && obj.get("certifications").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("certifications")) {
                    Label lbl = new Label("• " + el.getAsString());
                    lbl.setWrapText(true);
                    lbl.setStyle("-fx-font-size: 12;");
                    certsBox.getChildren().add(lbl);
                }
            }
            if (certsBox.getChildren().isEmpty()) {
                certsBox.getChildren().add(new Label("None found"));
            }

            // Languages
            languagesBox.getChildren().clear();
            if (obj.has("languages") && obj.get("languages").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("languages")) {
                    Label lbl = new Label("• " + el.getAsString());
                    lbl.setStyle("-fx-font-size: 12;");
                    languagesBox.getChildren().add(lbl);
                }
            }
            if (languagesBox.getChildren().isEmpty()) {
                languagesBox.getChildren().add(new Label("None found"));
            }

            hideStatus();
            dropZone.setVisible(false);
            dropZone.setManaged(false);
            resultContainer.setVisible(true);
            resultContainer.setManaged(true);
            SoundManager.getInstance().play(SoundManager.LOGIN_SUCCESS);
        } catch (Exception e) {
            showError("AI returned invalid JSON. Try again.\n" + e.getMessage());
        }
    }

    private String str(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return "—";
        String val = obj.get(key).getAsString().trim();
        return val.isEmpty() ? "—" : val;
    }

    @FXML
    private void handleReset() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        resultContainer.setVisible(false);
        resultContainer.setManaged(false);
        dropZone.setVisible(true);
        dropZone.setManaged(true);
        hideStatus();
        lastParsedJson = null;
    }

    @FXML
    private void handleCopy() {
        if (lastParsedJson == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastParsedJson);
        Clipboard.getSystemClipboard().setContent(cc);
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
        statusBar.setVisible(true);
        statusBar.setManaged(true);
    }

    private void hideStatus() {
        statusBar.setVisible(false);
        statusBar.setManaged(false);
    }

    private void showError(String msg) {
        hideStatus();
        SoundManager.getInstance().play(SoundManager.ERROR);
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText("Parse Error");
        DialogHelper.theme(alert);
        alert.showAndWait();
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/OfferContract.fxml");
    }

    @Override
    public void stop() {
        cancelled = true;
    }
}
