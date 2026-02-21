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
import javafx.scene.image.ImageView;
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
import utils.CaptchaGenerator;
import utils.SoundManager;

import java.io.IOException;
import java.sql.SQLException;

public class LoginController {

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private StackPane rootStack;
    @FXML private StackPane cardGlow;
    @FXML private ImageView captchaImage;
    @FXML private TextField captchaField;

    private final ServiceUser serviceUser = new ServiceUser();
    private SparkleCanvas sparkleCanvas;
    private final CaptchaGenerator captchaGenerator = new CaptchaGenerator();

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

        // Generate initial CAPTCHA
        refreshCaptcha();

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

        // CAPTCHA validation
        String captchaInput = captchaField.getText().trim();
        if (captchaInput.isEmpty()) {
            errors.append("\u2022 Please solve the CAPTCHA.\n");
        } else if (!captchaInput.equals(captchaGenerator.getAnswer())) {
            errors.append("\u2022 Incorrect CAPTCHA answer.\n");
            refreshCaptcha();
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
                SoundManager.getInstance().play(SoundManager.LOGIN_SUCCESS);
                if (sparkleCanvas != null) sparkleCanvas.stopAnimation();
                loadScene("/fxml/Dashboard.fxml");
            } else {
                SoundManager.getInstance().play(SoundManager.LOGIN_FAILED);
                showAlert("Login Failed", "Incorrect email or password.\nPlease try again.", "error");
            }
        } catch (SQLException e) {
            showAlert("Error", "A database error occurred.\nPlease try again later.", "error");
            e.printStackTrace();
        }
    }

    @FXML
    private void refreshCaptcha() {
        captchaImage.setImage(captchaGenerator.generate());
        captchaField.clear();
    }

    @FXML
    private void showSignup() {
        if (sparkleCanvas != null) sparkleCanvas.stopAnimation();
        loadScene("/fxml/Signup.fxml");
    }

    @FXML
    private void handleFaceLogin() {
        // Check if face recognition is available on this machine
        if (!utils.FaceRecognitionUtil.isAvailable()) {
            showAlert("Face Login Unavailable",
                    "Face recognition requires Python and the face_recognition library installed on this computer.\n\n"
                    + "Please use email & password to sign in.", "error");
            return;
        }

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
                            SoundManager.getInstance().play(SoundManager.FACE_RECOGNIZED);
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
                    SoundManager.getInstance().play(SoundManager.FACE_FAILED);
                    showAlert("Face Login Failed", error, "error");
                }
            });
        }).start();
    }

    // ==================== FORGOT PASSWORD (3-step OTP flow) ====================

    // ── Always-dark dialog palette (shadcn/zinc) ──
    private static final String DLG_BG        = "#09090B";
    private static final String DLG_BORDER    = "#27272A";
    private static final String DLG_TITLE     = "#FAFAFA";
    private static final String DLG_SUB       = "#A1A1AA";
    private static final String DLG_LABEL     = "#D4D4D8";
    private static final String DLG_DESC      = "#71717A";
    private static final String DLG_INP_BG    = "#09090B";
    private static final String DLG_INP_BD    = "#27272A";
    private static final String DLG_INP_TXT   = "#FAFAFA";
    private static final String DLG_ERR       = "#EF4444";
    private static final String DLG_ACCENT    = "#3B82F6";

    private String dlgInput() {
        return "-fx-background-color:" + DLG_INP_BG + ";-fx-text-fill:" + DLG_INP_TXT + ";"
             + "-fx-prompt-text-fill:#52525B;-fx-border-color:" + DLG_INP_BD + ";"
             + "-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 12;-fx-font-size:13;";
    }
    private String dlgBtn() {
        return "-fx-background-color:" + DLG_INP_TXT + ";-fx-text-fill:" + DLG_BG + ";"
             + "-fx-font-size:12;-fx-font-weight:600;-fx-background-radius:8;-fx-padding:8 0;-fx-cursor:hand;";
    }
    private String dlgBtnH() {
        return "-fx-background-color:#E4E4E7;-fx-text-fill:" + DLG_BG + ";"
             + "-fx-font-size:12;-fx-font-weight:600;-fx-background-radius:8;-fx-padding:8 0;-fx-cursor:hand;";
    }
    private String dlgGhost() {
        return "-fx-background-color:transparent;-fx-text-fill:" + DLG_SUB + ";"
             + "-fx-font-size:12;-fx-background-radius:8;-fx-padding:6 0;-fx-cursor:hand;"
             + "-fx-border-color:" + DLG_BORDER + ";-fx-border-radius:8;";
    }
    private String dlgGhostH() { return dlgGhost().replace("transparent", "#18181B"); }

    private VBox dlgField(String label, javafx.scene.control.TextInputControl input, String desc) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill:" + DLG_LABEL + ";-fx-font-size:12;-fx-font-weight:600;");
        input.setStyle(dlgInput());
        input.setPrefHeight(36);
        VBox f = new VBox(4, l, input);
        if (desc != null) {
            Label d = new Label(desc);
            d.setStyle("-fx-text-fill:" + DLG_DESC + ";-fx-font-size:10;");
            d.setWrapText(true);
            f.getChildren().add(d);
        }
        return f;
    }

    @FXML
    private void handleForgotPassword() {
        Stage owner = (Stage) emailField.getScene().getWindow();
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        final String[] resetEmail = {""};
        final String[] resetOtp   = {""};
        final String[] resetFirst = {""};

        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(20, 24, 18, 24));
        card.setMaxWidth(380);
        card.setStyle("-fx-background-color:" + DLG_BG + ";-fx-background-radius:12;"
                + "-fx-border-color:" + DLG_BORDER + ";-fx-border-radius:12;-fx-border-width:1;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),20,0,0,6);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color:rgba(0,0,0,0.5);");
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30));
        root.setOnMouseClicked(e -> { if (e.getTarget() == root) closeDialog(dialog, root); });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.setWidth(owner.getWidth());
        dialog.setHeight(owner.getHeight());
        dialog.setX(owner.getX());
        dialog.setY(owner.getY());

        showResetStep1(card, dialog, root, resetEmail, resetOtp, resetFirst);

        root.setOpacity(0);
        dialog.show();
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
        fadeIn.setToValue(1);
        fadeIn.play();
    }

    private void showResetStep1(VBox card, Stage dialog, StackPane root,
                                String[] email, String[] otp, String[] firstName) {
        card.getChildren().clear();

        Label title = new Label("Reset Password");
        title.setStyle("-fx-text-fill:" + DLG_TITLE + ";-fx-font-size:15;-fx-font-weight:bold;");
        Label sub = new Label("Enter the email linked to your account and we\u2019ll send a code.");
        sub.setStyle("-fx-text-fill:" + DLG_SUB + ";-fx-font-size:11;");
        sub.setWrapText(true);

        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color:" + DLG_BORDER + ";");

        TextField emailInput = new TextField();
        emailInput.setPromptText("name@company.com");
        if (!emailField.getText().isBlank()) emailInput.setText(emailField.getText().trim());
        VBox emailGrp = dlgField("Email Address", emailInput, "We\u2019ll send a 6-digit code to this email.");

        Label err = new Label();
        err.setStyle("-fx-text-fill:" + DLG_ERR + ";-fx-font-size:10;");
        err.setWrapText(true);
        err.setVisible(false); err.setManaged(false);

        Button send = new Button("Send Reset Code");
        send.setMaxWidth(Double.MAX_VALUE);
        send.setStyle(dlgBtn());
        send.setOnMouseEntered(ev -> send.setStyle(dlgBtnH()));
        send.setOnMouseExited(ev -> send.setStyle(dlgBtn()));

        Button cancel = new Button("Cancel");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setStyle(dlgGhost());
        cancel.setOnMouseEntered(ev -> cancel.setStyle(dlgGhostH()));
        cancel.setOnMouseExited(ev -> cancel.setStyle(dlgGhost()));
        cancel.setOnAction(e -> closeDialog(dialog, root));

        send.setOnAction(e -> {
            String inp = emailInput.getText().trim();
            if (inp.isEmpty() || !inp.matches("^[\\w.%+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
                err.setText("\u26A0 Please enter a valid email address.");
                err.setVisible(true); err.setManaged(true); return;
            }
            send.setDisable(true); send.setText("Sending\u2026");
            err.setVisible(false); err.setManaged(false);

            new Thread(() -> {
                try {
                    JsonObject res = serviceUser.requestOtp(inp);
                    if (res == null || !res.has("otp")) {
                        Platform.runLater(() -> {
                            err.setText("\u26A0 No account found with that email.");
                            err.setVisible(true); err.setManaged(true);
                            send.setDisable(false); send.setText("Send Reset Code");
                        }); return;
                    }
                    email[0] = inp;
                    otp[0] = res.get("otp").getAsString();
                    firstName[0] = res.has("first_name") ? res.get("first_name").getAsString() : "User";
                    EmailService.sendOtpEmail(inp, firstName[0], otp[0]);
                    Platform.runLater(() -> showResetStep2(card, dialog, root, email, otp, firstName));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        err.setText("\u26A0 " + ex.getMessage());
                        err.setVisible(true); err.setManaged(true);
                        send.setDisable(false); send.setText("Send Reset Code");
                    });
                }
            }).start();
        });

        card.getChildren().addAll(title, sub, sep, emailGrp, err, send, cancel);
    }

    private void showResetStep2(VBox card, Stage dialog, StackPane root,
                                String[] email, String[] otp, String[] firstName) {
        card.getChildren().clear();

        Label title = new Label("Verify Code");
        title.setStyle("-fx-text-fill:" + DLG_TITLE + ";-fx-font-size:15;-fx-font-weight:bold;");
        Label sub = new Label("Enter the 6-digit code sent to " + email[0]);
        sub.setStyle("-fx-text-fill:" + DLG_SUB + ";-fx-font-size:11;");
        sub.setWrapText(true);

        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color:" + DLG_BORDER + ";");

        Label timerLbl = new Label("Expires in 1:00");
        timerLbl.setStyle("-fx-text-fill:" + DLG_ACCENT + ";-fx-font-size:10;-fx-font-weight:600;");
        final int[] sec = {60};
        Timeline timer = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            sec[0]--;
            timerLbl.setText("Expires in " + sec[0] / 60 + ":" + String.format("%02d", sec[0] % 60));
            if (sec[0] <= 15) timerLbl.setStyle("-fx-text-fill:" + DLG_ERR + ";-fx-font-size:10;-fx-font-weight:600;");
            if (sec[0] <= 0) timerLbl.setText("Code expired");
        }));
        timer.setCycleCount(60);
        timer.play();

        HBox otpBox = new HBox(0);
        otpBox.setAlignment(Pos.CENTER);
        TextField[] digs = new TextField[6];

        String base = "-fx-background-color:" + DLG_INP_BG + ";-fx-text-fill:" + DLG_INP_TXT + ";"
                + "-fx-font-size:18;-fx-font-weight:bold;-fx-font-family:'Consolas';-fx-alignment:center;"
                + "-fx-pref-width:38;-fx-pref-height:42;-fx-max-width:38;-fx-max-height:42;"
                + "-fx-background-radius:0;-fx-border-color:" + DLG_INP_BD + ";-fx-border-width:1 0.5 1 0.5;";

        for (int i = 0; i < 6; i++) {
            TextField tf = new TextField();
            tf.setAlignment(Pos.CENTER);
            String s = base;
            if (i == 0)      s += "-fx-background-radius:8 0 0 8;-fx-border-radius:8 0 0 8;-fx-border-width:1 0.5 1 1;";
            else if (i == 2) s += "-fx-background-radius:0 8 8 0;-fx-border-radius:0 8 8 0;-fx-border-width:1 1 1 0.5;";
            else if (i == 3) s += "-fx-background-radius:8 0 0 8;-fx-border-radius:8 0 0 8;-fx-border-width:1 0.5 1 1;";
            else if (i == 5) s += "-fx-background-radius:0 8 8 0;-fx-border-radius:0 8 8 0;-fx-border-width:1 1 1 0.5;";
            tf.setStyle(s);

            final int idx = i;
            final String orig = tf.getStyle();
            tf.textProperty().addListener((obs, ov, nv) -> {
                if (nv.length() > 1) tf.setText(nv.substring(nv.length() - 1));
                if (!nv.isEmpty() && nv.matches("\\d") && idx < 5) digs[idx + 1].requestFocus();
                tf.setStyle(!nv.isEmpty()
                        ? orig.replace(DLG_INP_BD, DLG_ACCENT).replace(DLG_INP_BG, "#0C0C0F")
                        : orig);
            });
            tf.setOnKeyPressed(ke -> {
                if (ke.getCode() == javafx.scene.input.KeyCode.BACK_SPACE && tf.getText().isEmpty() && idx > 0)
                    digs[idx - 1].requestFocus();
            });
            digs[i] = tf;
            otpBox.getChildren().add(tf);
            if (i == 2) {
                Label dash = new Label("\u2013");
                dash.setStyle("-fx-text-fill:" + DLG_DESC + ";-fx-font-size:16;-fx-padding:0 6;");
                otpBox.getChildren().add(dash);
            }
        }

        digs[0].setOnKeyPressed(ke -> {
            if (ke.isControlDown() && ke.getCode() == javafx.scene.input.KeyCode.V) {
                try {
                    String clip = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                    if (clip != null && clip.matches("\\d{6}"))
                        for (int i = 0; i < 6; i++) digs[i].setText(String.valueOf(clip.charAt(i)));
                } catch (Exception ignored) {}
            }
        });

        Label desc = new Label("Didn\u2019t receive the code? Go back and try again.");
        desc.setStyle("-fx-text-fill:" + DLG_DESC + ";-fx-font-size:10;");
        desc.setWrapText(true);

        Label err = new Label();
        err.setStyle("-fx-text-fill:" + DLG_ERR + ";-fx-font-size:10;");
        err.setWrapText(true);
        err.setVisible(false); err.setManaged(false);

        Button verify = new Button("Verify Code");
        verify.setMaxWidth(Double.MAX_VALUE);
        verify.setStyle(dlgBtn());
        verify.setOnMouseEntered(ev -> verify.setStyle(dlgBtnH()));
        verify.setOnMouseExited(ev -> verify.setStyle(dlgBtn()));

        Button back = new Button("\u2190 Back");
        back.setMaxWidth(Double.MAX_VALUE);
        back.setStyle(dlgGhost());
        back.setOnMouseEntered(ev -> back.setStyle(dlgGhostH()));
        back.setOnMouseExited(ev -> back.setStyle(dlgGhost()));
        back.setOnAction(e -> { timer.stop(); showResetStep1(card, dialog, root, email, otp, firstName); });

        verify.setOnAction(e -> {
            StringBuilder code = new StringBuilder();
            for (TextField tf : digs) code.append(tf.getText());
            String entered = code.toString();
            if (entered.length() != 6 || !entered.matches("\\d{6}")) {
                err.setText("\u26A0 Please enter all 6 digits.");
                err.setVisible(true); err.setManaged(true); return;
            }
            if (sec[0] <= 0) {
                err.setText("\u26A0 Code expired. Go back and request a new one.");
                err.setVisible(true); err.setManaged(true); return;
            }
            verify.setDisable(true); verify.setText("Verifying\u2026");
            err.setVisible(false); err.setManaged(false);
            new Thread(() -> {
                boolean ok = serviceUser.verifyOtp(email[0], entered);
                Platform.runLater(() -> {
                    if (ok) { timer.stop(); showResetStep3(card, dialog, root, email, otp, firstName); }
                    else {
                        err.setText("\u26A0 Invalid code. Please try again.");
                        err.setVisible(true); err.setManaged(true);
                        verify.setDisable(false); verify.setText("Verify Code");
                    }
                });
            }).start();
        });

        HBox timerRow = new HBox(timerLbl);
        timerRow.setAlignment(Pos.CENTER);
        card.getChildren().addAll(title, sub, sep, timerRow, otpBox, desc, err, verify, back);
        Platform.runLater(() -> digs[0].requestFocus());
    }

    private void showResetStep3(VBox card, Stage dialog, StackPane root,
                                String[] email, String[] otp, String[] firstName) {
        card.getChildren().clear();

        Label title = new Label("Set New Password");
        title.setStyle("-fx-text-fill:" + DLG_TITLE + ";-fx-font-size:15;-fx-font-weight:bold;");
        Label sub = new Label("Choose a strong password for your account.");
        sub.setStyle("-fx-text-fill:" + DLG_SUB + ";-fx-font-size:11;");
        sub.setWrapText(true);

        Region sep = new Region();
        sep.setPrefHeight(1);
        sep.setStyle("-fx-background-color:" + DLG_BORDER + ";");

        PasswordField passIn = new PasswordField();
        passIn.setPromptText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        VBox passGrp = dlgField("New Password", passIn, "At least 6 characters.");

        PasswordField confirmIn = new PasswordField();
        confirmIn.setPromptText("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");
        VBox confirmGrp = dlgField("Confirm Password", confirmIn, null);

        // Strength bar
        HBox strBar = new HBox(3);
        strBar.setAlignment(Pos.CENTER_LEFT);
        Region[] bars = new Region[4];
        for (int i = 0; i < 4; i++) {
            bars[i] = new Region();
            bars[i].setPrefHeight(3); bars[i].setPrefWidth(50);
            bars[i].setStyle("-fx-background-color:" + DLG_BORDER + ";-fx-background-radius:2;");
            strBar.getChildren().add(bars[i]);
        }
        Label strLbl = new Label("");
        strLbl.setStyle("-fx-text-fill:" + DLG_DESC + ";-fx-font-size:9;");

        passIn.textProperty().addListener((obs, ov, nv) -> {
            int st = calcPasswordStrength(nv);
            String[] c = {DLG_ERR, "#FBBF24", DLG_ACCENT, "#22C55E"};
            String[] l = {"Weak", "Fair", "Good", "Strong"};
            for (int i = 0; i < 4; i++)
                bars[i].setStyle("-fx-background-color:" + (i < st ? c[st - 1] : DLG_BORDER) + ";-fx-background-radius:2;");
            strLbl.setText(nv.isEmpty() ? "" : l[Math.max(0, st - 1)]);
            strLbl.setStyle("-fx-text-fill:" + (nv.isEmpty() ? DLG_DESC : c[Math.max(0, st - 1)]) + ";-fx-font-size:9;");
        });

        HBox strRow = new HBox(6, strBar, strLbl);
        strRow.setAlignment(Pos.CENTER_LEFT);

        Label err = new Label();
        err.setStyle("-fx-text-fill:" + DLG_ERR + ";-fx-font-size:10;");
        err.setWrapText(true);
        err.setVisible(false); err.setManaged(false);

        Button reset = new Button("Reset Password");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setStyle(dlgBtn());
        reset.setOnMouseEntered(ev -> reset.setStyle(dlgBtnH()));
        reset.setOnMouseExited(ev -> reset.setStyle(dlgBtn()));

        reset.setOnAction(e -> {
            String p = passIn.getText(), co = confirmIn.getText();
            if (p.length() < 6) {
                err.setText("\u26A0 At least 6 characters.");
                err.setVisible(true); err.setManaged(true); return;
            }
            if (!p.equals(co)) {
                err.setText("\u26A0 Passwords do not match.");
                err.setVisible(true); err.setManaged(true); return;
            }
            reset.setDisable(true); reset.setText("Resetting\u2026");
            err.setVisible(false); err.setManaged(false);
            new Thread(() -> {
                try {
                    boolean ok = serviceUser.resetPassword(email[0], otp[0], p);
                    Platform.runLater(() -> {
                        if (ok) {
                            closeDialog(dialog, (StackPane) card.getParent());
                            showAlert("Password Reset", "Your password has been reset!\nYou can now sign in.", "success");
                        } else {
                            err.setText("\u26A0 Reset failed. Try again.");
                            err.setVisible(true); err.setManaged(true);
                            reset.setDisable(false); reset.setText("Reset Password");
                        }
                    });
                } catch (SQLException ex) {
                    Platform.runLater(() -> {
                        err.setText("\u26A0 " + ex.getMessage());
                        err.setVisible(true); err.setManaged(true);
                        reset.setDisable(false); reset.setText("Reset Password");
                    });
                }
            }).start();
        });

        card.getChildren().addAll(title, sub, sep, passGrp, strRow, confirmGrp, err, reset);
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
        boolean dark = SessionManager.getInstance().isDarkTheme();
        Stage owner = (Stage) emailField.getScene().getWindow();
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        String iconSymbol, iconColor;
        switch (type) {
            case "success": iconSymbol = "\u2713"; iconColor = "#34d399"; break;
            case "info":    iconSymbol = "\u2709"; iconColor = dark ? "#90DDF0" : "#613039"; break;
            default:        iconSymbol = "\u26A0"; iconColor = "#f87171"; break;
        }

        Label icon = new Label(iconSymbol);
        icon.setStyle("-fx-font-size: 22; -fx-text-fill: " + iconColor + "; -fx-font-weight: bold;");
        StackPane iconCircle = new StackPane(icon);
        iconCircle.setStyle("-fx-background-color: " + iconColor + "20; -fx-background-radius: 50; "
                + "-fx-min-width: 44; -fx-min-height: 44; -fx-max-width: 44; -fx-max-height: 44;");
        iconCircle.setAlignment(Pos.CENTER);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: " + (dark ? "#F0EDEE" : "#0D0A0B") + "; -fx-font-size: 14; -fx-font-weight: bold;");

        Label msgLbl = new Label(content);
        msgLbl.setStyle("-fx-text-fill: " + (dark ? "#9E9EA8" : "#4A3F42") + "; -fx-font-size: 12; -fx-line-spacing: 2;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(260);

        Button ok = new Button("Got it");
        String btnGrad1 = dark ? "#07393C" : "#613039";
        String btnGrad2 = dark ? "#2C666E" : "#8C4A56";
        String btnText = "#F0EDEE";
        String btnGradH1 = dark ? "#2C666E" : "#8C4A56";
        String btnGradH2 = dark ? "#90DDF0" : "#DE95A2";
        String btnTextH = dark ? "#0A090C" : "#FFFFFF";
        String btnN = "-fx-background-color: linear-gradient(to right," + btnGrad1 + "," + btnGrad2 + "); -fx-text-fill:" + btnText + "; "
                + "-fx-font-size:12; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:7 28; -fx-cursor:hand;";
        String btnH = "-fx-background-color: linear-gradient(to right," + btnGradH1 + "," + btnGradH2 + "); -fx-text-fill:" + btnTextH + "; "
                + "-fx-font-size:12; -fx-font-weight:600; -fx-background-radius:8; -fx-padding:7 28; -fx-cursor:hand;";
        ok.setStyle(btnN);
        ok.setOnMouseEntered(e -> ok.setStyle(btnH));
        ok.setOnMouseExited(e -> ok.setStyle(btnN));

        String cardBg = dark ? "#14131A" : "#FFFFFF";
        String cardBorder = dark ? "#1C1B22" : "#E0D6D8";
        String cardShadow = dark ? "rgba(0,0,0,0.5)" : "rgba(97,48,57,0.15)";
        VBox card = new VBox(10, iconCircle, titleLbl, msgLbl, ok);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20, 24, 16, 24));
        card.setMaxWidth(300);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setStyle("-fx-background-color: " + cardBg + "; -fx-background-radius: 14; "
                + "-fx-border-color: " + cardBorder + "; -fx-border-radius: 14; -fx-border-width: 1; "
                + "-fx-effect: dropshadow(gaussian, " + cardShadow + ", 24, 0, 0, 8);");

        String overlayBg = dark ? "rgba(0,0,0,0.55)" : "rgba(0,0,0,0.3)";
        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: " + overlayBg + ";");
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
