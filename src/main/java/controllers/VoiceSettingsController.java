package controllers;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import utils.AudioDeviceManager;

import javax.sound.sampled.*;
import java.util.List;

/**
 * Controller for Discord-style Voice & Video settings dialog.
 * Allows selecting microphone/speaker, adjusting volume, and testing mic.
 */
public class VoiceSettingsController {

    @FXML private ComboBox<String> micCombo;
    @FXML private ComboBox<String> speakerCombo;
    @FXML private Slider micVolumeSlider;
    @FXML private Slider speakerVolumeSlider;
    @FXML private Button btnMicTest;
    @FXML private HBox micLevelContainer;
    @FXML private Label micVolumeLabel;
    @FXML private Label speakerVolumeLabel;

    private final AudioDeviceManager deviceManager = AudioDeviceManager.getInstance();
    private TargetDataLine testMicLine;
    private AnimationTimer levelAnimator;
    private Thread testCaptureThread;
    private volatile boolean testRunning = false;
    private volatile double currentLevel = 0.0;

    // Level bars
    private static final int LEVEL_BAR_COUNT = 30;
    private final Rectangle[] levelBars = new Rectangle[LEVEL_BAR_COUNT];

    @FXML
    public void initialize() {
        loadDevices();
        loadSavedPrefs();
        buildLevelBars();

        // Volume label updates
        micVolumeSlider.valueProperty().addListener((obs, o, n) -> {
            micVolumeLabel.setText(String.format("%.0f%%", n.doubleValue() * 100));
            deviceManager.setMicVolume(n.doubleValue());
        });
        speakerVolumeSlider.valueProperty().addListener((obs, o, n) -> {
            speakerVolumeLabel.setText(String.format("%.0f%%", n.doubleValue() * 100));
            deviceManager.setSpeakerVolume(n.doubleValue());
        });

        // Save device selection on change
        micCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) deviceManager.setSelectedMicName(n);
        });
        speakerCombo.valueProperty().addListener((obs, o, n) -> {
            if (n != null) deviceManager.setSelectedSpeakerName(n);
        });
    }

    private void loadDevices() {
        micCombo.getItems().clear();
        speakerCombo.getItems().clear();

        List<Mixer.Info> inputs = deviceManager.getInputDevices();
        for (Mixer.Info info : inputs) {
            micCombo.getItems().add(info.getName());
        }

        List<Mixer.Info> outputs = deviceManager.getOutputDevices();
        for (Mixer.Info info : outputs) {
            speakerCombo.getItems().add(info.getName());
        }
    }

    private void loadSavedPrefs() {
        // Mic selection
        String savedMic = deviceManager.getSelectedMicName();
        if (!savedMic.isEmpty() && micCombo.getItems().contains(savedMic)) {
            micCombo.setValue(savedMic);
        } else if (!micCombo.getItems().isEmpty()) {
            micCombo.getSelectionModel().selectFirst();
        }

        // Speaker selection
        String savedSpeaker = deviceManager.getSelectedSpeakerName();
        if (!savedSpeaker.isEmpty() && speakerCombo.getItems().contains(savedSpeaker)) {
            speakerCombo.setValue(savedSpeaker);
        } else if (!speakerCombo.getItems().isEmpty()) {
            speakerCombo.getSelectionModel().selectFirst();
        }

        // Volumes
        micVolumeSlider.setValue(deviceManager.getMicVolume());
        speakerVolumeSlider.setValue(deviceManager.getSpeakerVolume());
        micVolumeLabel.setText(String.format("%.0f%%", deviceManager.getMicVolume() * 100));
        speakerVolumeLabel.setText(String.format("%.0f%%", deviceManager.getSpeakerVolume() * 100));
    }

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
            btnMicTest.setText("Mic Test");
            btnMicTest.getStyleClass().remove("mic-test-active");
        } else {
            startMicTest();
            btnMicTest.setText("Stop Test");
            if (!btnMicTest.getStyleClass().contains("mic-test-active")) {
                btnMicTest.getStyleClass().add("mic-test-active");
            }
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

            // UI level animation
            levelAnimator = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    updateLevelBars(currentLevel);
                }
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
        // Reset level bars
        for (Rectangle bar : levelBars) {
            bar.setFill(Color.web("#2C2C3A"));
        }
    }

    private void updateLevelBars(double level) {
        int activeBars = (int) (level * LEVEL_BAR_COUNT);
        for (int i = 0; i < LEVEL_BAR_COUNT; i++) {
            if (i < activeBars) {
                // Green for low, yellow for mid, red for high
                double ratio = (double) i / LEVEL_BAR_COUNT;
                if (ratio < 0.6) {
                    levelBars[i].setFill(Color.web("#4ade80"));
                } else if (ratio < 0.85) {
                    levelBars[i].setFill(Color.web("#facc15"));
                } else {
                    levelBars[i].setFill(Color.web("#ef4444"));
                }
            } else {
                levelBars[i].setFill(Color.web("#2C2C3A"));
            }
        }
    }

    /** Call this when dialog closes to clean up. */
    public void cleanup() {
        stopMicTest();
    }
}
