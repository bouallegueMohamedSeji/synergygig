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
        Stage owner = (Stage) emailField.getScene().getWindow();
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        String iconSymbol, iconColor;
        switch (type) {
            case "success": iconSymbol = "\u2713"; iconColor = "#34d399"; break;
            case "info":    iconSymbol = "\u2709"; iconColor = "#90DDF0"; break;
            default:        iconSymbol = "\u26A0"; iconColor = "#f87171"; break;
        }

        Label icon = new Label(iconSymbol);
        icon.setStyle("-fx-font-size: 22; -fx-text-fill: " + iconColor + "; -fx-font-weight: bold;");
        StackPane iconCircle = new StackPane(icon);
        iconCircle.setStyle("-fx-background-color: " + iconColor + "20; -fx-background-radius: 50; "
                + "-fx-min-width: 44; -fx-min-height: 44; -fx-max-width: 44; -fx-max-height: 44;");
        iconCircle.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 14; -fx-font-weight: bold;");

        Label msgLbl = new Label(content);
        msgLbl.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 12; -fx-line-spacing: 2;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(260);

        Button ok = new Button("Got it");
        String btnN = "-fx-background-color: linear-gradient(to right,#07393C,#2C666E); -fx-text-fill:#F0EDEE; "
                + "-fx-font-size:12; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:7 28; -fx-cursor:hand;";
        String btnH = "-fx-background-color: linear-gradient(to right,#2C666E,#90DDF0); -fx-text-fill:#0A090C; "
                + "-fx-font-size:12; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:7 28; -fx-cursor:hand;";
        ok.setStyle(btnN);
        ok.setOnMouseEntered(e -> ok.setStyle(btnH));
        ok.setOnMouseExited(e -> ok.setStyle(btnN));

        VBox card = new VBox(10, iconCircle, titleLbl, msgLbl, ok);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20, 24, 16, 24));
        card.setMaxWidth(300);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: #14131A; -fx-background-radius: 14; "
                + "-fx-border-color: #1C1B22; -fx-border-radius: 14; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 24, 0, 0, 8);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        root.setAlignment(Pos.CENTER);
        root.setOnMouseClicked(e -> { if (e.getTarget() == root) closeDialog(dialog, root); });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.setWidth(owner.getWidth());
        dialog.setHeight(owner.getHeight());
        dialog.setX(owner.getX());
        dialog.setY(owner.getY());

        root.setOpacity(0);
        ok.setOnAction(e -> closeDialog(dialog, root));
        dialog.show();
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void closeDialog(Stage dialog, StackPane root) {
        FadeTransition ft = new FadeTransition(Duration.millis(150), root);
        ft.setToValue(0);
        ft.setOnFinished(ev -> dialog.close());
        ft.play();
    }
}
