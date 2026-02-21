package utils;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/**
 * Install horizontal drag‑to‑swipe theme switching on any pane.
 *
 * <ul>
 *   <li>Drag → right (> threshold): switch to <b>light</b> theme</li>
 *   <li>Drag → left  (> threshold): switch to <b>dark</b> theme</li>
 * </ul>
 *
 * Persists the choice in {@link SessionManager} so it carries to the next scene.
 */
public class ThemeSwipeHelper {

    private static final String LIGHT_CSS = "/css/light-theme.css";
    private static final double SWIPE_THRESHOLD = 100;   // px drag needed to toggle

    private double startX;

    /**
     * Installs the drag-to-swipe listener on the given node.
     * Call this from the controller's {@code initialize()}.
     *
     * @param target the root pane or card to listen on (e.g. rootStack)
     */
    public static void install(Node target) {
        final double[] dragStartX = {0};

        target.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            dragStartX[0] = e.getSceneX();
        });

        target.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
            double deltaX = e.getSceneX() - dragStartX[0];

            if (Math.abs(deltaX) < SWIPE_THRESHOLD) return;

            Scene scene = target.getScene();
            if (scene == null) return;

            boolean isDark = SessionManager.getInstance().isDarkTheme();
            String lightCss = ThemeSwipeHelper.class.getResource(LIGHT_CSS).toExternalForm();

            if (deltaX > 0 && isDark) {
                // ── Swipe RIGHT → go light ──
                scene.getStylesheets().add(lightCss);
                SessionManager.getInstance().setDarkTheme(false);
                playSwipeTransition(target);

            } else if (deltaX < 0 && !isDark) {
                // ── Swipe LEFT → go dark ──
                scene.getStylesheets().remove(lightCss);
                SessionManager.getInstance().setDarkTheme(true);
                playSwipeTransition(target);
            }
        });
    }

    /**
     * Apply the persisted theme to a scene (call after scene creation / navigation).
     */
    public static void applyCurrentTheme(Scene scene) {
        String lightCss = ThemeSwipeHelper.class.getResource(LIGHT_CSS).toExternalForm();
        boolean isDark = SessionManager.getInstance().isDarkTheme();

        if (!isDark && !scene.getStylesheets().contains(lightCss)) {
            scene.getStylesheets().add(lightCss);
        } else if (isDark) {
            scene.getStylesheets().remove(lightCss);
        }
    }

    /** Quick fade+scale pulse to give tactile feedback on theme switch. */
    private static void playSwipeTransition(Node target) {
        FadeTransition fade = new FadeTransition(Duration.millis(180), target);
        fade.setFromValue(0.7);
        fade.setToValue(1.0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(180), target);
        scale.setFromX(0.98);
        scale.setFromY(0.98);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        new ParallelTransition(fade, scale).play();
    }
}
