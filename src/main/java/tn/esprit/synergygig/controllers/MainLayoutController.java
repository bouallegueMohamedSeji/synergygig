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

    private static MainLayoutController instance;

    public MainLayoutController() {
        instance = this;
    }

    public static MainLayoutController getInstance() {
        return instance;
    }

    public void navigate(String viewName) {
        loadCenter(viewName);
    }

    @FXML
    private BorderPane centerContainer;

    @FXML
    private BorderPane sidebarContainer;

    @FXML
    private HeaderController headerController; // Injected via fx:id="header" in FXML

    @FXML
    public void initialize() {
        loadSidebar(); // 👈 ICI

        // Setup Header Controller
        if (headerController != null) {
            headerController.setMainLayoutController(this);
        }

        showDashboard(); // vue par défaut
    }

    // ===== LOAD SIDEBAR =====
    private void loadSidebar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/tn/esprit/synergygig/gui/Sidebar.fxml"));

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
                    getClass().getResource("/tn/esprit/synergygig/gui/" + fxml));

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

    public void showProfile() {
        loadCenter("Profile.fxml");
    }

    public void showForums() {
        loadCenter("ForumsView.fxml");
    }

    private RootLayoutController rootLayoutController;

    public void setRootLayoutController(RootLayoutController rootLayoutController) {
        this.rootLayoutController = rootLayoutController;
    }

    public void logout() {
        if (rootLayoutController != null) {
            rootLayoutController.showLogin();
        } else {
            // Fallback
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/tn/esprit/synergygig/gui/Login.fxml"));
                Parent loginView = loader.load();

                // Get current stage from one of the nodes
                javafx.stage.Stage stage = (javafx.stage.Stage) centerContainer.getScene().getWindow();
                javafx.scene.Scene scene = new javafx.scene.Scene(loginView);
                stage.setTitle("SynergyGig - Login");
                stage.setScene(scene);
                stage.centerOnScreen();
                stage.show();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
