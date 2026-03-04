package utils;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Generates simple math-based CAPTCHA images.
 * Returns the expected answer so the controller can verify user input.
 */
public class CaptchaGenerator {

    private static final Random RNG = new Random();
    private static final int WIDTH = 200;
    private static final int HEIGHT = 60;

    private String answer;

    /** Generate a new CAPTCHA image and store the answer. */
    public Image generate() {
        // Random math problem
        int a = RNG.nextInt(20) + 1;
        int b = RNG.nextInt(20) + 1;
        int op = RNG.nextInt(3); // 0=add, 1=subtract, 2=multiply
        String text;
        int result;

        switch (op) {
            case 0:
                text = a + " + " + b + " = ?";
                result = a + b;
                break;
            case 1:
                // Ensure positive result
                if (a < b) { int tmp = a; a = b; b = tmp; }
                text = a + " - " + b + " = ?";
                result = a - b;
                break;
            default:
                a = RNG.nextInt(9) + 1;
                b = RNG.nextInt(9) + 1;
                text = a + " × " + b + " = ?";
                result = a * b;
                break;
        }

        answer = String.valueOf(result);

        // Render to BufferedImage
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Dark background
        g2d.setColor(new Color(18, 17, 22));
        g2d.fillRoundRect(0, 0, WIDTH, HEIGHT, 12, 12);

        // Noise lines
        for (int i = 0; i < 6; i++) {
            g2d.setColor(new Color(
                    40 + RNG.nextInt(60),
                    40 + RNG.nextInt(60),
                    60 + RNG.nextInt(80),
                    80 + RNG.nextInt(60)));
            g2d.setStroke(new BasicStroke(1 + RNG.nextFloat()));
            g2d.drawLine(RNG.nextInt(WIDTH), RNG.nextInt(HEIGHT),
                         RNG.nextInt(WIDTH), RNG.nextInt(HEIGHT));
        }

        // Noise dots
        for (int i = 0; i < 40; i++) {
            g2d.setColor(new Color(
                    80 + RNG.nextInt(100),
                    80 + RNG.nextInt(100),
                    120 + RNG.nextInt(80),
                    60 + RNG.nextInt(80)));
            int dotSize = 1 + RNG.nextInt(3);
            g2d.fillOval(RNG.nextInt(WIDTH), RNG.nextInt(HEIGHT), dotSize, dotSize);
        }

        // Draw each character with slight random rotation and offset
        g2d.setFont(new Font("Consolas", Font.BOLD, 26));
        FontMetrics fm = g2d.getFontMetrics();
        int totalWidth = fm.stringWidth(text);
        int startX = (WIDTH - totalWidth) / 2;
        int baseY = (HEIGHT + fm.getAscent() - fm.getDescent()) / 2;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            AffineTransform orig = g2d.getTransform();

            double angle = (RNG.nextDouble() - 0.5) * 0.3; // ±0.15 rad
            int offsetY = RNG.nextInt(7) - 3;

            g2d.translate(startX, baseY + offsetY);
            g2d.rotate(angle);

            // Teal/cyan color to match app theme
            g2d.setColor(new Color(
                    120 + RNG.nextInt(60),
                    200 + RNG.nextInt(55),
                    220 + RNG.nextInt(35)));
            g2d.drawString(String.valueOf(c), 0, 0);

            g2d.setTransform(orig);
            startX += fm.charWidth(c);
        }

        g2d.dispose();

        return SwingFXUtils.toFXImage(img, null);
    }

    /** Get the expected answer string for the current CAPTCHA. */
    public String getAnswer() {
        return answer;
    }
}
