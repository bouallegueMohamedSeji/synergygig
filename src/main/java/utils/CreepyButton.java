package utils;

import javafx.animation.AnimationTimer;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseEvent;

/**
 * Creepy Button — makes a button subtly lean toward the mouse cursor,
 * as if magnetically attracted to it.
 * <p>
 * Inspired by <a href="https://www.vengenceui.com/docs/creepy-button">vengenceui Creepy Button</a>.
 * <p>
 * Call {@code CreepyButton.install(button)} to apply the effect.
 * The button will:
 * <ul>
 *   <li>Translate a few pixels toward the cursor</li>
 *   <li>Rotate slightly in the cursor's direction</li>
 *   <li>Scale up subtly on hover</li>
 * </ul>
 * All transitions use smooth lerp-based trailing for the "creepy" lagging feel.
 */
public class CreepyButton {

    private static final double MAX_TRANSLATE = 5.0;   // max pixel shift
    private static final double MAX_ROTATE    = 1.8;   // max rotation degrees
    private static final double HOVER_SCALE   = 1.05;  // subtle grow on hover
    private static final double LERP          = 0.10;  // smoothing (lower = creepier lag)

    /**
     * Install the creepy-follow effect on any button.
     * Safe to call multiple times — only the first call attaches listeners.
     */
    public static void install(ButtonBase button) {
        if (Boolean.TRUE.equals(button.getProperties().get("creepyInstalled"))) return;
        button.getProperties().put("creepyInstalled", Boolean.TRUE);

        final double[] targetXY    = {0, 0};   // normalized target offset (-1..1)
        final double[] smoothXY    = {0, 0};   // current smoothed offset
        final double[] targetScale = {1.0};
        final double[] smoothScale = {1.0};

        button.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            double cx = button.getWidth()  / 2.0;
            double cy = button.getHeight() / 2.0;
            targetXY[0] = clamp((e.getX() - cx) / Math.max(cx, 1), -1, 1);
            targetXY[1] = clamp((e.getY() - cy) / Math.max(cy, 1), -1, 1);
        });

        button.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> {
            targetScale[0] = HOVER_SCALE;
            double cx = button.getWidth()  / 2.0;
            double cy = button.getHeight() / 2.0;
            targetXY[0] = clamp((e.getX() - cx) / Math.max(cx, 1), -1, 1);
            targetXY[1] = clamp((e.getY() - cy) / Math.max(cy, 1), -1, 1);
            // Snap smoothed to current to avoid jumps
            smoothXY[0] = targetXY[0] * 0.5;
            smoothXY[1] = targetXY[1] * 0.5;
        });

        button.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            targetXY[0]    = 0;
            targetXY[1]    = 0;
            targetScale[0] = 1.0;
        });

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Smooth interpolation toward target
                smoothXY[0]    += (targetXY[0]    - smoothXY[0])    * LERP;
                smoothXY[1]    += (targetXY[1]    - smoothXY[1])    * LERP;
                smoothScale[0] += (targetScale[0] - smoothScale[0]) * LERP;

                button.setTranslateX(smoothXY[0] * MAX_TRANSLATE);
                button.setTranslateY(smoothXY[1] * MAX_TRANSLATE);
                button.setRotate(smoothXY[0] * MAX_ROTATE);
                button.setScaleX(smoothScale[0]);
                button.setScaleY(smoothScale[0]);
            }
        };

        // Start / stop with scene lifecycle
        button.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) timer.start();
            else                  timer.stop();
        });
        if (button.getScene() != null) timer.start();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
