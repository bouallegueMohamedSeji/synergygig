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
import javafx.stage.FileChooser;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
    private TableColumn<Offer, String> colImage;
    @FXML
    private ImageView logoImage;
    @FXML
    private ImageView previewImage;

    private String uploadedImageName;


    private final OfferService service = new OfferService();
    private Offer selectedOffer;

    @FXML
    public void initialize() {

        typeBox.getItems().setAll(OfferType.values());

        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colImage.setCellValueFactory(new PropertyValueFactory<>("imageUrl"));

        colImage.setCellValueFactory(new PropertyValueFactory<>("imageUrl"));

        colImage.setCellFactory(col -> new TableCell<Offer, String>() {

            private final ImageView img = new ImageView();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                } else {

                    File file = new File("uploads/" + item);

                    if (file.exists()) {
                        img.setImage(new Image(file.toURI().toString()));
                        img.setFitHeight(40);
                        img.setPreserveRatio(true);
                        setGraphic(img);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });


        offerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        loadOffers();
        createStars();

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
        alert.setHeaderText("Validation Error");
        alert.setContentText(message);
        alert.showAndWait();
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

        if (!validateForm()) {
            return;
        }

        try {

            Offer offer = new Offer(
                    titleField.getText(),
                    descriptionArea.getText(),
                    typeBox.getValue(),
                    currentUserId,
                    uploadedImageName   // 🔥 IMPORTANT
            );


            service.addOffer(offer);
            loadOffers();
            clearForm();

            showSuccess("Offer added successfully");

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void updateOffer() {

        if (selectedOffer == null) {
            showError("Please select an offer to update");
            return;
        }

        if (!validateForm()) {
            return;
        }

        try {

            selectedOffer.setTitle(titleField.getText());
            selectedOffer.setDescription(descriptionArea.getText());
            selectedOffer.setType(typeBox.getValue());

            // 🔥 AJOUT IMAGE UNIQUEMENT SI NOUVELLE IMAGE
            if (uploadedImageName != null) {
                selectedOffer.setImageUrl(uploadedImageName);
            }

            service.updateOffer(selectedOffer);

            loadOffers();
            clearForm();

            showSuccess("Offer updated successfully");

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    @FXML
    private void deleteOffer() {

        if (selectedOffer == null) {
            showError("Please select an offer to delete");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Confirm deletion");
        confirm.setContentText("Are you sure you want to delete this offer?");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    service.deleteOffer(selectedOffer);
                    loadOffers();
                    clearForm();
                    showSuccess("Offer deleted successfully");
                } catch (Exception e) {
                    showError(e.getMessage());
                }
            }
        });
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
    private boolean validateForm() {

        String title = titleField.getText();
        String description = descriptionArea.getText();
        OfferType type = typeBox.getValue();

        if (title == null || title.trim().isEmpty()) {
            showError("Title is required");
            titleField.requestFocus();
            return false;
        }

        if (title.length() < 3) {
            showError("Title must contain at least 3 characters");
            titleField.requestFocus();
            return false;
        }

        if (description == null || description.trim().isEmpty()) {
            showError("Description is required");
            descriptionArea.requestFocus();
            return false;
        }

        if (description.length() < 5) {
            showError("Description must contain at least 5 characters");
            descriptionArea.requestFocus();
            return false;
        }

        if (type == null) {
            showError("Please select a type");
            return false;
        }

        return true;
    }
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    @FXML
    private void handleUploadImage() {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une image");

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(titleField.getScene().getWindow());

        if (file != null) {
            try {

                File uploadsDir = new File("uploads");
                if (!uploadsDir.exists()) {
                    uploadsDir.mkdir();
                }

                uploadedImageName = System.currentTimeMillis() + "_" + file.getName();

                File destination = new File("uploads/" + uploadedImageName);

                Files.copy(file.toPath(), destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);

                previewImage.setImage(new Image(destination.toURI().toString()));

            } catch (IOException e) {
                showError("Erreur upload image");
                e.printStackTrace();
            }
        }
    }





}
