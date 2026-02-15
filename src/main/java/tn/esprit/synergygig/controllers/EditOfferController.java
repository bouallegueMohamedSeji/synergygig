package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;
import tn.esprit.synergygig.services.OfferService;

public class EditOfferController {

    @FXML private TextField titleField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<OfferType> typeBox;
    @FXML private ComboBox<OfferStatus> statusBox;

    private Offer offer;
    private final OfferService service = new OfferService();

    public void setOffer(Offer offer) {
        this.offer = offer;

        titleField.setText(offer.getTitle());
        descriptionField.setText(offer.getDescription());

        typeBox.getItems().setAll(OfferType.values());
        statusBox.getItems().setAll(OfferStatus.values());

        typeBox.setValue(offer.getType());
        statusBox.setValue(offer.getStatus());
    }

    @FXML
    private void handleSave() {
        try {
            offer.setTitle(titleField.getText());
            offer.setDescription(descriptionField.getText());
            offer.setType(typeBox.getValue());
            offer.setStatus(statusBox.getValue());

            service.updateOffer(offer);

            close();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

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
        alert.setContentText(msg);
        alert.show();
    }
}
