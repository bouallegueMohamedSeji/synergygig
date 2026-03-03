package utils;

import javafx.animation.*;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * Animated weather icons for JavaFX — a port of the React/framer-motion animated-weather-icons.
 * <p>
 * Each static factory returns a {@link StackPane} containing animated shapes at the
 * requested pixel size. Animations start automatically and loop indefinitely.
 * <p>
 * Usage:
 * <pre>{@code
 *     Node icon = AnimatedWeatherIcons.forCondition("Partly cloudy", 48);
 *     somePane.getChildren().add(icon);
 * }</pre>
 */
public final class AnimatedWeatherIcons {

    private AnimatedWeatherIcons() { /* utility */ }

    // ─────────────────────── Public factory ───────────────────────

    /**
     * Pick the best animated icon for a wttr.in condition string.
     */
    public static Node forCondition(String condition, double size) {
        if (condition == null) return sun(size);
        String c = condition.toLowerCase();
        if (c.contains("thunder") || c.contains("storm"))                          return thunder(size);
        if (c.contains("heavy rain") || c.contains("heavy shower"))                return heavyRain(size);
        if (c.contains("rain") || c.contains("drizzle") || c.contains("shower"))   return rain(size);
        if (c.contains("snow") || c.contains("sleet") || c.contains("blizzard"))   return snow(size);
        if (c.contains("fog") || c.contains("mist") || c.contains("haze"))         return fog(size);
        if (c.contains("partly cloudy"))                                            return partlyCloudy(size);
        if (c.contains("cloud") || c.contains("overcast"))                          return cloud(size);
        if (c.contains("sunny") || c.contains("clear"))                             return sun(size);
        return sun(size);
    }

    // ─────────────────────── SUN ───────────────────────

    public static StackPane sun(double size) {
        double s = size / 48.0; // scale factor from 48×48 viewBox
        Group g = new Group();

        // Rays
        Group rays = new Group();
        for (int deg = 0; deg < 360; deg += 45) {
            Line ray = new Line(24 * s, 6 * s, 24 * s, 10 * s);
            ray.setStroke(Color.web("#FBBF24"));
            ray.setStrokeWidth(2 * s);
            ray.setStrokeLineCap(StrokeLineCap.ROUND);
            ray.getTransforms().add(new Rotate(deg, 24 * s, 24 * s));
            // Pulse opacity per ray
            FadeTransition ft = new FadeTransition(Duration.seconds(2), ray);
            ft.setFromValue(0.4); ft.setToValue(1.0);
            ft.setAutoReverse(true); ft.setCycleCount(Animation.INDEFINITE);
            ft.setDelay(Duration.millis(deg * 2000.0 / 360));
            ft.play();
            rays.getChildren().add(ray);
        }
        // Rotate whole ray group
        RotateTransition raysRot = new RotateTransition(Duration.seconds(12), rays);
        raysRot.setByAngle(360); raysRot.setCycleCount(Animation.INDEFINITE);
        raysRot.setInterpolator(Interpolator.LINEAR);
        rays.setTranslateX(0); rays.setTranslateY(0);
        raysRot.play();

        // Glow circle
        Circle glow = new Circle(24 * s, 24 * s, 8 * s);
        glow.setFill(Color.web("#FBBF24", 0.2));
        glow.setStroke(Color.TRANSPARENT);
        ScaleTransition glowPulse = new ScaleTransition(Duration.seconds(3), glow);
        glowPulse.setFromX(1); glowPulse.setToX(1.15);
        glowPulse.setFromY(1); glowPulse.setToY(1.15);
        glowPulse.setAutoReverse(true); glowPulse.setCycleCount(Animation.INDEFINITE);
        glowPulse.play();

        // Core circle
        Circle core = new Circle(24 * s, 24 * s, 8 * s);
        core.setFill(Color.TRANSPARENT);
        core.setStroke(Color.web("#FBBF24"));
        core.setStrokeWidth(2 * s);

        g.getChildren().addAll(rays, glow, core);
        return wrap(g, size);
    }

    // ─────────────────────── MOON ───────────────────────

    public static StackPane moon(double size) {
        double s = size / 48.0;
        Group g = new Group();

        // Moon crescent
        SVGPath moonFill = svgPath("M28 8a14 14 0 100 28 10 10 0 01 0-28z", s);
        moonFill.setFill(Color.web("#A78BFA", 0.15));
        moonFill.setStroke(Color.TRANSPARENT);

        SVGPath moonStroke = svgPath("M28 8a14 14 0 100 28 10 10 0 01 0-28z", s);
        moonStroke.setFill(Color.TRANSPARENT);
        moonStroke.setStroke(Color.web("#A78BFA"));
        moonStroke.setStrokeWidth(2 * s);
        moonStroke.setStrokeLineCap(StrokeLineCap.ROUND);

        g.getChildren().addAll(moonFill, moonStroke);

        // Twinkling stars
        double[][] stars = {{34, 10, 0}, {38, 18, 500}, {30, 6, 1000}, {40, 12, 1500}};
        for (double[] st : stars) {
            Circle star = new Circle(st[0] * s, st[1] * s, 1 * s);
            star.setFill(Color.web("#A78BFA"));
            FadeTransition ft = new FadeTransition(Duration.seconds(2), star);
            ft.setFromValue(0.2); ft.setToValue(0.9);
            ft.setAutoReverse(true); ft.setCycleCount(Animation.INDEFINITE);
            ft.setDelay(Duration.millis(st[2]));
            ft.play();
            ScaleTransition sc = new ScaleTransition(Duration.seconds(2), star);
            sc.setFromX(0.8); sc.setToX(1.2);
            sc.setFromY(0.8); sc.setToY(1.2);
            sc.setAutoReverse(true); sc.setCycleCount(Animation.INDEFINITE);
            sc.setDelay(Duration.millis(st[2]));
            sc.play();
            g.getChildren().add(star);
        }

        return wrap(g, size);
    }

    // ─────────────────────── CLOUD ───────────────────────

    public static StackPane cloud(double size) {
        double s = size / 48.0;
        Group g = new Group();

        SVGPath fill = svgPath("M36 30H14a8 8 0 01-.5-16A10 10 0 0134 16a7 7 0 012 14z", s);
        fill.setFill(Color.web("#94A3B8", 0.12));
        fill.setStroke(Color.TRANSPARENT);

        SVGPath stroke = svgPath("M36 30H14a8 8 0 01-.5-16A10 10 0 0134 16a7 7 0 012 14z", s);
        stroke.setFill(Color.TRANSPARENT);
        stroke.setStroke(Color.web("#94A3B8"));
        stroke.setStrokeWidth(2 * s);
        stroke.setStrokeLineCap(StrokeLineCap.ROUND);
        stroke.setStrokeLineJoin(StrokeLineJoin.ROUND);

        Group cloudGrp = new Group(fill, stroke);
        TranslateTransition drift = new TranslateTransition(Duration.seconds(6), cloudGrp);
        drift.setFromX(-2 * s); drift.setToX(2 * s);
        drift.setAutoReverse(true); drift.setCycleCount(Animation.INDEFINITE);
        drift.setInterpolator(Interpolator.EASE_BOTH);
        drift.play();

        g.getChildren().add(cloudGrp);
        return wrap(g, size);
    }

    // ─────────────────────── RAIN ───────────────────────

    public static StackPane rain(double size) {
        double s = size / 48.0;
        Group g = new Group();

        // Cloud
        addCloudShape(g, "M36 22H14a7 7 0 01-.5-14A9 9 0 0134 10a6 6 0 012 12z",
                Color.web("#60A5FA"), s);

        // Raindrops
        double[] dropX = {16, 22, 28, 34};
        double[] delays = {0, 0.3, 0.6, 0.15};
        for (int i = 0; i < dropX.length; i++) {
            Line drop = new Line(dropX[i] * s, 26 * s, dropX[i] * s, 30 * s);
            drop.setStroke(Color.web("#60A5FA"));
            drop.setStrokeWidth(2 * s);
            drop.setStrokeLineCap(StrokeLineCap.ROUND);
            animateRaindrop(drop, 12 * s, 800, delays[i] * 1000);
            g.getChildren().add(drop);
        }

        return wrap(g, size);
    }

    // ─────────────────────── HEAVY RAIN ───────────────────────

    public static StackPane heavyRain(double size) {
        double s = size / 48.0;
        Group g = new Group();

        addCloudShape(g, "M36 20H14a7 7 0 01-.5-14A9 9 0 0134 8a6 6 0 012 12z",
                Color.web("#3B82F6"), s);

        double[] dropX = {14, 19, 24, 29, 34, 37};
        double[] delays = {0, 150, 300, 100, 400, 250};
        for (int i = 0; i < dropX.length; i++) {
            Line drop = new Line(dropX[i] * s, 24 * s, (dropX[i] - 2) * s, 30 * s);
            drop.setStroke(Color.web("#3B82F6"));
            drop.setStrokeWidth(1.5 * s);
            drop.setStrokeLineCap(StrokeLineCap.ROUND);
            animateRaindrop(drop, 16 * s, 600, delays[i]);
            g.getChildren().add(drop);
        }

        return wrap(g, size);
    }

    // ─────────────────────── SNOW ───────────────────────

    public static StackPane snow(double size) {
        double s = size / 48.0;
        Group g = new Group();

        addCloudShape(g, "M36 22H14a7 7 0 01-.5-14A9 9 0 0134 10a6 6 0 012 12z",
                Color.web("#CBD5E1"), s);

        double[] flakeX = {16, 22, 28, 34, 19, 31};
        double[] delays = {0, 500, 200, 700, 1000, 400};
        for (int i = 0; i < flakeX.length; i++) {
            Circle flake = new Circle(flakeX[i] * s, 26 * s, 1.5 * s);
            flake.setFill(Color.web("#CBD5E1"));
            animateSnowflake(flake, 14 * s, i % 2 == 0 ? 3 * s : -3 * s, 2000, delays[i]);
            g.getChildren().add(flake);
        }

        return wrap(g, size);
    }

    // ─────────────────────── THUNDER ───────────────────────

    public static StackPane thunder(double size) {
        double s = size / 48.0;
        Group g = new Group();

        addCloudShape(g, "M36 20H14a7 7 0 01-.5-14A9 9 0 0134 8a6 6 0 012 12z",
                Color.web("#94A3B8"), s);

        // Lightning bolt
        SVGPath bolt = svgPath("M26 20l-3 8h6l-3 10", s);
        bolt.setFill(Color.TRANSPARENT);
        bolt.setStroke(Color.web("#F59E0B"));
        bolt.setStrokeWidth(2.5 * s);
        bolt.setStrokeLineCap(StrokeLineCap.ROUND);
        bolt.setStrokeLineJoin(StrokeLineJoin.ROUND);

        // Lightning flash animation
        Timeline flash = new Timeline(
                new KeyFrame(Duration.ZERO,               new KeyValue(bolt.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(300),         new KeyValue(bolt.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(600),         new KeyValue(bolt.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(900),         new KeyValue(bolt.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(1800),        new KeyValue(bolt.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(2100),        new KeyValue(bolt.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(2400),        new KeyValue(bolt.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(3000),        new KeyValue(bolt.opacityProperty(), 0))
        );
        flash.setCycleCount(Animation.INDEFINITE);
        flash.play();

        // Glow behind bolt
        SVGPath glow = svgPath("M26 20l-3 8h6l-3 10", s);
        glow.setFill(Color.web("#F59E0B", 0.3));
        glow.setStroke(Color.TRANSPARENT);
        Timeline glowFlash = new Timeline(
                new KeyFrame(Duration.ZERO,               new KeyValue(glow.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(300),         new KeyValue(glow.opacityProperty(), 0.3)),
                new KeyFrame(Duration.millis(600),         new KeyValue(glow.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(1800),        new KeyValue(glow.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(2100),        new KeyValue(glow.opacityProperty(), 0.2)),
                new KeyFrame(Duration.millis(2400),        new KeyValue(glow.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(3000),        new KeyValue(glow.opacityProperty(), 0))
        );
        glowFlash.setCycleCount(Animation.INDEFINITE);
        glowFlash.play();

        g.getChildren().addAll(glow, bolt);
        return wrap(g, size);
    }

    // ─────────────────────── WIND ───────────────────────

    public static StackPane wind(double size) {
        double s = size / 48.0;
        Group g = new Group();

        SVGPath line1 = svgPath("M6 18h26a4 4 0 000-8", s);
        line1.setFill(Color.TRANSPARENT);
        line1.setStroke(Color.web("#94A3B8"));
        line1.setStrokeWidth(2 * s);
        line1.setStrokeLineCap(StrokeLineCap.ROUND);
        animateWindLine(line1, 1500, 0);

        SVGPath line2 = svgPath("M6 24h32a3 3 0 010 6", s);
        line2.setFill(Color.TRANSPARENT);
        line2.setStroke(Color.web("#94A3B8"));
        line2.setStrokeWidth(2 * s);
        line2.setStrokeLineCap(StrokeLineCap.ROUND);
        animateWindLine(line2, 2000, 300);

        SVGPath line3 = svgPath("M10 30h18a3 3 0 000-0", s);
        line3.setFill(Color.TRANSPARENT);
        line3.setStroke(Color.web("#94A3B8", 0.5));
        line3.setStrokeWidth(2 * s);
        line3.setStrokeLineCap(StrokeLineCap.ROUND);
        animateWindLine(line3, 1800, 600);

        g.getChildren().addAll(line1, line2, line3);
        return wrap(g, size);
    }

    // ─────────────────────── FOG ───────────────────────

    public static StackPane fog(double size) {
        double s = size / 48.0;
        Group g = new Group();

        double[][] lines = {{16, 28, 0}, {22, 32, 500}, {28, 24, 1000}, {34, 30, 1500}};
        for (double[] l : lines) {
            double y = l[0], w = l[1], delay = l[2];
            Line line = new Line((24 - w / 2) * s, y * s, (24 + w / 2) * s, y * s);
            line.setStroke(Color.web("#94A3B8"));
            line.setStrokeWidth(2.5 * s);
            line.setStrokeLineCap(StrokeLineCap.ROUND);

            FadeTransition fade = new FadeTransition(Duration.seconds(3), line);
            fade.setFromValue(0.2); fade.setToValue(0.6);
            fade.setAutoReverse(true); fade.setCycleCount(Animation.INDEFINITE);
            fade.setDelay(Duration.millis(delay));
            fade.play();

            TranslateTransition drift = new TranslateTransition(Duration.seconds(3), line);
            drift.setFromX(0); drift.setToX(3 * s);
            drift.setAutoReverse(true); drift.setCycleCount(Animation.INDEFINITE);
            drift.setDelay(Duration.millis(delay));
            drift.setInterpolator(Interpolator.EASE_BOTH);
            drift.play();

            g.getChildren().add(line);
        }

        return wrap(g, size);
    }

    // ─────────────────────── PARTLY CLOUDY ───────────────────────

    public static StackPane partlyCloudy(double size) {
        double s = size / 48.0;
        Group g = new Group();

        // Small sun (upper left)
        Group sunRays = new Group();
        for (int deg = 0; deg < 360; deg += 60) {
            Line ray = new Line(16 * s, 6 * s, 16 * s, 9 * s);
            ray.setStroke(Color.web("#FBBF24"));
            ray.setStrokeWidth(1.5 * s);
            ray.setStrokeLineCap(StrokeLineCap.ROUND);
            ray.getTransforms().add(new Rotate(deg, 16 * s, 16 * s));
            sunRays.getChildren().add(ray);
        }
        RotateTransition sunRot = new RotateTransition(Duration.seconds(20), sunRays);
        sunRot.setByAngle(360); sunRot.setCycleCount(Animation.INDEFINITE);
        sunRot.setInterpolator(Interpolator.LINEAR);
        sunRot.play();

        Circle sunCore = new Circle(16 * s, 16 * s, 6 * s);
        sunCore.setFill(Color.web("#FBBF24", 0.15));
        sunCore.setStroke(Color.web("#FBBF24"));
        sunCore.setStrokeWidth(1.5 * s);

        // Cloud (overlapping sun)
        SVGPath cloudFill = svgPath("M38 34H18a7 7 0 01-.5-14A9 9 0 0136 22a6 6 0 012 12z", s);
        cloudFill.setFill(Color.web("#94A3B8", 0.12));
        cloudFill.setStroke(Color.TRANSPARENT);

        SVGPath cloudStroke = svgPath("M38 34H18a7 7 0 01-.5-14A9 9 0 0136 22a6 6 0 012 12z", s);
        cloudStroke.setFill(Color.TRANSPARENT);
        cloudStroke.setStroke(Color.web("#94A3B8"));
        cloudStroke.setStrokeWidth(2 * s);
        cloudStroke.setStrokeLineCap(StrokeLineCap.ROUND);

        Group cloudGrp = new Group(cloudFill, cloudStroke);
        TranslateTransition drift = new TranslateTransition(Duration.seconds(5), cloudGrp);
        drift.setFromX(-1.5 * s); drift.setToX(1.5 * s);
        drift.setAutoReverse(true); drift.setCycleCount(Animation.INDEFINITE);
        drift.setInterpolator(Interpolator.EASE_BOTH);
        drift.play();

        g.getChildren().addAll(sunRays, sunCore, cloudGrp);
        return wrap(g, size);
    }

    // ─────────────────────── SUNRISE ───────────────────────

    public static StackPane sunrise(double size) {
        double s = size / 48.0;
        Group g = new Group();

        // Horizon line
        Line horizon = new Line(6 * s, 34 * s, 42 * s, 34 * s);
        horizon.setStroke(Color.web("#94A3B8"));
        horizon.setStrokeWidth(2 * s);
        horizon.setStrokeLineCap(StrokeLineCap.ROUND);

        // Half sun + rays rising
        Group sunGrp = new Group();
        SVGPath arc = svgPath("M12 34a12 12 0 0124 0", s);
        arc.setFill(Color.web("#FBBF24", 0.15));
        arc.setStroke(Color.web("#FBBF24"));
        arc.setStrokeWidth(2 * s);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);

        for (int deg : new int[]{0, 30, 60, 90, 120, 150, 180}) {
            Line ray = new Line(24 * s, 14 * s, 24 * s, 17 * s);
            ray.setStroke(Color.web("#FBBF24"));
            ray.setStrokeWidth(1.5 * s);
            ray.setStrokeLineCap(StrokeLineCap.ROUND);
            ray.getTransforms().add(new Rotate(deg - 90, 24 * s, 34 * s));
            sunGrp.getChildren().add(ray);
        }
        sunGrp.getChildren().add(arc);

        TranslateTransition rise = new TranslateTransition(Duration.seconds(3), sunGrp);
        rise.setFromY(4 * s); rise.setToY(0);
        rise.setAutoReverse(true); rise.setCycleCount(Animation.INDEFINITE);
        rise.setInterpolator(Interpolator.EASE_BOTH);
        rise.play();

        // Up arrow
        Line arrow = new Line(24 * s, 42 * s, 24 * s, 36 * s);
        arrow.setStroke(Color.web("#FBBF24"));
        arrow.setStrokeWidth(2 * s);
        arrow.setStrokeLineCap(StrokeLineCap.ROUND);

        Polyline chevron = new Polyline(20 * s, 38 * s, 24 * s, 34 * s, 28 * s, 38 * s);
        chevron.setStroke(Color.web("#FBBF24"));
        chevron.setStrokeWidth(2 * s);
        chevron.setStrokeLineCap(StrokeLineCap.ROUND);
        chevron.setStrokeLineJoin(StrokeLineJoin.ROUND);
        chevron.setFill(Color.TRANSPARENT);

        Group arrowGrp = new Group(arrow, chevron);
        TranslateTransition arrowBounce = new TranslateTransition(Duration.seconds(2), arrowGrp);
        arrowBounce.setFromY(2 * s); arrowBounce.setToY(-2 * s);
        arrowBounce.setAutoReverse(true); arrowBounce.setCycleCount(Animation.INDEFINITE);
        arrowBounce.setInterpolator(Interpolator.EASE_BOTH);
        arrowBounce.play();

        g.getChildren().addAll(horizon, sunGrp, arrowGrp);
        return wrap(g, size);
    }

    // ─────────────────────── Helpers ───────────────────────

    /** Wrap shapes into a fixed-size StackPane. */
    private static StackPane wrap(Group g, double size) {
        StackPane pane = new StackPane(g);
        pane.setPrefSize(size, size);
        pane.setMinSize(size, size);
        pane.setMaxSize(size, size);
        pane.setMouseTransparent(true);
        return pane;
    }

    /** Create a scaled SVGPath node. */
    private static SVGPath svgPath(String content, double scale) {
        SVGPath path = new SVGPath();
        path.setContent(content);
        path.setScaleX(scale);
        path.setScaleY(scale);
        return path;
    }

    /** Add a cloud shape (fill + stroke) to a group. */
    private static void addCloudShape(Group g, String pathData, Color color, double s) {
        SVGPath fill = svgPath(pathData, s);
        fill.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.1));
        fill.setStroke(Color.TRANSPARENT);

        SVGPath stroke = svgPath(pathData, s);
        stroke.setFill(Color.TRANSPARENT);
        stroke.setStroke(color);
        stroke.setStrokeWidth(2 * s);
        stroke.setStrokeLineCap(StrokeLineCap.ROUND);

        g.getChildren().addAll(fill, stroke);
    }

    /** Raindrop fall animation: translate down + fade out, then reset. */
    private static void animateRaindrop(Node drop, double distance, double durationMs, double delayMs) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), drop);
        tt.setFromY(0); tt.setToY(distance);
        tt.setInterpolator(Interpolator.EASE_IN);

        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), drop);
        ft.setFromValue(0.8); ft.setToValue(0);

        ParallelTransition pt = new ParallelTransition(tt, ft);
        pt.setDelay(Duration.millis(delayMs));
        pt.setCycleCount(Animation.INDEFINITE);
        pt.play();
    }

    /** Snowflake drift animation: fall + horizontal wobble + fade. */
    private static void animateSnowflake(Node flake, double distance, double wobble,
                                          double durationMs, double delayMs) {
        TranslateTransition fall = new TranslateTransition(Duration.millis(durationMs), flake);
        fall.setFromY(0); fall.setToY(distance);
        fall.setInterpolator(Interpolator.EASE_IN);

        TranslateTransition sway = new TranslateTransition(Duration.millis(durationMs), flake);
        sway.setFromX(0); sway.setToX(wobble);
        sway.setAutoReverse(true);

        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), flake);
        fade.setFromValue(0.8); fade.setToValue(0);

        // Can't parallel two TranslateTransitions on same axis, so combine fall+fade
        ParallelTransition pt = new ParallelTransition(fall, fade);
        pt.setDelay(Duration.millis(delayMs));
        pt.setCycleCount(Animation.INDEFINITE);
        pt.play();
    }

    /** Wind line fade-in/out animation. */
    private static void animateWindLine(Node line, double durationMs, double delayMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), line);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setAutoReverse(true); ft.setCycleCount(Animation.INDEFINITE);
        ft.setDelay(Duration.millis(delayMs));
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }
}
