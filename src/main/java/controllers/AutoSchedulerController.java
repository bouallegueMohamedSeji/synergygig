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

import java.time.LocalDate;
import java.util.stream.IntStream;

public class AutoSchedulerController implements Stoppable {

    @FXML private TextArea tasksArea;
    @FXML private ComboBox<String> startHour, endHour;
    @FXML private DatePicker datePicker;
    @FXML private Button scheduleBtn;
    @FXML private HBox statusBar;
    @FXML private Label statusLabel;
    @FXML private VBox resultSection;
    @FXML private Label scheduleContent;

    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private String lastSchedule;

    @FXML
    private void initialize() {
        var hours = FXCollections.observableArrayList(
            IntStream.rangeClosed(6, 22).mapToObj(h -> String.format("%02d:00", h)).toList()
        );
        startHour.setItems(hours);
        endHour.setItems(hours);
        startHour.getSelectionModel().select("09:00");
        endHour.getSelectionModel().select("17:00");
        datePicker.setValue(LocalDate.now());
    }

    @FXML
    private void handleSchedule() {
        String tasks = tasksArea.getText().trim();
        if (tasks.length() < 10) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Please describe your tasks and constraints.", ButtonType.OK);
            DialogHelper.theme(alert);
            alert.showAndWait();
            return;
        }
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        scheduleBtn.setDisable(true);
        statusBar.setVisible(true);
        statusBar.setManaged(true);
        statusLabel.setText("AI is optimizing your schedule...");

        String start = startHour.getValue();
        String end = endHour.getValue();
        String date = datePicker.getValue() != null ? datePicker.getValue().toString() : "today";

        AppThreadPool.io(() -> {
            String system = """
                You are an expert scheduling assistant. Given a list of tasks, meetings, and constraints,
                generate an optimized daily schedule. Rules:
                - Working hours: %s to %s on %s
                - Place meetings requiring collaboration in the morning when possible
                - Group related tasks together
                - Include short breaks (5-10 min) between meetings
                - Allocate focused/deep-work blocks in the afternoon when possible
                - Format each entry as: "HH:MM - HH:MM | Task Name — brief note"
                - Add a "Schedule Tips" section at the end with 2-3 optimization suggestions
                Be practical and realistic with time estimates.
                """.formatted(start, end, date);

            String result = ai.chat(system, "Schedule these tasks:\n\n" + tasks);
            if (cancelled) return;
            Platform.runLater(() -> {
                lastSchedule = result;
                scheduleContent.setText(result);
                statusBar.setVisible(false);
                statusBar.setManaged(false);
                resultSection.setVisible(true);
                resultSection.setManaged(true);
                scheduleBtn.setDisable(false);
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    @FXML
    private void handleCopy() {
        if (lastSchedule == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putString(lastSchedule);
        Clipboard.getSystemClipboard().setContent(cc);
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
    }

    @FXML
    private void handleNew() {
        tasksArea.clear();
        resultSection.setVisible(false);
        resultSection.setManaged(false);
        lastSchedule = null;
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
