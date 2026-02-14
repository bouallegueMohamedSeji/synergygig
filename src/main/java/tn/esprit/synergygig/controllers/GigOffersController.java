package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.OfferStatus;
import tn.esprit.synergygig.entities.enums.OfferType;
import tn.esprit.synergygig.services.ApplicationService;
import tn.esprit.synergygig.services.OfferService;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.Animation;

import javafx.util.Duration;
import javafx.scene.layout.FlowPane;

public class GigOffersController {


    @FXML
    private FlowPane offersContainer;

    @FXML private Pane animatedBackground;



    private final OfferService offerService = new OfferService();
    private final ApplicationService applicationService = new ApplicationService();
    private final ObservableList<Offer> offers = FXCollections.observableArrayList();

    // 🔐 mock user (plus tard login)
    private final int connectedUserId = 3;

    @FXML
    public void initialize() {

        startBackgroundAnimation();
        loadPublishedOffers();
    }
    private void loadPublishedOffers() {
        try {
            offersContainer.getChildren().clear();

            for (Offer offer : offerService.getAllOffers()
                    .stream()
                    .filter(o -> o.getStatus() == OfferStatus.PUBLISHED)
                    .toList()) {

                offersContainer.getChildren().add(createOfferCard(offer));
            }

        } catch (Exception e) {
            showError(e.getMessage());
        }
    }




    private void applyToOffer(Offer offer) {
        try {
            applicationService.apply(offer, connectedUserId);
            showInfo("Candidature envoyée avec succès !");
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(msg);
        alert.show();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.show();
    }

    private VBox createOfferCard(Offer offer) {

        Image img = null;

        try {
            img = new Image(
                    getClass().getResourceAsStream(
                            "/tn/esprit/synergygig/gui/images/" + offer.getImageUrl()
                    )
            );
        } catch (Exception e) {
            System.out.println("Image not found: " + offer.getImageUrl());
        }

        ImageView image = new ImageView();

        if (img != null) {
            image.setImage(img);
        }


        image.setFitHeight(150);
        image.setFitWidth(260);
        image.setPreserveRatio(false);

        Label title = new Label(offer.getTitle());
        title.setStyle("-fx-text-fill: white; -fx-font-size:16; -fx-font-weight:bold;");

        Label description = new Label(offer.getDescription());
        description.setWrapText(true);
        description.setMaxWidth(250);
        description.setStyle("-fx-text-fill: #cfd8dc; -fx-font-size:12;");

        Label badge = new Label("PUBLISHED");
        badge.getStyleClass().addAll("badge", "badge-published");

        Button applyBtn = new Button("📬 Postuler");
        applyBtn.getStyleClass().add("btn-apply-neon");
        applyBtn.setOnAction(e -> applyToOffer(offer));

        VBox card = new VBox(10, image, title, description, badge, applyBtn);
        card.setPrefWidth(260);
        card.getStyleClass().add("offer-card");

        addHoverEffect(card);

        return card;
    }


    private void addHoverEffect(VBox card) {

        card.setOnMouseEntered(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1.05);
            scale.setToY(1.05);
            scale.play();
        });

        card.setOnMouseExited(e -> {
            ScaleTransition scale = new ScaleTransition(Duration.millis(200), card);
            scale.setToX(1);
            scale.setToY(1);
            scale.play();
        });
    }
    private void startBackgroundAnimation() {

        animatedBackground.setStyle("""
        -fx-background-color:
            linear-gradient(to bottom right,
                #0f2027,
                #203a43,
                #2c5364);
    """);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(0),
                        e -> animatedBackground.setStyle("""
                        -fx-background-color:
                            linear-gradient(to bottom right,
                                #0f2027,
                                #203a43,
                                #2c5364);
                    """)),

                new KeyFrame(Duration.seconds(6),
                        e -> animatedBackground.setStyle("""
                        -fx-background-color:
                            linear-gradient(to bottom right,
                                #141e30,
                                #243b55,
                                #00f260);
                    """)),

                new KeyFrame(Duration.seconds(12),
                        e -> animatedBackground.setStyle("""
                        -fx-background-color:
                            linear-gradient(to bottom right,
                                #1e3c72,
                                #2a5298,
                                #396afc);
                    """))
        );

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }


}
