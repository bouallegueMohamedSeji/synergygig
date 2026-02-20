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
import services.ServiceCall;
import services.ServiceUser;
import services.ServiceInterview;
import services.ServiceChatRoom;
import services.ServiceMessage;
import utils.AudioCallService;
import utils.SessionManager;
import utils.SoundManager;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class DashboardController {

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
    @FXML private Button btnMessages;
    @FXML private Button btnRecruitment;
    @FXML private Button btnHrAdmin;
    @FXML private Button btnProjects;
    @FXML private Button btnTraining;
    @FXML private Button btnCommunity;

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

    // Global incoming call state
    private ScheduledExecutorService incomingCallPoller;
    private VBox incomingCallToast;
    private Call pendingIncomingCall;
    private AudioCallService audioCallService = new AudioCallService();
    private Call globalActiveCall;
    private Timeline callTimerTimeline;
    private Timeline globalCallStatusPoller;
    private long globalCallStart;

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
                showNode(adminGroup);
                showNode(hrGroup);
                showNode(btnHrAdmin);
                showNode(btnProjects);
                showNode(btnRecruitment);
                break;

            case "HR_MANAGER":
                showNode(hrGroup);
                showNode(btnHrAdmin);
                showNode(btnProjects);
                showNode(btnRecruitment);
                break;

            case "EMPLOYEE":
                showNode(btnHrAdmin);
                break;

            case "PROJECT_OWNER":
                showNode(btnHrAdmin);
                showNode(btnProjects);
                showNode(btnRecruitment);
                break;

            case "GIG_WORKER":
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
            btn.getStyleClass().remove("sidebar-menu-btn-active");
        }
        if (activeBtn != null && !activeBtn.getStyleClass().contains("sidebar-menu-btn-active")) {
            activeBtn.getStyleClass().add("sidebar-menu-btn-active");
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
            btn.getStyleClass().remove("sidebar-menu-btn-active");
        }
    }

    // ========== Sidebar Collapse/Expand ==========

    @FXML
    private void toggleSidebar() {
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
            btnTooltipMap.put(btnDashboard, "Dashboard");
            btnTooltipMap.put(btnManageUsers, "Users");
            btnTooltipMap.put(btnHrDashboard, "HR Dashboard");
            btnTooltipMap.put(btnMessages, "Messages");
            btnTooltipMap.put(btnRecruitment, "Interviews");
            btnTooltipMap.put(btnHrAdmin, "HR Admin");
            btnTooltipMap.put(btnProjects, "Projects");
            btnTooltipMap.put(btnTraining, "Training");
            btnTooltipMap.put(btnCommunity, "Community");
        }
        btnTooltipMap.forEach((btn, tip) -> btn.setTooltip(new Tooltip(tip)));
    }

    private void removeCollapsedTooltips() {
        btnTooltipMap.forEach((btn, tip) -> btn.setTooltip(null));
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
        stopIncomingCallPoller();
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
    //  SETTINGS DIALOG (Voice & Video + Notifications)
    // ════════════════════════════════════════════════════════

    private void showVoiceSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Settings.fxml"));
            Parent settingsRoot = loader.load();
            SettingsController ctrl = loader.getController();

            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Settings");
            dialog.getDialogPane().setContent(settingsRoot);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().getStylesheets().add(
                    getClass().getResource("/css/style.css").toExternalForm());
            if (!isDarkTheme) {
                dialog.getDialogPane().getStylesheets().add(
                        getClass().getResource(LIGHT_THEME_PATH).toExternalForm());
            }
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
        // Resolve caller name
        String callerName = "Unknown";
        try {
            List<User> users = serviceUser.recuperer();
            for (User u : users) {
                if (u.getId() == call.getCallerId()) {
                    callerName = u.getFirstName() + " " + u.getLastName();
                    break;
                }
            }
        } catch (Exception ignored) {}

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

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        Button acceptBtn = new Button("Accept");
        acceptBtn.getStyleClass().addAll("call-overlay-btn", "btn-success");

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().addAll("call-overlay-btn", "btn-danger");

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

        callBar.getChildren().addAll(callIcon, nameLabel, timerLabel, spacer, muteBtn, endBtn);

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
        globalCallStatusPoller = new Timeline(new KeyFrame(Duration.seconds(2), ev -> {
            if (globalActiveCall == null) return;
            new Thread(() -> {
                Call c = serviceCall.getCall(globalActiveCall.getId());
                if (c != null && (c.isEnded() || "rejected".equals(c.getStatus()) || "missed".equals(c.getStatus()))) {
                    Platform.runLater(this::endGlobalActiveCall);
                }
            }, "global-call-status-check").start();
        }));
        globalCallStatusPoller.setCycleCount(Timeline.INDEFINITE);
        globalCallStatusPoller.play();
    }

    private void endGlobalActiveCall() {
        SoundManager.getInstance().stopLoop();
        SoundManager.getInstance().play(SoundManager.CALL_ENDED);
        if (callTimerTimeline != null) { callTimerTimeline.stop(); callTimerTimeline = null; }
        if (globalCallStatusPoller != null) { globalCallStatusPoller.stop(); globalCallStatusPoller = null; }
        audioCallService.stop();
        globalActiveCall = null;
        pendingIncomingCall = null;

        // Remove call bar from contentArea (it's the last child with global-call-bar class)
        contentArea.getChildren().removeIf(n ->
                n.getStyleClass().contains("global-call-bar") ||
                n.getStyleClass().contains("incoming-call-toast"));
    }
}
