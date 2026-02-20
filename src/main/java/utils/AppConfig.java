package utils;

import java.io.*;
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
        // Try project root first, then classpath
        File file = new File(System.getProperty("user.dir"), "config.properties");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
                loaded = true;
                System.out.println("✅ Config loaded from " + file.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("⚠ Failed to read config.properties: " + e.getMessage());
            }
        } else {
            // Try classpath
            try (InputStream is = AppConfig.class.getResourceAsStream("/config.properties")) {
                if (is != null) {
                    props.load(is);
                    loaded = true;
                    System.out.println("✅ Config loaded from classpath");
                }
            } catch (IOException e) {
                // ignore
            }
        }

        if (!loaded) {
            System.out.println("ℹ No config.properties found — using localhost defaults (XAMPP mode)");
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
