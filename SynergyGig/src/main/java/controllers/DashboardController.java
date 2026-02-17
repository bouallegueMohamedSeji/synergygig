package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import services.ServiceUser;
import utils.SessionManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class DashboardController {

    // Top bar
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label roleLabel;

    // Sidebar
    @FXML
    private VBox sidebar;
    @FXML
    private Button btnDashboard;
    @FXML
    private Button btnProfile;

    // Admin-only
    @FXML
    private Label adminSectionLabel;
    @FXML
    private Button btnManageUsers;

    // HR-only
    @FXML
    private Label hrSectionLabel;
    @FXML
    private Button btnHrDashboard;

    // Module buttons
    @FXML
    private Button btnMessages;
    @FXML
    private Button btnRecruitment;
    @FXML
    private Button btnHrAdmin;
    @FXML
    private Button btnProjects;
    @FXML
    private Button btnCourses;
    @FXML
    private Button btnResources;
    @FXML
    private Button btnQuizzes;
    @FXML
    private Button btnCommunity;

    // Content area
    @FXML
    private StackPane contentArea;
    @FXML
    private VBox dashboardHome;

    // Stats
    @FXML
    private Label dashboardTitle;
    @FXML
    private Label dashboardSubtitle;
    @FXML
    private Label statTotalUsers;
    @FXML
    private Label statEmployees;
    @FXML
    private Label statGigWorkers;
    @FXML
    private Label statRole;

    private ServiceUser serviceUser = new ServiceUser();

    @FXML
    public void initialize() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        // Set welcome info
        welcomeLabel.setText("Welcome, " + currentUser.getFirstName());
        roleLabel.setText(currentUser.getRole().replace("_", " "));

        // Configure sidebar based on role
        configureRoleAccess(currentUser.getRole());

        // Load stats
        loadDashboardStats();
    }

    private void configureRoleAccess(String role) {
        switch (role) {
            case "ADMIN":
                // Admin sees everything
                showNode(adminSectionLabel);
                showNode(btnManageUsers);
                showNode(hrSectionLabel);
                showNode(btnHrDashboard);
                showNode(btnHrAdmin);
                showNode(btnProjects);
                break;

            case "HR_MANAGER":
                // HR sees HR tools + employee management
                showNode(hrSectionLabel);
                showNode(btnHrDashboard);
                showNode(btnHrAdmin);
                showNode(btnProjects);
                break;

            case "EMPLOYEE":
                // Employee sees basic modules
                showNode(btnHrAdmin); // can view attendance/leave
                break;

            case "PROJECT_OWNER":
                // Employee + Projects/Tasks
                showNode(btnHrAdmin);
                showNode(btnProjects);
                break;

            case "GIG_WORKER":
                // Gig worker sees recruitment-focused modules
                break;
        }
    }

    private void showNode(javafx.scene.Node node) {
        node.setManaged(true);
        node.setVisible(true);
    }

    private void loadDashboardStats() {
        try {
            List<User> allUsers = serviceUser.recuperer();
            statTotalUsers.setText(String.valueOf(allUsers.size()));

            long employees = allUsers.stream()
                    .filter(u -> u.getRole().equals("EMPLOYEE") || u.getRole().equals("PROJECT_OWNER"))
                    .count();
            statEmployees.setText(String.valueOf(employees));

            long gigWorkers = allUsers.stream()
                    .filter(u -> u.getRole().equals("GIG_WORKER"))
                    .count();
            statGigWorkers.setText(String.valueOf(gigWorkers));

            statRole.setText(SessionManager.getInstance().getCurrentRole().replace("_", " "));

        } catch (SQLException e) {
            System.err.println("Failed to load stats: " + e.getMessage());
        }
    }

    // ========== Navigation Methods ==========

    @FXML
    private void showDashboardHome() {
        setContentVisible(dashboardHome);
        loadDashboardStats();
    }

    @FXML
    private void showProfile() {
        loadContent("/fxml/Profile.fxml");
    }

    @FXML
    private void showManageUsers() {
        loadContent("/fxml/AdminUsers.fxml");
    }

    @FXML
    private void showHrDashboard() {
        showPlaceholder("HR Dashboard", "HR tools will be implemented by your teammate.");
    }

    @FXML
    private void showMessages() {
        loadContent("/fxml/Chat.fxml");
    }

    @FXML
    private void showRecruitment() {
        // Linking Interview screen here for now as it's part of the User &
        // Communication module
        loadContent("/fxml/Interview.fxml");
    }

    @FXML
    private void showCourses() {
        loadContent("/fxml/CourseManagement.fxml");
    }

    @FXML
    private void showResources() {
        loadContent("/fxml/ResourceManagement.fxml");
    }

    @FXML
    private void showQuizzes() {
        loadContent("/fxml/QuizManagement.fxml");
    }

    @FXML
    private void showPlaceholder() {
        showPlaceholder("Module Placeholder", "This module will be implemented by a teammate.");
    }

    private void showPlaceholder(String title, String description) {
        VBox placeholder = new VBox(16);
        placeholder.setAlignment(javafx.geometry.Pos.CENTER);
        placeholder.setStyle("-fx-padding: 60;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("content-title");

        Label descLabel = new Label(description);
        descLabel.getStyleClass().add("content-subtitle");
        descLabel.setStyle("-fx-text-fill: #888;");

        Label icon = new Label("🚧");
        icon.setStyle("-fx-font-size: 48;");

        placeholder.getChildren().addAll(icon, titleLabel, descLabel);
        contentArea.getChildren().setAll(placeholder);
    }

    private void loadContent(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();
            contentArea.getChildren().setAll(content);
        } catch (IOException e) {
            showPlaceholder("Error", "Failed to load: " + e.getMessage());
        }
    }

    private void setContentVisible(javafx.scene.Node node) {
        contentArea.getChildren().setAll(node);
    }

    @FXML
    private void handleLogout() {
        SessionManager.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);
        } catch (IOException e) {
            System.err.println("Failed to logout: " + e.getMessage());
        }
    }
}
