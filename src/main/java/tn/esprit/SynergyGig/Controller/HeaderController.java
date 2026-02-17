package tn.esprit.SynergyGig.Controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * HEADER CONTROLLER
 * Gère le titre dynamique du header selon la vue affichée.
 */
public class HeaderController {

    @FXML private Label pageTitle;
    @FXML private Label adminLabel;

    @FXML
    public void initialize() {
        // Titre par défaut
        if (pageTitle != null) {
            pageTitle.setText("🚀 Gestion des Projets");
        }
    }

    /**
     * Appelée par MainLayoutController pour changer le titre
     */
    public void setTitle(String title) {
        if (pageTitle != null) {
            pageTitle.setText(title);
        }
    }

    /**
     * Appelée si on veut changer le nom affiché (connexion utilisateur)
     */
    public void setAdmin(String name) {
        if (adminLabel != null) {
            adminLabel.setText(name);
        }
    }
}
