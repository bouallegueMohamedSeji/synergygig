package controllers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entities.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.control.cell.PropertyValueFactory;
import services.*;
import utils.AppThreadPool;
import utils.DialogHelper;
import utils.PayrollPdfExporter;
import utils.SessionManager;
import utils.SoundManager;
import services.ZAIService;

import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class HRModuleController {

    @FXML private BorderPane rootPane;
    @FXML private HBox tabBar;
    @FXML private StackPane contentArea;
    @FXML private Label headerTitle;
    @FXML private Label headerRole;

    private final ServiceDepartment serviceDepartment = new ServiceDepartment();
    private final ServiceAttendance serviceAttendance = new ServiceAttendance();
    private final ServiceLeave serviceLeave = new ServiceLeave();
    private final ServicePayroll servicePayroll = new ServicePayroll();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceNotification serviceNotification = new ServiceNotification();

    private User currentUser;
    private boolean isHrOrAdmin;
    private Button activeTab;
    private Region ambienceIndicator;

    // Leave table sort/search state
    private String leaveSortColumn = "";
    private boolean leaveSortAsc = true;
    private String leaveSearchQuery = "";

    // Cached data
    private List<User> allUsers = new ArrayList<>();

    // HTTP client for API calls
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final Gson gson = new Gson();

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        String role = currentUser != null ? currentUser.getRole() : "";
        isHrOrAdmin = "ADMIN".equals(role) || "HR_MANAGER".equals(role);

        headerRole.setText(isHrOrAdmin ? (role.equals("ADMIN") ? "Administrator" : "HR Manager") : "Employee View");

        // Load user names for lookups
        loadUserNames();

        // Build tabs based on role
        List<String[]> tabs = new ArrayList<>();
        tabs.add(new String[]{"📊", "Overview"});
        if (isHrOrAdmin) {
            tabs.add(new String[]{"🏢", "Departments"});
        }
        tabs.add(new String[]{"📅", "Attendance"});
        tabs.add(new String[]{"🏖", "Leaves"});
        if (isHrOrAdmin) {
            tabs.add(new String[]{"💰", "Payroll"});
        }
        tabs.add(new String[]{"🤖", "AI Insights"});
        if (isHrOrAdmin) {
            tabs.add(new String[]{"📋", "Onboarding"});
            tabs.add(new String[]{"💬", "Policy Bot"});
            tabs.add(new String[]{"📄", "Doc Scanner"});
            tabs.add(new String[]{"📑", "Backlog"});
            tabs.add(new String[]{"🏆", "Employee of Month"});
        }

        for (String[] tab : tabs) {
            Button btn = new Button(tab[0] + "  " + tab[1]);
            btn.getStyleClass().add("hr-tab-btn");
            btn.setUserData(tab[1]);
            btn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
                switchTab(btn, tab[1]);
            });
            tabBar.getChildren().add(btn);
        }

        // === Spotlight Pill Navbar ===
        setupSpotlightTabBar();

        // Select first tab
        if (!tabBar.getChildren().isEmpty()) {
            Button first = (Button) tabBar.getChildren().get(0);
            switchTab(first, "Overview");
        }

        // Register auto-payroll callback to refresh payroll list when scheduled generation runs
        utils.PayrollScheduler.getInstance().setOnPayrollGenerated(() -> {
            if (activeTab != null && activeTab.getText().contains("Payroll")) {
                showPayroll();
            }
        });
    }

    private void loadUserNames() {
        allUsers = utils.UserNameCache.refresh();
    }

    private String getUserName(int userId) {
        return utils.UserNameCache.getName(userId);
    }

    private void switchTab(Button btn, String tabName) {
        if (activeTab != null) activeTab.getStyleClass().remove("hr-tab-active");
        btn.getStyleClass().add("hr-tab-active");
        activeTab = btn;
        animateAmbienceToTab(btn);

        switch (tabName) {
            case "Overview":     showOverview(); break;
            case "Departments":  showDepartments(); break;
            case "Attendance":   showAttendance(); break;
            case "Leaves":       showLeaves(); break;
            case "Payroll":      showPayroll(); break;
            case "AI Insights": showAIInsights(); break;
            case "Onboarding":
                DashboardController.getInstance().navigateTo("/fxml/OnboardingChecklist.fxml"); break;
            case "Policy Bot":
                DashboardController.getInstance().navigateTo("/fxml/HRPolicyChat.fxml"); break;
            case "Doc Scanner":
                DashboardController.getInstance().navigateTo("/fxml/DocumentOCR.fxml"); break;
            case "Backlog":
                DashboardController.getInstance().navigateTo("/fxml/HRBacklog.fxml"); break;
            case "Employee of Month":
                DashboardController.getInstance().navigateTo("/fxml/EmployeeOfMonth.fxml"); break;
        }
    }

    // ==================== SPOTLIGHT PILL NAVBAR ====================

    private Region spotlightGlow;

    private void setupSpotlightTabBar() {
        VBox topBar = (VBox) tabBar.getParent();
        int tabBarIdx = topBar.getChildren().indexOf(tabBar);
        topBar.getChildren().remove(tabBar);

        // Outer container centers the pill
        StackPane navContainer = new StackPane();
        navContainer.getStyleClass().add("hr-nav-container");

        // The pill-shaped navbar
        StackPane pill = new StackPane();
        pill.getStyleClass().add("hr-nav-pill");

        // Set the tabBar as contents inside the pill
        tabBar.getStyleClass().remove("hr-tab-bar");
        tabBar.getStyleClass().add("hr-pill-tabs");
        tabBar.setAlignment(Pos.CENTER);

        // Spotlight glow (follows mouse - radial gradient from bottom)
        spotlightGlow = new Region();
        spotlightGlow.setMouseTransparent(true);
        spotlightGlow.setOpacity(0);
        spotlightGlow.getStyleClass().add("hr-spotlight-glow");

        // Active ambience line (animated under active tab)
        ambienceIndicator = new Region();
        ambienceIndicator.setManaged(false);
        ambienceIndicator.setPrefHeight(2);
        ambienceIndicator.setMaxHeight(2);
        ambienceIndicator.setPrefWidth(50);
        ambienceIndicator.setMaxWidth(50);
        ambienceIndicator.getStyleClass().add("hr-ambience-line");

        // Bottom track line inside pill
        Region trackLine = new Region();
        trackLine.setManaged(false);
        trackLine.setPrefHeight(1);
        trackLine.setMaxHeight(1);
        trackLine.getStyleClass().add("hr-track-line");

        pill.getChildren().addAll(tabBar, spotlightGlow, trackLine, ambienceIndicator);
        navContainer.getChildren().add(pill);
        topBar.getChildren().add(tabBarIdx, navContainer);

        // Position unmanaged layers when pill resizes
        pill.layoutBoundsProperty().addListener((obs, ov, nv) -> {
            double h = nv.getHeight();
            double w = nv.getWidth();
            trackLine.setLayoutY(h - 1);
            trackLine.setPrefWidth(w - 16); // inset from edges
            trackLine.setLayoutX(8);
            ambienceIndicator.setLayoutY(h - 2);
        });

        // Mouse spotlight: radial gradient follows cursor along the bottom
        tabBar.setOnMouseMoved(e -> {
            double xInPill = e.getX() + tabBar.getLayoutX();
            double pillW = pill.getWidth();
            if (pillW <= 0) return;
            double pct = Math.max(0, Math.min(100, (xInPill / pillW) * 100.0));
            spotlightGlow.setOpacity(1.0);
            spotlightGlow.setStyle(String.format(
                    "-fx-background-color: radial-gradient(center %.1f%% 100%%, radius 25%%, rgba(138,138,255,0.12), transparent);",
                    pct));
        });

        tabBar.setOnMouseExited(e -> {
            javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
                    javafx.util.Duration.millis(400), spotlightGlow);
            ft.setToValue(0);
            ft.play();
        });
    }

    /** Slide the ambience indicator to the active tab. */
    private void animateAmbienceToTab(Button btn) {
        if (ambienceIndicator == null) return;
        Platform.runLater(() -> {
            javafx.geometry.Bounds tabBounds = btn.getBoundsInParent();
            if (tabBounds.getWidth() == 0) return;
            // Offset for tabBar position inside pill
            double pillOffset = tabBar.getLayoutX();
            double targetX = pillOffset + tabBounds.getMinX() + tabBounds.getWidth() / 2
                    - ambienceIndicator.getPrefWidth() / 2;
            javafx.animation.KeyValue kv = new javafx.animation.KeyValue(
                    ambienceIndicator.layoutXProperty(), targetX,
                    javafx.animation.Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
            javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(350), kv);
            javafx.animation.Timeline tl = new javafx.animation.Timeline(kf);
            tl.play();
        });
    }

    // ==================== OVERVIEW TAB ====================

    private void showOverview() {
        // Show loading placeholder instantly
        Label loading = new Label("Loading overview...");
        loading.setStyle("-fx-font-size: 14; -fx-text-fill: #888; -fx-padding: 40;");
        contentArea.getChildren().setAll(loading);

        // Load data in background — scope to own records for non-HR
        AppThreadPool.io(() -> {
            try {
                List<Department> depts = serviceDepartment.recuperer();
                List<Attendance> attendance;
                List<Leave> leaves;
                List<Payroll> payrolls;
                if (isHrOrAdmin) {
                    attendance = serviceAttendance.recuperer();
                    leaves = serviceLeave.recuperer();
                    payrolls = servicePayroll.recuperer();
                } else {
                    attendance = serviceAttendance.getByUser(currentUser.getId());
                    leaves = serviceLeave.getByUser(currentUser.getId());
                    payrolls = java.util.Collections.emptyList();
                }

                Platform.runLater(() -> buildOverviewUI(depts, attendance, leaves, payrolls));
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    Label err = new Label("Error loading overview: " + e.getMessage());
                    err.setStyle("-fx-text-fill: red; -fx-padding: 40;");
                    contentArea.getChildren().setAll(err);
                });
            }
        });
    }

    private void buildOverviewUI(List<Department> depts, List<Attendance> attendance,
                                  List<Leave> leaves, List<Payroll> payrolls) {
        VBox view = new VBox(20);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        Label title = new Label("HR Overview");
        title.getStyleClass().add("hr-view-title");

        // Stats cards row
        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        long totalEmployees = allUsers.stream().filter(u -> !"ADMIN".equals(u.getRole())).count();
        long presentToday = attendance.stream()
                .filter(a -> a.getDate() != null && a.getDate().toLocalDate().equals(LocalDate.now()))
                .filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus()))
                .count();
        long pendingLeaves = leaves.stream().filter(l -> "PENDING".equals(l.getStatus())).count();
        long pendingPayrolls = payrolls.stream().filter(p -> "PENDING".equals(p.getStatus())).count();

        if (isHrOrAdmin) {
            statsRow.getChildren().addAll(
                    createStatCard("👥", "Total Employees", String.valueOf(totalEmployees), "hr-stat-blue"),
                    createStatCard("🏢", "Departments", String.valueOf(depts.size()), "hr-stat-green"),
                    createStatCard("✅", "Present Today", String.valueOf(presentToday), "hr-stat-purple"),
                    createStatCard("📋", "Pending Leaves", String.valueOf(pendingLeaves), "hr-stat-orange"),
                    createStatCard("💰", "Pending Payroll", String.valueOf(pendingPayrolls), "hr-stat-red")
            );
        } else {
            // Employees see only their own summary
            long myLeaves = leaves.size();
            long myPendingLeaves = leaves.stream().filter(l -> "PENDING".equals(l.getStatus())).count();
            long myPresentDays = attendance.stream()
                    .filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus())).count();
            statsRow.getChildren().addAll(
                    createStatCard("📋", "My Leave Requests", String.valueOf(myLeaves), "hr-stat-blue"),
                    createStatCard("⏳", "Pending", String.valueOf(myPendingLeaves), "hr-stat-orange"),
                    createStatCard("✅", "Days Present", String.valueOf(myPresentDays), "hr-stat-green")
            );
        }

        // ── API Sections ──
        HBox apiRow = new HBox(16);
        apiRow.setAlignment(Pos.TOP_LEFT);

        VBox holidaySection = createHolidaysSection();
        HBox.setHgrow(holidaySection, Priority.ALWAYS);

        VBox activitySection = createTeamBuildingSection();
        HBox.setHgrow(activitySection, Priority.ALWAYS);

        apiRow.getChildren().addAll(holidaySection, activitySection);

        // Recent activity sections
        VBox recentSection = new VBox(16);

        // Build recent leaves from pre-loaded data (already scoped by role)
        VBox recentLeaves = buildRecentLeavesFromData(leaves);
        // Build recent attendance from pre-loaded data (already scoped by role)
        VBox recentAttendance = buildRecentAttendanceFromData(attendance);

        recentSection.getChildren().addAll(recentLeaves, recentAttendance);

        view.getChildren().addAll(title, statsRow, apiRow, recentSection);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);
    }

    /** Build recent leaves section using pre-loaded data (no additional DB query). */
    private VBox buildRecentLeavesFromData(List<Leave> leaves) {
        VBox section = new VBox(8);
        section.getStyleClass().add("hr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("📋 Recent Leave Requests");
        title.getStyleClass().add("hr-section-title");
        section.getChildren().add(title);

        List<Leave> recent = leaves.stream().limit(5).collect(Collectors.toList());
        if (recent.isEmpty()) {
            section.getChildren().add(new Label("No leave requests yet."));
        } else {
            for (Leave l : recent) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                Label name = new Label(getUserName(l.getUserId()));
                name.getStyleClass().add("hr-list-name");
                name.setPrefWidth(160);

                Label type = new Label(l.getType());
                type.getStyleClass().add("hr-badge");
                type.setPrefWidth(80);

                Label dates = new Label(l.getStartDate() + " → " + l.getEndDate());
                dates.getStyleClass().add("hr-list-detail");
                HBox.setHgrow(dates, Priority.ALWAYS);

                Label status = new Label(l.getStatus());
                status.getStyleClass().addAll("hr-status-badge", "hr-status-" + l.getStatus().toLowerCase());

                row.getChildren().addAll(name, type, dates, status);
                section.getChildren().add(row);
            }
        }
        return section;
    }

    /** Build recent attendance section using pre-loaded data (no additional DB query). */
    private VBox buildRecentAttendanceFromData(List<Attendance> attendance) {
        VBox section = new VBox(8);
        section.getStyleClass().add("hr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("📅 Today's Attendance");
        title.getStyleClass().add("hr-section-title");
        section.getChildren().add(title);

        List<Attendance> todayList = attendance.stream()
                .filter(a -> a.getDate() != null && a.getDate().toLocalDate().equals(LocalDate.now()))
                .limit(8)
                .collect(Collectors.toList());

        if (todayList.isEmpty()) {
            section.getChildren().add(new Label("No attendance records for today."));
        } else {
            for (Attendance a : todayList) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                Label name = new Label(getUserName(a.getUserId()));
                name.getStyleClass().add("hr-list-name");
                name.setPrefWidth(160);

                Label checkIn = new Label("In: " + (a.getCheckIn() != null ? a.getCheckIn().toString() : "—"));
                checkIn.setPrefWidth(100);

                Label checkOut = new Label("Out: " + (a.getCheckOut() != null ? a.getCheckOut().toString() : "—"));
                checkOut.setPrefWidth(100);

                Label status = new Label(a.getStatus());
                status.getStyleClass().addAll("hr-status-badge", "hr-status-" + a.getStatus().toLowerCase());

                row.getChildren().addAll(name, checkIn, checkOut, status);
                section.getChildren().add(row);
            }
        }
        return section;
    }

    private VBox createStatCard(String icon, String label, String value, String styleClass) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("hr-stat-card", styleClass);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(180);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-cursor: hand;");

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 28px;");

        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("hr-stat-value");

        Label nameLbl = new Label(label);
        nameLbl.getStyleClass().add("hr-stat-label");

        card.getChildren().addAll(iconLbl, valueLbl, nameLbl);

        // Make card clickable to navigate to the corresponding tab
        card.setOnMouseClicked(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            String targetTab = mapStatToTab(label);
            if (targetTab != null) {
                for (javafx.scene.Node node : tabBar.getChildren()) {
                    if (node instanceof Button) {
                        Button btn = (Button) node;
                        if (btn.getText().contains(targetTab)) {
                            switchTab(btn, targetTab);
                            break;
                        }
                    }
                }
            }
        });

        return card;
    }

    private String mapStatToTab(String statLabel) {
        switch (statLabel) {
            case "Total Employees":  return "Departments";
            case "Departments":      return "Departments";
            case "Present Today":    return "Attendance";
            case "Pending Leaves":   return "Leaves";
            case "Pending Payroll":  return "Payroll";
            default:                 return null;
        }
    }

    private VBox createRecentLeavesSection() {
        VBox section = new VBox(8);
        section.getStyleClass().add("hr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("📋 Recent Leave Requests");
        title.getStyleClass().add("hr-section-title");
        section.getChildren().add(title);

        try {
            List<Leave> leaves = serviceLeave.recuperer();
            List<Leave> recent = leaves.stream().limit(5).collect(Collectors.toList());

            if (recent.isEmpty()) {
                section.getChildren().add(new Label("No leave requests yet."));
            } else {
                for (Leave l : recent) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("hr-list-row");
                    row.setPadding(new Insets(8, 12, 8, 12));

                    Label name = new Label(getUserName(l.getUserId()));
                    name.getStyleClass().add("hr-list-name");
                    name.setPrefWidth(160);

                    Label type = new Label(l.getType());
                    type.getStyleClass().add("hr-badge");
                    type.setPrefWidth(80);

                    Label dates = new Label(l.getStartDate() + " → " + l.getEndDate());
                    dates.getStyleClass().add("hr-list-detail");
                    HBox.setHgrow(dates, Priority.ALWAYS);

                    Label status = new Label(l.getStatus());
                    status.getStyleClass().addAll("hr-status-badge", "hr-status-" + l.getStatus().toLowerCase());

                    row.getChildren().addAll(name, type, dates, status);
                    section.getChildren().add(row);
                }
            }
        } catch (SQLException e) {
            section.getChildren().add(new Label("Error: " + e.getMessage()));
        }

        return section;
    }

    private VBox createRecentAttendanceSection() {
        VBox section = new VBox(8);
        section.getStyleClass().add("hr-section-card");
        section.setPadding(new Insets(16));

        Label title = new Label("📅 Today's Attendance");
        title.getStyleClass().add("hr-section-title");
        section.getChildren().add(title);

        try {
            List<Attendance> todayList = serviceAttendance.getByDate(Date.valueOf(LocalDate.now()));

            if (todayList.isEmpty()) {
                section.getChildren().add(new Label("No attendance records for today."));
            } else {
                for (Attendance a : todayList.stream().limit(8).collect(Collectors.toList())) {
                    HBox row = new HBox(12);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("hr-list-row");
                    row.setPadding(new Insets(8, 12, 8, 12));

                    Label name = new Label(getUserName(a.getUserId()));
                    name.getStyleClass().add("hr-list-name");
                    name.setPrefWidth(160);

                    Label checkIn = new Label("In: " + (a.getCheckIn() != null ? a.getCheckIn().toString() : "—"));
                    checkIn.setPrefWidth(100);

                    Label checkOut = new Label("Out: " + (a.getCheckOut() != null ? a.getCheckOut().toString() : "—"));
                    checkOut.setPrefWidth(100);

                    Label status = new Label(a.getStatus());
                    status.getStyleClass().addAll("hr-status-badge", "hr-status-" + a.getStatus().toLowerCase());

                    row.getChildren().addAll(name, checkIn, checkOut, status);
                    section.getChildren().add(row);
                }
            }
        } catch (SQLException e) {
            section.getChildren().add(new Label("Error: " + e.getMessage()));
        }

        return section;
    }

    // ── API: Nager.Date Public Holidays ──

    private VBox createHolidaysSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("hr-section-card");
        section.setPadding(new Insets(16));
        section.setMinWidth(320);

        Label titleLbl = new Label("\uD83C\uDF10 Upcoming Public Holidays");
        titleLbl.getStyleClass().add("hr-section-title");

        VBox listBox = new VBox(6);
        Label loading = new Label("Detecting location...");
        loading.getStyleClass().add("hr-api-loading");
        listBox.getChildren().add(loading);

        section.getChildren().addAll(titleLbl, listBox);

        // Detect country from IP, then fetch holidays for that country
        int year = LocalDate.now().getYear();
        utils.GeoLocationService.resolveCountry().thenCompose(countryCode -> {
            String countryName = utils.GeoLocationService.getCountryName();
            String flag = utils.GeoLocationService.countryCodeToFlag(countryCode);
            Platform.runLater(() -> {
                titleLbl.setText(flag + " Upcoming Public Holidays");
                loading.setText("Loading holidays for " + countryName + "...");
            });
            String url = "https://date.nager.at/api/v3/PublicHolidays/" + year + "/" + countryCode;
            return httpClient.sendAsync(
                    HttpRequest.newBuilder().uri(URI.create(url)).GET()
                            .timeout(Duration.ofSeconds(8)).build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        }).thenAccept(resp -> {
            if (resp.statusCode() == 200) {
                JsonArray holidays = gson.fromJson(resp.body(), JsonArray.class);
                Platform.runLater(() -> {
                    listBox.getChildren().clear();
                    LocalDate today = LocalDate.now();
                    int shown = 0;
                    for (JsonElement el : holidays) {
                        JsonObject h = el.getAsJsonObject();
                        LocalDate hDate = LocalDate.parse(h.get("date").getAsString());
                        if (hDate.isBefore(today)) continue;
                        if (shown >= 5) break;

                        String name = h.get("localName").getAsString();
                        String intlName = h.get("name").getAsString();
                        long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, hDate);

                        HBox row = new HBox(10);
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.getStyleClass().add("hr-holiday-row");
                        row.setPadding(new Insets(8, 12, 8, 12));

                        Label dateLabel = new Label(hDate.format(DateTimeFormatter.ofPattern("MMM dd")));
                        dateLabel.getStyleClass().add("hr-holiday-date");
                        dateLabel.setMinWidth(60);

                        VBox nameBox = new VBox(1);
                        Label nameLbl = new Label(intlName);
                        nameLbl.getStyleClass().add("hr-holiday-name");
                        Label localLbl = new Label(name);
                        localLbl.getStyleClass().add("hr-holiday-local");
                        nameBox.getChildren().addAll(nameLbl, localLbl);
                        HBox.setHgrow(nameBox, Priority.ALWAYS);

                        Label daysLbl = new Label(daysUntil == 0 ? "\uD83C\uDF89 Today!"
                                : daysUntil == 1 ? "Tomorrow" : daysUntil + " days");
                        daysLbl.getStyleClass().add(daysUntil <= 7 ? "hr-holiday-soon" : "hr-holiday-days");

                        row.getChildren().addAll(dateLabel, nameBox, daysLbl);
                        listBox.getChildren().add(row);
                        shown++;
                    }
                    if (shown == 0) {
                        listBox.getChildren().add(new Label("No upcoming holidays found for this year."));
                    }
                });
            }
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                listBox.getChildren().clear();
                listBox.getChildren().add(new Label("\u26A0 Could not load holidays"));
            });
            return null;
        });

        return section;
    }

    // ── API: Bored API — Team Building Activities ──

    private VBox createTeamBuildingSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("hr-section-card");
        section.setPadding(new Insets(16));
        section.setMinWidth(320);

        Label titleLbl = new Label("\uD83E\uDDD1\u200D\uD83E\uDD1D\u200D\uD83E\uDDD1 Team Building Suggestion");
        titleLbl.getStyleClass().add("hr-section-title");

        VBox contentBox = new VBox(8);
        contentBox.setAlignment(Pos.CENTER_LEFT);

        Label activityLabel = new Label("Loading...");
        activityLabel.getStyleClass().add("hr-activity-text");
        activityLabel.setWrapText(true);

        Label typeLabel = new Label("");
        typeLabel.getStyleClass().add("hr-activity-type");

        Label participantsLabel = new Label("");
        participantsLabel.getStyleClass().add("hr-activity-detail");

        Button refreshBtn = new Button("\uD83C\uDFB2 New Suggestion");
        refreshBtn.getStyleClass().add("hr-primary-btn");
        refreshBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            fetchTeamActivity(activityLabel, typeLabel, participantsLabel);
        });

        contentBox.getChildren().addAll(activityLabel, typeLabel, participantsLabel, refreshBtn);
        section.getChildren().addAll(titleLbl, contentBox);

        // Initial fetch
        fetchTeamActivity(activityLabel, typeLabel, participantsLabel);

        return section;
    }

    private void fetchTeamActivity(Label activityLabel, Label typeLabel, Label participantsLabel) {
        activityLabel.setText("Finding activity...");
        typeLabel.setText("");
        participantsLabel.setText("");

        httpClient.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("https://bored-api.appbrewery.com/random"))
                        .GET().timeout(Duration.ofSeconds(8)).build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenAccept(resp -> {
            if (resp.statusCode() == 200) {
                JsonObject obj = gson.fromJson(resp.body(), JsonObject.class);
                String activity = obj.has("activity") ? obj.get("activity").getAsString() : "No suggestion";
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                int participants = obj.has("participants") ? obj.get("participants").getAsInt() : 0;

                Platform.runLater(() -> {
                    activityLabel.setText("\uD83C\uDFAF " + activity);
                    typeLabel.setText("\uD83C\uDFF7 Type: " + capitalize(type));
                    participantsLabel.setText("\uD83D\uDC65 Participants: " + (participants > 0 ? participants : "Any"));
                });
            }
        }).exceptionally(ex -> {
            Platform.runLater(() -> activityLabel.setText("\u26A0 Could not fetch activity"));
            return null;
        });
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ==================== DEPARTMENTS TAB ====================

    private void showDepartments() {
        VBox view = new VBox(16);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Departments");
        title.getStyleClass().add("hr-view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addBtn = new Button("+ Add Department");
        addBtn.getStyleClass().add("hr-primary-btn");
        addBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            showDepartmentDialog(null);
        });
        header.getChildren().addAll(title, spacer, addBtn);

        VBox deptList = new VBox(10);
        deptList.setId("deptList");

        view.getChildren().addAll(header, deptList);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);

        refreshDepartmentList(deptList);
    }

    private void refreshDepartmentList(VBox container) {
        container.getChildren().clear();
        try {
            List<Department> depts = serviceDepartment.recuperer();
            if (depts.isEmpty()) {
                Label empty = new Label("No departments yet. Click '+ Add Department' to create one.");
                empty.getStyleClass().add("hr-empty-label");
                container.getChildren().add(empty);
                return;
            }

            for (Department dept : depts) {
                VBox deptSection = new VBox(0);
                deptSection.getStyleClass().add("hr-dept-section");

                // ── Department header card ──
                HBox card = new HBox(16);
                card.getStyleClass().add("hr-dept-card");
                card.setPadding(new Insets(16));
                card.setAlignment(Pos.CENTER_LEFT);

                VBox info = new VBox(4);
                HBox.setHgrow(info, Priority.ALWAYS);

                Label name = new Label(dept.getName());
                name.getStyleClass().add("hr-dept-name");

                Label desc = new Label(dept.getDescription() != null ? dept.getDescription() : "No description");
                desc.getStyleClass().add("hr-dept-desc");

                HBox meta = new HBox(16);
                Label manager = new Label("👤 " + (dept.getManagerId() != null ? getUserName(dept.getManagerId()) : "No manager"));
                manager.getStyleClass().add("hr-dept-meta");
                Label budget = new Label("💰 " + String.format("%.2f TND", dept.getAllocatedBudget()));
                budget.getStyleClass().add("hr-dept-meta");

                // Count employees in this department
                long empCount = allUsers.stream()
                        .filter(u -> u.getDepartmentId() != null && u.getDepartmentId() == dept.getId())
                        .count();
                Label empLabel = new Label("👥 " + empCount + " employees");
                empLabel.getStyleClass().add("hr-dept-meta");

                meta.getChildren().addAll(manager, budget, empLabel);
                info.getChildren().addAll(name, desc, meta);

                VBox actions = new VBox(6);
                actions.setAlignment(Pos.CENTER);

                Button editBtn = new Button("✏ Edit");
                editBtn.getStyleClass().add("hr-action-btn");
                editBtn.setOnAction(e -> {
                    SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                    showDepartmentDialog(dept);
                });

                Button deleteBtn = new Button("🗑 Delete");
                deleteBtn.getStyleClass().addAll("hr-action-btn", "hr-danger-btn");
                deleteBtn.setOnAction(e -> {
                    SoundManager.getInstance().play(SoundManager.PROJECT_DELETED);
                    confirmDelete("department", dept.getName(), () -> {
                        try {
                            serviceDepartment.supprimer(dept.getId());
                            // Unassign employees from deleted department
                            for (User u : allUsers) {
                                if (u.getDepartmentId() != null && u.getDepartmentId() == dept.getId()) {
                                    serviceUser.updateDepartmentId(u.getId(), null);
                                    u.setDepartmentId(null);
                                }
                            }
                            refreshDepartmentList(container);
                        } catch (SQLException ex) {
                            showError("Failed to delete department: " + ex.getMessage());
                        }
                    });
                });

                Button teamBtn = new Button("👥 Manage Team");
                teamBtn.getStyleClass().add("hr-action-btn");

                actions.getChildren().addAll(editBtn, teamBtn, deleteBtn);
                card.getChildren().addAll(info, actions);

                // ── Team panel (toggle) ──
                VBox teamPanel = buildTeamPanel(dept, container, empLabel);
                teamPanel.setVisible(false);
                teamPanel.setManaged(false);

                teamBtn.setOnAction(e -> {
                    SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                    boolean show = !teamPanel.isVisible();
                    teamPanel.setVisible(show);
                    teamPanel.setManaged(show);
                    teamBtn.setText(show ? "▲ Hide Team" : "👥 Manage Team");
                });

                deptSection.getChildren().addAll(card, teamPanel);
                container.getChildren().add(deptSection);
            }
        } catch (SQLException e) {
            container.getChildren().add(new Label("Error loading departments: " + e.getMessage()));
        }
    }

    /** Roles eligible for team assignment (excludes admin/hr/gig) */
    private boolean isTeamEligible(User u) {
        String role = u.getRole();
        return "EMPLOYEE".equals(role) || "PROJECT_OWNER".equals(role);
    }

    private VBox buildTeamPanel(Department dept, VBox parentContainer, Label empLabel) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("hr-team-panel");
        panel.setPadding(new Insets(12, 16, 16, 16));

        // Assigned employees
        Label assignedTitle = new Label("✅ Assigned Members");
        assignedTitle.getStyleClass().add("hr-team-section-title");

        VBox assignedList = new VBox(4);
        assignedList.setId("assigned-" + dept.getId());

        // Unassigned employees
        Label unassignedTitle = new Label("➕ Unassigned Employees");
        unassignedTitle.getStyleClass().add("hr-team-section-title");

        VBox unassignedList = new VBox(4);
        unassignedList.setId("unassigned-" + dept.getId());

        panel.getChildren().addAll(assignedTitle, assignedList, unassignedTitle, unassignedList);

        refreshTeamPanel(dept, assignedList, unassignedList, parentContainer, empLabel);
        return panel;
    }

    private void refreshTeamPanel(Department dept, VBox assignedList, VBox unassignedList, VBox parentContainer, Label empLabel) {
        assignedList.getChildren().clear();
        unassignedList.getChildren().clear();

        List<User> assigned = allUsers.stream()
                .filter(this::isTeamEligible)
                .filter(u -> u.getDepartmentId() != null && u.getDepartmentId() == dept.getId())
                .collect(Collectors.toList());

        // Only show employees with NO department as assignable (prevents cross-department moves)
        List<User> unassigned = allUsers.stream()
                .filter(this::isTeamEligible)
                .filter(u -> u.getDepartmentId() == null || u.getDepartmentId() == 0)
                .collect(Collectors.toList());

        if (assigned.isEmpty()) {
            Label empty = new Label("No members assigned yet.");
            empty.getStyleClass().add("hr-team-empty");
            assignedList.getChildren().add(empty);
        } else {
            for (User u : assigned) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-team-row");
                row.setPadding(new Insets(6, 10, 6, 10));

                Label nameLabel = new Label(u.getFirstName() + " " + u.getLastName());
                nameLabel.getStyleClass().add("hr-team-name");
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);

                Label roleLabel = new Label(u.getRole().replace("_", " "));
                roleLabel.getStyleClass().add("hr-team-role");

                Button removeBtn = new Button("✕ Remove");
                removeBtn.getStyleClass().addAll("hr-action-btn", "hr-danger-btn");
                removeBtn.setOnAction(e -> {
                    SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                    try {
                        serviceUser.updateDepartmentId(u.getId(), null);
                        u.setDepartmentId(null);
                        // Notify employee about removal
                        serviceNotification.create(u.getId(), "DEPARTMENT",
                                "\uD83C\uDFE2 Department Change",
                                "You have been removed from \"" + dept.getName() + "\" department.",
                                dept.getId(), "DEPARTMENT");
                        // Notify dept manager
                        if (dept.getManagerId() != null && dept.getManagerId() != currentUser.getId()) {
                            serviceNotification.create(dept.getManagerId(), "DEPARTMENT",
                                    "\uD83D\uDC65 Team Update",
                                    u.getFirstName() + " " + u.getLastName() + " has been removed from your department.",
                                    dept.getId(), "DEPARTMENT");
                        }
                        // Force refresh user cache & update panel in-place
                        allUsers = utils.UserNameCache.forceRefresh();
                        refreshTeamPanel(dept, assignedList, unassignedList, parentContainer, empLabel);
                        updateEmpCount(dept, empLabel);
                    } catch (SQLException ex) {
                        showError("Failed to remove: " + ex.getMessage());
                    }
                });

                row.getChildren().addAll(nameLabel, roleLabel, removeBtn);
                assignedList.getChildren().add(row);
            }
        }

        if (unassigned.isEmpty()) {
            Label empty = new Label("All eligible employees are assigned.");
            empty.getStyleClass().add("hr-team-empty");
            unassignedList.getChildren().add(empty);
        } else {
            for (User u : unassigned) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-team-row");
                row.setPadding(new Insets(6, 10, 6, 10));

                Label nameLabel = new Label(u.getFirstName() + " " + u.getLastName());
                nameLabel.getStyleClass().add("hr-team-name");
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                nameLabel.setMaxWidth(Double.MAX_VALUE);

                Label roleLabel = new Label(u.getRole().replace("_", " "));
                roleLabel.getStyleClass().add("hr-team-role");

                Button addBtn = new Button("+ Assign");
                addBtn.getStyleClass().addAll("hr-action-btn", "hr-success-btn");
                addBtn.setOnAction(e -> {
                    SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                    // Guard: prevent assigning if already in a department
                    if (u.getDepartmentId() != null && u.getDepartmentId() != 0) {
                        String currentDeptName = "another department";
                        try {
                            for (Department d : serviceDepartment.recuperer()) {
                                if (d.getId() == u.getDepartmentId()) { currentDeptName = d.getName(); break; }
                            }
                        } catch (SQLException ignored) {}
                        showError(u.getFirstName() + " " + u.getLastName() +
                                " is already assigned to \"" + currentDeptName + "\".\n" +
                                "An employee cannot belong to multiple departments.");
                        return;
                    }
                    try {
                        serviceUser.updateDepartmentId(u.getId(), dept.getId());
                        u.setDepartmentId(dept.getId());
                        // Notify employee about assignment
                        serviceNotification.create(u.getId(), "DEPARTMENT",
                                "\uD83C\uDFE2 Department Assignment",
                                "You have been assigned to \"" + dept.getName() + "\" department.",
                                dept.getId(), "DEPARTMENT");
                        // Notify dept manager
                        if (dept.getManagerId() != null && dept.getManagerId() != currentUser.getId()) {
                            serviceNotification.create(dept.getManagerId(), "DEPARTMENT",
                                    "\uD83D\uDC65 New Team Member",
                                    u.getFirstName() + " " + u.getLastName() + " has been assigned to your department.",
                                    dept.getId(), "DEPARTMENT");
                        }
                        // Force refresh user cache & update panel in-place
                        allUsers = utils.UserNameCache.forceRefresh();
                        refreshTeamPanel(dept, assignedList, unassignedList, parentContainer, empLabel);
                        updateEmpCount(dept, empLabel);
                    } catch (SQLException ex) {
                        showError("Failed to assign: " + ex.getMessage());
                    }
                });

                row.getChildren().addAll(nameLabel, roleLabel, addBtn);
                unassignedList.getChildren().add(row);
            }
        }
    }

    /** Update employee count label for a department card. */
    private void updateEmpCount(Department dept, Label empLabel) {
        long count = allUsers.stream()
                .filter(u -> u.getDepartmentId() != null && u.getDepartmentId() == dept.getId())
                .count();
        empLabel.setText("👥 " + count + " employees");
    }

    private void showDepartmentDialog(Department existing) {
        Dialog<Department> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Department" : "Edit Department");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane dp = dialog.getDialogPane();
        dp.getStyleClass().add("hr-dialog-pane");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.getStyleClass().add("hr-dialog-ok-btn");
        Button cancelBtn = (Button) dp.lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("hr-dialog-cancel-btn");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("hr-dialog-form");

        Label header = new Label(existing == null ? "\uD83C\uDFE2  New Department" : "\u270F  Edit Department");
        header.getStyleClass().add("hr-dialog-title");

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        nameField.setPromptText("Department name");
        nameField.getStyleClass().add("hr-form-control");

        TextArea descField = new TextArea(existing != null && existing.getDescription() != null ? existing.getDescription() : "");
        descField.setPromptText("Description");
        descField.setPrefRowCount(3);
        descField.getStyleClass().add("hr-form-control");

        ComboBox<String> managerCombo = new ComboBox<>();
        managerCombo.getItems().add("None");

        // Collect manager IDs already assigned to other departments
        Set<Integer> takenManagerIds = new java.util.HashSet<>();
        try {
            for (Department dept : serviceDepartment.recuperer()) {
                if (dept.getManagerId() != null) {
                    // Allow the current department's manager to still appear
                    if (existing == null || existing.getId() != dept.getId()) {
                        takenManagerIds.add(dept.getManagerId());
                    }
                }
            }
        } catch (SQLException ignored) {}

        for (User u : allUsers) {
            // Skip users already managing another department
            if (takenManagerIds.contains(u.getId())) continue;
            managerCombo.getItems().add(u.getId() + " - " + u.getFirstName() + " " + u.getLastName());
        }
        if (existing != null && existing.getManagerId() != null) {
            String sel = existing.getManagerId() + " - " + getUserName(existing.getManagerId());
            managerCombo.setValue(sel);
        } else {
            managerCombo.setValue("None");
        }
        managerCombo.getStyleClass().add("hr-form-control");

        // Budget slider with live value display
        double initBudget = existing != null ? existing.getAllocatedBudget() : 0;
        Slider budgetSlider = new Slider(0, 2000000, initBudget);
        budgetSlider.setBlockIncrement(10000);
        budgetSlider.setMajorTickUnit(500000);
        budgetSlider.setMinorTickCount(4);
        budgetSlider.setShowTickLabels(true);
        budgetSlider.setShowTickMarks(true);
        budgetSlider.setSnapToTicks(false);
        budgetSlider.getStyleClass().add("hr-budget-slider");
        budgetSlider.setLabelFormatter(new javafx.util.StringConverter<Double>() {
            @Override public String toString(Double v) {
                if (v >= 1000000) return String.format("%.1fM", v / 1000000);
                if (v >= 1000) return String.format("%.0fK", v / 1000);
                return String.format("%.0f", v);
            }
            @Override public Double fromString(String s) { return 0.0; }
        });

        Label budgetValueLabel = new Label(String.format("%.0f TND", initBudget));
        budgetValueLabel.getStyleClass().add("hr-budget-value");

        TextField budgetInput = new TextField(String.format("%.0f", initBudget));
        budgetInput.setPrefWidth(110);
        budgetInput.getStyleClass().add("hr-form-control");

        budgetSlider.valueProperty().addListener((o, oldVal, newVal) -> {
            budgetValueLabel.setText(String.format("%.0f TND", newVal.doubleValue()));
            if (!budgetInput.isFocused()) {
                budgetInput.setText(String.format("%.0f", newVal.doubleValue()));
            }
        });
        budgetInput.textProperty().addListener((o, oldVal, newVal) -> {
            try {
                double val = Double.parseDouble(newVal.trim());
                if (val >= 0 && val <= 2000000) budgetSlider.setValue(val);
            } catch (NumberFormatException ignored) {}
        });

        HBox budgetRow = new HBox(10, budgetSlider, budgetInput);
        budgetRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(budgetSlider, Priority.ALWAYS);

        Label descSub = new Label(existing == null
                ? "Create a new department with its team and budget."
                : "Update department details.");
        descSub.getStyleClass().add("hr-dialog-desc");

        content.getChildren().addAll(
                header, descSub,
                new Separator() {{ getStyleClass().add("hr-dialog-sep"); }},
                new Label("Name") {{ getStyleClass().add("hr-form-label"); }}, nameField,
                new Label("Description") {{ getStyleClass().add("hr-form-label"); }}, descField,
                new Label("Manager") {{ getStyleClass().add("hr-form-label"); }}, managerCombo,
                new Separator() {{ getStyleClass().add("hr-dialog-sep"); }},
                new Label("Budget") {{ getStyleClass().add("hr-form-label"); }}, budgetValueLabel, budgetRow
        );

        // Wrap in ScrollPane for taller dialogs
        ScrollPane scrollContent = new ScrollPane(content);
        scrollContent.setFitToWidth(true);
        scrollContent.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollContent.setMaxHeight(500);
        dp.setContent(scrollContent);
        dp.setPrefWidth(480);

        // Validation error label
        Label validationError = new Label();
        validationError.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 12; -fx-padding: 4 0 0 0;");
        validationError.setWrapText(true);
        validationError.setVisible(false);
        validationError.setManaged(false);
        content.getChildren().add(validationError);

        // Disable OK button when name is empty
        okBtn.setDisable(nameField.getText().trim().isEmpty());
        nameField.textProperty().addListener((obs, ov, nv) -> {
            okBtn.setDisable(nv == null || nv.trim().isEmpty());
            validationError.setVisible(false);
            validationError.setManaged(false);
        });

        // Intercept OK button to validate before closing
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String name = nameField.getText().trim();

            // 1. Name required
            if (name.isEmpty()) {
                validationError.setText("⚠ Department name is required.");
                validationError.setVisible(true);
                validationError.setManaged(true);
                event.consume();
                return;
            }

            // 2. Name length check
            if (name.length() < 2 || name.length() > 100) {
                validationError.setText("⚠ Department name must be between 2 and 100 characters.");
                validationError.setVisible(true);
                validationError.setManaged(true);
                event.consume();
                return;
            }

            // 3. Duplicate name check
            try {
                for (Department dept : serviceDepartment.recuperer()) {
                    if (dept.getName().equalsIgnoreCase(name) &&
                            (existing == null || existing.getId() != dept.getId())) {
                        validationError.setText("⚠ A department named \"" + dept.getName() + "\" already exists.");
                        validationError.setVisible(true);
                        validationError.setManaged(true);
                        event.consume();
                        return;
                    }
                }
            } catch (SQLException ignored) {}

        });

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                Department d = existing != null ? existing : new Department();
                d.setName(nameField.getText().trim());
                d.setDescription(descField.getText().trim());
                String mgr = managerCombo.getValue();
                if (mgr != null && !mgr.equals("None")) {
                    d.setManagerId(Integer.parseInt(mgr.split(" - ")[0]));
                } else {
                    d.setManagerId(null);
                }
                d.setAllocatedBudget(budgetSlider.getValue());
                return d;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(d -> {
            try {
                if (existing == null) {
                    serviceDepartment.ajouter(d);
                } else {
                    serviceDepartment.modifier(d);
                }
                showDepartments();
            } catch (SQLException e) {
                showError("Failed to save department: " + e.getMessage());
            }
        });
    }

    // ==================== ATTENDANCE TAB ====================

    private void showAttendance() {
        VBox view = new VBox(16);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Attendance Records");
        title.getStyleClass().add("hr-view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Date filter
        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.getStyleClass().add("hr-date-picker");

        header.getChildren().addAll(title, spacer);

        if (isHrOrAdmin) {
            Button addBtn = new Button("+ Mark Attendance");
            addBtn.getStyleClass().add("hr-primary-btn");
            addBtn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.ATTENDANCE_CHECKIN);
                showAttendanceDialog(null);
            });
            header.getChildren().add(addBtn);
        }

        header.getChildren().add(datePicker);

        VBox attList = new VBox(8);

        view.getChildren().addAll(header, attList);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);

        // Load based on role
        datePicker.setOnAction(e -> refreshAttendanceList(attList, datePicker.getValue()));
        refreshAttendanceList(attList, datePicker.getValue());
    }

    private void refreshAttendanceList(VBox container, LocalDate filterDate) {
        container.getChildren().clear();
        try {
            List<Attendance> records;
            if (isHrOrAdmin) {
                if (filterDate != null) {
                    records = serviceAttendance.getByDate(Date.valueOf(filterDate));
                } else {
                    records = serviceAttendance.recuperer();
                }
            } else {
                records = serviceAttendance.getByUser(currentUser.getId());
                if (filterDate != null) {
                    records = records.stream()
                            .filter(a -> a.getDate() != null && a.getDate().toLocalDate().equals(filterDate))
                            .collect(Collectors.toList());
                }
            }

            if (records.isEmpty()) {
                Label empty = new Label("No attendance records found for " + (filterDate != null ? filterDate : "this period") + ".");
                empty.getStyleClass().add("hr-empty-label");
                container.getChildren().add(empty);
                return;
            }

            // Header row
            HBox headerRow = new HBox(12);
            headerRow.getStyleClass().add("hr-table-header");
            headerRow.setPadding(new Insets(8, 12, 8, 12));
            headerRow.getChildren().addAll(
                    createColLabel("Employee", 160),
                    createColLabel("Date", 100),
                    createColLabel("Check In", 80),
                    createColLabel("Check Out", 80),
                    createColLabel("Hours", 60),
                    createColLabel("Status", 80),
                    createColLabel("Actions", 100)
            );
            container.getChildren().add(headerRow);

            for (Attendance a : records) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                Label name = createColLabel(getUserName(a.getUserId()), 160);
                Label date = createColLabel(a.getDate() != null ? a.getDate().toString() : "—", 100);
                Label in = createColLabel(a.getCheckIn() != null ? a.getCheckIn().toString() : "—", 80);
                Label out = createColLabel(a.getCheckOut() != null ? a.getCheckOut().toString() : "—", 80);
                Label hours = createColLabel(String.format("%.1fh", a.getHoursWorked()), 60);

                Label status = new Label(a.getStatus());
                status.getStyleClass().addAll("hr-status-badge", "hr-status-" + a.getStatus().toLowerCase());
                status.setPrefWidth(80);

                HBox actions = new HBox(4);
                actions.setPrefWidth(100);
                if (isHrOrAdmin) {
                    Button editBtn = new Button("✏");
                    editBtn.getStyleClass().add("hr-icon-btn");
                    editBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                        showAttendanceDialog(a);
                    });
                    Button deleteBtn = new Button("🗑");
                    deleteBtn.getStyleClass().addAll("hr-icon-btn", "hr-danger-btn");
                    deleteBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                        confirmDelete("attendance record", getUserName(a.getUserId()), () -> {
                            try {
                                serviceAttendance.supprimer(a.getId());
                                refreshAttendanceList(container, filterDate);
                            } catch (SQLException ex) {
                                showError("Failed to delete: " + ex.getMessage());
                            }
                        });
                    });
                    actions.getChildren().addAll(editBtn, deleteBtn);
                }

                row.getChildren().addAll(name, date, in, out, hours, status, actions);
                container.getChildren().add(row);
            }
        } catch (SQLException e) {
            container.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private void showAttendanceDialog(Attendance existing) {
        Dialog<Attendance> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Mark Attendance" : "Edit Attendance");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane dp = dialog.getDialogPane();
        dp.getStyleClass().add("hr-dialog-pane");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.getStyleClass().add("hr-dialog-ok-btn");
        Button cancelBtn = (Button) dp.lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("hr-dialog-cancel-btn");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("hr-dialog-form");

        Label header = new Label(existing == null ? "\uD83D\uDCC5  Mark Attendance" : "\u270F  Edit Attendance");
        header.getStyleClass().add("hr-dialog-title");

        ComboBox<String> userCombo = new ComboBox<>();
        for (User u : allUsers) {
            userCombo.getItems().add(u.getId() + " - " + u.getFirstName() + " " + u.getLastName());
        }
        if (existing != null) {
            userCombo.setValue(existing.getUserId() + " - " + getUserName(existing.getUserId()));
        }
        userCombo.getStyleClass().add("hr-form-control");

        DatePicker datePicker = new DatePicker(existing != null && existing.getDate() != null ? existing.getDate().toLocalDate() : LocalDate.now());
        datePicker.getStyleClass().add("hr-form-control");

        TextField checkInField = new TextField(existing != null && existing.getCheckIn() != null ? existing.getCheckIn().toString() : "09:00:00");
        checkInField.setPromptText("HH:MM:SS");
        checkInField.getStyleClass().add("hr-form-control");

        TextField checkOutField = new TextField(existing != null && existing.getCheckOut() != null ? existing.getCheckOut().toString() : "");
        checkOutField.setPromptText("HH:MM:SS");
        checkOutField.getStyleClass().add("hr-form-control");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("PRESENT", "ABSENT", "LATE", "EXCUSED");
        statusCombo.setValue(existing != null ? existing.getStatus() : "PRESENT");
        statusCombo.getStyleClass().add("hr-form-control");

        content.getChildren().addAll(
                header,
                new Label("Employee") {{ getStyleClass().add("hr-form-label"); }}, userCombo,
                new Label("Date") {{ getStyleClass().add("hr-form-label"); }}, datePicker,
                new Label("Check In") {{ getStyleClass().add("hr-form-label"); }}, checkInField,
                new Label("Check Out") {{ getStyleClass().add("hr-form-label"); }}, checkOutField,
                new Label("Status") {{ getStyleClass().add("hr-form-label"); }}, statusCombo
        );

        dp.setContent(content);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                Attendance a = existing != null ? existing : new Attendance();
                String sel = userCombo.getValue();
                if (sel != null) a.setUserId(Integer.parseInt(sel.split(" - ")[0]));
                a.setDate(Date.valueOf(datePicker.getValue()));
                String ciText = checkInField.getText().trim();
                if (!ciText.isEmpty()) {
                    a.setCheckIn(Time.valueOf(ciText.length() == 5 ? ciText + ":00" : ciText));
                }
                String coText = checkOutField.getText().trim();
                if (!coText.isEmpty()) {
                    a.setCheckOut(Time.valueOf(coText.length() == 5 ? coText + ":00" : coText));
                }
                a.setStatus(statusCombo.getValue());
                return a;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(a -> {
            try {
                if (existing == null) {
                    serviceAttendance.ajouter(a);
                } else {
                    serviceAttendance.modifier(a);
                }
                showAttendance();
            } catch (SQLException e) {
                showError("Failed to save attendance: " + e.getMessage());
            }
        });
    }

    // ==================== LEAVES TAB ====================

    private void showLeaves() {
        leaveSortColumn = "";
        leaveSortAsc = true;
        leaveSearchQuery = "";

        VBox view = new VBox(16);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Leave Requests");
        title.getStyleClass().add("hr-view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Search bar
        TextField searchField = new TextField();
        searchField.setPromptText("\uD83D\uDD0D Search leaves...");
        searchField.getStyleClass().add("hr-search-field");
        searchField.setPrefWidth(200);

        // Status filter
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "PENDING", "APPROVED", "REJECTED");
        statusFilter.setValue("All");
        statusFilter.getStyleClass().add("hr-combo");

        Button requestBtn = new Button("+ Request Leave");
        requestBtn.getStyleClass().add("hr-primary-btn");
        requestBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.LEAVE_REQUESTED);
            showLeaveDialog(null);
        });

        header.getChildren().addAll(title, spacer, searchField, statusFilter, requestBtn);

        VBox leaveList = new VBox(0);

        view.getChildren().addAll(header, leaveList);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            leaveSearchQuery = newVal != null ? newVal.trim().toLowerCase() : "";
            refreshLeaveList(leaveList, statusFilter.getValue());
        });
        statusFilter.setOnAction(e -> refreshLeaveList(leaveList, statusFilter.getValue()));
        refreshLeaveList(leaveList, "All");
    }

    private void refreshLeaveList(VBox container, String statusFilter) {
        container.getChildren().clear();
        try {
            List<Leave> leaves;
            if (isHrOrAdmin) {
                if ("All".equals(statusFilter)) {
                    leaves = serviceLeave.recuperer();
                } else {
                    leaves = serviceLeave.getByStatus(statusFilter);
                }
            } else {
                leaves = serviceLeave.getByUser(currentUser.getId());
                if (!"All".equals(statusFilter)) {
                    String sf = statusFilter;
                    leaves = leaves.stream().filter(l -> sf.equals(l.getStatus())).collect(Collectors.toList());
                }
            }

            // Search filter
            if (!leaveSearchQuery.isEmpty()) {
                String q = leaveSearchQuery;
                leaves = leaves.stream().filter(l -> {
                    String empName = getUserName(l.getUserId()).toLowerCase();
                    String tp = l.getType() != null ? l.getType().toLowerCase() : "";
                    String rs = l.getReason() != null ? l.getReason().toLowerCase() : "";
                    String st = l.getStatus() != null ? l.getStatus().toLowerCase() : "";
                    String fr = l.getStartDate() != null ? l.getStartDate().toString() : "";
                    String t = l.getEndDate() != null ? l.getEndDate().toString() : "";
                    return empName.contains(q) || tp.contains(q) || rs.contains(q) || st.contains(q) || fr.contains(q) || t.contains(q);
                }).collect(Collectors.toList());
            }

            // Sort
            if (!leaveSortColumn.isEmpty()) {
                Comparator<Leave> cmp = null;
                switch (leaveSortColumn) {
                    case "Employee": cmp = Comparator.comparing(l -> getUserName(l.getUserId()).toLowerCase()); break;
                    case "Type":     cmp = Comparator.comparing(l -> l.getType() != null ? l.getType() : ""); break;
                    case "From":     cmp = Comparator.comparing(l -> l.getStartDate() != null ? l.getStartDate().toString() : ""); break;
                    case "To":       cmp = Comparator.comparing(l -> l.getEndDate() != null ? l.getEndDate().toString() : ""); break;
                    case "Days":     cmp = Comparator.comparingLong(Leave::getDays); break;
                    case "Reason":   cmp = Comparator.comparing(l -> l.getReason() != null ? l.getReason().toLowerCase() : ""); break;
                    case "Status":   cmp = Comparator.comparing(l -> l.getStatus() != null ? l.getStatus() : ""); break;
                }
                if (cmp != null) {
                    if (!leaveSortAsc) cmp = cmp.reversed();
                    leaves.sort(cmp);
                }
            }

            if (leaves.isEmpty()) {
                Label empty = new Label(leaveSearchQuery.isEmpty() ? "No leave requests found." : "No matching leave requests.");
                empty.getStyleClass().add("hr-empty-label");
                container.getChildren().add(empty);
                return;
            }

            // Sortable header
            String[] cols = {"Employee", "Type", "From", "To", "Days", "Reason", "Status", "Actions"};
            double[] widths = {140, 80, 90, 90, 45, 140, 80, 120};
            HBox headerRow = new HBox(12);
            headerRow.getStyleClass().add("hr-table-header");
            headerRow.setPadding(new Insets(8, 12, 8, 12));
            for (int i = 0; i < cols.length; i++) {
                String col = cols[i];
                double w = widths[i];
                if ("Actions".equals(col)) {
                    headerRow.getChildren().add(createColLabel(col, w));
                } else {
                    String arrow = col.equals(leaveSortColumn) ? (leaveSortAsc ? " \u25B2" : " \u25BC") : "";
                    Label hdr = createColLabel(col + arrow, w);
                    hdr.getStyleClass().add("hr-sort-header");
                    if (col.equals(leaveSortColumn)) hdr.getStyleClass().add("hr-sort-active");
                    hdr.setOnMouseClicked(ev -> {
                        if (col.equals(leaveSortColumn)) {
                            leaveSortAsc = !leaveSortAsc;
                        } else {
                            leaveSortColumn = col;
                            leaveSortAsc = true;
                        }
                        refreshLeaveList(container, statusFilter);
                    });
                    headerRow.getChildren().add(hdr);
                }
            }
            container.getChildren().add(headerRow);

            for (Leave l : leaves) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                Label name = createColLabel(getUserName(l.getUserId()), 140);
                Label type = createColLabel(l.getType(), 80);
                Label from = createColLabel(l.getStartDate() != null ? l.getStartDate().toString() : "\u2014", 90);
                Label to = createColLabel(l.getEndDate() != null ? l.getEndDate().toString() : "\u2014", 90);
                Label days = createColLabel(String.valueOf(l.getDays()), 45);
                Label reason = createColLabel(l.getReason() != null ? l.getReason() : "\u2014", 140);

                Label status = new Label(l.getStatus());
                status.getStyleClass().addAll("hr-status-badge", "hr-status-" + l.getStatus().toLowerCase());
                status.setPrefWidth(80);

                HBox actions = new HBox(4);
                actions.setPrefWidth(120);
                actions.setAlignment(Pos.CENTER_LEFT);

                if (isHrOrAdmin && "PENDING".equals(l.getStatus())) {
                    Button approveBtn = new Button("\u2713");
                    approveBtn.getStyleClass().addAll("hr-icon-btn", "hr-success-btn");
                    approveBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.LEAVE_APPROVED);
                        l.setStatus("APPROVED");
                        try {
                            serviceLeave.modifier(l);
                            // Notify employee
                            serviceNotification.create(l.getUserId(), "LEAVE",
                                    "\u2705 Leave Approved",
                                    "Your " + l.getType() + " leave (" + l.getStartDate() + " to " + l.getEndDate() + ") has been approved.",
                                    l.getId(), "LEAVE");
                            showLeaves();
                        } catch (SQLException ex) {
                            showError("Failed to approve: " + ex.getMessage());
                        }
                    });

                    Button rejectBtn = new Button("\u2717");
                    rejectBtn.getStyleClass().addAll("hr-icon-btn", "hr-danger-btn");
                    rejectBtn.setOnAction(e -> {
                        // Require rejection reason
                        TextInputDialog reasonDlg = new TextInputDialog();
                        reasonDlg.setTitle("Reject Leave");
                        reasonDlg.setHeaderText("Reject " + getUserName(l.getUserId()) + "'s " + l.getType() + " leave?");
                        reasonDlg.setContentText("Reason (required):");
                        DialogHelper.theme(reasonDlg);
                        reasonDlg.getEditor().setPromptText("e.g. insufficient staffing, no balance...");
                        Button okB = (Button) reasonDlg.getDialogPane().lookupButton(ButtonType.OK);
                        okB.setDisable(true);
                        reasonDlg.getEditor().textProperty().addListener((o2, ov, nv) -> okB.setDisable(nv.trim().isEmpty()));
                        reasonDlg.showAndWait().ifPresent(rejReason -> {
                            if (rejReason.trim().isEmpty()) return;
                            SoundManager.getInstance().play(SoundManager.LEAVE_REJECTED);
                            l.setStatus("REJECTED");
                            l.setRejectionReason(rejReason.trim());
                            try {
                                serviceLeave.modifier(l);
                                // Notify employee with reason
                                serviceNotification.create(l.getUserId(), "LEAVE",
                                        "\u274C Leave Rejected",
                                        "Your " + l.getType() + " leave (" + l.getStartDate() + " to " + l.getEndDate() + ") was rejected.\nReason: " + rejReason.trim(),
                                        l.getId(), "LEAVE");
                                showLeaves();
                            } catch (SQLException ex) {
                                showError("Failed to reject: " + ex.getMessage());
                            }
                        });
                    });
                    actions.getChildren().addAll(approveBtn, rejectBtn);
                }

                // Show rejection reason for rejected leaves
                if ("REJECTED".equals(l.getStatus()) && l.getRejectionReason() != null && !l.getRejectionReason().isEmpty()) {
                    Tooltip rejTip = new Tooltip("Rejection reason: " + l.getRejectionReason());
                    rejTip.setWrapText(true);
                    rejTip.setMaxWidth(300);
                    status.setTooltip(rejTip);
                    status.setText("REJECTED \u24D8");
                }

                Button deleteBtn = new Button("\uD83D\uDDD1");
                deleteBtn.getStyleClass().addAll("hr-icon-btn", "hr-danger-btn");
                // Only allow deleting PENDING leaves (employees can't delete approved/rejected)
                if (!isHrOrAdmin && !"PENDING".equals(l.getStatus())) {
                    deleteBtn.setDisable(true);
                    deleteBtn.setOpacity(0.3);
                }
                deleteBtn.setOnAction(e -> {
                    SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                    confirmDelete("leave request", "", () -> {
                        try {
                            serviceLeave.supprimer(l.getId());
                            showLeaves();
                        } catch (SQLException ex) {
                            showError("Failed to delete: " + ex.getMessage());
                        }
                    });
                });
                actions.getChildren().add(deleteBtn);

                row.getChildren().addAll(name, type, from, to, days, reason, status, actions);
                container.getChildren().add(row);
            }
        } catch (SQLException e) {
            container.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private void showLeaveDialog(Leave existing) {
        Dialog<Leave> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Request Leave" : "Edit Leave");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane dp = dialog.getDialogPane();
        dp.getStyleClass().add("hr-dialog-pane");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) dp.lookupButton(ButtonType.OK);
        okBtn.getStyleClass().add("hr-dialog-ok-btn");
        Button cancelBtn = (Button) dp.lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("hr-dialog-cancel-btn");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("hr-dialog-form");

        Label header = new Label(existing == null ? "\uD83C\uDFD6  Request Leave" : "\u270F  Edit Leave");
        header.getStyleClass().add("hr-dialog-title");

        // For HR: can pick any user. For employees: auto-set to self
        ComboBox<String> userCombo = new ComboBox<>();
        if (isHrOrAdmin) {
            for (User u : allUsers) {
                userCombo.getItems().add(u.getId() + " - " + u.getFirstName() + " " + u.getLastName());
            }
            if (existing != null) {
                userCombo.setValue(existing.getUserId() + " - " + getUserName(existing.getUserId()));
            }
        } else {
            userCombo.getItems().add(currentUser.getId() + " - " + currentUser.getFirstName() + " " + currentUser.getLastName());
            userCombo.setValue(userCombo.getItems().get(0));
            userCombo.setDisable(true);
        }
        userCombo.getStyleClass().add("hr-form-control");

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("SICK", "VACATION", "UNPAID");
        typeCombo.setValue(existing != null ? existing.getType() : "VACATION");
        typeCombo.getStyleClass().add("hr-form-control");

        DatePicker startPicker = new DatePicker(existing != null && existing.getStartDate() != null ? existing.getStartDate().toLocalDate() : LocalDate.now());
        startPicker.getStyleClass().add("hr-form-control");
        DatePicker endPicker = new DatePicker(existing != null && existing.getEndDate() != null ? existing.getEndDate().toLocalDate() : LocalDate.now().plusDays(1));
        endPicker.getStyleClass().add("hr-form-control");

        TextField reasonField = new TextField(existing != null && existing.getReason() != null ? existing.getReason() : "");
        reasonField.setPromptText("Reason for leave");
        reasonField.getStyleClass().add("hr-form-control");

        // Leave balance info label — shows remaining days by type
        Label balanceLabel = new Label();
        balanceLabel.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 11; -fx-padding: 4 0 0 0;");
        balanceLabel.setWrapText(true);

        // Warning label for validation messages
        Label warningLabel = new Label();
        warningLabel.setStyle("-fx-text-fill: #FF6B35; -fx-font-size: 11;");
        warningLabel.setWrapText(true);
        warningLabel.setVisible(false);
        warningLabel.setManaged(false);

        // Compute / update leave balance display
        Runnable updateBalance = () -> {
            String sel = userCombo.getValue();
            String leaveType = typeCombo.getValue();
            if (sel == null || leaveType == null) return;
            int uid = Integer.parseInt(sel.split(" - ")[0]);
            try {
                List<Leave> userLeaves = serviceLeave.getByUser(uid);
                int currentYear = LocalDate.now().getYear();
                // Count approved + pending days for each type this year
                long vacDays = 0, sickDays = 0, unpDays = 0;
                for (Leave lv : userLeaves) {
                    if (lv.getStartDate() == null || "REJECTED".equals(lv.getStatus())) continue;
                    if (lv.getStartDate().toLocalDate().getYear() != currentYear) continue;
                    long d = lv.getDays();
                    switch (lv.getType() != null ? lv.getType() : "") {
                        case "VACATION": vacDays += d; break;
                        case "SICK": sickDays += d; break;
                        case "UNPAID": unpDays += d; break;
                    }
                }
                long remVac = Leave.MAX_VACATION_DAYS - vacDays;
                long remSick = Leave.MAX_SICK_DAYS - sickDays;
                long remUnp = Leave.MAX_UNPAID_DAYS - unpDays;
                balanceLabel.setText(String.format("Balance (%d): Vacation %d/%d \u2502 Sick %d/%d \u2502 Unpaid %d/%d",
                        currentYear, remVac, Leave.MAX_VACATION_DAYS, remSick, Leave.MAX_SICK_DAYS, remUnp, Leave.MAX_UNPAID_DAYS));
            } catch (SQLException ignored) {
                balanceLabel.setText("Could not load balance.");
            }
        };
        updateBalance.run();
        userCombo.setOnAction(e -> updateBalance.run());
        typeCombo.setOnAction(e -> updateBalance.run());

        content.getChildren().addAll(
                header,
                new Label("Employee") {{ getStyleClass().add("hr-form-label"); }}, userCombo,
                new Label("Type") {{ getStyleClass().add("hr-form-label"); }}, typeCombo,
                balanceLabel,
                new Label("Start Date") {{ getStyleClass().add("hr-form-label"); }}, startPicker,
                new Label("End Date") {{ getStyleClass().add("hr-form-label"); }}, endPicker,
                new Label("Reason") {{ getStyleClass().add("hr-form-label"); }}, reasonField,
                warningLabel
        );

        dp.setContent(content);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                Leave l = existing != null ? existing : new Leave();
                String sel = userCombo.getValue();
                if (sel != null) l.setUserId(Integer.parseInt(sel.split(" - ")[0]));
                l.setType(typeCombo.getValue());
                l.setStartDate(Date.valueOf(startPicker.getValue()));
                l.setEndDate(Date.valueOf(endPicker.getValue()));
                l.setReason(reasonField.getText().trim());
                if (existing == null) l.setStatus("PENDING");
                return l;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(l -> {
            // Validation: end date >= start date
            if (l.getEndDate().before(l.getStartDate())) {
                showError("End date must be on or after start date.");
                return;
            }

            // Validation: no overlapping leaves
            try {
                List<Leave> userLeaves = serviceLeave.getByUser(l.getUserId());
                for (Leave ul : userLeaves) {
                    if ("REJECTED".equals(ul.getStatus())) continue;
                    if (existing != null && ul.getId() == existing.getId()) continue;
                    if (ul.getStartDate() != null && ul.getEndDate() != null &&
                            !l.getEndDate().before(ul.getStartDate()) && !l.getStartDate().after(ul.getEndDate())) {
                        showError("Overlapping leave detected!\nExisting: " + ul.getType() + " " + ul.getStartDate() + " to " + ul.getEndDate() + " (" + ul.getStatus() + ")");
                        return;
                    }
                }
            } catch (SQLException ignored) {}

            // Validation: check leave balance
            try {
                List<Leave> userLeaves = serviceLeave.getByUser(l.getUserId());
                int currentYear = l.getStartDate().toLocalDate().getYear();
                long usedDays = userLeaves.stream()
                        .filter(ul -> !"REJECTED".equals(ul.getStatus()))
                        .filter(ul -> ul.getStartDate() != null && ul.getStartDate().toLocalDate().getYear() == currentYear)
                        .filter(ul -> ul.getType() != null && ul.getType().equals(l.getType()))
                        .filter(ul -> existing == null || ul.getId() != existing.getId())
                        .mapToLong(Leave::getDays).sum();
                int maxDays = "SICK".equals(l.getType()) ? Leave.MAX_SICK_DAYS :
                              "UNPAID".equals(l.getType()) ? Leave.MAX_UNPAID_DAYS : Leave.MAX_VACATION_DAYS;
                long remaining = maxDays - usedDays;
                if (l.getDays() > remaining) {
                    showError("Insufficient " + l.getType() + " balance!\nRequested: " + l.getDays() + " days, Remaining: " + remaining + " days.");
                    return;
                }
            } catch (SQLException ignored) {}

            try {
                if (existing == null) {
                    serviceLeave.ajouter(l);
                } else {
                    serviceLeave.modifier(l);
                }
                showLeaves();
            } catch (SQLException e) {
                showError("Failed to save leave: " + e.getMessage());
            }
        });
    }

    // ==================== PAYROLL TAB ====================

    private void showPayroll() {
        VBox view = new VBox(16);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Payroll Management");
        title.getStyleClass().add("hr-view-title");

        // Auto-payroll status badge
        java.time.LocalDate nextFirst = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1);
        Label autoLabel = new Label("🔄 Auto-payroll active — next run: 1st " +
                nextFirst.getMonth().toString().substring(0, 1) +
                nextFirst.getMonth().toString().substring(1).toLowerCase() +
                " " + nextFirst.getYear());
        autoLabel.getStyleClass().add("hr-badge");
        autoLabel.setStyle(autoLabel.getStyle() + "-fx-background-color: rgba(34,197,94,0.15); " +
                "-fx-text-fill: #22c55e; -fx-font-size: 11; -fx-padding: 4 10; " +
                "-fx-background-radius: 12;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button generateBtn = new Button("🔄 Generate Now");
        generateBtn.getStyleClass().add("hr-primary-btn");
        generateBtn.setTooltip(new Tooltip("Generate payroll for last month immediately"));
        generateBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.PAYROLL_GENERATED);
            java.time.LocalDate prevMonth = java.time.LocalDate.now().minusMonths(1).withDayOfMonth(1);
            utils.PayrollScheduler.getInstance().setOnPayrollGenerated(() -> {
                showInfo("Payroll auto-generated for " +
                        prevMonth.getMonth().toString().substring(0, 1) +
                        prevMonth.getMonth().toString().substring(1).toLowerCase() +
                        " " + prevMonth.getYear());
                showPayroll();
            });
            utils.PayrollScheduler.getInstance().generateForMonth(prevMonth);
        });

        Button exportAllBtn = new Button("📄 Export All PDF");
        exportAllBtn.getStyleClass().add("hr-primary-btn");
        exportAllBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            exportAllPayrollPdf();
        });

        Button addBtn = new Button("+ Add Entry");
        addBtn.getStyleClass().add("hr-primary-btn");
        addBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            showPayrollDialog(null);
        });

        header.getChildren().addAll(title, autoLabel, spacer, generateBtn, exportAllBtn, addBtn);

        // ── Filter & Column Toggle Row ──
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getStyleClass().add("hr-filter-row");
        filterRow.setPadding(new Insets(8, 0, 4, 0));

        TextField nameFilter = new TextField();
        nameFilter.setPromptText("🔍 Search employee...");
        nameFilter.getStyleClass().add("hr-filter-search");
        nameFilter.setPrefWidth(200);

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All", "PENDING", "PAID");
        statusFilter.setValue("All");
        statusFilter.getStyleClass().add("hr-filter-combo");
        statusFilter.setPromptText("Status");

        Region filterSpacer = new Region();
        HBox.setHgrow(filterSpacer, Priority.ALWAYS);

        // Column toggle
        String[] colNames = {"Employee", "Month", "Base", "Bonus", "Deductions", "Net", "Hours", "Status", "Actions"};
        boolean[] colVis = {true, true, true, true, true, true, true, true, true};
        MenuButton colToggle = new MenuButton("⚙ Columns");
        colToggle.getStyleClass().add("hr-column-toggle");
        VBox payrollList = new VBox(8);

        for (int i = 0; i < colNames.length; i++) {
            CheckMenuItem item = new CheckMenuItem(colNames[i]);
            item.setSelected(true);
            final int idx = i;
            item.selectedProperty().addListener((obs, ov, nv) -> {
                colVis[idx] = nv;
                refreshPayrollList(payrollList, nameFilter.getText(), statusFilter.getValue(), colVis);
            });
            colToggle.getItems().add(item);
        }

        filterRow.getChildren().addAll(nameFilter, statusFilter, filterSpacer, colToggle);

        // Listeners for filters
        nameFilter.textProperty().addListener((obs, ov, nv) ->
                refreshPayrollList(payrollList, nv, statusFilter.getValue(), colVis));
        statusFilter.valueProperty().addListener((obs, ov, nv) ->
                refreshPayrollList(payrollList, nameFilter.getText(), nv, colVis));

        view.getChildren().addAll(header, filterRow, payrollList);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);

        refreshPayrollList(payrollList, "", "All", colVis);
    }

    private void refreshPayrollList(VBox container, String nameFilter, String statusFilter, boolean[] colVis) {
        container.getChildren().clear();
        try {
            List<Payroll> payrolls = servicePayroll.recuperer();

            // Apply filters
            if (nameFilter != null && !nameFilter.isEmpty()) {
                String q = nameFilter.toLowerCase();
                payrolls = payrolls.stream()
                        .filter(p -> getUserName(p.getUserId()).toLowerCase().contains(q))
                        .collect(Collectors.toList());
            }
            if (statusFilter != null && !"All".equals(statusFilter)) {
                payrolls = payrolls.stream()
                        .filter(p -> statusFilter.equals(p.getStatus()))
                        .collect(Collectors.toList());
            }

            if (payrolls.isEmpty()) {
                Label empty = new Label("No payroll records found.");
                empty.getStyleClass().add("hr-empty-label");
                container.getChildren().add(empty);
                return;
            }

            // Column widths (parallel to colVis array)
            double[] widths = {130, 80, 70, 60, 75, 70, 50, 70, 130};

            // Header
            HBox headerRow = new HBox(10);
            headerRow.getStyleClass().add("hr-table-header");
            headerRow.setPadding(new Insets(8, 12, 8, 12));
            String[] colNames = {"Employee", "Month", "Base", "Bonus", "Deductions", "Net", "Hours", "Status", "Actions"};
            for (int i = 0; i < colNames.length; i++) {
                if (colVis[i]) headerRow.getChildren().add(createColLabel(colNames[i], widths[i]));
            }
            container.getChildren().add(headerRow);

            for (Payroll p : payrolls) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                // Build all cells, conditionally add
                if (colVis[0]) row.getChildren().add(createColLabel(getUserName(p.getUserId()), 130));
                if (colVis[1]) row.getChildren().add(createColLabel(p.getMonth() != null ? p.getMonth().toString() : "—", 80));
                if (colVis[2]) row.getChildren().add(createColLabel(String.format("%.0f", p.getBaseSalary()), 70));
                if (colVis[3]) row.getChildren().add(createColLabel(String.format("%.0f", p.getBonus()), 60));
                if (colVis[4]) row.getChildren().add(createColLabel(String.format("%.0f", p.getDeductions()), 75));
                if (colVis[5]) {
                    Label net = createColLabel(String.format("%.0f", p.getNetSalary()), 70);
                    net.getStyleClass().add("hr-highlight");
                    row.getChildren().add(net);
                }
                if (colVis[6]) row.getChildren().add(createColLabel(String.format("%.1f", p.getTotalHoursWorked()), 50));
                if (colVis[7]) {
                    Label status = new Label(p.getStatus());
                    status.getStyleClass().addAll("hr-status-badge", "hr-status-" + p.getStatus().toLowerCase());
                    status.setPrefWidth(70);
                    row.getChildren().add(status);
                }
                if (colVis[8]) {
                    HBox actions = new HBox(4);
                    actions.setPrefWidth(130);

                    Button pdfBtn = new Button("📄");
                    pdfBtn.getStyleClass().addAll("hr-icon-btn");
                    pdfBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                        exportSinglePayslipPdf(p);
                    });

                    Button editBtn = new Button("✏");
                    editBtn.getStyleClass().add("hr-icon-btn");
                    editBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
                        showPayrollDialog(p);
                    });

                    Button deleteBtn = new Button("🗑");
                    deleteBtn.getStyleClass().addAll("hr-icon-btn", "hr-danger-btn");
                    deleteBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                        confirmDelete("payroll record", getUserName(p.getUserId()), () -> {
                            try {
                                servicePayroll.supprimer(p.getId());
                                refreshPayrollList(container, nameFilter, statusFilter, colVis);
                            } catch (SQLException ex) {
                                showError("Failed to delete: " + ex.getMessage());
                            }
                        });
                    });

                    actions.getChildren().addAll(pdfBtn, editBtn, deleteBtn);
                    row.getChildren().add(actions);
                }

                container.getChildren().add(row);
            }
        } catch (SQLException e) {
            container.getChildren().add(new Label("Error: " + e.getMessage()));
        }
    }

    private void showPayrollDialog(Payroll existing) {
        Dialog<Payroll> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Payroll Entry" : "Edit Payroll");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane dp = dialog.getDialogPane();
        dp.getStyleClass().add("hr-dialog-pane");
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtnP = (Button) dp.lookupButton(ButtonType.OK);
        okBtnP.getStyleClass().add("hr-dialog-ok-btn");
        Button cancelBtnP = (Button) dp.lookupButton(ButtonType.CANCEL);
        cancelBtnP.getStyleClass().add("hr-dialog-cancel-btn");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("hr-dialog-form");

        Label header = new Label(existing == null ? "\uD83D\uDCB0  New Payroll Entry" : "\u270F  Edit Payroll");
        header.getStyleClass().add("hr-dialog-title");

        ComboBox<String> userCombo = new ComboBox<>();
        for (User u : allUsers) {
            userCombo.getItems().add(u.getId() + " - " + u.getFirstName() + " " + u.getLastName());
        }
        if (existing != null) {
            userCombo.setValue(existing.getUserId() + " - " + getUserName(existing.getUserId()));
        }
        userCombo.getStyleClass().add("hr-form-control");

        DatePicker monthPicker = new DatePicker(existing != null && existing.getMonth() != null ? existing.getMonth().toLocalDate() : LocalDate.now().withDayOfMonth(1));
        monthPicker.getStyleClass().add("hr-form-control");

        TextField baseSalary = new TextField(existing != null ? String.valueOf(existing.getBaseSalary()) : "0");
        baseSalary.getStyleClass().add("hr-form-control");
        TextField bonus = new TextField(existing != null ? String.valueOf(existing.getBonus()) : "0");
        bonus.getStyleClass().add("hr-form-control");
        TextField deductions = new TextField(existing != null ? String.valueOf(existing.getDeductions()) : "0");
        deductions.getStyleClass().add("hr-form-control");
        TextField hoursField = new TextField(existing != null ? String.valueOf(existing.getTotalHoursWorked()) : "0");
        hoursField.getStyleClass().add("hr-form-control");
        TextField rateField = new TextField(existing != null ? String.valueOf(existing.getHourlyRate()) : "0");
        rateField.getStyleClass().add("hr-form-control");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("PENDING", "PAID");
        statusCombo.setValue(existing != null ? existing.getStatus() : "PENDING");
        statusCombo.getStyleClass().add("hr-form-control");

        // Salary row
        HBox salaryRow = new HBox(10);
        VBox baseCol = new VBox(4);
        baseCol.getChildren().addAll(new Label("Base Salary") {{ getStyleClass().add("hr-form-label"); }}, baseSalary);
        VBox bonusCol = new VBox(4);
        bonusCol.getChildren().addAll(new Label("Bonus") {{ getStyleClass().add("hr-form-label"); }}, bonus);
        VBox dedCol = new VBox(4);
        dedCol.getChildren().addAll(new Label("Deductions") {{ getStyleClass().add("hr-form-label"); }}, deductions);
        HBox.setHgrow(baseCol, Priority.ALWAYS);
        HBox.setHgrow(bonusCol, Priority.ALWAYS);
        HBox.setHgrow(dedCol, Priority.ALWAYS);
        salaryRow.getChildren().addAll(baseCol, bonusCol, dedCol);

        // Hours row
        HBox hoursRow = new HBox(10);
        VBox hrsCol = new VBox(4);
        hrsCol.getChildren().addAll(new Label("Hours Worked") {{ getStyleClass().add("hr-form-label"); }}, hoursField);
        VBox rateCol = new VBox(4);
        rateCol.getChildren().addAll(new Label("Hourly Rate") {{ getStyleClass().add("hr-form-label"); }}, rateField);
        HBox.setHgrow(hrsCol, Priority.ALWAYS);
        HBox.setHgrow(rateCol, Priority.ALWAYS);
        hoursRow.getChildren().addAll(hrsCol, rateCol);

        content.getChildren().addAll(
                header,
                new Label("Employee") {{ getStyleClass().add("hr-form-label"); }}, userCombo,
                new Label("Month") {{ getStyleClass().add("hr-form-label"); }}, monthPicker,
                salaryRow,
                hoursRow,
                new Label("Status") {{ getStyleClass().add("hr-form-label"); }}, statusCombo
        );

        dp.setContent(content);

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                Payroll p = existing != null ? existing : new Payroll();
                String sel = userCombo.getValue();
                if (sel != null) p.setUserId(Integer.parseInt(sel.split(" - ")[0]));
                p.setMonth(Date.valueOf(monthPicker.getValue()));
                p.setYear(monthPicker.getValue().getYear());
                try { p.setBaseSalary(Double.parseDouble(baseSalary.getText().trim())); } catch (NumberFormatException ex) { p.setBaseSalary(0); }
                try { p.setBonus(Double.parseDouble(bonus.getText().trim())); } catch (NumberFormatException ex) { p.setBonus(0); }
                try { p.setDeductions(Double.parseDouble(deductions.getText().trim())); } catch (NumberFormatException ex) { p.setDeductions(0); }
                try { p.setTotalHoursWorked(Double.parseDouble(hoursField.getText().trim())); } catch (NumberFormatException ex) { p.setTotalHoursWorked(0); }
                try { p.setHourlyRate(Double.parseDouble(rateField.getText().trim())); } catch (NumberFormatException ex) { p.setHourlyRate(0); }
                p.setNetSalary(p.getBaseSalary() + p.getBonus() - p.getDeductions());
                p.setAmount(p.getNetSalary());
                p.setStatus(statusCombo.getValue());
                return p;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(p -> {
            // Payroll validation
            if (p.getUserId() <= 0) {
                showError("Please select an employee.");
                return;
            }
            if (p.getBaseSalary() < 0) {
                showError("Base salary cannot be negative.");
                return;
            }
            if (p.getBonus() < 0) {
                showError("Bonus cannot be negative.");
                return;
            }
            if (p.getDeductions() < 0) {
                showError("Deductions cannot be negative.");
                return;
            }
            if (p.getTotalHoursWorked() < 0) {
                showError("Hours worked cannot be negative.");
                return;
            }
            if (p.getHourlyRate() < 0) {
                showError("Hourly rate cannot be negative.");
                return;
            }
            if (p.getBaseSalary() == 0 && p.getTotalHoursWorked() == 0) {
                showError("Base salary or hours worked must be greater than zero.");
                return;
            }
            // Minimum wage check (~$8.5/hr Tunisia SMIG approximation)
            double effectiveHourly = p.getTotalHoursWorked() > 0 ? p.getNetSalary() / p.getTotalHoursWorked() : 0;
            if (p.getTotalHoursWorked() > 0 && effectiveHourly < 2.0) {
                showError("Net pay (" + String.format("%.2f", p.getNetSalary()) + ") / hours (" + String.format("%.1f", p.getTotalHoursWorked()) + ") = "
                        + String.format("%.2f", effectiveHourly) + "/hr\nThis is below the minimum threshold (2.00/hr). Please review.");
                return;
            }

            try {
                if (existing == null) {
                    servicePayroll.ajouter(p);
                } else {
                    servicePayroll.modifier(p);
                }
                showPayroll();
            } catch (SQLException e) {
                showError("Failed to save payroll: " + e.getMessage());
            }
        });
    }

    private void showGeneratePayrollDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Generate Payroll");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane dp = dialog.getDialogPane();
        dp.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dp.getStyleClass().add("hr-dialog-pane");

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));
        content.getStyleClass().add("hr-dialog-form");

        Label titleLabel = new Label("⚡ Generate Monthly Payroll");
        titleLabel.getStyleClass().add("hr-dialog-title");

        Label descLabel = new Label("Calculates pay for every employee based on their tracked hours and rate.\n" +
                "Employees with a monthly salary get that as base. Hourly employees get: hours × rate.\n" +
                "Set a default hourly rate for employees without one configured.");
        descLabel.getStyleClass().add("hr-dialog-desc");
        descLabel.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.getStyleClass().add("hr-dialog-form");

        DatePicker monthPicker = new DatePicker(LocalDate.now().withDayOfMonth(1));
        monthPicker.getStyleClass().add("hr-form-control");

        TextField defaultRateField = new TextField("15.0");
        defaultRateField.setPromptText("e.g. 15.0");
        defaultRateField.getStyleClass().add("hr-form-control");

        CheckBox skipExisting = new CheckBox("Skip employees who already have payroll this month");
        skipExisting.setSelected(true);
        skipExisting.getStyleClass().add("hr-dialog-check");

        grid.add(new Label("For Month:"), 0, 0);
        grid.add(monthPicker, 1, 0);
        grid.add(new Label("Default Hourly Rate:"), 0, 1);
        grid.add(defaultRateField, 1, 1);

        content.getChildren().addAll(titleLabel, descLabel, grid, skipExisting);
        dp.setContent(content);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                LocalDate selectedMonth = monthPicker.getValue();
                double defaultRate;
                try { defaultRate = Double.parseDouble(defaultRateField.getText().trim()); }
                catch (NumberFormatException ex) { defaultRate = 15.0; }

                final double baseDefaultRate = defaultRate;
                final boolean doSkip = skipExisting.isSelected();

                // Show progress indicator
                ProgressIndicator progress = new ProgressIndicator();
                progress.setPrefSize(40, 40);
                Label progressLabel = new Label("Generating payroll...");
                progressLabel.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 13;");
                VBox progressBox = new VBox(10, progress, progressLabel);
                progressBox.setAlignment(Pos.CENTER);
                progressBox.setPadding(new Insets(40));
                contentArea.getChildren().add(progressBox);

                AppThreadPool.io(() -> {
                    // Fetch existing payrolls if skip is checked
                    Set<Integer> existingUserIds = new HashSet<>();
                    if (doSkip) {
                        try {
                            List<Payroll> existing = servicePayroll.recuperer();
                            for (Payroll ep : existing) {
                                if (ep.getMonth() != null &&
                                        ep.getMonth().toLocalDate().getMonth() == selectedMonth.getMonth() &&
                                        ep.getMonth().toLocalDate().getYear() == selectedMonth.getYear()) {
                                    existingUserIds.add(ep.getUserId());
                                }
                            }
                        } catch (SQLException e) { /* continue */ }
                    }

                    int generated = 0, skipped = 0;

                    for (User u : allUsers) {
                        if ("ADMIN".equals(u.getRole())) continue;
                        if (existingUserIds.contains(u.getId())) { skipped++; continue; }
                        try {
                            // Count attendance hours for this user in the selected month
                            List<Attendance> userAtt = serviceAttendance.getByUser(u.getId());
                            double totalHours = userAtt.stream()
                                    .filter(a -> a.getDate() != null &&
                                            a.getDate().toLocalDate().getMonth() == selectedMonth.getMonth() &&
                                            a.getDate().toLocalDate().getYear() == selectedMonth.getYear())
                                    .mapToDouble(Attendance::getHoursWorked)
                                    .sum();

                            double hourlyRate = u.getHourlyRate() > 0 ? u.getHourlyRate() : baseDefaultRate;
                            double baseSalary;
                            if (u.getMonthlySalary() > 0) {
                                baseSalary = u.getMonthlySalary();
                            } else {
                                baseSalary = totalHours * hourlyRate;
                            }

                            long absentDays = userAtt.stream()
                                    .filter(a -> a.getDate() != null &&
                                            a.getDate().toLocalDate().getMonth() == selectedMonth.getMonth() &&
                                            a.getDate().toLocalDate().getYear() == selectedMonth.getYear() &&
                                            "ABSENT".equals(a.getStatus()))
                                    .count();
                            double dailyRate = baseSalary > 0 ? baseSalary / 22.0 : 0;
                            double deductions = absentDays * dailyRate;
                            double netSalary = baseSalary - deductions;

                            Payroll p = new Payroll();
                            p.setUserId(u.getId());
                            p.setMonth(Date.valueOf(selectedMonth));
                            p.setYear(selectedMonth.getYear());
                            p.setBaseSalary(baseSalary);
                            p.setBonus(0);
                            p.setDeductions(deductions);
                            p.setNetSalary(netSalary);
                            p.setAmount(netSalary);
                            p.setTotalHoursWorked(totalHours);
                            p.setHourlyRate(hourlyRate);
                            p.setStatus("PENDING");

                            servicePayroll.ajouter(p);
                            generated++;
                        } catch (SQLException e) {
                            System.err.println("Failed to generate payroll for user " + u.getId() + ": " + e.getMessage());
                        }
                    }

                    final int gen = generated, skip = skipped;
                    Platform.runLater(() -> {
                        contentArea.getChildren().remove(progressBox);
                        showInfo("Payroll generated for " + gen + " employees." +
                                (skip > 0 ? "\n" + skip + " skipped (already existed)." : ""));
                        showPayroll();
                    });
                });
            }
        });
    }

    // ==================== PDF EXPORT ====================

    private void exportSinglePayslipPdf(Payroll p) {
        String empName = getUserName(p.getUserId());
        String monthStr = p.getMonth() != null
                ? p.getMonth().toLocalDate().format(DateTimeFormatter.ofPattern("yyyy_MM"))
                : "unknown";

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Pay Slip PDF");
        fc.setInitialFileName("Payslip_" + empName.replace(" ", "_") + "_" + monthStr + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fc.showSaveDialog(rootPane.getScene().getWindow());
        if (file == null) return;

        try {
            PayrollPdfExporter.exportEmployeePayslip(file, p, empName);
            showInfo("Pay slip saved to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            showError("Failed to export PDF: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void exportAllPayrollPdf() {
        try {
            List<Payroll> payrolls = servicePayroll.recuperer();
            if (payrolls.isEmpty()) {
                showError("No payroll records to export.");
                return;
            }

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Full Payroll Report");
            fc.setInitialFileName("Payroll_Report_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            File file = fc.showSaveDialog(rootPane.getScene().getWindow());
            if (file == null) return;

            // Build name map from cache for PDF export
            Map<Integer, String> nameMap = new HashMap<>();
            for (User u : allUsers) nameMap.put(u.getId(), u.getFirstName() + " " + u.getLastName());
            PayrollPdfExporter.exportAllPayroll(file, payrolls, nameMap);
            showInfo("Payroll report saved (" + payrolls.size() + " records):\n" + file.getAbsolutePath());
        } catch (SQLException ex) {
            showError("Failed to load payroll data: " + ex.getMessage());
        } catch (IOException ex) {
            showError("Failed to export PDF: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ==================== AI INSIGHTS TAB ====================

    private void showAIInsights() {
        VBox view = new VBox(20);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        Label title = new Label("🤖 AI-Powered HR Insights");
        title.getStyleClass().add("hr-view-title");

        Label subtitle = new Label("Get AI-generated analysis of attendance, leave, and payroll trends");
        subtitle.getStyleClass().add("hr-subtitle");

        // Insight result area
        TextArea insightArea = new TextArea();
        insightArea.setEditable(false);
        insightArea.setWrapText(true);
        insightArea.setPrefHeight(400);
        insightArea.setPromptText("Click 'Generate Insights' to get AI analysis...");
        insightArea.getStyleClass().add("hr-insight-area");

        // Status label
        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("hr-subtitle");

        Button btnGenerate = new Button("🧠 Generate Insights");
        btnGenerate.getStyleClass().add("hr-action-btn");
        btnGenerate.setOnAction(e -> {
            btnGenerate.setDisable(true);
            btnGenerate.setText("⏳ Analyzing...");
            statusLabel.setText("Gathering HR data and sending to AI...");

            AppThreadPool.io(() -> {
                try {
                    // Gather attendance summary
                    String attendanceSummary = buildAttendanceSummary();
                    String leaveSummary = buildLeaveSummary();
                    String payrollSummary = buildPayrollSummary();

                    ZAIService zai = new ZAIService();
                    String insights = zai.generateHRInsights(attendanceSummary, leaveSummary, payrollSummary);

                    Platform.runLater(() -> {
                        insightArea.setText(insights);
                        statusLabel.setText("✅ Analysis complete");
                        btnGenerate.setDisable(false);
                        btnGenerate.setText("🧠 Generate Insights");
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        insightArea.setText("Failed to generate insights: " + ex.getMessage());
                        statusLabel.setText("❌ Analysis failed");
                        btnGenerate.setDisable(false);
                        btnGenerate.setText("🧠 Generate Insights");
                    });
                }
            });
        });

        // Meeting prep button
        Button btnMeetingPrep = new Button("📋 Prepare HR Meeting");
        btnMeetingPrep.getStyleClass().add("hr-action-btn");
        btnMeetingPrep.setOnAction(e -> {
            btnMeetingPrep.setDisable(true);
            btnMeetingPrep.setText("⏳ Preparing...");

            AppThreadPool.io(() -> {
                try {
                    String attendanceSummary = buildAttendanceSummary();
                    String leaveSummary = buildLeaveSummary();

                    ZAIService zai = new ZAIService();
                    String agenda = zai.prepMeeting("HR Department Review",
                            "Attendance: " + attendanceSummary + "\nLeaves: " + leaveSummary,
                            "HR team");

                    Platform.runLater(() -> {
                        insightArea.setText(agenda);
                        statusLabel.setText("✅ Meeting agenda ready");
                        btnMeetingPrep.setDisable(false);
                        btnMeetingPrep.setText("📋 Prepare HR Meeting");
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        insightArea.setText("Failed: " + ex.getMessage());
                        btnMeetingPrep.setDisable(false);
                        btnMeetingPrep.setText("📋 Prepare HR Meeting");
                    });
                }
            });
        });

        HBox buttons = new HBox(12, btnGenerate, btnMeetingPrep);
        buttons.setAlignment(Pos.CENTER_LEFT);

        view.getChildren().addAll(title, subtitle, buttons, statusLabel, insightArea);
        contentArea.getChildren().setAll(view);
    }

    private String buildAttendanceSummary() {
        try {
            var records = serviceAttendance.recuperer();
            int total = records.size();
            long presentCount = records.stream().filter(a -> "PRESENT".equalsIgnoreCase(a.getStatus())).count();
            long absentCount = records.stream().filter(a -> "ABSENT".equalsIgnoreCase(a.getStatus())).count();
            long lateCount = records.stream().filter(a -> "LATE".equalsIgnoreCase(a.getStatus())).count();
            return String.format("Total records: %d | Present: %d | Absent: %d | Late: %d | Rate: %.1f%%",
                    total, presentCount, absentCount, lateCount,
                    total > 0 ? (presentCount * 100.0 / total) : 0);
        } catch (SQLException e) {
            return "Unable to load attendance data";
        }
    }

    private String buildLeaveSummary() {
        try {
            var leaves = serviceLeave.recuperer();
            int total = leaves.size();
            long approved = leaves.stream().filter(l -> "APPROVED".equalsIgnoreCase(l.getStatus())).count();
            long pending = leaves.stream().filter(l -> "PENDING".equalsIgnoreCase(l.getStatus())).count();
            long rejected = leaves.stream().filter(l -> "REJECTED".equalsIgnoreCase(l.getStatus())).count();
            Map<String, Long> byType = leaves.stream().collect(Collectors.groupingBy(
                    l -> l.getType() != null ? l.getType() : "UNKNOWN", Collectors.counting()));
            return String.format("Total: %d | Approved: %d | Pending: %d | Rejected: %d | By type: %s",
                    total, approved, pending, rejected, byType);
        } catch (SQLException e) {
            return "Unable to load leave data";
        }
    }

    private String buildPayrollSummary() {
        try {
            var payrolls = servicePayroll.recuperer();
            int total = payrolls.size();
            double totalNet = payrolls.stream().mapToDouble(p -> p.getNetSalary()).sum();
            double avgNet = total > 0 ? totalNet / total : 0;
            return String.format("Records: %d | Total net: %.2f TND | Average net: %.2f TND",
                    total, totalNet, avgNet);
        } catch (SQLException e) {
            return "Unable to load payroll data";
        }
    }

    // ==================== UTILITIES ====================

    private Label createColLabel(String text, double width) {
        Label lbl = new Label(text);
        lbl.setPrefWidth(width);
        lbl.setMaxWidth(width);
        return lbl;
    }

    private void confirmDelete(String type, String name, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete " + type + (name.isEmpty() ? "" : " '" + name + "'") + "?");
        alert.setContentText("This action cannot be undone.");
        DialogHelper.theme(alert);
        alert.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) action.run();
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle("Error");
        DialogHelper.theme(alert);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle("Info");
        DialogHelper.theme(alert);
        alert.showAndWait();
    }
}
