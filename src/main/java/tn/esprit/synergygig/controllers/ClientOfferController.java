package tn.esprit.synergygig.controllers;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.scene.shape.Circle;
import javafx.util.Duration;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferType;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.services.OfferService;
import javafx.animation.ScaleTransition;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.ScaleTransition;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;


import java.sql.SQLException;

public class ClientOfferController {

    @FXML
    private Pane animatedBackground;

    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private ComboBox<OfferType> typeBox;

    @FXML
    private TableView<Offer> offerTable;
    @FXML
    private TableColumn<Offer, String> colTitle;
    @FXML
    private TableColumn<Offer, String> colDescription;
    @FXML
    private TableColumn<Offer, OfferType> colType;
    @FXML
    private TableColumn<Offer, OfferStatus> colStatus;
    @FXML
    private ImageView logoImage;

    private final OfferService service = new OfferService();
    private Offer selectedOffer;

    @FXML
    public void initialize() {

        typeBox.getItems().setAll(OfferType.values());

        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        offerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        loadOffers();

        offerTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    selectedOffer = newSelection;
                    fillForm(newSelection);
                }
        );

        createStars();

        // Charger logo
        logoImage.setImage(new Image(
                getClass().getResourceAsStream("/tn/esprit/synergygig/gui/images/anaslogo1.png")
        ));

        animateLogo();
    }

    private int currentUserId = 1; // plus tard dynamique via login

    private void loadOffers() {

        try {
            offerTable.getItems().setAll(
                    service.getOffersByUser(currentUserId)
            );
        } catch (SQLException e) {
            showError("Erreur lors du chargement des offres");
            e.printStackTrace();
        }
    }
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Erreur");
        alert.setContentText(message);
        alert.show();
    }


    private void createStars() {



        for (int i = 0; i < 700; i++) {

            Circle star = new Circle(Math.random() * 2);

            star.setTranslateX(Math.random() * 1600);
            star.setTranslateY(Math.random() * 900);

            if (Math.random() > 0.5) {
                star.setStyle("-fx-fill: #396afc;");
            } else {
                star.setStyle("-fx-fill: rgba(255,255,255,0.8);");
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
    private void fillForm(Offer offer) {

        if (offer == null) {
            return;
        }

        titleField.setText(offer.getTitle());
        descriptionArea.setText(offer.getDescription());
        typeBox.setValue(offer.getType());
    }
    @FXML
    private void addOffer() {

        if (titleField.getText().isBlank() ||
                descriptionArea.getText().isBlank() ||
                typeBox.getValue() == null) {

            showError("All fields are required");
            return;
        }

        try {
            Offer offer = new Offer(
                    titleField.getText(),
                    descriptionArea.getText(),
                    typeBox.getValue(),
                    currentUserId,
                    null
            );

            service.addOffer(offer);
            loadOffers();
            clearForm();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }
    @FXML
    private void updateOffer() {

        if (selectedOffer == null) {
            showError("Select an offer first");
            return;
        }

        try {
            selectedOffer.setTitle(titleField.getText());
            selectedOffer.setDescription(descriptionArea.getText());
            selectedOffer.setType(typeBox.getValue());

            service.updateOffer(selectedOffer);
            loadOffers();

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }
    @FXML
    private void deleteOffer() {

        if (selectedOffer == null) {
            showError("Select an offer first");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Delete Offer?");
        confirm.setContentText("Are you sure?");

        if (confirm.showAndWait().get() == ButtonType.OK) {
            try {
                service.deleteOffer(selectedOffer);
                loadOffers();
                clearForm();
            } catch (Exception e) {
                showError(e.getMessage());
            }
        }
    }
    private void clearForm() {
        titleField.clear();
        descriptionArea.clear();
        typeBox.getSelectionModel().clearSelection();
        selectedOffer = null;
    }
    private void animateLogo() {

        ScaleTransition scale = new ScaleTransition(Duration.seconds(2), logoImage);
        scale.setFromX(1);
        scale.setFromY(1);
        scale.setToX(1.1);
        scale.setToY(1.1);
        scale.setAutoReverse(true);
        scale.setCycleCount(Animation.INDEFINITE);
        scale.play();
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#6a11cb"));
        glow.setRadius(30);
        logoImage.setEffect(glow);

    }


}
