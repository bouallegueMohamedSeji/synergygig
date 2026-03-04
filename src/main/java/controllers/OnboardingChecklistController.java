package controllers;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import services.ZAIService;
import utils.AppThreadPool;
import utils.SoundManager;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Onboarding checklist with persisted completion state and AI help.
 */
public class OnboardingChecklistController implements Stoppable {

    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private VBox checklistContainer;
    @FXML private TextField helpInput;
    @FXML private Label helpAnswer;

    private final Preferences prefs = Preferences.userNodeForPackage(OnboardingChecklistController.class);
    private final ZAIService ai = new ZAIService();
    private volatile boolean cancelled;
    private final List<CheckBox> allChecks = new ArrayList<>();

    // Structured checklist data
    private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>();
    static {
        SECTIONS.put("📋 Day 1 — IT & Access Setup", List.of(
            "Set up company email account",
            "Configure VPN access",
            "Install required software (IDE, Slack, etc.)",
            "Set up MFA / two-factor authentication",
            "Test access to internal systems"
        ));
        SECTIONS.put("👤 Week 1 — Meet Your Team", List.of(
            "Meet your manager for 1:1 intro",
            "Meet your buddy/mentor",
            "Attend team introduction meeting",
            "Review team wiki / documentation",
            "Set up your profile (avatar, bio, skills)"
        ));
        SECTIONS.put("📚 Week 1-2 — Required Training", List.of(
            "Complete Security Awareness training",
            "Complete Anti-Harassment training",
            "Complete Data Privacy training",
            "Review company Code of Conduct",
            "Read the Employee Handbook"
        ));
        SECTIONS.put("🎯 Week 2 — Getting Productive", List.of(
            "Set up your development environment",
            "Complete first assigned task/ticket",
            "Attend first stand-up meeting",
            "Submit your first timesheet",
            "Schedule 30-day check-in with manager"
        ));
        SECTIONS.put("✅ Month 1 — Wrap Up", List.of(
            "Complete probation goals document",
            "Set quarterly objectives with manager",
            "Join relevant Slack channels / groups",
            "Provide onboarding feedback to HR"
        ));
    }

    @FXML
    private void initialize() {
        buildChecklist();
        updateProgress();
    }

    private void buildChecklist() {
        checklistContainer.getChildren().clear();
        allChecks.clear();

        for (var entry : SECTIONS.entrySet()) {
            VBox section = new VBox(8);
            section.getStyleClass().add("card");
            section.setPadding(new Insets(16, 20, 16, 20));

            Label header = new Label(entry.getKey());
            header.setStyle("-fx-font-size: 15; -fx-font-weight: bold;");
            section.getChildren().add(header);
            section.getChildren().add(new Separator());

            for (String task : entry.getValue()) {
                String key = "onboard_" + task.hashCode();
                CheckBox cb = new CheckBox(task);
                cb.setSelected(prefs.getBoolean(key, false));
                cb.setStyle("-fx-font-size: 13;");
                cb.selectedProperty().addListener((o, ov, nv) -> {
                    prefs.putBoolean(key, nv);
                    SoundManager.getInstance().play(nv ? SoundManager.TASK_COMPLETED : SoundManager.BUTTON_CLICK);
                    updateProgress();
                });
                allChecks.add(cb);
                section.getChildren().add(cb);
            }
            checklistContainer.getChildren().add(section);
        }
    }

    private void updateProgress() {
        long done = allChecks.stream().filter(CheckBox::isSelected).count();
        double pct = allChecks.isEmpty() ? 0 : (double) done / allChecks.size();
        progressBar.setProgress(pct);
        progressLabel.setText(Math.round(pct * 100) + "%");
    }

    @FXML
    private void handleAskHelp() {
        String question = helpInput.getText().trim();
        if (question.isEmpty()) return;
        helpInput.clear();
        helpAnswer.setText("Thinking...");
        helpAnswer.setVisible(true);
        helpAnswer.setManaged(true);
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);

        AppThreadPool.io(() -> {
            String answer = ai.chat(
                "You are a friendly onboarding assistant for SynergyGig. " +
                "Help new employees with onboarding questions. Be concise and practical.",
                question
            );
            if (cancelled) return;
            Platform.runLater(() -> {
                helpAnswer.setText(answer);
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
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
