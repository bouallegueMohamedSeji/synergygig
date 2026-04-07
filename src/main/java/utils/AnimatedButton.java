package utils;

import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Aceternity-style animated button.
 * <p>
 * Normal state : shows text label, icon is hidden off-screen to the left.
 * Hover        : text slides out to the right, icon slides in from the left.
 * Click        : quick scale pulse.
 * <p>
 * Usage:
 * <pre>
 *   AnimatedButton btn = AnimatedButton.create("Schedule Interview", "📅",
 *       "btn-primary", e -&gt; handleSchedule());
 *   someContainer.getChildren().add(btn);
 * </pre>
 */
public class AnimatedButton {

    private static final Duration ANIM_DURATION = Duration.millis(300);
    private static final Interpolator SMOOTH = Interpolator.SPLINE(0.25, 0.1, 0.25, 1);

    /**
     * Factory — creates a fully wired animated button StackPane.
     *
     * @param text      label shown by default
     * @param icon      emoji or symbol shown on hover
     * @param styleClass CSS class for the wrapper (e.g. "btn-primary", "btn-animated-teal")
     * @param action    on-click handler
     * @return a StackPane that behaves like a button
     */
    public static StackPane create(String text, String icon,
                                   String styleClass,
                                   javafx.event.EventHandler<? super javafx.scene.input.MouseEvent> action) {

        /* ── Text label (default visible, slides right on hover) ── */
        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("anim-btn-text");
        textLabel.setMouseTransparent(true);

        /* ── Icon label (starts off-screen left, slides to center on hover) ── */
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("anim-btn-icon");
        iconLabel.setMouseTransparent(true);
        iconLabel.setOpacity(0);
        iconLabel.setTranslateX(-40);

        /* ── Wrapper ── */
        StackPane wrapper = new StackPane(textLabel, iconLabel);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setCursor(Cursor.HAND);
        wrapper.getStyleClass().addAll("anim-btn", styleClass);

        // Clip so sliding labels don't overflow
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(wrapper.widthProperty());
        clip.heightProperty().bind(wrapper.heightProperty());
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        wrapper.setClip(clip);

        /* ── Hover animations ── */
        // Text → slide right + fade
        TranslateTransition textSlideOut = createTranslate(textLabel, 0, 80);
        TranslateTransition textSlideIn  = createTranslate(textLabel, 80, 0);

        // Icon → slide from left to center + appear
        TranslateTransition iconSlideIn  = createTranslate(iconLabel, -40, 0);
        TranslateTransition iconSlideOut = createTranslate(iconLabel, 0, -40);

        wrapper.setOnMouseEntered(e -> {
            textSlideIn.stop();
            iconSlideOut.stop();

            textSlideOut.play();
            iconSlideIn.play();

            // Fade
            textLabel.setOpacity(1);
            animateOpacity(textLabel, 0, ANIM_DURATION);
            iconLabel.setOpacity(0);
            animateOpacity(iconLabel, 1, ANIM_DURATION);
        });

        wrapper.setOnMouseExited(e -> {
            textSlideOut.stop();
            iconSlideIn.stop();

            textSlideIn.play();
            iconSlideOut.play();

            animateOpacity(textLabel, 1, ANIM_DURATION);
            animateOpacity(iconLabel, 0, ANIM_DURATION);
        });

        /* ── Press pulse ── */
        ScaleTransition pressDown = new ScaleTransition(Duration.millis(100), wrapper);
        pressDown.setToX(0.95);
        pressDown.setToY(0.95);
        pressDown.setInterpolator(SMOOTH);

        ScaleTransition pressUp = new ScaleTransition(Duration.millis(200), wrapper);
        pressUp.setToX(1.0);
        pressUp.setToY(1.0);
        pressUp.setInterpolator(SMOOTH);

        wrapper.setOnMousePressed(e -> {
            pressUp.stop();
            pressDown.playFromStart();
        });

        wrapper.setOnMouseReleased(e -> {
            pressDown.stop();
            pressUp.playFromStart();
        });

        /* ── Click action ── */
        wrapper.setOnMouseClicked(action);

        return wrapper;
    }

    /**
     * Simpler variant — styled as a secondary outlined animated button.
     */
    public static StackPane createSecondary(String text, String icon,
                                            javafx.event.EventHandler<? super javafx.scene.input.MouseEvent> action) {
        return create(text, icon, "btn-animated-secondary", action);
    }

    /**
     * Teal primary gradient variant.
     */
    public static StackPane createPrimary(String text, String icon,
                                          javafx.event.EventHandler<? super javafx.scene.input.MouseEvent> action) {
        return create(text, icon, "btn-animated-primary", action);
    }

    /* ── helpers ── */

    private static TranslateTransition createTranslate(Label node, double fromX, double toX) {
        TranslateTransition tt = new TranslateTransition(ANIM_DURATION, node);
        tt.setFromX(fromX);
        tt.setToX(toX);
        tt.setInterpolator(SMOOTH);
        return tt;
    }

    private static void animateOpacity(Label node, double target, Duration dur) {
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(dur, node);
        ft.setToValue(target);
        ft.setInterpolator(SMOOTH);
        ft.play();
    }
}
