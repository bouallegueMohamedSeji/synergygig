package utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import entities.User;
import services.ServiceUser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Bridge between the JavaFX application and the Python face-recognition scripts.
 * <p>
 * Calls the Python scripts via {@link ProcessBuilder} and parses their JSON stdout.
 */
public class FaceRecognitionUtil {

    /** Folder that contains the Python scripts (relative to project root). */
    private static final String PYTHON_DIR = "python";

    /** Maximum seconds to wait for a Python process. */
    private static final int TIMEOUT_SECONDS = 60;

    // ==================== Public API ====================

    /**
     * Check whether the Python environment and dependencies are ready.
     *
     * @return JSON result from test_face_detection.py
     */
    public static JsonObject testSetup() {
        return runScript("test_face_detection.py");
    }

    /**
     * Open the webcam enrollment flow. On success the returned JSON has:<br>
     * {@code {"success": true, "encoding": [...]}}
     */
    public static JsonObject enrollFace() {
        return runScript("capture_frame.py");
    }

    /**
     * Open the webcam authentication flow.<br>
     * Writes a temp JSON of all enrolled user encodings, passes it to the
     * Python script, and returns the match result.
     *
     * @return JSON with {@code user_id} on success, or error message.
     */
    public static JsonObject authenticateFace() {
        ServiceUser serviceUser = new ServiceUser();
        try {
            List<User> allUsers = serviceUser.recuperer();

            // Filter to users with face-encoding data
            List<User> enrolled = allUsers.stream()
                    .filter(User::hasFaceEnrolled)
                    .collect(Collectors.toList());

            if (enrolled.isEmpty()) {
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "No users have enrolled their face yet.");
                return err;
            }

            // Build JSON file for the Python script
            JsonArray usersArray = new JsonArray();
            Gson gson = new Gson();
            for (User u : enrolled) {
                JsonObject uo = new JsonObject();
                uo.addProperty("id", u.getId());
                uo.add("encoding", gson.fromJson(u.getFaceEncoding(), JsonArray.class));
                usersArray.add(uo);
            }
            JsonObject wrapper = new JsonObject();
            wrapper.add("users", usersArray);

            // Write to temp file
            Path tempFile = Files.createTempFile("synergygig_face_", ".json");
            Files.write(tempFile, wrapper.toString().getBytes(StandardCharsets.UTF_8));

            JsonObject result = runScript("face_recognition_auth.py", tempFile.toString());

            // Clean up
            Files.deleteIfExists(tempFile);
            return result;

        } catch (SQLException | IOException e) {
            JsonObject err = new JsonObject();
            err.addProperty("success", false);
            err.addProperty("error", e.getMessage());
            return err;
        }
    }

    /**
     * Save a face encoding string to the database for the given user.
     */
    public static void saveEncoding(int userId, String encodingJson) throws SQLException {
        new ServiceUser().updateFaceEncoding(userId, encodingJson);
    }

    /**
     * Retrieve the User object for a matched user_id from authentication.
     */
    public static User getUserById(int userId) throws SQLException {
        return new ServiceUser().getById(userId);
    }

    // ==================== Internal ====================

    private static String findPythonExecutable() {
        // Try common names on Windows
        for (String exe : new String[]{"python", "python3", "py"}) {
            try {
                Process p = new ProcessBuilder(exe, "--version")
                        .redirectErrorStream(true).start();
                p.waitFor(5, TimeUnit.SECONDS);
                if (p.exitValue() == 0) return exe;
            } catch (Exception ignored) {
            }
        }
        return "python"; // fallback
    }

    private static Path getPythonDir() {
        // Try project-root/python first
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path pyDir = projectRoot.resolve(PYTHON_DIR);
        if (Files.isDirectory(pyDir)) return pyDir;

        // Fallback: relative to class resources  
        return pyDir;
    }

    private static JsonObject runScript(String scriptName, String... args) {
        try {
            Path pyDir = getPythonDir();
            Path script = pyDir.resolve(scriptName);

            if (!Files.exists(script)) {
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "Python script not found: " + script);
                return err;
            }

            String python = findPythonExecutable();

            // Build command: python script.py [args...]
            var cmd = new java.util.ArrayList<String>();
            cmd.add(python);
            cmd.add(script.toString());
            for (String a : args) cmd.add(a);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(pyDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Read stdout in background
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread outThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        stdout.append(line);
                    }
                } catch (IOException ignored) {}
            });

            Thread errThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });

            outThread.start();
            errThread.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            outThread.join(2000);
            errThread.join(2000);

            if (!finished) {
                process.destroyForcibly();
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                err.addProperty("error", "Face recognition timed out. Please try again.");
                return err;
            }

            String output = stdout.toString().trim();
            if (output.isEmpty()) {
                JsonObject err = new JsonObject();
                err.addProperty("success", false);
                String stderrText = stderr.toString().trim();
                err.addProperty("error", stderrText.isEmpty()
                        ? "No response from face recognition script."
                        : stderrText);
                return err;
            }

            return new Gson().fromJson(output, JsonObject.class);

        } catch (Exception e) {
            JsonObject err = new JsonObject();
            err.addProperty("success", false);
            err.addProperty("error", "Failed to run face recognition: " + e.getMessage());
            return err;
        }
    }
}
