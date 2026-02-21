package utils;

import java.io.*;
import java.net.URI;
import java.util.Properties;

/**
 * Loads application settings from {@code config.properties}.
 * Falls back to localhost defaults (XAMPP / local Python) if the file is missing.
 */
public class AppConfig {

    private static final Properties props = new Properties();
    private static boolean loaded = false;

    static {
        load();
    }

    private static void load() {
        // Try multiple locations for config.properties:
        // 1. Current working directory (project root when running from IDE)
        // 2. Next to the JAR/exe (jpackage app-image layout)
        // 3. Classpath (bundled inside the JAR)

        File[] candidates = {
            new File(System.getProperty("user.dir"), "config.properties"),
            resolveAppDir("config.properties"),
        };

        for (File file : candidates) {
            if (file != null && file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    props.load(fis);
                    loaded = true;
                    System.out.println("✅ Config loaded from " + file.getAbsolutePath());
                    return;
                } catch (IOException e) {
                    System.err.println("⚠ Failed to read " + file + ": " + e.getMessage());
                }
            }
        }

        // Fallback: classpath
        try (InputStream is = AppConfig.class.getResourceAsStream("/config.properties")) {
            if (is != null) {
                props.load(is);
                loaded = true;
                System.out.println("✅ Config loaded from classpath");
                return;
            }
        } catch (IOException e) {
            // ignore
        }

        if (!loaded) {
            System.out.println("ℹ No config.properties found — using localhost defaults (XAMPP mode)");
        }
    }

    /** Resolve a file next to the running JAR or exe (handles jpackage layout). */
    private static File resolveAppDir(String filename) {
        try {
            // For jpackage: the exe is in <app>/SynergyGig.exe, config beside it
            String appDir = System.getProperty("jpackage.app-path");
            if (appDir != null) {
                File dir = new File(appDir).getParentFile();
                if (dir != null) return new File(dir, filename);
            }
            // Fallback: resolve from the JAR/class location
            File jarFile = new File(AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = jarFile.isDirectory() ? jarFile : jarFile.getParentFile();
            return new File(dir, filename);
        } catch (Exception e) {
            return null;
        }
    }

    /** "local", "remote", or "api" */
    public static String getMode() {
        return props.getProperty("app.mode", "local");
    }

    public static boolean isRemote() {
        return "remote".equalsIgnoreCase(getMode()) || "api".equalsIgnoreCase(getMode());
    }

    /** true when the app should use REST API instead of direct JDBC */
    public static boolean isApiMode() {
        return "api".equalsIgnoreCase(getMode());
    }

    public static String getDbUrl() {
        return props.getProperty("db.url", "jdbc:mysql://localhost:3306/finale_synergygig");
    }

    public static String getDbUser() {
        return props.getProperty("db.user", "root");
    }

    public static String getDbPassword() {
        return props.getProperty("db.password", "");
    }

    public static String getAiBaseUrl() {
        return props.getProperty("ai.base_url", "http://localhost:5000");
    }

    public static String getServerHost() {
        return props.getProperty("server.host", "64.23.239.27");
    }

    public static String getServerSshUser() {
        return props.getProperty("server.ssh_user", "seji");
    }

    public static String getRestBaseUrl() {
        return props.getProperty("rest.base_url", "https://rest.benzaitsue.work.gd/api");
    }

    /** Generic property getter. */
    public static String get(String key) {
        return props.getProperty(key);
    }

    public static String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }
}
