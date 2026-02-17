package controllers;

import entities.User;
import entities.Interview;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import services.ServiceUser;
import services.ServiceInterview;
import services.ServiceChatRoom;
import services.ServiceMessage;
import utils.SessionManager;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

public class DashboardController {

    // Top bar
    @FXML
    private Label welcomeLabel;
    @FXML
    private Label roleLabel;
    @FXML
    private HBox themeToggleTrack;
    @FXML
    private Region themeToggleThumb;

    // Sidebar
    @FXML
    private VBox sidebar;
    @FXML
    private Button btnDashboard;

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
    private Button btnTraining;
    @FXML
    private Button btnCommunity;

    // Content area
    @FXML
    private StackPane contentArea;
    @FXML
    private ScrollPane dashboardScroll;
    @FXML
    private VBox dashboardHome;

    // Stats - top cards
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
    private Label statInterviews;
    @FXML
    private Label statTotalUsersTrend;
    @FXML
    private Label statEmployeesTrend;
    @FXML
    private Label statGigWorkersTrend;
    @FXML
    private Label statInterviewsTrend;

    // Account card
    @FXML
    private Label avatarInitial;
    @FXML
    private Label accountName;
    @FXML
    private Label accountEmail;
    @FXML
    private Label statRole;
    @FXML
    private Label accountCreatedAt;

    // Platform overview
    @FXML
    private Label statChatRooms;
    @FXML
    private Label statMessages;
    @FXML
    private Label statPendingInterviews;

    // Sidebar user card
    @FXML
    private Label sidebarUserName;
    @FXML
    private Label sidebarUserEmail;
    @FXML
    private VBox sidebarUserCard;
    @FXML
    private Label sidebarAvatarInitial;

    private ContextMenu userContextMenu;
    private boolean isDarkTheme = true;
    private static final String LIGHT_THEME_PATH = "/css/light-theme.css";

    private ServiceUser serviceUser = new ServiceUser();
    private ServiceInterview serviceInterview = new ServiceInterview();
    private ServiceChatRoom serviceChatRoom = new ServiceChatRoom();
    private ServiceMessage serviceMessage = new ServiceMessage();

    @FXML
    public void initialize() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        // Set welcome info
        welcomeLabel.setText("Welcome, " + currentUser.getFirstName());
        roleLabel.setText(currentUser.getRole().replace("_", " "));

        // Sidebar user info
        sidebarUserName.setText(currentUser.getFirstName() + " " + currentUser.getLastName());
        sidebarUserEmail.setText(currentUser.getEmail());
        sidebarAvatarInitial.setText(currentUser.getFirstName().substring(0, 1).toUpperCase());

        // Account card
        avatarInitial.setText(currentUser.getFirstName().substring(0, 1).toUpperCase());
        accountName.setText(currentUser.getFirstName() + " " + currentUser.getLastName());
        accountEmail.setText(currentUser.getEmail());
        statRole.setText(currentUser.getRole().replace("_", " "));
        if (currentUser.getCreatedAt() != null) {
            accountCreatedAt.setText(new SimpleDateFormat("MMM dd, yyyy").format(currentUser.getCreatedAt()));
        }

        // Configure sidebar based on role
        configureRoleAccess(currentUser.getRole());

        // Build user popup menu
        buildUserContextMenu();

        // Set initial active button
        setActiveButton(btnDashboard);

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
                showNode(btnRecruitment);
                break;

            case "HR_MANAGER":
                // HR sees HR tools + employee management
                showNode(hrSectionLabel);
                showNode(btnHrDashboard);
                showNode(btnHrAdmin);
                showNode(btnProjects);
                showNode(btnRecruitment);
                break;

            case "EMPLOYEE":
                // Employee sees basic modules — NO interviews
                showNode(btnHrAdmin); // can view attendance/leave
                break;

            case "PROJECT_OWNER":
                // Employee + Projects/Tasks + Interviews
                showNode(btnHrAdmin);
                showNode(btnProjects);
                showNode(btnRecruitment);
                break;

            case "GIG_WORKER":
                // Gig worker sees recruitment-focused modules — interviews where they are candidates
                showNode(btnRecruitment);
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
            int totalUsers = allUsers.size();
            statTotalUsers.setText(String.valueOf(totalUsers));

            long employees = allUsers.stream()
                    .filter(u -> u.getRole().equals("EMPLOYEE") || u.getRole().equals("PROJECT_OWNER"))
                    .count();
            statEmployees.setText(String.valueOf(employees));

            long gigWorkers = allUsers.stream()
                    .filter(u -> u.getRole().equals("GIG_WORKER"))
                    .count();
            statGigWorkers.setText(String.valueOf(gigWorkers));

            // Interviews count
            List<Interview> allInterviews = serviceInterview.recuperer();
            statInterviews.setText(String.valueOf(allInterviews.size()));

            long pendingInterviews = allInterviews.stream()
                    .filter(i -> "PENDING".equals(i.getStatus()))
                    .count();
            statPendingInterviews.setText(String.valueOf(pendingInterviews));
            statInterviewsTrend.setText(pendingInterviews + " pending");

            // Chat & messages
            int chatRooms = serviceChatRoom.recuperer().size();
            statChatRooms.setText(String.valueOf(chatRooms));

            int totalMessages = serviceMessage.recuperer().size();
            statMessages.setText(String.valueOf(totalMessages));

        } catch (SQLException e) {
            System.err.println("Failed to load stats: " + e.getMessage());
        }
    }

    // ========== Active Button Tracking ==========

    private void setActiveButton(Button activeBtn) {
        List<Button> allButtons = Arrays.asList(
                btnDashboard, btnManageUsers, btnHrDashboard,
                btnMessages, btnRecruitment, btnHrAdmin, btnProjects,
                btnTraining, btnCommunity
        );
        for (Button btn : allButtons) {
            btn.getStyleClass().remove("sidebar-btn-active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("sidebar-btn-active")) {
            activeBtn.getStyleClass().add("sidebar-btn-active");
        }
    }

    private void clearActiveButtons() {
        List<Button> allButtons = Arrays.asList(
                btnDashboard, btnManageUsers, btnHrDashboard,
                btnMessages, btnRecruitment, btnHrAdmin, btnProjects,
                btnTraining, btnCommunity
        );
        for (Button btn : allButtons) {
            btn.getStyleClass().remove("sidebar-btn-active");
        }
    }

    // ========== User Popup Menu ==========

    private void buildUserContextMenu() {
        userContextMenu = new ContextMenu();
        userContextMenu.getStyleClass().add("user-context-menu");

        MenuItem profileItem = new MenuItem("👤  Profile");
        profileItem.setOnAction(e -> showProfile());

        MenuItem settingsItem = new MenuItem("⚙  Settings");
        settingsItem.setOnAction(e -> showPlaceholder("Settings", "Settings will be implemented soon."));

        MenuItem logoutItem = new MenuItem("🚪  Log out");
        logoutItem.setOnAction(e -> handleLogout());

        userContextMenu.getItems().addAll(profileItem, new SeparatorMenuItem(), settingsItem, new SeparatorMenuItem(), logoutItem);
    }

    @FXML
    private void toggleUserMenu(MouseEvent event) {
        if (userContextMenu.isShowing()) {
            userContextMenu.hide();
        } else {
            Bounds bounds = sidebarUserCard.localToScreen(sidebarUserCard.getBoundsInLocal());
            userContextMenu.show(sidebarUserCard, bounds.getMinX(), bounds.getMinY() - 10);
        }
    }

    @FXML
    private void toggleTheme() {
        Scene scene = welcomeLabel.getScene();
        if (scene == null) return;
        String lightCss = getClass().getResource(LIGHT_THEME_PATH).toExternalForm();

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), themeToggleThumb);

        if (isDarkTheme) {
            scene.getStylesheets().add(lightCss);
            slide.setToX(-12);  // slide left toward sun
            isDarkTheme = false;
        } else {
            scene.getStylesheets().remove(lightCss);
            slide.setToX(12);   // slide right toward moon
            isDarkTheme = true;
        }
        slide.play();
    }

    // ========== Navigation Methods ==========

    @FXML
    private void showDashboardHome() {
        setActiveButton(btnDashboard);
        contentArea.getChildren().setAll(dashboardScroll);
        loadDashboardStats();
    }

    @FXML
    private void showProfile() {
        clearActiveButtons();
        loadContent("/fxml/Profile.fxml");
    }

    @FXML
    private void showManageUsers() {
        setActiveButton(btnManageUsers);
        loadContent("/fxml/AdminUsers.fxml");
    }

    @FXML
    private void showHrDashboard() {
        setActiveButton(btnHrDashboard);
        showPlaceholder("HR Dashboard", "HR tools will be implemented by your teammate.");
    }

    @FXML
    private void showMessages() {
        setActiveButton(btnMessages);
        loadContent("/fxml/Chat.fxml");
    }

    @FXML
    private void showRecruitment() {
        setActiveButton(btnRecruitment);
        // Linking Interview screen here for now as it's part of the User &
        // Communication module
        loadContent("/fxml/Interview.fxml");
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
