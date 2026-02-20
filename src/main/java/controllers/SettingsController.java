package controllers;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import utils.AudioDeviceManager;
import utils.ScreenShareService;
import utils.SoundManager;

import javax.sound.sampled.*;
import java.util.List;

/**
 * Unified settings controller with sidebar navigation.
 * Combines Voice & Video settings + Notification sound settings.
 */
public class SettingsController {

    // ── Sidebar nav ──
    @FXML private Button btnVoiceVideo;
    @FXML private Button btnNotifications;

    // ── Content panels ──
    @FXML private StackPane settingsContent;
    @FXML private VBox voiceVideoPanel;
    @FXML private VBox notificationsPanel;

    // ── Voice & Video ──
    @FXML private ComboBox<String> micCombo;
    @FXML private ComboBox<String> speakerCombo;
    @FXML private Slider micVolumeSlider;
    @FXML private Slider speakerVolumeSlider;
    @FXML private Button btnMicTest;
    @FXML private HBox micLevelContainer;
    @FXML private Label micVolumeLabel;
    @FXML private Label speakerVolumeLabel;

    // ── Screen Share ──
    @FXML private ComboBox<ScreenShareService.Resolution> resolutionCombo;
    @FXML private Slider fpsSlider;
    @FXML private Slider qualitySlider;
    @FXML private Label fpsLabel;
    @FXML private Label qualityLabel;

    // ── Notifications ──
    @FXML private CheckBox chkSoundsEnabled;
    @FXML private Slider masterVolumeSlider;
    @FXML private Label masterVolumeLabel;
    @FXML private VBox soundTogglesContainer;

    // ── Audio test state ──
    private final AudioDeviceManager deviceManager = AudioDeviceManager.getInstance();
    private final SoundManager soundManager = SoundManager.getInstance();
    private final ScreenShareService screenShareService = new ScreenShareService();
    private TargetDataLine testMicLine;
    private AnimationTimer levelAnimator;
    private Thread testCaptureThread;
    private volatile boolean testRunning = false;
    private volatile double currentLevel = 0.0;
    private static final int LEVEL_BAR_COUNT = 30;
    private final Rectangle[] levelBars = new Rectangle[LEVEL_BAR_COUNT];

    @FXML
    public void initialize() {
        // ── Voice & Video setup ──
        loadDevices();
        loadSavedVoicePrefs();
        buildLevelBars();

        micVolumeSlider.valueProperty().addListener((obs, o, n) -> {
            micVolumeLabel.setText(String.format("%.0f%%", n.doubleValue() * 100));
            deviceManager.setMicVolume(n.doubleValue());
        });
        speakerVolumeSlider.valueProperty().addListener((obs, o, n) -> {
            speakerVolumeLabel.setText(String.format("%.0f%%", n.doubleValue() * 100));
            deviceManager.setSpeakerVolume(n.doubleValue());
        });
        micCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) deviceManager.setSelectedMicName(n);
        });
        speakerCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) deviceManager.setSelectedSpeakerName(n);
        });

        // ── Notifications setup ──
        chkSoundsEnabled.setSelected(soundManager.isSoundsEnabled());
        masterVolumeSlider.setValue(soundManager.getMasterVolume());
        masterVolumeLabel.setText(String.format("%.0f%%", soundManager.getMasterVolume() * 100));

        chkSoundsEnabled.selectedProperty().addListener((obs, o, n) ->
                soundManager.setSoundsEnabled(n));

        masterVolumeSlider.valueProperty().addListener((obs, o, n) -> {
            soundManager.setMasterVolume(n.doubleValue());
            masterVolumeLabel.setText(String.format("%.0f%%", n.doubleValue() * 100));
        });

        buildSoundToggles();

        // ── Screen Share setup ──
        resolutionCombo.getItems().addAll(ScreenShareService.allResolutions());
        resolutionCombo.setValue(screenShareService.getResolution());
        resolutionCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) screenShareService.setResolution(n);
        });

        fpsSlider.setValue(screenShareService.getFps());
        fpsLabel.setText(screenShareService.getFps() + " fps");
        fpsSlider.valueProperty().addListener((obs, o, n) -> {
            int val = n.intValue();
            screenShareService.setFps(val);
            fpsLabel.setText(val + " fps");
        });

        qualitySlider.setValue(screenShareService.getJpegQuality());
        qualityLabel.setText(String.format("%.0f%%", screenShareService.getJpegQuality() * 100));
        qualitySlider.valueProperty().addListener((obs, o, n) -> {
            screenShareService.setJpegQuality(n.floatValue());
            qualityLabel.setText(String.format("%.0f%%", n.doubleValue() * 100));
        });

        // Default: show Voice & Video
        showVoiceVideo();
    }

    // ═══════════════════════════════════════════
    //  SIDEBAR NAVIGATION
    // ═══════════════════════════════════════════

    @FXML
    private void showVoiceVideo() {
        voiceVideoPanel.setVisible(true);
        voiceVideoPanel.setManaged(true);
        notificationsPanel.setVisible(false);
        notificationsPanel.setManaged(false);
        setActiveNav(btnVoiceVideo);
    }

    @FXML
    private void showNotifications() {
        voiceVideoPanel.setVisible(false);
        voiceVideoPanel.setManaged(false);
        notificationsPanel.setVisible(true);
        notificationsPanel.setManaged(true);
        setActiveNav(btnNotifications);
    }

    private void setActiveNav(Button active) {
        btnVoiceVideo.getStyleClass().remove("settings-nav-active");
        btnNotifications.getStyleClass().remove("settings-nav-active");
        if (active != null && !active.getStyleClass().contains("settings-nav-active")) {
            active.getStyleClass().add("settings-nav-active");
        }
    }

    // ═══════════════════════════════════════════
    //  NOTIFICATION SOUND TOGGLES
    // ═══════════════════════════════════════════

    private void buildSoundToggles() {
        soundTogglesContainer.getChildren().clear();

        for (String key : SoundManager.allSoundKeys()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("settings-sound-row");
            row.setPadding(new Insets(8, 12, 8, 12));

            // Icon
            Label icon = new Label(SoundManager.getIcon(key));
            icon.setStyle("-fx-font-size: 16; -fx-min-width: 24;");

            // Label
            Label label = new Label(SoundManager.getLabel(key));
            label.getStyleClass().add("settings-sound-label");
            HBox.setHgrow(label, Priority.ALWAYS);

            // Preview button
            Button previewBtn = new Button("▶");
            previewBtn.getStyleClass().add("settings-preview-btn");
            previewBtn.setOnAction(e -> {
                // Temporarily force play even if disabled
                boolean wasEnabled = soundManager.isSoundsEnabled();
                boolean wasKeyEnabled = soundManager.isSoundEnabled(key);
                soundManager.setSoundsEnabled(true);
                soundManager.setSoundEnabled(key, true);
                soundManager.play(key);
                soundManager.setSoundsEnabled(wasEnabled);
                soundManager.setSoundEnabled(key, wasKeyEnabled);
            });

            // Toggle
            CheckBox toggle = new CheckBox();
            toggle.setSelected(soundManager.isSoundEnabled(key));
            toggle.getStyleClass().add("settings-checkbox");
            toggle.selectedProperty().addListener((obs, o, n) ->
                    soundManager.setSoundEnabled(key, n));

            row.getChildren().addAll(icon, label, previewBtn, toggle);
            soundTogglesContainer.getChildren().add(row);
        }
    }

    // ═══════════════════════════════════════════
    //  VOICE DEVICES
    // ═══════════════════════════════════════════

    private void loadDevices() {
        micCombo.getItems().clear();
        speakerCombo.getItems().clear();

        List<Mixer.Info> inputs = deviceManager.getInputDevices();
        for (Mixer.Info info : inputs) micCombo.getItems().add(info.getName());

        List<Mixer.Info> outputs = deviceManager.getOutputDevices();
        for (Mixer.Info info : outputs) speakerCombo.getItems().add(info.getName());
    }

    private void loadSavedVoicePrefs() {
        String savedMic = deviceManager.getSelectedMicName();
        if (!savedMic.isEmpty() && micCombo.getItems().contains(savedMic))
            micCombo.setValue(savedMic);
        else if (!micCombo.getItems().isEmpty())
            micCombo.getSelectionModel().selectFirst();

        String savedSpeaker = deviceManager.getSelectedSpeakerName();
        if (!savedSpeaker.isEmpty() && speakerCombo.getItems().contains(savedSpeaker))
            speakerCombo.setValue(savedSpeaker);
        else if (!speakerCombo.getItems().isEmpty())
            speakerCombo.getSelectionModel().selectFirst();

        micVolumeSlider.setValue(deviceManager.getMicVolume());
        speakerVolumeSlider.setValue(deviceManager.getSpeakerVolume());
        micVolumeLabel.setText(String.format("%.0f%%", deviceManager.getMicVolume() * 100));
        speakerVolumeLabel.setText(String.format("%.0f%%", deviceManager.getSpeakerVolume() * 100));
    }

    // ═══════════════════════════════════════════
    //  MIC TEST
    // ═══════════════════════════════════════════

    private void buildLevelBars() {
        micLevelContainer.getChildren().clear();
        micLevelContainer.setSpacing(2);
        for (int i = 0; i < LEVEL_BAR_COUNT; i++) {
            Rectangle bar = new Rectangle(8, 20);
            bar.setArcWidth(3);
            bar.setArcHeight(3);
            bar.setFill(Color.web("#2C2C3A"));
            levelBars[i] = bar;
            micLevelContainer.getChildren().add(bar);
        }
    }

    @FXML
    private void handleMicTest() {
        if (testRunning) {
            stopMicTest();
            btnMicTest.setText("🎙 Mic Test");
            btnMicTest.getStyleClass().remove("mic-test-active");
        } else {
            startMicTest();
            btnMicTest.setText("⏹ Stop Test");
            if (!btnMicTest.getStyleClass().contains("mic-test-active"))
                btnMicTest.getStyleClass().add("mic-test-active");
        }
    }

    private void startMicTest() {
        testRunning = true;
        try {
            testMicLine = deviceManager.openMicLine();
            testMicLine.start();

            testCaptureThread = new Thread(() -> {
                byte[] buffer = new byte[640];
                while (testRunning) {
                    int read = testMicLine.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        AudioDeviceManager.applyVolume(buffer, read, deviceManager.getMicVolume());
                        currentLevel = AudioDeviceManager.calculateLevel(buffer, read);
                    }
                }
            }, "mic-test");
            testCaptureThread.setDaemon(true);
            testCaptureThread.start();

            levelAnimator = new AnimationTimer() {
                @Override public void handle(long now) { updateLevelBars(currentLevel); }
            };
            levelAnimator.start();

        } catch (LineUnavailableException e) {
            System.err.println("Could not open mic for test: " + e.getMessage());
            testRunning = false;
        }
    }

    private void stopMicTest() {
        testRunning = false;
        if (levelAnimator != null) { levelAnimator.stop(); levelAnimator = null; }
        if (testMicLine != null) {
            try { testMicLine.stop(); testMicLine.close(); } catch (Exception ignored) {}
            testMicLine = null;
        }
        if (testCaptureThread != null) {
            testCaptureThread.interrupt();
            testCaptureThread = null;
        }
        for (Rectangle bar : levelBars) bar.setFill(Color.web("#2C2C3A"));
    }

    private void updateLevelBars(double level) {
        int activeBars = (int) (level * LEVEL_BAR_COUNT);
        for (int i = 0; i < LEVEL_BAR_COUNT; i++) {
            if (i < activeBars) {
                double ratio = (double) i / LEVEL_BAR_COUNT;
                if (ratio < 0.6) levelBars[i].setFill(Color.web("#4ade80"));
                else if (ratio < 0.85) levelBars[i].setFill(Color.web("#facc15"));
                else levelBars[i].setFill(Color.web("#ef4444"));
            } else {
                levelBars[i].setFill(Color.web("#2C2C3A"));
            }
        }
    }

    /** Call when dialog closes to clean up mic test. */
    public void cleanup() {
        stopMicTest();
    }
}
