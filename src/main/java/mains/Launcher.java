package mains;

/**
 * Non-JavaFX launcher class.
 * Required for fat JAR packaging — JavaFX Application classes cannot be
 * launched directly from a shaded JAR without the module system.
 */
public class Launcher {
    public static void main(String[] args) {
        MainFX.main(args);
    }
}
