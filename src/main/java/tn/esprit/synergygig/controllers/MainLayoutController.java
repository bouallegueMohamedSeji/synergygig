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

    // ===== SIDEBAR TOGGLE =====
    private SidebarController sidebarController; // Reference to the controller

    // ===== LOAD SIDEBAR =====
    private void loadSidebar() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/tn/esprit/synergygig/gui/Sidebar.fxml"));

            Node sidebar = loader.load();

            // 🔥 Donner accès au MainLayout depuis la Sidebar
            sidebarController = loader.getController();
            sidebarController.setMainLayoutController(this);

            sidebarContainer.setCenter(sidebar);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void toggleSidebar() {
        if (sidebarController != null) {
            sidebarController.toggleSidebar();
            
            // Adjust container width if needed, but SidebarController handles properties of the root VBox.
            // However, sidebarContainer (BorderPane) might need its left area width adjusted?
            // Actually, if sidebarRoot changes prefWidth, and it's inside sidebarContainer center,
            // sidebarContainer is a BorderPane in LEFT of root. 
            // The constraint might be on the sidebarContainer itself.
            // Let's check MainLayout.fxml.
            // MainLayout.fxml: <BorderPane fx:id="sidebarContainer"/> inside <left>.
            // If sidebar changes size, the BorderPane (sidebarContainer) should adapt if it doesn't have fixed size.
            // SidebarController sets prefWidth on sidebarRoot.
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

    public void showHR() {
        loadCenter("HRMainView.fxml");
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
