package controllers;

import entities.Interview;
import entities.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import services.ServiceInterview;
import services.ServiceUser;
import utils.SessionManager;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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

    // Filter
    @FXML private TextField searchField;
    @FXML private ComboBox<String> filterStatusCombo;
    @FXML private Label countLabel;

    // Cards
    @FXML private FlowPane cardsPane;

    private ServiceInterview serviceInterview = new ServiceInterview();
    private ServiceUser serviceUser = new ServiceUser();
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
        }

        loadUsers();
        setupCandidateAutocomplete();
        setupDatePicker();
        loadInterviews();
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
                nameToId.put(display, u.getId());
                idToName.put(u.getId(), display);
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
            if ("GIG_WORKER".equals(currentRole)) {
                // GIG_WORKER only sees interviews where they are the candidate
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

        Timestamp ts = Timestamp.valueOf(date.atTime(time));
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
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Scheduling Conflict");
                            alert.setHeaderText("Duplicate Interview");
                            alert.setContentText("This candidate already has an interview at " + time + " on " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + ".");
                            alert.showAndWait();
                            return;
                        }
                        // Same day, different time — also block
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Scheduling Conflict");
                        alert.setHeaderText("Duplicate Interview");
                        alert.setContentText("This candidate already has an interview on " + date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy")) + " at " + existingTime + ". A candidate cannot have two interviews on the same day.");
                        alert.showAndWait();
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
                showFormStatus("Interview updated successfully!", false);
            } else {
                // CREATE
                Interview newInterview = new Interview(organizerId, candId, ts, link);
                newInterview.setStatus(status);
                serviceInterview.ajouter(newInterview);
                showFormStatus("Interview scheduled successfully!", false);
            }

            loadInterviews();

            // Auto-hide form after brief delay
            javafx.application.Platform.runLater(() -> {
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(this::cancelForm);
            });

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

    private void deleteInterview(Interview interview) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete interview #" + interview.getId() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Delete");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    serviceInterview.supprimer(interview.getId());
                    loadInterviews();
                } catch (SQLException e) {
                    System.err.println("Failed to delete: " + e.getMessage());
                }
            }
        });
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

            Separator sep3 = new Separator();
            sep3.getStyleClass().add("card-separator");

            // Action buttons — only HR_MANAGER gets full controls
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_LEFT);

            if ("HR_MANAGER".equals(currentRole)) {
                Button editBtn = new Button("Edit");
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding: 6 16;");
                editBtn.setOnAction(e -> openEditForm(interview));

                Button deleteBtn = new Button("Delete");
                deleteBtn.getStyleClass().add("btn-secondary");
                deleteBtn.setStyle("-fx-padding: 6 16; -fx-text-fill: #ef4444;");
                deleteBtn.setOnAction(e -> deleteInterview(interview));

                Region actionSpacer = new Region();
                HBox.setHgrow(actionSpacer, Priority.ALWAYS);

                // Quick status buttons
                if ("PENDING".equals(interview.getStatus())) {
                    Button acceptBtn = new Button("Accept");
                    acceptBtn.getStyleClass().add("btn-primary");
                    acceptBtn.setStyle("-fx-padding: 6 14; -fx-background-color: linear-gradient(to right, #059669, #10b981);");
                    acceptBtn.setOnAction(e -> updateStatus(interview, "ACCEPTED"));

                    Button rejectBtn = new Button("Reject");
                    rejectBtn.getStyleClass().add("btn-primary");
                    rejectBtn.setStyle("-fx-padding: 6 14; -fx-background-color: linear-gradient(to right, #dc2626, #b91c1c);");
                    rejectBtn.setOnAction(e -> updateStatus(interview, "REJECTED"));

                    actions.getChildren().addAll(editBtn, deleteBtn, actionSpacer, acceptBtn, rejectBtn);
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

            content.getChildren().addAll(dateRow, sep1, peopleRow, sep2, linkRow, sep3, actions);
            card.getChildren().addAll(header, content);
            cardsPane.getChildren().add(card);
        }
    }

    private void updateStatus(Interview interview, String newStatus) {
        try {
            interview.setStatus(newStatus);
            serviceInterview.modifier(interview);
            loadInterviews();
        } catch (SQLException e) {
            System.err.println("Failed to update status: " + e.getMessage());
        }
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
}
