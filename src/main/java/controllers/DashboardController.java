package controllers;

import entities.Call;
import entities.User;
import entities.Interview;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import services.ServiceAttendance;
import services.ServiceCall;
import services.ServiceUser;
import services.ServiceInterview;
import services.ServiceChatRoom;
import services.ServiceMessage;
import utils.AnimatedWeatherIcons;
import utils.AppThreadPool;
import utils.AudioCallService;
import utils.LanguageManager;
import utils.SessionManager;
import utils.SignalingService;
import utils.SoundManager;
import utils.WeatherService;
import controllers.NotificationPanel;
import services.ZAIService;
import utils.DialogHelper;

import com.google.gson.JsonObject;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class DashboardController {

    private static DashboardController instance;

    /** Other controllers call this to navigate. */
    public static DashboardController getInstance() { return instance; }

    // Top bar
    @FXML private Label welcomeLabel;
    @FXML private Label roleLabel;
    @FXML private HBox themeToggleTrack;
    @FXML private Region themeToggleThumb;
    @FXML private Button sidebarTrigger;
    @FXML private Label sidebarTriggerIcon;

    // Sidebar structure
    @FXML private VBox sidebar;
    @FXML private HBox sidebarHeader;
    @FXML private Label sidebarBrandName;
    @FXML private ScrollPane sidebarScroll;

    // Sidebar groups
    @FXML private VBox adminGroup;
    @FXML private VBox hrGroup;

    // Sidebar menu buttons
    @FXML private Button btnDashboard;
    @FXML private Button btnManageUsers;
    @FXML private Button btnHrDashboard;
    @FXML private Button btnHRBacklog;
    @FXML private Button btnMessages;
    @FXML private Button btnRecruitment;
    @FXML private Button btnProjects;
    @FXML private Button btnOffers;
    @FXML private Button btnTraining;
    @FXML private Button btnCommunity;
    @FXML private Button btnJobScanner;
    @FXML private Button btnAiAssistant;

    // Content area
    @FXML private StackPane contentArea;
    @FXML private ScrollPane dashboardScroll;
    @FXML private VBox dashboardHome;

    // Stats - top cards
    @FXML private Label dashboardTitle;
    @FXML private Label dashboardSubtitle;
    @FXML private Label statTotalUsers;
    @FXML private Label statEmployees;
    @FXML private Label statGigWorkers;
    @FXML private Label statInterviews;
    @FXML private Label statTotalUsersTrend;
    @FXML
    private Label statEmployeesTrend;
    @FXML
    private Label statGigWorkersTrend;
    @FXML
    private Label statInterviewsTrend;

    // Account card
    @FXML private Label avatarInitial;
    @FXML private Label accountName;
    @FXML private Label accountEmail;
    @FXML private Label statRole;
    @FXML private Label accountCreatedAt;

    // Platform overview
    @FXML private Label statChatRooms;
    @FXML private Label statMessages;
    @FXML private Label statPendingInterviews;

    // Interviews card
    @FXML private VBox interviewCard;

    // Dashboard stat sections (hidden for non-admin)
    @FXML private HBox statsCardsRow;
    @FXML private VBox platformOverviewCard;

    // Quote card on dashboard
    @FXML private VBox quoteCard;
    @FXML private Label quoteText;
    @FXML private Label quoteAuthor;
    @FXML private Button quoteRefreshBtn;

    // Weather card on dashboard
    @FXML private VBox weatherCard;
    @FXML private Label weatherCardEmoji;
    @FXML private Label weatherCardTitle;
    @FXML private Label weatherCardSubtitle;
    @FXML private Label weatherCardTemp;
    @FXML private Label weatherCardCondition;
    @FXML private Label weatherCardHumidity;
    @FXML private Label weatherCardWind;

    // Sidebar footer (user card)
    @FXML private Label sidebarUserName;
    @FXML private Label sidebarUserEmail;
    @FXML private VBox sidebarUserCard;
    @FXML private VBox sidebarUserInfo;
    @FXML private Label sidebarUserChevron;
    @FXML private Label sidebarAvatarInitial;
    @FXML private ImageView sidebarAvatarImage;
    @FXML private ImageView accountAvatarImage;

    private ContextMenu userContextMenu;
    private boolean isDarkTheme = true;
    private boolean sidebarCollapsed = false;
    private static final String LIGHT_THEME_PATH = "/css/light-theme.css";
    private static final double SIDEBAR_EXPANDED = 220;
    private static final double SIDEBAR_COLLAPSED = 60;

    private ServiceUser serviceUser = new ServiceUser();
    private ServiceInterview serviceInterview = new ServiceInterview();
    private ServiceChatRoom serviceChatRoom = new ServiceChatRoom();
    private ServiceMessage serviceMessage = new ServiceMessage();
    private ServiceCall serviceCall = new ServiceCall();

    // Track current loaded page
    private String currentPage = "";

    // Notification bell
    @FXML private StackPane notificationBellSlot;
    private NotificationPanel notificationPanel;

    // Global incoming call state
    private ScheduledExecutorService incomingCallPoller;
    private VBox incomingCallToast;
    private Call pendingIncomingCall;
    private AudioCallService audioCallService = new AudioCallService();
    private Call globalActiveCall;
    private Timeline callTimerTimeline;
    private Timeline globalCallStatusPoller;
    private long globalCallStart;
    private volatile boolean globalCallPollInFlight = false;

    @FXML
    public void initialize() {
        instance = this;
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        // Set welcome info
        welcomeLabel.setText("Welcome, " + currentUser.getFirstName());
        roleLabel.setText(formatRoleDisplay(currentUser.getRole()));

        // Sidebar user info
        sidebarUserName.setText(currentUser.getFirstName() + " " + currentUser.getLastName());
        sidebarUserEmail.setText(currentUser.getEmail());
        sidebarAvatarInitial.setText(currentUser.getFirstName().substring(0, 1).toUpperCase());

        // Account card
        avatarInitial.setText(currentUser.getFirstName().substring(0, 1).toUpperCase());
        accountName.setText(currentUser.getFirstName() + " " + currentUser.getLastName());
        accountEmail.setText(currentUser.getEmail());
        statRole.setText(formatRoleDisplay(currentUser.getRole()));
        if (currentUser.getCreatedAt() != null) {
            accountCreatedAt.setText(new SimpleDateFormat("MMM dd, yyyy").format(currentUser.getCreatedAt()));
        }

        // Load avatar images
        loadDashboardAvatars(currentUser);

        // Register avatar change listener for instant updates
        SessionManager.getInstance().setOnAvatarChanged(() -> {
            User refreshedUser = SessionManager.getInstance().getCurrentUser();
            if (refreshedUser != null) {
                loadDashboardAvatars(refreshedUser);
            }
        });

        // Configure sidebar based on role
        configureRoleAccess(currentUser.getRole());

        // Build user popup menu
        buildUserContextMenu();

        // Set initial active button
        setActiveButton(btnDashboard);

        // Apply i18n language to sidebar & welcome label
        applyLanguage();
        LanguageManager.getInstance().addChangeListener(() -> Platform.runLater(this::applyLanguage));

        // Load stats
        loadDashboardStats();

        // Register Ctrl+B keyboard shortcut for sidebar toggle
        sidebar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN),
                        () -> toggleSidebar()
                );

                // Sync theme state from SessionManager (carries swipe from Login/Signup)
                isDarkTheme = SessionManager.getInstance().isDarkTheme();
                if (!isDarkTheme) {
                    String lightCss = getClass().getResource(LIGHT_THEME_PATH).toExternalForm();
                    if (!newScene.getStylesheets().contains(lightCss)) {
                        newScene.getStylesheets().add(lightCss);
                    }
                    themeToggleThumb.setTranslateX(-12);
                }
            }
        });

        // Start global incoming call poller
        startIncomingCallPoller();

        // Connect SignalingService for real-time notifications (off FX thread — WebSocket .join() blocks)
        final int sigUserId = currentUser.getId();
        AppThreadPool.io(() -> connectSignaling(sigUserId));

        // Initialize notification bell
        notificationPanel = new NotificationPanel();
        if (notificationBellSlot != null) {
            notificationBellSlot.getChildren().add(notificationPanel.getNode());
        }
        notificationPanel.start(currentUser.getId());
        notificationPanel.setOnNotificationAction((type, refId) -> {
            if (refId != null && ("FRIEND_REQUEST".equals(type) || "NEW_FRIEND".equals(type))) {
                SessionManager.getInstance().setPendingCommunityProfile(refId);
                showCommunity();
            }
        });

        // Auto check-in attendance on login
        AppThreadPool.io(() -> {
            ServiceAttendance attService = new ServiceAttendance();
            attService.autoCheckIn(currentUser.getId());
        });

        // Load weather for dashboard card
        loadWeatherCard();

        // Load motivational quote
        loadQuote();
    }

    /**
     * Load avatar images into sidebar and account card ImageViews.
     */
    private void loadDashboardAvatars(User user) {
        if (user.getAvatarPath() != null && !user.getAvatarPath().isEmpty()) {
            java.io.File avatarFile = new java.io.File(user.getAvatarPath());
            if (avatarFile.exists()) {
                Image avatarImg = new Image(avatarFile.toURI().toString());

                // Sidebar avatar (32x32)
                sidebarAvatarImage.setImage(avatarImg);
                sidebarAvatarImage.setVisible(true);
                sidebarAvatarImage.setManaged(true);
                sidebarAvatarInitial.getParent().setVisible(false);
                sidebarAvatarInitial.getParent().setManaged(false);

                // Account card avatar (40x40)
                accountAvatarImage.setImage(avatarImg);
                accountAvatarImage.setVisible(true);
                accountAvatarImage.setManaged(true);
                avatarInitial.getParent().setVisible(false);
                avatarInitial.getParent().setManaged(false);
            }
        }
    }

    private void configureRoleAccess(String role) {
        switch (role) {
            case "ADMIN":
                // Full access to everything
                showNode(adminGroup);
                showNode(hrGroup);
                showNode(btnProjects);
                showNode(btnOffers);
                showNode(btnRecruitment);
                break;

            case "HR_MANAGER":
                // HR dashboard + recruitment + projects + offers
                showNode(hrGroup);
                showNode(btnProjects);
                showNode(btnOffers);
                showNode(btnRecruitment);
                break;

            case "PROJECT_OWNER":
                // Projects + Offers (manages projects, posts offers)
                showNode(btnProjects);
                showNode(btnOffers);
                // Hide admin-level dashboards stats
                hideNode(statsCardsRow);
                hideNode(platformOverviewCard);
                break;

            case "EMPLOYEE":
                // Projects + Offers + HR (leave requests, attendance)
                showNode(hrGroup);
                showNode(btnProjects);
                showNode(btnOffers);
                // Hide admin-level dashboard stats
                hideNode(statsCardsRow);
                hideNode(platformOverviewCard);
                break;

            case "GIG_WORKER":
                // Projects + Offers + HR (leave requests, attendance)
                showNode(hrGroup);
                showNode(btnProjects);
                showNode(btnOffers);
                // Hide admin-level dashboard stats
                hideNode(statsCardsRow);
                hideNode(platformOverviewCard);
                break;
        }
    }

    private void showNode(javafx.scene.Node node) {
        node.setManaged(true);
        node.setVisible(true);
    }

    private void hideNode(javafx.scene.Node node) {
        if (node != null) {
            node.setManaged(false);
            node.setVisible(false);
        }
    }

    /** Display-friendly role name (PROJECT_OWNER → Project Manager). */
    private String formatRoleDisplay(String role) {
        if ("PROJECT_OWNER".equals(role)) return "PROJECT MANAGER";
        return role.replace("_", " ");
    }

    private void loadDashboardStats() {
        // Run DB queries on background thread to avoid freezing the UI
        AppThreadPool.io(() -> {
            try {
                // Fire all 4 data fetches in parallel
                CompletableFuture<List<User>> usersFuture = CompletableFuture.supplyAsync(() -> {
                    try { return serviceUser.recuperer(); }
                    catch (Exception e) { return java.util.Collections.emptyList(); }
                });
                CompletableFuture<List<Interview>> interviewsFuture = CompletableFuture.supplyAsync(() -> {
                    try { return serviceInterview.recuperer(); }
                    catch (Exception e) { return java.util.Collections.emptyList(); }
                });
                CompletableFuture<Integer> chatRoomsFuture = CompletableFuture.supplyAsync(() -> {
                    try { return serviceChatRoom.count(); }
                    catch (Exception e) { return 0; }
                });
                CompletableFuture<Integer> messagesFuture = CompletableFuture.supplyAsync(() -> {
                    try { return serviceMessage.count(); }
                    catch (Exception e) { return 0; }
                });

                // Wait for all to complete (parallel, not sequential)
                CompletableFuture.allOf(usersFuture, interviewsFuture, chatRoomsFuture, messagesFuture)
                        .join();

                List<User> allUsers = usersFuture.join();
                int totalUsers = allUsers.size();

                long employees = allUsers.stream()
                        .filter(u -> "EMPLOYEE".equals(u.getRole()) || "PROJECT_OWNER".equals(u.getRole()))
                        .count();

                long gigWorkers = allUsers.stream()
                        .filter(u -> "GIG_WORKER".equals(u.getRole()))
                        .count();

                List<Interview> allInterviews = interviewsFuture.join();
                int interviewCount = allInterviews.size();

                long pendingInterviews = allInterviews.stream()
                        .filter(i -> "PENDING".equals(i.getStatus()))
                        .count();

                int chatRooms = chatRoomsFuture.join();
                int totalMessages = messagesFuture.join();

                // Update UI on FX thread
                javafx.application.Platform.runLater(() -> {
                    statTotalUsers.setText(String.valueOf(totalUsers));
                    statEmployees.setText(String.valueOf(employees));
                    statGigWorkers.setText(String.valueOf(gigWorkers));
                    statInterviews.setText(String.valueOf(interviewCount));
                    statPendingInterviews.setText(String.valueOf(pendingInterviews));
                    statInterviewsTrend.setText(pendingInterviews + " pending");
                    statChatRooms.setText(String.valueOf(chatRooms));
                    statMessages.setText(String.valueOf(totalMessages));
                });

            } catch (Exception e) {
                System.err.println("Failed to load stats: " + e.getMessage());
            }
        });
    }

    // ========== Active Button Tracking ==========

    private void setActiveButton(Button activeBtn) {
        List<Button> allButtons = Arrays.asList(
                btnDashboard, btnManageUsers, btnHrDashboard,
                btnMessages, btnRecruitment, btnProjects,
                btnOffers, btnTraining, btnJobScanner, btnCommunity,
                btnAiAssistant
        );
        for (Button btn : allButtons) {
            btn.getStyleClass().remove("sidebar-btn-active");
            btn.getStyleClass().remove("sidebar-menu-btn-active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("sidebar-menu-btn-active")) {
            activeBtn.getStyleClass().add("sidebar-menu-btn-active");
        }
    }

    private void clearActiveButtons() {
        List<Button> allButtons = Arrays.asList(
                btnDashboard, btnManageUsers, btnHrDashboard,
                btnMessages, btnRecruitment, btnProjects,
                btnOffers, btnTraining, btnJobScanner, btnCommunity,
                btnAiAssistant
        );
        for (Button btn : allButtons) {
            btn.getStyleClass().remove("sidebar-btn-active");
            btn.getStyleClass().remove("sidebar-menu-btn-active");
        }
    }

    // ========== Sidebar Collapse/Expand ==========

    @FXML
    private void toggleSidebar() {
        SoundManager.getInstance().play(SoundManager.SIDEBAR_TOGGLE);
        sidebarCollapsed = !sidebarCollapsed;
        double target = sidebarCollapsed ? SIDEBAR_COLLAPSED : SIDEBAR_EXPANDED;

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(200),
                        new KeyValue(sidebar.prefWidthProperty(), target, Interpolator.EASE_BOTH),
                        new KeyValue(sidebar.maxWidthProperty(), target, Interpolator.EASE_BOTH),
                        new KeyValue(sidebar.minWidthProperty(), target, Interpolator.EASE_BOTH)
                )
        );

        if (sidebarCollapsed) {
            setSidebarTextVisible(false);
            sidebarTriggerIcon.setText("\u25b6");
        } else {
            timeline.setOnFinished(e -> setSidebarTextVisible(true));
            sidebarTriggerIcon.setText("\u2630");
        }

        timeline.play();
    }

    private void setSidebarTextVisible(boolean visible) {
        // Brand name
        sidebarBrandName.setVisible(visible);
        sidebarBrandName.setManaged(visible);

        // Group labels
        sidebar.lookupAll(".sidebar-group-label").forEach(n -> {
            n.setVisible(visible);
            n.setManaged(visible);
        });

        // Menu text labels (inside button graphics)
        sidebar.lookupAll(".sidebar-menu-text").forEach(n -> {
            n.setVisible(visible);
            n.setManaged(visible);
        });

        // User card info
        sidebarUserInfo.setVisible(visible);
        sidebarUserInfo.setManaged(visible);
        sidebarUserChevron.setVisible(visible);
        sidebarUserChevron.setManaged(visible);

        // Toggle CSS class
        if (!visible) {
            if (!sidebar.getStyleClass().contains("sidebar-collapsed")) {
                sidebar.getStyleClass().add("sidebar-collapsed");
            }
            addCollapsedTooltips();
        } else {
            sidebar.getStyleClass().remove("sidebar-collapsed");
            removeCollapsedTooltips();
        }
    }

    private final Map<Button, String> btnTooltipMap = new LinkedHashMap<>();

    private void addCollapsedTooltips() {
        if (btnTooltipMap.isEmpty()) {
            LanguageManager lang = LanguageManager.getInstance();
            btnTooltipMap.put(btnDashboard,   lang.get("sidebar.dashboard"));
            btnTooltipMap.put(btnManageUsers,  lang.get("sidebar.users"));
            btnTooltipMap.put(btnHrDashboard,  lang.get("sidebar.hrDashboard"));
            btnTooltipMap.put(btnMessages,     lang.get("sidebar.messages"));
            btnTooltipMap.put(btnRecruitment,  lang.get("sidebar.interviews"));
            btnTooltipMap.put(btnProjects,     lang.get("sidebar.projects"));
            btnTooltipMap.put(btnOffers,       lang.get("sidebar.offers"));
            btnTooltipMap.put(btnTraining,     lang.get("sidebar.training"));
            btnTooltipMap.put(btnJobScanner,   lang.get("sidebar.jobScanner"));
            btnTooltipMap.put(btnCommunity,    lang.get("sidebar.community"));
        }
        btnTooltipMap.forEach((btn, tip) -> btn.setTooltip(new Tooltip(tip)));
    }

    private void removeCollapsedTooltips() {
        btnTooltipMap.forEach((btn, tip) -> btn.setTooltip(null));
    }

    // ========== i18n Language ==========

    /**
     * Apply the current LanguageManager language to the Dashboard sidebar,
     * welcome label, and collapsed tooltips.
     */
    private void applyLanguage() {
        LanguageManager lang = LanguageManager.getInstance();

        // Welcome label
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            welcomeLabel.setText(lang.get("dashboard.welcome") + ", " + currentUser.getFirstName());
        }

        // ── Sidebar group labels ──
        Map<String, String> groupMap = new LinkedHashMap<>();
        groupMap.put("Overview", "sidebar.overview");
        groupMap.put("Admin",    "sidebar.admin");
        groupMap.put("HR",       "sidebar.hr");
        groupMap.put("Modules",  "sidebar.modules");
        groupMap.put("AI",       "sidebar.ai");
        // also match already-translated text so re-apply works
        for (String key : new ArrayList<>(groupMap.values())) {
            groupMap.put(lang.get(key), key);
        }
        sidebar.lookupAll(".sidebar-group-label").forEach(n -> {
            if (n instanceof Label lbl) {
                String k = groupMap.get(lbl.getText());
                if (k != null) lbl.setText(lang.get(k));
            }
        });

        // ── Sidebar menu-text labels (inside button graphics) ──
        Map<Button, String> btnKeyMap = new LinkedHashMap<>();
        btnKeyMap.put(btnDashboard,    "sidebar.dashboard");
        btnKeyMap.put(btnManageUsers,  "sidebar.users");
        btnKeyMap.put(btnHrDashboard,  "sidebar.hrDashboard");
        btnKeyMap.put(btnHRBacklog,    "sidebar.hrBacklog");
        btnKeyMap.put(btnMessages,     "sidebar.messages");
        btnKeyMap.put(btnRecruitment,  "sidebar.interviews");
        btnKeyMap.put(btnProjects,     "sidebar.projects");
        btnKeyMap.put(btnOffers,       "sidebar.offers");
        btnKeyMap.put(btnTraining,     "sidebar.training");
        btnKeyMap.put(btnJobScanner,   "sidebar.jobScanner");
        btnKeyMap.put(btnCommunity,    "sidebar.community");
        btnKeyMap.put(btnAiAssistant,  "sidebar.aiAssistant");

        btnKeyMap.forEach((btn, key) -> {
            if (btn == null) return;
            // The button graphic is HBox > [icon Label, text Label]
            if (btn.getGraphic() instanceof HBox hbox) {
                for (javafx.scene.Node child : hbox.getChildren()) {
                    if (child instanceof Label lbl && lbl.getStyleClass().contains("sidebar-menu-text")) {
                        lbl.setText(lang.get(key));
                    }
                }
            }
        });

        // Update collapsed-sidebar tooltips to translated text
        btnTooltipMap.clear();
        btnKeyMap.forEach((btn, key) -> {
            if (btn != null) btnTooltipMap.put(btn, lang.get(key));
        });
        // Re-apply tooltips if sidebar is currently collapsed
        if (sidebar.getStyleClass().contains("sidebar-collapsed")) {
            btnTooltipMap.forEach((btn, tip) -> btn.setTooltip(new Tooltip(tip)));
        }
    }

    // ========== User Popup Menu ==========

    private void buildUserContextMenu() {
        userContextMenu = new ContextMenu();
        userContextMenu.getStyleClass().add("user-context-menu");

        MenuItem profileItem = new MenuItem("👤  Profile");
        profileItem.setOnAction(e -> showProfile());

        MenuItem settingsItem = new MenuItem("⚙  Settings");
        settingsItem.setOnAction(e -> showVoiceSettings());

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
        SessionManager.getInstance().setDarkTheme(isDarkTheme);
        slide.play();
        SoundManager.getInstance().play(SoundManager.THEME_TOGGLE);
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
        loadContent("/fxml/HRModule.fxml");
    }

    @FXML
    private void showHRBacklog() {
        setActiveButton(btnHRBacklog);
        loadContent("/fxml/HRBacklog.fxml");
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
    private void showCommunity() {
        setActiveButton(btnCommunity);
        loadContent("/fxml/Community.fxml");
    }

    @FXML
    private void showJobScanner() {
        setActiveButton(btnJobScanner);
        loadContent("/fxml/JobScanner.fxml");
    }

    @FXML
    private void showProjects() {
        setActiveButton(btnProjects);
        loadContent("/fxml/ProjectManagement.fxml");
    }

    @FXML
    private void showOffers() {
        setActiveButton(btnOffers);
        loadContent("/fxml/OfferManagement.fxml");
    }

    @FXML
    private void showTraining() {
        setActiveButton(btnTraining);
        loadContent("/fxml/Training.fxml");
    }

    // ════════════════════════════════════════════════════════
    //  UNIVERSAL AI ASSISTANT
    // ════════════════════════════════════════════════════════

    @FXML
    private void showAiAssistant() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("AI Assistant");
        dialog.setHeaderText("🤖 SynergyGig AI Assistant");
        DialogHelper.theme(dialog);

        VBox chatBox = new VBox(10);
        chatBox.setPrefWidth(550);
        chatBox.setPrefHeight(420);
        chatBox.setStyle("-fx-padding: 10;");

        // Chat history area
        VBox messagesBox = new VBox(8);
        messagesBox.setStyle("-fx-padding: 5;");

        ScrollPane scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setPrefHeight(320);
        scrollPane.getStyleClass().add("ai-chat-scroll");

        // Welcome bubble
        Label welcome = new Label("Hi! I'm your AI assistant. Ask me anything about:\n" +
                "  \u2022 Projects & tasks\n  \u2022 HR & payroll\n  \u2022 Training & courses\n" +
                "  \u2022 Community posts\n  \u2022 Offers & contracts\n  \u2022 Or any general question!");
        welcome.setWrapText(true);
        welcome.setStyle("-fx-background-color: #2a2d35; -fx-text-fill: #e0e0e0; -fx-padding: 10; " +
                "-fx-background-radius: 10; -fx-max-width: 420;");
        messagesBox.getChildren().add(welcome);

        // Input row
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);
        TextField inputField = new TextField();
        inputField.setPromptText("Ask me anything...");
        inputField.setPrefWidth(430);
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendBtn = new Button("Send");
        sendBtn.getStyleClass().addAll("btn", "btn-primary");

        inputRow.getChildren().addAll(inputField, sendBtn);

        chatBox.getChildren().addAll(scrollPane, inputRow);
        dialog.getDialogPane().setContent(chatBox);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);

        // Gather context for routing
        String userRole = SessionManager.getInstance().getCurrentUser() != null ?
                SessionManager.getInstance().getCurrentUser().getRole() : "EMPLOYEE";
        String userName = SessionManager.getInstance().getCurrentUser() != null ?
                SessionManager.getInstance().getCurrentUser().getFirstName() : "User";

        Runnable sendAction = () -> {
            String question = inputField.getText();
            if (question == null || question.trim().isEmpty()) return;
            inputField.clear();

            // User bubble
            Label userBubble = new Label(question);
            userBubble.setWrapText(true);
            userBubble.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 10; " +
                    "-fx-background-radius: 10; -fx-max-width: 380;");
            HBox userRow = new HBox(userBubble);
            userRow.setAlignment(Pos.CENTER_RIGHT);
            messagesBox.getChildren().add(userRow);

            // Thinking indicator
            Label thinking = new Label("⏳ Thinking...");
            thinking.setStyle("-fx-text-fill: #888; -fx-padding: 5;");
            messagesBox.getChildren().add(thinking);
            scrollPane.setVvalue(1.0);

            sendBtn.setDisable(true);
            inputField.setDisable(true);

            final String q = question;
            AppThreadPool.io(() -> {
                try {
                    ZAIService zai = new ZAIService();
                    String systemPrompt = "You are the SynergyGig AI Assistant. " +
                            "You help " + userName + " (" + userRole + ") with questions about: " +
                            "HR (attendance, leave, payroll), Projects (tasks, sprints, kanban), " +
                            "Training (courses, enrollments, certificates), Community (posts, messages), " +
                            "Offers & Contracts (job offers, applications, contracts). " +
                            "Be concise, helpful, and professional. If asked about specific data, " +
                            "explain where to find it in the platform.";
                    String response = zai.chat(systemPrompt, q);

                    Platform.runLater(() -> {
                        messagesBox.getChildren().remove(thinking);
                        Label aiBubble = new Label(response);
                        aiBubble.setWrapText(true);
                        aiBubble.setStyle("-fx-background-color: #2a2d35; -fx-text-fill: #e0e0e0; " +
                                "-fx-padding: 10; -fx-background-radius: 10; -fx-max-width: 420;");
                        messagesBox.getChildren().add(aiBubble);
                        scrollPane.setVvalue(1.0);
                        sendBtn.setDisable(false);
                        inputField.setDisable(false);
                        inputField.requestFocus();
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        messagesBox.getChildren().remove(thinking);
                        Label errBubble = new Label("Sorry, I couldn't process that: " + e.getMessage());
                        errBubble.setWrapText(true);
                        errBubble.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; " +
                                "-fx-padding: 10; -fx-background-radius: 10; -fx-max-width: 420;");
                        messagesBox.getChildren().add(errBubble);
                        sendBtn.setDisable(false);
                        inputField.setDisable(false);
                    });
                }
            });
        };

        sendBtn.setOnAction(e -> sendAction.run());
        inputField.setOnAction(e -> sendAction.run());

        dialog.showAndWait();
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

    /** The currently loaded sub-controller (if it implements {@link Stoppable}). */
    private Stoppable activeSubController;

    private void loadContent(String fxmlPath) {
        try {
            // Stop the previous controller's background tasks before replacing the view
            if (activeSubController != null) {
                activeSubController.stop();
                activeSubController = null;
            }
            currentPage = fxmlPath;
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof Stoppable) {
                activeSubController = (Stoppable) ctrl;
            }
            contentArea.getChildren().setAll(content);
        } catch (IOException e) {
            showPlaceholder("Error", "Failed to load: " + e.getMessage());
        }
    }

    /**
     * Public navigation entry point — module controllers can call:
     * {@code DashboardController.getInstance().navigateTo("/fxml/ResumeParser.fxml");}
     */
    public void navigateTo(String fxmlPath) {
        loadContent(fxmlPath);
    }

    private void setContentVisible(javafx.scene.Node node) {
        contentArea.getChildren().setAll(node);
    }

    @FXML
    private void handleLogout() {
        SoundManager.getInstance().play(SoundManager.LOGOUT);
        stopIncomingCallPoller();

        // Auto check-out attendance on logout
        User logoutUser = SessionManager.getInstance().getCurrentUser();
        if (logoutUser != null) {
            AppThreadPool.io(() -> {
                ServiceAttendance attService = new ServiceAttendance();
                attService.autoCheckOut(logoutUser.getId());
            });
        }

        SessionManager.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
            Parent root = loader.load();
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
            scene.setFill(null);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);

            utils.ResizeHelper.addResizeListener(stage);
        } catch (IOException e) {
            System.err.println("Failed to logout: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  WEATHER DASHBOARD CARD + POPUP
    // ════════════════════════════════════════════════════════

    private void loadWeatherCard() {
        AppThreadPool.io(() -> {
            WeatherService.CurrentWeather w = WeatherService.fetch("Tunis");
            if (w != null) {
                Platform.runLater(() -> {
                    weatherCardEmoji.setText("");
                    weatherCardEmoji.setGraphic(AnimatedWeatherIcons.forCondition(w.condition, 24));
                    weatherCardTitle.setText("Weather");
                    weatherCardSubtitle.setText(w.city + ", " + w.country);
                    weatherCardTemp.setText(w.tempC + "°C");
                    weatherCardCondition.setText(w.condition);
                    weatherCardHumidity.setText("💧 " + w.humidity + "%");
                    weatherCardWind.setText("💨 " + w.windKmph + " km/h");
                });
            } else {
                Platform.runLater(() -> {
                    weatherCardSubtitle.setText("Unable to load");
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  MOTIVATIONAL QUOTE WIDGET
    // ════════════════════════════════════════════════════════

    @FXML
    private void refreshQuote() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        loadQuote();
    }

    private void loadQuote() {
        if (quoteText == null) return;
        quoteText.setText("Loading...");
        quoteAuthor.setText("");
        AppThreadPool.io(() -> {
            try {
                // ZenQuotes API - free, no key required
                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(8)).build();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://zenquotes.io/api/random"))
                        .timeout(java.time.Duration.ofSeconds(8))
                        .GET().build();
                java.net.http.HttpResponse<String> resp = client.send(req,
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonArray arr = com.google.gson.JsonParser
                            .parseString(resp.body()).getAsJsonArray();
                    if (!arr.isEmpty()) {
                        com.google.gson.JsonObject q = arr.get(0).getAsJsonObject();
                        String text = q.get("q").getAsString();
                        String author = q.get("a").getAsString();
                        Platform.runLater(() -> {
                            quoteText.setText("\"" + text + "\"");
                            quoteAuthor.setText("— " + author);
                        });
                        return;
                    }
                }
            } catch (Exception ignored) { }
            // Fallback quotes if API fails
            String[][] fallback = {
                {"The only way to do great work is to love what you do.", "Steve Jobs"},
                {"Innovation distinguishes between a leader and a follower.", "Steve Jobs"},
                {"Stay hungry, stay foolish.", "Steve Jobs"},
                {"The best time to plant a tree was 20 years ago. The second best time is now.", "Chinese Proverb"},
                {"Success is not final, failure is not fatal: it is the courage to continue that counts.", "Winston Churchill"},
                {"Believe you can and you're halfway there.", "Theodore Roosevelt"},
                {"It does not matter how slowly you go as long as you do not stop.", "Confucius"},
                {"The future belongs to those who believe in the beauty of their dreams.", "Eleanor Roosevelt"}
            };
            String[] pick = fallback[(int) (Math.random() * fallback.length)];
            Platform.runLater(() -> {
                quoteText.setText("\"" + pick[0] + "\"");
                quoteAuthor.setText("— " + pick[1]);
            });
        });
    }

    @FXML
    private void openWeatherPopup() {
        SoundManager.getInstance().play(SoundManager.WEATHER_REFRESH);
        WeatherPopup popup = new WeatherPopup();
        popup.show(weatherCard);
    }

    @FXML
    private void openInterviewCalendar() {
        SoundManager.getInstance().play(SoundManager.INTERVIEW_SCHEDULED);
        InterviewCalendarPopup cal = new InterviewCalendarPopup();
        cal.show(interviewCard);
    }

    // ════════════════════════════════════════════════════════
    //  SETTINGS DIALOG (Voice & Video + Notifications)
    // ════════════════════════════════════════════════════════

    private void showVoiceSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Settings.fxml"));
            Parent settingsRoot = loader.load();
            SettingsController ctrl = loader.getController();

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Settings");
            utils.DialogHelper.theme(dialog);
            dialog.getDialogPane().setContent(settingsRoot);
            utils.DialogHelper.hideCloseButton(dialog.getDialogPane());
            dialog.getDialogPane().getStyleClass().add("settings-dialog-pane");

            // Cleanup mic test on close
            dialog.setOnCloseRequest(e -> ctrl.cleanup());
            dialog.showAndWait();
        } catch (IOException e) {
            System.err.println("Failed to open settings: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  GLOBAL INCOMING CALL NOTIFICATION (top-right toast)
    // ════════════════════════════════════════════════════════

    // ════════════════════════════════════════════════════════
    //  SIGNALING SERVICE — real-time call & message notifications
    // ════════════════════════════════════════════════════════

    private void connectSignaling(int userId) {
        SignalingService sig = SignalingService.getInstance();
        sig.connect(userId);

        // Instant incoming call notification via signaling
        sig.onMessage("call-state", msg -> {
            try {
                JsonObject data = msg.getAsJsonObject("data");
                String state = data.has("state") ? data.get("state").getAsString() : "";
                if ("incoming-call".equals(state)) {
                    int callId = data.get("callId").getAsInt();
                    String callerName = data.has("callerName") ? data.get("callerName").getAsString() : "Unknown";
                    // Skip if Chat page is active — ChatController handles incoming calls with video support
                    if ("/fxml/Chat.fxml".equals(currentPage)) return;
                    if (pendingIncomingCall == null && globalActiveCall == null) {
                        Call incoming = serviceCall.getCall(callId);
                        if (incoming != null && "pending".equals(incoming.getStatus())) {
                            pendingIncomingCall = incoming;
                            SoundManager.getInstance().playLoop(SoundManager.INCOMING_CALL);
                            showGlobalIncomingCallToast(incoming);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Signaling] call-state handler error: " + e.getMessage());
            }
        });

        // Real-time message notification
        sig.onMessage("new-message", msg -> {
            try {
                JsonObject data = msg.getAsJsonObject("data");
                String senderName = data.has("senderName") ? data.get("senderName").getAsString() : "Someone";
                String preview = data.has("preview") ? data.get("preview").getAsString() : "New message";
                SoundManager.getInstance().play(SoundManager.NEW_MESSAGE);
                showMessageToast(senderName, preview);
            } catch (Exception e) {
                System.err.println("[Signaling] new-message handler error: " + e.getMessage());
            }
        });
    }

    /** Show a brief toast notification for an incoming chat message. */
    private void showMessageToast(String senderName, String preview) {
        VBox toast = new VBox(4);
        toast.getStyleClass().add("incoming-call-toast"); // reuse call-toast styling
        toast.setAlignment(Pos.TOP_LEFT);
        toast.setMaxWidth(280);
        toast.setMaxHeight(80);
        toast.setPadding(new Insets(10, 14, 10, 14));

        Label icon = new Label("\uD83D\uDD14"); // 🔔
        icon.setStyle("-fx-font-size: 16;");

        Label nameLbl = new Label(senderName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13;");

        Label previewLbl = new Label(preview);
        previewLbl.setStyle("-fx-font-size: 12; -fx-opacity: 0.85;");
        previewLbl.setWrapText(true);
        previewLbl.setMaxWidth(240);

        HBox header = new HBox(6, icon, nameLbl);
        header.setAlignment(Pos.CENTER_LEFT);
        toast.getChildren().addAll(header, previewLbl);

        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new Insets(12, 12, 0, 0));

        contentArea.getChildren().add(toast);

        // Fade in
        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();

        // Auto-remove after 4 seconds
        PauseTransition hold = new PauseTransition(Duration.seconds(4));
        hold.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> contentArea.getChildren().remove(toast));
            fadeOut.play();
        });
        hold.play();
    }

    private void startIncomingCallPoller() {
        incomingCallPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "global-call-poller");
            t.setDaemon(true);
            return t;
        });

        incomingCallPoller.scheduleAtFixedRate(() -> {
            try {
                User user = SessionManager.getInstance().getCurrentUser();
                if (user == null) return;

                // Don't poll if we already have a call active or toast showing
                if (globalActiveCall != null || pendingIncomingCall != null) return;

                // Skip if Chat page is active — ChatController handles incoming calls with video support
                if ("/fxml/Chat.fxml".equals(currentPage)) return;

                Call incoming = serviceCall.getIncomingCall(user.getId());
                if (incoming != null) {
                    pendingIncomingCall = incoming;
                    Platform.runLater(() -> {
                        SoundManager.getInstance().playLoop(SoundManager.INCOMING_CALL);
                        showGlobalIncomingCallToast(incoming);
                    });
                }
            } catch (Exception e) {
                // Silently ignore polling errors
            }
        }, 1, 2, TimeUnit.SECONDS);
    }

    private void stopIncomingCallPoller() {
        if (incomingCallPoller != null && !incomingCallPoller.isShutdown()) {
            incomingCallPoller.shutdownNow();
        }
    }

    private void showGlobalIncomingCallToast(Call call) {
        // Resolve caller name from shared cache (avoids loading ALL users)
        String callerName = utils.UserNameCache.getName(call.getCallerId());

        // Build toast UI
        VBox toast = new VBox(8);
        toast.getStyleClass().add("incoming-call-toast");
        toast.setAlignment(Pos.CENTER);
        toast.setMaxWidth(300);
        toast.setMaxHeight(140);

        Label icon = new Label("\uD83D\uDCDE"); // 📞
        icon.setStyle("-fx-font-size: 28;");

        Label nameLbl = new Label(callerName);
        nameLbl.getStyleClass().add("call-toast-name");

        Label statusLbl = new Label("Incoming call...");
        statusLbl.getStyleClass().add("call-toast-status");

        HBox btnRow = new HBox(28);
        btnRow.setAlignment(Pos.CENTER);

        // Green circular accept button with phone icon
        Label acceptIcon = new Label("\u260E");
        acceptIcon.setStyle("-fx-font-size: 24; -fx-text-fill: white;");
        Button acceptBtn = new Button();
        acceptBtn.setGraphic(acceptIcon);
        acceptBtn.getStyleClass().add("call-circle-accept");

        // Red circular reject button with hangup icon
        Label rejectIcon = new Label("\u260E");
        rejectIcon.setStyle("-fx-font-size: 24; -fx-text-fill: white; -fx-rotate: 135;");
        Button rejectBtn = new Button();
        rejectBtn.setGraphic(rejectIcon);
        rejectBtn.getStyleClass().add("call-circle-reject");

        final String resolvedCallerName = callerName;

        acceptBtn.setOnAction(e -> {
            SoundManager.getInstance().stopLoop();
            SoundManager.getInstance().play(SoundManager.CALL_CONNECTED);
            serviceCall.acceptCall(call.getId());
            removeCallToast();
            startGlobalActiveCall(call, resolvedCallerName);
        });

        rejectBtn.setOnAction(e -> {
            SoundManager.getInstance().stopLoop();
            SoundManager.getInstance().play(SoundManager.CALL_ENDED);
            serviceCall.rejectCall(call.getId());
            removeCallToast();
            pendingIncomingCall = null;
        });

        btnRow.getChildren().addAll(acceptBtn, rejectBtn);
        toast.getChildren().addAll(icon, nameLbl, statusLbl, btnRow);

        // Position top-right in contentArea
        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new Insets(12, 12, 0, 0));

        incomingCallToast = toast;
        contentArea.getChildren().add(toast);
    }

    private void removeCallToast() {
        if (incomingCallToast != null) {
            contentArea.getChildren().remove(incomingCallToast);
            incomingCallToast = null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  GLOBAL ACTIVE CALL BAR (bottom of content area)
    // ════════════════════════════════════════════════════════

    private void startGlobalActiveCall(Call call, String peerName) {
        globalActiveCall = call;
        pendingIncomingCall = null;
        globalCallStart = System.currentTimeMillis();

        User me = SessionManager.getInstance().getCurrentUser();
        audioCallService.start(call.getId(), me.getId(), () -> {
            Platform.runLater(() -> endGlobalActiveCall());
        });

        // Build active call bar
        HBox callBar = new HBox(12);
        callBar.getStyleClass().add("global-call-bar");
        callBar.setAlignment(Pos.CENTER_LEFT);
        callBar.setPadding(new Insets(8, 16, 8, 16));
        callBar.setMaxHeight(44);
        callBar.setMinHeight(44);

        Label callIcon = new Label("📞");
        callIcon.setStyle("-fx-font-size: 16;");

        Label nameLabel = new Label("In call with " + peerName);
        nameLabel.getStyleClass().add("call-bar-label");

        Label timerLabel = new Label("00:00");
        timerLabel.getStyleClass().add("call-timer-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button muteBtn = new Button("🎤");
        muteBtn.getStyleClass().add("call-mute-btn");
        muteBtn.setOnAction(e -> {
            boolean muted = audioCallService.toggleMute();
            muteBtn.setText(muted ? "🔇" : "🎤");
            if (muted) {
                if (!muteBtn.getStyleClass().contains("call-muted"))
                    muteBtn.getStyleClass().add("call-muted");
            } else {
                muteBtn.getStyleClass().remove("call-muted");
            }
        });

        Button endBtn = new Button("End Call");
        endBtn.getStyleClass().addAll("call-overlay-btn", "btn-danger");
        endBtn.setOnAction(e -> {
            serviceCall.endCall(globalActiveCall.getId());
            endGlobalActiveCall();
        });

        // Volume slider for speaker during call
        Label volIcon = new Label("\uD83D\uDD0A");
        volIcon.setStyle("-fx-font-size: 14; -fx-text-fill: #90DDF0;");
        javafx.scene.control.Slider volSlider = new javafx.scene.control.Slider(0, 3.0,
                utils.AudioDeviceManager.getInstance().getSpeakerVolume());
        volSlider.setMaxWidth(100);
        volSlider.setPrefWidth(100);
        volSlider.getStyleClass().add("call-volume-slider");
        volSlider.valueProperty().addListener((obs, oldV, newV) -> {
            utils.AudioDeviceManager.getInstance().setSpeakerVolume(newV.doubleValue());
            if (newV.doubleValue() < 0.01) volIcon.setText("\uD83D\uDD07");
            else if (newV.doubleValue() < 1.0) volIcon.setText("\uD83D\uDD09");
            else volIcon.setText("\uD83D\uDD0A");
        });
        HBox volBox = new HBox(4, volIcon, volSlider);
        volBox.setAlignment(Pos.CENTER);

        callBar.getChildren().addAll(callIcon, nameLabel, timerLabel, spacer, volBox, muteBtn, endBtn);

        StackPane.setAlignment(callBar, Pos.BOTTOM_CENTER);
        contentArea.getChildren().add(callBar);

        // Timer
        callTimerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            long elapsed = (System.currentTimeMillis() - globalCallStart) / 1000;
            timerLabel.setText(String.format("%02d:%02d", elapsed / 60, elapsed % 60));
        }));
        callTimerTimeline.setCycleCount(Timeline.INDEFINITE);
        callTimerTimeline.play();

        // Poll call status to detect remote hang-up
        globalCallPollInFlight = false;
        globalCallStatusPoller = new Timeline(new KeyFrame(Duration.seconds(3), ev -> {
            if (globalActiveCall == null) return;
            if (globalCallPollInFlight) return; // skip if previous request still pending
            globalCallPollInFlight = true;
            AppThreadPool.io(() -> {
                try {
                    Call c = serviceCall.getCall(globalActiveCall.getId());
                    if (c != null && (c.isEnded() || "rejected".equals(c.getStatus()) || "missed".equals(c.getStatus()))) {
                        Platform.runLater(this::endGlobalActiveCall);
                    }
                } finally {
                    globalCallPollInFlight = false;
                }
            });
        }));
        globalCallStatusPoller.setCycleCount(Timeline.INDEFINITE);
        globalCallStatusPoller.play();
    }

    private void endGlobalActiveCall() {
        SoundManager.getInstance().stopLoop();
        SoundManager.getInstance().play(SoundManager.CALL_ENDED);
        if (callTimerTimeline != null) { callTimerTimeline.stop(); callTimerTimeline = null; }
        if (globalCallStatusPoller != null) { globalCallStatusPoller.stop(); globalCallStatusPoller = null; }
        globalCallPollInFlight = false;
        audioCallService.stop();
        globalActiveCall = null;
        pendingIncomingCall = null;

        // Remove call bar from contentArea (it's the last child with global-call-bar class)
        contentArea.getChildren().removeIf(n ->
                n.getStyleClass().contains("global-call-bar") ||
                n.getStyleClass().contains("incoming-call-toast"));
    }

    /** Disconnect SignalingService when leaving the dashboard. */
    public void stop() {
        stopIncomingCallPoller();
        SignalingService.getInstance().disconnect();
        if (globalActiveCall != null) {
            serviceCall.endCall(globalActiveCall.getId());
            endGlobalActiveCall();
        }
    }
}
