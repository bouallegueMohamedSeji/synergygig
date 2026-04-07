package utils;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/**
 * Aceternity-style mouse-tracking spotlight border.
 *
 * How it works:
 *  1. A canvas fills the wrapper and draws a radial white gradient at the mouse
 *     position — this is the "glow".
 *  2. A second opaque dark rounded-rect is drawn *inside* with a 1.5 px inset,
 *     punching out the interior so only the border edge shows the glow.
 *  3. The result: a bright white border glow that follows your cursor.
 */
public class SpotlightBorder {

    private static final double BORDER = 1.5;
    private static final double RADIUS = 14;
    private static final double INNER_R = RADIUS - BORDER;

    public static void install(StackPane wrapper) {
        Canvas canvas = new Canvas();
        canvas.setMouseTransparent(true);
        wrapper.getChildren().add(0, canvas);

        canvas.widthProperty().bind(wrapper.widthProperty());
        canvas.heightProperty().bind(wrapper.heightProperty());

        // Clip to rounded corners
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(wrapper.widthProperty());
        clip.heightProperty().bind(wrapper.heightProperty());
        clip.setArcWidth(RADIUS * 2);
        clip.setArcHeight(RADIUS * 2);
        wrapper.setClip(clip);

        final double[] mouse = {-1, -1};
        final double[] smooth = {-1, -1};
        final boolean[] inside = {false};

        drawIdle(canvas);

        // Use addEventFilter so we get events even when children consume them
        wrapper.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
            mouse[0] = e.getX(); mouse[1] = e.getY(); inside[0] = true;
        });
        wrapper.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
            mouse[0] = e.getX(); mouse[1] = e.getY();
        });
        wrapper.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_ENTERED, e -> {
            mouse[0] = e.getX(); mouse[1] = e.getY();
            smooth[0] = e.getX(); smooth[1] = e.getY();
            inside[0] = true;
        });
        wrapper.setOnMouseExited(e -> {
            inside[0] = false;
            drawIdle(canvas);
        });

        AnimationTimer t = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!inside[0]) return;
                if (smooth[0] < 0) { smooth[0] = mouse[0]; smooth[1] = mouse[1]; }
                else {
                    smooth[0] += (mouse[0] - smooth[0]) * 0.3;
                    smooth[1] += (mouse[1] - smooth[1]) * 0.3;
                }
                drawGlow(canvas, smooth[0], smooth[1]);
            }
        };
        t.start();
        wrapper.sceneProperty().addListener((o, a, n) -> { if (n == null) t.stop(); });
    }

    /* ── Idle: subtle dark border ──────────────────────────── */
    private static void drawIdle(Canvas c) {
        double w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = c.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // Outer border fill
        g.setFill(Color.web("#222228"));
        g.fillRoundRect(0, 0, w, h, RADIUS * 2, RADIUS * 2);

        // Inner opaque fill — creates thin border
        g.setFill(Color.web("#0a0a0c"));
        g.fillRoundRect(BORDER, BORDER, w - BORDER * 2, h - BORDER * 2,
                INNER_R * 2, INNER_R * 2);
    }

    /* ── Active: radial white glow at cursor ───────────────── */
    private static void drawGlow(Canvas c, double mx, double my) {
        double w = c.getWidth(), h = c.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = c.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // 1. Dark base border
        g.setFill(Color.web("#222228"));
        g.fillRoundRect(0, 0, w, h, RADIUS * 2, RADIUS * 2);

        // 2. Bright radial glow at mouse — tight radius so glow is concentrated
        double glowR = 150;   // fixed pixel radius for a tight, punchy glow
        RadialGradient glow = new RadialGradient(
                0, 0, mx, my, glowR,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.web("#ffffff", 0.85)),
                new Stop(0.10, Color.web("#ffffff", 0.60)),
                new Stop(0.25, Color.web("#ffffff", 0.30)),
                new Stop(0.50, Color.web("#ffffff", 0.08)),
                new Stop(0.75, Color.web("#ffffff", 0.02)),
                new Stop(1.00, Color.TRANSPARENT));
        g.setFill(glow);
        g.fillRoundRect(0, 0, w, h, RADIUS * 2, RADIUS * 2);

        // 3. Inner opaque dark fill — punches out interior, leaving only the
        //    1.5 px border edge glowing
        g.setFill(Color.web("#0a0a0c"));
        g.fillRoundRect(BORDER, BORDER, w - BORDER * 2, h - BORDER * 2,
                INNER_R * 2, INNER_R * 2);
    }
}
