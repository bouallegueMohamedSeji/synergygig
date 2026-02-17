package controllers;

import entities.User;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import services.ServiceUser;

import java.io.IOException;
import java.sql.SQLException;

public class SignupController {

    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private ComboBox<String> roleComboBox;
    @FXML
    private MediaView brandVideoView;

    private final ServiceUser serviceUser = new ServiceUser();
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize() {
        // Exclude ADMIN and HR_MANAGER from direct signup
        roleComboBox.setItems(FXCollections.observableArrayList(
                "EMPLOYEE", "PROJECT_OWNER", "GIG_WORKER"));
        roleComboBox.getSelectionModel().selectFirst();

        initVideo();
    }

    private void initVideo() {
        try {
            String videoPath = getClass().getResource("/videos/HR_Team_Video_Generation_for_Application.mp4")
                    .toExternalForm();
            Media media = new Media(videoPath);
            mediaPlayer = new MediaPlayer(media);
            brandVideoView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            mediaPlayer.setMute(true);
            mediaPlayer.play();
        } catch (Exception e) {
            System.err.println("Failed to load video: " + e.getMessage());
        }
    }

    @FXML
    private void handleSignup() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String role = roleComboBox.getValue();

        // Validations
        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "All fields are required.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showAlert("Error", "Passwords do not match.");
            return;
        }

        try {
            if (serviceUser.emailExists(email)) {
                showAlert("Error", "Email is already registered.");
                return;
            }

            User newUser = new User(email, password, firstName, lastName, role);
            serviceUser.ajouter(newUser);

            showAlert("Success", "Account created successfully! Please login.");
            showLogin();

        } catch (SQLException e) {
            showAlert("Error", "Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void showLogin() {
        if (mediaPlayer != null)
            mediaPlayer.stop();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) emailField.getScene().getWindow();

            Scene scene = new Scene(root, 1200, 800);
            scene.setFill(null);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);

            utils.ResizeHelper.addResizeListener(stage);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
