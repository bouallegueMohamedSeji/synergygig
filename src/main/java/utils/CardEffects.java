package utils;

import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * Aceternity UI-inspired card effects for JavaFX.
 * <ul>
 *   <li>{@link #applyHoverEffect(Region)}  – glow / scale on hover (interview &amp; offer cards)</li>
 *   <li>{@link #applyWobbleEffect(Region)} – 3-D tilt that follows the mouse (project cards)</li>
 *   <li>{@link #applyCometEffect(Region)}  – animated comet-trail border glow (training catalog cards)</li>
 *   <li>{@link #apply3DPinEffect(Region)}  – 3-D float lift with glowing pin bar on hover (task cards)</li>
 *   <li>{@link #wrapWithLoadingAction(Button, Runnable)} – loading spinner on button during async work</li>
 * </ul>
 */
public final class CardEffects {

    private CardEffects() {}

    /* ───────────────────────── HOVER EFFECT ───────────────────────── */

    public static void applyHoverEffect(Region card) {
        DropShadow baseShadow = new DropShadow(8, Color.rgb(0, 0, 0, 0.25));
        card.setEffect(baseShadow);

        DropShadow glowShadow = new DropShadow(20, Color.rgb(99, 102, 241, 0.55));
        glowShadow.setSpread(0.15);

        card.setOnMouseEntered(e -> {
            ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), card);
            scaleUp.setToX(1.035);
            scaleUp.setToY(1.035);
            scaleUp.setInterpolator(Interpolator.EASE_OUT);
            scaleUp.play();
            card.setEffect(glowShadow);
            card.setStyle(card.getStyle() + "-fx-border-color: rgba(99,102,241,0.6); -fx-border-width: 1.5; -fx-border-radius: 12;");
        });

        card.setOnMouseExited(e -> {
            ScaleTransition scaleDown = new ScaleTransition(Duration.millis(250), card);
            scaleDown.setToX(1.0);
            scaleDown.setToY(1.0);
            scaleDown.setInterpolator(Interpolator.EASE_BOTH);
            scaleDown.play();
            card.setEffect(baseShadow);
            String style = card.getStyle();
            style = style.replace("-fx-border-color: rgba(99,102,241,0.6); -fx-border-width: 1.5; -fx-border-radius: 12;", "");
            card.setStyle(style);
        });
    }

    /* ───────────────────────── WOBBLE / TILT EFFECT ───────────────────────── */

    public static void applyWobbleEffect(Region card) {
        Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        card.getTransforms().addAll(rotateX, rotateY);

        DropShadow baseShadow = new DropShadow(10, Color.rgb(0, 0, 0, 0.3));
        card.setEffect(baseShadow);

        final double MAX_TILT = 8.0;

        card.setOnMouseMoved(e -> {
            double w = card.getWidth();
            double h = card.getHeight();
            if (w == 0 || h == 0) return;
            double normX = (e.getX() / w) * 2 - 1;
            double normY = (e.getY() / h) * 2 - 1;
            rotateY.setAngle(normX * MAX_TILT);
            rotateX.setAngle(-normY * MAX_TILT);
            rotateX.setPivotX(w / 2);
            rotateX.setPivotY(h / 2);
            rotateY.setPivotX(w / 2);
            rotateY.setPivotY(h / 2);
            DropShadow dynamicShadow = new DropShadow(14, -normX * 6, normY * 6, Color.rgb(0, 0, 0, 0.35));
            card.setEffect(dynamicShadow);
        });

        card.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(180), card);
            st.setToX(1.025);
            st.setToY(1.025);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });

        card.setOnMouseExited(e -> {
            rotateX.setAngle(0);
            rotateY.setAngle(0);
            ScaleTransition st = new ScaleTransition(Duration.millis(350), card);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.play();
            card.setEffect(baseShadow);
        });
    }

    /* ───────────────────────── COMET BORDER EFFECT ───────────────────────── */

    /**
     * Wraps the given card in a StackPane that shows an animated comet-trail
     * gradient border that orbits continuously. The inner card floats on hover.
     * Inspired by Aceternity CometCard.
     *
     * @return the wrapper StackPane (use this instead of the raw card)
     */
    public static StackPane applyCometEffect(Region card) {
        // Outer wrapper that clips the rotating glow
        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().add("comet-wrapper");
        wrapper.setPrefWidth(card.getPrefWidth() + 4);
        if (card.getMaxWidth() > 0) wrapper.setMaxWidth(card.getMaxWidth() + 4);

        // Rotating glow rectangle (the "comet trail")
        Rectangle glowRect = new Rectangle(600, 600);
        glowRect.setManaged(false);

        LinearGradient gradient = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.TRANSPARENT),
                new Stop(0.35, Color.TRANSPARENT),
                new Stop(0.5, Color.rgb(144, 221, 240, 0.8)),  // teal comet head
                new Stop(0.55, Color.rgb(99, 102, 241, 0.5)),   // indigo trail
                new Stop(0.7, Color.TRANSPARENT),
                new Stop(1.0, Color.TRANSPARENT));
        glowRect.setFill(gradient);

        // Spin the glow around the centre
        RotateTransition spin = new RotateTransition(Duration.seconds(3), glowRect);
        spin.setByAngle(360);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.setInterpolator(Interpolator.LINEAR);

        // Clip the wrapper to rounded rect
        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        wrapper.layoutBoundsProperty().addListener((obs, o, n) -> {
            clip.setWidth(n.getWidth());
            clip.setHeight(n.getHeight());
            glowRect.setTranslateX(n.getWidth() / 2 - glowRect.getWidth() / 2);
            glowRect.setTranslateY(n.getHeight() / 2 - glowRect.getHeight() / 2);
        });
        wrapper.setClip(clip);

        // Dark inner bg (slightly inset so the glow border shows through)
        Region innerBg = new Region();
        innerBg.setStyle("-fx-background-color: #0F0E17; -fx-background-radius: 13;");
        StackPane.setMargin(innerBg, new Insets(1.5));

        StackPane.setMargin(card, new Insets(1.5));

        wrapper.getChildren().addAll(glowRect, innerBg, card);

        // Only spin while hovered
        wrapper.setOnMouseEntered(e -> {
            spin.play();
            ScaleTransition st = new ScaleTransition(Duration.millis(200), card);
            st.setToX(1.02);
            st.setToY(1.02);
            st.setInterpolator(Interpolator.EASE_OUT);
            st.play();
        });
        wrapper.setOnMouseExited(e -> {
            spin.pause();
            ScaleTransition st = new ScaleTransition(Duration.millis(300), card);
            st.setToX(1.0);
            st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_BOTH);
            st.play();
        });

        return wrapper;
    }

    /* ───────────────────────── 3-D PIN EFFECT ───────────────────────── */

    /**
     * Wraps a task card in a container that lifts it in 3-D on hover,
     * revealing a glowing "pin" bar below. Inspired by Aceternity 3D-Pin.
     *
     * @return the wrapper VBox (use instead of the raw card)
     */
    public static VBox apply3DPinEffect(Region card) {
        VBox wrapper = new VBox(0);
        wrapper.setAlignment(Pos.TOP_CENTER);

        // The pin line (thin glowing bar below card)
        Region pinLine = new Region();
        pinLine.setPrefHeight(3);
        pinLine.setMaxWidth(60);
        pinLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #7B61FF, transparent); " +
                "-fx-background-radius: 2; -fx-opacity: 0;");

        // Pin dot
        Region pinDot = new Region();
        pinDot.setPrefSize(6, 6);
        pinDot.setMaxSize(6, 6);
        pinDot.setStyle("-fx-background-color: #7B61FF; -fx-background-radius: 3; -fx-opacity: 0;");

        VBox pinContainer = new VBox(2);
        pinContainer.setAlignment(Pos.CENTER);
        pinContainer.getChildren().addAll(pinDot, pinLine);
        pinContainer.setPadding(new Insets(4, 0, 0, 0));

        Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);
        card.getTransforms().addAll(rotateX, rotateY);

        final double MAX_TILT = 6.0;

        DropShadow baseShadow = new DropShadow(6, Color.rgb(0, 0, 0, 0.2));
        DropShadow liftShadow = new DropShadow(22, 0, 8, Color.rgb(123, 97, 255, 0.3));
        card.setEffect(baseShadow);

        card.setOnMouseMoved(e -> {
            double w = card.getWidth();
            double h = card.getHeight();
            if (w == 0 || h == 0) return;
            double normX = (e.getX() / w) * 2 - 1;
            double normY = (e.getY() / h) * 2 - 1;
            rotateY.setAngle(normX * MAX_TILT);
            rotateX.setAngle(-normY * MAX_TILT);
            rotateX.setPivotX(w / 2);
            rotateX.setPivotY(h / 2);
            rotateY.setPivotX(w / 2);
            rotateY.setPivotY(h / 2);
        });

        card.setOnMouseEntered(e -> {
            // Lift card up
            TranslateTransition lift = new TranslateTransition(Duration.millis(200), card);
            lift.setToY(-8);
            lift.setInterpolator(Interpolator.EASE_OUT);
            lift.play();

            card.setEffect(liftShadow);

            // Show pin
            FadeTransition showPin = new FadeTransition(Duration.millis(250), pinContainer);
            showPin.setToValue(1.0);
            showPin.play();
            pinLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #7B61FF, transparent); " +
                    "-fx-background-radius: 2; -fx-opacity: 1;");
            pinDot.setStyle("-fx-background-color: #7B61FF; -fx-background-radius: 3; -fx-opacity: 1;");

            // Glow border
            card.setStyle(card.getStyle() + "-fx-border-color: rgba(123,97,255,0.5); -fx-border-width: 1;");
        });

        card.setOnMouseExited(e -> {
            // Drop card back
            TranslateTransition drop = new TranslateTransition(Duration.millis(300), card);
            drop.setToY(0);
            drop.setInterpolator(Interpolator.EASE_BOTH);
            drop.play();

            rotateX.setAngle(0);
            rotateY.setAngle(0);
            card.setEffect(baseShadow);

            // Hide pin
            FadeTransition hidePin = new FadeTransition(Duration.millis(200), pinContainer);
            hidePin.setToValue(0.0);
            hidePin.play();
            pinLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #7B61FF, transparent); " +
                    "-fx-background-radius: 2; -fx-opacity: 0;");
            pinDot.setStyle("-fx-background-color: #7B61FF; -fx-background-radius: 3; -fx-opacity: 0;");

            // Remove glow border
            String style = card.getStyle();
            style = style.replace("-fx-border-color: rgba(123,97,255,0.5); -fx-border-width: 1;", "");
            card.setStyle(style);
        });

        wrapper.getChildren().addAll(card, pinContainer);
        return wrapper;
    }

    /* ───────────────────────── LOADING BUTTON ───────────────────────── */

    /**
     * Wraps a Button's action so that while the async {@code task} runs,
     * the button text is replaced by a spinning indicator + "loading..." text,
     * then restored after completion.
     * <p>
     * Call this ONCE after the button is created. It replaces the button's
     * onAction handler.
     *
     * @param button       the button
     * @param asyncAction  the work to run on a background thread (e.g. API call).
     *                     Runs on the IO pool; UI restoration is automatic.
     */
    public static void wrapWithLoadingAction(Button button, Runnable asyncAction) {
        // We store the original graphic and text so we can restore them
        button.setOnAction(event -> {
            String originalText = button.getText();
            javafx.scene.Node originalGraphic = button.getGraphic();
            boolean wasDisabled = button.isDisable();

            // Build spinner
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(16, 16);
            spinner.setPrefSize(16, 16);
            spinner.setStyle("-fx-progress-color: #F0EDEE;");

            button.setGraphic(spinner);
            button.setText("");
            button.setDisable(true);
            button.setOpacity(0.8);

            AppThreadPool.io(() -> {
                try {
                    asyncAction.run();
                } finally {
                    javafx.application.Platform.runLater(() -> {
                        button.setGraphic(originalGraphic);
                        button.setText(originalText);
                        button.setDisable(wasDisabled);
                        button.setOpacity(1.0);
                    });
                }
            });
        });
    }

    /**
     * Shows a brief loading state on the button (spinner for specified millis),
     * then runs the provided action on the FX thread. Use for actions that are
     * synchronous but you still want the visual feedback.
     */
    public static void wrapWithBriefLoading(Button button, int millis, Runnable fxAction) {
        button.setOnAction(event -> {
            String originalText = button.getText();
            javafx.scene.Node originalGraphic = button.getGraphic();

            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setMaxSize(16, 16);
            spinner.setPrefSize(16, 16);
            spinner.setStyle("-fx-progress-color: #F0EDEE;");

            button.setGraphic(spinner);
            button.setText("");
            button.setDisable(true);
            button.setOpacity(0.8);

            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(e -> {
                fxAction.run();
                button.setGraphic(originalGraphic);
                button.setText(originalText);
                button.setDisable(false);
                button.setOpacity(1.0);
            });
            pause.play();
        });
    }
}
