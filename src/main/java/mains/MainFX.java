package mains;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.io.IOException;

public class MainFX extends Application {

    private double xOffset = 0;
    private double yOffset = 0;

    /** The background Python AI assistant process. */
    private static Process aiProcess;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Start the AI assistant Python service in the background
        startAIService();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        primaryStage.initStyle(StageStyle.TRANSPARENT);

        Scene scene = new Scene(root, 1200, 800);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        primaryStage.setTitle("SynergyGig");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);

        utils.ResizeHelper.addResizeListener(primaryStage);

        primaryStage.show();
    }

    /** Launch python/hr_assistant.py as a background process. */
    private void startAIService() {
        try {
            // Resolve the python script relative to the project root
            String projectRoot = System.getProperty("user.dir");
            File scriptDir = new File(projectRoot, "python");
            File script = new File(scriptDir, "hr_assistant.py");

            if (!script.exists()) {
                System.out.println("[AI Service] Script not found: " + script.getAbsolutePath());
                return;
            }

            ProcessBuilder pb = new ProcessBuilder("python", script.getAbsolutePath());
            pb.directory(scriptDir);
            pb.redirectErrorStream(true);
            // Discard output to avoid blocking
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);

            aiProcess = pb.start();
            System.out.println("[AI Service] Started hr_assistant.py (PID " + aiProcess.pid() + ")");

            // Shutdown hook to kill the process when the JVM exits
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (aiProcess != null && aiProcess.isAlive()) {
                    aiProcess.destroyForcibly();
                    System.out.println("[AI Service] Stopped.");
                }
            }));

        } catch (IOException e) {
            System.out.println("[AI Service] Failed to start: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        // Also kill AI service on explicit JavaFX stop
        if (aiProcess != null && aiProcess.isAlive()) {
            aiProcess.destroyForcibly();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
