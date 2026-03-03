package controllers;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import services.ZAIService;
import utils.AppThreadPool;
import utils.SoundManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class InterviewPrepController implements Stoppable {

    @FXML private ScrollPane chatScroll;
    @FXML private VBox chatBox;
    @FXML private VBox setupRow;
    @FXML private HBox inputRow;
    @FXML private TextField roleField, answerField;
    @FXML private ComboBox<String> levelCombo;
    @FXML private Label questionBadge;
    @FXML private ProgressBar progressBar;

    private final ZAIService ai = new ZAIService();
    private final List<Map<String, String>> history = new ArrayList<>();
    private volatile boolean cancelled;
    private int questionCount;
    private static final int MAX_QUESTIONS = 8;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private Timeline typingAnim;

    @FXML
    private void initialize() {
        levelCombo.setItems(FXCollections.observableArrayList(
            "Junior", "Mid-level", "Senior", "Lead / Staff", "Manager"
        ));
        levelCombo.getSelectionModel().selectFirst();
        addWelcomeCard();
    }

    /* ──────── Welcome card (first-time help) ──────── */
    private void addWelcomeCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("ip-welcome-card");
        card.setPadding(new Insets(16, 18, 16, 18));

        Label title = new Label("👋 Welcome to Interview Prep Coach");
        title.getStyleClass().add("ip-welcome-title");

        Label body = new Label(
            "Configure your target role and experience level below, then press Start.\n" +
            "The AI will ask you questions one at a time. You can:\n" +
            "  •  Type your answer and press Send\n" +
            "  •  Skip a question to see a sample answer\n" +
            "  •  End the session for a performance summary"
        );
        body.setWrapText(true);
        body.getStyleClass().add("ip-welcome-body");

        HBox tips = new HBox(10);
        tips.setAlignment(Pos.CENTER_LEFT);
        tips.getChildren().addAll(
            buildTipChip("🎤", "Behavioral"),
            buildTipChip("💻", "Technical"),
            buildTipChip("🧩", "Situational")
        );

        card.getChildren().addAll(title, body, tips);
        chatBox.getChildren().add(card);
    }

    private HBox buildTipChip(String icon, String label) {
        Label ic = new Label(icon);
        ic.setStyle("-fx-font-size: 13;");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("ip-chip-text");
        HBox chip = new HBox(4, ic, lbl);
        chip.getStyleClass().add("ip-chip");
        chip.setPadding(new Insets(4, 10, 4, 8));
        chip.setAlignment(Pos.CENTER);
        return chip;
    }

    /* ──────── Start session ──────── */
    @FXML
    private void handleStart() {
        String role = roleField.getText().trim();
        if (role.isEmpty()) {
            shakeNode(roleField);
            return;
        }
        String level = levelCombo.getValue();
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);

        // Toggle UI
        setupRow.setVisible(false);
        setupRow.setManaged(false);
        inputRow.setVisible(true);
        inputRow.setManaged(true);
        questionBadge.setVisible(true);
        questionBadge.setManaged(true);
        progressBar.setVisible(true);
        progressBar.setManaged(true);

        chatBox.getChildren().clear();
        history.clear();
        questionCount = 0;
        updateProgress();

        // Session header
        addSystemChip("Session started — " + role + " (" + level + ")");

        addBotMessage("Great! Let's begin your mock interview for **" + role + "** (" +
            level + " level). I'll ask you questions one at a time.\n\nReady? Here comes the first question…");

        String systemPrompt = """
            You are an expert interview coach conducting a mock interview for a %s position at %s level.
            Rules:
            - Ask one question at a time
            - Mix behavioral (STAR), technical, and situational questions
            - After the candidate answers, give brief feedback (1-2 sentences) then immediately ask the next question
            - Rate each answer on a scale of 1-5 with a one-line tip
            - If told to "skip", provide a sample answer then move to next question
            - If told to "end", summarize performance with overall rating and top 3 improvement tips
            - Keep questions relevant to the role and seniority level
            Start with an icebreaker / "tell me about yourself" style question.
            """.formatted(role, level);
        history.add(Map.of("role", "system", "content", systemPrompt));

        askNextQuestion();
    }

    /* ──────── Ask next question from AI ──────── */
    private void askNextQuestion() {
        showTypingIndicator();

        AppThreadPool.io(() -> {
            List<Map<String, String>> msgs = new ArrayList<>(history);
            if (questionCount == 0) {
                msgs.add(Map.of("role", "user", "content", "Start the interview. Ask the first question."));
            }
            String reply = ai.chatWithHistory(msgs.get(0).get("content"),
                msgs.subList(1, msgs.size()));
            if (cancelled) return;
            history.add(Map.of("role", "assistant", "content", reply));
            questionCount++;
            Platform.runLater(() -> {
                hideTypingIndicator();
                updateProgress();
                addBotMessage(reply);
                answerField.requestFocus();
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    /* ──────── User actions ──────── */
    @FXML
    private void handleAnswer() {
        String text = answerField.getText().trim();
        if (text.isEmpty()) return;
        answerField.clear();
        SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
        addUserMessage(text);
        history.add(Map.of("role", "user", "content", text));
        askNextQuestion();
    }

    @FXML
    private void handleSkip() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        addUserMessage("⏭ Skipped");
        history.add(Map.of("role", "user", "content",
            "I'll skip this question. Please provide a sample answer, then ask the next question."));
        askNextQuestion();
    }

    @FXML
    private void handleEnd() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        addSystemChip("Session ended — preparing summary…");
        history.add(Map.of("role", "user", "content",
            "End the interview now. Provide a summary with overall rating and top 3 improvement tips."));
        inputRow.setVisible(false);
        inputRow.setManaged(false);

        showTypingIndicator();

        AppThreadPool.io(() -> {
            String reply = ai.chatWithHistory(history.get(0).get("content"),
                history.subList(1, history.size()));
            if (cancelled) return;
            Platform.runLater(() -> {
                hideTypingIndicator();
                addSummaryCard(reply);
                // Show restart button
                setupRow.setVisible(true);
                setupRow.setManaged(true);
                progressBar.setProgress(1.0);
                questionBadge.setText("✅ Complete");
                SoundManager.getInstance().play(SoundManager.NOTIFICATION_POP);
            });
        });
    }

    /* ──────── Bubble builders ──────── */
    private void addUserMessage(String text) {
        VBox col = new VBox(2);
        col.setAlignment(Pos.CENTER_RIGHT);

        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(480);
        lbl.getStyleClass().add("ip-user-bubble");

        Label ts = new Label(TIME_FMT.format(LocalTime.now()));
        ts.getStyleClass().add("ip-timestamp");

        col.getChildren().addAll(lbl, ts);

        HBox row = new HBox(col);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(2, 0, 2, 80));
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        chatBox.getChildren().add(buildBotBubble(text));
        scrollToBottom();
    }

    private HBox buildBotBubble(String text) {
        // Avatar
        StackPane avatar = new StackPane();
        Circle bg = new Circle(16);
        bg.getStyleClass().add("ip-bot-avatar");
        Label ic = new Label("🎯");
        ic.setStyle("-fx-font-size: 14;");
        avatar.getChildren().addAll(bg, ic);

        // Content
        VBox col = new VBox(2);
        col.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label("Interview Coach");
        name.getStyleClass().add("ip-bot-name");

        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(480);
        lbl.getStyleClass().add("ip-bot-bubble");

        Label ts = new Label(TIME_FMT.format(LocalTime.now()));
        ts.getStyleClass().add("ip-timestamp");

        col.getChildren().addAll(name, lbl, ts);

        HBox row = new HBox(8, avatar, col);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(2, 80, 2, 0));
        return row;
    }

    /* ──────── Summary card (end of session) ──────── */
    private void addSummaryCard(String text) {
        VBox card = new VBox(10);
        card.getStyleClass().add("ip-summary-card");
        card.setPadding(new Insets(18, 20, 18, 20));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label trophy = new Label("🏆");
        trophy.setStyle("-fx-font-size: 20;");
        Label title = new Label("Performance Summary");
        title.getStyleClass().add("ip-summary-title");
        Label countLabel = new Label(questionCount + " questions answered");
        countLabel.getStyleClass().add("ip-summary-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(trophy, title, spacer, countLabel);

        Label body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add("ip-summary-body");

        card.getChildren().addAll(header, body);
        chatBox.getChildren().add(card);
        scrollToBottom();
    }

    /* ──────── System timestamp chip (session started / ended) ──────── */
    private void addSystemChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("ip-system-chip");
        HBox row = new HBox(chip);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8, 0, 8, 0));
        chatBox.getChildren().add(row);
        scrollToBottom();
    }

    /* ──────── Animated typing indicator ──────── */
    private void showTypingIndicator() {
        HBox indicator = new HBox(6);
        indicator.setId("typingIndicator");
        indicator.setAlignment(Pos.CENTER_LEFT);
        indicator.setPadding(new Insets(4, 0, 4, 44));

        Label txt = new Label("Coach is typing");
        txt.getStyleClass().add("ip-typing-text");

        HBox dots = new HBox(3);
        dots.setAlignment(Pos.CENTER);
        Circle d1 = new Circle(3); d1.getStyleClass().add("ip-typing-dot");
        Circle d2 = new Circle(3); d2.getStyleClass().add("ip-typing-dot");
        Circle d3 = new Circle(3); d3.getStyleClass().add("ip-typing-dot");
        dots.getChildren().addAll(d1, d2, d3);

        indicator.getChildren().addAll(txt, dots);
        chatBox.getChildren().add(indicator);
        scrollToBottom();

        // Bounce animation
        typingAnim = new Timeline();
        for (int i = 0; i < 3; i++) {
            Circle dot = (Circle) dots.getChildren().get(i);
            KeyFrame up = new KeyFrame(Duration.millis(200 + i * 150),
                new KeyValue(dot.translateYProperty(), -4, Interpolator.EASE_BOTH));
            KeyFrame down = new KeyFrame(Duration.millis(400 + i * 150),
                new KeyValue(dot.translateYProperty(), 0, Interpolator.EASE_BOTH));
            typingAnim.getKeyFrames().addAll(up, down);
        }
        typingAnim.setCycleCount(Animation.INDEFINITE);
        typingAnim.play();
    }

    private void hideTypingIndicator() {
        if (typingAnim != null) { typingAnim.stop(); typingAnim = null; }
        chatBox.getChildren().removeIf(n -> "typingIndicator".equals(n.getId()));
    }

    /* ──────── Progress / badge ──────── */
    private void updateProgress() {
        questionBadge.setText("Q " + questionCount + " / " + MAX_QUESTIONS);
        progressBar.setProgress(Math.min(1.0, (double) questionCount / MAX_QUESTIONS));
    }

    /* ──────── Shake animation for validation ──────── */
    private void shakeNode(javafx.scene.Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(60), node);
        shake.setFromX(0);
        shake.setByX(8);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> chatScroll.setVvalue(1.0));
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/OfferContract.fxml");
    }

    @Override
    public void stop() {
        cancelled = true;
        if (typingAnim != null) typingAnim.stop();
    }
}
