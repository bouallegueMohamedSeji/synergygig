package controllers;

import entities.Interview;
import entities.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import services.ServiceInterview;
import services.ServiceUser;
import services.ServiceJobApplication;
import services.ServiceContract;
import services.ServiceOffer;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import utils.AnimatedButton;
import utils.AppThreadPool;
import utils.CardEffects;
import utils.SessionManager;
import utils.StyledAlert;
import utils.SoundManager;
import javafx.stage.Window;

import java.io.File;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.scene.control.Alert;

public class InterviewController {

    // Form card
    @FXML private VBox formCard;
    @FXML private Label formTitle;
    @FXML private Label formSubtitle;
    @FXML private ComboBox<String> candidateCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private DatePicker datePicker;
    @FXML private TextField timeField;
    @FXML private TextField linkField;
    @FXML private Label formStatus;
    @FXML private Button btnSave;
    @FXML private Button btnSchedule;
    @FXML private HBox headerRow;

    // Filter
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterStatusCombo;
    @FXML private Label countLabel;

    // Cards
    @FXML private FlowPane cardsPane;

    private ServiceInterview serviceInterview = new ServiceInterview();
    private ServiceUser serviceUser = new ServiceUser();
    private services.ServiceNotification serviceNotification = new services.ServiceNotification();
    private List<Interview> allInterviews;
    private Interview editingInterview = null; // null = creating new
    private String currentRole = "";
    private int currentUserId = -1;

    // User name lookup  (display name → userId)  and  (userId → display name)
    private Map<String, Integer> nameToId = new HashMap<>();
    private Map<Integer, String> idToName = new HashMap<>();
    private ObservableList<String> allCandidateNames = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentRole = currentUser.getRole();
            currentUserId = currentUser.getId();
        }

        statusCombo.setItems(FXCollections.observableArrayList("PENDING", "ACCEPTED", "REJECTED"));
        statusCombo.setValue("PENDING");

        filterStatusCombo.setItems(FXCollections.observableArrayList("ALL", "PENDING", "ACCEPTED", "REJECTED"));
        filterStatusCombo.setValue("ALL");

        // Only HR_MANAGER can schedule new interviews
        if (!"HR_MANAGER".equals(currentRole)) {
            btnSchedule.setManaged(false);
            btnSchedule.setVisible(false);
        } else {
            // Replace static button with animated version
            btnSchedule.setManaged(false);
            btnSchedule.setVisible(false);
            StackPane animSchedule = AnimatedButton.createPrimary(
                    "+ Schedule New", "📅", e -> toggleForm());
            animSchedule.setMinWidth(170);
            animSchedule.setMaxHeight(38);

            StackPane animAiQuestions = AnimatedButton.createSecondary(
                    "🤖 AI Questions", "💡", e -> showAiQuestionsDialog());
            animAiQuestions.setMinWidth(150);
            animAiQuestions.setMaxHeight(38);

            if (headerRow != null) {
                headerRow.getChildren().addAll(animAiQuestions, animSchedule);
            }
        }

        setupCandidateAutocomplete();
        setupDatePicker();

        // Load users and interviews asynchronously
        AppThreadPool.io(() -> {
            // loadUsers populates nameToId, idToName, allCandidateNames
            try {
                List<User> users = serviceUser.recuperer();
                List<Interview> interviews;
                if ("GIG_WORKER".equals(currentRole) || "EMPLOYEE".equals(currentRole)) {
                    interviews = serviceInterview.getByCandidate(currentUserId);
                } else {
                    interviews = serviceInterview.recuperer();
                }
                Platform.runLater(() -> {
                    nameToId.clear();
                    idToName.clear();
                    allCandidateNames.clear();
                    for (User u : users) {
                        String display = u.getFirstName() + " " + u.getLastName();
                        idToName.put(u.getId(), display);
                        String role2 = u.getRole();
                        if ("HR_MANAGER".equals(role2) || "ADMIN".equals(role2)) continue;
                        nameToId.put(display, u.getId());
                        allCandidateNames.add(display);
                    }
                    candidateCombo.setItems(allCandidateNames);
                    allInterviews = interviews;
                    applyFilters();
                });
            } catch (SQLException e) {
                System.err.println("Failed to load data: " + e.getMessage());
            }
        });
    }

    // ========== DatePicker Formatter ==========

    private void setupDatePicker() {
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("MMMM d, yyyy");
        datePicker.setConverter(new javafx.util.StringConverter<LocalDate>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? date.format(displayFmt) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                if (string == null || string.isEmpty()) return null;
                try {
                    return LocalDate.parse(string, displayFmt);
                } catch (Exception e) {
                    return null;
                }
            }
        });

        // Disable past dates in the DatePicker
        datePicker.setDayCellFactory(picker -> new javafx.scene.control.DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (date.isBefore(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #555; -fx-opacity: 0.5;");
                }
            }
        });
    }

    // ========== User Lookup ==========

    private void loadUsers() {
        try {
            List<User> users = serviceUser.recuperer();
            nameToId.clear();
            idToName.clear();
            allCandidateNames.clear();

            for (User u : users) {
                String display = u.getFirstName() + " " + u.getLastName();
                // Always build the idToName lookup (for card display of organizers)
                idToName.put(u.getId(), display);

                // Only non-HR, non-ADMIN users can be candidates for interviews
                String role = u.getRole();
                if ("HR_MANAGER".equals(role) || "ADMIN".equals(role)) {
                    continue; // skip as selectable candidates
                }
                nameToId.put(display, u.getId());
                allCandidateNames.add(display);
            }
            candidateCombo.setItems(allCandidateNames);
        } catch (SQLException e) {
            System.err.println("Failed to load users: " + e.getMessage());
        }
    }

    private void setupCandidateAutocomplete() {
        candidateCombo.getEditor().addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            String typed = candidateCombo.getEditor().getText();
            if (typed == null || typed.isEmpty()) {
                candidateCombo.setItems(allCandidateNames);
                candidateCombo.show();
                return;
            }
            String lower = typed.toLowerCase();
            ObservableList<String> filtered = allCandidateNames.stream()
                    .filter(name -> name.toLowerCase().contains(lower))
                    .collect(Collectors.toCollection(FXCollections::observableArrayList));
            candidateCombo.setItems(filtered);
            if (!filtered.isEmpty()) {
                candidateCombo.show();
            }
        });
    }

    private void loadInterviews() {
        try {
            if ("GIG_WORKER".equals(currentRole) || "EMPLOYEE".equals(currentRole)) {
                // GIG_WORKER / EMPLOYEE only sees interviews where they are the candidate
                allInterviews = serviceInterview.getByCandidate(currentUserId);
            } else {
                allInterviews = serviceInterview.recuperer();
            }
            applyFilters();
        } catch (SQLException e) {
            System.err.println("Failed to load interviews: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        applyFilters();
    }

    private void applyFilters() {
        if (allInterviews == null) return;

        String query = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String statusFilter = filterStatusCombo.getValue();

        List<Interview> filtered = allInterviews.stream()
                .filter(i -> {
                    boolean matchSearch = query.isEmpty()
                            || String.valueOf(i.getId()).contains(query)
                            || (idToName.containsKey(i.getCandidateId()) && idToName.get(i.getCandidateId()).toLowerCase().contains(query))
                            || (idToName.containsKey(i.getOrganizerId()) && idToName.get(i.getOrganizerId()).toLowerCase().contains(query))
                            || (i.getMeetLink() != null && i.getMeetLink().toLowerCase().contains(query));
                    boolean matchStatus = "ALL".equals(statusFilter) || i.getStatus().equals(statusFilter);
                    return matchSearch && matchStatus;
                })
                .collect(Collectors.toList());

        countLabel.setText(filtered.size() + " interview" + (filtered.size() != 1 ? "s" : ""));
        buildCards(filtered);
    }

    // ========== Form Toggle ==========

    @FXML
    private void toggleForm() {
        editingInterview = null;
        formTitle.setText("Schedule Interview");
        formSubtitle.setText("Create a new interview session");
        btnSave.setText("Schedule");
        clearForm();
        candidateCombo.setDisable(false);
        statusCombo.setValue("PENDING");
        formCard.setManaged(true);
        formCard.setVisible(true);
    }

    @FXML
    private void cancelForm() {
        formCard.setManaged(false);
        formCard.setVisible(false);
        editingInterview = null;
        clearForm();
        hideFormStatus();
    }

    private void clearForm() {
        candidateCombo.setValue(null);
        candidateCombo.getEditor().setText("");
        datePicker.setValue(null);
        timeField.setText("");
        linkField.setText("");
        hideFormStatus();
    }

    // ========== Save (Create / Update) ==========

    @FXML
    private void handleSave() {
        String selectedName = candidateCombo.getValue();
        if (selectedName == null || selectedName.trim().isEmpty()) {
            selectedName = candidateCombo.getEditor().getText();
        }
        LocalDate date = datePicker.getValue();
        String timeText = timeField.getText().trim();
        String link = linkField.getText().trim();
        String status = statusCombo.getValue();

        // Validation
        if (selectedName == null || selectedName.trim().isEmpty() || date == null || timeText.isEmpty()) {
            showFormStatus("Please fill in Candidate, Date, and Time.", true);
            return;
        }

        Integer candId = nameToId.get(selectedName.trim());
        if (candId == null) {
            showFormStatus("Candidate not found. Please select a valid name.", true);
            return;
        }

        LocalTime time;
        try {
            time = LocalTime.parse(timeText);
        } catch (Exception e) {
            showFormStatus("Time must be in HH:MM format (e.g. 14:30).", true);
            return;
        }

        // ===== Prevent scheduling in the past =====
        java.time.LocalDateTime scheduledDateTime = date.atTime(time);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (scheduledDateTime.isBefore(now)) {
            String reason;
            if (date.isBefore(LocalDate.now())) {
                reason = "The date " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + " is in the past.";
            } else {
                reason = "The time " + time.format(DateTimeFormatter.ofPattern("HH:mm")) +
                         " has already passed today. Current time is " +
                         LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) + ".";
            }
            utils.StyledAlert.show(interviewOwnerWindow(), "Invalid Date/Time",
                    reason + "\n\nPlease select a future date and time.", "warning");
            return;
        }

        Timestamp ts = Timestamp.valueOf(scheduledDateTime);
        int organizerId = SessionManager.getInstance().getCurrentUser().getId();

        // ===== Duplicate interview check =====
        try {
            List<Interview> candidateInterviews = serviceInterview.getByCandidate(candId);
            for (Interview existing : candidateInterviews) {
                // Skip the interview being edited
                if (editingInterview != null && existing.getId() == editingInterview.getId()) continue;
                if (existing.getDateTime() != null) {
                    LocalDate existingDate = existing.getDateTime().toLocalDateTime().toLocalDate();
                    LocalTime existingTime = existing.getDateTime().toLocalDateTime().toLocalTime();
                    if (existingDate.equals(date)) {
                        if (existingTime.equals(time)) {
                            utils.StyledAlert.show(interviewOwnerWindow(), "Scheduling Conflict",
                                    "This candidate already has an interview at " + time + " on " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + ".", "error");
                            return;
                        }
                        // Same day, different time — also block
                        utils.StyledAlert.show(interviewOwnerWindow(), "Scheduling Conflict",
                                "This candidate already has an interview on " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + " at " + existingTime + ". A candidate cannot have two interviews on the same day.", "error");
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            showFormStatus("Error checking conflicts: " + e.getMessage(), true);
            return;
        }

        try {
            if (editingInterview != null) {
                // UPDATE
                editingInterview.setDateTime(ts);
                editingInterview.setStatus(status);
                editingInterview.setMeetLink(link);
                serviceInterview.modifier(editingInterview);
                SoundManager.getInstance().play(SoundManager.INTERVIEW_SCHEDULED);
                showFormStatus("Interview updated successfully!", false);
            } else {
                // CREATE
                Interview newInterview = new Interview(organizerId, candId, ts, link);
                newInterview.setStatus(status);

                // Try to auto-link to an existing SHORTLISTED application for this candidate
                try {
                    ServiceJobApplication svcApp = new ServiceJobApplication();
                    List<entities.JobApplication> candidateApps = svcApp.recuperer().stream()
                            .filter(a -> a.getApplicantId() == candId && "SHORTLISTED".equals(a.getStatus()))
                            .collect(java.util.stream.Collectors.toList());
                    if (candidateApps.size() == 1) {
                        newInterview.setApplicationId(candidateApps.get(0).getId());
                        newInterview.setOfferId(candidateApps.get(0).getOfferId());
                        System.out.println("[Interview] Auto-linked to application #" + candidateApps.get(0).getId());
                    } else if (candidateApps.size() > 1) {
                        System.out.println("[Interview] ⚠ Multiple SHORTLISTED applications for candidate #" + candId + " — not auto-linking (count=" + candidateApps.size() + ")");
                    }
                } catch (Exception linkEx) {
                    System.err.println("[Interview] Auto-link check failed: " + linkEx.getMessage());
                }

                // Notify the candidate
                String candName = idToName.getOrDefault(candId, "User #" + candId);
                String dateStr = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) + " at " + time;
                serviceNotification.notifyInterview(candId, "Scheduled",
                        "You have an interview scheduled for " + dateStr + ".", newInterview.getId());

                serviceInterview.ajouter(newInterview);
                SoundManager.getInstance().play(SoundManager.INTERVIEW_SCHEDULED);
                showFormStatus("Interview scheduled successfully!", false);
            }

            loadInterviews();

            // Auto-hide form after brief delay
            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(e -> cancelForm());
            pause.play();

        } catch (SQLException e) {
            showFormStatus("Error: " + e.getMessage(), true);
        }
    }

    // ========== Edit ==========

    private void openEditForm(Interview interview) {
        editingInterview = interview;
        formTitle.setText("Edit Interview #" + interview.getId());
        formSubtitle.setText("Update interview details");
        btnSave.setText("Save Changes");

        candidateCombo.setValue(idToName.getOrDefault(interview.getCandidateId(), "User #" + interview.getCandidateId()));
        candidateCombo.setDisable(true);

        statusCombo.setValue(interview.getStatus());

        if (interview.getDateTime() != null) {
            java.time.LocalDateTime ldt = interview.getDateTime().toLocalDateTime();
            datePicker.setValue(ldt.toLocalDate());
            timeField.setText(String.format("%02d:%02d", ldt.getHour(), ldt.getMinute()));
        }

        linkField.setText(interview.getMeetLink() != null ? interview.getMeetLink() : "");

        formCard.setManaged(true);
        formCard.setVisible(true);
    }

    // ========== Delete ==========

    private Window interviewOwnerWindow() {
        return cardsPane != null && cardsPane.getScene() != null
                ? cardsPane.getScene().getWindow() : null;
    }

    private void deleteInterview(Interview interview) {
        if (utils.StyledAlert.confirm(interviewOwnerWindow(), "Confirm Delete",
                "Delete interview #" + interview.getId() + "?")) {
            try {
                serviceInterview.supprimer(interview.getId());
                SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                loadInterviews();
            } catch (SQLException e) {
                System.err.println("Failed to delete: " + e.getMessage());
            }
        }
    }

    // ========== Card Builder ==========

    private void buildCards(List<Interview> interviews) {
        cardsPane.getChildren().clear();

        if (interviews.isEmpty()) {
            Label empty = new Label("No interviews found");
            empty.getStyleClass().add("content-subtitle");
            empty.setStyle("-fx-padding: 40;");
            cardsPane.getChildren().add(empty);
            return;
        }

        SimpleDateFormat dateFmt = new SimpleDateFormat("MMM dd, yyyy");
        SimpleDateFormat timeFmt = new SimpleDateFormat("hh:mm a");

        for (Interview interview : interviews) {
            VBox card = new VBox(0);
            card.getStyleClass().add("dashboard-card");
            card.setPrefWidth(320);
            card.setMinWidth(290);
            card.setMaxWidth(350);

            // -- Header --
            VBox header = new VBox(2);
            header.getStyleClass().add("dashboard-card-header");

            HBox headerRow = new HBox(8);
            headerRow.setAlignment(Pos.CENTER_LEFT);

            Label idLabel = new Label("Interview #" + interview.getId());
            idLabel.getStyleClass().add("dashboard-card-title");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label statusBadge = new Label(interview.getStatus());
            statusBadge.getStyleClass().add("topbar-role-badge");
            String badgeColor = switch (interview.getStatus()) {
                case "ACCEPTED" -> "-fx-background-color: linear-gradient(to right, #059669, #10b981);";
                case "REJECTED" -> "-fx-background-color: linear-gradient(to right, #dc2626, #ef4444);";
                default -> "";
            };
            if (!badgeColor.isEmpty()) statusBadge.setStyle(badgeColor);

            headerRow.getChildren().addAll(idLabel, spacer, statusBadge);
            header.getChildren().add(headerRow);

            // -- Content --
            VBox content = new VBox(10);
            content.getStyleClass().add("dashboard-card-content");

            // Date & Time row
            HBox dateRow = new HBox(8);
            dateRow.setAlignment(Pos.CENTER_LEFT);
            Label calIcon = new Label("\uD83D\uDCC5");
            calIcon.setStyle("-fx-font-size: 15;");
            VBox dateInfo = new VBox(2);
            Label dateLabel = new Label("Date & Time");
            dateLabel.getStyleClass().add("stat-label");
            String dtText = interview.getDateTime() != null
                    ? dateFmt.format(interview.getDateTime()) + "  •  " + timeFmt.format(interview.getDateTime())
                    : "-";
            Label dateValue = new Label(dtText);
            dateValue.getStyleClass().add("account-meta");
            dateValue.setStyle("-fx-font-size: 13;");
            dateInfo.getChildren().addAll(dateLabel, dateValue);
            dateRow.getChildren().addAll(calIcon, dateInfo);

            Separator sep1 = new Separator();
            sep1.getStyleClass().add("card-separator");

            // Organizer & Candidate row
            HBox peopleRow = new HBox(16);
            peopleRow.setAlignment(Pos.CENTER_LEFT);

            VBox orgCol = new VBox(2);
            Label orgLbl = new Label("Organizer");
            orgLbl.getStyleClass().add("stat-label");
            Label orgVal = new Label(idToName.getOrDefault(interview.getOrganizerId(), "User #" + interview.getOrganizerId()));
            orgVal.getStyleClass().add("account-meta");
            orgVal.setStyle("-fx-font-size: 13;");
            orgCol.getChildren().addAll(orgLbl, orgVal);

            VBox candCol = new VBox(2);
            Label candLbl = new Label("Candidate");
            candLbl.getStyleClass().add("stat-label");
            Label candVal = new Label(idToName.getOrDefault(interview.getCandidateId(), "User #" + interview.getCandidateId()));
            candVal.getStyleClass().add("account-meta");
            candVal.setStyle("-fx-font-size: 13;");
            candCol.getChildren().addAll(candLbl, candVal);

            peopleRow.getChildren().addAll(orgCol, candCol);

            Separator sep2 = new Separator();
            sep2.getStyleClass().add("card-separator");

            // Meeting Link
            HBox linkRow = new HBox(8);
            linkRow.setAlignment(Pos.CENTER_LEFT);
            Label linkIcon = new Label("\uD83D\uDD17");
            linkIcon.setStyle("-fx-font-size: 15;");
            VBox linkInfo = new VBox(2);
            Label linkLbl = new Label("Meeting Link");
            linkLbl.getStyleClass().add("stat-label");
            String linkText = interview.getMeetLink() != null && !interview.getMeetLink().isEmpty()
                    ? interview.getMeetLink() : "No link provided";
            Label linkVal = new Label(linkText);
            linkVal.getStyleClass().add("account-meta");
            linkVal.setStyle("-fx-font-size: 12; -fx-wrap-text: true;");
            linkVal.setWrapText(true);
            linkVal.setMaxWidth(260);
            linkInfo.getChildren().addAll(linkLbl, linkVal);
            linkRow.getChildren().addAll(linkIcon, linkInfo);

            // ── Linked Application/Offer info ──
            VBox pipelineInfo = new VBox(2);
            if (interview.getApplicationId() > 0) {
                Label pipeLabel = new Label("📋 Linked to Application #" + interview.getApplicationId());
                pipeLabel.setStyle("-fx-text-fill: #8A8AFF; -fx-font-size: 11; -fx-font-weight: bold;");
                pipelineInfo.getChildren().add(pipeLabel);
                // Try to show offer name
                if (interview.getOfferId() > 0) {
                    try {
                        ServiceOffer svcOffer = new ServiceOffer();
                        entities.Offer offer = svcOffer.recuperer().stream()
                                .filter(o -> o.getId() == interview.getOfferId())
                                .findFirst().orElse(null);
                        if (offer != null) {
                            Label offerLabel = new Label("Position: " + offer.getTitle());
                            offerLabel.setStyle("-fx-text-fill: #facc15; -fx-font-size: 11;");
                            pipelineInfo.getChildren().add(offerLabel);
                        }
                    } catch (Exception ignored) {}
                }
            }

            Separator sep3 = new Separator();
            sep3.getStyleClass().add("card-separator");

            // Action buttons — only HR_MANAGER gets full controls
            HBox actions = new HBox(6);
            actions.setAlignment(Pos.CENTER_LEFT);

            if ("HR_MANAGER".equals(currentRole)) {
                StackPane editBtn = AnimatedButton.createSecondary(
                        "Edit", "✏", e -> openEditForm(interview));
                editBtn.getStyleClass().add("anim-btn-compact");
                editBtn.setMinWidth(62);
                editBtn.setMaxHeight(30);

                StackPane deleteBtn = AnimatedButton.create(
                        "Delete", "🗑", "btn-animated-danger", e -> deleteInterview(interview));
                deleteBtn.getStyleClass().add("anim-btn-compact");
                deleteBtn.setMinWidth(66);
                deleteBtn.setMaxHeight(30);

                // Quick status buttons
                if ("PENDING".equals(interview.getStatus())) {
                    StackPane acceptBtn = AnimatedButton.create(
                            "Accept", "✓", "btn-animated-success", e -> updateStatus(interview, "ACCEPTED"));
                    acceptBtn.getStyleClass().add("anim-btn-compact");
                    acceptBtn.setMinWidth(68);
                    acceptBtn.setMaxHeight(30);

                    StackPane rejectBtn = AnimatedButton.create(
                            "Reject", "✕", "btn-animated-danger", e -> updateStatus(interview, "REJECTED"));
                    rejectBtn.getStyleClass().add("anim-btn-compact");
                    rejectBtn.setMinWidth(68);
                    rejectBtn.setMaxHeight(30);

                    actions.getChildren().addAll(editBtn, deleteBtn, acceptBtn, rejectBtn);
                } else {
                    actions.getChildren().addAll(editBtn, deleteBtn);
                }
            } else {
                // Read-only: show a label instead of action buttons
                Label readOnlyLabel = new Label("View only");
                readOnlyLabel.getStyleClass().add("stat-label");
                readOnlyLabel.setStyle("-fx-font-size: 11; -fx-padding: 6 0;");
                actions.getChildren().add(readOnlyLabel);
            }

            content.getChildren().addAll(dateRow, sep1, peopleRow, sep2, linkRow);
            if (!pipelineInfo.getChildren().isEmpty()) {
                Separator sepPipeline = new Separator();
                sepPipeline.getStyleClass().add("card-separator");
                content.getChildren().addAll(sepPipeline, pipelineInfo);
            }
            content.getChildren().addAll(sep3, actions);
            card.getChildren().addAll(header, content);

            CardEffects.applyHoverEffect(card);
            cardsPane.getChildren().add(card);
        }
    }

    private void updateStatus(Interview interview, String newStatus) {
        try {
            interview.setStatus(newStatus);
            serviceInterview.modifier(interview);

            if ("ACCEPTED".equals(newStatus)) {
                SoundManager.getInstance().play(SoundManager.INTERVIEW_ACCEPTED);
            } else if ("REJECTED".equals(newStatus)) {
                SoundManager.getInstance().play(SoundManager.INTERVIEW_REJECTED);
            }

            // Notify the candidate about status change
            serviceNotification.notifyInterview(interview.getCandidateId(),
                    newStatus.substring(0, 1).toUpperCase() + newStatus.substring(1).toLowerCase(),
                    "Your interview has been " + newStatus.toLowerCase() + ".",
                    interview.getId());

            // ── Pipeline link: update the linked JobApplication ──
            System.out.println("[Interview] updateStatus called: interview #" + interview.getId()
                    + " -> " + newStatus + " | applicationId=" + interview.getApplicationId()
                    + " | offerId=" + interview.getOfferId() + " | candidateId=" + interview.getCandidateId());

            if (interview.getApplicationId() > 0 || "ACCEPTED".equals(newStatus)) {
                try {
                    ServiceJobApplication serviceApp = new ServiceJobApplication();
                    List<entities.JobApplication> apps = serviceApp.recuperer();

                    // Try direct link first, then fallback to candidate+offer lookup
                    entities.JobApplication linkedApp = null;
                    if (interview.getApplicationId() > 0) {
                        linkedApp = apps.stream()
                                .filter(a -> a.getId() == interview.getApplicationId())
                                .findFirst().orElse(null);
                    }
                    // Fallback: if applicationId was 0 (interview created from InterviewController form),
                    // find the SHORTLISTED application for this candidate
                    if (linkedApp == null && "ACCEPTED".equals(newStatus)) {
                        System.out.println("[Interview] ⚠ No applicationId linked — searching by candidateId=" + interview.getCandidateId());
                        linkedApp = apps.stream()
                                .filter(a -> a.getApplicantId() == interview.getCandidateId()
                                        && "SHORTLISTED".equals(a.getStatus()))
                                .findFirst().orElse(null);
                        if (linkedApp == null) {
                            // Also try PENDING/REVIEWED
                            linkedApp = apps.stream()
                                    .filter(a -> a.getApplicantId() == interview.getCandidateId()
                                            && !"REJECTED".equals(a.getStatus())
                                            && !"WITHDRAWN".equals(a.getStatus())
                                            && !"ACCEPTED".equals(a.getStatus()))
                                    .findFirst().orElse(null);
                        }
                        if (linkedApp != null) {
                            System.out.println("[Interview] Found application #" + linkedApp.getId() + " via fallback lookup (status=" + linkedApp.getStatus() + ")");
                            // Update the interview with the found applicationId for future reference
                            interview.setApplicationId(linkedApp.getId());
                            interview.setOfferId(linkedApp.getOfferId());
                            try { serviceInterview.modifier(interview); } catch (Exception ignored) {}
                        } else {
                            System.err.println("[Interview] ⚠ No matching application found for candidate #" + interview.getCandidateId());
                            showAlert(javafx.scene.control.Alert.AlertType.WARNING,
                                    "No Application Found",
                                    "Interview accepted, but no linked job application was found for this candidate.\n"
                                    + "No contract was created. Please create one manually from the Offers panel.");
                        }
                    }

                    final entities.JobApplication finalLinkedApp = linkedApp;
                    if (finalLinkedApp != null) {
                        if ("ACCEPTED".equals(newStatus)) {
                            finalLinkedApp.setStatus("ACCEPTED");
                            serviceApp.modifier(finalLinkedApp);
                            System.out.println("[Interview] Application #" + finalLinkedApp.getId() + " auto-ACCEPTED via interview pipeline.");

                            // Trigger contract creation
                            Platform.runLater(() -> {
                                try {
                                    ServiceContract serviceContract = new ServiceContract();
                                    ServiceOffer serviceOffer = new ServiceOffer();
                                    ServiceUser svcUser = new ServiceUser();
                                    services.ZAIService zaiService = new services.ZAIService();
                                    entities.Offer offer = serviceOffer.recuperer().stream()
                                            .filter(o -> o.getId() == finalLinkedApp.getOfferId())
                                            .findFirst().orElse(null);

                                    if (offer == null) {
                                        System.err.println("[Interview] ⚠ Offer not found for application #" + finalLinkedApp.getId() + " (offerId=" + finalLinkedApp.getOfferId() + ")");
                                        showAlert(javafx.scene.control.Alert.AlertType.WARNING,
                                                "Contract Not Created",
                                                "The offer for application #" + finalLinkedApp.getId() + " was not found.\nThe application was accepted but no contract was generated.\nPlease create the contract manually from the Offers panel.");
                                        return;
                                    }

                                    // Generate AI contract terms (with fallback)
                                    String terms;
                                    try {
                                        terms = zaiService.generateContract(
                                                offer.getTitle(),
                                                offer.getDescription() != null ? offer.getDescription() : offer.getTitle(),
                                                offer.getAmount(),
                                                getUserDisplayName(finalLinkedApp.getApplicantId()),
                                                java.time.LocalDate.now().toString(),
                                                java.time.LocalDate.now().plusMonths(6).toString());
                                    } catch (Exception aiEx) {
                                        System.err.println("[Interview] AI contract gen failed, using template: " + aiEx.getMessage());
                                        terms = "Employment contract for " + offer.getTitle()
                                                + "\nParties: " + getUserDisplayName(offer.getOwnerId()) + " (Employer) and "
                                                + getUserDisplayName(finalLinkedApp.getApplicantId()) + " (Employee)"
                                                + "\nCompensation: " + offer.getCurrency() + " " + offer.getAmount()
                                                + "\n\n[Terms to be finalized]";
                                    }

                                    entities.Contract c = new entities.Contract(
                                            offer.getId(), finalLinkedApp.getApplicantId(), offer.getOwnerId(),
                                            terms, offer.getAmount(), offer.getCurrency(),
                                            "DRAFT", offer.getStartDate(), offer.getEndDate()
                                    );
                                    String hash = utils.BlockchainVerifier.generateHash(0, "Contract from hiring pipeline", offer.getAmount());
                                    c.setBlockchainHash(hash);
                                    serviceContract.ajouter(c);
                                    System.out.println("[Interview] ✅ Contract #" + c.getId() + " created for application #" + finalLinkedApp.getId());

                                    // Notify applicant about contract
                                    serviceNotification.notifyContractReady(
                                            finalLinkedApp.getApplicantId(),
                                            getUserDisplayName(finalLinkedApp.getApplicantId()),
                                            offer.getTitle(), c.getId());

                                    // Show immediate feedback
                                    showAlert(javafx.scene.control.Alert.AlertType.INFORMATION,
                                            "✅ Contract Created",
                                            "Contract #" + c.getId() + " created (DRAFT).\n"
                                            + "Blockchain: " + utils.BlockchainVerifier.shortHash(hash) + "\n\n"
                                            + "PDF generation & email sending in progress...");

                                    // Generate PDF + email in background
                                    String applicantName = getUserDisplayName(finalLinkedApp.getApplicantId());
                                    String ownerName = getUserDisplayName(offer.getOwnerId());
                                    final entities.Offer finalOffer = offer;
                                    AppThreadPool.io(() -> {
                                        try {
                                            File pdf = utils.ContractPdfGenerator.generatePdf(c, finalOffer.getTitle(), ownerName, applicantName);
                                            entities.User applicant = svcUser.getById(finalLinkedApp.getApplicantId());
                                            if (applicant == null || applicant.getEmail() == null) {
                                                System.err.println("[Interview] ⚠ Cannot send contract email: applicant or email is null (userId=" + finalLinkedApp.getApplicantId() + ")");
                                                Platform.runLater(() -> showAlert(javafx.scene.control.Alert.AlertType.WARNING,
                                                        "Email Not Sent",
                                                        "Contract #" + c.getId() + " was created, but the applicant has no email address on file.\n"
                                                        + "The contract is available in the Contracts tab."));
                                                return;
                                            }
                                            utils.EmailService.sendContractEmail(
                                                    applicant.getEmail(), applicant.getFirstName(),
                                                    ownerName, finalOffer.getTitle(),
                                                    finalOffer.getCurrency(), finalOffer.getAmount(), hash, pdf);
                                            System.out.println("[Interview] ✅ Contract email sent to " + applicant.getEmail());
                                        } catch (Exception ex) {
                                            System.err.println("[Interview] ❌ Contract email/PDF failed: " + ex.getMessage());
                                            ex.printStackTrace();
                                            Platform.runLater(() -> showAlert(javafx.scene.control.Alert.AlertType.ERROR,
                                                    "Contract Email Failed",
                                                    "Contract #" + c.getId() + " was created, but the email could not be sent.\n\n"
                                                    + "Error: " + ex.getMessage() + "\n\n"
                                                    + "The contract is still available in the Contracts tab."));
                                        }
                                    });
                                } catch (Exception ex) {
                                    System.err.println("[Interview] ❌ Contract creation failed: " + ex.getMessage());
                                    ex.printStackTrace();
                                    showAlert(javafx.scene.control.Alert.AlertType.ERROR,
                                            "Contract Creation Failed",
                                            "The interview was accepted but the contract could not be created.\n\n"
                                            + "Error: " + ex.getMessage() + "\n\n"
                                            + "Please create the contract manually from the Offers panel.");
                                }
                            });
                        } else if ("REJECTED".equals(newStatus)) {
                            finalLinkedApp.setStatus("REJECTED");
                            serviceApp.modifier(finalLinkedApp);
                            System.out.println("[Interview] Application #" + finalLinkedApp.getId() + " auto-REJECTED via interview pipeline.");

                            // Notify applicant
                            serviceNotification.notifyInterview(finalLinkedApp.getApplicantId(), "Rejected",
                                    "Your application has been rejected following the interview.", 0);
                        }
                    }
                } catch (SQLException appEx) {
                    System.err.println("[Interview] Failed to update linked application: " + appEx.getMessage());
                }
            }

            loadInterviews();
        } catch (SQLException e) {
            System.err.println("Failed to update status: " + e.getMessage());
        }
    }

    /** Helper to get display name from userId. */
    private String getUserDisplayName(int userId) {
        String name = idToName.get(userId);
        if (name != null) return name;
        try {
            entities.User u = serviceUser.getById(userId);
            if (u != null) return u.getFirstName() + " " + u.getLastName();
        } catch (Exception ignored) {}
        return "User #" + userId;
    }

    // ========== Alerts ==========

    private void showAlert(javafx.scene.control.Alert.AlertType type, String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        utils.DialogHelper.theme(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // ========== Form Status ==========

    private void showFormStatus(String message, boolean isError) {
        formStatus.setText(message);
        formStatus.setManaged(true);
        formStatus.setVisible(true);
        formStatus.getStyleClass().removeAll("error-label", "success-label");
        formStatus.getStyleClass().add(isError ? "error-label" : "success-label");
    }

    private void hideFormStatus() {
        formStatus.setManaged(false);
        formStatus.setVisible(false);
    }

    // ========== AI Interview Questions ==========

    private void showAiQuestionsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("AI Interview Questions Generator");
        dialog.setResizable(true);

        utils.DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("pm-dialog-pane");
        pane.getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(12);
        content.setPadding(new javafx.geometry.Insets(20));
        content.setPrefWidth(500);

        Label header = new Label("🤖 AI Interview Questions");
        header.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: white;");

        Label desc = new Label("Enter the position and department to generate tailored interview questions.");
        desc.setStyle("-fx-text-fill: #aaa; -fx-font-size: 12;");
        desc.setWrapText(true);

        TextField positionField = new TextField();
        positionField.setPromptText("Position (e.g. Software Developer)");
        positionField.getStyleClass().add("pm-form-control");

        TextField departmentField = new TextField();
        departmentField.setPromptText("Department (e.g. Engineering)");
        departmentField.getStyleClass().add("pm-form-control");

        ComboBox<String> difficultyBox = new ComboBox<>();
        difficultyBox.getItems().addAll("junior", "intermediate", "senior");
        difficultyBox.setValue("intermediate");
        difficultyBox.getStyleClass().add("pm-form-control");

        Button generateBtn = new Button("Generate Questions");
        generateBtn.getStyleClass().add("pm-dialog-ok-btn");

        TextArea resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(15);
        resultArea.getStyleClass().add("pm-form-control");
        resultArea.setPromptText("Questions will appear here...");

        generateBtn.setOnAction(ev -> {
            String position = positionField.getText().trim();
            String department = departmentField.getText().trim();
            if (position.isEmpty()) {
                resultArea.setText("Please enter a position.");
                return;
            }
            generateBtn.setDisable(true);
            generateBtn.setText("Generating...");
            resultArea.setText("⏳ Asking AI...");

            AppThreadPool.io(() -> {
                try {
                    java.util.Map<String, Object> body = new java.util.HashMap<>();
                    body.put("position", position);
                    body.put("department", department.isEmpty() ? "General" : department);
                    body.put("difficulty", difficultyBox.getValue());

                    com.google.gson.JsonElement resp = utils.ApiClient.post("/ai/interview-questions", body);
                    if (resp != null && resp.isJsonObject()) {
                        com.google.gson.JsonArray questions = resp.getAsJsonObject().getAsJsonArray("questions");
                        if (questions != null) {
                            StringBuilder sb = new StringBuilder();
                            int i = 1;
                            for (com.google.gson.JsonElement qEl : questions) {
                                com.google.gson.JsonObject q = qEl.getAsJsonObject();
                                sb.append(i++).append(". [").append(q.get("category").getAsString()).append("]\n");
                                sb.append("   ").append(q.get("question").getAsString()).append("\n");
                                sb.append("   💡 Tip: ").append(q.get("tip").getAsString()).append("\n\n");
                            }
                            javafx.application.Platform.runLater(() -> {
                                resultArea.setText(sb.toString());
                                generateBtn.setDisable(false);
                                generateBtn.setText("Generate Questions");
                            });
                            return;
                        }
                    }
                    javafx.application.Platform.runLater(() -> {
                        resultArea.setText("AI returned an empty response. Try again.");
                        generateBtn.setDisable(false);
                        generateBtn.setText("Generate Questions");
                    });
                } catch (Exception ex) {
                    javafx.application.Platform.runLater(() -> {
                        resultArea.setText("Error: " + ex.getMessage());
                        generateBtn.setDisable(false);
                        generateBtn.setText("Generate Questions");
                    });
                }
            });
        });

        content.getChildren().addAll(header, desc,
                new Label("Position") {{ getStyleClass().add("pm-form-label"); }}, positionField,
                new Label("Department") {{ getStyleClass().add("pm-form-label"); }}, departmentField,
                new Label("Difficulty") {{ getStyleClass().add("pm-form-label"); }}, difficultyBox,
                generateBtn, resultArea);

        pane.setContent(content);
        dialog.showAndWait();
    }
}
