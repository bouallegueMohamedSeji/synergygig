package controllers;

import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import utils.AppConfig;
import utils.AudioDeviceManager;
import utils.LanguageManager;
import utils.ScreenShareService;
import utils.SessionManager;
import utils.SoundManager;

import javax.sound.sampled.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified settings controller with sidebar navigation.
 * Combines Voice & Video settings + Notification sound settings.
 */
public class SettingsController {

    // ── Sidebar nav ──
    @FXML private Button btnVoiceVideo;
    @FXML private Button btnNotifications;
    @FXML private Button btnApiKeys;
    @FXML private Button btnLanguage;

    // ── Content panels ──
    @FXML private StackPane settingsContent;
    @FXML private VBox voiceVideoPanel;
    @FXML private VBox notificationsPanel;
    @FXML private VBox apiKeysPanel;
    @FXML private VBox themeSchedulePanel;
    @FXML private VBox languagePanel;

    // ── Language ──
    @FXML private Label lblLangTitle;
    @FXML private Label lblLangDesc;
    @FXML private Label lblLangCurrent;
    @FXML private Label lblLangCurrentValue;
    @FXML private VBox languageOptionsContainer;
    @FXML private Label lblLangStatus;

    // ── API Keys ──
    @FXML private VBox apiFieldsContainer;
    @FXML private Button btnSaveApiKeys;
    @FXML private Label lblApiKeyStatus;

    // ── Theme Schedule ──
    @FXML private Button btnThemeSchedule;
    @FXML private TextField txtLightTime;
    @FXML private TextField txtDarkTime;
    @FXML private CheckBox chkScheduleEnabled;
    @FXML private Button btnSaveSchedule;
    @FXML private Label lblScheduleStatus;

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

    /** Map of config key → text field for each API key row. */
    private final Map<String, TextField> apiKeyFields = new LinkedHashMap<>();

    /** Ordered list of API key entries: config key → [display label, description]. */
    private static final String[][] API_KEY_ENTRIES = {
        {"zai.api.key",              "Z.AI Primary Key",        "Primary Z.AI (GLM-5 / GLM-4.7-Flash) API key"},
        {"zai.api.key.backup",       "Z.AI Backup Key",         "Backup Z.AI key — used when the primary is rate-limited"},
        {"siliconflow.api.key",      "SiliconFlow Primary Key", "SiliconFlow AI (GLM-4-9B / Qwen2.5) — free tier"},
        {"siliconflow.api.key.backup","SiliconFlow Backup Key", "Backup SiliconFlow key"},
        {"gemini.api_key",           "Gemini API Key",          "Google Gemini API key (used for some AI features)"},
        {"rest.base_url",            "REST API Base URL",       "Backend REST API base URL"},
        {"smtp.email",               "SMTP Email",              "Gmail address for email notifications"},
        {"smtp.password",            "SMTP App Password",       "Gmail app password (not your account password)"},
        {"n8n.base_url",             "n8n Base URL",            "n8n automation webhook URL (leave empty to skip)"},
    };

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

        // ── Language panel setup ──
        buildLanguageOptions();

        // Default: show Voice & Video
        showVoiceVideo();

        // ── API Keys panel: admin-only visibility ──
        String role = SessionManager.getInstance().getCurrentRole();
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        btnApiKeys.setVisible(isAdmin);
        btnApiKeys.setManaged(isAdmin);

        if (isAdmin) {
            buildApiKeyFields();
        }
    }

    // ═══════════════════════════════════════════
    //  SIDEBAR NAVIGATION
    // ═══════════════════════════════════════════

    @FXML
    private void showVoiceVideo() {
        hideAllPanels();
        voiceVideoPanel.setVisible(true);
        voiceVideoPanel.setManaged(true);
        setActiveNav(btnVoiceVideo);
    }

    @FXML
    private void showNotifications() {
        hideAllPanels();
        notificationsPanel.setVisible(true);
        notificationsPanel.setManaged(true);
        setActiveNav(btnNotifications);
    }

    @FXML
    private void showApiKeys() {
        hideAllPanels();
        apiKeysPanel.setVisible(true);
        apiKeysPanel.setManaged(true);
        setActiveNav(btnApiKeys);
    }

    @FXML
    private void showThemeSchedule() {
        hideAllPanels();
        themeSchedulePanel.setVisible(true);
        themeSchedulePanel.setManaged(true);
        setActiveNav(btnThemeSchedule);
        loadSchedulePrefs();
    }

    @FXML
    private void showLanguage() {
        hideAllPanels();
        languagePanel.setVisible(true);
        languagePanel.setManaged(true);
        setActiveNav(btnLanguage);
        refreshLanguageLabels();
    }

    private void hideAllPanels() {
        voiceVideoPanel.setVisible(false);  voiceVideoPanel.setManaged(false);
        notificationsPanel.setVisible(false); notificationsPanel.setManaged(false);
        apiKeysPanel.setVisible(false);      apiKeysPanel.setManaged(false);
        themeSchedulePanel.setVisible(false); themeSchedulePanel.setManaged(false);
        languagePanel.setVisible(false);     languagePanel.setManaged(false);
    }

    private void setActiveNav(Button active) {
        btnVoiceVideo.getStyleClass().remove("settings-nav-active");
        btnNotifications.getStyleClass().remove("settings-nav-active");
        btnApiKeys.getStyleClass().remove("settings-nav-active");
        btnThemeSchedule.getStyleClass().remove("settings-nav-active");
        btnLanguage.getStyleClass().remove("settings-nav-active");
        if (active != null && !active.getStyleClass().contains("settings-nav-active")) {
            active.getStyleClass().add("settings-nav-active");
        }
    }

    // ═══════════════════════════════════════════
    //  THEME SCHEDULE
    // ═══════════════════════════════════════════

    private void loadSchedulePrefs() {
        txtLightTime.setText(AppConfig.get("theme.schedule.light", "07:00"));
        txtDarkTime.setText(AppConfig.get("theme.schedule.dark", "19:00"));
        chkScheduleEnabled.setSelected("true".equalsIgnoreCase(
                AppConfig.get("theme.schedule.enabled", "false")));
    }

    @FXML
    private void handleSaveSchedule() {
        String lightTime = txtLightTime.getText().trim();
        String darkTime  = txtDarkTime.getText().trim();
        if (!lightTime.matches("\\d{1,2}:\\d{2}") || !darkTime.matches("\\d{1,2}:\\d{2}")) {
            lblScheduleStatus.setText("Invalid time format. Use HH:mm");
            lblScheduleStatus.setStyle("-fx-text-fill: #ff6b6b;");
            return;
        }
        AppConfig.set("theme.schedule.light", lightTime);
        AppConfig.set("theme.schedule.dark", darkTime);
        AppConfig.set("theme.schedule.enabled",
                String.valueOf(chkScheduleEnabled.isSelected()));
        try { AppConfig.save(); } catch (Exception ignored) {}
        lblScheduleStatus.setText("Schedule saved ✓");
        lblScheduleStatus.setStyle("-fx-text-fill: #51cf66;");
    }

    // ═══════════════════════════════════════════
    //  NOTIFICATION SOUND TOGGLES
    // ═══════════════════════════════════════════

    private void buildSoundToggles() {
        soundTogglesContainer.getChildren().clear();

        boolean dark = utils.SessionManager.getInstance().isDarkTheme();

        for (String key : SoundManager.allSoundKeys()) {
            VBox rowWrapper = new VBox(4);
            rowWrapper.setPadding(new Insets(0));

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
            rowWrapper.getChildren().add(row);

            // ── Variant picker (only if multiple variants exist) ──
            if (SoundManager.hasVariants(key)) {
                HBox variantRow = new HBox(8);
                variantRow.setAlignment(Pos.CENTER_LEFT);
                variantRow.setPadding(new Insets(0, 12, 6, 52));

                Label variantLabel = new Label("Sound:");
                variantLabel.setStyle("-fx-font-size: 11; -fx-text-fill: " + (dark ? "#9E9EA8" : "#8A7C7F") + ";");

                ComboBox<String> variantCombo = new ComboBox<>();
                variantCombo.getStyleClass().add("settings-variant-combo");
                variantCombo.setStyle("-fx-font-size: 11; -fx-pref-height: 26;");

                java.util.List<String> variants = SoundManager.getVariants(key);
                for (String suffix : variants) {
                    variantCombo.getItems().add(SoundManager.variantLabel(suffix));
                }

                // Pre-select current variant
                String currentSuffix = soundManager.getSelectedVariant(key);
                int idx = variants.indexOf(currentSuffix);
                variantCombo.getSelectionModel().select(Math.max(0, idx));

                variantCombo.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
                    if (n != null && n.intValue() >= 0 && n.intValue() < variants.size()) {
                        String suffix = variants.get(n.intValue());
                        soundManager.setSelectedVariant(key, suffix);
                    }
                });

                // Preview specific variant
                Button varPreview = new Button("▶");
                varPreview.getStyleClass().add("settings-preview-btn");
                varPreview.setStyle("-fx-font-size: 10; -fx-padding: 2 6;");
                varPreview.setOnAction(e -> {
                    int sel = variantCombo.getSelectionModel().getSelectedIndex();
                    String suffix = (sel >= 0 && sel < variants.size()) ? variants.get(sel) : "";
                    soundManager.playVariant(key, suffix);
                });

                variantRow.getChildren().addAll(variantLabel, variantCombo, varPreview);
                rowWrapper.getChildren().add(variantRow);
            }

            soundTogglesContainer.getChildren().add(rowWrapper);
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

    // ═══════════════════════════════════════════
    //  API KEY MANAGEMENT
    // ═══════════════════════════════════════════

    private void buildApiKeyFields() {
        apiFieldsContainer.getChildren().clear();
        apiKeyFields.clear();

        boolean dark = SessionManager.getInstance().isDarkTheme();

        for (String[] entry : API_KEY_ENTRIES) {
            String configKey  = entry[0];
            String label      = entry[1];
            String desc       = entry[2];
            String currentVal = AppConfig.get(configKey, "");

            VBox fieldBox = new VBox(4);
            fieldBox.getStyleClass().add("settings-api-field");

            // Label
            Label fieldLabel = new Label(label);
            fieldLabel.getStyleClass().add("settings-section-label");

            // Password field (masked) + toggle show button
            HBox inputRow = new HBox(8);
            inputRow.setAlignment(Pos.CENTER_LEFT);

            PasswordField pwField = new PasswordField();
            pwField.setText(currentVal);
            pwField.setPromptText("Enter " + label.toLowerCase());
            pwField.getStyleClass().add("settings-api-input");
            HBox.setHgrow(pwField, Priority.ALWAYS);

            TextField plainField = new TextField();
            plainField.setText(currentVal);
            plainField.setPromptText("Enter " + label.toLowerCase());
            plainField.getStyleClass().add("settings-api-input");
            plainField.setVisible(false);
            plainField.setManaged(false);
            HBox.setHgrow(plainField, Priority.ALWAYS);

            // Sync the two fields
            pwField.textProperty().addListener((o, oldV, newV) -> {
                if (!plainField.getText().equals(newV)) plainField.setText(newV);
            });
            plainField.textProperty().addListener((o, oldV, newV) -> {
                if (!pwField.getText().equals(newV)) pwField.setText(newV);
            });

            Button toggleBtn = new Button("👁");
            toggleBtn.getStyleClass().add("settings-api-toggle-btn");
            toggleBtn.setTooltip(new Tooltip("Show / hide value"));
            toggleBtn.setOnAction(e -> {
                boolean showing = plainField.isVisible();
                pwField.setVisible(showing);
                pwField.setManaged(showing);
                plainField.setVisible(!showing);
                plainField.setManaged(!showing);
                toggleBtn.setText(showing ? "👁" : "🙈");
            });

            inputRow.getChildren().addAll(pwField, plainField, toggleBtn);

            // Description
            Label descLabel = new Label(desc);
            descLabel.getStyleClass().add("settings-api-desc");
            descLabel.setWrapText(true);

            fieldBox.getChildren().addAll(fieldLabel, inputRow, descLabel);
            apiFieldsContainer.getChildren().add(fieldBox);

            // Track by config key — we read from whichever field is active
            apiKeyFields.put(configKey, pwField);
        }
    }

    @FXML
    private void handleSaveApiKeys() {
        try {
            for (Map.Entry<String, TextField> e : apiKeyFields.entrySet()) {
                AppConfig.set(e.getKey(), e.getValue().getText().trim());
            }
            AppConfig.save();

            // Refresh the ZAI service so new keys take effect immediately
            try {
                services.ZAIService.refreshInstance();
            } catch (Exception ignored) {}

            lblApiKeyStatus.setText("✅ Keys saved & reloaded");
            lblApiKeyStatus.setStyle("-fx-text-fill: #4ade80;");

            // Clear status after 4 seconds
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4));
            pause.setOnFinished(ev -> lblApiKeyStatus.setText(""));
            pause.play();

        } catch (Exception ex) {
            lblApiKeyStatus.setText("❌ Save failed: " + ex.getMessage());
            lblApiKeyStatus.setStyle("-fx-text-fill: #ef4444;");
            System.err.println("Failed to save API keys: " + ex.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    //  LANGUAGE
    // ═══════════════════════════════════════════

    private final LanguageManager lang = LanguageManager.getInstance();
    private ToggleGroup langToggleGroup;

    private void buildLanguageOptions() {
        lblLangCurrentValue.setText(lang.getCurrentDisplayName());
        languageOptionsContainer.getChildren().clear();
        langToggleGroup = new ToggleGroup();

        boolean dark = SessionManager.getInstance().isDarkTheme();

        for (int i = 0; i < LanguageManager.SUPPORTED_CODES.length; i++) {
            String code = LanguageManager.SUPPORTED_CODES[i];
            String label = LanguageManager.DISPLAY_NAMES[i];

            RadioButton rb = new RadioButton(label);
            rb.setToggleGroup(langToggleGroup);
            rb.setUserData(code);
            rb.setStyle("-fx-font-size: 15; -fx-text-fill: " + (dark ? "#EAEAF0" : "#23232B") + ";");
            rb.setPadding(new Insets(8, 16, 8, 16));

            if (code.equals(lang.getCode())) {
                rb.setSelected(true);
            }

            rb.selectedProperty().addListener((obs, o, n) -> {
                if (n) {
                    lang.setLanguage(code);
                    refreshLanguageLabels();
                    applyLanguageToSettingsSidebar();
                    lblLangStatus.setText(lang.get("settings.language.changed"));
                    lblLangStatus.setStyle("-fx-text-fill: #4ade80;");
                    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
                    pause.setOnFinished(ev -> lblLangStatus.setText(""));
                    pause.play();
                }
            });

            // ── Card-like wrapper ──
            HBox card = new HBox(12);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setPadding(new Insets(12, 16, 12, 16));
            card.setStyle("-fx-background-color: " + (dark ? "#1E1E2E" : "#F4F4F8")
                    + "; -fx-background-radius: 10; -fx-cursor: hand;");

            // Direction indicator for Arabic
            String dirLabel = "ar".equals(code) ? "  (RTL)" : "";
            Label dirHint = new Label(dirLabel);
            dirHint.setStyle("-fx-font-size: 11; -fx-text-fill: #9E9EA8;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            card.getChildren().addAll(rb, spacer, dirHint);
            card.setOnMouseClicked(e -> rb.setSelected(true));

            languageOptionsContainer.getChildren().add(card);
        }
    }

    /** Update the labels on the Language panel itself to the current language. */
    private void refreshLanguageLabels() {
        lblLangTitle.setText(lang.get("settings.language.title"));
        lblLangDesc.setText(lang.get("settings.language.desc"));
        lblLangCurrent.setText(lang.get("settings.language.current"));
        lblLangCurrentValue.setText(lang.getCurrentDisplayName());

        // Apply RTL alignment if Arabic
        if (lang.isRTL()) {
            languagePanel.setStyle("-fx-alignment: center-right;");
            lblLangTitle.setStyle(lblLangTitle.getStyle() + "; -fx-alignment: center-right;");
        } else {
            languagePanel.setStyle("");
        }
    }

    /** Update sidebar button labels to the current language. */
    private void applyLanguageToSettingsSidebar() {
        btnVoiceVideo.setText(lang.get("settings.voiceVideo"));
        btnNotifications.setText(lang.get("settings.notifications"));
        btnApiKeys.setText(lang.get("settings.apiKeys"));
        btnThemeSchedule.setText(lang.get("settings.themeSchedule"));
        btnLanguage.setText(lang.get("settings.language"));
    }

    /** Call when dialog closes to clean up mic test. */
    public void cleanup() {
        stopMicTest();
    }
}
