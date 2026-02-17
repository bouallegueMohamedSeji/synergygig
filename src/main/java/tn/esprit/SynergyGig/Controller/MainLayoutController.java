package tn.esprit.SynergyGig.Controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * MAIN LAYOUT CONTROLLER
 * Coordonne la sidebar, le header et le contenu central.
 * C'est lui qui charge les vues dans la zone contentArea.
 */
public class MainLayoutController {

    @FXML private StackPane contentArea;

    // Référence aux controllers inclus
    @FXML private SidebarController sidebarController;
    @FXML private HeaderController headerController;

    @FXML
    public void initialize() {
        // Injecter this dans la sidebar pour qu'elle puisse changer la vue
        if (sidebarController != null) {
            sidebarController.setMainLayoutController(this);
        }
        // Charger la vue Projets par défaut au démarrage
        showProjets();
    }

    // ===== NAVIGATION =====

    public void showProjets() {
        loadView("/tn/esprit/SynergyGig/gui/fxml/projetView.fxml");
        setHeaderTitle("🚀 Gestion des Projets");
        if (sidebarController != null) sidebarController.setActive("projets");
    }

    public void showTaches() {
        loadView("/tn/esprit/SynergyGig/gui/fxml/tache-view.fxml");
        setHeaderTitle("✅ Gestion des Tâches");
        if (sidebarController != null) sidebarController.setActive("taches");
    }

    public void showDashboard() {
        // Vue dashboard (à créer plus tard)
        setHeaderTitle("📊 Dashboard");
        if (sidebarController != null) sidebarController.setActive("dashboard");
    }

    public void showEmployes() {
        setHeaderTitle("👥 Gestion des Employés");
        if (sidebarController != null) sidebarController.setActive("employes");
    }

    public void showConges() {
        setHeaderTitle("🌴 Gestion des Congés");
        if (sidebarController != null) sidebarController.setActive("conges");
    }

    public void showOffres() {
        setHeaderTitle("💼 Gestion des Offres");
        if (sidebarController != null) sidebarController.setActive("offres");
    }

    // ===== UTILITAIRES =====

    /**
     * Charge une vue FXML dans la zone centrale (contentArea)
     */
    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(fxmlPath)
            );
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            System.err.println("❌ Impossible de charger la vue : " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Met à jour le titre dans le header
     */
    private void setHeaderTitle(String title) {
        if (headerController != null) {
            headerController.setTitle(title);
        }
    }
}
