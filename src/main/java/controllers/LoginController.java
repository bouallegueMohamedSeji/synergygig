package controllers;

import com.google.gson.JsonObject;
import entities.User;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import services.ServiceUser;
import utils.EmailService;
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
                if (!user.isVerified()) {
                    showAlert("Email Not Verified",
                            "Please check your inbox and verify\nyour email before signing in.", "error");
                    return;
                }
                if (!user.isActive()) {
                    showAlert("Account Frozen",
                            "Your account has been frozen.\nPlease contact an administrator.", "error");
                    return;
                }
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

    // ==================== FORGOT PASSWORD (3-step OTP flow) ====================

    @FXML
    private void handleForgotPassword() {
        Stage owner = (Stage) emailField.getScene().getWindow();
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        final String[] resetEmail = {""};
        final String[] resetOtp = {""};
        final String[] resetFirstName = {""};

        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(24, 28, 20, 28));
        card.setMaxWidth(360);
        card.setStyle("-fx-background-color: #14131A; -fx-background-radius: 14; "
                + "-fx-border-color: #1C1B22; -fx-border-radius: 14; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 24, 0, 0, 8);");

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

        showResetStep1(card, dialog, root, resetEmail, resetOtp, resetFirstName);

        root.setOpacity(0);
        dialog.show();
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void showResetStep1(VBox card, Stage dialog, StackPane root,
                                String[] email, String[] otp, String[] firstName) {
        card.getChildren().clear();

        Label icon = new Label("\uD83D\uDD12");
        icon.setStyle("-fx-font-size: 22;");
        StackPane iconCircle = makeIconCircle(icon, "#2C666E");

        Label title = new Label("Reset Password");
        title.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 15; -fx-font-weight: bold;");
        Label subtitle = new Label("Enter the email linked to your account");
        subtitle.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11;");
        subtitle.setWrapText(true);

        Label emailLabel = new Label("Email Address");
        emailLabel.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 11; -fx-font-weight: 600;");
        TextField emailInput = new TextField();
        emailInput.setPromptText("name@company.com");
        emailInput.getStyleClass().add("auth-input");
        if (!emailField.getText().isBlank()) emailInput.setText(emailField.getText().trim());

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #f87171; -fx-font-size: 10;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);

        Button sendBtn = new Button("Send Reset Code  \u2192");
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.getStyleClass().add("auth-btn-primary");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("auth-link");
        cancelBtn.setOnAction(e -> closeDialog(dialog, root));

        sendBtn.setOnAction(e -> {
            String inputEmail = emailInput.getText().trim();
            if (inputEmail.isEmpty() || !inputEmail.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                errorLbl.setText("\u26A0 Please enter a valid email address.");
                errorLbl.setVisible(true);
                return;
            }
            sendBtn.setDisable(true);
            sendBtn.setText("Sending\u2026");
            errorLbl.setVisible(false);

            new Thread(() -> {
                try {
                    JsonObject result = serviceUser.requestOtp(inputEmail);
                    if (result == null || !result.has("otp")) {
                        Platform.runLater(() -> {
                            errorLbl.setText("\u26A0 No account found with that email.");
                            errorLbl.setVisible(true);
                            sendBtn.setDisable(false);
                            sendBtn.setText("Send Reset Code  \u2192");
                        });
                        return;
                    }
                    email[0] = inputEmail;
                    otp[0] = result.get("otp").getAsString();
                    firstName[0] = result.has("first_name") ? result.get("first_name").getAsString() : "User";
                    EmailService.sendOtpEmail(inputEmail, firstName[0], otp[0]);
                    Platform.runLater(() -> showResetStep2(card, dialog, root, email, otp, firstName));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        errorLbl.setText("\u26A0 " + ex.getMessage());
                        errorLbl.setVisible(true);
                        sendBtn.setDisable(false);
                        sendBtn.setText("Send Reset Code  \u2192");
                    });
                }
            }).start();
        });

        VBox emailGroup = new VBox(5, emailLabel, emailInput);
        card.getChildren().addAll(iconCircle, title, subtitle,
                emailGroup, errorLbl, sendBtn, cancelBtn);
    }

    private void showResetStep2(VBox card, Stage dialog, StackPane root,
                                String[] email, String[] otp, String[] firstName) {
        card.getChildren().clear();

        Label icon = new Label("\u2709");
        icon.setStyle("-fx-font-size: 22;");
        StackPane iconCircle = makeIconCircle(icon, "#90DDF0");

        Label title = new Label("Check Your Email");
        title.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 15; -fx-font-weight: bold;");
        Label subtitle = new Label("We sent a 6-digit code to " + email[0]);
        subtitle.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11;");
        subtitle.setWrapText(true);

        // --- Timer (1 minute) ---
        Label timerLabel = new Label("Code expires in 1:00");
        timerLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 10; -fx-font-weight: 600;");
        final int[] secondsLeft = {60};
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            secondsLeft[0]--;
            int m = secondsLeft[0] / 60, s = secondsLeft[0] % 60;
            timerLabel.setText("Code expires in " + m + ":" + String.format("%02d", s));
            if (secondsLeft[0] <= 15)
                timerLabel.setStyle("-fx-text-fill: #f87171; -fx-font-size: 10; -fx-font-weight: 600;");
            if (secondsLeft[0] <= 0)
                timerLabel.setText("Code expired \u2014 go back and request a new one");
        }));
        timer.setCycleCount(60);
        timer.play();

        // --- OTP input (shadcn-style: 3 + separator + 3) ---
        HBox otpBox = new HBox(0);
        otpBox.setAlignment(Pos.CENTER);
        TextField[] digitFields = new TextField[6];

        String baseStyle = "-fx-background-color: #0F0E11; -fx-text-fill: #F0EDEE; -fx-font-size: 18; "
                + "-fx-font-weight: bold; -fx-font-family: 'Consolas'; -fx-alignment: center; "
                + "-fx-pref-width: 40; -fx-pref-height: 46; -fx-max-width: 40; -fx-max-height: 46; "
                + "-fx-background-radius: 0; -fx-border-color: #1C1B22; -fx-border-width: 1 0.5 1 0.5;";
        String firstStyle = baseStyle + " -fx-background-radius: 8 0 0 8; -fx-border-radius: 8 0 0 8; -fx-border-width: 1 0.5 1 1;";
        String lastGroupStyle = baseStyle + " -fx-background-radius: 0 8 8 0; -fx-border-radius: 0 8 8 0; -fx-border-width: 1 1 1 0.5;";

        for (int i = 0; i < 6; i++) {
            TextField tf = new TextField();
            tf.setAlignment(Pos.CENTER);
            if (i == 0) tf.setStyle(firstStyle);
            else if (i == 2) tf.setStyle(baseStyle + " -fx-background-radius: 0 8 8 0; -fx-border-radius: 0 8 8 0; -fx-border-width: 1 1 1 0.5;");
            else if (i == 3) tf.setStyle(baseStyle + " -fx-background-radius: 8 0 0 8; -fx-border-radius: 8 0 0 8; -fx-border-width: 1 0.5 1 1;");
            else if (i == 5) tf.setStyle(lastGroupStyle);
            else tf.setStyle(baseStyle);

            final int idx = i;
            final String origStyle = tf.getStyle();
            tf.textProperty().addListener((obs, ov, nv) -> {
                if (nv.length() > 1) tf.setText(nv.substring(nv.length() - 1));
                if (!nv.isEmpty() && nv.matches("\\d") && idx < 5) digitFields[idx + 1].requestFocus();
                tf.setStyle(!nv.isEmpty()
                        ? origStyle.replace("#1C1B22", "#2C666E").replace("#0F0E11", "#12171A")
                        : origStyle);
            });
            tf.setOnKeyPressed(ke -> {
                if (ke.getCode() == javafx.scene.input.KeyCode.BACK_SPACE && tf.getText().isEmpty() && idx > 0)
                    digitFields[idx - 1].requestFocus();
            });
            digitFields[i] = tf;
            otpBox.getChildren().add(tf);

            // Dash separator between group 1 (0-2) and group 2 (3-5)
            if (i == 2) {
                Label dash = new Label("\u2013");
                dash.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 16; -fx-padding: 0 8;");
                otpBox.getChildren().add(dash);
            }
        }

        // Paste support
        digitFields[0].setOnKeyPressed(ke -> {
            if (ke.isControlDown() && ke.getCode() == javafx.scene.input.KeyCode.V) {
                try {
                    String clip = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                    if (clip != null && clip.matches("\\d{6}")) {
                        for (int i = 0; i < 6; i++) digitFields[i].setText(String.valueOf(clip.charAt(i)));
                        digitFields[5].requestFocus();
                    }
                } catch (Exception ignored) {}
            }
        });

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #f87171; -fx-font-size: 10;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);

        Button verifyBtn = new Button("Verify Code  \u2192");
        verifyBtn.setMaxWidth(Double.MAX_VALUE);
        verifyBtn.getStyleClass().add("auth-btn-primary");

        Button backBtn = new Button("\u2190 Back");
        backBtn.getStyleClass().add("auth-link");
        backBtn.setOnAction(e -> { timer.stop(); showResetStep1(card, dialog, root, email, otp, firstName); });

        verifyBtn.setOnAction(e -> {
            StringBuilder code = new StringBuilder();
            for (TextField tf : digitFields) code.append(tf.getText());
            String entered = code.toString();

            if (entered.length() != 6 || !entered.matches("\\d{6}")) {
                errorLbl.setText("\u26A0 Please enter all 6 digits.");
                errorLbl.setVisible(true);
                return;
            }
            if (secondsLeft[0] <= 0) {
                errorLbl.setText("\u26A0 Code expired. Go back and request a new one.");
                errorLbl.setVisible(true);
                return;
            }
            verifyBtn.setDisable(true);
            verifyBtn.setText("Verifying\u2026");
            errorLbl.setVisible(false);

            new Thread(() -> {
                boolean valid = serviceUser.verifyOtp(email[0], entered);
                Platform.runLater(() -> {
                    if (valid) { timer.stop(); showResetStep3(card, dialog, root, email, otp, firstName); }
                    else {
                        errorLbl.setText("\u26A0 Invalid code. Please check and try again.");
                        errorLbl.setVisible(true);
                        verifyBtn.setDisable(false);
                        verifyBtn.setText("Verify Code  \u2192");
                    }
                });
            }).start();
        });

        card.getChildren().addAll(iconCircle, title, subtitle, timerLabel,
                otpBox, errorLbl, verifyBtn, backBtn);
        Platform.runLater(() -> digitFields[0].requestFocus());
    }

    private void showResetStep3(VBox card, Stage dialog, StackPane root,
                                String[] email, String[] otp, String[] firstName) {
        card.getChildren().clear();

        Label icon = new Label("\uD83D\uDD11");
        icon.setStyle("-fx-font-size: 22;");
        StackPane iconCircle = makeIconCircle(icon, "#34d399");

        Label title = new Label("Set New Password");
        title.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 15; -fx-font-weight: bold;");
        Label subtitle = new Label("Choose a strong password for your account");
        subtitle.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11;");
        subtitle.setWrapText(true);

        Label passLabel = new Label("New Password");
        passLabel.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 11; -fx-font-weight: 600;");
        PasswordField passField = new PasswordField();
        passField.setPromptText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        passField.getStyleClass().add("auth-input");

        Label confirmLabel = new Label("Confirm Password");
        confirmLabel.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 11; -fx-font-weight: 600;");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        confirmField.getStyleClass().add("auth-input");

        // Strength bar
        HBox strengthBar = new HBox(3);
        strengthBar.setAlignment(Pos.CENTER_LEFT);
        Region[] bars = new Region[4];
        for (int i = 0; i < 4; i++) {
            bars[i] = new Region();
            bars[i].setPrefHeight(3);
            bars[i].setPrefWidth(50);
            bars[i].setStyle("-fx-background-color: #1C1B22; -fx-background-radius: 2;");
            strengthBar.getChildren().add(bars[i]);
        }
        Label strengthLabel = new Label("");
        strengthLabel.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 9;");

        passField.textProperty().addListener((obs, ov, nv) -> {
            int strength = calcPasswordStrength(nv);
            String[] colors = {"#f87171", "#FBBF24", "#90DDF0", "#34d399"};
            String[] labels = {"Weak", "Fair", "Good", "Strong"};
            for (int i = 0; i < 4; i++)
                bars[i].setStyle("-fx-background-color: " + (i < strength ? colors[strength - 1] : "#1C1B22") + "; -fx-background-radius: 2;");
            strengthLabel.setText(nv.isEmpty() ? "" : labels[Math.max(0, strength - 1)]);
            strengthLabel.setStyle("-fx-text-fill: " + (nv.isEmpty() ? "#6B6B78" : colors[Math.max(0, strength - 1)]) + "; -fx-font-size: 9;");
        });

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #f87171; -fx-font-size: 10;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);

        Button resetBtn = new Button("Reset Password  \u2713");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        resetBtn.getStyleClass().add("auth-btn-primary");

        resetBtn.setOnAction(e -> {
            String pass = passField.getText();
            String confirm = confirmField.getText();
            if (pass.length() < 6) { errorLbl.setText("\u26A0 At least 6 characters."); errorLbl.setVisible(true); return; }
            if (!pass.equals(confirm)) { errorLbl.setText("\u26A0 Passwords do not match."); errorLbl.setVisible(true); return; }
            resetBtn.setDisable(true);
            resetBtn.setText("Resetting\u2026");
            errorLbl.setVisible(false);
            new Thread(() -> {
                try {
                    boolean ok = serviceUser.resetPassword(email[0], otp[0], pass);
                    Platform.runLater(() -> {
                        if (ok) {
                            closeDialog(dialog, (StackPane) card.getParent());
                            showAlert("Password Reset", "Your password has been reset!\nYou can now sign in.", "success");
                        } else {
                            errorLbl.setText("\u26A0 Reset failed. Try again.");
                            errorLbl.setVisible(true);
                            resetBtn.setDisable(false);
                            resetBtn.setText("Reset Password  \u2713");
                        }
                    });
                } catch (SQLException ex) {
                    Platform.runLater(() -> {
                        errorLbl.setText("\u26A0 " + ex.getMessage());
                        errorLbl.setVisible(true);
                        resetBtn.setDisable(false);
                        resetBtn.setText("Reset Password  \u2713");
                    });
                }
            }).start();
        });

        VBox passGroup = new VBox(5, passLabel, passField, new HBox(6, strengthBar, strengthLabel));
        VBox confirmGroup = new VBox(5, confirmLabel, confirmField);
        card.getChildren().addAll(iconCircle, title, subtitle, passGroup, confirmGroup, errorLbl, resetBtn);
    }

    // ==================== helpers ====================

    private StackPane makeIconCircle(Label icon, String color) {
        StackPane circle = new StackPane(icon);
        circle.setStyle("-fx-background-color: " + color + "20; -fx-background-radius: 50; "
                + "-fx-min-width: 44; -fx-min-height: 44; -fx-max-width: 44; -fx-max-height: 44;");
        circle.setAlignment(Pos.CENTER);
        return circle;
    }

    private int calcPasswordStrength(String pwd) {
        if (pwd == null || pwd.isEmpty()) return 0;
        int score = 0;
        if (pwd.length() >= 6) score++;
        if (pwd.length() >= 10) score++;
        if (pwd.matches(".*[A-Z].*") && pwd.matches(".*[a-z].*")) score++;
        if (pwd.matches(".*\\d.*") && pwd.matches(".*[^a-zA-Z0-9].*")) score++;
        return Math.min(score, 4);
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
