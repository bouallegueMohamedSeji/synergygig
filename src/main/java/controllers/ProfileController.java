package controllers;

import entities.User;
import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import services.ServiceUser;
import utils.FaceRecognitionUtil;
import utils.SessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;

public class ProfileController {

    // Profile identity card
    @FXML private Label profileAvatarInitial;
    @FXML private ImageView profileAvatarImage;
    @FXML private Label profileFullName;
    @FXML private Label profileEmail;
    @FXML private Label profileRoleBadge;

    // Account details — view mode
    @FXML private VBox viewModePane;
    @FXML private Label detailFirstName;
    @FXML private Label detailLastName;
    @FXML private Label detailEmail;
    @FXML private Label detailRole;
    @FXML private Label detailCreatedAt;

    // Account details — edit mode
    @FXML private VBox editModePane;
    @FXML private Button editToggleBtn;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private TextField roleField;

    // Validation error labels
    @FXML private Label firstNameError;
    @FXML private Label lastNameError;
    @FXML private Label emailError;

    // Buttons
    @FXML private Button cancelEditBtn;
    @FXML private Button saveEditBtn;
    @FXML private Label statusLabel;

    // Face ID
    @FXML private Label faceIdStatusLabel;
    @FXML private Label faceIdDescLabel;
    @FXML private Label faceIdBadge;
    @FXML private Button enrollFaceBtn;
    @FXML private Button removeFaceBtn;
    @FXML private Label faceStatusMsg;

    private boolean isEditMode = false;
    private ServiceUser serviceUser = new ServiceUser();

    /** Directory where avatar images are stored */
    private static final String AVATARS_DIR = System.getProperty("user.home") + File.separator + ".synergygig_avatars";

    @FXML
    public void initialize() {
        new File(AVATARS_DIR).mkdirs();

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            populateProfile(currentUser);
        }

        // Live validation listeners
        firstNameField.textProperty().addListener((o, ov, nv) -> clearFieldError(firstNameField, firstNameError));
        lastNameField.textProperty().addListener((o, ov, nv) -> clearFieldError(lastNameField, lastNameError));
        emailField.textProperty().addListener((o, ov, nv) -> clearFieldError(emailField, emailError));
    }

    private void populateProfile(User user) {
        // Profile identity card
        String initial = user.getFirstName().substring(0, 1).toUpperCase();
        profileAvatarInitial.setText(initial);
        profileFullName.setText(user.getFirstName() + " " + user.getLastName());
        profileEmail.setText(user.getEmail());
        profileRoleBadge.setText(user.getRole().replace("_", " "));

        loadAvatar(user);

        // View mode details
        detailFirstName.setText(user.getFirstName());
        detailLastName.setText(user.getLastName());
        detailEmail.setText(user.getEmail());
        detailRole.setText(user.getRole().replace("_", " "));
        if (user.getCreatedAt() != null) {
            detailCreatedAt.setText(new SimpleDateFormat("MMMM dd, yyyy").format(user.getCreatedAt()));
        } else {
            detailCreatedAt.setText("N/A");
        }

        // Pre-fill edit fields
        firstNameField.setText(user.getFirstName());
        lastNameField.setText(user.getLastName());
        emailField.setText(user.getEmail());
        roleField.setText(user.getRole().replace("_", " "));

        // Update face ID status
        updateFaceIdStatus(user);
    }

    private void updateFaceIdStatus(User user) {
        if (user.hasFaceEnrolled()) {
            faceIdStatusLabel.setText("Face ID enrolled");
            faceIdDescLabel.setText("Your face is registered for secure login");
            faceIdBadge.setText("ON");
            faceIdBadge.getStyleClass().setAll("face-id-badge-on");
            enrollFaceBtn.setText("\uD83D\uDCF7  Re-enroll Face");
            removeFaceBtn.setVisible(true);
            removeFaceBtn.setManaged(true);
        } else {
            faceIdStatusLabel.setText("Face ID not enrolled");
            faceIdDescLabel.setText("Register your face for passwordless login");
            faceIdBadge.setText("OFF");
            faceIdBadge.getStyleClass().setAll("face-id-badge-off");
            enrollFaceBtn.setText("\uD83D\uDCF7  Enroll Face");
            removeFaceBtn.setVisible(false);
            removeFaceBtn.setManaged(false);
        }
    }

    // ========== EDIT MODE TOGGLE ==========

    @FXML
    private void handleToggleEdit() {
        if (isEditMode) {
            switchToViewMode();
        } else {
            switchToEditMode();
        }
    }

    private void switchToEditMode() {
        isEditMode = true;

        // Animate pen icon rotation
        RotateTransition rotate = new RotateTransition(Duration.millis(300), editToggleBtn);
        rotate.setByAngle(180);
        rotate.play();
        editToggleBtn.setText("✕");

        // Pre-fill fields with current values
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            firstNameField.setText(user.getFirstName());
            lastNameField.setText(user.getLastName());
            emailField.setText(user.getEmail());
            passwordField.clear();
            roleField.setText(user.getRole().replace("_", " "));
        }

        // Clear any previous errors
        clearAllErrors();
        hideStatus();

        // Fade out view, fade in edit
        fadeSwitch(viewModePane, editModePane);
    }

    private void switchToViewMode() {
        isEditMode = false;

        // Animate pen icon back
        RotateTransition rotate = new RotateTransition(Duration.millis(300), editToggleBtn);
        rotate.setByAngle(-180);
        rotate.play();
        editToggleBtn.setText("✏");

        clearAllErrors();
        hideStatus();

        // Fade out edit, fade in view
        fadeSwitch(editModePane, viewModePane);
    }

    @FXML
    private void handleCancelEdit() {
        switchToViewMode();
    }

    private void fadeSwitch(VBox fadeOut, VBox fadeIn) {
        FadeTransition out = new FadeTransition(Duration.millis(150), fadeOut);
        out.setFromValue(1.0);
        out.setToValue(0.0);
        out.setOnFinished(e -> {
            fadeOut.setVisible(false);
            fadeOut.setManaged(false);
            fadeIn.setVisible(true);
            fadeIn.setManaged(true);
            fadeIn.setOpacity(0);
            FadeTransition in = new FadeTransition(Duration.millis(200), fadeIn);
            in.setFromValue(0.0);
            in.setToValue(1.0);
            in.play();
        });
        out.play();
    }

    // ========== VALIDATION ==========

    private boolean validateFields() {
        boolean valid = true;
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();

        if (firstName.isEmpty()) {
            showFieldError(firstNameField, firstNameError, "First name is required.");
            valid = false;
        }
        if (lastName.isEmpty()) {
            showFieldError(lastNameField, lastNameError, "Last name is required.");
            valid = false;
        }
        if (email.isEmpty()) {
            showFieldError(emailField, emailError, "Email address is required.");
            valid = false;
        } else if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            showFieldError(emailField, emailError, "Please enter a valid email address.");
            valid = false;
        }

        return valid;
    }

    private void showFieldError(TextField field, Label errorLabel, String message) {
        field.getStyleClass().remove("field-input-invalid");
        if (!field.getStyleClass().contains("field-input-invalid")) {
            field.getStyleClass().add("field-input-invalid");
        }
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearFieldError(TextField field, Label errorLabel) {
        field.getStyleClass().remove("field-input-invalid");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        errorLabel.setText("");
    }

    private void clearAllErrors() {
        clearFieldError(firstNameField, firstNameError);
        clearFieldError(lastNameField, lastNameError);
        clearFieldError(emailField, emailError);
    }

    /**
     * Load and display the avatar image if the user has one.
     * Falls back to initial letter if no avatar is set.
     */
    private void loadAvatar(User user) {
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            File avatarFile = new File(user.getAvatarPath());
            if (avatarFile.exists()) {
                Image image = new Image(avatarFile.toURI().toString(), 80, 80, false, true);
                profileAvatarImage.setImage(image);
                profileAvatarImage.setVisible(true);
                profileAvatarImage.setManaged(true);
                // Hide the initial since we have an image
                profileAvatarInitial.getParent().setVisible(false);
                profileAvatarInitial.getParent().setManaged(false);
                return;
            }
        }
        // Show fallback initial
        profileAvatarImage.setVisible(false);
        profileAvatarImage.setManaged(false);
        profileAvatarInitial.getParent().setVisible(true);
        profileAvatarInitial.getParent().setManaged(true);
    }

    /**
     * Handle avatar upload — opens a file chooser, then a crop dialog.
     */
    @FXML
    private void handleUploadAvatar() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Avatar Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        File selectedFile = fileChooser.showOpenDialog(profileAvatarImage.getScene().getWindow());
        if (selectedFile == null) return;

        Image originalImage = new Image(selectedFile.toURI().toString());
        showCropDialog(originalImage, currentUser, getFileExtension(selectedFile.getName()));
    }

    /**
     * Show a modal crop dialog with zoom in/out. The crop circle is fixed in the center;
     * the user pans and zooms the image underneath to frame their avatar.
     */
    private void showCropDialog(Image image, User user, String extension) {
        Stage cropStage = new Stage();
        cropStage.initModality(Modality.APPLICATION_MODAL);
        cropStage.initOwner(profileAvatarImage.getScene().getWindow());
        cropStage.setTitle("Crop Avatar");

        final double imgW = image.getWidth();
        final double imgH = image.getHeight();

        // Fixed crop area size
        final double CROP_SIZE = 280;
        final double VIEW_SIZE = 320;

        // ImageView — zoomable and pannable
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);

        // Initial fit: scale so the smaller dimension fills the crop circle
        double initialScale = Math.max(CROP_SIZE / imgW, CROP_SIZE / imgH);
        final double[] currentScale = {initialScale};
        imageView.setFitWidth(imgW * initialScale);
        imageView.setFitHeight(imgH * initialScale);

        // Center the image
        final double[] imgOffset = {
            (VIEW_SIZE - imgW * initialScale) / 2,
            (VIEW_SIZE - imgH * initialScale) / 2
        };
        imageView.setLayoutX(imgOffset[0]);
        imageView.setLayoutY(imgOffset[1]);

        // Dark overlay with circular hole in center
        final double circleX = VIEW_SIZE / 2;
        final double circleY = VIEW_SIZE / 2;
        final double circleR = CROP_SIZE / 2;

        Pane overlay = new Pane();
        overlay.setPrefSize(VIEW_SIZE, VIEW_SIZE);
        overlay.setMouseTransparent(true);
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.55);");
        Rectangle fullRect = new Rectangle(VIEW_SIZE, VIEW_SIZE);
        Circle hole = new Circle(circleX, circleY, circleR);
        overlay.setClip(javafx.scene.shape.Shape.subtract(fullRect, hole));

        // Circle border indicator
        Circle circleBorder = new Circle(circleX, circleY, circleR);
        circleBorder.setFill(Color.TRANSPARENT);
        circleBorder.setStroke(Color.web("#90DDF0"));
        circleBorder.setStrokeWidth(2);
        circleBorder.setMouseTransparent(true);

        // Clip the entire view area
        Rectangle viewClip = new Rectangle(VIEW_SIZE, VIEW_SIZE);
        viewClip.setArcWidth(12);
        viewClip.setArcHeight(12);

        Pane imagePane = new Pane(imageView, overlay, circleBorder);
        imagePane.setPrefSize(VIEW_SIZE, VIEW_SIZE);
        imagePane.setMaxSize(VIEW_SIZE, VIEW_SIZE);
        imagePane.setMinSize(VIEW_SIZE, VIEW_SIZE);
        imagePane.setClip(viewClip);
        imagePane.setCursor(Cursor.OPEN_HAND);
        imagePane.setStyle("-fx-background-color: #000000;");

        // Pan logic
        final double[] dragStart = new double[4]; // startSceneX, startSceneY, startLayoutX, startLayoutY
        imagePane.setOnMousePressed(e -> {
            dragStart[0] = e.getSceneX();
            dragStart[1] = e.getSceneY();
            dragStart[2] = imageView.getLayoutX();
            dragStart[3] = imageView.getLayoutY();
            imagePane.setCursor(Cursor.CLOSED_HAND);
        });
        imagePane.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - dragStart[0];
            double dy = e.getSceneY() - dragStart[1];
            imageView.setLayoutX(dragStart[2] + dx);
            imageView.setLayoutY(dragStart[3] + dy);
        });
        imagePane.setOnMouseReleased(e -> imagePane.setCursor(Cursor.OPEN_HAND));

        // Zoom slider
        Slider zoomSlider = new Slider(0.5, 4.0, 1.0);
        zoomSlider.setPrefWidth(VIEW_SIZE - 80);
        zoomSlider.setStyle("-fx-control-inner-background: #1C1B22;");

        Label zoomOutLabel = new Label("−");
        zoomOutLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 20; -fx-font-weight: bold; -fx-cursor: hand;");
        zoomOutLabel.setOnMouseClicked(e -> zoomSlider.setValue(Math.max(zoomSlider.getMin(), zoomSlider.getValue() - 0.2)));

        Label zoomInLabel = new Label("+");
        zoomInLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 20; -fx-font-weight: bold; -fx-cursor: hand;");
        zoomInLabel.setOnMouseClicked(e -> zoomSlider.setValue(Math.min(zoomSlider.getMax(), zoomSlider.getValue() + 0.2)));

        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double zoomFactor = newVal.doubleValue();
            double newFitW = imgW * initialScale * zoomFactor;
            double newFitH = imgH * initialScale * zoomFactor;

            // Keep image centered while zooming
            double centerX = imageView.getLayoutX() + imageView.getFitWidth() / 2;
            double centerY = imageView.getLayoutY() + imageView.getFitHeight() / 2;

            imageView.setFitWidth(newFitW);
            imageView.setFitHeight(newFitH);

            imageView.setLayoutX(centerX - newFitW / 2);
            imageView.setLayoutY(centerY - newFitH / 2);

            currentScale[0] = initialScale * zoomFactor;
        });

        // Scroll to zoom
        imagePane.setOnScroll(e -> {
            double delta = e.getDeltaY() > 0 ? 0.1 : -0.1;
            zoomSlider.setValue(Math.max(zoomSlider.getMin(), Math.min(zoomSlider.getMax(), zoomSlider.getValue() + delta)));
        });

        HBox zoomBar = new HBox(8, zoomOutLabel, zoomSlider, zoomInLabel);
        zoomBar.setAlignment(Pos.CENTER);

        // Buttons
        Button btnCrop = new Button("Crop & Save");
        btnCrop.getStyleClass().add("btn-primary");
        btnCrop.setPrefWidth(140);

        Button btnCancel = new Button("Cancel");
        btnCancel.getStyleClass().add("btn-secondary");
        btnCancel.setPrefWidth(100);
        btnCancel.setOnAction(e -> cropStage.close());

        btnCrop.setOnAction(e -> {
            try {
                // Calculate the crop region in original image coordinates
                // The circle center in the pane is (circleX, circleY) with radius circleR
                // The image is at (layoutX, layoutY) with displayed size (fitWidth, fitHeight)
                double fitW = imageView.getFitWidth();
                double fitH = imageView.getFitHeight();
                double layoutX = imageView.getLayoutX();
                double layoutY = imageView.getLayoutY();

                // Map circle bounds to image pixel coordinates
                double scaleX = imgW / fitW;
                double scaleY = imgH / fitH;
                double cropX = (circleX - circleR - layoutX) * scaleX;
                double cropY = (circleY - circleR - layoutY) * scaleY;
                double cropW = (CROP_SIZE) * scaleX;
                double cropH = (CROP_SIZE) * scaleY;

                // Clamp
                cropX = Math.max(0, cropX);
                cropY = Math.max(0, cropY);
                cropW = Math.min(cropW, imgW - cropX);
                cropH = Math.min(cropH, imgH - cropY);

                // Crop using viewport
                ImageView croppedView = new ImageView(image);
                croppedView.setViewport(new Rectangle2D(cropX, cropY, cropW, cropH));
                croppedView.setFitWidth(256);
                croppedView.setFitHeight(256);
                croppedView.setPreserveRatio(false);

                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                WritableImage croppedImage = croppedView.snapshot(params, null);

                // Save to disk
                String avatarFileName = "avatar_" + user.getId() + ".png";
                Path destination = Paths.get(AVATARS_DIR, avatarFileName);
                BufferedImage buffered = SwingFXUtils.fromFXImage(croppedImage, null);
                ImageIO.write(buffered, "png", destination.toFile());

                String avatarPath = destination.toString();

                // Update DB
                serviceUser.updateAvatar(user.getId(), avatarPath);
                user.setAvatarPath(avatarPath);
                SessionManager.getInstance().setCurrentUser(user);
                loadAvatar(user);

                // Notify dashboard to update instantly
                SessionManager.getInstance().fireAvatarChanged();

                showStatus("Avatar updated successfully!", false);
                cropStage.close();

            } catch (IOException | SQLException ex) {
                showStatus("Error saving avatar: " + ex.getMessage(), true);
                cropStage.close();
            }
        });

        HBox buttonBar = new HBox(12, btnCancel, btnCrop);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);
        buttonBar.setPadding(new Insets(8, 0, 0, 0));

        // Title
        Label title = new Label("Crop Avatar");
        title.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 16; -fx-font-weight: bold;");
        Label subtitle = new Label("Drag to pan, scroll or use slider to zoom");
        subtitle.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 12;");

        VBox header = new VBox(2, title, subtitle);

        VBox root = new VBox(16, header, imagePane, zoomBar, buttonBar);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0F0E11; -fx-border-color: #1C1B22; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1;");
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, Color.TRANSPARENT);
        cropStage.initStyle(StageStyle.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        // Apply light theme if currently active
        String lightThemePath = "/css/light-theme.css";
        if (profileAvatarImage.getScene() != null) {
            for (String ss : profileAvatarImage.getScene().getStylesheets()) {
                if (ss.contains("light-theme")) {
                    scene.getStylesheets().add(getClass().getResource(lightThemePath).toExternalForm());
                    root.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0D6D8; -fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1;");
                    title.setStyle("-fx-text-fill: #0D0A0B; -fx-font-size: 16; -fx-font-weight: bold;");
                    subtitle.setStyle("-fx-text-fill: #8A7C7F; -fx-font-size: 12;");
                    zoomOutLabel.setStyle("-fx-text-fill: #613039; -fx-font-size: 20; -fx-font-weight: bold; -fx-cursor: hand;");
                    zoomInLabel.setStyle("-fx-text-fill: #613039; -fx-font-size: 20; -fx-font-weight: bold; -fx-cursor: hand;");
                    break;
                }
            }
        }

        cropStage.setScene(scene);
        cropStage.showAndWait();
    }

    /**
     * Create a shape that covers the full area except the crop circle (inverted clip for the dark overlay).
     */
    private javafx.scene.shape.Shape createInvertedClip(double w, double h, Rectangle cropRect) {
        Rectangle fullRect = new Rectangle(w, h);
        double cx = cropRect.getLayoutX() + cropRect.getWidth() / 2;
        double cy = cropRect.getLayoutY() + cropRect.getHeight() / 2;
        Circle hole = new Circle(cx, cy, cropRect.getWidth() / 2);
        return javafx.scene.shape.Shape.subtract(fullRect, hole);
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex >= 0) ? fileName.substring(dotIndex + 1).toLowerCase() : "png";
    }

    @FXML
    private void handleSave() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        // Validate
        if (!validateFields()) return;

        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        try {
            // Check if email changed and already exists
            if (!email.equals(currentUser.getEmail()) && serviceUser.emailExists(email)) {
                showFieldError(emailField, emailError, "This email is already in use.");
                return;
            }

            currentUser.setFirstName(firstName);
            currentUser.setLastName(lastName);
            currentUser.setEmail(email);
            if (!password.isEmpty()) {
                currentUser.setPassword(password);
            }

            serviceUser.modifier(currentUser);

            // Update session
            SessionManager.getInstance().setCurrentUser(currentUser);

            // Refresh display
            populateProfile(currentUser);

            // Switch back to view mode with success
            switchToViewMode();
            showStatus("Profile updated successfully!", false);

        } catch (SQLException e) {
            showStatus("Error: " + e.getMessage(), true);
        }
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.getStyleClass().removeAll("error-label", "success-label");
        statusLabel.getStyleClass().add(isError ? "error-label" : "success-label");
    }

    private void hideStatus() {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        statusLabel.setText("");
    }

    // ========== FACE RECOGNITION ==========

    @FXML
    private void handleEnrollFace() {
        showFaceStatus("Opening camera for face enrollment...", false);
        enrollFaceBtn.setDisable(true);

        new Thread(() -> {
            com.google.gson.JsonObject result = FaceRecognitionUtil.enrollFace();

            javafx.application.Platform.runLater(() -> {
                enrollFaceBtn.setDisable(false);

                if (result.get("success").getAsBoolean()) {
                    // Save the encoding to DB
                    String encodingJson = result.get("encoding").toString();
                    User currentUser = SessionManager.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        try {
                            FaceRecognitionUtil.saveEncoding(currentUser.getId(), encodingJson);
                            currentUser.setFaceEncoding(encodingJson);
                            updateFaceIdStatus(currentUser);
                            showFaceStatus("Face enrolled successfully! You can now use Face Login.", false);
                        } catch (java.sql.SQLException e) {
                            showFaceStatus("Failed to save face data: " + e.getMessage(), true);
                        }
                    }
                } else {
                    String error = result.has("error") ? result.get("error").getAsString() : "Unknown error";
                    showFaceStatus(error, true);
                }
            });
        }).start();
    }

    @FXML
    private void handleRemoveFace() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        try {
            FaceRecognitionUtil.saveEncoding(currentUser.getId(), null);
            currentUser.setFaceEncoding(null);
            updateFaceIdStatus(currentUser);
            showFaceStatus("Face data removed.", false);
        } catch (java.sql.SQLException e) {
            showFaceStatus("Failed to remove face data: " + e.getMessage(), true);
        }
    }

    @FXML
    private void handleTestFaceSetup() {
        showFaceStatus("Testing face recognition setup...", false);

        new Thread(() -> {
            com.google.gson.JsonObject result = FaceRecognitionUtil.testSetup();

            javafx.application.Platform.runLater(() -> {
                if (result.get("success").getAsBoolean()) {
                    String msg = result.has("message") ? result.get("message").getAsString() : "Setup OK";
                    showFaceStatus(msg, false);
                } else {
                    String error = result.has("error") ? result.get("error").getAsString() : "Setup failed";
                    showFaceStatus(error, true);
                }
            });
        }).start();
    }

    private void showFaceStatus(String msg, boolean isError) {
        faceStatusMsg.setText(msg);
        faceStatusMsg.setVisible(true);
        faceStatusMsg.setManaged(true);
        faceStatusMsg.getStyleClass().removeAll("error-label", "success-label");
        faceStatusMsg.getStyleClass().add(isError ? "error-label" : "success-label");
    }
}
