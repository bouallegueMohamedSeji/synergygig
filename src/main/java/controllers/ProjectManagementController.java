package controllers;

import entities.Department;
import entities.Project;
import entities.Task;
import entities.User;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import services.ServiceDepartment;
import services.ServiceProject;
import services.ServiceProjectMember;
import services.ServiceNotification;
import services.ServiceTask;
import services.ServiceUser;
import utils.DialogHelper;
import utils.SessionManager;
import utils.SoundManager;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectManagementController {

    // ═══ Project List View ═══
    @FXML private VBox projectListView;
    @FXML private Label projectSubtitle;
    @FXML private TextField searchField;
    @FXML private Button btnNewProject;
    @FXML private Button filterAll, filterPlanning, filterInProgress, filterCompleted;
    @FXML private FlowPane projectGrid;

    // ═══ Project Detail View ═══
    @FXML private VBox projectDetailView;
    @FXML private Button btnBack, btnEditProject, btnDeleteProject, btnAddTask, btnAiPlan;
    @FXML private Label detailProjectName, detailStatusBadge, detailProjectDesc;
    @FXML private Label detailManager, detailDates, detailProgress, detailTaskCount, detailTeam;
    @FXML private ProgressBar detailProgressBar;
    @FXML private VBox colTodo, colInProgress, colDone;
    @FXML private Label countTodo, countInProgress, countDone;

    // ═══ Services ═══
    private final ServiceProject serviceProject = new ServiceProject();
    private final ServiceTask serviceTask = new ServiceTask();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceNotification serviceNotification = new ServiceNotification();

    // ═══ State ═══
    private User currentUser;
    private boolean isProjectOwner;  // PROJECT_OWNER — can create/edit/delete projects
    private boolean isAdmin;          // ADMIN or HR_MANAGER — can see ALL projects (read-only)
    private List<Project> allProjects = new ArrayList<>();
    private Map<Integer, User> userCache = new HashMap<>();
    private String activeFilter = "ALL";
    private Project currentProject;

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        String role = currentUser.getRole();

        isAdmin = "ADMIN".equals(role) || "HR_MANAGER".equals(role);
        isProjectOwner = "PROJECT_OWNER".equals(role);

        // Only PROJECT_OWNER can create new projects
        if (isProjectOwner) {
            btnNewProject.setVisible(true);
            btnNewProject.setManaged(true);
        }
        // Edit/delete/add-task are hidden at list level; shown per-project in openProjectDetail
        btnEditProject.setVisible(false);
        btnEditProject.setManaged(false);
        btnDeleteProject.setVisible(false);
        btnDeleteProject.setManaged(false);
        btnAddTask.setVisible(false);
        btnAddTask.setManaged(false);

        // Update subtitle based on role
        if (isAdmin) {
            projectSubtitle.setText("All projects across the organization");
        } else if ("PROJECT_OWNER".equals(role)) {
            projectSubtitle.setText("Your projects and tasks");
        } else {
            projectSubtitle.setText("Projects and tasks assigned to you");
        }

        loadUsers();
        loadProjects();
    }

    // ════════════════════════════════════════════════════════
    //  DATA LOADING
    // ════════════════════════════════════════════════════════

    private void loadUsers() {
        try {
            List<User> users = serviceUser.recuperer();
            for (User u : users) userCache.put(u.getId(), u);
        } catch (SQLException e) {
            System.err.println("⚠ Failed to load users: " + e.getMessage());
        }
    }

    private void loadProjects() {
        new Thread(() -> {
            try {
                List<Project> projects;
                if (isAdmin) {
                    projects = serviceProject.recuperer(); // all projects
                } else if ("PROJECT_OWNER".equals(currentUser.getRole())) {
                    projects = serviceProject.getByManager(currentUser.getId());
                } else {
                    // EMPLOYEE / GIG_WORKER — get projects that have tasks assigned to them
                    List<Task> myTasks = serviceTask.getByAssignee(currentUser.getId());
                    Set<Integer> projectIds = myTasks.stream()
                            .map(Task::getProjectId).collect(Collectors.toSet());
                    projects = serviceProject.recuperer().stream()
                            .filter(p -> projectIds.contains(p.getId()))
                            .collect(Collectors.toList());
                }
                Platform.runLater(() -> {
                    allProjects = projects;
                    renderProjectGrid();
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed to load projects: " + e.getMessage()));
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════
    //  PROJECT LIST RENDERING
    // ════════════════════════════════════════════════════════

    private void renderProjectGrid() {
        projectGrid.getChildren().clear();

        String query = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";

        List<Project> filtered = allProjects.stream()
                .filter(p -> {
                    if (!"ALL".equals(activeFilter)) {
                        if ("IN_PROGRESS".equals(activeFilter) && !"IN_PROGRESS".equals(p.getStatus())) return false;
                        if ("PLANNING".equals(activeFilter) && !"PLANNING".equals(p.getStatus())) return false;
                        if ("COMPLETED".equals(activeFilter) && !"COMPLETED".equals(p.getStatus())) return false;
                    }
                    if (!query.isEmpty()) {
                        return p.getName().toLowerCase().contains(query)
                                || (p.getDescription() != null && p.getDescription().toLowerCase().contains(query));
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            Label empty = new Label(allProjects.isEmpty() ? "No projects yet. Create your first project!" : "No matching projects.");
            empty.getStyleClass().add("pm-empty-label");
            projectGrid.getChildren().add(empty);
            return;
        }

        for (Project project : filtered) {
            projectGrid.getChildren().add(buildProjectCard(project));
        }
    }

    private VBox buildProjectCard(Project project) {
        VBox card = new VBox(10);
        card.getStyleClass().add("pm-project-card");
        card.setPrefWidth(320);
        card.setMinWidth(280);
        card.setMaxWidth(380);

        // Title + status badge
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(project.getName());
        name.getStyleClass().add("pm-card-title");
        name.setWrapText(true);
        Label statusBadge = createStatusBadge(project.getStatus());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleRow.getChildren().addAll(name, spacer, statusBadge);

        // Description
        Label desc = new Label(project.getDescription() != null && !project.getDescription().isEmpty()
                ? truncate(project.getDescription(), 100) : "No description");
        desc.getStyleClass().add("pm-card-desc");
        desc.setWrapText(true);

        // Manager
        User manager = userCache.get(project.getManagerId());
        String mgrName = manager != null ? manager.getFirstName() + " " + manager.getLastName() : "Unknown";
        Label mgrLabel = new Label("👤 " + mgrName);
        mgrLabel.getStyleClass().add("pm-card-meta");

        // Dates
        String dateStr = "";
        if (project.getStartDate() != null) dateStr += project.getStartDate().toString();
        if (project.getDeadline() != null) dateStr += " → " + project.getDeadline().toString();
        Label dateLabel = new Label(dateStr.isEmpty() ? "No dates set" : "📅 " + dateStr);
        dateLabel.getStyleClass().add("pm-card-meta");

        // Task progress bar
        int[] counts = getTaskCounts(project.getId());
        int total = counts[0] + counts[1] + counts[2];
        double progress = total > 0 ? (double) counts[2] / total : 0;

        ProgressBar progressBar = new ProgressBar(progress);
        progressBar.getStyleClass().add("pm-progress-bar");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        Label taskLabel = new Label(counts[2] + "/" + total + " tasks done");
        taskLabel.getStyleClass().add("pm-card-meta");

        card.getChildren().addAll(titleRow, desc, mgrLabel, dateLabel, progressBar, taskLabel);

        // Click to open detail
        card.setOnMouseClicked(e -> openProjectDetail(project));
        card.setCursor(javafx.scene.Cursor.HAND);

        return card;
    }

    /** Returns [todoCount, inProgressCount, doneCount] */
    private int[] getTaskCounts(int projectId) {
        try {
            List<Task> tasks = serviceTask.getByProject(projectId);
            int todo = 0, inProg = 0, done = 0;
            for (Task t : tasks) {
                switch (t.getStatus()) {
                    case "TODO": todo++; break;
                    case "IN_PROGRESS": inProg++; break;
                    case "DONE": done++; break;
                }
            }
            return new int[]{todo, inProg, done};
        } catch (SQLException e) {
            return new int[]{0, 0, 0};
        }
    }

    private Label createStatusBadge(String status) {
        Label badge = new Label(formatStatus(status));
        badge.getStyleClass().addAll("pm-status-badge", "pm-status-" + status.toLowerCase().replace("_", ""));
        return badge;
    }

    // ════════════════════════════════════════════════════════
    //  PROJECT DETAIL / TASK BOARD
    // ════════════════════════════════════════════════════════

    private void openProjectDetail(Project project) {
        this.currentProject = project;
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);

        // Switch views
        projectListView.setVisible(false);
        projectListView.setManaged(false);
        projectDetailView.setVisible(true);
        projectDetailView.setManaged(true);

        // Populate header
        detailProjectName.setText(project.getName());
        detailProjectDesc.setText(project.getDescription() != null ? project.getDescription() : "");
        detailStatusBadge.setText(formatStatus(project.getStatus()));
        detailStatusBadge.getStyleClass().removeIf(s -> s.startsWith("pm-status-") && !s.equals("pm-status-badge"));
        detailStatusBadge.getStyleClass().add("pm-status-" + project.getStatus().toLowerCase().replace("_", ""));

        User manager = userCache.get(project.getManagerId());
        detailManager.setText("Manager: " + (manager != null
                ? manager.getFirstName() + " " + manager.getLastName() : "Unknown"));

        String dateStr = "";
        if (project.getStartDate() != null) dateStr += project.getStartDate().toString();
        if (project.getDeadline() != null) dateStr += " → " + project.getDeadline().toString();
        detailDates.setText(dateStr.isEmpty() ? "" : "📅 " + dateStr);

        // Load team members
        new Thread(() -> {
            try {
                ServiceProjectMember spm = new ServiceProjectMember();
                List<ServiceProjectMember.ProjectMember> members = spm.getMembers(project.getId());
                String teamText = members.isEmpty() ? "No team members"
                        : "👥 Team: " + members.stream()
                            .map(m -> m.firstName + " " + m.lastName)
                            .collect(Collectors.joining(", "));
                Platform.runLater(() -> detailTeam.setText(teamText));
            } catch (SQLException ignored) {
                Platform.runLater(() -> detailTeam.setText(""));
            }
        }).start();

        // Only the project's own manager (PROJECT_OWNER) can edit/delete/add tasks
        boolean canManage = project.getManagerId() == currentUser.getId();
        btnEditProject.setVisible(canManage);
        btnEditProject.setManaged(canManage);
        btnDeleteProject.setVisible(canManage);
        btnDeleteProject.setManaged(canManage);
        btnAddTask.setVisible(canManage);
        btnAddTask.setManaged(canManage);
        btnAiPlan.setVisible(canManage);
        btnAiPlan.setManaged(canManage);

        loadTaskBoard();
    }

    private void loadTaskBoard() {
        new Thread(() -> {
            try {
                List<Task> tasks = serviceTask.getByProject(currentProject.getId());
                Platform.runLater(() -> renderKanban(tasks));
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed to load tasks: " + e.getMessage()));
            }
        }).start();
    }

    private void renderKanban(List<Task> tasks) {
        colTodo.getChildren().clear();
        colInProgress.getChildren().clear();
        colDone.getChildren().clear();

        int todo = 0, inProg = 0, done = 0;

        for (Task task : tasks) {
            VBox card = buildTaskCard(task);
            switch (task.getStatus()) {
                case "TODO":
                    colTodo.getChildren().add(card);
                    todo++;
                    break;
                case "IN_PROGRESS":
                    colInProgress.getChildren().add(card);
                    inProg++;
                    break;
                case "DONE":
                    colDone.getChildren().add(card);
                    done++;
                    break;
            }
        }

        countTodo.setText(String.valueOf(todo));
        countInProgress.setText(String.valueOf(inProg));
        countDone.setText(String.valueOf(done));

        int total = todo + inProg + done;
        double progress = total > 0 ? (double) done / total : 0;
        detailProgress.setText(Math.round(progress * 100) + "%");
        detailProgressBar.setProgress(progress);
        detailTaskCount.setText(total + " task" + (total != 1 ? "s" : ""));
    }

    private VBox buildTaskCard(Task task) {
        VBox card = new VBox(6);
        card.getStyleClass().add("pm-task-card");
        card.setPadding(new Insets(10, 12, 10, 12));

        // Title
        Label title = new Label(task.getTitle());
        title.getStyleClass().add("pm-task-title");
        title.setWrapText(true);

        // Priority badge
        Label priBadge = new Label(task.getPriority());
        priBadge.getStyleClass().addAll("pm-priority-badge", "pm-priority-" + task.getPriority().toLowerCase());

        // Assignee
        User assignee = task.getAssigneeId() > 0 ? userCache.get(task.getAssigneeId()) : null;
        Label assigneeLabel = new Label(assignee != null
                ? "👤 " + assignee.getFirstName() + " " + assignee.getLastName() : "Unassigned");
        assigneeLabel.getStyleClass().add("pm-task-meta");

        // Due date
        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.getChildren().add(priBadge);
        if (task.getDueDate() != null) {
            Label dueLabel = new Label("📅 " + task.getDueDate().toString());
            dueLabel.getStyleClass().add("pm-task-meta");
            boolean overdue = task.getDueDate().toLocalDate().isBefore(LocalDate.now())
                    && !"DONE".equals(task.getStatus());
            if (overdue) dueLabel.getStyleClass().add("pm-overdue");
            metaRow.getChildren().add(dueLabel);
        }

        card.getChildren().addAll(title, metaRow, assigneeLabel);

        // Context menu for status changes (for managers)
        boolean canManageTask = currentProject.getManagerId() == currentUser.getId();
        if (canManageTask) {
            ContextMenu ctx = new ContextMenu();
            if (!"TODO".equals(task.getStatus())) {
                MenuItem toTodo = new MenuItem("Move to To Do");
                toTodo.setOnAction(e -> changeTaskStatus(task, "TODO"));
                ctx.getItems().add(toTodo);
            }
            if (!"IN_PROGRESS".equals(task.getStatus())) {
                MenuItem toIP = new MenuItem("Move to In Progress");
                toIP.setOnAction(e -> changeTaskStatus(task, "IN_PROGRESS"));
                ctx.getItems().add(toIP);
            }
            if (!"DONE".equals(task.getStatus())) {
                MenuItem toDone = new MenuItem("Move to Done");
                toDone.setOnAction(e -> changeTaskStatus(task, "DONE"));
                ctx.getItems().add(toDone);
            }
            ctx.getItems().add(new SeparatorMenuItem());

            // Review task (PM only)
            if ("DONE".equals(task.getStatus())) {
                MenuItem reviewItem = new MenuItem("\u2B50 Review & Feedback");
                reviewItem.setOnAction(e -> showReviewDialog(task));
                ctx.getItems().add(reviewItem);
                ctx.getItems().add(new SeparatorMenuItem());
            }

            MenuItem editItem = new MenuItem("Edit Task");
            editItem.setOnAction(e -> showEditTaskDialog(task));
            ctx.getItems().add(editItem);

            MenuItem deleteItem = new MenuItem("Delete Task");
            deleteItem.setOnAction(e -> deleteTask(task));
            ctx.getItems().add(deleteItem);

            card.setOnContextMenuRequested(e -> ctx.show(card, e.getScreenX(), e.getScreenY()));
        } else if (task.getAssigneeId() == currentUser.getId()) {
            // Employee assigned to this task — can submit for review
            ContextMenu ctx = new ContextMenu();
            if (!"DONE".equals(task.getStatus())) {
                MenuItem submitItem = new MenuItem("\u2705 Submit for Review");
                submitItem.setOnAction(e -> submitTaskForReview(task));
                ctx.getItems().add(submitItem);
            }
            if (!"IN_PROGRESS".equals(task.getStatus())) {
                MenuItem startItem = new MenuItem("\u25B6 Start Working");
                startItem.setOnAction(e -> changeTaskStatus(task, "IN_PROGRESS"));
                ctx.getItems().add(startItem);
            }
            card.setOnContextMenuRequested(e -> ctx.show(card, e.getScreenX(), e.getScreenY()));
        }

        card.setCursor(javafx.scene.Cursor.HAND);
        return card;
    }

    private void changeTaskStatus(Task task, String newStatus) {
        new Thread(() -> {
            try {
                serviceTask.updateStatus(task.getId(), newStatus);
                task.setStatus(newStatus);
                Platform.runLater(this::loadTaskBoard);
                SoundManager.getInstance().play(SoundManager.TASK_MOVED);
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed to update status: " + e.getMessage()));
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════
    //  FILTER HANDLERS
    // ════════════════════════════════════════════════════════

    @FXML private void filterAll()        { setFilter("ALL", filterAll); }
    @FXML private void filterPlanning()   { setFilter("PLANNING", filterPlanning); }
    @FXML private void filterInProgress() { setFilter("IN_PROGRESS", filterInProgress); }
    @FXML private void filterCompleted()  { setFilter("COMPLETED", filterCompleted); }

    private void setFilter(String filter, Button activeBtn) {
        activeFilter = filter;
        for (Button b : List.of(filterAll, filterPlanning, filterInProgress, filterCompleted)) {
            b.getStyleClass().remove("pm-filter-active");
        }
        activeBtn.getStyleClass().add("pm-filter-active");
        renderProjectGrid();
        SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
    }

    @FXML
    private void onSearchChanged() {
        renderProjectGrid();
    }

    // ════════════════════════════════════════════════════════
    //  NAVIGATION
    // ════════════════════════════════════════════════════════

    @FXML
    private void backToProjectList() {
        projectDetailView.setVisible(false);
        projectDetailView.setManaged(false);
        projectListView.setVisible(true);
        projectListView.setManaged(true);
        currentProject = null;
        loadProjects(); // refresh
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
    }

    // ════════════════════════════════════════════════════════
    //  DIALOGS — NEW / EDIT PROJECT
    // ════════════════════════════════════════════════════════

    @FXML
    private void showNewProjectDialog() {
        showProjectDialog(null);
    }

    @FXML
    private void showEditProjectDialog() {
        if (currentProject != null) showProjectDialog(currentProject);
    }

    private void showProjectDialog(Project existing) {
        boolean isEdit = existing != null;
        Dialog<Project> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Project" : "New Project");

        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("pm-dialog-pane");
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.setText(isEdit ? "Save" : "Create");
        okBtn.getStyleClass().add("pm-dialog-ok-btn");
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("pm-dialog-cancel-btn");

        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("pm-dialog-form");

        Label headerLabel = new Label(isEdit ? "✏  Edit Project" : "📁  New Project");
        headerLabel.getStyleClass().add("pm-dialog-header");

        // Form controls
        TextField nameField = new TextField(isEdit ? existing.getName() : "");
        nameField.setPromptText("Project name");
        nameField.getStyleClass().add("pm-form-control");

        TextArea descField = new TextArea(isEdit && existing.getDescription() != null ? existing.getDescription() : "");
        descField.setPromptText("Description");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);
        descField.getStyleClass().add("pm-form-control");

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("PLANNING", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "CANCELLED");
        statusBox.setValue(isEdit ? existing.getStatus() : "PLANNING");
        statusBox.getStyleClass().add("pm-form-control");

        DatePicker startPicker = new DatePicker();
        startPicker.setPromptText("Start date");
        startPicker.getStyleClass().add("pm-form-control");
        if (isEdit && existing.getStartDate() != null)
            startPicker.setValue(existing.getStartDate().toLocalDate());

        DatePicker deadlinePicker = new DatePicker();
        deadlinePicker.setPromptText("Deadline");
        deadlinePicker.getStyleClass().add("pm-form-control");
        if (isEdit && existing.getDeadline() != null)
            deadlinePicker.setValue(existing.getDeadline().toLocalDate());

        // Date row
        HBox dateRow = new HBox(12);
        VBox startCol = new VBox(4);
        startCol.getChildren().addAll(new Label("Start Date") {{ getStyleClass().add("pm-form-label"); }}, startPicker);
        VBox deadlineCol = new VBox(4);
        deadlineCol.getChildren().addAll(new Label("Deadline") {{ getStyleClass().add("pm-form-label"); }}, deadlinePicker);
        HBox.setHgrow(startCol, Priority.ALWAYS);
        HBox.setHgrow(deadlineCol, Priority.ALWAYS);
        dateRow.getChildren().addAll(startCol, deadlineCol);

        content.getChildren().addAll(
                headerLabel,
                new Label("Name") {{ getStyleClass().add("pm-form-label"); }}, nameField,
                new Label("Description") {{ getStyleClass().add("pm-form-label"); }}, descField,
                new Label("Status") {{ getStyleClass().add("pm-form-label"); }}, statusBox,
                dateRow
        );

        // ── Department assignment ──
        Label deptLabel = new Label("Department");
        deptLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> deptBox = new ComboBox<>();
        Map<String, Integer> deptIdMap = new LinkedHashMap<>();
        deptBox.getItems().add("None");
        deptIdMap.put("None", null);
        try {
            ServiceDepartment sd = new ServiceDepartment();
            for (Department d : sd.recuperer()) {
                deptBox.getItems().add(d.getName());
                deptIdMap.put(d.getName(), d.getId());
            }
        } catch (SQLException ignored) {}
        deptBox.setValue("None");
        if (isEdit && existing.getDepartmentId() != null) {
            for (Map.Entry<String, Integer> e : deptIdMap.entrySet()) {
                if (existing.getDepartmentId().equals(e.getValue())) { deptBox.setValue(e.getKey()); break; }
            }
        }
        deptBox.getStyleClass().add("pm-form-control");
        content.getChildren().addAll(deptLabel, deptBox);

        // ── Team Members ──
        Label teamLabel = new Label("Team Members");
        teamLabel.getStyleClass().add("pm-form-label");
        teamLabel.setStyle("-fx-padding: 8 0 0 0;");

        // Current members list
        VBox membersList = new VBox(4);
        membersList.getStyleClass().add("pm-team-list");
        ListView<String> membersListView = new ListView<>();
        membersListView.setPrefHeight(120);
        membersListView.getStyleClass().add("pm-form-control");
        Map<String, Integer> memberNameToId = new LinkedHashMap<>();

        // Load existing members for edit mode
        if (isEdit) {
            try {
                ServiceProjectMember spm = new ServiceProjectMember();
                for (ServiceProjectMember.ProjectMember m : spm.getMembers(existing.getId())) {
                    String display = m.firstName + " " + m.lastName + " (" + m.email + ")";
                    membersListView.getItems().add(display);
                    memberNameToId.put(display, m.userId);
                }
            } catch (SQLException ignored) {}
        }

        // Add member controls
        HBox addMemberRow = new HBox(8);
        addMemberRow.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> addMemberBox = new ComboBox<>();
        addMemberBox.setPromptText("Select user to add...");
        addMemberBox.getStyleClass().add("pm-form-control");
        addMemberBox.setEditable(true);
        Map<String, Integer> addUserIdMap = new LinkedHashMap<>();
        List<String> allUserDisplayNames = new java.util.ArrayList<>();
        for (User u : userCache.values()) {
            String display = u.getFirstName() + " " + u.getLastName() + " (" + u.getEmail() + ")";
            allUserDisplayNames.add(display);
            addUserIdMap.put(display, u.getId());
        }
        addMemberBox.getItems().addAll(allUserDisplayNames);

        // Auto-filter ComboBox as user types
        addMemberBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                addMemberBox.getItems().setAll(allUserDisplayNames);
                return;
            }
            String lower = newVal.toLowerCase();
            List<String> filtered = allUserDisplayNames.stream()
                    .filter(d -> d.toLowerCase().contains(lower))
                    .collect(java.util.stream.Collectors.toList());
            addMemberBox.getItems().setAll(filtered);
            if (!filtered.isEmpty() && !addMemberBox.isShowing()) {
                addMemberBox.show();
            }
        });

        Button btnAddMember = new Button("+ Add");
        btnAddMember.getStyleClass().add("pm-dialog-ok-btn");
        btnAddMember.setOnAction(e -> {
            String sel = addMemberBox.getValue();
            if (sel == null || sel.isBlank()) return;
            // Exact match first
            if (addUserIdMap.containsKey(sel) && !memberNameToId.containsKey(sel)) {
                membersListView.getItems().add(sel);
                memberNameToId.put(sel, addUserIdMap.get(sel));
                addMemberBox.setValue(null);
                addMemberBox.getEditor().clear();
                SoundManager.getInstance().play(SoundManager.MEMBER_ADDED);
                return;
            }
            // Partial match: find first matching entry
            String lower = sel.toLowerCase();
            for (Map.Entry<String, Integer> entry : addUserIdMap.entrySet()) {
                if (entry.getKey().toLowerCase().contains(lower) && !memberNameToId.containsKey(entry.getKey())) {
                    membersListView.getItems().add(entry.getKey());
                    memberNameToId.put(entry.getKey(), entry.getValue());
                    addMemberBox.setValue(null);
                    addMemberBox.getEditor().clear();
                    SoundManager.getInstance().play(SoundManager.MEMBER_ADDED);
                    return;
                }
            }
        });

        // Add by department button
        Button btnAddDept = new Button("+ Add Department");
        btnAddDept.getStyleClass().add("pm-dialog-cancel-btn");
        btnAddDept.setOnAction(e -> {
            String selDept = deptBox.getValue();
            Integer selDeptId = deptIdMap.get(selDept);
            if (selDeptId != null) {
                for (User u : userCache.values()) {
                    if (selDeptId.equals(u.getDepartmentId())) {
                        String display = u.getFirstName() + " " + u.getLastName() + " (" + u.getEmail() + ")";
                        if (!memberNameToId.containsKey(display)) {
                            membersListView.getItems().add(display);
                            memberNameToId.put(display, u.getId());
                        }
                    }
                }
            }
        });

        // Remove member button
        Button btnRemoveMember = new Button("Remove Selected");
        btnRemoveMember.getStyleClass().add("pm-dialog-cancel-btn");
        btnRemoveMember.setOnAction(e -> {
            String sel = membersListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                membersListView.getItems().remove(sel);
                memberNameToId.remove(sel);
            }
        });

        addMemberRow.getChildren().addAll(addMemberBox, btnAddMember, btnAddDept);
        HBox.setHgrow(addMemberBox, Priority.ALWAYS);

        content.getChildren().addAll(teamLabel, addMemberRow, membersListView, btnRemoveMember);

        // For admin: manager selection
        ComboBox<String> managerBox = new ComboBox<>();
        Map<String, Integer> managerIdMap = new HashMap<>();
        if (isAdmin) {
            managerBox.getStyleClass().add("pm-form-control");
            for (User u : userCache.values()) {
                if ("PROJECT_OWNER".equals(u.getRole()) || "ADMIN".equals(u.getRole())) {
                    String display = u.getFirstName() + " " + u.getLastName() + " (" + u.getEmail() + ")";
                    managerBox.getItems().add(display);
                    managerIdMap.put(display, u.getId());
                    if (isEdit && existing.getManagerId() == u.getId()) managerBox.setValue(display);
                }
            }
            if (!isEdit) {
                String self = currentUser.getFirstName() + " " + currentUser.getLastName() + " (" + currentUser.getEmail() + ")";
                if (managerBox.getItems().contains(self)) managerBox.setValue(self);
            }
            content.getChildren().addAll(
                    new Label("Manager") {{ getStyleClass().add("pm-form-label"); }},
                    managerBox
            );
        }

        pane.setContent(content);

        // Validation
        okBtn.setDisable(nameField.getText().trim().isEmpty());
        nameField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Project p = isEdit ? existing : new Project();
                p.setName(nameField.getText().trim());
                p.setDescription(descField.getText().trim());
                p.setStatus(statusBox.getValue());
                p.setStartDate(startPicker.getValue() != null ? Date.valueOf(startPicker.getValue()) : null);
                p.setDeadline(deadlinePicker.getValue() != null ? Date.valueOf(deadlinePicker.getValue()) : null);
                // Department
                String selDeptName = deptBox.getValue();
                p.setDepartmentId(deptIdMap.get(selDeptName));
                if (isAdmin && managerBox.getValue() != null) {
                    p.setManagerId(managerIdMap.getOrDefault(managerBox.getValue(), currentUser.getId()));
                } else if (!isEdit) {
                    p.setManagerId(currentUser.getId());
                }
                return p;
            }
            return null;
        });

        // Capture member list for use in the save thread
        final Map<String, Integer> finalMemberMap = memberNameToId;

        dialog.showAndWait().ifPresent(project -> {
            new Thread(() -> {
                try {
                    if (isEdit) {
                        serviceProject.modifier(project);
                    } else {
                        serviceProject.ajouter(project);
                    }
                    // Save team members
                    try {
                        ServiceProjectMember spm = new ServiceProjectMember();
                        // Get current members to diff
                        Set<Integer> existingMembers = new HashSet<>();
                        if (isEdit) {
                            for (ServiceProjectMember.ProjectMember m : spm.getMembers(project.getId())) {
                                existingMembers.add(m.userId);
                            }
                        }
                        Set<Integer> newMembers = new HashSet<>(finalMemberMap.values());
                        // Add new members
                        for (int uid : newMembers) {
                            if (!existingMembers.contains(uid)) {
                                spm.addMember(project.getId(), uid);
                            }
                        }
                        // Remove old members not in list
                        for (int uid : existingMembers) {
                            if (!newMembers.contains(uid)) {
                                spm.removeMember(project.getId(), uid);
                            }
                        }
                    } catch (SQLException ex) {
                        System.err.println("⚠ Failed to save team members: " + ex.getMessage());
                    }
                    Platform.runLater(() -> {
                        loadProjects();
                        if (isEdit && currentProject != null) {
                            openProjectDetail(project);
                        }
                        SoundManager.getInstance().play(SoundManager.PROJECT_CREATED);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showError("Failed to save project: " + e.getMessage()));
                }
            }).start();
        });
    }

    @FXML
    private void deleteCurrentProject() {
        if (currentProject == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Project");
        confirm.setHeaderText("Delete \"" + currentProject.getName() + "\"?");
        confirm.setContentText("This will also delete all tasks in this project. This action cannot be undone.");
        DialogHelper.theme(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        serviceProject.supprimer(currentProject.getId());
                        Platform.runLater(() -> {
                            backToProjectList();
                            SoundManager.getInstance().play(SoundManager.PROJECT_DELETED);
                        });
                    } catch (SQLException e) {
                        Platform.runLater(() -> showError("Failed to delete project: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  DIALOGS — NEW / EDIT TASK
    // ════════════════════════════════════════════════════════

    @FXML
    private void showNewTaskDialog() {
        showTaskDialog(null);
    }

    private void showEditTaskDialog(Task existing) {
        showTaskDialog(existing);
    }

    private void showTaskDialog(Task existing) {
        boolean isEdit = existing != null;
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Task" : "New Task");

        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("pm-dialog-pane");
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.setText(isEdit ? "Save" : "Create");
        okBtn.getStyleClass().add("pm-dialog-ok-btn");
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("pm-dialog-cancel-btn");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("pm-dialog-form");

        // Title header
        Label headerLabel = new Label(isEdit ? "✏  Edit Task" : "📋  New Task");
        headerLabel.getStyleClass().add("pm-dialog-header");

        // Title field
        TextField titleField = new TextField(isEdit ? existing.getTitle() : "");
        titleField.setPromptText("Task title");
        titleField.getStyleClass().add("pm-form-control");

        // Description
        TextArea descField = new TextArea(isEdit && existing.getDescription() != null ? existing.getDescription() : "");
        descField.setPromptText("Description (optional)");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);
        descField.getStyleClass().add("pm-form-control");

        // Status + Priority in one row
        HBox statusPriorityRow = new HBox(12);
        statusPriorityRow.setAlignment(Pos.CENTER_LEFT);

        VBox statusCol = new VBox(4);
        Label statusLabel = new Label("Status");
        statusLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("TODO", "IN_PROGRESS", "DONE");
        statusBox.setValue(isEdit ? existing.getStatus() : "TODO");
        statusBox.getStyleClass().add("pm-form-control");
        statusCol.getChildren().addAll(statusLabel, statusBox);

        VBox priCol = new VBox(4);
        Label priLabel = new Label("Priority");
        priLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> priorityBox = new ComboBox<>();
        priorityBox.getItems().addAll("LOW", "MEDIUM", "HIGH");
        priorityBox.setValue(isEdit ? existing.getPriority() : "MEDIUM");
        priorityBox.getStyleClass().add("pm-form-control");
        priCol.getChildren().addAll(priLabel, priorityBox);

        statusPriorityRow.getChildren().addAll(statusCol, priCol);

        // Assignee - grouped by department
        Label assigneeLabel = new Label("Assignee");
        assigneeLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> assigneeBox = new ComboBox<>();
        Map<String, Integer> assigneeIdMap = new LinkedHashMap<>();
        assigneeBox.getItems().add("Unassigned");
        assigneeIdMap.put("Unassigned", 0);

        // Load departments for grouping
        Map<Integer, String> deptNames = new LinkedHashMap<>();
        try {
            ServiceDepartment sd = new ServiceDepartment();
            for (Department d : sd.recuperer()) {
                deptNames.put(d.getId(), d.getName());
            }
        } catch (SQLException ignored) {}

        // Group users by department
        Map<String, List<User>> grouped = new LinkedHashMap<>();
        grouped.put("No Department", new ArrayList<>());
        for (String dName : deptNames.values()) grouped.put(dName, new ArrayList<>());

        for (User u : userCache.values()) {
            String deptName = "No Department";
            if (u.getDepartmentId() != null && deptNames.containsKey(u.getDepartmentId())) {
                deptName = deptNames.get(u.getDepartmentId());
            }
            grouped.computeIfAbsent(deptName, k -> new ArrayList<>()).add(u);
        }

        for (Map.Entry<String, List<User>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            // Add department header as a disabled separator item
            assigneeBox.getItems().add("── " + entry.getKey() + " ──");
            assigneeIdMap.put("── " + entry.getKey() + " ──", -1); // marker
            for (User u : entry.getValue()) {
                String display = u.getFirstName() + " " + u.getLastName();
                assigneeBox.getItems().add("    " + display);
                assigneeIdMap.put("    " + display, u.getId());
                if (isEdit && existing.getAssigneeId() == u.getId()) assigneeBox.setValue("    " + display);
            }
        }
        if (assigneeBox.getValue() == null) assigneeBox.setValue("Unassigned");
        assigneeBox.getStyleClass().add("pm-form-control");

        // Disable selection of department header items
        assigneeBox.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                setText(item);
                if (item.startsWith("──")) {
                    setStyle("-fx-font-weight: bold; -fx-opacity: 0.6; -fx-font-size: 11;");
                    setDisable(true);
                } else if (item.startsWith("    ")) {
                    setStyle("-fx-padding: 4 8 4 16;");
                    setDisable(false);
                } else {
                    setStyle("");
                    setDisable(false);
                }
            }
        });

        // Due date
        Label dueLabel = new Label("Due Date");
        dueLabel.getStyleClass().add("pm-form-label");
        DatePicker duePicker = new DatePicker();
        duePicker.setPromptText("Due date (optional)");
        duePicker.getStyleClass().add("pm-form-control");
        if (isEdit && existing.getDueDate() != null)
            duePicker.setValue(existing.getDueDate().toLocalDate());

        content.getChildren().addAll(
                headerLabel,
                new Label("Title") {{ getStyleClass().add("pm-form-label"); }},
                titleField,
                new Label("Description") {{ getStyleClass().add("pm-form-label"); }},
                descField,
                statusPriorityRow,
                assigneeLabel, assigneeBox,
                dueLabel, duePicker
        );

        pane.setContent(content);

        okBtn.setDisable(titleField.getText().trim().isEmpty());
        titleField.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                Task t = isEdit ? existing : new Task();
                t.setProjectId(currentProject.getId());
                t.setTitle(titleField.getText().trim());
                t.setDescription(descField.getText().trim());
                t.setStatus(statusBox.getValue());
                t.setPriority(priorityBox.getValue());
                String selAssignee = assigneeBox.getValue();
                t.setAssigneeId(assigneeIdMap.getOrDefault(selAssignee != null ? selAssignee : "Unassigned", 0));
                t.setDueDate(duePicker.getValue() != null ? Date.valueOf(duePicker.getValue()) : null);
                return t;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(task -> {
            new Thread(() -> {
                try {
                    if (isEdit) {
                        serviceTask.modifier(task);
                    } else {
                        serviceTask.ajouter(task);
                    }
                    // Notify assigned employee
                    if (task.getAssigneeId() > 0 && task.getAssigneeId() != currentUser.getId()) {
                        serviceNotification.notifyTaskAssigned(
                                task.getAssigneeId(), task.getTitle(),
                                currentProject.getName(), task.getId());
                    }
                    Platform.runLater(() -> {
                        loadTaskBoard();
                        SoundManager.getInstance().play(SoundManager.TASK_CREATED);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showError("Failed to save task: " + e.getMessage()));
                }
            }).start();
        });
    }

    private void deleteTask(Task task) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Task");
        confirm.setHeaderText("Delete \"" + task.getTitle() + "\"?");
        confirm.setContentText("This action cannot be undone.");
        DialogHelper.theme(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        serviceTask.supprimer(task.getId());
                        Platform.runLater(() -> {
                            loadTaskBoard();
                            SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                        });
                    } catch (SQLException e) {
                        Platform.runLater(() -> showError("Failed to delete task: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  TASK REVIEW & FEEDBACK
    // ════════════════════════════════════════════════════════

    private void submitTaskForReview(Task task) {
        SoundManager.getInstance().play(SoundManager.TASK_SUBMITTED);
        new Thread(() -> {
            try {
                serviceTask.updateStatus(task.getId(), "DONE");
                task.setStatus("DONE");

                // Notify project manager
                User assignee = userCache.get(task.getAssigneeId());
                String empName = assignee != null ? assignee.getFirstName() + " " + assignee.getLastName() : "Employee";
                serviceNotification.notifyTaskSubmitted(
                        currentProject.getManagerId(), empName, task.getTitle(),
                        currentProject.getName(), task.getId());

                Platform.runLater(() -> {
                    loadTaskBoard();
                    showInfo("Task submitted for review!");
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed: " + e.getMessage()));
            }
        }).start();
    }

    private void showReviewDialog(Task task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Review Task");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("pm-dialog-pane");
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.setText("Submit Review");
        okBtn.getStyleClass().add("pm-dialog-ok-btn");
        Button cancelBtn = (Button) pane.lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("pm-dialog-cancel-btn");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("pm-dialog-form");
        content.setPrefWidth(420);

        Label header = new Label("\u2B50  Review Task");
        header.getStyleClass().add("review-dialog-header");

        // Task info
        User assignee = userCache.get(task.getAssigneeId());
        String assigneeName = assignee != null ? assignee.getFirstName() + " " + assignee.getLastName() : "Unassigned";
        Label taskInfo = new Label("\uD83D\uDCCB " + task.getTitle() + "\n\uD83D\uDC64 " + assigneeName);
        taskInfo.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 12;");
        taskInfo.setWrapText(true);

        // Rating (1-5 stars)
        Label ratingLabel = new Label("Rating");
        ratingLabel.getStyleClass().add("pm-form-label");
        HBox stars = new HBox(4);
        stars.setAlignment(Pos.CENTER_LEFT);
        final int[] rating = {0};
        Label[] starLabels = new Label[5];
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            starLabels[i] = new Label("\u2606"); // empty star
            starLabels[i].getStyleClass().add("review-rating-star");
            starLabels[i].setOnMouseClicked(e -> {
                rating[0] = idx + 1;
                for (int j = 0; j < 5; j++) {
                    starLabels[j].setText(j <= idx ? "\u2605" : "\u2606");
                    starLabels[j].getStyleClass().remove("review-rating-star-active");
                    if (j <= idx) starLabels[j].getStyleClass().add("review-rating-star-active");
                }
                SoundManager.getInstance().play(SoundManager.STAR_RATING);
            });
            stars.getChildren().add(starLabels[i]);
        }

        // Review status
        Label statusLabel = new Label("Review Decision");
        statusLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("APPROVED", "NEEDS_REVISION", "REJECTED");
        statusBox.setValue("APPROVED");
        statusBox.getStyleClass().add("pm-form-control");

        // Feedback text
        Label fbLabel = new Label("Feedback");
        fbLabel.getStyleClass().add("pm-form-label");
        TextArea feedbackArea = new TextArea();
        feedbackArea.setPromptText("Write your feedback for the employee...");
        feedbackArea.setPrefRowCount(4);
        feedbackArea.setWrapText(true);
        feedbackArea.getStyleClass().addAll("pm-form-control", "review-feedback-area");

        content.getChildren().addAll(header, taskInfo, ratingLabel, stars, statusLabel, statusBox, fbLabel, feedbackArea);
        pane.setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK && rating[0] > 0) {
                new Thread(() -> {
                    try {
                        // Submit review via API
                        Map<String, Object> body = new HashMap<>();
                        body.put("review_status", statusBox.getValue());
                        body.put("review_rating", rating[0]);
                        body.put("review_feedback", feedbackArea.getText().trim());
                        utils.ApiClient.put("/tasks/" + task.getId() + "/review", body);

                        // Notify the employee
                        if (task.getAssigneeId() > 0) {
                            serviceNotification.notifyTaskReviewed(
                                    task.getAssigneeId(), task.getTitle(),
                                    statusBox.getValue(), rating[0], task.getId());
                        }

                        // If needs revision, move back to TODO
                        if ("NEEDS_REVISION".equals(statusBox.getValue())) {
                            serviceTask.updateStatus(task.getId(), "TODO");
                        }

                        Platform.runLater(() -> {
                            loadTaskBoard();
                            SoundManager.getInstance().play(SoundManager.REVIEW_SUBMITTED);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showError("Review failed: " + e.getMessage()));
                    }
                }).start();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  AI-POWERED FEATURES
    // ════════════════════════════════════════════════════════

    @FXML
    private void aiPlanTasks() {
        if (currentProject == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("AI Task Planning");
        confirm.setHeaderText("🤖 Generate tasks with AI?");
        confirm.setContentText("AI will analyze your project description and create a task breakdown.\n" +
                "This will ADD new tasks — existing tasks won't be affected.");
        DialogHelper.theme(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                btnAiPlan.setDisable(true);
                btnAiPlan.setText("🤖 Generating...");

                new Thread(() -> {
                    try {
                        // Calculate deadline days
                        int deadlineDays = 30;
                        if (currentProject.getStartDate() != null && currentProject.getDeadline() != null) {
                            long diff = currentProject.getDeadline().getTime() - currentProject.getStartDate().getTime();
                            deadlineDays = Math.max(7, (int) (diff / (1000 * 60 * 60 * 24)));
                        }

                        Map<String, Object> body = new HashMap<>();
                        body.put("project_name", currentProject.getName());
                        body.put("description", currentProject.getDescription() != null ? currentProject.getDescription() : "");
                        body.put("team_size", userCache.size());
                        body.put("deadline_days", deadlineDays);

                        com.google.gson.JsonElement resp = utils.ApiClient.post("/ai/plan-project", body);
                        if (resp != null && resp.isJsonObject()) {
                            com.google.gson.JsonArray tasks = resp.getAsJsonObject().getAsJsonArray("tasks");
                            if (tasks != null) {
                                int created = 0;
                                for (com.google.gson.JsonElement taskEl : tasks) {
                                    com.google.gson.JsonObject t = taskEl.getAsJsonObject();
                                    Task newTask = new Task();
                                    newTask.setProjectId(currentProject.getId());
                                    newTask.setTitle(t.get("title").getAsString());
                                    newTask.setDescription(t.has("description") ? t.get("description").getAsString() : "");
                                    newTask.setStatus("TODO");
                                    newTask.setPriority(t.has("priority") ? t.get("priority").getAsString() : "MEDIUM");
                                    newTask.setAssigneeId(0);
                                    serviceTask.ajouter(newTask);
                                    created++;
                                }
                                final int count = created;
                                Platform.runLater(() -> {
                                    loadTaskBoard();
                                    btnAiPlan.setDisable(false);
                                    btnAiPlan.setText("🤖 Plan with AI");
                                    SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                                    info.setTitle("AI Planning Complete");
                                    info.setHeaderText("✅ " + count + " tasks generated!");
                                    info.setContentText("Review them in the board and assign team members.");
                                    DialogHelper.theme(info);
                                    info.showAndWait();
                                });
                                return;
                            }
                        }
                        Platform.runLater(() -> {
                            btnAiPlan.setDisable(false);
                            btnAiPlan.setText("🤖 Plan with AI");
                            showError("AI returned an empty response. Try again later.");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            btnAiPlan.setDisable(false);
                            btnAiPlan.setText("🤖 Plan with AI");
                            showError("AI planning failed: " + e.getMessage());
                        });
                    }
                }).start();
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  UTILITIES
    // ════════════════════════════════════════════════════════

    private String formatStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case "IN_PROGRESS": return "In Progress";
            case "ON_HOLD":     return "On Hold";
            default:            return status.charAt(0) + status.substring(1).toLowerCase();
        }
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle("Error");
        DialogHelper.theme(alert);
        alert.showAndWait();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        alert.setTitle("Info");
        DialogHelper.theme(alert);
        alert.showAndWait();
    }
}
