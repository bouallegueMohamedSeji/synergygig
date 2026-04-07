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
    /** The file we actually loaded from — used for save(). */
    private static File configFile = null;

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
                    configFile = file;
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
                // classpath is read-only — pick a writable location for saves
                configFile = new File(System.getProperty("user.dir"), "config.properties");
                System.out.println("✅ Config loaded from classpath");
                return;
            }
        } catch (IOException e) {
            // ignore
        }

        if (!loaded) {
            configFile = new File(System.getProperty("user.dir"), "config.properties");
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

    public static String getGeminiApiKey() {
        return props.getProperty("gemini.api_key", "");
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

    // ── Write support (for in-app settings) ──────────────────────

    /** Update a property in memory. Call {@link #save()} to persist. */
    public static void set(String key, String value) {
        props.setProperty(key, value);
    }

    /**
     * Persist current properties back to the config file on disk.
     * Preserves comments and ordering by rewriting the original file
     * line-by-line, replacing known key=value lines, and appending new keys.
     */
    public static void save() throws IOException {
        if (configFile == null) {
            throw new IOException("No config file location known — cannot save.");
        }

        // Read original lines to preserve comments / ordering
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.Set<String> writtenKeys = new java.util.HashSet<>();

        if (configFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Detect key=value lines (skip blanks / comments)
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                        String key = trimmed.substring(0, trimmed.indexOf('=')).trim();
                        if (props.containsKey(key)) {
                            lines.add(key + "=" + props.getProperty(key));
                            writtenKeys.add(key);
                        } else {
                            lines.add(line);
                        }
                    } else {
                        lines.add(line);
                    }
                }
            }
        }

        // Append any new keys that weren't in the original file
        for (String key : props.stringPropertyNames()) {
            if (!writtenKeys.contains(key)) {
                lines.add(key + "=" + props.getProperty(key));
            }
        }

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(configFile))) {
            for (String l : lines) {
                writer.println(l);
            }
        }
        System.out.println("✅ Config saved to " + configFile.getAbsolutePath());
    }

    /** Re-load properties from disk (e.g. after external edits). */
    public static void reload() {
        props.clear();
        loaded = false;
        configFile = null;
        load();
    }
}
