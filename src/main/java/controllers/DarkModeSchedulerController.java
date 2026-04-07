package controllers;

import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import utils.SessionManager;
import utils.ThemeSwipeHelper;
import utils.SoundManager;

import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

/**
 * Automatic dark/light theme scheduler.
 * Checks every 60 s whether the current time falls in the "dark" or "light" window
 * and toggles the theme accordingly via {@link ThemeSwipeHelper}.
 */
public class DarkModeSchedulerController implements Stoppable {

    @FXML private CheckBox enabledCheck;
    @FXML private ComboBox<String> lightTimeCombo;
    @FXML private ComboBox<String> darkTimeCombo;
    @FXML private Label currentThemeIcon;
    @FXML private Label currentThemeLabel;
    @FXML private Label currentTimeLabel;

    private static final Preferences prefs = Preferences.userNodeForPackage(DarkModeSchedulerController.class);
    private ScheduledExecutorService scheduler;

    /* ════════════════════════════════════════════ lifecycle ═══ */

    @FXML
    private void initialize() {
        // populate hour combos  06:00 – 23:00
        for (int h = 0; h < 24; h++) {
            String t = String.format("%02d:00", h);
            lightTimeCombo.getItems().add(t);
            darkTimeCombo.getItems().add(t);
        }

        // restore persisted values
        enabledCheck.setSelected(prefs.getBoolean("dms_enabled", false));
        lightTimeCombo.setValue(prefs.get("dms_light_time", "07:00"));
        darkTimeCombo.setValue(prefs.get("dms_dark_time", "19:00"));

        updateStatusLabels();
        startScheduler();
    }

    /* ════════════════════════════════════════════ actions ═══ */

    @FXML
    private void handleSave() {
        prefs.putBoolean("dms_enabled", enabledCheck.isSelected());
        prefs.put("dms_light_time", lightTimeCombo.getValue());
        prefs.put("dms_dark_time", darkTimeCombo.getValue());

        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);

        // restart scheduler with new values
        stopScheduler();
        startScheduler();
        updateStatusLabels();
    }

    @FXML
    private void switchLight() {
        applyTheme(false);
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    @FXML
    private void switchDark() {
        applyTheme(true);
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    /* ════════════════════════════════════════════ scheduler ═══ */

    private void startScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dark-mode-scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, 60, TimeUnit.SECONDS);
    }

    private void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void tick() {
        javafx.application.Platform.runLater(() -> {
            updateStatusLabels();
            if (!enabledCheck.isSelected()) return;

            int lightHour = parseHour(lightTimeCombo.getValue());
            int darkHour  = parseHour(darkTimeCombo.getValue());
            int now       = LocalTime.now().getHour();

            boolean shouldBeDark;
            if (lightHour < darkHour) {
                // e.g. light 07 – dark 19 → dark outside [07,19)
                shouldBeDark = now < lightHour || now >= darkHour;
            } else {
                // e.g. light 19 – dark 07 → dark outside [19,07)
                shouldBeDark = now >= darkHour && now < lightHour;
            }

            boolean isDark = SessionManager.getInstance().isDarkTheme();
            if (shouldBeDark != isDark) {
                applyTheme(shouldBeDark);
            }
        });
    }

    /* ════════════════════════════════════════════ helpers ═══ */

    private void applyTheme(boolean dark) {
        Scene scene = enabledCheck.getScene();
        if (scene == null) return;

        String lightCss = ThemeSwipeHelper.class.getResource("/css/light-theme.css").toExternalForm();

        if (dark) {
            scene.getStylesheets().remove(lightCss);
            SessionManager.getInstance().setDarkTheme(true);
        } else {
            if (!scene.getStylesheets().contains(lightCss)) {
                scene.getStylesheets().add(lightCss);
            }
            SessionManager.getInstance().setDarkTheme(false);
        }
        updateStatusLabels();
    }

    private void updateStatusLabels() {
        boolean isDark = SessionManager.getInstance().isDarkTheme();
        currentThemeIcon.setText(isDark ? "🌙" : "☀️");
        currentThemeLabel.setText(isDark ? "Dark Mode" : "Light Mode");
        currentTimeLabel.setText(LocalTime.now().withSecond(0).withNano(0).toString());
    }

    private int parseHour(String val) {
        try {
            return Integer.parseInt(val.split(":")[0]);
        } catch (Exception e) {
            return 7;
        }
    }

    /* ════════════════════════════════════════════ cleanup ═══ */

    @Override
    public void stop() {
        stopScheduler();
    }
}
