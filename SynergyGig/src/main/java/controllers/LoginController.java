package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import services.ServiceUser;
import utils.SessionManager;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private MediaView brandVideoView;

    private final ServiceUser serviceUser = new ServiceUser();
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize() {
        // Initialize Video
        try {
            String videoPath = getClass().getResource("/videos/HR_Team_Video_Generation_for_Application.mp4")
                    .toExternalForm();
            Media media = new Media(videoPath);
            mediaPlayer = new MediaPlayer(media);
            brandVideoView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            mediaPlayer.setMute(true); // Mute mainly for background ambiance
            mediaPlayer.play();

        } catch (Exception e) {
            System.err.println("Failed to load video: " + e.getMessage());
            // Fallback is handled by the Rect overlay usually, or just a black screen if
            // video fails
        }
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please fill in all fields.");
            return;
        }

        try {
            User user = serviceUser.login(email, password);
            if (user != null) {
                // Login successful
                SessionManager.getInstance().setCurrentUser(user);

                // Stop video to save resources
                if (mediaPlayer != null)
                    mediaPlayer.stop();

                // Navigate to Dashboard
                loadScene("/fxml/Dashboard.fxml");
            } else {
                showAlert("Error", "Invalid email or password.");
            }
        } catch (SQLException e) {
            showAlert("Error", "Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void showSignup() {
        if (mediaPlayer != null)
            mediaPlayer.stop();
        loadScene("/fxml/Signup.fxml");
    }

    private void loadScene(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = (Stage) emailField.getScene().getWindow();

            // Re-apply transparent scene style if needed, though Stage style persists
            Scene scene = new Scene(root, 1200, 800);
            scene.setFill(null);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            stage.setScene(scene);

            // Re-apply resize listener to the NEW scene/root
            utils.ResizeHelper.addResizeListener(stage);

            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to load scene: " + fxmlPath);
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
