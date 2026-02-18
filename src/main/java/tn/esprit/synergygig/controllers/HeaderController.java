package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.utils.UserSession;

public class HeaderController {

    @FXML
    private Label userNameLabel;

    @FXML
    private HBox userBox;

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    @FXML
    private ImageView logoImage;

    @FXML
    public void initialize() {
        // Load user name from session
        if (UserSession.getInstance() != null) {
            User user = UserSession.getInstance().getUser();
            if (user != null) {
                userNameLabel.setText(user.getFullName() + " (" + user.getRole() + ")");
            }
        }
        
        // Load Logo
        try {
            javafx.scene.image.Image image = new javafx.scene.image.Image(getClass().getResource("/tn/esprit/synergygig/gui/images/anaslogo1.png").toExternalForm());
            logoImage.setImage(image);
        } catch (Exception e) {
            System.out.println("Logo not found in Header.");
        }
        
        // Apply default theme (Delayed to ensure Scene is attached? No, initialize might be too early for getScene())
        // We will check if scene is null. If so, we might need a listener.
        javafx.application.Platform.runLater(this::applyTheme);
    }

    @FXML
    private void handleProfileClick() {
        if (mainLayoutController != null) {
            mainLayoutController.showProfile();
        }
    }
    
    @FXML
    private void toggleSidebar() {
        if (mainLayoutController != null) {
            mainLayoutController.toggleSidebar();
        }
    }

    // ===== DAY/NIGHT TOGGLE =====
    @FXML private javafx.scene.layout.StackPane toggleContainer;
    @FXML private javafx.scene.layout.Pane toggleTrack;
    @FXML private javafx.scene.layout.Pane toggleHandler;
    @FXML private javafx.scene.shape.Circle crater1;
    @FXML private javafx.scene.shape.Circle crater2;
    @FXML private javafx.scene.shape.Circle crater3;
    @FXML private javafx.scene.shape.Circle star1;
    @FXML private javafx.scene.shape.Circle star2;
    @FXML private javafx.scene.shape.Circle star3;
    @FXML private javafx.scene.shape.Circle star4;
    @FXML private javafx.scene.shape.Circle star5;
    @FXML private javafx.scene.shape.Circle star6;

    private boolean isNight = false;
    
    @FXML
    private void toggleTheme() {
        isNight = !isNight;
        animateToggle();
        applyTheme();
    }

    private void animateToggle() {
        // Animation Duration
        javafx.util.Duration duration = javafx.util.Duration.millis(400);

        // 1. Move Handler (Sun/Moon) - Move 40px to the right
        javafx.animation.TranslateTransition translate = new javafx.animation.TranslateTransition(duration, toggleHandler);
        
        // 2. Animate Craters & Stars Opacity
        javafx.animation.FadeTransition fadeCraters1 = new javafx.animation.FadeTransition(duration, crater1);
        javafx.animation.FadeTransition fadeCraters2 = new javafx.animation.FadeTransition(duration, crater2);
        javafx.animation.FadeTransition fadeCraters3 = new javafx.animation.FadeTransition(duration, crater3);
        
        javafx.animation.FadeTransition fadeStars1 = new javafx.animation.FadeTransition(duration, star1);
        javafx.animation.FadeTransition fadeStars2 = new javafx.animation.FadeTransition(duration, star2);
        javafx.animation.FadeTransition fadeStars3 = new javafx.animation.FadeTransition(duration, star3);
        javafx.animation.FadeTransition fadeStars4 = new javafx.animation.FadeTransition(duration, star4);
        javafx.animation.FadeTransition fadeStars5 = new javafx.animation.FadeTransition(duration, star5);
        javafx.animation.FadeTransition fadeStars6 = new javafx.animation.FadeTransition(duration, star6);

        if (isNight) {
            // Switch to Night (Moon)
            translate.setToX(24); 
            // Manual Style updates for colors (JavaFX CSS transition is limited)
            toggleTrack.setStyle("-fx-background-color: #749dd6; -fx-background-radius: 50px;"); 
            toggleHandler.setStyle("-fx-background-color: #ffe5b5; -fx-background-radius: 50%; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);");
            
            // Show Craters
            fadeCraters1.setToValue(1); fadeCraters2.setToValue(1); fadeCraters3.setToValue(1);
            // Show Stars
            fadeStars1.setToValue(1); fadeStars2.setToValue(1); fadeStars3.setToValue(1); 
            fadeStars4.setToValue(1); fadeStars5.setToValue(1); fadeStars6.setToValue(1);
            
        } else {
            // Switch to Day (Sun)
            translate.setToX(0); 
            toggleTrack.setStyle("-fx-background-color: #83d8ff; -fx-background-radius: 50px;");
            toggleHandler.setStyle("-fx-background-color: #ffcf96; -fx-background-radius: 50%; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 3, 0, 0, 1);");
            
            // Hide Craters
            fadeCraters1.setToValue(0); fadeCraters2.setToValue(0); fadeCraters3.setToValue(0);
            // Hide Stars
            fadeStars1.setToValue(0); fadeStars2.setToValue(0); fadeStars3.setToValue(0); 
            fadeStars4.setToValue(0); fadeStars5.setToValue(0); fadeStars6.setToValue(0);
        }
        
        javafx.animation.ParallelTransition parallel = new javafx.animation.ParallelTransition(
            translate, 
            fadeCraters1, fadeCraters2, fadeCraters3,
            fadeStars1, fadeStars2, fadeStars3, fadeStars4, fadeStars5, fadeStars6
        );
        parallel.play();
    }

    private void applyTheme() {
        if (toggleContainer.getScene() != null) {
            String darkTheme = getClass().getResource("/tn/esprit/synergygig/gui/dark-theme.css").toExternalForm();
            String lightTheme = getClass().getResource("/tn/esprit/synergygig/gui/light-theme.css").toExternalForm();
            
            if (isNight) {
                if (!toggleContainer.getScene().getStylesheets().contains(darkTheme)) {
                    toggleContainer.getScene().getStylesheets().add(darkTheme);
                }
                toggleContainer.getScene().getStylesheets().remove(lightTheme);
            } else {
                if (!toggleContainer.getScene().getStylesheets().contains(lightTheme)) {
                    toggleContainer.getScene().getStylesheets().add(lightTheme);
                }
                toggleContainer.getScene().getStylesheets().remove(darkTheme);
            }
        }
    }
}
