package utils;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure black animated background with:
 *  - Shooting stars streaking across the sky
 *  - Tiny twinkling star dots
 *  - A bright white spotlight glow centered on the form area
 */
public class SparkleCanvas extends Pane {

    private final Canvas bgCanvas;       // static: black bg + spotlight
    private final Canvas fxCanvas;       // animated: stars + shooting stars
    private final List<Star> stars = new ArrayList<>();
    private final List<ShootingStar> shootingStars = new ArrayList<>();
    private final Random rng = new Random();
    private AnimationTimer timer;
    private long lastShootingSpawn = 0;

    private static final int STAR_COUNT = 110;
    private static final int MAX_SHOOTING = 3;

    public SparkleCanvas() {
        bgCanvas = new Canvas();
        fxCanvas = new Canvas();
        getChildren().addAll(bgCanvas, fxCanvas);

        bgCanvas.widthProperty().bind(widthProperty());
        bgCanvas.heightProperty().bind(heightProperty());
        fxCanvas.widthProperty().bind(widthProperty());
        fxCanvas.heightProperty().bind(heightProperty());

        widthProperty().addListener((o, a, b) -> { initStars(); drawBackground(); });
        heightProperty().addListener((o, a, b) -> { initStars(); drawBackground(); });

        startAnimation();
    }

    /* ── Static layer: pure black + white spotlight ────────── */
    private void drawBackground() {
        double w = bgCanvas.getWidth(), h = bgCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext g = bgCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // Pure black
        g.setFill(Color.BLACK);
        g.fillRect(0, 0, w, h);

        double cx = w / 2, cy = h * 0.46;

        // Outer soft white glow
        g.save();
        g.setGlobalAlpha(0.045);
        RadialGradient outer = new RadialGradient(0, 0, cx, cy, w * 0.42,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE),
                new Stop(0.5, Color.web("#ffffff", 0.3)),
                new Stop(1, Color.TRANSPARENT));
        g.setFill(outer);
        g.fillOval(cx - w * 0.5, cy - h * 0.35, w, h * 0.7);
        g.restore();

        // Mid white glow
        g.save();
        g.setGlobalAlpha(0.06);
        RadialGradient mid = new RadialGradient(0, 0, cx, cy, w * 0.28,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.WHITE),
                new Stop(0.6, Color.web("#ffffff", 0.15)),
                new Stop(1, Color.TRANSPARENT));
        g.setFill(mid);
        g.fillOval(cx - w * 0.35, cy - h * 0.22, w * 0.7, h * 0.44);
        g.restore();

        // Bright core
        g.save();
        g.setGlobalAlpha(0.07);
        RadialGradient core = new RadialGradient(0, 0, cx, cy, w * 0.14,
                false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#ffffff", 0.9)),
                new Stop(0.4, Color.web("#e0e0e0", 0.3)),
                new Stop(1, Color.TRANSPARENT));
        g.setFill(core);
        g.fillOval(cx - w * 0.2, cy - h * 0.12, w * 0.4, h * 0.24);
        g.restore();
    }

    /* ── Stars init ────────────────────────────────────────── */
    private void initStars() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (stars.size() != STAR_COUNT) {
            stars.clear();
            for (int i = 0; i < STAR_COUNT; i++)
                stars.add(new Star(w, h, rng));
        }
    }

    /* ── Animation ─────────────────────────────────────────── */
    private void startAnimation() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) { drawFrame(now); }
        };
        timer.start();
    }

    public void stopAnimation() {
        if (timer != null) timer.stop();
    }

    private void drawFrame(long now) {
        double w = fxCanvas.getWidth(), h = fxCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext g = fxCanvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        // ── Twinkling stars ──
        for (Star s : stars) {
            s.update(rng);
            g.save();
            g.setGlobalAlpha(s.opacity);
            g.setFill(Color.WHITE);
            g.fillOval(s.x - s.size / 2, s.y - s.size / 2, s.size, s.size);
            // tiny halo on bigger stars
            if (s.size > 0.9) {
                g.setGlobalAlpha(s.opacity * 0.15);
                double hs = s.size * 3.5;
                g.fillOval(s.x - hs / 2, s.y - hs / 2, hs, hs);
            }
            g.restore();
        }

        // ── Shooting stars ──
        // Spawn new ones periodically
        long nanos200ms = 200_000_000L;
        if (now - lastShootingSpawn > 1_800_000_000L + rng.nextInt(3) * 1_000_000_000L) {
            if (shootingStars.size() < MAX_SHOOTING) {
                shootingStars.add(new ShootingStar(w, h, rng));
                lastShootingSpawn = now;
            }
        }

        var it = shootingStars.iterator();
        while (it.hasNext()) {
            ShootingStar ss = it.next();
            ss.update();

            if (ss.isDead(w, h)) {
                it.remove();
                continue;
            }

            // Draw the tail as a line with gradient opacity
            g.save();
            double tailLen = ss.tailLength;
            double dx = -Math.cos(ss.angle) * tailLen;
            double dy = -Math.sin(ss.angle) * tailLen;

            // Main streak
            g.setGlobalAlpha(ss.opacity * 0.9);
            g.setStroke(Color.WHITE);
            g.setLineWidth(ss.thickness);
            g.strokeLine(ss.x, ss.y, ss.x + dx, ss.y + dy);

            // Bright head glow
            g.setGlobalAlpha(ss.opacity);
            g.setFill(Color.WHITE);
            double headSize = ss.thickness * 2;
            g.fillOval(ss.x - headSize / 2, ss.y - headSize / 2, headSize, headSize);

            // Soft glow around head
            g.setGlobalAlpha(ss.opacity * 0.2);
            double glowSize = ss.thickness * 6;
            g.fillOval(ss.x - glowSize / 2, ss.y - glowSize / 2, glowSize, glowSize);

            g.restore();
        }
    }

    /* ── Star (twinkling dot) ─────────────────────────────── */
    private static class Star {
        double x, y, size, opacity, twinkleSpeed, twinklePhase, maxOpacity;

        Star(double w, double h, Random r) {
            x = r.nextDouble() * w;
            y = r.nextDouble() * h;
            size = 0.3 + r.nextDouble() * 1.4;
            maxOpacity = 0.15 + r.nextDouble() * 0.6;
            opacity = r.nextDouble() * maxOpacity;
            twinkleSpeed = 0.004 + r.nextDouble() * 0.012;
            twinklePhase = r.nextDouble() * Math.PI * 2;
        }

        void update(Random r) {
            twinklePhase += twinkleSpeed;
            opacity = maxOpacity * (0.4 + 0.6 * (0.5 + 0.5 * Math.sin(twinklePhase)));
        }
    }

    /* ── Shooting Star ────────────────────────────────────── */
    private static class ShootingStar {
        double x, y, speed, angle, opacity, tailLength, thickness;
        double life, maxLife;

        ShootingStar(double w, double h, Random r) {
            // Start from top half edges
            boolean fromLeft = r.nextBoolean();
            if (fromLeft) {
                x = -10;
                y = r.nextDouble() * h * 0.4;
            } else {
                x = w + 10;
                y = r.nextDouble() * h * 0.4;
            }
            // Angle: roughly diagonal
            angle = fromLeft
                    ? (0.3 + r.nextDouble() * 0.5)      // ~17°–46° heading right-down
                    : (Math.PI - 0.3 - r.nextDouble() * 0.5); // heading left-down
            speed = 4 + r.nextDouble() * 6;
            tailLength = 40 + r.nextDouble() * 80;
            thickness = 0.8 + r.nextDouble() * 1.2;
            maxLife = 80 + r.nextInt(60);
            life = 0;
            opacity = 0;
        }

        void update() {
            x += Math.cos(angle) * speed;
            y += Math.sin(angle) * speed;
            life++;
            double t = life / maxLife;
            // Fade in quickly, linger, fade out
            if (t < 0.15) opacity = t / 0.15;
            else if (t > 0.7) opacity = 1.0 - (t - 0.7) / 0.3;
            else opacity = 1.0;
        }

        boolean isDead(double w, double h) {
            return life >= maxLife || x < -200 || x > w + 200 || y < -100 || y > h + 100;
        }
    }
}
