package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Modality;


import javafx.stage.Stage;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;
import tn.esprit.synergygig.services.OfferService;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javafx.scene.image.Image;
import tn.esprit.synergygig.entities.Offer;
import javafx.scene.shape.Circle;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;




import java.sql.SQLException;


public class OfferController {

    // ===== TABLE =====
    @FXML private TableView<Offer> offerTable;
    @FXML private TableColumn<Offer, String> colTitle;
    @FXML private TableColumn<Offer, OfferType> colType;
    @FXML private TableColumn<Offer, OfferStatus> colStatus;
    @FXML
    private TableColumn<Offer, Double> colAmount;

    @FXML private TableColumn<Offer, Void> colActions;
    @FXML
    private TableColumn<Offer, String> colDescription;

    @FXML
    private ImageView bgImage;
    // ===== FORM =====
    @FXML private TextField titleField;
    @FXML private TextField descriptionField;
    @FXML private ComboBox<OfferType> typeBox;
    @FXML private ImageView previewImage;
    @FXML
    private Pane animatedBackground;
    @FXML
    private TextField amountField;



    private String selectedImageName;

    // ===== SERVICE =====
    private final OfferService service = new OfferService();
    private final ObservableList<Offer> offerList = FXCollections.observableArrayList();

    // ===== INITIALIZATION =====
    @FXML
    public void initialize() {

        // Table columns mapping
        colTitle.setCellValueFactory(new PropertyValueFactory<>("title"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colAmount.setCellValueFactory(
                new PropertyValueFactory<>("amount")
        );
        colActions.setPrefWidth(300);
        colActions.setMinWidth(300);
        offerTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(OfferStatus status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                Label badge = new Label(status.name());
                badge.getStyleClass().add("badge");

                switch (status) {
                    case PUBLISHED -> badge.getStyleClass().add("badge-published");
                    case DRAFT -> badge.getStyleClass().add("badge-draft");
                    case IN_PROGRESS -> badge.getStyleClass().add("badge-progress");
                    case CANCELLED -> badge.getStyleClass().add("badge-cancelled");
                    case COMPLETED -> badge.getStyleClass().add("badge-completed");
                }

                setGraphic(badge);
                setText(null);
                createStars();

            }
        });
        // ComboBox values
        typeBox.setItems(FXCollections.observableArrayList(OfferType.values()));

        // Load data
        loadOffers();

        // Add action buttons
        addActionButtons();

    }

    // ===== LOAD DATA =====
    private void loadOffers() {
        try {
            offerList.setAll(service.getAllOffers());
            offerTable.setItems(offerList);
        } catch (SQLException e) {
            showError("Error loading offers");
        }
    }

    // ===== ADD OFFER =====
    @FXML
    private void addOffer() {

        try {

            if (selectedImageName == null) {
                showError("Please upload an image");
                return;
            }

            Offer offer = new Offer(
                    titleField.getText(),
                    descriptionField.getText(),
                    typeBox.getValue(),
                    1,
                    selectedImageName,
                    Double.parseDouble(amountField.getText())
            );


            service.addOffer(offer);
            loadOffers();
            clearForm();

            previewImage.setImage(null);
            selectedImageName = null;

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }


    // ===== ACTION BUTTONS COLUMN =====
    private void addActionButtons() {

        colActions.setCellFactory(col -> new TableCell<>() {

            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Delete");
            private final Button btnPublish = new Button("Publish");
            private final Button btnCancel = new Button("Cancel");

            private final HBox box = new HBox(6, btnEdit, btnDelete, btnPublish, btnCancel);

            {
                btnEdit.getStyleClass().add("btn-edit");
                btnDelete.getStyleClass().add("btn-delete");
                btnPublish.getStyleClass().add("btn-publish");
                btnCancel.getStyleClass().add("btn-cancel");

                btnEdit.setOnAction(e -> {
                    Offer offer = getTableView().getItems().get(getIndex());
                    editOffer(offer);
                });

                btnDelete.setOnAction(e -> {
                    Offer offer = getTableView().getItems().get(getIndex());
                    deleteOffer(offer);
                });

                btnPublish.setOnAction(e -> {
                    Offer offer = getTableView().getItems().get(getIndex());
                    publishOffer(offer);
                });

                btnCancel.setOnAction(e -> {
                    Offer offer = getTableView().getItems().get(getIndex());
                    cancelOffer(offer);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    Offer offer = getTableView().getItems().get(getIndex());
                    OfferStatus status = offer.getStatus();

                    btnEdit.setDisable(true);
                    btnDelete.setDisable(true);
                    btnPublish.setDisable(true);
                    btnCancel.setDisable(true);

                    switch (status) {
                        case DRAFT -> {
                            btnEdit.setDisable(false);
                            btnDelete.setDisable(false);
                            btnPublish.setDisable(false);
                            btnCancel.setDisable(false);
                        }
                        case PUBLISHED, IN_PROGRESS -> btnCancel.setDisable(false);
                        case COMPLETED, CANCELLED -> {}
                    }

                    setGraphic(box);
                }
            }
        });
    }


    // ===== ACTION METHODS =====
    private void publishOffer(Offer offer) {
        try {
            service.publishOffer(offer);
            loadOffers();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void deleteOffer(Offer offer) {
        try {
            service.deleteOffer(offer);
            loadOffers();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void editOffer(Offer offer) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/tn/esprit/synergygig/gui/EditOffer.fxml")
            );

            Parent root = loader.load();

            // Controller
            EditOfferController controller = loader.getController();
            controller.setOffer(offer);

            // 🔥 Création Scene + CSS
            Scene scene = new Scene(root);

            scene.getStylesheets().add(
                    getClass().getResource("/tn/esprit/synergygig/gui/app.css").toExternalForm()
            );

            // Stage
            Stage stage = new Stage();
            stage.setTitle("Edit Offer");
            stage.setScene(scene);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            loadOffers(); // refresh table

        } catch (Exception e) {
            e.printStackTrace();
            showError(e.getMessage());
        }
    }




    // ===== UTILITIES =====
    private void clearForm() {
        titleField.clear();
        descriptionField.clear();
        typeBox.getSelectionModel().clearSelection();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText("Error");
        alert.setContentText(msg);
        alert.show();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.show();
    }
    private void cancelOffer(Offer offer) {
        try {
            service.cancelOffer(offer);
            loadOffers();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }
    @FXML
    private void handleUploadImage() {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Offer Image");

        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(previewImage.getScene().getWindow());

        if (file != null) {
            try {
                // 📁 dossier uploads à la racine du projet
                Path targetDir = Paths.get("uploads");

                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }

                Path targetPath = targetDir.resolve(file.getName());

                Files.copy(file.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                selectedImageName = file.getName();

                previewImage.setImage(new Image(file.toURI().toString()));

            } catch (Exception e) {
                showError("Error uploading image");
                e.printStackTrace();
            }
        }
    }
    private void createStars() {

        animatedBackground.getChildren().clear();

        int starCount = 700; // 🌟 nombre réduit

        for (int i = 0; i < starCount; i++) {

            Circle star = new Circle(0.5 + Math.random() * 1.5);

            star.setTranslateX(Math.random() * animatedBackground.getWidth());
            star.setTranslateY(Math.random() * animatedBackground.getHeight());

            star.setStyle(Math.random() > 0.7
                    ? "-fx-fill: #6c8cff;"
                    : "-fx-fill: rgba(255,255,255,0.6);"
            );

            FadeTransition fade = new FadeTransition(
                    Duration.seconds(3 + Math.random() * 4),
                    star
            );

            fade.setFromValue(0.2);
            fade.setToValue(0.9);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }



}
