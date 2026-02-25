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
    private ZAIService zaiService;

    // ── State ──
    private User currentUser;
    private boolean isOwnerOrAdmin;
    private Button activeTab;
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
            btn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
                switchTab(btn, tab[1]);
            });
            tabBar.getChildren().add(btn);
        }

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

    // ==================== Tab Switching ====================

    private void switchTab(Button btn, String tabName) {
        if (activeTab != null) activeTab.getStyleClass().remove("oc-tab-active");
        btn.getStyleClass().add("oc-tab-active");
        activeTab = btn;

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

        Button btnToggle = new Button(o.getStatus().equals("DRAFT") ? "Publish" : o.getStatus().equals("PUBLISHED") ? "Close" : o.getStatus());
        btnToggle.getStyleClass().add("oc-btn-primary");
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
        dialog.setTitle(existing == null ? "New Offer" : "Edit Offer");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(500);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField tfTitle = new TextField(existing != null ? existing.getTitle() : "");
        tfTitle.setPromptText("Offer title");

        TextArea taDesc = new TextArea(existing != null ? existing.getDescription() : "");
        taDesc.setPromptText("Description");
        taDesc.setPrefRowCount(4);

        ComboBox<String> cbType = new ComboBox<>(FXCollections.observableArrayList("FULL_TIME", "PART_TIME", "FREELANCE", "INTERNSHIP", "CONTRACT"));
        cbType.setValue(existing != null ? existing.getOfferType() : "FREELANCE");

        TextField tfSkills = new TextField(existing != null ? existing.getRequiredSkills() : "");
        tfSkills.setPromptText("Java, Python, React...");

        TextField tfLocation = new TextField(existing != null ? existing.getLocation() : "");
        tfLocation.setPromptText("Remote / City");

        TextField tfAmount = new TextField(existing != null ? String.valueOf(existing.getAmount()) : "0");
        ComboBox<String> cbCurrency = new ComboBox<>(FXCollections.observableArrayList("USD", "EUR", "GBP", "TND"));
        cbCurrency.setValue(existing != null ? existing.getCurrency() : "USD");

        DatePicker dpStart = new DatePicker(existing != null && existing.getStartDate() != null ? existing.getStartDate().toLocalDate() : null);
        DatePicker dpEnd = new DatePicker(existing != null && existing.getEndDate() != null ? existing.getEndDate().toLocalDate() : null);

        grid.add(new Label("Title:"), 0, 0);     grid.add(tfTitle, 1, 0, 2, 1);
        grid.add(new Label("Description:"), 0, 1); grid.add(taDesc, 1, 1, 2, 1);
        grid.add(new Label("Type:"), 0, 2);       grid.add(cbType, 1, 2);
        grid.add(new Label("Skills:"), 0, 3);     grid.add(tfSkills, 1, 3, 2, 1);
        grid.add(new Label("Location:"), 0, 4);   grid.add(tfLocation, 1, 4);
        grid.add(new Label("Amount:"), 0, 5);     grid.add(tfAmount, 1, 5);
        grid.add(new Label("Currency:"), 0, 6);   grid.add(cbCurrency, 1, 6);
        grid.add(new Label("Start Date:"), 0, 7); grid.add(dpStart, 1, 7);
        grid.add(new Label("End Date:"), 0, 8);   grid.add(dpEnd, 1, 8);

        dialog.getDialogPane().setContent(grid);
        styleDarkDialog(dialog.getDialogPane());

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    Offer o = existing != null ? existing : new Offer();
                    o.setTitle(tfTitle.getText().trim());
                    o.setDescription(taDesc.getText().trim());
                    o.setOfferType(cbType.getValue());
                    o.setRequiredSkills(tfSkills.getText().trim());
                    o.setLocation(tfLocation.getText().trim());
                    o.setAmount(Double.parseDouble(tfAmount.getText().trim()));
                    o.setCurrency(cbCurrency.getValue());
                    o.setOwnerId(currentUser.getId());
                    if (existing == null) o.setStatus("DRAFT");
                    o.setStartDate(dpStart.getValue() != null ? Date.valueOf(dpStart.getValue()) : null);
                    o.setEndDate(dpEnd.getValue() != null ? Date.valueOf(dpEnd.getValue()) : null);
                    return o;
                } catch (Exception ex) {
                    showError("Invalid input: " + ex.getMessage());
                }
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

    private void toggleOfferStatus(Offer o) {
        try {
            if ("DRAFT".equals(o.getStatus())) {
                o.setStatus("PUBLISHED");
                serviceOffer.modifier(o);
                showInfo("Offer published!");
            } else if ("PUBLISHED".equals(o.getStatus())) {
                o.setStatus("COMPLETED");
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
            // Owner can review, accept, reject
            Button btnAiScore = new Button("🎯 AI Score");
            btnAiScore.getStyleClass().add("oc-btn-secondary");
            btnAiScore.setOnAction(e -> runAiScoring(a));

            Button btnAccept = new Button("✓ Accept");
            btnAccept.getStyleClass().add("oc-btn-primary");
            btnAccept.setOnAction(e -> updateApplicationStatus(a, "ACCEPTED"));

            Button btnReject = new Button("✗ Reject");
            btnReject.getStyleClass().add("oc-btn-danger");
            btnReject.setOnAction(e -> updateApplicationStatus(a, "REJECTED"));

            if ("PENDING".equals(a.getStatus()) || "REVIEWED".equals(a.getStatus())) {
                actions.getChildren().addAll(btnAiScore, btnAccept, btnReject);
            }
        } else {
            // Applicant can withdraw
            if ("PENDING".equals(a.getStatus())) {
                Button btnWithdraw = new Button("Withdraw");
                btnWithdraw.getStyleClass().add("oc-btn-danger");
                btnWithdraw.setOnAction(e -> updateApplicationStatus(a, "WITHDRAWN"));
                actions.getChildren().add(btnWithdraw);
            }
        }

        Button btnViewCover = new Button("View");
        btnViewCover.getStyleClass().add("oc-btn-secondary");
        btnViewCover.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Application Details");
            alert.setHeaderText(offerTitle + " — " + getUserName(a.getApplicantId()));
            String content = "Cover Letter:\n" + (a.getCoverLetter() != null ? a.getCoverLetter() : "N/A");
            if (a.getAiFeedback() != null) content += "\n\nAI Feedback:\n" + a.getAiFeedback();
            alert.setContentText(content);
            alert.getDialogPane().setMinWidth(500);
            styleDarkDialog(alert.getDialogPane());
            alert.showAndWait();
        });
        actions.getChildren().add(btnViewCover);

        row.getChildren().addAll(info, scoreBox, status, actions);
        return row;
    }

    private void showApplyDialog(Offer offer) {
        Dialog<JobApplication> dialog = new Dialog<>();
        dialog.setTitle("Apply to: " + offer.getTitle());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(450);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));
        Label lbl = new Label("Write your cover letter / motivation:");
        TextArea taCover = new TextArea();
        taCover.setPromptText("Why are you a great fit for this offer?");
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
                showInfo("Application submitted!");
                SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);
            } catch (SQLException e) {
                showError("Application failed: " + e.getMessage());
            }
        });
    }

    private void updateApplicationStatus(JobApplication a, String newStatus) {
        try {
            a.setStatus(newStatus);
            serviceApp.modifier(a);
            SoundManager.getInstance().play(SoundManager.MESSAGE_SENT);

            // If accepted, auto-create a draft contract + send email + notify
            if ("ACCEPTED".equals(newStatus)) {
                Offer offer = offerMap.get(a.getOfferId());
                if (offer != null) {
                    Contract c = new Contract(
                            offer.getId(), a.getApplicantId(), offer.getOwnerId(),
                            null, offer.getAmount(), offer.getCurrency(),
                            "DRAFT", offer.getStartDate(), offer.getEndDate()
                    );
                    // Generate blockchain hash
                    String hash = BlockchainVerifier.generateHash(0, "Auto-generated from application acceptance", offer.getAmount());
                    c.setBlockchainHash(hash);
                    try {
                        serviceContract.ajouter(c);
                        System.out.println("[OfferContract] Contract #" + c.getId() + " created for application #" + a.getId());
                    } catch (Exception contractEx) {
                        contractEx.printStackTrace();
                        // Rollback application status on contract creation failure
                        a.setStatus("REVIEWED");
                        try { serviceApp.modifier(a); } catch (Exception ignored) {}
                        showError("Failed to create contract: " + contractEx.getMessage());
                        refreshApplications();
                        return;
                    }

                    // Show immediate feedback — don't wait for PDF/email
                    showInfo("✅ Application accepted!\n\nContract #" + c.getId() + " created (DRAFT).\n" +
                            "Blockchain hash: " + BlockchainVerifier.shortHash(hash) + "\n\n" +
                            "PDF generation & email sending in progress...");

                    String applicantName = getUserName(a.getApplicantId());
                    String ownerName = getUserName(offer.getOwnerId());

                    // Generate PDF + send email + notify in background
                    AppThreadPool.io(() -> {
                        try {
                            // 1) Generate contract PDF
                            System.out.println("[OfferContract] Generating PDF for contract #" + c.getId() + "...");
                            File pdf = ContractPdfGenerator.generatePdf(c, offer.getTitle(), ownerName, applicantName);
                            System.out.println("[OfferContract] PDF generated: " + pdf.getAbsolutePath());

                            // 2) Send email with PDF to applicant
                            User applicant = serviceUser.getById(a.getApplicantId());
                            if (applicant != null && applicant.getEmail() != null) {
                                System.out.println("[OfferContract] Sending email to " + applicant.getEmail() + "...");
                                EmailService.sendContractEmail(
                                        applicant.getEmail(), applicant.getFirstName(),
                                        ownerName, offer.getTitle(),
                                        offer.getCurrency(), offer.getAmount(),
                                        hash, pdf
                                );
                                System.out.println("[OfferContract] Email sent successfully.");
                            } else {
                                System.out.println("[OfferContract] No email address for applicant, skipping email.");
                            }

                            // 3) Create in-app notification for applicant
                            serviceNotification.notifyContractReady(
                                    a.getApplicantId(), applicantName, offer.getTitle(), c.getId());
                            System.out.println("[OfferContract] Notification created for applicant.");

                        } catch (Exception ex) {
                            ex.printStackTrace();
                            System.err.println("[OfferContract] Background task failed: " + ex.getMessage());
                            Platform.runLater(() -> showError(
                                    "Contract was created but post-processing failed:\n" + ex.getMessage()));
                        }
                    });
                } else {
                    showError("Offer not found for this application (ID: " + a.getOfferId() + ").");
                }
            }
            refreshApplications();
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Status update failed: " + e.getMessage());
        }
    }

    private void runAiScoring(JobApplication a) {
        Offer offer = offerMap.get(a.getOfferId());
        if (offer == null) { showError("Offer not found."); return; }

        aiStatusLabel.setText("🤖 Scoring applicant...");
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
                    JsonObject json = JsonParser.parseString(result).getAsJsonObject();
                    int score = json.get("score").getAsInt();
                    a.setAiScore(score);
                    a.setAiFeedback(result);
                    a.setStatus("REVIEWED");
                    serviceApp.modifier(a);
                    showInfo("AI Score: " + score + "/100");
                    refreshApplications();
                } catch (Exception ex) {
                    // Couldn't parse JSON, store raw feedback
                    a.setAiFeedback(result);
                    try { serviceApp.modifier(a); } catch (Exception ignored) {}
                    showInfo("AI feedback received (raw):\n" + result.substring(0, Math.min(200, result.length())));
                }
            });
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

                if (isOwnerOrAdmin && "DRAFT".equals(c.getStatus())) {
                    Button btnActivate = new Button("Activate");
                    btnActivate.getStyleClass().add("oc-btn-primary");
                    btnActivate.setOnAction(e -> {
                        try {
                            c.setStatus("ACTIVE");
                            serviceContract.modifier(c);
                            refreshContracts();
                        } catch (SQLException ex) { showError(ex.getMessage()); }
                    });
                    box.getChildren().add(0, btnActivate);
                }

                setGraphic(box);
                setText(null);
            }
        });
    }

    private void refreshContracts() {
        try {
            List<Contract> contracts;
            if (isOwnerOrAdmin) {
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
    // QR CODE VERIFICATION DIALOG (HR)
    // ================================================================

    private void showQrVerificationDialog(Contract contract) {
        Dialog<ButtonType> dialog = new Dialog<>();
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
        typeDialog.setTitle("Draft Email");
        typeDialog.setHeaderText("What type of email?");
        typeDialog.showAndWait().ifPresent(type -> {
            ChoiceDialog<String> styleDialog = new ChoiceDialog<>("Professional", "Professional", "Friendly", "Direct");
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
        dialog.setTitle("Enhance Description");
        dialog.setHeaderText("Enter bullet points for the offer description:");
        dialog.getEditor().setPrefWidth(400);
        dialog.showAndWait().ifPresent(bullets -> {
            TextInputDialog titleDialog = new TextInputDialog();
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
