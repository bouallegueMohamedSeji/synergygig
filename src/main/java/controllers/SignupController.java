package controllers;

import com.google.gson.JsonObject;
import entities.User;
import javafx.animation.FadeTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import services.ServiceUser;
import utils.AppConfig;
import utils.EmailService;
import utils.SparkleCanvas;
import utils.SpotlightBorder;
import utils.ThemeSwipeHelper;

import java.io.IOException;
import java.sql.SQLException;

public class SignupController {

    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> roleComboBox;
    @FXML private StackPane rootStack;
    @FXML private StackPane cardGlow;

    private final ServiceUser serviceUser = new ServiceUser();
    private SparkleCanvas sparkleCanvas;

    @FXML
    public void initialize() {
        roleComboBox.setItems(FXCollections.observableArrayList(
                "EMPLOYEE", "PROJECT_OWNER", "GIG_WORKER"));
        roleComboBox.getSelectionModel().selectFirst();

        sparkleCanvas = new SparkleCanvas();
        rootStack.getChildren().add(0, sparkleCanvas);
        sparkleCanvas.prefWidthProperty().bind(rootStack.widthProperty());
        sparkleCanvas.prefHeightProperty().bind(rootStack.heightProperty());

        // Spotlight border on the form card
        SpotlightBorder.install(cardGlow);

        // Swipe right → light theme, swipe left → dark theme
        ThemeSwipeHelper.install(rootStack);

        // Apply persisted theme (in case user already swiped on Login page)
        rootStack.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                ThemeSwipeHelper.applyCurrentTheme(newScene);
                newScene.windowProperty().addListener((wo, ow, nw) -> {
                    if (nw instanceof Stage) utils.ResizeHelper.addResizeListener((Stage) nw);
                });
                if (newScene.getWindow() instanceof Stage)
                    utils.ResizeHelper.addResizeListener((Stage) newScene.getWindow());
            }
        });
    }

    @FXML
    private void handleSignup() {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String role = roleComboBox.getValue();

        StringBuilder errors = new StringBuilder();

        if (firstName.isEmpty()) {
            errors.append("\u2022 First name is required.\n");
        } else if (firstName.length() < 2) {
            errors.append("\u2022 First name must be at least 2 characters.\n");
        } else if (!firstName.matches("^[a-zA-Z\\u00C0-\\u00FF\\s'-]+$")) {
            errors.append("\u2022 First name must contain only letters.\n");
        }

        if (lastName.isEmpty()) {
            errors.append("\u2022 Last name is required.\n");
        } else if (lastName.length() < 2) {
            errors.append("\u2022 Last name must be at least 2 characters.\n");
        } else if (!lastName.matches("^[a-zA-Z\\u00C0-\\u00FF\\s'-]+$")) {
            errors.append("\u2022 Last name must contain only letters.\n");
        }

        if (email.isEmpty()) {
            errors.append("\u2022 Email address is required.\n");
        } else if (!email.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            errors.append("\u2022 Please enter a valid email address.\n");
        }

        if (role == null || role.isEmpty()) {
            errors.append("\u2022 Please select an account type.\n");
        }

        if (password.isEmpty()) {
            errors.append("\u2022 Password is required.\n");
        } else if (password.length() < 6) {
            errors.append("\u2022 Password must be at least 6 characters.\n");
        }

        if (confirmPassword.isEmpty()) {
            errors.append("\u2022 Please confirm your password.\n");
        } else if (!password.isEmpty() && !password.equals(confirmPassword)) {
            errors.append("\u2022 Passwords do not match.\n");
        }

        if (errors.length() > 0) {
            showAlert("Validation Error", errors.toString().trim(), "error");
            return;
        }

        try {
            if (serviceUser.emailExists(email)) {
                showAlert("Email Taken", "This email is already registered.\nPlease use a different email or sign in.", "error");
                return;
            }

            User newUser = new User(email, password, firstName, lastName, role);
            serviceUser.ajouter(newUser);

            // Send verification email asynchronously
            new Thread(() -> {
                try {
                    JsonObject verif = serviceUser.requestVerification(email);
                    if (verif != null && verif.has("token")) {
                        String token = verif.get("token").getAsString();
                        String baseUrl = AppConfig.get("rest.base_url", "https://rest.benzaitsue.work.gd/api");
                        String verifyUrl = baseUrl.replace("/api", "") + "/api/auth/verify/" + token;
                        EmailService.sendVerificationEmail(email, firstName, verifyUrl);
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to send verification email: " + ex.getMessage());
                }
            }).start();

            showAlert("Verify Your Email",
                    "We sent a verification link to\n" + email
                    + "\n\nPlease check your inbox and verify\nbefore signing in.", "info");
            showLogin();

        } catch (SQLException e) {
            showAlert("Error", "A database error occurred.\nPlease try again later.", "error");
            e.printStackTrace();
        }
    }

    @FXML
    private void showLogin() {
        if (sparkleCanvas != null) sparkleCanvas.stopAnimation();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) emailField.getScene().getWindow();

            Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
            scene.setFill(null);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            ThemeSwipeHelper.applyCurrentTheme(scene);
            stage.setScene(scene);

            utils.ResizeHelper.addResizeListener(stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content, String type) {
        utils.StyledAlert.show(emailField.getScene().getWindow(), title, content, type);
    }
}
