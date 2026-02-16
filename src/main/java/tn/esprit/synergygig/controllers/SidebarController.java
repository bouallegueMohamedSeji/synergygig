package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.animation.FadeTransition;
import javafx.animation.Animation;
import javafx.util.Duration;
import javafx.scene.layout.Pane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;


public class SidebarController {
    @FXML
    private Pane sidebarBackground;

    @FXML
    private ImageView logoImage;
    @FXML
    private Pane starsPane;

    @FXML
    private StackPane rootSidebar;
    @FXML
    private VBox sidebarContent;
    @FXML
    public void initialize() {

        loadLogo();
        createStars();
    }

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    public void showDashboard() {
        mainLayoutController.showDashboard();
    }

    public void showOffers() {
        mainLayoutController.showOffers();
    }

    public void showApplications() {
        mainLayoutController.showApplications();
    }
    @FXML
    private void goGigOffers() {
        mainLayoutController.showGigOffers();
    }
    @FXML
    private void showApplicationsAdmin() {
        mainLayoutController.showApplicationsAdmin();
    }
    @FXML
    private void showClientOffers() {
        if (mainLayoutController != null) {
            mainLayoutController.showClientOffers();
        }
    }

    private void createStars() {

        starsPane.setStyle("-fx-background-color: #121826;"); // nouvelle couleur sidebar

        for (int i = 0; i < 200; i++) {

            Circle star = new Circle(Math.random() * 2 + 0.5);

            star.setLayoutX(Math.random() * 250);
            star.setLayoutY(Math.random() * 1000);

            // 🌟 mélange blanc + bleu néon
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

            starsPane.getChildren().add(star);
        }
    }

    private void loadLogo() {
        Image logo = new Image(
                getClass().getResourceAsStream("/tn/esprit/synergygig/gui/images/anaslogo1.png")
        );
        logoImage.setImage(logo);
    }







}
