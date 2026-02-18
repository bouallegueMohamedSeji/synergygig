package controllers;

import entities.User;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import services.ServiceUser;
import utils.FaceRecognitionUtil;
import utils.SessionManager;
import utils.SparkleCanvas;
import utils.SpotlightBorder;
import utils.ThemeSwipeHelper;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private StackPane rootStack;
    @FXML private StackPane cardGlow;

    private final ServiceUser serviceUser = new ServiceUser();
    private SparkleCanvas sparkleCanvas;

    @FXML
    public void initialize() {
        sparkleCanvas = new SparkleCanvas();
        rootStack.getChildren().add(0, sparkleCanvas);
        sparkleCanvas.prefWidthProperty().bind(rootStack.widthProperty());
        sparkleCanvas.prefHeightProperty().bind(rootStack.heightProperty());

        // Spotlight border on the form card
        SpotlightBorder.install(cardGlow);

        // Swipe right → light theme, swipe left → dark theme
        ThemeSwipeHelper.install(rootStack);

        // Apply persisted theme (in case user already swiped on Signup page)
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
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        StringBuilder errors = new StringBuilder();

        if (email.isEmpty()) {
            errors.append("\u2022 Email address is required.\n");
        } else if (!email.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            errors.append("\u2022 Please enter a valid email address.\n");
        }

        if (password.isEmpty()) {
            errors.append("\u2022 Password is required.\n");
        }

        if (errors.length() > 0) {
            showAlert("Validation Error", errors.toString().trim(), "error");
            return;
        }

        try {
            User user = serviceUser.login(email, password);
            if (user != null) {
                SessionManager.getInstance().setCurrentUser(user);
                if (sparkleCanvas != null) sparkleCanvas.stopAnimation();
                loadScene("/fxml/Dashboard.fxml");
            } else {
                showAlert("Login Failed", "Incorrect email or password.\nPlease try again.", "error");
            }
        } catch (SQLException e) {
            showAlert("Error", "A database error occurred.\nPlease try again later.", "error");
            e.printStackTrace();
        }
    }

    @FXML
    private void showSignup() {
        if (sparkleCanvas != null) sparkleCanvas.stopAnimation();
        loadScene("/fxml/Signup.fxml");
    }

    @FXML
    private void handleFaceLogin() {
        showAlert("Face Login", "Opening camera for face authentication...\nPlease look at the camera.", "info");

        new Thread(() -> {
            com.google.gson.JsonObject result = FaceRecognitionUtil.authenticateFace();

            javafx.application.Platform.runLater(() -> {
                if (result.get("success").getAsBoolean()) {
                    int userId = result.get("user_id").getAsInt();
                    double confidence = result.has("confidence") ? result.get("confidence").getAsDouble() : 0;

                    try {
                        User user = FaceRecognitionUtil.getUserById(userId);
                        if (user != null) {
                            SessionManager.getInstance().setCurrentUser(user);
                            if (sparkleCanvas != null) sparkleCanvas.stopAnimation();
                            showAlert("Welcome!",
                                    "Identity verified as " + user.getFirstName() + " " + user.getLastName()
                                    + "\nConfidence: " + String.format("%.0f%%", confidence * 100),
                                    "success");

                            // Small delay to let user see the success message
                            new Thread(() -> {
                                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                                javafx.application.Platform.runLater(() -> loadScene("/fxml/Dashboard.fxml"));
                            }).start();
                        } else {
                            showAlert("Face Login Failed", "User not found in database.", "error");
                        }
                    } catch (java.sql.SQLException e) {
                        showAlert("Error", "Database error: " + e.getMessage(), "error");
                    }
                } else {
                    String error = result.has("error") ? result.get("error").getAsString() : "Face login failed";
                    showAlert("Face Login Failed", error, "error");
                }
            });
        }).start();
    }

    private void loadScene(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
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
            case "info":    iconSymbol = "\uD83E\uDDD1"; iconColor = "#90DDF0"; break;
            default:        iconSymbol = "\u26A0"; iconColor = "#f87171"; break;
        }

        Label icon = new Label(iconSymbol);
        icon.setStyle("-fx-font-size: 28; -fx-text-fill: " + iconColor + "; -fx-font-weight: bold;");
        StackPane iconCircle = new StackPane(icon);
        iconCircle.setStyle("-fx-background-color: " + iconColor + "20; -fx-background-radius: 50; "
                + "-fx-min-width: 52; -fx-min-height: 52; -fx-max-width: 52; -fx-max-height: 52;");
        iconCircle.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 17; -fx-font-weight: bold;");

        Label msgLbl = new Label(content);
        msgLbl.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 13; -fx-line-spacing: 3;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(320);

        Button ok = new Button("Got it");
        String btnN = "-fx-background-color: linear-gradient(to right,#07393C,#2C666E); -fx-text-fill:#F0EDEE; "
                + "-fx-font-size:13; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:8 32; -fx-cursor:hand; "
                + "-fx-effect:dropshadow(gaussian,rgba(44,102,110,0.3),10,0,0,2);";
        String btnH = "-fx-background-color: linear-gradient(to right,#2C666E,#90DDF0); -fx-text-fill:#0A090C; "
                + "-fx-font-size:13; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:8 32; -fx-cursor:hand; "
                + "-fx-effect:dropshadow(gaussian,rgba(144,221,240,0.35),16,0,0,2);";
        ok.setStyle(btnN);
        ok.setOnMouseEntered(e -> ok.setStyle(btnH));
        ok.setOnMouseExited(e -> ok.setStyle(btnN));

        VBox card = new VBox(16, iconCircle, titleLbl, msgLbl, ok);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30, 36, 24, 36));
        card.setMaxWidth(420);
        card.setStyle("-fx-background-color: #14131A; -fx-background-radius: 16; "
                + "-fx-border-color: #1C1B22; -fx-border-radius: 16; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 30, 0, 0, 10);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        root.setPadding(new Insets(40));
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
