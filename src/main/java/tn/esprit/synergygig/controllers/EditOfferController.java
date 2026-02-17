package tn.esprit.synergygig.controllers;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;
import tn.esprit.synergygig.services.OfferService;

public class EditOfferController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<OfferType> typeBox;
    @FXML private ComboBox<OfferStatus> statusBox;
    @FXML private Pane animatedBackground;

    private Offer offer;
    private final OfferService service = new OfferService();

    // ===============================
    // INITIALIZE
    // ===============================
    @FXML
    public void initialize() {
        createStars();

        typeBox.getItems().setAll(OfferType.values());
        statusBox.getItems().setAll(OfferStatus.values());
    }

    // ===============================
    // SET OFFER (appelé depuis bouton Edit)
    // ===============================
    public void setOffer(Offer offer) {
        this.offer = offer;

        titleField.setText(offer.getTitle());
        descriptionField.setText(offer.getDescription());
        typeBox.setValue(offer.getType());
        statusBox.setValue(offer.getStatus());
    }

    // ===============================
    // SAVE
    // ===============================
    @FXML
    private void handleSave() {

        if (!validateForm()) {
            return;
        }

        try {
            offer.setTitle(titleField.getText().trim());
            offer.setDescription(descriptionField.getText().trim());
            offer.setType(typeBox.getValue());
            offer.setStatus(statusBox.getValue());

            service.updateOffer(offer);

            close();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    // ===============================
    // VALIDATION ADMIN
    // ===============================
    private boolean validateForm() {

        if (titleField.getText() == null || titleField.getText().trim().isEmpty()) {
            showError("Title is required");
            return false;
        }

        if (titleField.getText().length() < 3) {
            showError("Title must contain at least 3 characters");
            return false;
        }

        if (descriptionField.getText() == null || descriptionField.getText().trim().isEmpty()) {
            showError("Description is required");
            return false;
        }

        if (typeBox.getValue() == null) {
            showError("Please select a type");
            return false;
        }

        if (statusBox.getValue() == null) {
            showError("Please select a status");
            return false;
        }

        return true;
    }

    // ===============================
    // CANCEL
    // ===============================
    @FXML
    private void handleCancel() {
        close();
    }

    private void close() {
        Stage stage = (Stage) titleField.getScene().getWindow();
        stage.close();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Validation Error");
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // ===============================
    // GALAXY BACKGROUND
    // ===============================
    private void createStars() {

        for (int i = 0; i < 300; i++) {

            Circle star = new Circle(Math.random() * 2 + 0.5);

            star.setLayoutX(Math.random() * 800);
            star.setLayoutY(Math.random() * 600);

            if (Math.random() > 0.5) {
                star.setStyle("-fx-fill: rgba(255,255,255,0.8);");
            } else {
                star.setStyle("-fx-fill: #396afc;");
            }

            FadeTransition fade = new FadeTransition(
                    Duration.seconds(2 + Math.random() * 3),
                    star
            );

            fade.setFromValue(0.2);
            fade.setToValue(1);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }
}
