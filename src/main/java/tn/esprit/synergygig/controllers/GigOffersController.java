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
import javafx.scene.shape.Circle;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.Animation;
import javafx.util.Duration;


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
        startGalaxyBackground();

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
        card.setStyle(card.getStyle() + "; -fx-background-insets: 0;");


        addHoverEffect(card);


        return card;
    }


    private void addHoverEffect(VBox card) {

        card.setOnMouseEntered(e -> {

            ScaleTransition scale = new ScaleTransition(Duration.millis(250), card);
            scale.setToX(1.06);
            scale.setToY(1.06);
            scale.play();

            card.setStyle("""
            -fx-background-color: rgba(255,255,255,0.12);
            -fx-background-radius: 20;
            -fx-border-radius: 20;
            -fx-border-color: #00f260;
            -fx-border-width: 1.5;
            -fx-effect: dropshadow(gaussian,
                    #00f260,
                    40,
                    0.6,
                    0,
                    0);
        """);
        });

        card.setOnMouseExited(e -> {

            ScaleTransition scale = new ScaleTransition(Duration.millis(250), card);
            scale.setToX(1);
            scale.setToY(1);
            scale.play();

            card.getStyleClass().add("offer-card");
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
    private void startGalaxyBackground() {

        animatedBackground.setStyle("""
        -fx-background-color:
            radial-gradient(center 50% 50%, radius 100%,
                #0f2027 0%,
                #203a43 40%,
                #2c5364 70%,
                #000000 100%);
    """);

        Timeline gradientShift = new Timeline(
                new KeyFrame(Duration.seconds(0), e ->
                        animatedBackground.setStyle("""
                -fx-background-color:
                    radial-gradient(center 30% 30%, radius 120%,
                        #0f2027,
                        #141e30,
                        #000000);
            """)),

                new KeyFrame(Duration.seconds(8), e ->
                        animatedBackground.setStyle("""
                -fx-background-color:
                    radial-gradient(center 70% 70%, radius 120%,
                        #1e3c72,
                        #2a5298,
                        #000000);
            """))
        );

        gradientShift.setCycleCount(Animation.INDEFINITE);
        gradientShift.setAutoReverse(true);
        gradientShift.play();

        createStars();
    }
    private void createStars() {

        for (int i = 0; i < 120; i++) {

            Circle star = new Circle(Math.random() * 2);
            star.setTranslateX(Math.random() * 1600);
            star.setTranslateY(Math.random() * 900);
            star.setStyle("-fx-fill: white;");

            FadeTransition fade = new FadeTransition(
                    Duration.seconds(2 + Math.random() * 3),
                    star
            );
            fade.setFromValue(0.1);
            fade.setToValue(1);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }


}
