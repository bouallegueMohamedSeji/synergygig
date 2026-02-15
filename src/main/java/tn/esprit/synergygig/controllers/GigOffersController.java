package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
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
import javafx.scene.shape.Circle;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.Animation;
import javafx.util.Duration;
import javafx.animation.TranslateTransition;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.geometry.Pos;



public class GigOffersController {


    @FXML
    private FlowPane offersContainer;

    @FXML private Pane animatedBackground;
    @FXML
    private StackPane modalOverlay;



    private final OfferService offerService = new OfferService();
    private final ApplicationService applicationService = new ApplicationService();
    private final ObservableList<Offer> offers = FXCollections.observableArrayList();

    // 🔐 mock user (plus tard login)
    private final int connectedUserId = 3;

    @FXML
    public void initialize() {


        loadPublishedOffers();
        startGalaxyBackground();
        animatedBackground.widthProperty().addListener((obs, oldVal, newVal) -> createStars());
        animatedBackground.heightProperty().addListener((obs, oldVal, newVal) -> createStars());

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
        description.setStyle("-fx-text-fill: white; -fx-font-size:12;");

        Label badge = new Label("PUBLISHED");
        badge.getStyleClass().addAll("badge", "badge-published");

        Button applyBtn = new Button("📬 Postuler");
        applyBtn.getStyleClass().add("btn-apply-neon");
        applyBtn.setOnAction(e -> showApplyModal(offer));


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
            -fx-border-color: #4c175a;
            -fx-border-width: 1.5;
            -fx-effect: dropshadow(gaussian,
                    #4c175a,
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


    private void startGalaxyBackground() {
        createStars();
    }
    private void createStars() {

        animatedBackground.getChildren().clear();

        for (int i = 0; i < 700; i++) { // 🔥 plus intense

            Circle star = new Circle(Math.random() * 2.2);

            // position aléatoire dynamique (utilise la taille réelle du pane)
            star.setTranslateX(Math.random() * animatedBackground.getWidth());
            star.setTranslateY(Math.random() * animatedBackground.getHeight());

            // 🌌 Couleur aléatoire (blanc ou bleu galaxie)
            if (Math.random() > 0.5) {
                star.setStyle("-fx-fill: #396afc;"); // bleu cosmique
            } else {
                star.setStyle("-fx-fill: rgba(255,255,255,0.8);"); // blanc doux
            }

            // ✨ Effet scintillement
            FadeTransition fade = new FadeTransition(
                    Duration.seconds(2 + Math.random() * 3),
                    star
            );
            fade.setFromValue(0.15);
            fade.setToValue(1);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }

    private void showApplyModal(Offer offer) {

        VBox modal = new VBox(20);
        modal.setMaxWidth(350);
        modal.setStyle("""
    -fx-background-color: rgba(20,25,40,0.95);
    -fx-background-radius: 20;
    -fx-padding: 30;
    -fx-effect: dropshadow(gaussian, #4c175a, 25, 0.3, 0, 0);
""");

        modal.setMaxWidth(420);
        modal.setPrefWidth(380);
        modal.setMaxHeight(300);
        modal.setAlignment(Pos.CENTER);

        Label title = new Label("Confirmer la candidature");
        title.setStyle("-fx-text-fill:white; -fx-font-size:18; -fx-font-weight:bold;");

        Label subtitle = new Label(offer.getTitle());
        subtitle.setStyle("-fx-text-fill:#4c175a;");

        Button confirm = new Button("🚀 Confirmer");
        confirm.getStyleClass().add("btn-apply-neon");

        Button cancel = new Button("Annuler");
        cancel.setStyle("-fx-background-color:transparent; -fx-text-fill:white;");

        confirm.setOnAction(e -> {
            startLoader(confirm);
            simulateApply(offer, modal);
        });

        cancel.setOnAction(e -> closeModal());

        modal.getChildren().addAll(title, subtitle, confirm, cancel);

        modalOverlay.getChildren().setAll(modal);
        modalOverlay.setVisible(true);

        animateModal(modal);
        blurBackground(true);
    }
    private void animateModal(VBox modal) {

        modal.setOpacity(0);
        modal.setTranslateY(40);

        FadeTransition fade = new FadeTransition(Duration.millis(300), modal);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), modal);
        slide.setFromY(40);
        slide.setToY(0);
        Timeline glow = new Timeline(
                new KeyFrame(Duration.seconds(0),
                        new KeyValue(modal.scaleXProperty(), 1)),
                new KeyFrame(Duration.seconds(1),
                        new KeyValue(modal.scaleXProperty(), 1.02))
        );

        glow.setAutoReverse(true);
        glow.setCycleCount(Animation.INDEFINITE);
        glow.play();
        fade.play();
        slide.play();

    }
    private void closeModal() {
        modalOverlay.setVisible(false);
        modalOverlay.getChildren().clear();
        blurBackground(false);
    }
    private void startLoader(Button button) {

        ProgressIndicator loader = new ProgressIndicator();
        loader.setPrefSize(20, 20);

        button.setGraphic(loader);
        button.setText(" Envoi...");
        button.setDisable(true);
    }
    private void simulateApply(Offer offer, VBox modal) {

        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));

        pause.setOnFinished(e -> {

            try {
                applicationService.apply(offer, connectedUserId);

                modal.getChildren().clear();

                Label success = new Label("✔ Candidature envoyée !");
                success.setStyle("-fx-text-fill:#00f260; -fx-font-size:16;");

                modal.getChildren().add(success);

                PauseTransition close = new PauseTransition(Duration.seconds(1));
                close.setOnFinished(ev -> closeModal());
                close.play();

            } catch (Exception ex) {
                modal.getChildren().clear();

                Label error = new Label("❌ Déjà postulé !");
                error.setStyle("-fx-text-fill:#ff4b5c;");

                modal.getChildren().add(error);
            }
        });

        pause.play();
    }
    private void blurBackground(boolean blur) {

        if (blur) {
            animatedBackground.setEffect(
                    new javafx.scene.effect.GaussianBlur(20)
            );
        } else {
            animatedBackground.setEffect(null);
        }
    }



}
