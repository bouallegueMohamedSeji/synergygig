package tn.esprit.synergygig.controllers;

import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;

import java.io.IOException;

public class MainLayoutController {

    @FXML
    private BorderPane centerContainer;

    @FXML
    private BorderPane sidebarContainer;

    @FXML
    public void initialize() {
        loadSidebar();      // 👈 ICI
        showDashboard();    // vue par défaut
    }

    // ===== LOAD SIDEBAR =====
    private void loadSidebar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/tn/esprit/synergygig/gui/Sidebar.fxml")
            );

            Node sidebar = loader.load();

            // 🔥 Donner accès au MainLayout depuis la Sidebar
            SidebarController controller = loader.getController();
            controller.setMainLayoutController(this);

            sidebarContainer.setCenter(sidebar);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===== NAVIGATION =====
    public void showDashboard() {
        loadCenter("DashboardView.fxml");
    }

    public void showOffers() {
        loadCenter("OfferView.fxml");
    }

    public void showApplications() {
        loadCenter("ApplicationView.fxml");
    }

    private void loadCenter(String fxml) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/tn/esprit/synergygig/gui/" + fxml)
            );

            Parent view = loader.load();

            centerContainer.setCenter(view);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void showGigOffers() {
        loadCenter("GigOffersView.fxml");
    }
    public void showGigOffersView() {
        loadCenter("GigOffersView.fxml");
    }
    public void showApplicationsAdmin() {
        loadCenter("ApplicationsAdminView.fxml");
    }




}
