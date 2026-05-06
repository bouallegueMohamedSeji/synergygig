package controllers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import entities.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import services.*;
import utils.*;

import java.awt.Desktop;
import java.io.File;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for the Offer & Contract Management module.
 * 5 tabs: Marketplace, My Offers, Applications, Contracts, AI Assistant.
 */
public class OfferContractController {

    // ── FXML injected fields ──
    @FXML private BorderPane rootPane;
    @FXML private HBox tabBar;
    @FXML private StackPane contentArea;
    @FXML private Label headerTitle, headerRole;

    // Tab 1: Marketplace
    @FXML private VBox marketplaceView;
    @FXML private TextField marketSearchField;
    @FXML private ComboBox<String> filterType, filterCurrency;
    @FXML private HBox marketFilterBar;
    @FXML private FlowPane offerGrid;
    @FXML private Button filterMktAll, filterMktFullTime, filterMktFreelance, filterMktInternship, filterMktContract;

    // Tab 1: Currency converter
    @FXML private ComboBox<String> convertToCurrency;

    // Tab 1b: External Job Listings
    @FXML private HBox externalJobsHeader;
    @FXML private FlowPane externalJobGrid;
    @FXML private Button btnRefreshExternal;

    // Tab 2: My Offers
    @FXML private VBox myOffersView;
    @FXML private VBox myOffersList;
    @FXML private Button btnNewOffer;

    // Tab 3: Applications
    @FXML private VBox applicationsView;
    @FXML private VBox applicationsList;
    @FXML private ComboBox<String> filterAppStatus;

    // Tab 4: Contracts
    @FXML private VBox contractsView;
    @FXML private TableView<Contract> contractsTable;
    @FXML private TableColumn<Contract, Integer> colContractId;
    @FXML private TableColumn<Contract, String> colContractOffer, colContractParty, colContractAmount,
            colContractStatus, colContractRisk, colContractDates, colContractActions;
    @FXML private ComboBox<String> filterContractStatus;

    // Tab 5: AI Assistant
    @FXML private VBox aiAssistantView;
    @FXML private VBox aiChatBox;
    @FXML private ScrollPane aiChatScroll;
    @FXML private TextField aiInputField;
    @FXML private Label aiStatusLabel;

    // ── Services ──
    private final ServiceOffer serviceOffer = new ServiceOffer();
    private final ServiceJobApplication serviceApp = new ServiceJobApplication();
    private final ServiceContract serviceContract = new ServiceContract();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceNotification serviceNotification = new ServiceNotification();
    private final ServiceInterview serviceInterview = new ServiceInterview();
    private ZAIService zaiService;

    // ── State ──
    private User currentUser;
    private boolean isOwnerOrAdmin;
    private Button activeTab;
    private Region ocAmbienceIndicator;
    private String currentMarketFilter = "ALL";
    private Map<Integer, Offer> offerMap = new HashMap<>();
    private List<Offer> allOffers = new ArrayList<>();
    private List<Map<String, String>> aiChatHistory = new ArrayList<>();
    private Contract selectedAiContract;
    private boolean externalJobsLoaded = false;

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        String role = currentUser != null ? currentUser.getRole() : "";
        isOwnerOrAdmin = "ADMIN".equals(role) || "PROJECT_OWNER".equals(role) || "HR_MANAGER".equals(role);

        headerRole.setText(isOwnerOrAdmin ? "Owner View" : "Applicant View");

        zaiService = new ZAIService();

        // Setup filter combos (UI only, instant)
        filterType.setItems(FXCollections.observableArrayList("All", "FULL_TIME", "PART_TIME", "FREELANCE", "INTERNSHIP", "CONTRACT"));
        filterType.setValue("All");
        filterType.setOnAction(e -> refreshMarketplace());

        filterCurrency.setItems(FXCollections.observableArrayList("All", "USD", "EUR", "GBP", "TND"));
        filterCurrency.setValue("All");
        filterCurrency.setOnAction(e -> refreshMarketplace());

        // Currency converter combo (live rates)
        List<String> convertOptions = new ArrayList<>();
        convertOptions.add("Original");
        convertOptions.addAll(java.util.Arrays.asList(CurrencyService.CURRENCIES));
        convertToCurrency.setItems(FXCollections.observableArrayList(convertOptions));
        convertToCurrency.setValue("Original");
        convertToCurrency.setOnAction(e -> refreshMarketplace());

        filterAppStatus.setItems(FXCollections.observableArrayList("All", "PENDING", "REVIEWED", "SHORTLISTED", "ACCEPTED", "REJECTED", "WITHDRAWN"));
        filterAppStatus.setValue("All");

        filterContractStatus.setItems(FXCollections.observableArrayList("All", "DRAFT", "PENDING_SIGNATURE", "ACTIVE", "COMPLETED", "TERMINATED", "DISPUTED"));
        filterContractStatus.setValue("All");

        // Build tabs
        List<String[]> tabs = new ArrayList<>();
        tabs.add(new String[]{"🏪", "Marketplace"});
        if (isOwnerOrAdmin) {
            tabs.add(new String[]{"📋", "My Offers"});
        }
        tabs.add(new String[]{"📩", "Applications"});
        if (isOwnerOrAdmin) {
            tabs.add(new String[]{"📄", "Contracts"});
        }
        tabs.add(new String[]{"🤖", "AI Assistant"});

        for (String[] tab : tabs) {
            Button btn = new Button(tab[0] + "  " + tab[1]);
            btn.getStyleClass().add("oc-tab-btn");
            btn.setUserData(tab[1]);
            btn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
                switchTab(btn, tab[1]);
            });
            tabBar.getChildren().add(btn);
        }

        // Spotlight Pill Navbar
        setupSpotlightPillNavbar();

        setupContractsTable();

        // Load data in background, then trigger first tab
        AppThreadPool.io(() -> {
            loadUserNames();
            loadOfferMap();
            Platform.runLater(() -> {
                if (!tabBar.getChildren().isEmpty()) {
                    Button first = (Button) tabBar.getChildren().get(0);
                    switchTab(first, "Marketplace");
                }
            });
        });
    }

    // ==================== Data Loading ====================

    private void loadUserNames() {
        utils.UserNameCache.refresh();
    }

    private void loadOfferMap() {
        try {
            allOffers = serviceOffer.recuperer();
            offerMap.clear();
            for (Offer o : allOffers) offerMap.put(o.getId(), o);
        } catch (SQLException e) { System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }

    private String getUserName(int userId) {
        return utils.UserNameCache.getName(userId);
    }

    // ==================== Spotlight Pill Navbar ====================

    private void setupSpotlightPillNavbar() {
        javafx.scene.layout.VBox topBar = (javafx.scene.layout.VBox) tabBar.getParent();
        int idx = topBar.getChildren().indexOf(tabBar);
        topBar.getChildren().remove(tabBar);

        StackPane navContainer = new StackPane();
        navContainer.getStyleClass().add("oc-nav-container");

        StackPane pill = new StackPane();
        pill.getStyleClass().add("oc-nav-pill");

        tabBar.getStyleClass().remove("oc-tab-bar");
        tabBar.getStyleClass().add("oc-pill-tabs");
        tabBar.setAlignment(Pos.CENTER);

        Region spotlightGlow = new Region();
        spotlightGlow.setMouseTransparent(true);
        spotlightGlow.setOpacity(0);
        spotlightGlow.getStyleClass().add("oc-spotlight-glow");

        ocAmbienceIndicator = new Region();
        ocAmbienceIndicator.setManaged(false);
        ocAmbienceIndicator.setPrefHeight(2);
        ocAmbienceIndicator.setMaxHeight(2);
        ocAmbienceIndicator.setPrefWidth(50);
        ocAmbienceIndicator.setMaxWidth(50);
        ocAmbienceIndicator.getStyleClass().add("oc-ambience-line");

        Region trackLine = new Region();
        trackLine.setManaged(false);
        trackLine.setPrefHeight(1);
        trackLine.setMaxHeight(1);
        trackLine.getStyleClass().add("oc-track-line");

        pill.getChildren().addAll(tabBar, spotlightGlow, trackLine, ocAmbienceIndicator);
        navContainer.getChildren().add(pill);
        topBar.getChildren().add(idx, navContainer);

        pill.layoutBoundsProperty().addListener((obs, ov, nv) -> {
            double h = nv.getHeight();
            trackLine.setLayoutY(h - 1);
            trackLine.setPrefWidth(nv.getWidth() - 16);
            trackLine.setLayoutX(8);
            ocAmbienceIndicator.setLayoutY(h - 2);
        });

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

    private void animateOcAmbience(Button btn) {
        if (ocAmbienceIndicator == null) return;
        Platform.runLater(() -> {
            javafx.geometry.Bounds b = btn.getBoundsInParent();
            if (b.getWidth() == 0) return;
            double offset = tabBar.getLayoutX();
            double targetX = offset + b.getMinX() + b.getWidth() / 2 - ocAmbienceIndicator.getPrefWidth() / 2;
            javafx.animation.KeyValue kv = new javafx.animation.KeyValue(
                    ocAmbienceIndicator.layoutXProperty(), targetX,
                    javafx.animation.Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
            javafx.animation.KeyFrame kf = new javafx.animation.KeyFrame(
                    javafx.util.Duration.millis(350), kv);
            javafx.animation.Timeline tl = new javafx.animation.Timeline(kf);
            tl.play();
        });
    }

    // ==================== Tab Switching ====================

    private void switchTab(Button btn, String tabName) {
        if (activeTab != null) activeTab.getStyleClass().remove("oc-tab-active");
        btn.getStyleClass().add("oc-tab-active");
        activeTab = btn;
        animateOcAmbience(btn);

        // Hide all views
        marketplaceView.setVisible(false); marketplaceView.setManaged(false);
        myOffersView.setVisible(false); myOffersView.setManaged(false);
        applicationsView.setVisible(false); applicationsView.setManaged(false);
        contractsView.setVisible(false); contractsView.setManaged(false);
        aiAssistantView.setVisible(false); aiAssistantView.setManaged(false);

        switch (tabName) {
            case "Marketplace":
                marketplaceView.setVisible(true); marketplaceView.setManaged(true);
                refreshMarketplace();
                break;
            case "My Offers":
                myOffersView.setVisible(true); myOffersView.setManaged(true);
                refreshMyOffers();
                break;
            case "Applications":
                applicationsView.setVisible(true); applicationsView.setManaged(true);
                refreshApplications();
                break;
            case "Contracts":
                contractsView.setVisible(true); contractsView.setManaged(true);
                refreshContracts();
                break;
            case "AI Assistant":
                aiAssistantView.setVisible(true); aiAssistantView.setManaged(true);
                break;
            case "Resume Parser":
                DashboardController.getInstance().navigateTo("/fxml/ResumeParser.fxml");
                break;
            case "Interview Prep":
                DashboardController.getInstance().navigateTo("/fxml/InterviewPrep.fxml");
                break;
        }
    }

    // ================================================================
    // TAB 1: MARKETPLACE
    // ================================================================

    private void refreshMarketplace() {
        offerGrid.getChildren().clear();
        loadOfferMap();

        String searchText = marketSearchField.getText() != null ? marketSearchField.getText().toLowerCase() : "";
        String typeFilter = filterType.getValue();
        String currFilter = filterCurrency.getValue();

        List<Offer> filtered = allOffers.stream()
                .filter(o -> "PUBLISHED".equals(o.getStatus()))
                .filter(o -> o.getEndDate() == null || !o.getEndDate().toLocalDate().isBefore(LocalDate.now()))
                .filter(o -> searchText.isEmpty()
                        || o.getTitle().toLowerCase().contains(searchText)
                        || (o.getDescription() != null && o.getDescription().toLowerCase().contains(searchText))
                        || (o.getRequiredSkills() != null && o.getRequiredSkills().toLowerCase().contains(searchText)))
                .filter(o -> "All".equals(typeFilter) || typeFilter.equals(o.getOfferType()))
                .filter(o -> "All".equals(currFilter) || currFilter.equals(o.getCurrency()))
                .filter(o -> "ALL".equals(currentMarketFilter) || currentMarketFilter.equals(o.getOfferType()))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label("No open offers found.");
            empty.getStyleClass().add("oc-empty-label");
            offerGrid.getChildren().add(empty);
            return;
        }

        for (Offer o : filtered) {
            offerGrid.getChildren().add(createOfferCard(o));
        }

        // Load external jobs (only first time or if cache expired)
        if (!externalJobsLoaded) {
            refreshExternalJobs();
        }
    }

    private VBox createOfferCard(Offer o) {
        VBox card = new VBox(8);
        card.getStyleClass().add("oc-offer-card");
        card.setPrefWidth(300);
        card.setPadding(new Insets(16));

        // Type badge
        Label badge = new Label(o.getOfferType());
        badge.getStyleClass().addAll("oc-badge", "oc-badge-" + o.getOfferType().toLowerCase().replace("_", "-"));

        Label title = new Label(o.getTitle());
        title.getStyleClass().add("oc-card-title");
        title.setWrapText(true);

        Label desc = new Label(o.getDescription() != null ?
                (o.getDescription().length() > 120 ? o.getDescription().substring(0, 120) + "..." : o.getDescription())
                : "No description");
        desc.getStyleClass().add("oc-card-desc");
        desc.setWrapText(true);
        desc.setMaxHeight(60);

        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        Label amount = new Label(String.format("%s %.0f", o.getCurrency(), o.getAmount()));
        amount.getStyleClass().add("oc-card-amount");

        // Live currency conversion
        String targetCur = convertToCurrency.getValue();
        if (targetCur != null && !"Original".equals(targetCur)
                && !targetCur.equalsIgnoreCase(o.getCurrency())) {
            double converted = CurrencyService.convert(o.getAmount(), o.getCurrency(), targetCur);
            if (converted > 0) {
                Label convertedLbl = new Label("≈ " + CurrencyService.formatAmount(converted, targetCur));
                convertedLbl.getStyleClass().add("oc-card-converted");
                metaRow.getChildren().add(convertedLbl);
            }
        }

        Label location = new Label(o.getLocation() != null ? "📍 " + o.getLocation() : "");
        location.getStyleClass().add("oc-card-meta");
        metaRow.getChildren().addAll(amount, location);

        Label skills = new Label(o.getRequiredSkills() != null ? "🔧 " + o.getRequiredSkills() : "");
        skills.getStyleClass().add("oc-card-skills");
        skills.setWrapText(true);

        Label owner = new Label("By: " + getUserName(o.getOwnerId()));
        owner.getStyleClass().add("oc-card-meta");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        // Don't show Apply if it's the user's own offer
        if (currentUser != null && o.getOwnerId() != currentUser.getId()) {
            Button btnApply = new Button("Apply");
            btnApply.getStyleClass().add("oc-btn-primary");
            btnApply.setOnAction(e -> showApplyDialog(o));
            actions.getChildren().add(btnApply);
        }

        Button btnView = new Button("Details");
        btnView.getStyleClass().add("oc-btn-secondary");
        btnView.setOnAction(e -> showOfferDetails(o));
        actions.getChildren().add(btnView);

        card.getChildren().addAll(badge, title, desc, metaRow, skills, owner, actions);
        CardEffects.applyHoverEffect(card);
        return card;
    }

    @FXML private void onMarketSearchChanged() { refreshMarketplace(); }
    @FXML private void filterMarketAll() { setMarketFilter("ALL", filterMktAll); }
    @FXML private void filterMarketType(javafx.event.ActionEvent e) {
        Button btn = (Button) e.getSource();
        String type = btn.getText().toUpperCase().replace("-", "_");
        setMarketFilter(type, btn);
    }

    private void setMarketFilter(String type, Button btn) {
        currentMarketFilter = type;
        for (Node n : marketFilterBar.getChildren()) {
            n.getStyleClass().remove("oc-filter-active");
        }
        btn.getStyleClass().add("oc-filter-active");
        refreshMarketplace();
    }

    // ================================================================
    // EXTERNAL JOB LISTINGS (Remotive, Arbeitnow, Jobicy, RemoteOK)
    // ================================================================

    private void refreshExternalJobs() {
        // Show loading indicator
        externalJobGrid.getChildren().clear();
        externalJobsHeader.setVisible(true);
        externalJobsHeader.setManaged(true);
        externalJobGrid.setVisible(true);
        externalJobGrid.setManaged(true);

        Label loading = new Label("⏳ Loading external job listings...");
        loading.getStyleClass().add("oc-empty-label");
        externalJobGrid.getChildren().add(loading);

        AppThreadPool.io(() -> {
            List<entities.ExternalJob> jobs = services.ExternalJobService.fetchAll();
            Platform.runLater(() -> {
                externalJobGrid.getChildren().clear();
                if (jobs.isEmpty()) {
                    Label empty = new Label("No external jobs available at the moment.");
                    empty.getStyleClass().add("oc-empty-label");
                    externalJobGrid.getChildren().add(empty);
                } else {
                    for (entities.ExternalJob job : jobs) {
                        externalJobGrid.getChildren().add(createExternalJobCard(job));
                    }
                }
                externalJobsLoaded = true;
            });
        });
    }

    @FXML
    private void onRefreshExternalJobs() {
        services.ExternalJobService.clearCache();
        externalJobsLoaded = false;
        refreshExternalJobs();
    }

    private VBox createExternalJobCard(entities.ExternalJob job) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("oc-offer-card", "oc-external-card");
        card.setPrefWidth(300);
        card.setPadding(new Insets(16));

        // Source badge + Type badge row
        HBox badgeRow = new HBox(6);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        Label sourceBadge = new Label(job.getSource());
        sourceBadge.getStyleClass().addAll("oc-badge", "oc-badge-source",
                "oc-badge-" + job.getSource().toLowerCase().replace(" ", ""));

        Label typeBadge = new Label(job.getJobType());
        typeBadge.getStyleClass().addAll("oc-badge",
                "oc-badge-" + job.getJobType().toLowerCase().replace("-", "").replace("_", ""));

        badgeRow.getChildren().addAll(sourceBadge, typeBadge);

        // Title
        Label title = new Label(job.getTitle());
        title.getStyleClass().add("oc-card-title");
        title.setWrapText(true);

        // Company
        Label company = new Label("🏢 " + (job.getCompany() != null ? job.getCompany() : "Unknown"));
        company.getStyleClass().add("oc-card-meta");

        // Description snippet
        Label desc = new Label(job.getDescription() != null && !job.getDescription().isEmpty()
                ? job.getDescription() : "No description available");
        desc.getStyleClass().add("oc-card-desc");
        desc.setWrapText(true);
        desc.setMaxHeight(50);

        // Meta row: salary & location
        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        if (job.getSalary() != null && !job.getSalary().isEmpty()) {
            Label salary = new Label("💰 " + job.getSalary());
            salary.getStyleClass().add("oc-card-amount");
            metaRow.getChildren().add(salary);
        }
        if (job.getLocation() != null && !job.getLocation().isEmpty()) {
            Label loc = new Label("📍 " + job.getLocation());
            loc.getStyleClass().add("oc-card-meta");
            metaRow.getChildren().add(loc);
        }

        // Category
        Label category = new Label(job.getCategory() != null && !job.getCategory().isEmpty()
                ? "🏷 " + job.getCategory() : "");
        category.getStyleClass().add("oc-card-skills");
        category.setWrapText(true);

        // Action: View Job (opens in browser)
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button btnView = new Button("View Job ↗");
        btnView.getStyleClass().add("oc-btn-external");
        btnView.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new java.net.URI(job.getUrl()));
                }
            } catch (Exception ex) {
                System.err.println("Failed to open URL: " + ex.getMessage());
            }
        });
        actions.getChildren().add(btnView);

        card.getChildren().addAll(badgeRow, title, company, desc, metaRow, category, actions);
        CardEffects.applyHoverEffect(card);
        return card;
    }

    private void showOfferDetails(Offer o) {
        Dialog<Void> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Offer Details");
        dialog.setHeaderText(o.getTitle());

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        content.setPrefWidth(500);

        // Basic info
        Label typeStatus = new Label(String.format("Type: %s  •  Status: %s", o.getOfferType(), o.getStatus()));
        typeStatus.getStyleClass().add("oc-card-meta");
        typeStatus.setStyle("-fx-font-size: 13;");

        Label loc = new Label("📍 " + (o.getLocation() != null ? o.getLocation() : "N/A"));
        loc.getStyleClass().add("oc-card-meta");

        Label amountLbl = new Label(String.format("💰 %s %.2f", o.getCurrency(), o.getAmount()));
        amountLbl.getStyleClass().add("oc-card-amount");
        amountLbl.setStyle("-fx-font-size: 18;");

        // ── Live currency converter row ──
        HBox converterRow = new HBox(8);
        converterRow.setAlignment(Pos.CENTER_LEFT);
        converterRow.getStyleClass().add("oc-converter-row");

        Label converterIcon = new Label("💱");
        converterIcon.setStyle("-fx-font-size: 14;");

        ComboBox<String> targetCombo = new ComboBox<>(FXCollections.observableArrayList(CurrencyService.CURRENCIES));
        targetCombo.getStyleClass().add("oc-combo");
        targetCombo.setPromptText("Convert to…");
        targetCombo.setPrefWidth(110);

        Label convertedResult = new Label("");
        convertedResult.getStyleClass().add("oc-card-converted");
        convertedResult.setStyle("-fx-font-size: 15;");

        Label rateInfo = new Label("");
        rateInfo.getStyleClass().add("oc-card-meta");
        rateInfo.setStyle("-fx-font-size: 10;");

        targetCombo.setOnAction(ev -> {
            String target = targetCombo.getValue();
            if (target == null || target.equalsIgnoreCase(o.getCurrency())) {
                convertedResult.setText("");
                rateInfo.setText("");
                return;
            }
            AppThreadPool.io(() -> {
                double converted = CurrencyService.convert(o.getAmount(), o.getCurrency(), target);
                double rate = CurrencyService.getRate(o.getCurrency(), target);
                Platform.runLater(() -> {
                    if (converted > 0) {
                        convertedResult.setText("≈ " + CurrencyService.formatAmount(converted, target));
                        rateInfo.setText(String.format("1 %s = %.4f %s (live)", o.getCurrency(), rate, target));
                    } else {
                        convertedResult.setText("Unavailable");
                        rateInfo.setText("");
                    }
                });
            });
        });

        converterRow.getChildren().addAll(converterIcon, targetCombo, convertedResult);

        // Skills & description
        Label skillsLbl = new Label("🔧 " + (o.getRequiredSkills() != null ? o.getRequiredSkills() : "N/A"));
        skillsLbl.getStyleClass().add("oc-card-skills");
        skillsLbl.setWrapText(true);
        skillsLbl.setStyle("-fx-font-size: 12;");

        Label descLbl = new Label(o.getDescription() != null ? o.getDescription() : "No description");
        descLbl.getStyleClass().add("oc-card-desc");
        descLbl.setWrapText(true);
        descLbl.setStyle("-fx-font-size: 12;");

        Label ownerLbl = new Label("Posted by: " + getUserName(o.getOwnerId()));
        ownerLbl.getStyleClass().add("oc-card-meta");

        Label datesLbl = new Label(String.format("📅 %s → %s",
                o.getStartDate() != null ? o.getStartDate().toString() : "TBD",
                o.getEndDate() != null ? o.getEndDate().toString() : "TBD"));
        datesLbl.getStyleClass().add("oc-card-meta");

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-background-color: #2A2A4A;");
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color: #2A2A4A;");

        content.getChildren().addAll(
                typeStatus, loc, amountLbl,
                converterRow, rateInfo,
                sep1, skillsLbl, descLbl,
                sep2, ownerLbl, datesLbl
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setMinWidth(520);
        styleDarkDialog(dialog.getDialogPane());
        dialog.showAndWait();
    }

    // ================================================================
    // TAB 2: MY OFFERS (owner/admin only)
    // ================================================================

    private void refreshMyOffers() {
        myOffersList.getChildren().clear();
        try {
            List<Offer> offers = serviceOffer.getByOwner(currentUser.getId());
            if (offers.isEmpty()) {
                Label empty = new Label("You haven't posted any offers yet. Click '+ New Offer' to get started.");
                empty.getStyleClass().add("oc-empty-label");
                myOffersList.getChildren().add(empty);
                return;
            }
            for (Offer o : offers) {
                myOffersList.getChildren().add(createMyOfferRow(o));
            }
        } catch (SQLException e) {
            showError("Failed to load offers: " + e.getMessage());
        }
    }

    private HBox createMyOfferRow(Offer o) {
        HBox row = new HBox(12);
        row.getStyleClass().add("oc-list-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));

        Label badge = new Label(o.getOfferType());
        badge.getStyleClass().addAll("oc-badge", "oc-badge-" + o.getOfferType().toLowerCase().replace("_", "-"));
        badge.setMinWidth(90);

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(o.getTitle());
        title.getStyleClass().add("oc-row-title");
        Label meta = new Label(String.format("%s %.0f • %s • %s",
                o.getCurrency(), o.getAmount(), o.getLocation() != null ? o.getLocation() : "Remote", o.getStatus()));
        meta.getStyleClass().add("oc-row-meta");
        info.getChildren().addAll(title, meta);

        Label status = new Label(o.getStatus());
        status.getStyleClass().addAll("oc-status-badge", "oc-status-" + o.getStatus().toLowerCase());

        Button btnEdit = new Button("Edit");
        btnEdit.getStyleClass().add("oc-btn-secondary");
        btnEdit.setOnAction(e -> showEditOfferDialog(o));

        Button btnDelete = new Button("Delete");
        btnDelete.getStyleClass().add("oc-btn-danger");
        btnDelete.setOnAction(e -> deleteOffer(o));

        Button btnToggle = new Button(Offer.STATUS_DRAFT.equals(o.getStatus()) ? "Publish" : Offer.STATUS_PUBLISHED.equals(o.getStatus()) ? "Close" : o.getStatus());
        if (Offer.STATUS_DRAFT.equals(o.getStatus())) {
            btnToggle.getStyleClass().add("oc-btn-publish");
        } else if (Offer.STATUS_PUBLISHED.equals(o.getStatus())) {
            btnToggle.getStyleClass().add("oc-btn-close");
        } else {
            btnToggle.getStyleClass().add("oc-btn-secondary");
        }
        btnToggle.setOnAction(e -> toggleOfferStatus(o));

        row.getChildren().addAll(badge, info, status, btnToggle, btnEdit, btnDelete);
        return row;
    }

    @FXML
    private void showNewOfferDialog() {
        showOfferFormDialog(null);
    }

    private void showEditOfferDialog(Offer o) {
        showOfferFormDialog(o);
    }

    private void showOfferFormDialog(Offer existing) {
        Dialog<Offer> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle(existing == null ? "New Offer" : "Edit Offer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(580);

        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

        VBox form = new VBox(14);
        form.setPadding(new Insets(20, 24, 20, 24));

        // ── Title ──
        VBox titleSec = new VBox(4);
        Label lblTitle = new Label("Title *");
        lblTitle.getStyleClass().add("offer-field-label");
        TextField tfTitle = new TextField(existing != null ? existing.getTitle() : "");
        tfTitle.setPromptText("Offer title");
        tfTitle.getStyleClass().add("offer-field-input");
        Label errTitle = new Label();
        errTitle.getStyleClass().add("offer-field-error");
        errTitle.setVisible(false); errTitle.setManaged(false);
        titleSec.getChildren().addAll(lblTitle, tfTitle, errTitle);

        // ── Description ──
        VBox descSec = new VBox(4);
        Label lblDesc = new Label("Description *");
        lblDesc.getStyleClass().add("offer-field-label");
        TextArea taDesc = new TextArea(existing != null ? existing.getDescription() : "");
        taDesc.setPromptText("Describe the offer, requirements, responsibilities...");
        taDesc.setPrefRowCount(3);
        taDesc.setWrapText(true);
        taDesc.getStyleClass().add("offer-field-input");
        Label errDesc = new Label();
        errDesc.getStyleClass().add("offer-field-error");
        errDesc.setVisible(false); errDesc.setManaged(false);
        descSec.getChildren().addAll(lblDesc, taDesc, errDesc);

        // ── Type + Skills ──
        HBox typeSkillsRow = new HBox(12);
        VBox typeBox = new VBox(4);
        typeBox.setPrefWidth(160);
        Label lblType = new Label("Type");
        lblType.getStyleClass().add("offer-field-label");
        ComboBox<String> cbType = new ComboBox<>(FXCollections.observableArrayList(
                "FULL_TIME", "PART_TIME", "FREELANCE", "INTERNSHIP", "CONTRACT"));
        cbType.setValue(existing != null ? existing.getOfferType() : "FREELANCE");
        cbType.setMaxWidth(Double.MAX_VALUE);
        typeBox.getChildren().addAll(lblType, cbType);

        VBox skillsBox = new VBox(4);
        HBox.setHgrow(skillsBox, Priority.ALWAYS);
        Label lblSkills = new Label("Skills *");
        lblSkills.getStyleClass().add("offer-field-label");
        TextField tfSkills = new TextField(existing != null ? existing.getRequiredSkills() : "");
        tfSkills.setPromptText("Java, Python, React...");
        tfSkills.getStyleClass().add("offer-field-input");
        Label errSkills = new Label();
        errSkills.getStyleClass().add("offer-field-error");
        errSkills.setVisible(false); errSkills.setManaged(false);
        skillsBox.getChildren().addAll(lblSkills, tfSkills, errSkills);
        typeSkillsRow.getChildren().addAll(typeBox, skillsBox);

        // ── Location: Remote / On-site toggle ──
        VBox locationSec = new VBox(6);
        Label lblLocation = new Label("Location *");
        lblLocation.getStyleClass().add("offer-field-label");

        boolean[] isRemote = { true };
        if (existing != null && existing.getLocation() != null
                && !"Remote".equalsIgnoreCase(existing.getLocation().trim())) {
            isRemote[0] = false;
        }

        Button btnRemote = new Button("\uD83C\uDF0D  Remote");
        Button btnOnsite = new Button("\uD83C\uDFE2  On-site");
        HBox toggleBox = new HBox(0, btnRemote, btnOnsite);
        toggleBox.setAlignment(Pos.CENTER_LEFT);

        TextField tfCity = new TextField();
        tfCity.setPromptText("Enter city name...");
        tfCity.getStyleClass().add("offer-field-input");
        if (!isRemote[0] && existing != null) tfCity.setText(existing.getLocation());
        tfCity.setVisible(!isRemote[0]);
        tfCity.setManaged(!isRemote[0]);

        Label errLocation = new Label();
        errLocation.getStyleClass().add("offer-field-error");
        errLocation.setVisible(false); errLocation.setManaged(false);

        Runnable updateToggle = () -> {
            if (isRemote[0]) {
                btnRemote.getStyleClass().setAll("offer-toggle", "offer-toggle-left", "offer-toggle-active");
                btnOnsite.getStyleClass().setAll("offer-toggle", "offer-toggle-right");
                tfCity.setVisible(false); tfCity.setManaged(false);
            } else {
                btnRemote.getStyleClass().setAll("offer-toggle", "offer-toggle-left");
                btnOnsite.getStyleClass().setAll("offer-toggle", "offer-toggle-right", "offer-toggle-active");
                tfCity.setVisible(true); tfCity.setManaged(true);
                Platform.runLater(tfCity::requestFocus);
            }
        };
        updateToggle.run();
        btnRemote.setOnAction(e -> { isRemote[0] = true; updateToggle.run(); });
        btnOnsite.setOnAction(e -> { isRemote[0] = false; updateToggle.run(); });
        locationSec.getChildren().addAll(lblLocation, toggleBox, tfCity, errLocation);

        // ── Amount + Currency ──
        HBox amountRow = new HBox(12);
        VBox amountBox = new VBox(4);
        HBox.setHgrow(amountBox, Priority.ALWAYS);
        Label lblAmount = new Label("Amount");
        lblAmount.getStyleClass().add("offer-field-label");
        TextField tfAmount = new TextField(existing != null ? String.valueOf(existing.getAmount()) : "0");
        tfAmount.setPromptText("0");
        tfAmount.getStyleClass().add("offer-field-input");
        Label errAmount = new Label();
        errAmount.getStyleClass().add("offer-field-error");
        errAmount.setVisible(false); errAmount.setManaged(false);
        amountBox.getChildren().addAll(lblAmount, tfAmount, errAmount);

        VBox currencyBox = new VBox(4);
        currencyBox.setPrefWidth(100);
        Label lblCurrency = new Label("Currency");
        lblCurrency.getStyleClass().add("offer-field-label");
        ComboBox<String> cbCurrency = new ComboBox<>(FXCollections.observableArrayList("USD", "EUR", "GBP", "TND"));
        cbCurrency.setValue(existing != null ? existing.getCurrency() : "USD");
        cbCurrency.setMaxWidth(Double.MAX_VALUE);
        currencyBox.getChildren().addAll(lblCurrency, cbCurrency);
        amountRow.getChildren().addAll(amountBox, currencyBox);

        // ── Date Range Calendar ──
        VBox dateSec = new VBox(6);
        Label lblDates = new Label("Duration *");
        lblDates.getStyleClass().add("offer-field-label");
        LocalDate[] startDate = {
                existing != null && existing.getStartDate() != null ? existing.getStartDate().toLocalDate() : null
        };
        LocalDate[] endDate = {
                existing != null && existing.getEndDate() != null ? existing.getEndDate().toLocalDate() : null
        };
        VBox calendarWidget = buildOfferDateRangeCalendar(startDate, endDate);
        Label errDates = new Label();
        errDates.getStyleClass().add("offer-field-error");
        errDates.setVisible(false); errDates.setManaged(false);
        dateSec.getChildren().addAll(lblDates, calendarWidget, errDates);

        // ── Assemble form ──
        Separator sep1 = new Separator();
        Separator sep2 = new Separator();
        Separator sep3 = new Separator();
        form.getChildren().addAll(
                titleSec, descSec, sep1,
                typeSkillsRow, locationSec, sep2,
                amountRow, sep3,
                dateSec
        );

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(540);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        dialog.getDialogPane().setContent(scroll);

        // ── Validation (prevents close on invalid input) ──
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            boolean valid = true;
            for (Label err : new Label[]{ errTitle, errDesc, errSkills, errLocation, errAmount, errDates }) {
                err.setVisible(false); err.setManaged(false);
            }
            tfTitle.getStyleClass().remove("offer-field-invalid");
            taDesc.getStyleClass().remove("offer-field-invalid");
            tfSkills.getStyleClass().remove("offer-field-invalid");
            tfCity.getStyleClass().remove("offer-field-invalid");
            tfAmount.getStyleClass().remove("offer-field-invalid");

            if (tfTitle.getText().trim().length() < 3) {
                errTitle.setText("\u26A0 Title must be at least 3 characters");
                errTitle.setVisible(true); errTitle.setManaged(true);
                tfTitle.getStyleClass().add("offer-field-invalid");
                valid = false;
            }
            if (taDesc.getText().trim().isEmpty()) {
                errDesc.setText("\u26A0 Description is required");
                errDesc.setVisible(true); errDesc.setManaged(true);
                taDesc.getStyleClass().add("offer-field-invalid");
                valid = false;
            }
            if (tfSkills.getText().trim().isEmpty()) {
                errSkills.setText("\u26A0 At least one skill is required");
                errSkills.setVisible(true); errSkills.setManaged(true);
                tfSkills.getStyleClass().add("offer-field-invalid");
                valid = false;
            }
            if (!isRemote[0] && tfCity.getText().trim().isEmpty()) {
                errLocation.setText("\u26A0 City name is required for on-site offers");
                errLocation.setVisible(true); errLocation.setManaged(true);
                tfCity.getStyleClass().add("offer-field-invalid");
                valid = false;
            }
            try {
                double amt = Double.parseDouble(tfAmount.getText().trim());
                if (amt < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                errAmount.setText("\u26A0 Enter a valid positive number");
                errAmount.setVisible(true); errAmount.setManaged(true);
                tfAmount.getStyleClass().add("offer-field-invalid");
                valid = false;
            }
            if (startDate[0] == null || endDate[0] == null) {
                errDates.setText("\u26A0 Select both start and end dates on the calendar");
                errDates.setVisible(true); errDates.setManaged(true);
                valid = false;
            } else if (endDate[0].isBefore(startDate[0])) {
                errDates.setText("\u26A0 End date must be on or after start date");
                errDates.setVisible(true); errDates.setManaged(true);
                valid = false;
            }
            if (!valid) {
                event.consume();
                SoundManager.getInstance().play(SoundManager.ERROR);
            }
        });

        // ── Result ──
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Offer o = existing != null ? existing : new Offer();
                o.setTitle(tfTitle.getText().trim());
                o.setDescription(taDesc.getText().trim());
                o.setOfferType(cbType.getValue());
                o.setRequiredSkills(tfSkills.getText().trim());
                o.setLocation(isRemote[0] ? "Remote" : tfCity.getText().trim());
                o.setAmount(Double.parseDouble(tfAmount.getText().trim()));
                o.setCurrency(cbCurrency.getValue());
                o.setOwnerId(currentUser.getId());
                if (existing == null) o.setStatus("DRAFT");
                o.setStartDate(startDate[0] != null ? Date.valueOf(startDate[0]) : null);
                o.setEndDate(endDate[0] != null ? Date.valueOf(endDate[0]) : null);
                return o;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(o -> {
            try {
                if (existing == null) {
                    serviceOffer.ajouter(o);
                    showInfo("Offer created!");
                } else {
                    serviceOffer.modifier(o);
                    showInfo("Offer updated!");
                }
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                refreshMyOffers();
            } catch (SQLException e) {
                showError("Save failed: " + e.getMessage());
            }
        });
    }

    /** MUI-inspired inline date range calendar for offer forms. */
    private VBox buildOfferDateRangeCalendar(LocalDate[] startDate, LocalDate[] endDate) {
        VBox cal = new VBox(8);
        cal.getStyleClass().add("offer-calendar");
        cal.setPadding(new Insets(14));

        LocalDate[] viewMonth = {
                startDate[0] != null ? startDate[0].withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1)
        };
        boolean[] selectingEnd = { startDate[0] != null && endDate[0] == null };

        // ── Selected dates display ──
        HBox selectedDates = new HBox(16);
        selectedDates.setAlignment(Pos.CENTER);

        VBox startBox = new VBox(2);
        startBox.setAlignment(Pos.CENTER);
        startBox.getStyleClass().add("offer-cal-date-box");
        Label startLabel = new Label("START");
        startLabel.getStyleClass().add("offer-cal-section-label");
        Label startValue = new Label(startDate[0] != null
                ? startDate[0].format(DateTimeFormatter.ofPattern("MMM d, yyyy")) : "Select...");
        startValue.getStyleClass().add("offer-cal-date-value");
        if (startDate[0] != null) startValue.getStyleClass().add("offer-cal-date-active");
        startBox.getChildren().addAll(startLabel, startValue);

        Label arrowLbl = new Label("\u2192");
        arrowLbl.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 20; -fx-padding: 8 0 0 0;");

        VBox endBox = new VBox(2);
        endBox.setAlignment(Pos.CENTER);
        endBox.getStyleClass().add("offer-cal-date-box");
        Label endLabel = new Label("END");
        endLabel.getStyleClass().add("offer-cal-section-label");
        Label endValue = new Label(endDate[0] != null
                ? endDate[0].format(DateTimeFormatter.ofPattern("MMM d, yyyy")) : "Select...");
        endValue.getStyleClass().add("offer-cal-date-value");
        if (endDate[0] != null) endValue.getStyleClass().add("offer-cal-date-active");
        endBox.getChildren().addAll(endLabel, endValue);

        selectedDates.getChildren().addAll(startBox, arrowLbl, endBox);

        // ── Month navigation ──
        HBox monthNav = new HBox();
        monthNav.setAlignment(Pos.CENTER);
        monthNav.setPadding(new Insets(4, 0, 4, 0));
        Button prevBtn = new Button("\u25C2");
        prevBtn.getStyleClass().setAll("offer-cal-nav");
        Label monthLabel = new Label();
        monthLabel.getStyleClass().add("offer-cal-month-label");
        HBox.setHgrow(monthLabel, Priority.ALWAYS);
        monthLabel.setMaxWidth(Double.MAX_VALUE);
        monthLabel.setAlignment(Pos.CENTER);
        Button nextBtn = new Button("\u25B8");
        nextBtn.getStyleClass().setAll("offer-cal-nav");
        monthNav.getChildren().addAll(prevBtn, monthLabel, nextBtn);

        // ── Day-of-week headers ──
        GridPane dowRow = new GridPane();
        dowRow.setAlignment(Pos.CENTER);
        double cellW = 42;
        String[] dows = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        for (int i = 0; i < 7; i++) {
            Label d = new Label(dows[i]);
            d.getStyleClass().add("offer-cal-dow");
            d.setPrefWidth(cellW);
            d.setAlignment(Pos.CENTER);
            dowRow.add(d, i, 0);
        }

        // ── Day grid ──
        GridPane dayGrid = new GridPane();
        dayGrid.setAlignment(Pos.CENTER);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");

        Runnable[] renderRef = new Runnable[1];
        renderRef[0] = () -> {
            dayGrid.getChildren().clear();
            LocalDate first = viewMonth[0];
            monthLabel.setText(first.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            int startDow = first.getDayOfWeek().getValue() % 7; // Sun=0
            int daysInMonth = first.lengthOfMonth();
            LocalDate today = LocalDate.now();

            int row = 0, col = startDow;
            for (int d = 1; d <= daysInMonth; d++) {
                LocalDate date = first.withDayOfMonth(d);
                Button dayBtn = new Button(String.valueOf(d));
                dayBtn.setPrefSize(cellW, 34);
                dayBtn.setMinSize(cellW, 34);
                dayBtn.getStyleClass().setAll("offer-cal-day");

                boolean isStart = date.equals(startDate[0]);
                boolean isEnd = date.equals(endDate[0]);
                boolean inRange = startDate[0] != null && endDate[0] != null
                        && !date.isBefore(startDate[0]) && !date.isAfter(endDate[0]);
                boolean isToday = date.equals(today);

                if (isStart || isEnd) {
                    dayBtn.getStyleClass().add("offer-cal-day-selected");
                } else if (inRange) {
                    dayBtn.getStyleClass().add("offer-cal-day-range");
                }
                if (isToday) {
                    dayBtn.getStyleClass().add("offer-cal-day-today");
                }

                final LocalDate clickDate = date;
                dayBtn.setOnAction(ev -> {
                    if (!selectingEnd[0] || startDate[0] == null || clickDate.isBefore(startDate[0])) {
                        startDate[0] = clickDate;
                        endDate[0] = null;
                        selectingEnd[0] = true;
                    } else {
                        endDate[0] = clickDate;
                        selectingEnd[0] = false;
                    }
                    startValue.setText(startDate[0] != null ? startDate[0].format(fmt) : "Select...");
                    startValue.getStyleClass().remove("offer-cal-date-active");
                    if (startDate[0] != null) startValue.getStyleClass().add("offer-cal-date-active");
                    endValue.setText(endDate[0] != null ? endDate[0].format(fmt) : "Select...");
                    endValue.getStyleClass().remove("offer-cal-date-active");
                    if (endDate[0] != null) endValue.getStyleClass().add("offer-cal-date-active");
                    renderRef[0].run();
                });

                dayGrid.add(dayBtn, col, row);
                col++;
                if (col > 6) { col = 0; row++; }
            }
        };

        prevBtn.setOnAction(e -> { viewMonth[0] = viewMonth[0].minusMonths(1); renderRef[0].run(); });
        nextBtn.setOnAction(e -> { viewMonth[0] = viewMonth[0].plusMonths(1); renderRef[0].run(); });
        renderRef[0].run();

        Separator sep = new Separator();
        cal.getChildren().addAll(selectedDates, sep, monthNav, dowRow, dayGrid);
        return cal;
    }

    private void toggleOfferStatus(Offer o) {
        try {
            if (Offer.STATUS_DRAFT.equals(o.getStatus())) {
                o.setStatus(Offer.STATUS_PUBLISHED);
                serviceOffer.modifier(o);
                showInfo("Offer published!");
            } else if (Offer.STATUS_PUBLISHED.equals(o.getStatus())) {
                o.setStatus(Offer.STATUS_COMPLETED);
                serviceOffer.modifier(o);
                showInfo("Offer closed.");
            }
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            refreshMyOffers();
        } catch (SQLException e) {
            showError("Status change failed: " + e.getMessage());
        }
    }

    private void deleteOffer(Offer o) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete offer '" + o.getTitle() + "'? This will also delete all applications and contracts.", ButtonType.YES, ButtonType.NO);
        DialogHelper.theme(confirm);
        styleDarkDialog(confirm.getDialogPane());
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    serviceOffer.supprimer(o.getId());
                    SoundManager.getInstance().play(SoundManager.ERROR);
                    refreshMyOffers();
                } catch (SQLException e) {
                    showError("Delete failed: " + e.getMessage());
                }
            }
        });
    }

    // ================================================================
    // TAB 3: APPLICATIONS
    // ================================================================

    private void refreshApplications() {
        applicationsList.getChildren().clear();

        // ── Auto-cleanup: remove interviews whose date has already passed ──
        AppThreadPool.io(() -> {
            try {
                List<Interview> allInterviews = serviceInterview.recuperer();
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                for (Interview iv : allInterviews) {
                    if (iv.getDateTime() != null && iv.getDateTime().toLocalDateTime().isBefore(now)
                            && "SCHEDULED".equalsIgnoreCase(iv.getStatus())) {
                        serviceInterview.supprimer(iv.getId());
                        System.out.println("[OC] Auto-removed past interview #" + iv.getId());
                    }
                }
            } catch (Exception ex) {
                System.err.println("Past interview cleanup failed: " + ex.getMessage());
            }
        });

        try {
            List<JobApplication> apps;
            if (isOwnerOrAdmin) {
                // Show all applications for my offers
                apps = serviceApp.recuperer();
            } else {
                // Show only my applications
                apps = serviceApp.getByUser(currentUser.getId());
            }

            String statusFilter = filterAppStatus.getValue();
            if (!"All".equals(statusFilter)) {
                apps = apps.stream().filter(a -> statusFilter.equals(a.getStatus())).collect(Collectors.toList());
            }

            if (apps.isEmpty()) {
                Label empty = new Label(isOwnerOrAdmin ? "No applications received yet." : "You haven't applied to any offers yet.");
                empty.getStyleClass().add("oc-empty-label");
                applicationsList.getChildren().add(empty);
                return;
            }

            for (JobApplication a : apps) {
                applicationsList.getChildren().add(createApplicationRow(a));
            }
        } catch (SQLException e) {
            showError("Failed to load applications: " + e.getMessage());
        }
    }

    @FXML private void onAppStatusFilterChanged() { refreshApplications(); }

    private HBox createApplicationRow(JobApplication a) {
        HBox row = new HBox(12);
        row.getStyleClass().add("oc-list-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));

        Offer offer = offerMap.get(a.getOfferId());
        String offerTitle = offer != null ? offer.getTitle() : "Offer #" + a.getOfferId();

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(offerTitle);
        title.getStyleClass().add("oc-row-title");
        Label meta = new Label("Applicant: " + getUserName(a.getApplicantId()) +
                (a.getAppliedAt() != null ? " • Applied: " + a.getAppliedAt().toLocalDateTime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) : ""));
        meta.getStyleClass().add("oc-row-meta");
        info.getChildren().addAll(title, meta);

        // AI score badge
        VBox scoreBox = new VBox(2);
        scoreBox.setAlignment(Pos.CENTER);
        if (a.getAiScore() != null) {
            Label scoreLbl = new Label(a.getAiScore() + "%");
            scoreLbl.getStyleClass().addAll("oc-score-badge",
                    a.getAiScore() >= 70 ? "oc-score-high" : a.getAiScore() >= 40 ? "oc-score-medium" : "oc-score-low");
            Label scoreLabel = new Label("AI Score");
            scoreLabel.getStyleClass().add("oc-score-label");
            scoreBox.getChildren().addAll(scoreLbl, scoreLabel);
        }

        Label status = new Label(a.getStatus());
        status.getStyleClass().addAll("oc-status-badge", "oc-status-" + a.getStatus().toLowerCase());

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER);

        if (isOwnerOrAdmin) {
            Button btnAiScore = new Button("🎯 AI Score");
            btnAiScore.getStyleClass().add("oc-btn-secondary");
            btnAiScore.setOnAction(e -> runAiScoring(a));

            // === NEW PIPELINE BUTTONS ===
            if ("PENDING".equals(a.getStatus()) || "REVIEWED".equals(a.getStatus())) {
                // Step 1: HR can Shortlist or Reject
                Button btnShortlist = new Button("⭐ Shortlist");
                btnShortlist.getStyleClass().add("oc-btn-primary");
                btnShortlist.setOnAction(e -> updateApplicationStatus(a, "SHORTLISTED"));

                Button btnReject = new Button("✗ Reject");
                btnReject.getStyleClass().add("oc-btn-danger");
                btnReject.setOnAction(e -> updateApplicationStatus(a, "REJECTED"));

                actions.getChildren().addAll(btnAiScore, btnShortlist, btnReject);
            } else if ("SHORTLISTED".equals(a.getStatus())) {
                // Step 2: HR schedules an interview for this applicant
                Button btnInterview = new Button("📅 Schedule Interview");
                btnInterview.getStyleClass().add("oc-btn-primary");
                btnInterview.setOnAction(e -> showScheduleInterviewDialog(a));

                // Check if interview already exists for this application
                try {
                    List<Interview> appInterviews = serviceInterview.getByApplication(a.getId());
                    if (!appInterviews.isEmpty()) {
                        Interview latest = appInterviews.get(0);
                        Label interviewStatus = new Label("Interview: " + latest.getStatus());
                        interviewStatus.getStyleClass().addAll("oc-status-badge",
                                "oc-status-" + latest.getStatus().toLowerCase());
                        actions.getChildren().add(interviewStatus);
                    } else {
                        actions.getChildren().add(btnInterview);
                    }
                } catch (SQLException ex) {
                    actions.getChildren().add(btnInterview);
                }

                Button btnReject = new Button("✗ Reject");
                btnReject.getStyleClass().add("oc-btn-danger");
                btnReject.setOnAction(e -> updateApplicationStatus(a, "REJECTED"));
                actions.getChildren().addAll(btnAiScore, btnReject);
            } else if ("ACCEPTED".equals(a.getStatus())) {
                // Already accepted via interview — show interview status
                Label done = new Label("✓ Accepted — Contract Created");
                done.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 11;");
                actions.getChildren().add(done);
            }
        } else {
            // Applicant can withdraw if pending/shortlisted
            if ("PENDING".equals(a.getStatus()) || "SHORTLISTED".equals(a.getStatus())) {
                Button btnWithdraw = new Button("Withdraw");
                btnWithdraw.getStyleClass().add("oc-btn-danger");
                btnWithdraw.setOnAction(e -> updateApplicationStatus(a, "WITHDRAWN"));
                actions.getChildren().add(btnWithdraw);
            }
            // Show interview info to applicant
            if ("SHORTLISTED".equals(a.getStatus())) {
                try {
                    List<Interview> appInterviews = serviceInterview.getByApplication(a.getId());
                    if (!appInterviews.isEmpty()) {
                        Interview latest = appInterviews.get(0);
                        String dtStr = latest.getDateTime() != null
                                ? latest.getDateTime().toLocalDateTime().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
                                : "TBD";
                        Label interviewInfo = new Label("🗓 Interview: " + dtStr + " (" + latest.getStatus() + ")");
                        interviewInfo.setStyle("-fx-text-fill: #8A8AFF; -fx-font-size: 11;");
                        actions.getChildren().add(interviewInfo);
                    } else {
                        Label waitLabel = new Label("⏳ Awaiting interview scheduling");
                        waitLabel.setStyle("-fx-text-fill: #facc15; -fx-font-size: 11;");
                        actions.getChildren().add(waitLabel);
                    }
                } catch (SQLException ex) { /* ignore */ }
            }
        }

        Button btnViewCover = new Button("View");
        btnViewCover.getStyleClass().add("oc-btn-secondary");
        btnViewCover.setOnAction(e -> {
            Dialog<ButtonType> detailDialog = new Dialog<>();
            DialogHelper.theme(detailDialog);
            detailDialog.setTitle("Application Details");
            detailDialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
            detailDialog.getDialogPane().setMinWidth(520);

            VBox detailContent = new VBox(12);
            detailContent.setPadding(new Insets(16));

            Label detailHeader = new Label(offerTitle + " — " + getUserName(a.getApplicantId()));
            detailHeader.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: #F0EDEE;");
            detailHeader.setWrapText(true);

            Label coverHeader = new Label("\ud83d\udcdd Cover Letter");
            coverHeader.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #90DDF0;");
            Label coverBody = new Label(a.getCoverLetter() != null ? a.getCoverLetter() : "N/A");
            coverBody.setWrapText(true);
            coverBody.setStyle("-fx-text-fill: #C0C0D8; -fx-font-size: 12; -fx-padding: 8 12; -fx-background-color: #14131A; -fx-background-radius: 8;");

            detailContent.getChildren().addAll(detailHeader, new Separator(), coverHeader, coverBody);

            if (a.getAiFeedback() != null && !a.getAiFeedback().isBlank()) {
                Label aiHeader = new Label("\ud83e\udd16 AI Assessment");
                aiHeader.setStyle("-fx-font-size: 13; -fx-font-weight: bold; -fx-text-fill: #90DDF0; -fx-padding: 8 0 0 0;");

                String feedbackDisplay = a.getAiFeedback();
                try {
                    String cleaned = feedbackDisplay.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
                    JsonObject fbJson = JsonParser.parseString(cleaned).getAsJsonObject();
                    feedbackDisplay = formatAiFeedback(fbJson);
                } catch (Exception ignored) { }

                Label aiBody = new Label(feedbackDisplay);
                aiBody.setWrapText(true);
                aiBody.setStyle("-fx-text-fill: #C0C0D8; -fx-font-size: 12; -fx-padding: 8 12; -fx-background-color: #14131A; -fx-background-radius: 8;");

                if (a.getAiScore() != null) {
                    String color = a.getAiScore() >= 70 ? "#22C55E" : a.getAiScore() >= 40 ? "#F59E0B" : "#EF4444";
                    Label scoreBadge = new Label("AI Fit Score: " + a.getAiScore() + "%");
                    scoreBadge.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: " + color + "; -fx-padding: 4 0 0 0;");
                    detailContent.getChildren().addAll(aiHeader, scoreBadge, aiBody);
                } else {
                    detailContent.getChildren().addAll(aiHeader, aiBody);
                }
            }

            ScrollPane sp = new ScrollPane(detailContent);
            sp.setFitToWidth(true);
            sp.setPrefViewportHeight(400);
            sp.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            detailDialog.getDialogPane().setContent(sp);
            detailDialog.showAndWait();
        });
        actions.getChildren().add(btnViewCover);

        row.getChildren().addAll(info, scoreBox, status, actions);
        return row;
    }

    private void showApplyDialog(Offer offer) {
        // ── Guard: check if offer has expired ──
        if (offer.getEndDate() != null && offer.getEndDate().toLocalDate().isBefore(LocalDate.now())) {
            showError("This offer expired on " + offer.getEndDate().toLocalDate()
                    .format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) + ". You can no longer apply.");
            return;
        }

        // ── Guard: check for duplicate application ──
        try {
            List<JobApplication> myApps = serviceApp.getByUser(currentUser.getId());
            boolean alreadyApplied = myApps.stream()
                    .anyMatch(a -> a.getOfferId() == offer.getId()
                            && !"WITHDRAWN".equals(a.getStatus())
                            && !"REJECTED".equals(a.getStatus()));
            if (alreadyApplied) {
                showError("You have already applied to this offer. You cannot apply again.");
                return;
            }
        } catch (SQLException e) {
            // Non-blocking — allow apply if check fails
            System.err.println("Duplicate check failed: " + e.getMessage());
        }

        Dialog<JobApplication> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Apply to: " + offer.getTitle());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(450);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));

        // Show required skills prominently
        if (offer.getRequiredSkills() != null && !offer.getRequiredSkills().isBlank()) {
            Label skillsHeader = new Label("🔧 Required Skills:");
            skillsHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #facc15; -fx-font-size: 13;");
            Label skillsVal = new Label(offer.getRequiredSkills());
            skillsVal.setWrapText(true);
            skillsVal.setStyle("-fx-text-fill: #ccc; -fx-font-size: 12; -fx-padding: 4 0 8 0;");
            content.getChildren().addAll(skillsHeader, skillsVal);
        }

        Label lbl = new Label("Write your cover letter / motivation:");
        TextArea taCover = new TextArea();
        taCover.setPromptText("Address the required skills above and explain why you're a great fit.");
        taCover.setPrefRowCount(8);
        content.getChildren().addAll(lbl, taCover);
        dialog.getDialogPane().setContent(content);
        styleDarkDialog(dialog.getDialogPane());

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                JobApplication a = new JobApplication(
                        offer.getId(), currentUser.getId(), taCover.getText().trim(), "PENDING"
                );
                return a;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(a -> {
            try {
                serviceApp.ajouter(a);
                showInfo("Application submitted! Your application is now PENDING review.");
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);

                // Auto-trigger AI scoring in background
                AppThreadPool.io(() -> {
                    try {
                        String result = zaiService.scoreApplicant(
                                offer.getTitle(),
                                offer.getRequiredSkills() != null ? offer.getRequiredSkills() : "",
                                a.getCoverLetter() != null ? a.getCoverLetter() : "",
                                getUserName(a.getApplicantId())
                        );
                        // Parse score from AI result
                        try {
                            String cleaned = result.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(cleaned).getAsJsonObject();
                            if (json.has("score")) a.setAiScore(json.get("score").getAsInt());
                            a.setAiFeedback(formatAiFeedback(json));
                        } catch (Exception parseEx) {
                            a.setAiFeedback(result);
                        }

                        // ── Auto-reject if AI score < 60% ──
                        if (a.getAiScore() != null && a.getAiScore() < 60) {
                            a.setStatus(JobApplication.STATUS_REJECTED);
                            serviceApp.modifier(a);
                            // Notify applicant about auto-rejection
                            try {
                                String offerTitle = offer.getTitle() != null ? offer.getTitle() : "Offer #" + offer.getId();
                                serviceNotification.notifyInterview(a.getApplicantId(), "REJECTED",
                                        offerTitle + ": Your application was automatically declined (AI fit score: "
                                                + a.getAiScore() + "%). A minimum score of 60% is required.", 0);
                            } catch (Exception notifEx) {
                                System.err.println("Auto-reject notification failed: " + notifEx.getMessage());
                            }
                            Platform.runLater(() -> refreshApplications());
                        } else {
                            serviceApp.modifier(a);
                            Platform.runLater(() -> refreshApplications());
                        }
                    } catch (Exception ex) {
                        System.err.println("Auto AI scoring failed: " + ex.getMessage());
                    }
                });
            } catch (SQLException e) {
                showError("Application failed: " + e.getMessage());
            }
        });
    }

    private void updateApplicationStatus(JobApplication a, String newStatus) {
        try {
            String oldStatus = a.getStatus();
            a.setStatus(newStatus);
            serviceApp.modifier(a);
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);

            // Notify applicant about status change
            String statusMsg = switch (newStatus) {
                case "SHORTLISTED" -> "Congratulations! Your application has been shortlisted. An interview will be scheduled soon.";
                case "REJECTED" -> "Your application has been rejected.";
                case "WITHDRAWN" -> "Your application has been withdrawn.";
                case "ACCEPTED" -> "Your application has been accepted! A contract is being prepared.";
                default -> "Your application status changed to " + newStatus;
            };
            try {
                Offer offer = offerMap.get(a.getOfferId());
                String offerTitle = offer != null ? offer.getTitle() : "Offer #" + a.getOfferId();
                serviceNotification.notifyInterview(a.getApplicantId(), newStatus,
                        offerTitle + ": " + statusMsg, 0);
            } catch (Exception notifEx) {
                System.err.println("Notification failed: " + notifEx.getMessage());
            }

            // If ACCEPTED (only triggered from InterviewController pipeline), create contract
            if ("ACCEPTED".equals(newStatus)) {
                createContractForApplication(a);
            }

            refreshApplications();
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Status update failed: " + e.getMessage());
        }
    }

    /**
     * Creates a DRAFT contract for an accepted application.
     * Called when an interview is passed and the application is auto-accepted.
     */
    void createContractForApplication(JobApplication a) {
        Offer offer = offerMap.get(a.getOfferId());
        if (offer == null) {
            // Try to reload offers
            loadOfferMap();
            offer = offerMap.get(a.getOfferId());
        }
        if (offer == null) {
            showError("Offer not found for this application (ID: " + a.getOfferId() + ").");
            return;
        }

        final Offer finalOffer = offer;
        try {
            // Generate AI contract terms
            String terms = null;
            try {
                terms = zaiService.generateContract(
                        finalOffer.getTitle(),
                        finalOffer.getDescription() != null ? finalOffer.getDescription() : finalOffer.getTitle(),
                        finalOffer.getAmount(),
                        getUserName(a.getApplicantId()),
                        java.time.LocalDate.now().toString(),
                        java.time.LocalDate.now().plusMonths(6).toString());
            } catch (Exception aiEx) {
                System.err.println("AI contract gen failed, using template: " + aiEx.getMessage());
                terms = "Employment contract for " + finalOffer.getTitle()
                        + "\nParties: " + getUserName(finalOffer.getOwnerId()) + " (Employer) and "
                        + getUserName(a.getApplicantId()) + " (Employee)"
                        + "\nCompensation: " + finalOffer.getCurrency() + " " + finalOffer.getAmount()
                        + "\n\n[Terms to be finalized]";
            }

            Contract c = new Contract(
                    finalOffer.getId(), a.getApplicantId(), finalOffer.getOwnerId(),
                    terms, finalOffer.getAmount(), finalOffer.getCurrency(),
                    "DRAFT", finalOffer.getStartDate(), finalOffer.getEndDate()
            );
            // Generate blockchain hash
            String hash = BlockchainVerifier.generateHash(0, "Contract from hiring pipeline", finalOffer.getAmount());
            c.setBlockchainHash(hash);
            serviceContract.ajouter(c);
            System.out.println("[OfferContract] Contract #" + c.getId() + " created for application #" + a.getId());

            showInfo("✅ Application accepted!\n\nContract #" + c.getId() + " created (DRAFT).\n" +
                    "Blockchain hash: " + BlockchainVerifier.shortHash(hash) + "\n\n" +
                    "PDF generation & email sending in progress...");

            String applicantName = getUserName(a.getApplicantId());
            String ownerName = getUserName(finalOffer.getOwnerId());
            final Contract finalContract = c;

            // Generate PDF + send email + notify in background
            AppThreadPool.io(() -> {
                try {
                    File pdf = ContractPdfGenerator.generatePdf(finalContract, finalOffer.getTitle(), ownerName, applicantName);
                    User applicant = serviceUser.getById(a.getApplicantId());
                    if (applicant != null && applicant.getEmail() != null) {
                        EmailService.sendContractEmail(
                                applicant.getEmail(), applicant.getFirstName(),
                                ownerName, finalOffer.getTitle(),
                                finalOffer.getCurrency(), finalOffer.getAmount(),
                                hash, pdf
                        );
                        System.out.println("[OfferContract] ✅ Contract email sent to " + applicant.getEmail());
                    } else {
                        System.err.println("[OfferContract] ⚠ Cannot send contract email: applicant or email is null (userId=" + a.getApplicantId() + ")");
                        Platform.runLater(() -> showError(
                                "Contract #" + finalContract.getId() + " was created, but the applicant has no email address.\n"
                                + "The contract is available in the Contracts tab."));
                    }
                    serviceNotification.notifyContractReady(
                            a.getApplicantId(), applicantName, finalOffer.getTitle(), finalContract.getId());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Platform.runLater(() -> showError(
                            "Contract was created but email/PDF failed:\n" + ex.getMessage()
                            + "\n\nThe contract is still available in the Contracts tab."));
                }
            });
        } catch (Exception contractEx) {
            contractEx.printStackTrace();
            a.setStatus("SHORTLISTED");
            try { serviceApp.modifier(a); } catch (Exception ignored) {}
            showError("Failed to create contract: " + contractEx.getMessage());
            refreshApplications();
        }
    }

    /**
     * Shows a dialog to schedule an interview for a shortlisted applicant.
     * The interview is linked to the application and offer.
     */
    private void showScheduleInterviewDialog(JobApplication app) {
        Offer offer = offerMap.get(app.getOfferId());
        String offerTitle = offer != null ? offer.getTitle() : "Offer #" + app.getOfferId();
        String candidateName = getUserName(app.getApplicantId());

        Dialog<Interview> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Schedule Interview");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(450);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        Label header = new Label("📅 Schedule Interview for " + candidateName);
        header.setStyle("-fx-font-size: 16; -fx-font-weight: bold; -fx-text-fill: white;");

        Label offerInfo = new Label("Position: " + offerTitle);
        offerInfo.setStyle("-fx-text-fill: #8A8AFF; -fx-font-size: 13;");

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Interview date");
        datePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date.isBefore(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #555;");
                }
            }
        });
        datePicker.getStyleClass().add("pm-form-control");

        TextField timeField = new TextField();
        timeField.setPromptText("Time (HH:MM, e.g. 14:30)");
        timeField.getStyleClass().add("pm-form-control");

        TextField linkField = new TextField();
        linkField.setPromptText("Meeting link (e.g. https://meet.google.com/...)");
        linkField.getStyleClass().add("pm-form-control");

        content.getChildren().addAll(header, offerInfo, datePicker, timeField, linkField);
        dialog.getDialogPane().setContent(content);
        styleDarkDialog(dialog.getDialogPane());

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                if (datePicker.getValue() == null || timeField.getText().isBlank()) return null;
                try {
                    java.time.LocalTime time = java.time.LocalTime.parse(timeField.getText().trim());
                    java.time.LocalDateTime ldt = datePicker.getValue().atTime(time);
                    if (ldt.isBefore(java.time.LocalDateTime.now())) return null;

                    Interview interview = new Interview(
                            currentUser.getId(), app.getApplicantId(),
                            Timestamp.valueOf(ldt), linkField.getText().trim()
                    );
                    interview.setApplicationId(app.getId());
                    interview.setOfferId(app.getOfferId());
                    return interview;
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(interview -> {
            try {
                serviceInterview.ajouter(interview);
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);

                // Notify candidate
                String dateStr = interview.getDateTime().toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
                serviceNotification.notifyInterview(app.getApplicantId(), "Scheduled",
                        "Interview for " + offerTitle + " on " + dateStr + ".", interview.getId());

                showInfo("✅ Interview scheduled!\n\nCandidate: " + candidateName +
                        "\nDate: " + dateStr +
                        "\n\nThe application will be auto-accepted/rejected based on the interview result.");
                refreshApplications();
            } catch (SQLException e) {
                showError("Failed to schedule interview: " + e.getMessage());
            }
        });
    }

    private void runAiScoring(JobApplication a) {
        Offer offer = offerMap.get(a.getOfferId());
        if (offer == null) { showError("Offer not found."); return; }

        aiStatusLabel.setText("\ud83e\udd16 Scoring applicant...");
        AppThreadPool.io(() -> {
            String result = zaiService.scoreApplicant(
                    offer.getTitle(),
                    offer.getRequiredSkills() != null ? offer.getRequiredSkills() : "",
                    a.getCoverLetter() != null ? a.getCoverLetter() : "",
                    getUserName(a.getApplicantId())
            );

            Platform.runLater(() -> {
                aiStatusLabel.setText("");
                try {
                    String cleaned = result.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
                    JsonObject json = JsonParser.parseString(cleaned).getAsJsonObject();
                    int score = json.get("score").getAsInt();
                    a.setAiScore(score);
                    a.setAiFeedback(formatAiFeedback(json));

                    if (score < 60) {
                        a.setStatus("REJECTED");
                        serviceApp.modifier(a);
                        try {
                            String offerTitle = offer.getTitle() != null ? offer.getTitle() : "Offer #" + offer.getId();
                            serviceNotification.notifyInterview(a.getApplicantId(), "REJECTED",
                                    offerTitle + ": Application auto-rejected (AI fit score: " + score + "%). Minimum required: 60%.", 0);
                        } catch (Exception notifEx) {
                            System.err.println("Auto-reject notification failed: " + notifEx.getMessage());
                        }
                        showInfo("\u26a0\ufe0f AI Score: " + score + "/100 \u2014 Auto-rejected (below 60% threshold)");
                    } else {
                        a.setStatus("REVIEWED");
                        serviceApp.modifier(a);
                        showInfo("\u2705 AI Score: " + score + "/100 \u2014 Application reviewed!");
                    }
                    refreshApplications();
                } catch (Exception ex) {
                    a.setAiFeedback(result);
                    try { serviceApp.modifier(a); } catch (Exception ignored) {}
                    showInfo("\u2705 AI scoring complete.\n" + result.substring(0, Math.min(200, result.length())));
                }
            });
        });
    }

    /** Parse AI JSON feedback into readable, user-friendly text. */
    private String formatAiFeedback(JsonObject json) {
        StringBuilder sb = new StringBuilder();
        if (json.has("score")) {
            sb.append("Score: ").append(json.get("score").getAsInt()).append("/100\n\n");
        }
        if (json.has("strengths") && json.get("strengths").isJsonArray()) {
            sb.append("\u2705 Strengths:\n");
            for (var el : json.getAsJsonArray("strengths")) {
                sb.append("   \u2022 ").append(el.getAsString()).append("\n");
            }
            sb.append("\n");
        }
        if (json.has("gaps") && json.get("gaps").isJsonArray()) {
            sb.append("\u26a0\ufe0f Gaps:\n");
            for (var el : json.getAsJsonArray("gaps")) {
                sb.append("   \u2022 ").append(el.getAsString()).append("\n");
            }
            sb.append("\n");
        }
        if (json.has("feedback")) {
            sb.append("\ud83d\udcac Feedback:\n").append(json.get("feedback").getAsString()).append("\n");
        }
        if (json.has("recommendation")) {
            sb.append("\n\ud83d\udccb Recommendation: ").append(json.get("recommendation").getAsString()).append("\n");
        }
        return sb.toString().trim();
    }

    /** Score ALL unscored applications at once. */
    @FXML
    private void scoreAllApplications() {
        aiStatusLabel.setText("\ud83e\udd16 Scoring all applications...");
        AppThreadPool.io(() -> {
            try {
                List<JobApplication> allApps = serviceApp.recuperer();
                List<JobApplication> unscored = allApps.stream()
                        .filter(ap -> ap.getAiScore() == null
                                && !"REJECTED".equals(ap.getStatus())
                                && !"WITHDRAWN".equals(ap.getStatus()))
                        .collect(Collectors.toList());

                if (unscored.isEmpty()) {
                    Platform.runLater(() -> {
                        aiStatusLabel.setText("");
                        showInfo("All applications have already been scored!");
                    });
                    return;
                }

                int[] progress = {0};
                int total = unscored.size();

                for (JobApplication ap : unscored) {
                    Offer offer = offerMap.get(ap.getOfferId());
                    if (offer == null) continue;

                    progress[0]++;
                    int p = progress[0];
                    Platform.runLater(() -> aiStatusLabel.setText("\ud83e\udd16 Scoring " + p + "/" + total + "..."));

                    try {
                        String res = zaiService.scoreApplicant(
                                offer.getTitle(),
                                offer.getRequiredSkills() != null ? offer.getRequiredSkills() : "",
                                ap.getCoverLetter() != null ? ap.getCoverLetter() : "",
                                getUserName(ap.getApplicantId())
                        );
                        try {
                            String cleaned = res.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
                            JsonObject json = JsonParser.parseString(cleaned).getAsJsonObject();
                            int score = json.get("score").getAsInt();
                            ap.setAiScore(score);
                            ap.setAiFeedback(formatAiFeedback(json));
                            if (score < 60) {
                                ap.setStatus("REJECTED");
                                try {
                                    String offerTitle = offer.getTitle() != null ? offer.getTitle() : "Offer #" + offer.getId();
                                    serviceNotification.notifyInterview(ap.getApplicantId(), "REJECTED",
                                            offerTitle + ": Application auto-rejected (AI fit score: " + score + "%). Minimum required: 60%.", 0);
                                } catch (Exception ignored) {}
                            } else {
                                ap.setStatus("REVIEWED");
                            }
                            serviceApp.modifier(ap);
                        } catch (Exception parseEx) {
                            ap.setAiFeedback(res);
                            try { serviceApp.modifier(ap); } catch (Exception ignored) {}
                        }
                    } catch (Exception scoreEx) {
                        System.err.println("Score failed for app #" + ap.getId() + ": " + scoreEx.getMessage());
                    }
                }

                Platform.runLater(() -> {
                    aiStatusLabel.setText("");
                    showInfo("\u2705 Scored " + total + " application" + (total > 1 ? "s" : "") + "! Applications below 60% were auto-rejected.");
                    refreshApplications();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    aiStatusLabel.setText("");
                    showError("Batch scoring failed: " + ex.getMessage());
                });
            }
        });
    }

    // ================================================================
    // TAB 4: CONTRACTS TABLE
    // ================================================================

    private void setupContractsTable() {
        contractsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        contractsTable.setPlaceholder(new Label("No contracts yet") {{
            setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 13px;");
        }});

        colContractId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colContractStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        // NOTE: colContractAmount uses a custom cell factory that reads from Contract directly,
        // so no PropertyValueFactory (which returns Double, mismatching the String column type).

        // Custom cell factories for rich display
        colContractOffer.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setText(null); return; }
                Contract c = getTableRow().getItem();
                Offer o = offerMap.get(c.getOfferId());
                setText(o != null ? o.getTitle() : "Offer #" + c.getOfferId());
            }
        });
        colContractParty.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setText(null); return; }
                Contract c = getTableRow().getItem();
                setText(getUserName(isOwnerOrAdmin ? c.getApplicantId() : c.getOwnerId()));
            }
        });
        colContractAmount.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setText(null); return; }
                Contract c = getTableRow().getItem();
                setText(String.format("%s %.2f", c.getCurrency(), c.getAmount()));
            }
        });
        colContractRisk.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setText(null); setStyle(""); return; }
                Contract c = getTableRow().getItem();
                if (c.getRiskScore() != null) {
                    setText(c.getRiskScore() + "%");
                    if (c.getRiskScore() <= 30) setStyle("-fx-text-fill: #22c55e;");
                    else if (c.getRiskScore() <= 70) setStyle("-fx-text-fill: #f59e0b;");
                    else setStyle("-fx-text-fill: #ef4444;");
                } else {
                    setText("—");
                    setStyle("");
                }
            }
        });
        colContractDates.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setText(null); return; }
                Contract c = getTableRow().getItem();
                String s = (c.getStartDate() != null ? c.getStartDate().toString() : "?") + " → " + (c.getEndDate() != null ? c.getEndDate().toString() : "?");
                setText(s);
            }
        });
        colContractStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setText(null); setGraphic(null); return; }
                Contract c = getTableRow().getItem();
                Label lbl = new Label(c.getStatus());
                lbl.getStyleClass().addAll("oc-status-badge", "oc-status-" + c.getStatus().toLowerCase().replace("_", "-"));
                setGraphic(lbl);
                setText(null);
            }
        });
        colContractActions.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                Contract c = getTableRow().getItem();
                HBox box = new HBox(4);
                box.setAlignment(Pos.CENTER);

                Button btnPdf = new Button("PDF");
                btnPdf.getStyleClass().add("oc-btn-secondary");
                btnPdf.setOnAction(e -> exportContractPdf(c));

                Button btnRisk = new Button("⚠ Risk");
                btnRisk.getStyleClass().add("oc-btn-secondary");
                btnRisk.setOnAction(e -> runRiskAnalysis(c));

                Button btnChat = new Button("💬");
                btnChat.getStyleClass().add("oc-btn-secondary");
                btnChat.setOnAction(e -> {
                    selectedAiContract = c;
                    aiChatHistory.clear();
                    // Switch to AI tab
                    for (Node n : tabBar.getChildren()) {
                        if (n instanceof Button && ((Button) n).getText().contains("AI")) {
                            switchTab((Button) n, "AI Assistant");
                            addAiMessage("system", "Selected contract #" + c.getId() + ". Ask me anything about it!");
                            break;
                        }
                    }
                });

                box.getChildren().addAll(btnPdf, btnRisk, btnChat);

                // QR Verification button for HR/owner
                if (isOwnerOrAdmin && c.getBlockchainHash() != null && !c.getBlockchainHash().isEmpty()) {
                    Button btnVerify = new Button("🔍 Verify");
                    btnVerify.getStyleClass().add("oc-btn-secondary");
                    btnVerify.setOnAction(e -> showQrVerificationDialog(c));
                    box.getChildren().add(btnVerify);
                }

                String st = c.getStatus();

                // ── Owner/Admin actions ──
                if (isOwnerOrAdmin) {
                    if (Contract.STATUS_DRAFT.equals(st)) {
                        // Send to applicant for review
                        Button btnSend = new Button("📧 Send");
                        btnSend.getStyleClass().add("oc-btn-primary");
                        btnSend.setTooltip(new Tooltip("Send to applicant for review"));
                        btnSend.setOnAction(e -> {
                            try {
                                c.setStatus(Contract.STATUS_PENDING_REVIEW);
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getApplicantId(), "CONTRACT",
                                        "📋 Contract Ready for Review",
                                        "Contract for offer #" + c.getOfferId() + " is ready for your review.",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });
                        box.getChildren().add(0, btnSend);

                        // Also keep Activate shortcut for direct activation
                        Button btnActivate = new Button("Activate");
                        btnActivate.getStyleClass().add("oc-btn-secondary");
                        btnActivate.setOnAction(e -> {
                            try { c.setStatus(Contract.STATUS_ACTIVE); serviceContract.modifier(c); refreshContracts(); }
                            catch (SQLException ex) { showError(ex.getMessage()); }
                        });
                        box.getChildren().add(1, btnActivate);
                    }

                    if (Contract.STATUS_COUNTER_PROPOSED.equals(st)) {
                        // Applicant counter-offered → owner can accept or reject the counter
                        Button btnAcceptCounter = new Button("✅ Accept Counter");
                        btnAcceptCounter.getStyleClass().add("oc-btn-primary");
                        btnAcceptCounter.setOnAction(e -> {
                            try {
                                // Apply counter-proposed values
                                if (c.getCounterAmount() != null) c.setAmount(c.getCounterAmount());
                                if (c.getCounterTerms() != null && !c.getCounterTerms().isEmpty()) c.setTerms(c.getCounterTerms());
                                c.setStatus(Contract.STATUS_PENDING_SIGNATURE);
                                c.setCounterAmount(null);
                                c.setCounterTerms(null);
                                c.setNegotiationRound(c.getNegotiationRound() + 1);
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getApplicantId(), "CONTRACT",
                                        "✅ Counter-Offer Accepted",
                                        "Your counter-offer for contract #" + c.getId() + " was accepted. Ready for signature.",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });

                        Button btnRejectCounter = new Button("❌ Reject Counter");
                        btnRejectCounter.getStyleClass().add("oc-btn-secondary");
                        btnRejectCounter.setStyle("-fx-text-fill: #FF4444;");
                        btnRejectCounter.setOnAction(e -> {
                            try {
                                c.setStatus(Contract.STATUS_DRAFT);
                                c.setCounterAmount(null);
                                c.setCounterTerms(null);
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getApplicantId(), "CONTRACT",
                                        "❌ Counter-Offer Rejected",
                                        "Your counter-offer for contract #" + c.getId() + " was not accepted. Revised terms incoming.",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });
                        box.getChildren().add(0, btnAcceptCounter);
                        box.getChildren().add(1, btnRejectCounter);
                    }

                    // Owner can complete or terminate active contracts
                    if (Contract.STATUS_ACTIVE.equals(st)) {
                        Button btnComplete = new Button("✓ Complete");
                        btnComplete.getStyleClass().add("oc-btn-primary");
                        btnComplete.setOnAction(e -> {
                            try {
                                c.setStatus(Contract.STATUS_COMPLETED);
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getApplicantId(), "CONTRACT",
                                        "🎉 Contract Completed",
                                        "Contract #" + c.getId() + " has been marked as completed.",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });
                        Button btnTerminate = new Button("✕ Terminate");
                        btnTerminate.getStyleClass().add("oc-btn-secondary");
                        btnTerminate.setStyle("-fx-text-fill: #FF4444;");
                        btnTerminate.setOnAction(e -> {
                            try {
                                c.setStatus(Contract.STATUS_TERMINATED);
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getApplicantId(), "CONTRACT",
                                        "⚠ Contract Terminated",
                                        "Contract #" + c.getId() + " has been terminated.",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });
                        box.getChildren().add(0, btnComplete);
                        box.getChildren().add(1, btnTerminate);
                    }
                }

                // ── Sign button — available to BOTH owner and applicant when PENDING_SIGNATURE ──
                if (Contract.STATUS_PENDING_SIGNATURE.equals(st) && !c.isSigned()) {
                    Button btnSign = new Button("✍ Sign");
                    btnSign.getStyleClass().add("oc-btn-primary");
                    btnSign.setStyle("-fx-background-color: linear-gradient(to right, #07393C, #2C666E); -fx-text-fill: #F0EDEE; "
                            + "-fx-font-weight: bold; -fx-padding: 6 16; -fx-background-radius: 6; -fx-cursor: hand;");
                    btnSign.setTooltip(new Tooltip("Verify blockchain hash & draw your signature"));
                    btnSign.setOnAction(e -> showContractSignatureDialog(c));
                    box.getChildren().add(0, btnSign);
                }
                // Show signed badge if already signed
                if (c.isSigned()) {
                    Label signedBadge = new Label("✅ Signed");
                    signedBadge.setStyle("-fx-background-color: #166534; -fx-text-fill: #86efac; -fx-padding: 3 8; "
                            + "-fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;");
                    box.getChildren().add(0, signedBadge);
                }

                // ── Applicant actions ──
                if (!isOwnerOrAdmin) {
                    if (Contract.STATUS_PENDING_REVIEW.equals(st)) {
                        // Applicant can accept, counter-offer, or reject
                        Button btnAccept = new Button("✅ Accept");
                        btnAccept.getStyleClass().add("oc-btn-primary");
                        btnAccept.setOnAction(e -> {
                            try {
                                c.setStatus(Contract.STATUS_PENDING_SIGNATURE);
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getOwnerId(), "CONTRACT",
                                        "✅ Contract Accepted",
                                        getUserName(c.getApplicantId()) + " accepted contract #" + c.getId() + ".",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });

                        Button btnCounter = new Button("💬 Counter-Offer");
                        btnCounter.getStyleClass().add("oc-btn-secondary");
                        btnCounter.setOnAction(e -> showCounterOfferDialog(c));

                        Button btnReject = new Button("❌ Reject");
                        btnReject.getStyleClass().add("oc-btn-secondary");
                        btnReject.setStyle("-fx-text-fill: #FF4444;");
                        btnReject.setOnAction(e -> {
                            try {
                                c.setStatus(Contract.STATUS_TERMINATED);
                                c.setNegotiationNotes((c.getNegotiationNotes() != null ? c.getNegotiationNotes() + "\n" : "")
                                        + "Rejected by applicant.");
                                serviceContract.modifier(c);
                                serviceNotification.create(c.getOwnerId(), "CONTRACT",
                                        "❌ Contract Rejected",
                                        getUserName(c.getApplicantId()) + " rejected contract #" + c.getId() + ".",
                                        c.getId(), "CONTRACT");
                                refreshContracts();
                            } catch (SQLException ex) { showError(ex.getMessage()); }
                        });
                        box.getChildren().add(0, btnAccept);
                        box.getChildren().add(1, btnCounter);
                        box.getChildren().add(2, btnReject);
                    }
                }

                setGraphic(box);
                setText(null);
            }
        });
    }

    private void refreshContracts() {
        try {
            List<Contract> contracts;
            String role = currentUser != null ? currentUser.getRole() : "";
            if ("ADMIN".equals(role) || "HR_MANAGER".equals(role)) {
                // Admins & HR managers see ALL contracts
                contracts = serviceContract.recuperer();
            } else if (isOwnerOrAdmin) {
                contracts = serviceContract.getByOwner(currentUser.getId());
            } else {
                contracts = serviceContract.getByApplicant(currentUser.getId());
            }

            String statusFilter = filterContractStatus.getValue();
            if (!"All".equals(statusFilter)) {
                contracts = contracts.stream().filter(c -> statusFilter.equals(c.getStatus())).collect(Collectors.toList());
            }

            contractsTable.setItems(FXCollections.observableArrayList(contracts));
        } catch (SQLException e) {
            showError("Failed to load contracts: " + e.getMessage());
        }
    }

    @FXML private void onContractStatusFilterChanged() { refreshContracts(); }

    // ================================================================
    // CONTRACT COUNTER-OFFER DIALOG (APPLICANT)
    // ================================================================

    private void showCounterOfferDialog(Contract c) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Counter-Offer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(500);
        dialog.getDialogPane().setStyle("-fx-background-color: #1E1E2E;");
        try { dialog.initOwner(contractsTable.getScene().getWindow()); } catch (Exception ignored) {}

        VBox content = new VBox(14);
        content.setPadding(new Insets(16));

        Label title = new Label("💬 Propose a Counter-Offer");
        title.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 18; -fx-font-weight: bold;");

        Label currentInfo = new Label("Current: " + c.getCurrency() + " " + String.format("%.2f", c.getAmount()) +
                " | Round: " + (c.getNegotiationRound() + 1));
        currentInfo.setStyle("-fx-text-fill: #8888AA; -fx-font-size: 12;");

        Label amountLabel = new Label("Proposed Amount:");
        amountLabel.setStyle("-fx-text-fill: #C0C0D8;");
        TextField amountField = new TextField(String.format("%.2f", c.getAmount()));
        amountField.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0; -fx-background-radius: 8; -fx-padding: 10;");
        amountField.setPromptText("Enter your proposed amount");

        Label termsLabel = new Label("Proposed Terms (optional changes):");
        termsLabel.setStyle("-fx-text-fill: #C0C0D8;");
        TextArea termsArea = new TextArea(c.getTerms() != null ? c.getTerms() : "");
        termsArea.setWrapText(true);
        termsArea.setPrefRowCount(5);
        termsArea.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0; -fx-background-radius: 8;");

        Label notesLabel = new Label("Message to employer (optional):");
        notesLabel.setStyle("-fx-text-fill: #C0C0D8;");
        TextField notesField = new TextField();
        notesField.setPromptText("Explain your counter-offer...");
        notesField.setStyle("-fx-background-color: #2A2A3C; -fx-text-fill: #E0E0F0; -fx-background-radius: 8; -fx-padding: 10;");

        content.getChildren().addAll(title, currentInfo, amountLabel, amountField, termsLabel, termsArea, notesLabel, notesField);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                double proposedAmount = Double.parseDouble(amountField.getText().trim());
                c.setCounterAmount(proposedAmount);
                c.setCounterTerms(termsArea.getText().trim());
                String note = notesField.getText().trim();
                if (!note.isEmpty()) {
                    c.setNegotiationNotes((c.getNegotiationNotes() != null ? c.getNegotiationNotes() + "\n" : "")
                            + "Applicant (round " + (c.getNegotiationRound() + 1) + "): " + note);
                }
                c.setNegotiationRound(c.getNegotiationRound() + 1);
                c.setStatus(Contract.STATUS_COUNTER_PROPOSED);
                serviceContract.modifier(c);

                serviceNotification.create(c.getOwnerId(), "CONTRACT",
                        "💬 Counter-Offer Received",
                        getUserName(c.getApplicantId()) + " counter-offered on contract #" + c.getId()
                                + ": " + c.getCurrency() + " " + String.format("%.2f", proposedAmount),
                        c.getId(), "CONTRACT");

                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                refreshContracts();
            } catch (NumberFormatException ex) {
                showError("Invalid amount. Please enter a valid number.");
            } catch (SQLException ex) {
                showError("Failed to submit counter-offer: " + ex.getMessage());
            }
        });
    }

    // ================================================================
    // QR CODE VERIFICATION DIALOG (HR)
    // ================================================================

    private void showQrVerificationDialog(Contract contract) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle("Contract QR Verification");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.getDialogPane().setMinWidth(600);
        dialog.getDialogPane().setMinHeight(620);
        dialog.getDialogPane().setStyle("-fx-background-color: #0A090C;");

        VBox root = new VBox(14);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #0A090C;");

        // ── Title Section ──
        Label titleLbl = new Label("🔐 Blockchain Contract Verification");
        titleLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #90DDF0;");

        Offer offer = offerMap.get(contract.getOfferId());
        String offerTitle = offer != null ? offer.getTitle() : "Contract #" + contract.getId();
        Label offerLbl = new Label("Offer: " + offerTitle + "  |  Contract #" + contract.getId());
        offerLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #6B6B80;");

        // ── Status Timeline ──
        HBox timeline = createContractTimeline(contract);

        // ── QR Code Card ──
        VBox qrBox = new VBox(8);
        qrBox.setAlignment(Pos.CENTER);
        qrBox.setStyle("-fx-background-color: #14131A; -fx-background-radius: 14; -fx-padding: 20; " +
                "-fx-border-color: #1E1E3A; -fx-border-radius: 14; -fx-border-width: 1;");

        Label qrTitle = new Label("BLOCKCHAIN QR CODE");
        qrTitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #90DDF0; -fx-font-weight: bold; -fx-letter-spacing: 2;");

        javafx.scene.image.ImageView qrImageView = new javafx.scene.image.ImageView();
        qrImageView.setFitWidth(160);
        qrImageView.setFitHeight(160);
        qrImageView.setPreserveRatio(true);

        ProgressIndicator qrLoader = new ProgressIndicator();
        qrLoader.setMaxSize(40, 40);
        qrLoader.setStyle("-fx-progress-color: #90DDF0;");

        AppThreadPool.io(() -> {
            try {
                byte[] qrBytes = ContractPdfGenerator.fetchQrCode(contract.getBlockchainHash());
                if (qrBytes != null) {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(qrBytes));
                    Platform.runLater(() -> {
                        qrImageView.setImage(img);
                        qrBox.getChildren().remove(qrLoader);
                    });
                } else {
                    Platform.runLater(() -> {
                        qrBox.getChildren().remove(qrLoader);
                        Label noQr = new Label("QR not available");
                        noQr.setStyle("-fx-text-fill: #6B6B80; -fx-font-size: 11px;");
                        qrBox.getChildren().add(noQr);
                    });
                }
            } catch (Exception ignored) {
                Platform.runLater(() -> qrBox.getChildren().remove(qrLoader));
            }
        });

        qrBox.getChildren().addAll(qrTitle, qrLoader, qrImageView);

        // ── Hash Display Card ──
        VBox hashCard = new VBox(6);
        hashCard.setStyle("-fx-background-color: #14131A; -fx-background-radius: 10; -fx-padding: 14; " +
                "-fx-border-color: #1E1E3A; -fx-border-radius: 10; -fx-border-width: 1;");

        Label hashLabel = new Label("SHA-256 HASH");
        hashLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B6B80; -fx-font-weight: bold;");

        String hash = contract.getBlockchainHash();
        TextField hashField = new TextField(hash != null ? hash : "No hash generated");
        hashField.setEditable(false);
        hashField.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-background-color: #0F0E11; " +
                "-fx-text-fill: #90DDF0; -fx-border-color: #2C666E; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

        // Copy button
        Button btnCopy = new Button("📋 Copy");
        btnCopy.setStyle("-fx-background-color: #1A1A30; -fx-text-fill: #8A8AFF; -fx-font-size: 11px; " +
                "-fx-padding: 4 12; -fx-background-radius: 6; -fx-cursor: hand;");
        btnCopy.setOnAction(e -> {
            if (hash != null) {
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(
                        java.util.Collections.singletonMap(javafx.scene.input.DataFormat.PLAIN_TEXT, hash));
                btnCopy.setText("✅ Copied!");
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    Platform.runLater(() -> btnCopy.setText("📋 Copy"));
                }).start();
            }
        });

        HBox hashRow = new HBox(8);
        hashRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hashField, Priority.ALWAYS);
        hashRow.getChildren().addAll(hashField, btnCopy);

        hashCard.getChildren().addAll(hashLabel, hashRow);

        // ── Verification Input ──
        VBox verifyCard = new VBox(8);
        verifyCard.setStyle("-fx-background-color: #14131A; -fx-background-radius: 10; -fx-padding: 14; " +
                "-fx-border-color: #1E1E3A; -fx-border-radius: 10; -fx-border-width: 1;");

        Label verifyLabel = new Label("VERIFY HASH");
        verifyLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B6B80; -fx-font-weight: bold;");

        Label verifyHint = new Label("Paste the hash from the contract PDF or QR code to verify authenticity");
        verifyHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #555570;");

        TextField verifyInput = new TextField();
        verifyInput.setPromptText("Paste blockchain hash here...");
        verifyInput.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-background-color: #0F0E11; " +
                "-fx-text-fill: #F0EDEE; -fx-border-color: #2A2A4A; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

        // Result area
        VBox resultBox = new VBox(6);
        resultBox.setStyle("-fx-padding: 10;");
        Label resultLabel = new Label();
        resultLabel.setWrapText(true);
        Label resultDetail = new Label();
        resultDetail.setWrapText(true);
        resultDetail.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B6B80;");
        resultBox.getChildren().addAll(resultLabel, resultDetail);

        // Action Buttons
        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);

        Button btnVerify = new Button("🔍 Verify Hash");
        btnVerify.setStyle("-fx-background-color: linear-gradient(to right, #07393C, #2C666E); -fx-text-fill: #F0EDEE; " +
                "-fx-font-weight: bold; -fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 13px;");

        Button btnChainCheck = new Button("🔗 Check Chain Integrity");
        btnChainCheck.setStyle("-fx-background-color: #1A1A30; -fx-text-fill: #8A8AFF; " +
                "-fx-font-weight: bold; -fx-padding: 10 18; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 12px; " +
                "-fx-border-color: #2A2A4A; -fx-border-radius: 8;");

        Button btnAcceptContract = new Button("✔ Accept & Activate");
        btnAcceptContract.setStyle("-fx-background-color: linear-gradient(to right, #166534, #22c55e); -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
        btnAcceptContract.setVisible(false);

        Button btnReject = new Button("✖ Reject & Dispute");
        btnReject.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; " +
                "-fx-font-weight: bold; -fx-padding: 10 24; -fx-background-radius: 8; -fx-cursor: hand;");
        btnReject.setVisible(false);

        btnVerify.setOnAction(e -> {
            String inputHash = verifyInput.getText().trim();
            if (inputHash.isEmpty()) {
                resultLabel.setText("⚠️ Please enter a hash to verify.");
                resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #f59e0b;");
                resultDetail.setText("");
                return;
            }

            // Use DB-backed verification with stored hash fallback for API mode
            BlockchainVerifier.VerificationResult vr = BlockchainVerifier.verifyContract(
                    contract.getId(), inputHash, contract.getBlockchainHash());

            resultLabel.setText(vr.message.split("\n")[0]);
            resultDetail.setText(vr.message.contains("\n") ? vr.message.substring(vr.message.indexOf("\n") + 1) : "");

            if (vr.matches) {
                resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
                btnAcceptContract.setVisible(true);
                btnReject.setVisible(true);
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            } else if (vr.hashValid) {
                resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ef4444; -fx-font-weight: bold;");
                btnAcceptContract.setVisible(false);
                btnReject.setVisible(true);
                SoundManager.getInstance().play(SoundManager.ERROR);
            } else {
                resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #ef4444;");
                SoundManager.getInstance().play(SoundManager.ERROR);
            }
        });

        btnChainCheck.setOnAction(e -> {
            btnChainCheck.setDisable(true);
            btnChainCheck.setText("🔗 Checking...");
            AppThreadPool.io(() -> {
                BlockchainVerifier.ChainIntegrityResult cir = BlockchainVerifier.verifyChainIntegrity();
                Platform.runLater(() -> {
                    btnChainCheck.setDisable(false);
                    btnChainCheck.setText("🔗 Check Chain Integrity");
                    resultLabel.setText(cir.message);
                    resultLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " +
                            (cir.intact ? "#22c55e" : "#ef4444") + ";");
                    resultDetail.setText("Valid blocks: " + cir.validBlocks +
                            (cir.brokenBlocks > 0 ? "  |  Broken: " + cir.brokenBlocks : ""));
                });
            });
        });

        btnAcceptContract.setOnAction(e -> {
            try {
                contract.setStatus("PENDING_SIGNATURE");
                contract.setSignedAt(new java.sql.Timestamp(System.currentTimeMillis()));
                serviceContract.modifier(contract);

                String applicantName = getUserName(contract.getApplicantId());
                serviceNotification.notifyContractVerified(
                        currentUser.getId(), applicantName, offerTitle, contract.getId());

                resultLabel.setText("✅ Contract verified and moved to PENDING_SIGNATURE!");
                resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
                resultDetail.setText("");
                btnAcceptContract.setDisable(true);
                btnReject.setDisable(true);
                // Update timeline
                timeline.getChildren().clear();
                HBox updatedTimeline = createContractTimeline(contract);
                timeline.getChildren().addAll(updatedTimeline.getChildren());
                refreshContracts();
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            } catch (SQLException ex) {
                showError("Failed to update contract: " + ex.getMessage());
            }
        });

        btnReject.setOnAction(e -> {
            try {
                contract.setStatus("DISPUTED");
                serviceContract.modifier(contract);
                resultLabel.setText("⚠️ Contract marked as DISPUTED.");
                resultLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #f59e0b; -fx-font-weight: bold;");
                resultDetail.setText("");
                btnAcceptContract.setDisable(true);
                btnReject.setDisable(true);
                refreshContracts();
                SoundManager.getInstance().play(SoundManager.ERROR);
            } catch (SQLException ex) {
                showError("Failed to update contract: " + ex.getMessage());
            }
        });

        verifyCard.getChildren().addAll(verifyLabel, verifyHint, verifyInput);

        btnRow.getChildren().addAll(btnVerify, btnChainCheck);
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER);
        actionRow.getChildren().addAll(btnAcceptContract, btnReject);

        root.getChildren().addAll(titleLbl, offerLbl, timeline, qrBox, hashCard, verifyCard, btnRow, resultBox, actionRow);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0A090C; -fx-background-color: #0A090C;");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setStyle("-fx-background-color: #0A090C;");
        dialog.showAndWait();
    }

    // ================================================================
    // CONTRACT SIGNATURE DIALOG (Blockchain verify → Draw signature)
    // ================================================================

    private void showContractSignatureDialog(Contract contract) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.initOwner(rootPane.getScene().getWindow());

        Offer offer = offerMap.get(contract.getOfferId());
        String offerTitle = offer != null ? offer.getTitle() : "Contract #" + contract.getId();
        dialog.setTitle("Sign Contract — " + offerTitle);

        VBox root = new VBox(14);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1a2e;");

        // ── Header ──
        Label header = new Label("🔐 Blockchain Verified Signing");
        header.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #90DDF0;");

        Label subtitle = new Label("Contract #" + contract.getId() + "  •  " + offerTitle);
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #6B6B80;");
        subtitle.setWrapText(true);

        // ── Status Timeline ──
        HBox timeline = createContractTimeline(contract);

        // ══════════════════════════════════════════════════════
        //  STEP 1: BLOCKCHAIN VERIFICATION
        // ══════════════════════════════════════════════════════
        VBox step1Box = new VBox(10);
        step1Box.setStyle("-fx-background-color: #14131A; -fx-background-radius: 12; -fx-padding: 16; "
                + "-fx-border-color: #1E1E3A; -fx-border-radius: 12; -fx-border-width: 1;");

        Label step1Title = new Label("STEP 1 — VERIFY BLOCKCHAIN HASH");
        step1Title.setStyle("-fx-font-size: 11px; -fx-text-fill: #90DDF0; -fx-font-weight: bold;");

        Label hashDisplay = new Label("Hash: " + (contract.getBlockchainHash() != null
                ? contract.getBlockchainHash().substring(0, Math.min(50, contract.getBlockchainHash().length())) + "..."
                : "No hash"));
        hashDisplay.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 10px; -fx-text-fill: #6B6B80;");

        TextField verifyInput = new TextField();
        verifyInput.setPromptText("Paste blockchain hash to verify...");
        verifyInput.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11px; -fx-background-color: #0F0E11; "
                + "-fx-text-fill: #F0EDEE; -fx-border-color: #2A2A4A; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8;");

        Label verifyResult = new Label();
        verifyResult.setWrapText(true);

        Button btnVerify = new Button("🔍 Verify Hash");
        btnVerify.setStyle("-fx-background-color: linear-gradient(to right, #07393C, #2C666E); -fx-text-fill: #F0EDEE; "
                + "-fx-font-weight: bold; -fx-padding: 8 20; -fx-background-radius: 8; -fx-cursor: hand;");

        step1Box.getChildren().addAll(step1Title, hashDisplay, verifyInput, btnVerify, verifyResult);

        // ══════════════════════════════════════════════════════
        //  STEP 2: DRAW SIGNATURE (hidden until verified)
        // ══════════════════════════════════════════════════════
        VBox step2Box = new VBox(12);
        step2Box.setStyle("-fx-background-color: #14131A; -fx-background-radius: 12; -fx-padding: 16; "
                + "-fx-border-color: #1E1E3A; -fx-border-radius: 12; -fx-border-width: 1;");
        step2Box.setVisible(false);
        step2Box.setManaged(false);

        Label step2Title = new Label("STEP 2 — DRAW YOUR SIGNATURE");
        step2Title.setStyle("-fx-font-size: 11px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");

        Label signAs = new Label("Signing as: " + currentUser.getFullName());
        signAs.setStyle("-fx-font-size: 12px; -fx-text-fill: #90DDF0;");

        // ── Canvas ──
        double canvasW = 480, canvasH = 180;
        StackPane canvasWrapper = new StackPane();
        canvasWrapper.setMaxSize(canvasW + 4, canvasH + 4);
        canvasWrapper.setStyle("-fx-background-color: #2C666E; -fx-background-radius: 10; -fx-padding: 2;");

        javafx.scene.canvas.Canvas sigCanvas = new javafx.scene.canvas.Canvas(canvasW, canvasH);
        javafx.scene.canvas.GraphicsContext gc = sigCanvas.getGraphicsContext2D();

        // White background
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillRect(0, 0, canvasW, canvasH);

        // Guide line
        gc.setStroke(javafx.scene.paint.Color.rgb(200, 200, 210));
        gc.setLineWidth(0.8);
        gc.strokeLine(40, canvasH * 0.72, canvasW - 40, canvasH * 0.72);

        // "Sign here" hint
        gc.setFill(javafx.scene.paint.Color.rgb(180, 180, 195));
        gc.setFont(javafx.scene.text.Font.font("Arial", 10));
        gc.fillText("Sign here", canvasW / 2 - 22, canvasH * 0.72 + 14);

        // Drawing state
        final boolean[] drawing = {false};
        final double[] lastX = {0}, lastY = {0};

        gc.setStroke(javafx.scene.paint.Color.rgb(25, 25, 60));
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineWidth(2.5);

        sigCanvas.setOnMousePressed(e -> {
            drawing[0] = true;
            lastX[0] = e.getX();
            lastY[0] = e.getY();
        });
        sigCanvas.setOnMouseDragged(e -> {
            if (!drawing[0]) return;
            gc.strokeLine(lastX[0], lastY[0], e.getX(), e.getY());
            lastX[0] = e.getX();
            lastY[0] = e.getY();
        });
        sigCanvas.setOnMouseReleased(e -> drawing[0] = false);

        canvasWrapper.getChildren().add(sigCanvas);

        // ── Pen controls ──
        HBox penControls = new HBox(12);
        penControls.setAlignment(Pos.CENTER);

        String pcBtnStyle = "-fx-background-color: #333; -fx-text-fill: #ccc; -fx-font-size: 11px; "
                + "-fx-padding: 4 10; -fx-background-radius: 5; -fx-cursor: hand;";

        Label penLabel = new Label("Pen:");
        penLabel.setStyle("-fx-text-fill: #9696A5; -fx-font-size: 11px;");

        Button thinBtn = new Button("Thin");
        thinBtn.setStyle(pcBtnStyle);
        thinBtn.setOnAction(e -> gc.setLineWidth(1.5));

        Button medBtn = new Button("Medium");
        medBtn.setStyle(pcBtnStyle);
        medBtn.setOnAction(e -> gc.setLineWidth(2.5));

        Button boldBtn = new Button("Bold");
        boldBtn.setStyle(pcBtnStyle);
        boldBtn.setOnAction(e -> gc.setLineWidth(4.0));

        Label colorLabel = new Label("Color:");
        colorLabel.setStyle("-fx-text-fill: #9696A5; -fx-font-size: 11px;");

        Button blackPen = new Button("⬛");
        blackPen.setStyle(pcBtnStyle);
        blackPen.setOnAction(e -> gc.setStroke(javafx.scene.paint.Color.rgb(25, 25, 60)));

        Button bluePen = new Button("🔵");
        bluePen.setStyle(pcBtnStyle);
        bluePen.setOnAction(e -> gc.setStroke(javafx.scene.paint.Color.rgb(20, 50, 140)));

        penControls.getChildren().addAll(penLabel, thinBtn, medBtn, boldBtn,
                new Region() {{ setPrefWidth(12); }},
                colorLabel, blackPen, bluePen);

        // ── Action buttons ──
        HBox actionBar = new HBox(12);
        actionBar.setAlignment(Pos.CENTER);

        Button clearBtn = new Button("🗑 Clear");
        clearBtn.setStyle("-fx-background-color: #444; -fx-text-fill: #F0EDEE; -fx-font-size: 13px; "
                + "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> {
            gc.setFill(javafx.scene.paint.Color.WHITE);
            gc.fillRect(0, 0, canvasW, canvasH);
            gc.setStroke(javafx.scene.paint.Color.rgb(200, 200, 210));
            gc.setLineWidth(0.8);
            gc.strokeLine(40, canvasH * 0.72, canvasW - 40, canvasH * 0.72);
            gc.setFill(javafx.scene.paint.Color.rgb(180, 180, 195));
            gc.setFont(javafx.scene.text.Font.font("Arial", 10));
            gc.fillText("Sign here", canvasW / 2 - 22, canvasH * 0.72 + 14);
            gc.setStroke(javafx.scene.paint.Color.rgb(25, 25, 60));
            gc.setLineWidth(2.5);
        });

        Button confirmBtn = new Button("✅ Confirm & Sign Contract");
        confirmBtn.setStyle("-fx-background-color: linear-gradient(to right, #166534, #22c55e); -fx-text-fill: white; "
                + "-fx-font-size: 13px; -fx-padding: 8 24; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-weight: bold;");
        confirmBtn.setOnAction(e -> {
            try {
                // Snapshot canvas to base64 PNG
                javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                params.setFill(javafx.scene.paint.Color.WHITE);
                javafx.scene.image.WritableImage snapshot = sigCanvas.snapshot(params, null);

                java.awt.image.BufferedImage bImg = javafx.embed.swing.SwingFXUtils.fromFXImage(snapshot, null);
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(bImg, "png", baos);
                String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

                // Persist signature
                serviceContract.signContract(contract.getId(), currentUser.getId(), base64);

                // Update in-memory object
                contract.setSignedByUserId(currentUser.getId());
                contract.setSignatureData(base64);
                contract.setSignedAt(new Timestamp(System.currentTimeMillis()));
                contract.setStatus(Contract.STATUS_ACTIVE);

                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
                dialog.close();
                refreshContracts();

                // Notify the other party
                int notifyUserId = (currentUser.getId() == contract.getOwnerId())
                        ? contract.getApplicantId() : contract.getOwnerId();
                serviceNotification.create(notifyUserId, "CONTRACT",
                        "✍ Contract Signed",
                        "Contract #" + contract.getId() + " for \"" + offerTitle + "\" has been signed by "
                                + currentUser.getFullName() + " and is now ACTIVE.",
                        contract.getId(), "CONTRACT");

                showInfo("Contract signed and activated!\n\nContract #" + contract.getId()
                        + " is now ACTIVE.\nSigned by: " + currentUser.getFullName());
            } catch (Exception ex) {
                showError("Failed to sign contract: " + ex.getMessage());
            }
        });

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #333; -fx-text-fill: #ccc; -fx-font-size: 13px; "
                + "-fx-padding: 8 20; -fx-background-radius: 6; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> dialog.close());

        actionBar.getChildren().addAll(clearBtn, confirmBtn, cancelBtn);

        step2Box.getChildren().addAll(step2Title, signAs, canvasWrapper, penControls, actionBar);

        // ── Step 1 verify logic ──
        btnVerify.setOnAction(e -> {
            String inputHash = verifyInput.getText().trim();
            if (inputHash.isEmpty()) {
                verifyResult.setText("⚠ Please paste the blockchain hash to verify.");
                verifyResult.setStyle("-fx-font-size: 13px; -fx-text-fill: #f59e0b;");
                return;
            }

            BlockchainVerifier.VerificationResult vr = BlockchainVerifier.verifyContract(
                    contract.getId(), inputHash, contract.getBlockchainHash());

            if (vr.matches) {
                verifyResult.setText("✅ " + vr.message.split("\n")[0] + "\nBlockchain integrity confirmed — you may now sign.");
                verifyResult.setStyle("-fx-font-size: 13px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);

                // Reveal Step 2
                step2Box.setVisible(true);
                step2Box.setManaged(true);
            } else {
                verifyResult.setText("❌ " + vr.message);
                verifyResult.setStyle("-fx-font-size: 13px; -fx-text-fill: #ef4444; -fx-font-weight: bold;");
                SoundManager.getInstance().play(SoundManager.ERROR);
                step2Box.setVisible(false);
                step2Box.setManaged(false);
            }
        });

        root.getChildren().addAll(header, subtitle, timeline, step1Box, step2Box);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");

        javafx.scene.Scene scene = new javafx.scene.Scene(scroll, 580, 700);
        // Apply stylesheets from parent if available
        try {
            scene.getStylesheets().addAll(rootPane.getScene().getStylesheets());
        } catch (Exception ignored) {}
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /**
     * Creates a visual status timeline for a contract.
     * Shows: DRAFT → PENDING_SIGNATURE → ACTIVE → COMPLETED with current step highlighted.
     */
    private HBox createContractTimeline(Contract contract) {
        HBox timeline = new HBox(0);
        timeline.setAlignment(Pos.CENTER);
        timeline.setPadding(new Insets(8, 0, 8, 0));

        String[] steps = {"DRAFT", "PENDING_SIGNATURE", "ACTIVE", "COMPLETED"};
        String[] labels = {"Draft", "Pending Sign", "Active", "Completed"};
        String[] icons = {"📝", "✍️", "🟢", "✅"};
        String currentStatus = contract.getStatus();

        // If status is TERMINATED or DISPUTED, find the last reached step
        boolean isTerminal = "TERMINATED".equals(currentStatus) || "DISPUTED".equals(currentStatus);
        int currentIdx = -1;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i].equals(currentStatus)) { currentIdx = i; break; }
        }

        for (int i = 0; i < steps.length; i++) {
            boolean reached = i <= currentIdx;
            boolean isCurrent = i == currentIdx;

            // Step circle
            VBox step = new VBox(2);
            step.setAlignment(Pos.CENTER);

            Label icon = new Label(icons[i]);
            icon.setStyle("-fx-font-size: 16px;");

            Label lbl = new Label(labels[i]);
            lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: " + (isCurrent ? "bold" : "normal") + "; -fx-text-fill: " +
                    (reached ? "#90DDF0" : "#3A3A5E") + ";");

            Label dot = new Label(isCurrent ? "●" : reached ? "●" : "○");
            dot.setStyle("-fx-font-size: 14px; -fx-text-fill: " +
                    (isCurrent ? "#22c55e" : reached ? "#2C666E" : "#2A2A4A") + ";");

            step.getChildren().addAll(icon, dot, lbl);

            timeline.getChildren().add(step);

            // Connector line
            if (i < steps.length - 1) {
                Region line = new Region();
                line.setPrefHeight(2);
                line.setMinHeight(2);
                line.setPrefWidth(50);
                line.setStyle("-fx-background-color: " + (i < currentIdx ? "#2C666E" : "#2A2A4A") + ";");
                VBox lineWrap = new VBox(line);
                lineWrap.setAlignment(Pos.CENTER);
                lineWrap.setPadding(new Insets(14, 4, 0, 4));
                timeline.getChildren().add(lineWrap);
            }
        }

        // Show terminal status badge if applicable
        if (isTerminal) {
            Label termBadge = new Label(currentStatus.equals("DISPUTED") ? "⚠️ DISPUTED" : "🛑 TERMINATED");
            termBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " +
                    ("DISPUTED".equals(currentStatus) ? "#f59e0b" : "#ef4444") +
                    "; -fx-background-color: " +
                    ("DISPUTED".equals(currentStatus) ? "rgba(245,158,11,0.15)" : "rgba(239,68,68,0.15)") +
                    "; -fx-padding: 4 12; -fx-background-radius: 10;");
            VBox termWrap = new VBox(termBadge);
            termWrap.setAlignment(Pos.CENTER);
            termWrap.setPadding(new Insets(0, 0, 0, 12));
            timeline.getChildren().add(termWrap);
        }

        return timeline;
    }


    private void exportContractPdf(Contract c) {
        Offer offer = offerMap.get(c.getOfferId());
        String offerTitle = offer != null ? offer.getTitle() : "Offer #" + c.getOfferId();
        String ownerName = getUserName(c.getOwnerId());
        String applicantName = getUserName(c.getApplicantId());

        AppThreadPool.io(() -> {
            try {
                File pdf = ContractPdfGenerator.generatePdf(c, offerTitle, ownerName, applicantName);
                Platform.runLater(() -> {
                    // Offer to save
                    FileChooser fc = new FileChooser();
                    fc.setTitle("Save Contract PDF");
                    fc.setInitialFileName("contract_" + c.getId() + ".pdf");
                    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
                    File target = fc.showSaveDialog(rootPane.getScene().getWindow());
                    if (target != null) {
                        try {
                            java.nio.file.Files.copy(pdf.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            showInfo("PDF exported to: " + target.getName());
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(target);
                            }
                        } catch (Exception ex) {
                            showError("Export failed: " + ex.getMessage());
                        }
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError("PDF generation failed: " + ex.getMessage()));
            }
        });
    }

    private void runRiskAnalysis(Contract c) {
        aiStatusLabel.setText("🤖 Analyzing risk...");
        AppThreadPool.io(() -> {
            String terms = c.getTerms() != null ? c.getTerms() : "No terms specified";
            String duration = (c.getStartDate() != null && c.getEndDate() != null)
                    ? c.getStartDate() + " to " + c.getEndDate() : "Unknown";
            String result = zaiService.analyzeRisk(terms, c.getAmount(), duration);

            Platform.runLater(() -> {
                aiStatusLabel.setText("");
                try {
                    JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                    int riskScore = json.get("risk_score").getAsInt();
                    c.setRiskScore(riskScore);
                    c.setRiskFactors(result);
                    serviceContract.modifier(c);
                    refreshContracts();
                    showInfo("Risk Score: " + riskScore + "/100 (" +
                            (riskScore <= 30 ? "Low" : riskScore <= 70 ? "Medium" : "High") + ")");
                } catch (Exception ex) {
                    showInfo("Risk analysis:\n" + result.substring(0, Math.min(300, result.length())));
                }
            });
        });
    }

    // ================================================================
    // TAB 5: AI ASSISTANT
    // ================================================================

    @FXML
    private void aiSendMessage() {
        String msg = aiInputField.getText().trim();
        if (msg.isEmpty()) return;
        aiInputField.clear();
        addAiMessage("user", msg);

        aiStatusLabel.setText("🤖 Thinking...");
        AppThreadPool.io(() -> {
            String response;
            if (selectedAiContract != null && selectedAiContract.getTerms() != null) {
                response = zaiService.chatWithContract(selectedAiContract.getTerms(), aiChatHistory, msg);
            } else {
                response = zaiService.chat("You are the SynergyGig AI assistant. Help with offers, applications, and contracts.", msg);
            }
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", msg);
            aiChatHistory.add(userMsg);
            Map<String, String> aiMsg = new HashMap<>();
            aiMsg.put("role", "assistant");
            aiMsg.put("content", response);
            aiChatHistory.add(aiMsg);
            Platform.runLater(() -> {
                aiStatusLabel.setText("");
                addAiMessage("assistant", response);
            });
        });
    }

    @FXML private void aiGenerateContract() {
        if (selectedAiContract == null) {
            showError("Select a contract first (from Contracts tab → 💬 button).");
            return;
        }
        Offer offer = offerMap.get(selectedAiContract.getOfferId());
        if (offer == null) { showError("Offer not found."); return; }

        addAiMessage("user", "Generate contract terms for: " + offer.getTitle());
        aiStatusLabel.setText("🤖 Generating contract...");

        AppThreadPool.io(() -> {
            String result = zaiService.generateContract(
                    offer.getTitle(), offer.getDescription() != null ? offer.getDescription() : "",
                    offer.getAmount(), getUserName(selectedAiContract.getApplicantId()),
                    offer.getStartDate() != null ? offer.getStartDate().toString() : "TBD",
                    offer.getEndDate() != null ? offer.getEndDate().toString() : "TBD"
            );
            Platform.runLater(() -> {
                aiStatusLabel.setText("");
                addAiMessage("assistant", result);
                // Ask if they want to apply the terms
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Apply these generated terms to the contract?", ButtonType.YES, ButtonType.NO);
                DialogHelper.theme(confirm);
                styleDarkDialog(confirm.getDialogPane());
                confirm.showAndWait().ifPresent(btn -> {
                    if (btn == ButtonType.YES) {
                        try {
                            selectedAiContract.setTerms(result);
                            String hash = BlockchainVerifier.generateHash(selectedAiContract.getId(), result, selectedAiContract.getAmount());
                            selectedAiContract.setBlockchainHash(hash);
                            serviceContract.modifier(selectedAiContract);
                            showInfo("Contract terms applied + blockchain hash generated!");
                            refreshContracts();
                        } catch (SQLException e) { showError(e.getMessage()); }
                    }
                });
            });
        });
    }

    @FXML private void aiAnalyzeRisk() {
        if (selectedAiContract == null) { showError("Select a contract first."); return; }
        runRiskAnalysis(selectedAiContract);
    }

    @FXML private void aiImproveTerms() {
        if (selectedAiContract == null || selectedAiContract.getTerms() == null) {
            showError("Select a contract with terms first.");
            return;
        }
        addAiMessage("user", "Improve the contract terms");
        aiStatusLabel.setText("🤖 Improving terms...");
        AppThreadPool.io(() -> {
            String result = zaiService.improveTerms(selectedAiContract.getTerms());
            Platform.runLater(() -> {
                aiStatusLabel.setText("");
                addAiMessage("assistant", result);
            });
        });
    }

    @FXML private void aiSummarize() {
        if (selectedAiContract == null || selectedAiContract.getTerms() == null) {
            showError("Select a contract with terms first.");
            return;
        }
        addAiMessage("user", "Summarize this contract");
        aiStatusLabel.setText("🤖 Summarizing...");
        AppThreadPool.io(() -> {
            String result = zaiService.summarizeContract(selectedAiContract.getTerms());
            Platform.runLater(() -> {
                aiStatusLabel.setText("");
                addAiMessage("assistant", result);
            });
        });
    }

    @FXML private void aiDraftEmail() {
        if (selectedAiContract == null) { showError("Select a contract first."); return; }
        // Show dialog to choose email type and style
        ChoiceDialog<String> typeDialog = new ChoiceDialog<>("CONTRACT_READY", "ACCEPTED", "REJECTED", "CONTRACT_READY");
        DialogHelper.theme(typeDialog);
        typeDialog.setTitle("Draft Email");
        typeDialog.setHeaderText("What type of email?");
        typeDialog.showAndWait().ifPresent(type -> {
            ChoiceDialog<String> styleDialog = new ChoiceDialog<>("Professional", "Professional", "Friendly", "Direct");
            DialogHelper.theme(styleDialog);
            styleDialog.setTitle("Email Style");
            styleDialog.setHeaderText("Choose tone:");
            styleDialog.showAndWait().ifPresent(style -> {
                Offer offer = offerMap.get(selectedAiContract.getOfferId());
                addAiMessage("user", "Draft a " + type + " email (" + style + " tone)");
                aiStatusLabel.setText("🤖 Drafting email...");
                AppThreadPool.io(() -> {
                    String result = zaiService.draftEmail(type, style,
                            getUserName(selectedAiContract.getApplicantId()),
                            offer != null ? offer.getTitle() : "Contract #" + selectedAiContract.getId(),
                            "Contract amount: " + selectedAiContract.getCurrency() + " " + selectedAiContract.getAmount());
                    Platform.runLater(() -> {
                        aiStatusLabel.setText("");
                        addAiMessage("assistant", result);
                    });
                });
            });
        });
    }

    @FXML private void aiScoreApplicant() {
        // Let user pick an application to score
        try {
            List<JobApplication> apps = serviceApp.recuperer();
            List<String> choices = apps.stream()
                    .map(a -> "#" + a.getId() + " — " + getUserName(a.getApplicantId()) + " → Offer #" + a.getOfferId())
                    .collect(Collectors.toList());
            if (choices.isEmpty()) { showError("No applications found."); return; }
            ChoiceDialog<String> d = new ChoiceDialog<>(choices.get(0), choices);
            DialogHelper.theme(d);
            d.setTitle("Score Applicant");
            d.setHeaderText("Select an application to score:");
            d.showAndWait().ifPresent(choice -> {
                int appId = Integer.parseInt(choice.split(" — ")[0].replace("#", ""));
                apps.stream().filter(a -> a.getId() == appId).findFirst().ifPresent(this::runAiScoring);
            });
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    @FXML private void aiOfferStrategy() {
        try {
            List<Offer> offers = serviceOffer.getByOwner(currentUser.getId());
            if (offers.isEmpty()) { showError("No offers found."); return; }
            List<String> choices = offers.stream()
                    .map(o -> "#" + o.getId() + " — " + o.getTitle())
                    .collect(Collectors.toList());
            ChoiceDialog<String> d = new ChoiceDialog<>(choices.get(0), choices);
            DialogHelper.theme(d);
            d.setTitle("Offer Strategy");
            d.setHeaderText("Select an offer to analyze:");
            d.showAndWait().ifPresent(choice -> {
                int offerId = Integer.parseInt(choice.split(" — ")[0].replace("#", ""));
                offers.stream().filter(o -> o.getId() == offerId).findFirst().ifPresent(offer -> {
                    addAiMessage("user", "Analyze strategy for: " + offer.getTitle());
                    aiStatusLabel.setText("🤖 Analyzing strategy...");
                    AppThreadPool.io(() -> {
                        String result = zaiService.adviseOfferStrategy(offer.getTitle(),
                                offer.getDescription() != null ? offer.getDescription() : "",
                                offer.getAmount(),
                                offer.getLocation() != null ? offer.getLocation() : "Remote",
                                offer.getRequiredSkills() != null ? offer.getRequiredSkills() : "");
                        Platform.runLater(() -> {
                            aiStatusLabel.setText("");
                            addAiMessage("assistant", result);
                        });
                    });
                });
            });
        } catch (SQLException e) { showError(e.getMessage()); }
    }

    @FXML private void aiEnhanceDescription() {
        TextInputDialog dialog = new TextInputDialog();
        DialogHelper.theme(dialog);
        dialog.setTitle("Enhance Description");
        dialog.setHeaderText("Enter bullet points for the offer description:");
        dialog.getEditor().setPrefWidth(400);
        dialog.showAndWait().ifPresent(bullets -> {
            TextInputDialog titleDialog = new TextInputDialog();
            DialogHelper.theme(titleDialog);
            titleDialog.setTitle("Offer Title");
            titleDialog.setHeaderText("What's the offer title?");
            titleDialog.showAndWait().ifPresent(title -> {
                addAiMessage("user", "Enhance description for: " + title);
                aiStatusLabel.setText("🤖 Enhancing...");
                AppThreadPool.io(() -> {
                    String result = zaiService.enhanceOfferDescription(bullets, title);
                    Platform.runLater(() -> {
                        aiStatusLabel.setText("");
                        addAiMessage("assistant", result);
                    });
                });
            });
        });
    }

    // ==================== AI Chat UI Helpers ====================

    private void addAiMessage(String role, String text) {
        HBox row = new HBox(8);
        row.setPadding(new Insets(6, 8, 6, 8));

        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(500);
        bubble.setPadding(new Insets(10, 14, 10, 14));

        if ("user".equals(role)) {
            bubble.getStyleClass().add("oc-chat-user");
            row.setAlignment(Pos.CENTER_RIGHT);
        } else if ("assistant".equals(role)) {
            bubble.getStyleClass().add("oc-chat-ai");
            row.setAlignment(Pos.CENTER_LEFT);
        } else {
            bubble.getStyleClass().add("oc-chat-system");
            row.setAlignment(Pos.CENTER);
        }

        row.getChildren().add(bubble);
        aiChatBox.getChildren().add(row);

        // Auto scroll to bottom
        Platform.runLater(() -> aiChatScroll.setVvalue(1.0));
    }

    // ==================== Utility ====================

    private void showInfo(String msg) {
        showStyledPopup("✅ Success", msg, "#22c55e", "rgba(34,197,94,0.12)");
    }

    private void showError(String msg) {
        showStyledPopup("❌ Error", msg, "#ef4444", "rgba(239,68,68,0.12)");
    }

    /**
     * Apply dark theme to any DialogPane so all popups match our UI.
     */
    private void styleDarkDialog(DialogPane pane) {
        pane.setStyle("-fx-background-color: #0D0D1A;");
        pane.lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: #C0C0D8;"));
        pane.lookupAll(".text-field").forEach(n ->
                n.setStyle("-fx-background-color: #14131A; -fx-text-fill: #E0E0F0; -fx-border-color: #2A2A4A; -fx-border-radius: 6; -fx-background-radius: 6;"));
        pane.lookupAll(".text-area").forEach(n ->
                n.setStyle("-fx-background-color: #14131A; -fx-text-fill: #E0E0F0; -fx-border-color: #2A2A4A; -fx-border-radius: 6; -fx-background-radius: 6; -fx-control-inner-background: #14131A;"));
        pane.lookupAll(".combo-box").forEach(n ->
                n.setStyle("-fx-background-color: #14131A; -fx-mark-color: #8A8AFF;"));
        pane.lookupAll(".date-picker").forEach(n ->
                n.setStyle("-fx-background-color: #14131A; -fx-control-inner-background: #14131A;"));
        pane.lookupAll(".button").forEach(n -> {
            if (!n.getStyleClass().contains("cancel-button")) {
                n.setStyle("-fx-background-color: linear-gradient(to right, #07393C, #2C666E); -fx-text-fill: #F0EDEE; -fx-background-radius: 6; -fx-cursor: hand;");
            } else {
                n.setStyle("-fx-background-color: #1A1A30; -fx-text-fill: #8A8AFF; -fx-background-radius: 6; -fx-cursor: hand;");
            }
        });
        pane.lookupAll(".content").forEach(n -> n.setStyle("-fx-text-fill: #C0C0D8;"));
        pane.lookupAll(".header-panel").forEach(n -> n.setStyle("-fx-background-color: #141430;"));
    }

    /**
     * Dark-themed styled popup that matches our UI instead of default OS Alert dialogs.
     */
    private void showStyledPopup(String title, String msg, String accentColor, String bgTint) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogHelper.theme(dialog);
        dialog.setTitle(title.replaceAll("[^\\w\\s]", "").trim());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().setStyle("-fx-background-color: #0D0D1A;");
        dialog.getDialogPane().setMinWidth(420);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.TOP_LEFT);
        root.setStyle("-fx-background-color: #0D0D1A;");

        // Accent top bar
        Region accent = new Region();
        accent.setPrefHeight(3);
        accent.setStyle("-fx-background-color: " + accentColor + "; -fx-background-radius: 2;");

        // Title
        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        // Message in a styled card
        Label msgLbl = new Label(msg);
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(380);
        msgLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #C0C0D8; -fx-padding: 12; " +
                "-fx-background-color: " + bgTint + "; -fx-background-radius: 8;");

        root.getChildren().addAll(accent, titleLbl, msgLbl);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }
}
