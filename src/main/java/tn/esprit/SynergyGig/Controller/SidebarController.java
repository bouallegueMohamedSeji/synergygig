package tn.esprit.SynergyGig.Controller;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/**
 * SIDEBAR CONTROLLER
 * Gère la navigation entre les modules et l'animation galaxy (étoiles).
 */
public class SidebarController {

    @FXML private Pane starsPane;
    @FXML private Button btnProjets;
    @FXML private Button btnTaches;
    @FXML private Button btnDashboard;
    @FXML private Button btnEmployes;
    @FXML private Button btnConges;
    @FXML private Button btnOffres;

    private MainLayoutController mainLayoutController;

    @FXML
    public void initialize() {
        createStars();
    }

    // ===== INJECTION DU MAIN LAYOUT =====
    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    // ===== NAVIGATION =====

    @FXML
    private void showProjets() {
        if (mainLayoutController != null) mainLayoutController.showProjets();
    }

    @FXML
    private void showTaches() {
        if (mainLayoutController != null) mainLayoutController.showTaches();
    }

    @FXML
    private void showDashboard() {
        if (mainLayoutController != null) mainLayoutController.showDashboard();
    }

    @FXML
    private void showEmployes() {
        if (mainLayoutController != null) mainLayoutController.showEmployes();
    }

    @FXML
    private void showConges() {
        if (mainLayoutController != null) mainLayoutController.showConges();
    }

    @FXML
    private void showOffres() {
        if (mainLayoutController != null) mainLayoutController.showOffres();
    }

    // ===== BOUTON ACTIF (surbrillance) =====
    public void setActive(String module) {
        // Réinitialiser tous les boutons
        resetAllButtons();

        // Appliquer la classe active au bon bouton
        switch (module) {
            case "projets"   -> setActive(btnProjets);
            case "taches"    -> setActive(btnTaches);
            case "dashboard" -> setActive(btnDashboard);
            case "employes"  -> setActive(btnEmployes);
            case "conges"    -> setActive(btnConges);
            case "offres"    -> setActive(btnOffres);
        }
    }

    private void resetAllButtons() {
        Button[] buttons = {btnProjets, btnTaches, btnDashboard,
                btnEmployes, btnConges, btnOffres};
        for (Button btn : buttons) {
            if (btn != null) {
                btn.getStyleClass().remove("sidebar-btn-active");
                if (!btn.getStyleClass().contains("sidebar-btn")) {
                    btn.getStyleClass().add("sidebar-btn");
                }
            }
        }
    }

    private void setActive(Button btn) {
        if (btn != null) {
            btn.getStyleClass().remove("sidebar-btn");
            if (!btn.getStyleClass().contains("sidebar-btn-active")) {
                btn.getStyleClass().add("sidebar-btn-active");
            }
        }
    }

    // ===== ANIMATION ÉTOILES GALAXY =====
    private void createStars() {
        if (starsPane == null) return;

        for (int i = 0; i < 180; i++) {
            // Rayon aléatoire entre 0.5 et 2.5
            double radius = Math.random() * 2.0 + 0.5;
            Circle star = new Circle(radius);

            // Position aléatoire
            star.setLayoutX(Math.random() * 210);
            star.setLayoutY(Math.random() * 900);

            // Couleur : blanc ou bleu néon ou violet
            double colorRand = Math.random();
            if (colorRand < 0.5) {
                star.setStyle("-fx-fill: rgba(255,255,255,0.85);");
            } else if (colorRand < 0.75) {
                star.setStyle("-fx-fill: #00d4ff;");   // cyan
            } else {
                star.setStyle("-fx-fill: #6c63ff;");   // violet
            }

            // Animation fade (clignotement)
            FadeTransition fade = new FadeTransition(
                    Duration.seconds(1.5 + Math.random() * 3.5),
                    star
            );
            fade.setFromValue(0.1 + Math.random() * 0.3);
            fade.setToValue(0.8 + Math.random() * 0.2);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            starsPane.getChildren().add(star);
        }
    }
}
