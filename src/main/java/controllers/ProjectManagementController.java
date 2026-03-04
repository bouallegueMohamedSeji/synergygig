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
import javafx.scene.input.*;
import javafx.scene.layout.*;
import services.ServiceDepartment;
import services.ServiceProject;
import services.ServiceProjectMember;
import services.ServiceNotification;
import services.ServiceTask;
import services.ServiceUser;
import services.ZAIService;
import utils.DialogHelper;
import utils.CardEffects;
import utils.SessionManager;
import utils.SoundManager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import utils.AppConfig;
import utils.AppThreadPool;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Duration;
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
    @FXML private Button btnBack, btnEditProject, btnDeleteProject, btnAddTask, btnAiPlan, btnDailyTip, btnFunFact;
    @FXML private Button btnSprintPlan, btnMeetingNotes, btnDecisionHelper;
    @FXML private Label detailProjectName, detailStatusBadge, detailProjectDesc;
    @FXML private Label detailManager, detailDates, detailProgress, detailTaskCount, detailTeam;
    @FXML private Label quoteLabel, holidayWarning, weatherLabel;
    @FXML private HBox quoteBanner;
    @FXML private ProgressBar detailProgressBar;
    @FXML private VBox colTodo, colInProgress, colDone, colInReview;
    @FXML private Label countTodo, countInProgress, countDone, countInReview;

    // ═══ Team View ═══
    @FXML private Button btnTeamView;
    @FXML private VBox teamView;
    @FXML private HBox kanbanBoard;

    // ═══ Employee Dashboard (task-centric, replaces project grid for EMPLOYEE/GIG_WORKER) ═══
    @FXML private VBox employeeDashboard;

    // ═══ Workload Constants ═══
    private static final int MAX_ACTIVE_TASKS = 8;
    private static final int WARN_THRESHOLD = 6;

    // ═══ Services ═══
    private final ServiceProject serviceProject = new ServiceProject();
    private final ServiceTask serviceTask = new ServiceTask();
    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceNotification serviceNotification = new ServiceNotification();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    // ═══ State ═══
    private User currentUser;
    private boolean isProjectOwner;  // PROJECT_OWNER — can create/edit/delete projects
    private boolean isAdmin;          // ADMIN or HR_MANAGER — can see ALL projects (read-only)
    private List<Project> allProjects = new ArrayList<>();
    private Map<Integer, User> userCache = new HashMap<>();
    private String activeFilter = "ALL";
    private Project currentProject;
    private boolean showingTeam = false;
    private List<ServiceProjectMember.ProjectMember> teamMembersCache = new ArrayList<>();

    @FXML
    public void initialize() {
        currentUser = SessionManager.getInstance().getCurrentUser();
        String role = currentUser.getRole();

        isAdmin = "ADMIN".equals(role) || "HR_MANAGER".equals(role);
        isProjectOwner = "PROJECT_OWNER".equals(role);
        boolean isEmployee = "EMPLOYEE".equals(role) || "GIG_WORKER".equals(role);

        // PROJECT_OWNER and ADMIN can create new projects
        if (isProjectOwner || isAdmin) {
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
            projectSubtitle.setText("Your assigned tasks across all projects");
        }

        // Load users in background, projects already loads async
        AppThreadPool.io(() -> {
            try {
                List<User> users = serviceUser.recuperer();
                Platform.runLater(() -> {
                    for (User u : users) userCache.put(u.getId(), u);
                    // Employee dashboard requires users to be loaded first
                    if (isEmployee && employeeDashboard != null) {
                        showEmployeeDashboard();
                    }
                });
            } catch (SQLException e) {
                System.err.println("⚠ Failed to load users: " + e.getMessage());
            }
        });

        // Employee/GigWorker: show task-centric dashboard instead of project grid
        if (isEmployee && employeeDashboard != null) {
            projectListView.setVisible(false);
            projectListView.setManaged(false);
            employeeDashboard.setVisible(true);
            employeeDashboard.setManaged(true);
        } else {
            if (employeeDashboard != null) {
                employeeDashboard.setVisible(false);
                employeeDashboard.setManaged(false);
            }
            loadProjects();
        }
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
        AppThreadPool.io(() -> {
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
        });
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

        // Task progress bar  [0]=TODO, [1]=IN_PROGRESS, [2]=IN_REVIEW, [3]=DONE
        int[] counts = getTaskCounts(project.getId());
        int total = counts[0] + counts[1] + counts[2] + counts[3];
        double progress = total > 0 ? (double) counts[3] / total : 0;

        ProgressBar progressBar = new ProgressBar(progress);
        progressBar.getStyleClass().add("pm-progress-bar");
        progressBar.setMaxWidth(Double.MAX_VALUE);

        Label taskLabel = new Label(counts[3] + "/" + total + " tasks done");
        taskLabel.getStyleClass().add("pm-card-meta");

        card.getChildren().addAll(titleRow, desc, mgrLabel, dateLabel, progressBar, taskLabel);

        // Click to open detail
        card.setOnMouseClicked(e -> openProjectDetail(project));
        card.setCursor(javafx.scene.Cursor.HAND);

        CardEffects.applyWobbleEffect(card);

        return card;
    }

    /** Returns [todoCount, inProgressCount, doneCount] */
    private int[] getTaskCounts(int projectId) {
        try {
            List<Task> tasks = serviceTask.getByProject(projectId);
            int todo = 0, inProg = 0, inReview = 0, done = 0;
            for (Task t : tasks) {
                switch (t.getStatus()) {
                    case "TODO": todo++; break;
                    case "IN_PROGRESS": inProg++; break;
                    case "IN_REVIEW": inReview++; break;
                    case "DONE": done++; break;
                }
            }
            return new int[]{todo, inProg, inReview, done};
        } catch (SQLException e) {
            return new int[]{0, 0, 0, 0};
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

        // Reset team view — always show kanban first
        showingTeam = false;
        kanbanBoard.setVisible(true);
        kanbanBoard.setManaged(true);
        teamView.setVisible(false);
        teamView.setManaged(false);
        btnTeamView.setText("👥 Team");

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
        AppThreadPool.io(() -> {
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
        });

        // The project's own manager OR any ADMIN can edit/delete/add tasks
        boolean canManage = project.getManagerId() == currentUser.getId() || isAdmin;
        btnEditProject.setVisible(canManage);
        btnEditProject.setManaged(canManage);
        btnDeleteProject.setVisible(canManage);
        btnDeleteProject.setManaged(canManage);
        btnAddTask.setVisible(canManage);
        btnAddTask.setManaged(canManage);
        btnAiPlan.setVisible(canManage);
        btnAiPlan.setManaged(canManage);
        btnSprintPlan.setVisible(canManage);
        btnSprintPlan.setManaged(canManage);
        btnMeetingNotes.setVisible(canManage);
        btnMeetingNotes.setManaged(canManage);
        btnDecisionHelper.setVisible(canManage);
        btnDecisionHelper.setManaged(canManage);

        // Fetch motivational quote from ZenQuotes API
        fetchQuoteOfDay();

        // Fetch weather from Open-Meteo API
        fetchWeather();

        // Check public holidays near deadline (Nager.Date API)
        checkDeadlineHolidays();

        loadTaskBoard();
    }

    private void loadTaskBoard() {
        AppThreadPool.io(() -> {
            try {
                List<Task> tasks = serviceTask.getByProject(currentProject.getId());
                Platform.runLater(() -> renderKanban(tasks));
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed to load tasks: " + e.getMessage()));
            }
        });
    }

    private static final DataFormat TASK_ID_FORMAT = new DataFormat("application/x-synergygig-task-id");

    private void renderKanban(List<Task> tasks) {
        colTodo.getChildren().clear();
        colInProgress.getChildren().clear();
        colDone.getChildren().clear();
        if (colInReview != null) colInReview.getChildren().clear();

        int todo = 0, inProg = 0, done = 0, inReview = 0;

        for (Task task : tasks) {
            VBox card = buildTaskCard(task);
            VBox pinnedCard = CardEffects.apply3DPinEffect(card);
            switch (task.getStatus()) {
                case "TODO":
                    colTodo.getChildren().add(pinnedCard);
                    todo++;
                    break;
                case "IN_PROGRESS":
                    colInProgress.getChildren().add(pinnedCard);
                    inProg++;
                    break;
                case "IN_REVIEW":
                    if (colInReview != null) {
                        colInReview.getChildren().add(pinnedCard);
                    } else {
                        colDone.getChildren().add(pinnedCard); // fallback
                    }
                    inReview++;
                    break;
                case "DONE":
                    colDone.getChildren().add(pinnedCard);
                    done++;
                    break;
            }
        }

        countTodo.setText(String.valueOf(todo));
        countInProgress.setText(String.valueOf(inProg));
        countDone.setText(String.valueOf(done));
        if (countInReview != null) countInReview.setText(String.valueOf(inReview));

        int total = todo + inProg + done + inReview;
        double progress = total > 0 ? (double) done / total : 0;
        detailProgress.setText(Math.round(progress * 100) + "%");
        detailProgressBar.setProgress(progress);
        detailTaskCount.setText(total + " task" + (total != 1 ? "s" : ""));

        // Setup drop targets on each Kanban column
        setupDropTarget(colTodo, "TODO");
        setupDropTarget(colInProgress, "IN_PROGRESS");
        setupDropTarget(colDone, "DONE");
        if (colInReview != null) setupDropTarget(colInReview, "IN_REVIEW");

        // ── Auto-completion suggestion: all tasks DONE? ──
        if (total > 0 && done == total && currentProject != null
                && !"COMPLETED".equals(currentProject.getStatus())) {
            boolean isManagerOrAdmin = currentProject.getManagerId() == currentUser.getId() || isAdmin;
            if (isManagerOrAdmin) {
                Platform.runLater(() -> {
                    Alert prompt = new Alert(Alert.AlertType.CONFIRMATION);
                    prompt.setTitle("Project Complete!");
                    prompt.setHeaderText("🎉 All tasks are done!");
                    prompt.setContentText("All " + total + " tasks in \"" + currentProject.getName() +
                            "\" are completed. Mark the project as COMPLETED?");
                    DialogHelper.theme(prompt);
                    prompt.showAndWait().ifPresent(btn -> {
                        if (btn == ButtonType.OK) {
                            try {
                                currentProject.setStatus("COMPLETED");
                                serviceProject.modifier(currentProject);
                                showInfo("Project marked as COMPLETED!");
                                SoundManager.getInstance().play(SoundManager.REVIEW_SUBMITTED);
                            } catch (SQLException e) {
                                showError("Failed: " + e.getMessage());
                            }
                        }
                    });
                });
            }
        }
    }

    /** Allow a task card to be dropped onto this column. */
    private void setupDropTarget(VBox column, String targetStatus) {
        column.setOnDragOver(event -> {
            if (event.getGestureSource() != column && event.getDragboard().hasContent(TASK_ID_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        column.setOnDragEntered(event -> {
            if (event.getDragboard().hasContent(TASK_ID_FORMAT)) {
                column.setStyle("-fx-background-color: rgba(144,221,240,0.06); -fx-background-radius: 12;");
            }
            event.consume();
        });

        column.setOnDragExited(event -> {
            column.setStyle("");
            event.consume();
        });

        column.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasContent(TASK_ID_FORMAT)) {
                int taskId = (int) db.getContent(TASK_ID_FORMAT);
                // Find the task and update its status
                changeTaskStatus(findTaskById(taskId), targetStatus);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /** Lookup a task by ID from current board. */
    private Task findTaskById(int taskId) {
        try {
            for (Task t : serviceTask.getByProject(currentProject.getId())) {
                if (t.getId() == taskId) return t;
            }
        } catch (SQLException ignored) {}
        // Fallback: create stub with just the ID
        Task stub = new Task();
        stub.setId(taskId);
        return stub;
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

        // ── Drag-and-drop: allow this card to be dragged ──
        boolean canDrag = currentProject.getManagerId() == currentUser.getId()
                || isAdmin
                || task.getAssigneeId() == currentUser.getId();
        if (canDrag) {
            card.setOnDragDetected(event -> {
                Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.put(TASK_ID_FORMAT, task.getId());
                db.setContent(content);
                card.setOpacity(0.5);
                event.consume();
            });
            card.setOnDragDone(event -> {
                card.setOpacity(1.0);
                event.consume();
            });
        }

        // Context menu for status changes (for managers)
        boolean canManageTask = currentProject.getManagerId() == currentUser.getId() || isAdmin;
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
            if (!"IN_REVIEW".equals(task.getStatus())) {
                MenuItem toReview = new MenuItem("Move to In Review");
                toReview.setOnAction(e -> changeTaskStatus(task, "IN_REVIEW"));
                ctx.getItems().add(toReview);
            }
            if (!"DONE".equals(task.getStatus())) {
                MenuItem toDone = new MenuItem("Move to Done");
                toDone.setOnAction(e -> changeTaskStatus(task, "DONE"));
                ctx.getItems().add(toDone);
            }
            ctx.getItems().add(new SeparatorMenuItem());

            // Review task (PM only) — available for IN_REVIEW tasks
            if ("IN_REVIEW".equals(task.getStatus())) {
                MenuItem viewSub = new MenuItem("📋 View Submission");
                viewSub.setOnAction(e -> showSubmissionPopup(task));
                ctx.getItems().add(viewSub);
                MenuItem reviewItem = new MenuItem("\u2B50 Review & Feedback");
                reviewItem.setOnAction(e -> showReviewDialog(task));
                ctx.getItems().add(reviewItem);
                ctx.getItems().add(new SeparatorMenuItem());
            }

            // View submission for DONE tasks that have one
            if ("DONE".equals(task.getStatus()) && (task.getSubmissionText() != null || task.getSubmissionFile() != null)) {
                MenuItem viewSub = new MenuItem("📋 View Submission");
                viewSub.setOnAction(e -> showSubmissionPopup(task));
                ctx.getItems().add(viewSub);
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
            // Employee assigned to this task — can submit for review or decline
            ContextMenu ctx = new ContextMenu();
            if (!"DONE".equals(task.getStatus()) && !"IN_REVIEW".equals(task.getStatus())) {
                MenuItem submitItem = new MenuItem("\u2705 Submit for Review");
                submitItem.setOnAction(e -> submitTaskForReview(task));
                ctx.getItems().add(submitItem);
            }
            if (!"IN_PROGRESS".equals(task.getStatus()) && !"DONE".equals(task.getStatus()) && !"IN_REVIEW".equals(task.getStatus())) {
                MenuItem startItem = new MenuItem("\u25B6 Start Working");
                startItem.setOnAction(e -> changeTaskStatus(task, "IN_PROGRESS"));
                ctx.getItems().add(startItem);
            }
            // Decline task — lets the employee refuse a task with a reason
            if ("TODO".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus())) {
                ctx.getItems().add(new SeparatorMenuItem());
                MenuItem declineItem = new MenuItem("🚫 Decline Task");
                declineItem.setOnAction(e -> declineTask(task));
                ctx.getItems().add(declineItem);
            }
            card.setOnContextMenuRequested(e -> ctx.show(card, e.getScreenX(), e.getScreenY()));
        }

        card.setCursor(javafx.scene.Cursor.HAND);

        // Double-click to view submission (IN_REVIEW / DONE with submission)
        card.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                if ("IN_REVIEW".equals(task.getStatus()) ||
                        ("DONE".equals(task.getStatus()) && (task.getSubmissionText() != null || task.getSubmissionFile() != null))) {
                    showSubmissionPopup(task);
                }
            }
        });

        return card;
    }

    private void changeTaskStatus(Task task, String newStatus) {
        AppThreadPool.io(() -> {
            try {
                serviceTask.updateStatus(task.getId(), newStatus);
                task.setStatus(newStatus);
                Platform.runLater(this::loadTaskBoard);
                SoundManager.getInstance().play(SoundManager.TASK_MOVED);
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed to update status: " + e.getMessage()));
            }
        });
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

    // ── AI Tool shortcuts ──────────────────────────────────
    @FXML private void openCodeReview()    { DashboardController.getInstance().navigateTo("/fxml/CodeReview.fxml"); }
    @FXML private void openMeetingNotes()  { DashboardController.getInstance().navigateTo("/fxml/MeetingSummarizer.fxml"); }
    @FXML private void openAutoScheduler() { DashboardController.getInstance().navigateTo("/fxml/AutoScheduler.fxml"); }

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
    //  TEAM / CONTRIBUTORS VIEW
    // ════════════════════════════════════════════════════════

    @FXML
    private void toggleTeamView() {
        showingTeam = !showingTeam;
        SoundManager.getInstance().play(SoundManager.TAB_SWITCH);

        kanbanBoard.setVisible(!showingTeam);
        kanbanBoard.setManaged(!showingTeam);
        teamView.setVisible(showingTeam);
        teamView.setManaged(showingTeam);
        btnTeamView.setText(showingTeam ? "📋 Tasks" : "👥 Team");

        if (showingTeam) {
            loadTeamMembers();
        }
    }

    private void loadTeamMembers() {
        AppThreadPool.io(() -> {
            try {
                ServiceProjectMember spm = new ServiceProjectMember();
                List<ServiceProjectMember.ProjectMember> members = spm.getMembers(currentProject.getId());
                Platform.runLater(() -> {
                    teamMembersCache = members;
                    renderTeamView(members, "", "All");
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showError("Failed to load team: " + e.getMessage()));
            }
        });
    }

    private void renderTeamView(List<ServiceProjectMember.ProjectMember> members,
                                String nameFilter, String roleFilter) {
        teamView.getChildren().clear();

        // ── Filter Row ──
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        filterRow.getStyleClass().add("pm-team-filter-row");

        TextField nameSearch = new TextField(nameFilter);
        nameSearch.setPromptText("🔍 Search by name or email...");
        nameSearch.getStyleClass().add("pm-team-search");
        nameSearch.setPrefWidth(250);

        // Collect unique roles
        Set<String> roles = new LinkedHashSet<>();
        roles.add("All");
        for (ServiceProjectMember.ProjectMember m : teamMembersCache) {
            if (m.role != null && !m.role.isEmpty()) roles.add(m.role);
        }
        ComboBox<String> roleCombo = new ComboBox<>();
        roleCombo.getItems().addAll(roles);
        roleCombo.setValue(roleFilter);
        roleCombo.getStyleClass().add("pm-team-role-filter");
        roleCombo.setPromptText("Filter by role");

        // Column toggle
        MenuButton colToggle = new MenuButton("⚙ Columns");
        colToggle.getStyleClass().add("pm-btn-ghost");
        String[] columnNames = {"Avatar", "Name", "Email", "Role", "Department"};
        boolean[] colVisible = {true, true, true, true, true};
        for (int i = 0; i < columnNames.length; i++) {
            CheckMenuItem item = new CheckMenuItem(columnNames[i]);
            item.setSelected(true);
            final int idx = i;
            item.selectedProperty().addListener((obs, ov, nv) -> {
                colVisible[idx] = nv;
                renderTeamTable(teamView, members, nameSearch.getText(), roleCombo.getValue(), colVisible);
            });
            colToggle.getItems().add(item);
        }

        Label countLabel = new Label(members.size() + " members");
        countLabel.getStyleClass().add("pm-team-count");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        filterRow.getChildren().addAll(nameSearch, roleCombo, spacer, countLabel, colToggle);
        teamView.getChildren().add(filterRow);

        // (Listeners are set up after scroll content creation below)

        // Wrap everything in a ScrollPane so team table + workload are scrollable
        ScrollPane teamScroll = new ScrollPane();
        teamScroll.setFitToWidth(true);
        teamScroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(teamScroll, Priority.ALWAYS);

        VBox scrollContent = new VBox(12);
        scrollContent.setPadding(new Insets(0));
        teamScroll.setContent(scrollContent);
        teamView.getChildren().add(teamScroll);

        // Initial render — pass scrollContent for the table + workload
        renderTeamTable(scrollContent, members, nameFilter, roleFilter, colVisible);

        // Workload panel for project owner/admin (below the table)
        if (isProjectOwner || isAdmin) {
            addWorkloadToTeamView(members, scrollContent);
        }

        // Re-wire filter listeners to target scrollContent
        nameSearch.textProperty().addListener((obs2, ov2, nv2) -> {
            scrollContent.getChildren().clear();
            renderTeamTable(scrollContent, teamMembersCache, nv2, roleCombo.getValue(), colVisible);
            if (isProjectOwner || isAdmin) addWorkloadToTeamView(teamMembersCache, scrollContent);
        });
        roleCombo.valueProperty().addListener((obs2, ov2, nv2) -> {
            scrollContent.getChildren().clear();
            renderTeamTable(scrollContent, teamMembersCache, nameSearch.getText(), nv2, colVisible);
            if (isProjectOwner || isAdmin) addWorkloadToTeamView(teamMembersCache, scrollContent);
        });
    }

    private void renderTeamTable(VBox container,
                                 List<ServiceProjectMember.ProjectMember> allMembers,
                                 String nameFilter, String roleFilter, boolean[] colVisible) {

        // Filter
        List<ServiceProjectMember.ProjectMember> filtered = allMembers.stream()
                .filter(m -> {
                    if (nameFilter != null && !nameFilter.isEmpty()) {
                        String q = nameFilter.toLowerCase();
                        String full = (m.firstName + " " + m.lastName + " " + m.email).toLowerCase();
                        if (!full.contains(q)) return false;
                    }
                    if (roleFilter != null && !"All".equals(roleFilter)) {
                        if (!roleFilter.equals(m.role)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Update count label in filter row (which lives in teamView, not this container)
        if (teamView.getChildren().size() > 0 && teamView.getChildren().get(0) instanceof HBox) {
            for (Node n : ((HBox) teamView.getChildren().get(0)).getChildren()) {
                if (n instanceof Label && ((Label) n).getStyleClass().contains("pm-team-count")) {
                    ((Label) n).setText(filtered.size() + " member" + (filtered.size() != 1 ? "s" : ""));
                }
            }
        }

        VBox table = new VBox(0);
        table.getStyleClass().add("pm-team-table");

        // Header row
        HBox header = buildTeamRow(null, colVisible, true);
        table.getChildren().add(header);

        // Data rows
        if (filtered.isEmpty()) {
            Label empty = new Label("No team members found");
            empty.getStyleClass().add("pm-team-empty");
            empty.setPadding(new Insets(24));
            table.getChildren().add(empty);
        } else {
            for (ServiceProjectMember.ProjectMember m : filtered) {
                HBox row = buildTeamRow(m, colVisible, false);
                table.getChildren().add(row);
            }
        }

        container.getChildren().add(table);
    }

    private HBox buildTeamRow(ServiceProjectMember.ProjectMember member,
                              boolean[] colVisible, boolean isHeader) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add(isHeader ? "pm-team-header-row" : "pm-team-data-row");
        row.setPadding(new Insets(8, 12, 8, 12));

        // Col 0: Avatar (width 50)
        if (colVisible[0]) {
            if (isHeader) {
                row.getChildren().add(createTeamCol("", 50));
            } else {
                String initials = "";
                if (member.firstName != null && !member.firstName.isEmpty())
                    initials += member.firstName.charAt(0);
                if (member.lastName != null && !member.lastName.isEmpty())
                    initials += member.lastName.charAt(0);
                Label avatar = new Label(initials.toUpperCase());
                avatar.getStyleClass().add("pm-team-avatar");
                // Color based on hash
                String[] colors = {"#6366f1", "#ec4899", "#14b8a6", "#f59e0b", "#8b5cf6", "#ef4444", "#06b6d4", "#84cc16"};
                String color = colors[Math.abs((member.firstName + member.lastName).hashCode()) % colors.length];
                avatar.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; "
                        + "-fx-min-width: 36; -fx-min-height: 36; -fx-max-width: 36; -fx-max-height: 36; "
                        + "-fx-background-radius: 18; -fx-alignment: center; -fx-font-weight: bold; -fx-font-size: 13;");
                HBox cell = new HBox(avatar);
                cell.setAlignment(Pos.CENTER);
                cell.setMinWidth(50);
                cell.setMaxWidth(50);
                cell.setPrefWidth(50);
                row.getChildren().add(cell);
            }
        }

        // Col 1: Name (width 180)
        if (colVisible[1]) {
            if (isHeader) {
                row.getChildren().add(createTeamCol("Name", 180));
            } else {
                Label name = new Label(member.firstName + " " + member.lastName);
                name.getStyleClass().add("pm-team-name");
                name.setMinWidth(180);
                name.setPrefWidth(180);
                name.setMaxWidth(180);
                row.getChildren().add(name);
            }
        }

        // Col 2: Email (grow)
        if (colVisible[2]) {
            if (isHeader) {
                Label lbl = new Label("Email");
                lbl.getStyleClass().add("pm-team-col-header");
                HBox cell = new HBox(lbl);
                HBox.setHgrow(cell, Priority.ALWAYS);
                cell.setMinWidth(150);
                row.getChildren().add(cell);
            } else {
                Label email = new Label(member.email != null ? member.email : "—");
                email.getStyleClass().add("pm-team-email");
                HBox cell = new HBox(email);
                HBox.setHgrow(cell, Priority.ALWAYS);
                cell.setMinWidth(150);
                row.getChildren().add(cell);
            }
        }

        // Col 3: Role (width 130)
        if (colVisible[3]) {
            if (isHeader) {
                row.getChildren().add(createTeamCol("Role", 130));
            } else {
                Label roleBadge = new Label(member.role != null ? member.role : "—");
                roleBadge.getStyleClass().addAll("pm-team-role-badge", "pm-team-role-"
                        + (member.role != null ? member.role.toLowerCase().replace("_", "") : "default"));
                HBox cell = new HBox(roleBadge);
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.setMinWidth(130);
                cell.setPrefWidth(130);
                cell.setMaxWidth(130);
                row.getChildren().add(cell);
            }
        }

        // Col 4: Department (width 120)
        if (colVisible[4]) {
            if (isHeader) {
                row.getChildren().add(createTeamCol("Dept", 120));
            } else {
                String deptName = "—";
                if (member.departmentId != null && member.departmentId > 0) {
                    try {
                        Department dept = new ServiceDepartment().getById(member.departmentId);
                        if (dept != null) deptName = dept.getName();
                    } catch (SQLException ignored) {}
                }
                Label dept = new Label(deptName);
                dept.getStyleClass().add("pm-team-dept");
                dept.setMinWidth(120);
                dept.setPrefWidth(120);
                dept.setMaxWidth(120);
                row.getChildren().add(dept);
            }
        }

        return row;
    }

    private HBox createTeamCol(String text, double width) {
        Label lbl = new Label(text);
        lbl.getStyleClass().add("pm-team-col-header");
        HBox cell = new HBox(lbl);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        return cell;
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

        // Auto-filter ComboBox as user types (with guard to prevent infinite recursion)
        final boolean[] filtering = {false};
        addMemberBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (filtering[0]) return;
            filtering[0] = true;
            try {
                if (newVal == null || newVal.isEmpty()) {
                    addMemberBox.getItems().setAll(allUserDisplayNames);
                    return;
                }
                String lower = newVal.toLowerCase();
                List<String> filtered = allUserDisplayNames.stream()
                        .filter(d -> d.toLowerCase().contains(lower))
                        .collect(java.util.stream.Collectors.toList());
                addMemberBox.getItems().setAll(filtered);
                // Restore the typed text since setAll may clear it
                addMemberBox.getEditor().setText(newVal);
                addMemberBox.getEditor().positionCaret(newVal.length());
                if (!filtered.isEmpty() && !addMemberBox.isShowing()) {
                    addMemberBox.show();
                }
            } finally {
                filtering[0] = false;
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
            AppThreadPool.io(() -> {
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
                    // n8n webhook
                    triggerN8nWebhook(isEdit ? "project-update" : "project-create", Map.of(
                            "project_id", project.getId(),
                            "project_name", project.getName(),
                            "status", project.getStatus(),
                            "manager", currentUser.getFirstName() + " " + currentUser.getLastName(),
                            "timestamp", java.time.Instant.now().toString()));

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
            });
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
                AppThreadPool.io(() -> {
                    try {
                        String deletedName = currentProject.getName();
                        int deletedId = currentProject.getId();
                        serviceProject.supprimer(deletedId);
                        triggerN8nWebhook("project-delete", Map.of(
                                "project_id", deletedId,
                                "project_name", deletedName,
                                "deleted_by", currentUser.getFirstName() + " " + currentUser.getLastName(),
                                "timestamp", java.time.Instant.now().toString()));
                        Platform.runLater(() -> {
                            backToProjectList();
                            SoundManager.getInstance().play(SoundManager.PROJECT_DELETED);
                        });
                    } catch (SQLException e) {
                        Platform.runLater(() -> showError("Failed to delete project: " + e.getMessage()));
                    }
                });
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
        statusBox.getItems().addAll("TODO", "IN_PROGRESS", "IN_REVIEW", "DONE");
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

        // Assignee — only project team members
        Label assigneeLabel = new Label("Assignee");
        assigneeLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> assigneeBox = new ComboBox<>();
        Map<String, Integer> assigneeIdMap = new LinkedHashMap<>();
        assigneeBox.getItems().add("Unassigned");
        assigneeIdMap.put("Unassigned", 0);

        // Load team members for this project
        List<ServiceProjectMember.ProjectMember> teamMembers = new ArrayList<>();
        try {
            teamMembers = new ServiceProjectMember().getMembers(currentProject.getId());
        } catch (SQLException ignored) {}

        // Always include the project manager
        User manager = userCache.get(currentProject.getManagerId());
        if (manager != null) {
            String display = manager.getFirstName() + " " + manager.getLastName() + " (Manager)";
            assigneeBox.getItems().add(display);
            assigneeIdMap.put(display, manager.getId());
            if (isEdit && existing.getAssigneeId() == manager.getId()) assigneeBox.setValue(display);
        }

        // Add team members (skip manager — already added)
        for (ServiceProjectMember.ProjectMember m : teamMembers) {
            if (m.userId == currentProject.getManagerId()) continue;
            String display = m.firstName + " " + m.lastName;
            assigneeBox.getItems().add(display);
            assigneeIdMap.put(display, m.userId);
            if (isEdit && existing.getAssigneeId() == m.userId) assigneeBox.setValue(display);
        }

        if (teamMembers.isEmpty() && manager == null) {
            assigneeBox.getItems().add("── No team members ──");
        }
        if (assigneeBox.getValue() == null) assigneeBox.setValue("Unassigned");
        assigneeBox.getStyleClass().add("pm-form-control");

        // Workload warning label — shows when selected assignee has >= 5 active tasks
        Label workloadWarning = new Label();
        workloadWarning.setStyle("-fx-text-fill: #FF6B35; -fx-font-size: 11; -fx-padding: 2 0 0 0;");
        workloadWarning.setWrapText(true);
        workloadWarning.setVisible(false);
        workloadWarning.setManaged(false);
        final List<ServiceProjectMember.ProjectMember> finalTeamMembers = teamMembers;
        assigneeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            Integer uid = assigneeIdMap.get(newVal);
            if (uid == null || uid == 0) { workloadWarning.setVisible(false); workloadWarning.setManaged(false); return; }
            try {
                int activeCount = serviceTask.getActiveTaskCount(uid);
                if (activeCount >= MAX_ACTIVE_TASKS) {
                    workloadWarning.setText("🚫 LIMIT REACHED — " + activeCount + "/" + MAX_ACTIVE_TASKS + " active tasks. Cannot assign more.");
                    workloadWarning.setStyle("-fx-text-fill: #FF4444; -fx-font-size: 11; -fx-font-weight: bold; -fx-padding: 2 0 0 0;");
                    workloadWarning.setVisible(true);
                    workloadWarning.setManaged(true);
                } else if (activeCount >= WARN_THRESHOLD) {
                    workloadWarning.setText("⚠️ High workload — " + activeCount + "/" + MAX_ACTIVE_TASKS + " active tasks.");
                    workloadWarning.setStyle("-fx-text-fill: #FF6B35; -fx-font-size: 11; -fx-padding: 2 0 0 0;");
                    workloadWarning.setVisible(true);
                    workloadWarning.setManaged(true);
                } else {
                    workloadWarning.setVisible(false);
                    workloadWarning.setManaged(false);
                }
            } catch (Exception ignored) { workloadWarning.setVisible(false); workloadWarning.setManaged(false); }
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
                assigneeLabel, assigneeBox, workloadWarning,
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
            // ── Hard limit: block if assignee already at MAX_ACTIVE_TASKS ──
            if (task.getAssigneeId() > 0) {
                try {
                    int activeCount = serviceTask.getActiveTaskCount(task.getAssigneeId());
                    // Skip check if editing and assignee hasn't changed
                    boolean sameAssignee = isEdit && existing.getAssigneeId() == task.getAssigneeId();
                    if (!sameAssignee && activeCount >= MAX_ACTIVE_TASKS) {
                        showError("Cannot assign — this person already has " + activeCount + "/" + MAX_ACTIVE_TASKS + " active tasks.\n\nPlease complete or reassign some tasks first.");
                        return;
                    }
                } catch (Exception ignored) {}
            }
            AppThreadPool.io(() -> {
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
                    // n8n webhook
                    triggerN8nWebhook(isEdit ? "task-update" : "task-create", Map.of(
                            "task_id", task.getId(),
                            "task_title", task.getTitle(),
                            "project_name", currentProject.getName(),
                            "status", task.getStatus(),
                            "priority", task.getPriority(),
                            "timestamp", java.time.Instant.now().toString()));
                    Platform.runLater(() -> {
                        loadTaskBoard();
                        SoundManager.getInstance().play(SoundManager.TASK_CREATED);
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showError("Failed to save task: " + e.getMessage()));
                }
            });
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
                AppThreadPool.io(() -> {
                    try {
                        serviceTask.supprimer(task.getId());
                        triggerN8nWebhook("task-delete", Map.of(
                                "task_id", task.getId(),
                                "task_title", task.getTitle(),
                                "project_name", currentProject.getName(),
                                "deleted_by", currentUser.getFirstName() + " " + currentUser.getLastName(),
                                "timestamp", java.time.Instant.now().toString()));
                        Platform.runLater(() -> {
                            loadTaskBoard();
                            SoundManager.getInstance().play(SoundManager.TASK_DELETED);
                        });
                    } catch (SQLException e) {
                        Platform.runLater(() -> showError("Failed to delete task: " + e.getMessage()));
                    }
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  TASK REVIEW & FEEDBACK
    // ════════════════════════════════════════════════════════

    private void submitTaskForReview(Task task) {
        // ── Submit dialog: text field + file upload ──
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Submit for Review");
        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("pm-dialog-pane");
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.setText("📤 Submit");
        okBtn.getStyleClass().add("pm-dialog-ok-btn");
        ((Button) pane.lookupButton(ButtonType.CANCEL)).getStyleClass().add("pm-dialog-cancel-btn");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("pm-dialog-form");
        content.setPrefWidth(460);

        Label header = new Label("📤  Submit \"" + task.getTitle() + "\"");
        header.getStyleClass().add("pm-dialog-header");

        Label infoLabel = new Label("Describe what you've done and optionally attach a file.");
        infoLabel.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 12;");
        infoLabel.setWrapText(true);

        // Submission text
        Label textLabel = new Label("Submission Notes");
        textLabel.getStyleClass().add("pm-form-label");
        TextArea submissionText = new TextArea();
        submissionText.setPromptText("Describe your work, deliverables, or any notes for the reviewer...");
        submissionText.setPrefRowCount(5);
        submissionText.setWrapText(true);
        submissionText.getStyleClass().add("pm-form-control");

        // File upload
        Label fileLabel = new Label("Attach File (optional)");
        fileLabel.getStyleClass().add("pm-form-label");
        HBox fileRow = new HBox(10);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        Label fileNameLabel = new Label("No file selected");
        fileNameLabel.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 12;");
        final File[] selectedFile = {null};
        Button chooseFile = new Button("📎 Choose File");
        chooseFile.getStyleClass().add("pm-btn-secondary");
        chooseFile.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Attach File");
            fc.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"),
                    new javafx.stage.FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt"),
                    new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif")
            );
            File f = fc.showOpenDialog(pane.getScene().getWindow());
            if (f != null) {
                selectedFile[0] = f;
                fileNameLabel.setText("📄 " + f.getName() + " (" + (f.length() / 1024) + " KB)");
                fileNameLabel.setStyle("-fx-text-fill: #34d399; -fx-font-size: 12;");
            }
        });
        fileRow.getChildren().addAll(chooseFile, fileNameLabel);

        content.getChildren().addAll(header, infoLabel, textLabel, submissionText, fileLabel, fileRow);
        pane.setContent(content);

        okBtn.setDisable(true);
        submissionText.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.trim().isEmpty()));

        dialog.showAndWait().ifPresent(btn -> {
            if (btn != ButtonType.OK) return;
            SoundManager.getInstance().play(SoundManager.TASK_SUBMITTED);
            AppThreadPool.io(() -> {
                try {
                    // Upload file via ImgBB or server if selected
                    if (selectedFile[0] != null) {
                        String fileUrl = uploadFileToImgBB(selectedFile[0]);
                        if (fileUrl != null) {
                            task.setSubmissionFile(fileUrl);
                        }
                    }

                    String subText = submissionText.getText().trim();
                    task.setSubmissionText(subText);
                    serviceTask.submitForReview(task.getId(), subText);
                    if (task.getSubmissionFile() != null) {
                        Map<String, Object> fileBody = new HashMap<>();
                        fileBody.put("submission_file", task.getSubmissionFile());
                        utils.ApiClient.put("/tasks/" + task.getId(), fileBody);
                    }
                    task.setStatus("IN_REVIEW");

                    // Notify project manager
                    User assignee = userCache.get(task.getAssigneeId());
                    String empName = assignee != null ? assignee.getFirstName() + " " + assignee.getLastName() : "Employee";
                    Project proj = null;
                    try {
                        for (Project p : serviceProject.recuperer()) {
                            if (p.getId() == task.getProjectId()) { proj = p; break; }
                        }
                    } catch (Exception ignored) {}
                    if (proj != null) {
                        serviceNotification.notifyTaskSubmitted(
                                proj.getManagerId(), empName, task.getTitle(),
                                proj.getName(), task.getId());
                    }

                    Platform.runLater(() -> {
                        if (currentProject != null) loadTaskBoard();
                        refreshEmployeeDashboard();
                        showInfo("Task submitted for review! Awaiting manager approval.");
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showError("Failed: " + e.getMessage()));
                }
            });
        });
    }

    private void declineTask(Task task) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Decline Task");
        dlg.setHeaderText("Decline \"" + task.getTitle() + "\"");
        dlg.setContentText("Reason (required):");
        DialogHelper.theme(dlg);
        dlg.getEditor().setPromptText("e.g. overloaded, outside my expertise...");
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setDisable(true);
        dlg.getEditor().textProperty().addListener((o, ov, nv) -> okBtn.setDisable(nv.trim().isEmpty()));

        dlg.showAndWait().ifPresent(reason -> {
            if (reason.trim().isEmpty()) return;
            AppThreadPool.io(() -> {
                try {
                    // Move task back to TODO and unassign
                    task.setStatus("TODO");
                    task.setAssigneeId(0);
                    serviceTask.modifier(task);

                    // Notify project manager
                    String empName = currentUser.getFirstName() + " " + currentUser.getLastName();
                    serviceNotification.create(
                            currentProject.getManagerId(),
                            "TASK",
                            "\uD83D\uDEAB Task Declined",
                            empName + " declined \"" + task.getTitle() + "\" — Reason: " + reason.trim(),
                            task.getId(), "TASK");

                    Platform.runLater(() -> {
                        loadTaskBoard();
                        showInfo("Task declined. Manager has been notified.");
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> showError("Failed to decline task: " + e.getMessage()));
                }
            });
        });
    }

    // ════════════════════════════════════════════════════════
    //  VIEW EMPLOYEE SUBMISSION
    // ════════════════════════════════════════════════════════

    /**
     * Shows a read-only popup displaying what the employee/gig worker submitted.
     * Includes submission notes, attached file, and review info if any.
     */
    private void showSubmissionPopup(Task task) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Task Submission");
        dialog.setHeaderText(null);

        DialogHelper.theme(dialog);
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("pm-dialog-pane");
        pane.getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) pane.lookupButton(ButtonType.CLOSE);
        closeBtn.getStyleClass().add("pm-dialog-cancel-btn");

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("pm-dialog-form");
        content.setPrefWidth(520);

        // ── Header ──
        Label header = new Label("📋  Task Submission");
        header.setStyle("-fx-font-size: 18; -fx-font-weight: bold; -fx-text-fill: #F0EDEE;");

        // ── Task info ──
        Label taskTitle = new Label("📌 " + task.getTitle());
        taskTitle.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 15; -fx-font-weight: bold;");
        taskTitle.setWrapText(true);

        User assignee = userCache.get(task.getAssigneeId());
        String assigneeName = assignee != null ? assignee.getFirstName() + " " + assignee.getLastName() : "Unknown";
        String assigneeRole = assignee != null ? assignee.getRole() : "";

        HBox assigneeRow = new HBox(8);
        assigneeRow.setAlignment(Pos.CENTER_LEFT);
        Label avatar = new Label(assignee != null ?
                ("" + assignee.getFirstName().charAt(0) + assignee.getLastName().charAt(0)).toUpperCase() : "??");
        String[] colors = {"#6366f1", "#ec4899", "#14b8a6", "#f59e0b", "#8b5cf6", "#ef4444"};
        String avColor = colors[Math.abs(assigneeName.hashCode()) % colors.length];
        avatar.setStyle("-fx-background-color: " + avColor + "; -fx-text-fill: white; " +
                "-fx-min-width: 36; -fx-min-height: 36; -fx-max-width: 36; -fx-max-height: 36; " +
                "-fx-background-radius: 18; -fx-alignment: center; -fx-font-weight: bold; -fx-font-size: 13;");
        VBox nameBox = new VBox(1);
        Label nameLabel = new Label(assigneeName);
        nameLabel.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 13; -fx-font-weight: bold;");
        Label roleLabel = new Label(assigneeRole.replace("_", " "));
        roleLabel.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 11;");
        nameBox.getChildren().addAll(nameLabel, roleLabel);
        assigneeRow.getChildren().addAll(avatar, nameBox);

        // Status badge
        Label statusBadge = new Label(formatStatus(task.getStatus()));
        statusBadge.getStyleClass().addAll("emp-status-badge",
                "emp-status-" + task.getStatus().toLowerCase().replace("_", "-"));
        statusBadge.setStyle(statusBadge.getStyle() + "-fx-font-size: 12; -fx-padding: 4 12;");

        content.getChildren().addAll(header, new Separator(), taskTitle, assigneeRow, statusBadge);

        // ── Submission text ──
        if (task.getSubmissionText() != null && !task.getSubmissionText().isEmpty()) {
            Label subLabel = new Label("📝 Submission Notes");
            subLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 10 0 4 0;");

            TextArea subText = new TextArea(task.getSubmissionText());
            subText.setEditable(false);
            subText.setWrapText(true);
            subText.setPrefRowCount(Math.min(8, (int) Math.ceil(task.getSubmissionText().length() / 60.0) + 1));
            subText.getStyleClass().add("pm-form-control");
            subText.setStyle("-fx-opacity: 0.9; -fx-background-color: #12111A; -fx-text-fill: #E0E0F0;");

            content.getChildren().addAll(subLabel, subText);
        } else {
            Label noSub = new Label("ℹ️ No submission notes provided");
            noSub.setStyle("-fx-text-fill: #6B6B7B; -fx-font-size: 12; -fx-padding: 10 0 0 0;");
            content.getChildren().add(noSub);
        }

        // ── Attached file ──
        if (task.getSubmissionFile() != null && !task.getSubmissionFile().isEmpty()) {
            Label fileLabel = new Label("📎 Attached File");
            fileLabel.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 8 0 4 0;");

            HBox fileRow = new HBox(10);
            fileRow.setAlignment(Pos.CENTER_LEFT);
            fileRow.setPadding(new Insets(8, 12, 8, 12));
            fileRow.setStyle("-fx-background-color: #1A1A2E; -fx-background-radius: 8; -fx-border-color: #2D2D3F; -fx-border-radius: 8;");

            Label fileIcon = new Label("🖼️");
            fileIcon.setStyle("-fx-font-size: 20;");

            Hyperlink fileLink = new Hyperlink(task.getSubmissionFile().length() > 60
                    ? task.getSubmissionFile().substring(0, 60) + "…" : task.getSubmissionFile());
            fileLink.setStyle("-fx-text-fill: #7B61FF; -fx-font-size: 12;");
            fileLink.setWrapText(true);
            fileLink.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new URI(task.getSubmissionFile()));
                } catch (Exception ex) {
                    showError("Can't open file: " + ex.getMessage());
                }
            });

            Button copyBtn = new Button("📋 Copy URL");
            copyBtn.getStyleClass().add("pm-btn-ghost");
            copyBtn.setStyle("-fx-font-size: 11;");
            copyBtn.setOnAction(e -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(task.getSubmissionFile());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
                copyBtn.setText("✅ Copied!");
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override public void run() { Platform.runLater(() -> copyBtn.setText("📋 Copy URL")); }
                }, 2000);
            });

            Button downloadBtn = new Button("⬇ Download");
            downloadBtn.getStyleClass().add("pm-btn-ghost");
            downloadBtn.setStyle("-fx-font-size: 11;");
            downloadBtn.setOnAction(e -> {
                String url = task.getSubmissionFile();
                // Guess extension from URL
                String ext = ".png";
                if (url.contains(".")) {
                    String last = url.substring(url.lastIndexOf('.'));
                    if (last.matches("\\.[a-zA-Z0-9]{2,5}(\\?.*)?")) {
                        ext = last.contains("?") ? last.substring(0, last.indexOf('?')) : last;
                    }
                }
                javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
                fc.setTitle("Save Attachment");
                fc.setInitialFileName(task.getTitle().replaceAll("[^a-zA-Z0-9_\\-]", "_") + ext);
                fc.getExtensionFilters().addAll(
                        new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"),
                        new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
                        new javafx.stage.FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt")
                );
                java.io.File saveFile = fc.showSaveDialog(downloadBtn.getScene().getWindow());
                if (saveFile != null) {
                    downloadBtn.setText("⏳ Downloading...");
                    downloadBtn.setDisable(true);
                    AppThreadPool.io(() -> {
                        try {
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(new URI(url))
                                    .timeout(Duration.ofSeconds(30))
                                    .GET().build();
                            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                            if (resp.statusCode() == 200) {
                                Files.write(saveFile.toPath(), resp.body());
                                Platform.runLater(() -> {
                                    downloadBtn.setText("✅ Saved!");
                                    downloadBtn.setDisable(false);
                                    new java.util.Timer().schedule(new java.util.TimerTask() {
                                        @Override public void run() { Platform.runLater(() -> downloadBtn.setText("⬇ Download")); }
                                    }, 2000);
                                });
                            } else {
                                Platform.runLater(() -> {
                                    downloadBtn.setText("⬇ Download");
                                    downloadBtn.setDisable(false);
                                    showError("Download failed: HTTP " + resp.statusCode());
                                });
                            }
                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                downloadBtn.setText("⬇ Download");
                                downloadBtn.setDisable(false);
                                showError("Download failed: " + ex.getMessage());
                            });
                        }
                    });
                }
            });

            fileRow.getChildren().addAll(fileIcon, fileLink, copyBtn, downloadBtn);
            content.getChildren().addAll(fileLabel, fileRow);
        }

        // ── Review info (if already reviewed) ──
        if (task.getReviewStatus() != null && !task.getReviewStatus().isEmpty()) {
            Label reviewLabel = new Label("⭐ Review");
            reviewLabel.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 10 0 4 0;");

            VBox reviewBox = new VBox(6);
            reviewBox.setPadding(new Insets(10, 14, 10, 14));
            reviewBox.setStyle("-fx-background-color: #1A1A2E; -fx-background-radius: 8; -fx-border-color: #2D2D3F; -fx-border-radius: 8;");

            // Status
            String statusEmoji = "APPROVED".equals(task.getReviewStatus()) ? "✅" :
                    "NEEDS_REVISION".equals(task.getReviewStatus()) ? "🔄" : "❌";
            Label rvStatus = new Label(statusEmoji + " " + task.getReviewStatus().replace("_", " "));
            rvStatus.setStyle("-fx-text-fill: #E0E0F0; -fx-font-size: 13; -fx-font-weight: bold;");

            // Rating — MUI-style read-only
            if (task.getReviewRating() != null && task.getReviewRating() > 0) {
                HBox ratingStars = createReadOnlyStarRating(task.getReviewRating(), 5);
                reviewBox.getChildren().add(ratingStars);
            }

            reviewBox.getChildren().add(0, rvStatus);

            // Feedback
            if (task.getReviewFeedback() != null && !task.getReviewFeedback().isEmpty()) {
                Label fb = new Label("💬 " + task.getReviewFeedback());
                fb.setStyle("-fx-text-fill: #B0B0C0; -fx-font-size: 12;");
                fb.setWrapText(true);
                reviewBox.getChildren().add(fb);
            }

            if (task.getReviewDate() != null) {
                Label dateLabel = new Label("📅 Reviewed: " + task.getReviewDate().toString());
                dateLabel.setStyle("-fx-text-fill: #6B6B7B; -fx-font-size: 11;");
                reviewBox.getChildren().add(dateLabel);
            }

            content.getChildren().addAll(reviewLabel, reviewBox);
        }

        // ── Action buttons for manager ──
        if ("IN_REVIEW".equals(task.getStatus()) &&
                (currentProject.getManagerId() == currentUser.getId() || isAdmin)) {
            HBox actions = new HBox(10);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.setPadding(new Insets(12, 0, 0, 0));

            Button reviewBtn = new Button("⭐ Review & Decide");
            reviewBtn.getStyleClass().add("pm-btn-primary");
            reviewBtn.setOnAction(e -> {
                dialog.close();
                showReviewDialog(task);
            });
            actions.getChildren().add(reviewBtn);
            content.getChildren().add(actions);
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        pane.setContent(scroll);

        dialog.showAndWait();
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
        content.setPrefWidth(480);

        Label header = new Label("\u2B50  Review Task");
        header.getStyleClass().add("review-dialog-header");

        // Task info
        User assignee = userCache.get(task.getAssigneeId());
        String assigneeName = assignee != null ? assignee.getFirstName() + " " + assignee.getLastName() : "Unassigned";
        Label taskInfo = new Label("\uD83D\uDCCB " + task.getTitle() + "\n\uD83D\uDC64 " + assigneeName);
        taskInfo.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 12;");
        taskInfo.setWrapText(true);

        content.getChildren().addAll(header, taskInfo);

        // ── Show employee's submission (text + file) ──
        if (task.getSubmissionText() != null && !task.getSubmissionText().isEmpty()) {
            Label subLabel = new Label("📝 Employee's Submission:");
            subLabel.getStyleClass().add("pm-form-label");
            subLabel.setStyle("-fx-padding: 8 0 2 0;");
            TextArea subPreview = new TextArea(task.getSubmissionText());
            subPreview.setEditable(false);
            subPreview.setWrapText(true);
            subPreview.setPrefRowCount(3);
            subPreview.getStyleClass().add("pm-form-control");
            subPreview.setStyle("-fx-opacity: 0.85;");
            content.getChildren().addAll(subLabel, subPreview);
        }
        if (task.getSubmissionFile() != null && !task.getSubmissionFile().isEmpty()) {
            Hyperlink fileLink = new Hyperlink("📎 View Attached File");
            fileLink.setStyle("-fx-text-fill: #7B61FF; -fx-font-size: 12;");
            fileLink.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new URI(task.getSubmissionFile()));
                } catch (Exception ex) {
                    showError("Can't open file: " + ex.getMessage());
                }
            });
            content.getChildren().add(fileLink);
        }

        // Rating (1-5 stars) — MUI-style interactive
        Label ratingLabel = new Label("⭐ Rating");
        ratingLabel.getStyleClass().add("pm-form-label");
        final int[] rating = {0};
        HBox stars = createInteractiveStarRating(5, 0, rating);

        // Review status
        Label statusLabel = new Label("Review Decision");
        statusLabel.getStyleClass().add("pm-form-label");
        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll("APPROVED", "NEEDS_REVISION", "REJECTED");
        statusBox.setValue("APPROVED");
        statusBox.getStyleClass().add("pm-form-control");

        // Decision explanation
        Label decisionExpl = new Label("✅ Approved → task moves to DONE");
        decisionExpl.setStyle("-fx-text-fill: #34d399; -fx-font-size: 11;");
        statusBox.valueProperty().addListener((obs, o, n) -> {
            switch (n) {
                case "APPROVED":
                    decisionExpl.setText("✅ Approved → task moves to DONE");
                    decisionExpl.setStyle("-fx-text-fill: #34d399; -fx-font-size: 11;");
                    break;
                case "NEEDS_REVISION":
                    decisionExpl.setText("🔄 Needs Revision → task moves to IN PROGRESS (stays assigned)");
                    decisionExpl.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 11;");
                    break;
                case "REJECTED":
                    decisionExpl.setText("❌ Rejected → task moves to TODO (unassigned)");
                    decisionExpl.setStyle("-fx-text-fill: #FF6B6B; -fx-font-size: 11;");
                    break;
            }
        });

        // Feedback text
        Label fbLabel = new Label("Feedback");
        fbLabel.getStyleClass().add("pm-form-label");
        TextArea feedbackArea = new TextArea();
        feedbackArea.setPromptText("Write your feedback for the employee...");
        feedbackArea.setPrefRowCount(4);
        feedbackArea.setWrapText(true);
        feedbackArea.getStyleClass().addAll("pm-form-control", "review-feedback-area");

        content.getChildren().addAll(ratingLabel, stars, statusLabel, statusBox, decisionExpl, fbLabel, feedbackArea);
        pane.setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK && rating[0] > 0) {
                AppThreadPool.io(() -> {
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

                        // If rejected, unassign
                        if ("REJECTED".equals(statusBox.getValue())) {
                            task.setAssigneeId(0);
                            task.setStatus("TODO");
                            serviceTask.modifier(task);
                        }

                        Platform.runLater(() -> {
                            loadTaskBoard();
                            SoundManager.getInstance().play(SoundManager.REVIEW_SUBMITTED);
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showError("Review failed: " + e.getMessage()));
                    }
                });
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
        confirm.setContentText("AI (Z.AI GLM) will analyze your project to create a smart task breakdown.\n" +
                "This will ADD new tasks — existing tasks won't be affected.");
        DialogHelper.theme(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                btnAiPlan.setDisable(true);
                btnAiPlan.setText("🤖 Generating...");

                AppThreadPool.io(() -> {
                    try {
                        List<String[]> generatedTasks;
                        // Try Z.AI first, fall back to keyword-based
                        try {
                            ZAIService zai = new ZAIService();
                            int teamSize = 1;
                            try { teamSize = new ServiceProjectMember().getMembers(currentProject.getId()).size(); } catch (Exception ignored) {}
                            if (teamSize == 0) teamSize = 1;
                            String response = zai.generateProjectTasks(
                                    currentProject.getName(),
                                    currentProject.getDescription() != null ? currentProject.getDescription() : "",
                                    teamSize);
                            generatedTasks = parseAiTaskResponse(response);
                        } catch (Exception aiErr) {
                            // Fallback to keyword-based
                            generatedTasks = generateSmartTasks(
                                    currentProject.getName(),
                                    currentProject.getDescription() != null ? currentProject.getDescription() : "");
                        }

                        int created = 0;
                        for (String[] taskData : generatedTasks) {
                            Task newTask = new Task();
                            newTask.setProjectId(currentProject.getId());
                            newTask.setTitle(taskData[0]);
                            newTask.setDescription(taskData[1]);
                            newTask.setStatus("TODO");
                            newTask.setPriority(taskData[2]);
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
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            btnAiPlan.setDisable(false);
                            btnAiPlan.setText("🤖 Plan with AI");
                            showError("AI planning failed: " + e.getMessage());
                        });
                    }
                });
            }
        });
    }

    /**
     * Parse Z.AI JSON response for task generation.
     */
    private List<String[]> parseAiTaskResponse(String json) {
        List<String[]> tasks = new ArrayList<>();
        try {
            // Strip markdown code fences if present
            String clean = json.trim();
            if (clean.startsWith("```")) {
                clean = clean.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
            }
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(clean).getAsJsonArray();
            for (com.google.gson.JsonElement el : arr) {
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                String title = obj.has("title") ? obj.get("title").getAsString() : "Untitled Task";
                String desc = obj.has("description") ? obj.get("description").getAsString() : "";
                String priority = obj.has("priority") ? obj.get("priority").getAsString().toUpperCase() : "MEDIUM";
                if (!priority.matches("HIGH|MEDIUM|LOW")) priority = "MEDIUM";
                tasks.add(new String[]{title, desc, priority});
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage());
        }
        if (tasks.isEmpty()) throw new RuntimeException("AI returned no tasks");
        return tasks;
    }

    // ════════════════════════════════════════════════════════
    //  AI: SPRINT PLANNER
    // ════════════════════════════════════════════════════════

    @FXML
    private void showSprintPlanner() {
        if (currentProject == null) return;

        // Build task list JSON from board
        List<Task> allTasks;
        try {
            allTasks = serviceTask.getByProject(currentProject.getId());
        } catch (SQLException e) {
            showError("Failed to load tasks: " + e.getMessage());
            return;
        }
        if (allTasks.isEmpty()) {
            showInfo("No tasks to plan. Add some tasks first.");
            return;
        }

        // Ask for sprint parameters
        TextInputDialog daysDialog = new TextInputDialog("10");
        daysDialog.setTitle("Sprint Planner");
        daysDialog.setHeaderText("📋 AI Sprint Planner");
        daysDialog.setContentText("Sprint duration (days):");
        DialogHelper.theme(daysDialog);

        daysDialog.showAndWait().ifPresent(daysStr -> {
            int sprintDays;
            try { sprintDays = Integer.parseInt(daysStr.trim()); } catch (NumberFormatException e) { sprintDays = 10; }

            btnSprintPlan.setDisable(true);
            btnSprintPlan.setText("⏳ Planning...");

            StringBuilder taskJson = new StringBuilder("[");
            for (int i = 0; i < allTasks.size(); i++) {
                Task t = allTasks.get(i);
                if (i > 0) taskJson.append(",");
                taskJson.append(String.format("{\"id\":%d,\"title\":\"%s\",\"status\":\"%s\",\"priority\":\"%s\"}",
                        t.getId(), t.getTitle().replace("\"", "'"), t.getStatus(), t.getPriority()));
            }
            taskJson.append("]");

            int teamSize = 1;
            try { teamSize = new ServiceProjectMember().getMembers(currentProject.getId()).size(); } catch (Exception ignored) {}
            if (teamSize == 0) teamSize = 1;

            final int ts = teamSize;
            final int sd = sprintDays;
            final String tj = taskJson.toString();

            AppThreadPool.io(() -> {
                try {
                    ZAIService zai = new ZAIService();
                    String result = zai.planSprint(tj, ts, sd);

                    Platform.runLater(() -> {
                        btnSprintPlan.setDisable(false);
                        btnSprintPlan.setText("📋 Sprint Plan");
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                        showSprintPlanResult(result);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        btnSprintPlan.setDisable(false);
                        btnSprintPlan.setText("📋 Sprint Plan");
                        showError("Sprint planning failed: " + e.getMessage());
                    });
                }
            });
        });
    }

    /**
     * Parse and display Sprint Plan AI result as a rich formatted dialog.
     */
    private void showSprintPlanResult(String jsonResult) {
        try {
            String clean = jsonResult.trim();
            if (clean.startsWith("```")) {
                clean = clean.replaceAll("^```[a-z]*\\n?", "").replaceAll("\\n?```$", "").trim();
            }
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(clean).getAsJsonObject();

            com.google.gson.JsonArray tasksArr = root.getAsJsonArray("tasks");
            int totalPoints = root.has("total_points") ? root.get("total_points").getAsInt() : 0;
            int capacityPoints = root.has("capacity_points") ? root.get("capacity_points").getAsInt() : 1;
            com.google.gson.JsonArray recommendedIds = root.has("recommended_ids") ? root.getAsJsonArray("recommended_ids") : new com.google.gson.JsonArray();
            com.google.gson.JsonArray warningsArr = root.has("warnings") ? root.getAsJsonArray("warnings") : new com.google.gson.JsonArray();

            Set<Integer> recSet = new HashSet<>();
            for (var el : recommendedIds) recSet.add(el.getAsInt());

            int utilization = capacityPoints > 0 ? (totalPoints * 100 / capacityPoints) : 0;

            // ─── Build UI ───
            VBox mainBox = new VBox(16);
            mainBox.setPadding(new Insets(8));
            mainBox.setStyle("-fx-background-color: #0F0E11;");

            // Summary cards row
            HBox summaryRow = new HBox(12);
            summaryRow.setAlignment(Pos.CENTER_LEFT);
            summaryRow.getChildren().addAll(
                    buildAiStatCard("Total Points", String.valueOf(totalPoints), "#2C666E"),
                    buildAiStatCard("Capacity", String.valueOf(capacityPoints), "#07393C"),
                    buildAiStatCard("Utilization", utilization + "%",
                            utilization > 90 ? "#dc2626" : utilization > 70 ? "#2C666E" : "#059669")
            );
            mainBox.getChildren().add(summaryRow);

            // Table header
            HBox headerRow = new HBox();
            headerRow.setStyle("-fx-background-color: #14131A; -fx-background-radius: 8 8 0 0; -fx-padding: 10 16;");
            headerRow.setAlignment(Pos.CENTER_LEFT);
            Label hId = new Label("#");        hId.setPrefWidth(40);  hId.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11; -fx-font-weight: bold;");
            Label hTitle = new Label("Task");  hTitle.setPrefWidth(220); hTitle.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11; -fx-font-weight: bold;");
            Label hPts = new Label("Points");  hPts.setPrefWidth(60);  hPts.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11; -fx-font-weight: bold;");
            Label hSpr = new Label("Sprint?"); hSpr.setPrefWidth(70);  hSpr.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11; -fx-font-weight: bold;");
            Label hReason = new Label("Reason"); hReason.setPrefWidth(200); hReason.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11; -fx-font-weight: bold;");
            headerRow.getChildren().addAll(hId, hTitle, hPts, hSpr, hReason);

            VBox tableBox = new VBox();
            tableBox.setStyle("-fx-border-color: #1C1B22; -fx-border-radius: 8; -fx-border-width: 1; -fx-background-radius: 8;");
            tableBox.getChildren().add(headerRow);

            // Task rows
            for (int i = 0; i < tasksArr.size(); i++) {
                com.google.gson.JsonObject t = tasksArr.get(i).getAsJsonObject();
                int id = t.has("id") ? t.get("id").getAsInt() : (i + 1);
                String title = t.has("title") ? t.get("title").getAsString() : "Task " + id;
                int points = t.has("points") ? t.get("points").getAsInt() : 0;
                String reason = t.has("reason") ? t.get("reason").getAsString() : "";
                boolean inSprint = recSet.contains(id);

                HBox row = new HBox();
                row.setAlignment(Pos.CENTER_LEFT);
                String rowBg = (i % 2 == 0) ? "#0F0E11" : "#12111A";
                String borderRadius = (i == tasksArr.size() - 1) ? "-fx-background-radius: 0 0 8 8;" : "";
                row.setStyle("-fx-background-color: " + rowBg + "; -fx-padding: 10 16; " + borderRadius);

                Label cId = new Label(String.valueOf(id));  cId.setPrefWidth(40);  cId.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 12;");
                Label cTitle = new Label(title); cTitle.setPrefWidth(220); cTitle.setWrapText(true); cTitle.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 12; -fx-font-weight: 600;");

                // Points badge
                Label cPts = new Label(String.valueOf(points));
                cPts.setPrefWidth(60);
                String ptsColor = points >= 8 ? "#dc2626" : points >= 5 ? "#f59e0b" : "#059669";
                cPts.setStyle("-fx-text-fill: " + ptsColor + "; -fx-font-size: 12; -fx-font-weight: bold;");

                // Sprint badge
                Label cSpr = new Label(inSprint ? "✓ Yes" : "✗ No");
                cSpr.setPrefWidth(70);
                cSpr.setStyle("-fx-text-fill: " + (inSprint ? "#34d399" : "#6B6B78") + "; -fx-font-size: 12; -fx-font-weight: 600;");

                Label cReason = new Label(reason); cReason.setPrefWidth(200); cReason.setWrapText(true); cReason.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 11;");

                row.getChildren().addAll(cId, cTitle, cPts, cSpr, cReason);
                tableBox.getChildren().add(row);
            }
            mainBox.getChildren().add(tableBox);

            // Warnings
            if (warningsArr.size() > 0) {
                VBox warningsBox = new VBox(6);
                warningsBox.setPadding(new Insets(12));
                warningsBox.setStyle("-fx-background-color: rgba(220,38,38,0.08); -fx-border-color: rgba(220,38,38,0.3); -fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1;");
                Label warnTitle = new Label("⚠  Warnings");
                warnTitle.setStyle("-fx-text-fill: #f87171; -fx-font-size: 13; -fx-font-weight: bold;");
                warningsBox.getChildren().add(warnTitle);
                for (var w : warningsArr) {
                    Label wl = new Label("• " + w.getAsString());
                    wl.setWrapText(true);
                    wl.setStyle("-fx-text-fill: #fca5a5; -fx-font-size: 12;");
                    warningsBox.getChildren().add(wl);
                }
                mainBox.getChildren().add(warningsBox);
            }

            ScrollPane scroll = new ScrollPane(mainBox);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(480);
            scroll.setPrefWidth(660);
            scroll.setStyle("-fx-background-color: #0F0E11; -fx-border-color: transparent;");

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Sprint Plan");
            info.setHeaderText("📋 AI Sprint Plan");
            info.getDialogPane().setContent(scroll);
            info.getDialogPane().setPrefWidth(700);
            DialogHelper.theme(info);
            info.showAndWait();

        } catch (Exception e) {
            // Fallback: show with styled dialog if JSON parsing fails
            showAiResultDialog("Sprint Plan", "📋", jsonResult);
        }
    }

    /** Build a small stat card for AI results */
    private VBox buildAiStatCard(String label, String value, String accentColor) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 20, 12, 20));
        card.setStyle("-fx-background-color: #14131A; -fx-background-radius: 10; -fx-border-color: " + accentColor +
                "; -fx-border-radius: 10; -fx-border-width: 1; -fx-min-width: 130;");
        Label vLabel = new Label(value);
        vLabel.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 22; -fx-font-weight: bold;");
        Label nLabel = new Label(label);
        nLabel.setStyle("-fx-text-fill: #6B6B78; -fx-font-size: 11; -fx-font-weight: 500;");
        card.getChildren().addAll(vLabel, nLabel);
        return card;
    }

    /** Build a styled AI result dialog showing formatted text with themed sections */
    private void showAiResultDialog(String title, String emoji, String rawText) {
        VBox mainBox = new VBox(12);
        mainBox.setPadding(new Insets(12));
        mainBox.setStyle("-fx-background-color: #0F0E11;");

        String[] lines = rawText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                Region spacer = new Region();
                spacer.setPrefHeight(6);
                mainBox.getChildren().add(spacer);
                continue;
            }

            Label lbl = new Label(trimmed);
            lbl.setWrapText(true);
            lbl.setMaxWidth(600);

            if (trimmed.startsWith("# ") || trimmed.startsWith("## ") || trimmed.startsWith("### ") ||
                trimmed.matches("^\\d+\\.\\s+\\*\\*.*\\*\\*.*") || trimmed.matches("^#+\\s+.*") ||
                trimmed.matches("^\\*\\*.*\\*\\*$") || trimmed.toUpperCase().equals(trimmed) && trimmed.length() > 3) {
                // Heading
                String cleaned = trimmed.replaceAll("^#+\\s*", "").replaceAll("\\*\\*", "");
                lbl.setText(cleaned);
                lbl.setStyle("-fx-text-fill: #90DDF0; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 8 0 4 0;");
            } else if (trimmed.startsWith("⚠") || trimmed.toLowerCase().contains("warning") || trimmed.toLowerCase().contains("risk") || trimmed.toLowerCase().contains("alert")) {
                lbl.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 12; -fx-padding: 2 0 2 8;");
            } else if (trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]") || trimmed.startsWith("☐") || trimmed.startsWith("☑")) {
                lbl.setStyle("-fx-text-fill: #34d399; -fx-font-size: 12; -fx-padding: 2 0 2 16;");
            } else if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*")) {
                String cleaned = trimmed.replaceAll("\\*\\*", "");
                lbl.setText(cleaned);
                lbl.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 12; -fx-padding: 2 0 2 16;");
            } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
                String cleaned = trimmed.replaceAll("\\*\\*", "");
                lbl.setText(cleaned);
                lbl.setStyle("-fx-text-fill: #F0EDEE; -fx-font-size: 12; -fx-font-weight: 600; -fx-padding: 4 0 2 8;");
            } else {
                String cleaned = trimmed.replaceAll("\\*\\*", "");
                lbl.setText(cleaned);
                lbl.setStyle("-fx-text-fill: #9E9EA8; -fx-font-size: 12; -fx-padding: 2 0 2 8;");
            }
            mainBox.getChildren().add(lbl);
        }

        ScrollPane scroll = new ScrollPane(mainBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(480);
        scroll.setPrefWidth(620);
        scroll.setStyle("-fx-background-color: #0F0E11; -fx-border-color: transparent;");

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle(title);
        info.setHeaderText(emoji + " " + title);
        info.getDialogPane().setContent(scroll);
        info.getDialogPane().setPrefWidth(660);
        DialogHelper.theme(info);
        info.showAndWait();
    }

    @FXML
    private void showMeetingNotes() {
        if (currentProject == null) return;

        // Build task/team context
        List<Task> allTasks;
        try {
            allTasks = serviceTask.getByProject(currentProject.getId());
        } catch (SQLException e) {
            showError("Failed to load tasks: " + e.getMessage());
            return;
        }

        // Two-choice dialog: Prep meeting OR Summarize notes
        Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
        choice.setTitle("Meeting Assistant");
        choice.setHeaderText("📝 Meeting Assistant");
        choice.setContentText("What would you like to do?");
        ButtonType btnPrep = new ButtonType("Prepare Meeting");
        ButtonType btnSummarize = new ButtonType("Summarize Notes");
        choice.getButtonTypes().setAll(btnPrep, btnSummarize, ButtonType.CANCEL);
        DialogHelper.theme(choice);

        choice.showAndWait().ifPresent(bt -> {
            if (bt == btnPrep) {
                prepMeeting(allTasks);
            } else if (bt == btnSummarize) {
                summarizeMeeting();
            }
        });
    }

    private void prepMeeting(List<Task> tasks) {
        btnMeetingNotes.setDisable(true);
        btnMeetingNotes.setText("⏳ Preparing...");

        StringBuilder tasksJson = new StringBuilder();
        for (Task t : tasks) {
            tasksJson.append(String.format("- [%s] %s (%s)\n", t.getStatus(), t.getTitle(), t.getPriority()));
        }

        String teamInfo = "Unknown";
        try {
            var members = new ServiceProjectMember().getMembers(currentProject.getId());
            teamInfo = members.stream().map(m -> m.firstName + " " + m.lastName + " (" + m.role + ")")
                    .collect(Collectors.joining(", "));
        } catch (Exception ignored) {}

        final String ti = teamInfo;
        final String tj = tasksJson.toString();

        AppThreadPool.io(() -> {
            try {
                ZAIService zai = new ZAIService();
                String agenda = zai.prepMeeting(currentProject.getName(), tj, ti);

                Platform.runLater(() -> {
                    btnMeetingNotes.setDisable(false);
                    btnMeetingNotes.setText("📝 Meeting Notes");
                    SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                    showAiResultDialog("Meeting Agenda", "📝", agenda);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnMeetingNotes.setDisable(false);
                    btnMeetingNotes.setText("📝 Meeting Notes");
                    showError("Meeting prep failed: " + e.getMessage());
                });
            }
        });
    }

    private void summarizeMeeting() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Summarize Meeting");
        dialog.setHeaderText("📝 Paste your meeting notes/transcript");
        dialog.setContentText("Notes:");
        DialogHelper.theme(dialog);

        // Replace text field with a TextArea
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Paste meeting notes or transcript here...");
        notesArea.setWrapText(true);
        notesArea.setPrefRowCount(10);
        dialog.getDialogPane().setContent(notesArea);
        dialog.getDialogPane().setPrefWidth(500);

        dialog.showAndWait().ifPresent(ignored -> {
            String notes = notesArea.getText();
            if (notes == null || notes.trim().isEmpty()) return;

            btnMeetingNotes.setDisable(true);
            btnMeetingNotes.setText("⏳ Summarizing...");

            AppThreadPool.io(() -> {
                try {
                    ZAIService zai = new ZAIService();
                    String summary = zai.summarizeMeeting(notes);

                    Platform.runLater(() -> {
                        btnMeetingNotes.setDisable(false);
                        btnMeetingNotes.setText("📝 Meeting Notes");
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                        showAiResultDialog("Meeting Summary", "📝", summary);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        btnMeetingNotes.setDisable(false);
                        btnMeetingNotes.setText("📝 Meeting Notes");
                        showError("Summarization failed: " + e.getMessage());
                    });
                }
            });
        });
    }

    // ════════════════════════════════════════════════════════
    //  AI: DECISION HELPER
    // ════════════════════════════════════════════════════════

    @FXML
    private void showDecisionHelper() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("AI Decision Helper");
        dialog.setHeaderText("⚖ AI-Powered Decision Analysis");
        DialogHelper.theme(dialog);

        VBox content = new VBox(12);
        content.setPrefWidth(500);

        TextField questionField = new TextField();
        questionField.setPromptText("What decision do you need help with?");

        TextArea optionsArea = new TextArea();
        optionsArea.setPromptText("List your options (one per line)...");
        optionsArea.setPrefRowCount(4);
        optionsArea.setWrapText(true);

        TextArea criteriaArea = new TextArea();
        criteriaArea.setPromptText("What criteria matter? (e.g., cost, time, quality — one per line)");
        criteriaArea.setPrefRowCount(3);
        criteriaArea.setWrapText(true);

        content.getChildren().addAll(
                new Label("Decision Question:"), questionField,
                new Label("Options:"), optionsArea,
                new Label("Criteria:"), criteriaArea
        );
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(bt -> bt == ButtonType.OK ? "ok" : null);

        dialog.showAndWait().ifPresent(result -> {
            String question = questionField.getText();
            String options = optionsArea.getText();
            String criteria = criteriaArea.getText();

            if (question == null || question.trim().isEmpty()) return;

            btnDecisionHelper.setDisable(true);
            btnDecisionHelper.setText("⏳ Analyzing...");

            AppThreadPool.io(() -> {
                try {
                    ZAIService zai = new ZAIService();
                    String analysis = zai.helpDecide(question,
                            options != null ? options : "Not specified",
                            criteria != null ? criteria : "Not specified");

                    Platform.runLater(() -> {
                        btnDecisionHelper.setDisable(false);
                        btnDecisionHelper.setText("⚖ Decide");
                        SoundManager.getInstance().play(SoundManager.AI_COMPLETE);
                        showAiResultDialog("Decision Analysis", "⚖", analysis);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        btnDecisionHelper.setDisable(false);
                        btnDecisionHelper.setText("⚖ Decide");
                        showError("Decision analysis failed: " + e.getMessage());
                    });
                }
            });
        });
    }

    /**
     * Smart keyword-based task generator — analyzes project name/description
     * to detect the project category and generates relevant task breakdown.
     * Returns list of {title, description, priority} arrays.
     */
    private List<String[]> generateSmartTasks(String name, String description) {
        String combined = (name + " " + description).toLowerCase();

        // Detect project category from keywords
        String category = "general";
        if (matches(combined, "web|website|frontend|backend|api|app|mobile|software|platform|portal|saas|dashboard")) {
            category = "software";
        } else if (matches(combined, "market|campaign|brand|seo|social media|ads|advertis|content|promot")) {
            category = "marketing";
        } else if (matches(combined, "event|conference|workshop|seminar|meetup|ceremony|party|festival")) {
            category = "event";
        } else if (matches(combined, "research|study|analysis|survey|data|report|thesis|paper|academ")) {
            category = "research";
        } else if (matches(combined, "design|ui|ux|graphic|logo|visual|prototype|wireframe|figma")) {
            category = "design";
        } else if (matches(combined, "train|course|learn|educat|onboard|workshop|curriculum|tutorial")) {
            category = "training";
        }

        List<String[]> tasks = new ArrayList<>();
        switch (category) {
            case "software":
                tasks.add(new String[]{"Requirements Gathering", "Collect and document functional and non-functional requirements from stakeholders", "HIGH"});
                tasks.add(new String[]{"System Architecture Design", "Design the system architecture, database schema, and API contracts", "HIGH"});
                tasks.add(new String[]{"UI/UX Wireframes", "Create wireframes and mockups for all major screens and user flows", "MEDIUM"});
                tasks.add(new String[]{"Backend Development", "Implement server-side logic, API endpoints, and database layer", "HIGH"});
                tasks.add(new String[]{"Frontend Development", "Build the user interface components and integrate with backend APIs", "HIGH"});
                tasks.add(new String[]{"Unit & Integration Testing", "Write and run tests for all critical modules and integration points", "MEDIUM"});
                tasks.add(new String[]{"Security Audit", "Perform security review — authentication, authorization, data validation", "HIGH"});
                tasks.add(new String[]{"Deployment & CI/CD Setup", "Set up deployment pipeline, staging/production environments", "MEDIUM"});
                tasks.add(new String[]{"Documentation", "Write technical docs, API reference, and user guides", "LOW"});
                break;
            case "marketing":
                tasks.add(new String[]{"Market Research & Analysis", "Analyze target audience, competitors, and market trends", "HIGH"});
                tasks.add(new String[]{"Strategy & Goals Definition", "Define marketing objectives, KPIs, budget, and timeline", "HIGH"});
                tasks.add(new String[]{"Content Calendar Creation", "Plan content themes, formats, and publishing schedule", "MEDIUM"});
                tasks.add(new String[]{"Visual Asset Production", "Create graphics, banners, videos, and branded materials", "MEDIUM"});
                tasks.add(new String[]{"Social Media Campaigns", "Launch and manage campaigns across social media platforms", "HIGH"});
                tasks.add(new String[]{"Email Marketing Setup", "Design email templates, segment lists, and schedule sends", "MEDIUM"});
                tasks.add(new String[]{"SEO Optimization", "Optimize content for search engines — keywords, meta tags, backlinks", "MEDIUM"});
                tasks.add(new String[]{"Analytics & Reporting", "Track campaign metrics and produce performance reports", "LOW"});
                break;
            case "event":
                tasks.add(new String[]{"Event Concept & Planning", "Define event theme, goals, target audience, and format", "HIGH"});
                tasks.add(new String[]{"Venue Selection & Booking", "Research, compare, and book the event venue", "HIGH"});
                tasks.add(new String[]{"Budget Planning", "Create detailed budget breakdown and track expenses", "HIGH"});
                tasks.add(new String[]{"Speaker/Guest Coordination", "Invite and confirm speakers, guests, and performers", "MEDIUM"});
                tasks.add(new String[]{"Marketing & Invitations", "Design and send invitations, promote event on social media", "MEDIUM"});
                tasks.add(new String[]{"Logistics & Setup", "Arrange catering, AV equipment, decorations, and seating", "HIGH"});
                tasks.add(new String[]{"Registration System", "Set up registration/ticketing and manage attendee list", "MEDIUM"});
                tasks.add(new String[]{"Post-Event Follow-up", "Send thank-you notes, collect feedback, and write summary", "LOW"});
                break;
            case "research":
                tasks.add(new String[]{"Literature Review", "Survey existing research, papers, and publications in the field", "HIGH"});
                tasks.add(new String[]{"Research Question Definition", "Clearly define the research hypothesis and questions", "HIGH"});
                tasks.add(new String[]{"Methodology Design", "Select and design appropriate research methodology", "HIGH"});
                tasks.add(new String[]{"Data Collection", "Gather data through surveys, experiments, or secondary sources", "HIGH"});
                tasks.add(new String[]{"Data Analysis", "Process and analyze collected data using statistical methods", "MEDIUM"});
                tasks.add(new String[]{"Results Interpretation", "Interpret findings and compare with existing literature", "MEDIUM"});
                tasks.add(new String[]{"Report Writing", "Write the final research report or thesis document", "MEDIUM"});
                tasks.add(new String[]{"Peer Review & Revision", "Submit for peer review and revise based on feedback", "LOW"});
                break;
            case "design":
                tasks.add(new String[]{"Design Brief & Mood Board", "Define project vision, style guide, and mood board", "HIGH"});
                tasks.add(new String[]{"User Research", "Conduct user interviews and analyze personas and journeys", "HIGH"});
                tasks.add(new String[]{"Wireframing", "Create low-fidelity wireframes for all key screens", "HIGH"});
                tasks.add(new String[]{"Visual Design System", "Build a design system — colors, typography, components", "MEDIUM"});
                tasks.add(new String[]{"High-Fidelity Mockups", "Design pixel-perfect mockups for all screens", "HIGH"});
                tasks.add(new String[]{"Interactive Prototype", "Build a clickable prototype for usability testing", "MEDIUM"});
                tasks.add(new String[]{"Usability Testing", "Test prototypes with real users and gather feedback", "MEDIUM"});
                tasks.add(new String[]{"Design Handoff", "Prepare assets and specs for development team", "LOW"});
                break;
            case "training":
                tasks.add(new String[]{"Training Needs Assessment", "Identify skills gaps and learning objectives", "HIGH"});
                tasks.add(new String[]{"Curriculum Development", "Define modules, topics, and learning outcomes", "HIGH"});
                tasks.add(new String[]{"Content Creation", "Develop presentations, exercises, and course materials", "HIGH"});
                tasks.add(new String[]{"Platform Setup", "Prepare LMS or training environment and tools", "MEDIUM"});
                tasks.add(new String[]{"Instructor Preparation", "Brief trainers and rehearse session delivery", "MEDIUM"});
                tasks.add(new String[]{"Schedule & Enrollment", "Set dates, notify participants, and manage registration", "MEDIUM"});
                tasks.add(new String[]{"Deliver Training Sessions", "Conduct the training sessions and provide support", "HIGH"});
                tasks.add(new String[]{"Evaluation & Feedback", "Collect feedback, measure outcomes, and improve content", "LOW"});
                break;
            default: // general
                tasks.add(new String[]{"Project Kickoff & Planning", "Define scope, objectives, success criteria, and create project plan", "HIGH"});
                tasks.add(new String[]{"Requirements Analysis", "Gather and document detailed project requirements", "HIGH"});
                tasks.add(new String[]{"Resource Allocation", "Assign team members, tools, and budget to project tasks", "MEDIUM"});
                tasks.add(new String[]{"Design Phase", "Create designs, mockups, or blueprints for deliverables", "MEDIUM"});
                tasks.add(new String[]{"Core Implementation", "Execute the main work — build, create, or produce deliverables", "HIGH"});
                tasks.add(new String[]{"Quality Assurance & Review", "Test, review, and validate all deliverables against requirements", "MEDIUM"});
                tasks.add(new String[]{"Stakeholder Presentation", "Present progress and deliverables to stakeholders for feedback", "MEDIUM"});
                tasks.add(new String[]{"Final Delivery & Closure", "Deliver final outputs, document lessons learned, and close project", "LOW"});
                break;
        }
        return tasks;
    }

    private boolean matches(String text, String keywords) {
        for (String kw : keywords.split("\\|")) {
            if (text.contains(kw.trim())) return true;
        }
        return false;
    }

    // ════════════════════════════════════════════════════════
    //  API INTEGRATIONS: ZenQuotes & Advice Slip
    // ════════════════════════════════════════════════════════

    /**
     * API #1 — QuickChart Burndown (https://quickchart.io/)
     * Generates a burndown chart image showing task completion over time.
     */
    private void fetchQuoteOfDay() {
        AppThreadPool.io(() -> {
            try {
                // Gather task completion data for the current project
                List<Task> tasks = serviceTask.getByProject(currentProject.getId());
                int total = tasks.size();
                long done = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
                long inReview = tasks.stream().filter(t -> "IN_REVIEW".equals(t.getStatus())).count();
                long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
                long todo = tasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();

                String summary = "📊 " + total + " tasks: ✅ " + done + " done · 🔍 " + inReview
                        + " in review · 🔄 " + inProgress + " in progress · 📋 " + todo + " to do";
                Platform.runLater(() -> {
                    quoteLabel.setText(summary);
                    quoteBanner.setVisible(true);
                    quoteBanner.setManaged(true);
                });
            } catch (Exception ignored) {}
        });
    }

    /**
     * API #2 — Exchange Rate API (https://api.exchangerate-api.com/)
     * Fetches current exchange rates for common freelancer currencies.
     * Useful for gig workers working with international clients.
     */
    @FXML
    private void fetchDailyTip() {
        btnDailyTip.setDisable(true);
        btnDailyTip.setText("⏳ Loading...");

        AppThreadPool.io(() -> {
            try {
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.exchangerate-api.com/v4/latest/USD"))
                        .GET()
                        .timeout(java.time.Duration.ofSeconds(8))
                        .build();
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                    com.google.gson.JsonObject rates = json.getAsJsonObject("rates");

                    StringBuilder sb = new StringBuilder();
                    sb.append("💱 Exchange Rates (1 USD)\n\n");
                    String[] currencies = {"EUR", "GBP", "TND", "CAD", "AUD", "JPY", "CHF", "MAD"};
                    String[] flags = {"🇪🇺", "🇬🇧", "🇹🇳", "🇨🇦", "🇦🇺", "🇯🇵", "🇨🇭", "🇲🇦"};
                    for (int i = 0; i < currencies.length; i++) {
                        if (rates.has(currencies[i])) {
                            double rate = rates.get(currencies[i]).getAsDouble();
                            sb.append(flags[i]).append("  ").append(currencies[i]).append(":  ")
                              .append(String.format("%.3f", rate)).append("\n");
                        }
                    }

                    Platform.runLater(() -> {
                        btnDailyTip.setDisable(false);
                        btnDailyTip.setText("💱 Rates");
                        Alert tip = new Alert(Alert.AlertType.INFORMATION);
                        tip.setTitle("Exchange Rates");
                        tip.setHeaderText("💱 Current Exchange Rates");
                        tip.setContentText(sb.toString());
                        DialogHelper.theme(tip);
                        tip.showAndWait();
                    });
                } else {
                    Platform.runLater(() -> {
                        btnDailyTip.setDisable(false);
                        btnDailyTip.setText("💱 Rates");
                        showError("Could not fetch rates. Try again.");
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnDailyTip.setDisable(false);
                    btnDailyTip.setText("💱 Rates");
                    showError("Network error: " + e.getMessage());
                });
            }
        });
    }

    /**
     * API #3 — Open-Meteo (https://open-meteo.com/)
     * Fetches current weather and displays it in the project header.
     * Free, no API key required.
     */
    private void fetchWeather() {
        Platform.runLater(() -> weatherLabel.setText(""));
        AppThreadPool.io(() -> {
            try {
                // Default coordinates: Tunis, Tunisia (36.8, 10.18)
                String url = "https://api.open-meteo.com/v1/forecast?latitude=36.8&longitude=10.18&current_weather=true";
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .GET()
                        .timeout(java.time.Duration.ofSeconds(8))
                        .build();
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                    com.google.gson.JsonObject cw = json.getAsJsonObject("current_weather");
                    double temp = cw.get("temperature").getAsDouble();
                    int weatherCode = cw.get("weathercode").getAsInt();
                    String emoji = weatherCodeToEmoji(weatherCode);
                    String display = emoji + " " + String.format("%.0f", temp) + "°C";
                    Platform.runLater(() -> weatherLabel.setText(display));
                }
            } catch (Exception ignored) {
                // Weather is non-essential
            }
        });
    }

    private String weatherCodeToEmoji(int code) {
        if (code == 0) return "☀️";
        if (code <= 3) return "⛅";
        if (code <= 48) return "🌫️";
        if (code <= 57) return "🌧️";
        if (code <= 67) return "🌧️";
        if (code <= 77) return "🌨️";
        if (code <= 82) return "🌧️";
        if (code <= 86) return "🌨️";
        if (code >= 95) return "⛈️";
        return "🌤️";
    }

    /**
     * API #4 — Nager.Date Public Holidays (https://date.nager.at/)
     * Checks if the project deadline falls on or near a public holiday.
     * Free, no API key required.
     */
    private void checkDeadlineHolidays() {
        Platform.runLater(() -> {
            holidayWarning.setVisible(false);
            holidayWarning.setManaged(false);
        });
        if (currentProject.getDeadline() == null) return;

        AppThreadPool.io(() -> {
            try {
                java.sql.Date deadline = currentProject.getDeadline();
                java.time.LocalDate dlDate = deadline.toLocalDate();
                int year = dlDate.getYear();
                String cc = utils.GeoLocationService.resolveCountry().join(); // detect from IP
                String url = "https://date.nager.at/api/v3/PublicHolidays/" + year + "/" + cc;
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(url))
                        .GET()
                        .timeout(java.time.Duration.ofSeconds(8))
                        .build();
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonArray holidays = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonArray();
                    List<String> warnings = new ArrayList<>();
                    for (com.google.gson.JsonElement el : holidays) {
                        com.google.gson.JsonObject h = el.getAsJsonObject();
                        java.time.LocalDate hDate = java.time.LocalDate.parse(h.get("date").getAsString());
                        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(dlDate, hDate);
                        if (Math.abs(daysDiff) <= 3) {
                            String name = h.get("localName").getAsString();
                            String dateStr = hDate.toString();
                            if (daysDiff == 0) {
                                warnings.add("⚠️ Deadline falls ON \"" + name + "\" (" + dateStr + ")");
                            } else if (daysDiff > 0) {
                                warnings.add("📅 \"" + name + "\" is " + daysDiff + " day(s) after deadline (" + dateStr + ")");
                            } else {
                                warnings.add("📅 \"" + name + "\" is " + Math.abs(daysDiff) + " day(s) before deadline (" + dateStr + ")");
                            }
                        }
                    }
                    if (!warnings.isEmpty()) {
                        String warningText = String.join("\n", warnings);
                        Platform.runLater(() -> {
                            holidayWarning.setText(warningText);
                            holidayWarning.setVisible(true);
                            holidayWarning.setManaged(true);
                        });
                    }
                }
            } catch (Exception ignored) {
                // Holiday check is non-essential
            }
        });
    }

    /**
     * API #5 — Useless Facts (https://uselessfacts.jsph.pl/api/v2/facts/random)
     * Fetches a random fun fact for team morale.
     * Free, no API key required.
     */
    @FXML
    private void fetchFunFact() {
        btnFunFact.setDisable(true);
        btnFunFact.setText("⏳ Loading...");

        AppThreadPool.io(() -> {
            try {
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://uselessfacts.jsph.pl/api/v2/facts/random?language=en"))
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(java.time.Duration.ofSeconds(8))
                        .build();
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                    String fact = obj.get("text").getAsString();
                    Platform.runLater(() -> {
                        btnFunFact.setDisable(false);
                        btnFunFact.setText("🎲 Fun Fact");
                        Alert info = new Alert(Alert.AlertType.INFORMATION);
                        info.setTitle("Fun Fact");
                        info.setHeaderText("🎲 Did you know?");
                        info.setContentText(fact);
                        DialogHelper.theme(info);
                        info.showAndWait();
                    });
                } else {
                    Platform.runLater(() -> {
                        btnFunFact.setDisable(false);
                        btnFunFact.setText("🎲 Fun Fact");
                        showError("Could not fetch fun fact. Try again.");
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    btnFunFact.setDisable(false);
                    btnFunFact.setText("🎲 Fun Fact");
                    showError("Network error: " + e.getMessage());
                });
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

    // ════════════════════════════════════════════════════════
    //  n8n WEBHOOK INTEGRATION
    // ════════════════════════════════════════════════════════

    private void triggerN8nWebhook(String event, Map<String, Object> payload) {
        AppThreadPool.io(() -> {
            try {
                String n8nBase = AppConfig.get("n8n.base_url", "");
                if (n8nBase.isEmpty()) return;

                String url = n8nBase + "/webhook/pm-" + event;
                String json = gson.toJson(payload);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(5))
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (Exception ignored) {
                // n8n optional — don't break the app
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  EMPLOYEE DASHBOARD (task-centric view)
    // ════════════════════════════════════════════════════════

    /**
     * Builds and shows the employee task-centric dashboard.
     * Replaces the project grid for EMPLOYEE / GIG_WORKER roles.
     */
    private void showEmployeeDashboard() {
        if (employeeDashboard == null) return;
        refreshEmployeeDashboard();
    }

    /**
     * Reloads employee task data and rebuilds the dashboard UI.
     */
    private void refreshEmployeeDashboard() {
        if (employeeDashboard == null) return;
        AppThreadPool.io(() -> {
            try {
                List<Task> myTasks = serviceTask.getByAssignee(currentUser.getId());
                int activeCount = serviceTask.getActiveTaskCount(currentUser.getId());

                // Group by status
                long todo = myTasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
                long inProgress = myTasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
                long inReview = myTasks.stream().filter(t -> "IN_REVIEW".equals(t.getStatus())).count();
                long done = myTasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();

                // Get project names for display
                Map<Integer, String> projectNames = new HashMap<>();
                try {
                    List<Project> allProj = serviceProject.recuperer();
                    for (Project p : allProj) projectNames.put(p.getId(), p.getName());
                } catch (Exception ignored) {}
                for (Task t : myTasks) {
                    projectNames.putIfAbsent(t.getProjectId(), "Project #" + t.getProjectId());
                }

                Platform.runLater(() -> {
                    employeeDashboard.getChildren().clear();
                    employeeDashboard.setSpacing(16);
                    employeeDashboard.setPadding(new Insets(4, 0, 0, 0));

                    // ── Header ──
                    Label title = new Label("My Tasks");
                    title.getStyleClass().add("content-title");
                    Label subtitle = new Label("Welcome back, " + currentUser.getFirstName() + " — here's your workload");
                    subtitle.getStyleClass().add("content-subtitle");
                    VBox headerBox = new VBox(2, title, subtitle);

                    // ── Workload Bar ──
                    HBox workloadRow = new HBox(12);
                    workloadRow.setAlignment(Pos.CENTER_LEFT);
                    workloadRow.getStyleClass().add("emp-workload-row");
                    workloadRow.setPadding(new Insets(12, 16, 12, 16));

                    ProgressBar workloadBar = new ProgressBar((double) activeCount / MAX_ACTIVE_TASKS);
                    workloadBar.setPrefWidth(200);
                    workloadBar.setPrefHeight(10);
                    workloadBar.getStyleClass().add("emp-workload-bar");
                    if (activeCount >= MAX_ACTIVE_TASKS) {
                        workloadBar.getStyleClass().add("emp-workload-full");
                    } else if (activeCount >= WARN_THRESHOLD) {
                        workloadBar.getStyleClass().add("emp-workload-warn");
                    }

                    Label workloadLabel = new Label(activeCount + " / " + MAX_ACTIVE_TASKS + " active tasks");
                    workloadLabel.getStyleClass().add("emp-workload-label");
                    if (activeCount >= MAX_ACTIVE_TASKS) {
                        workloadLabel.setStyle("-fx-text-fill: #FF4444; -fx-font-weight: bold;");
                    }
                    workloadRow.getChildren().addAll(new Label("📊"), workloadBar, workloadLabel);

                    // ── Stat cards row ──
                    HBox statsRow = new HBox(12);
                    statsRow.setAlignment(Pos.CENTER_LEFT);
                    statsRow.getChildren().addAll(
                            buildStatCard("📋", "To Do", String.valueOf(todo), "#7B61FF"),
                            buildStatCard("🔄", "In Progress", String.valueOf(inProgress), "#3B82F6"),
                            buildStatCard("🔍", "In Review", String.valueOf(inReview), "#FBBF24"),
                            buildStatCard("✅", "Done", String.valueOf(done), "#34D399")
                    );

                    // ── Filter chips ──
                    HBox filterRow = new HBox(8);
                    filterRow.setAlignment(Pos.CENTER_LEFT);
                    filterRow.getStyleClass().add("pm-filter-bar");
                    String[] filters = {"All", "To Do", "In Progress", "In Review", "Done"};
                    String[] filterValues = {"ALL", "TODO", "IN_PROGRESS", "IN_REVIEW", "DONE"};
                    final String[] currentFilter = {"ALL"};
                    for (int i = 0; i < filters.length; i++) {
                        Button chip = new Button(filters[i]);
                        chip.getStyleClass().add("pm-filter-chip");
                        if (i == 0) chip.getStyleClass().add("pm-filter-active");
                        final int fi = i;
                        chip.setOnAction(e -> {
                            currentFilter[0] = filterValues[fi];
                            filterRow.getChildren().forEach(c -> c.getStyleClass().remove("pm-filter-active"));
                            chip.getStyleClass().add("pm-filter-active");
                            // Re-render task list
                            VBox taskList = (VBox) employeeDashboard.lookup("#empTaskList");
                            if (taskList != null) {
                                taskList.getChildren().clear();
                                List<Task> filtered = "ALL".equals(filterValues[fi]) ? myTasks
                                        : myTasks.stream().filter(t -> filterValues[fi].equals(t.getStatus())).collect(Collectors.toList());
                                for (Task t : filtered) {
                                    taskList.getChildren().add(buildEmployeeTaskCard(t, projectNames.getOrDefault(t.getProjectId(), "")));
                                }
                                if (filtered.isEmpty()) {
                                    Label empty = new Label("No tasks in this category");
                                    empty.setStyle("-fx-text-fill: #6B6B7B; -fx-font-size: 13; -fx-padding: 20;");
                                    taskList.getChildren().add(empty);
                                }
                            }
                        });
                        filterRow.getChildren().add(chip);
                    }

                    // ── Task list (scrollable) ──
                    VBox taskList = new VBox(10);
                    taskList.setId("empTaskList");
                    taskList.setPadding(new Insets(4));
                    for (Task t : myTasks) {
                        taskList.getChildren().add(buildEmployeeTaskCard(t, projectNames.getOrDefault(t.getProjectId(), "")));
                    }
                    if (myTasks.isEmpty()) {
                        Label empty = new Label("🎉 No tasks assigned yet — enjoy some free time!");
                        empty.setStyle("-fx-text-fill: #6B6B7B; -fx-font-size: 14; -fx-padding: 40;");
                        taskList.getChildren().add(empty);
                    }
                    ScrollPane scroll = new ScrollPane(taskList);
                    scroll.setFitToWidth(true);
                    scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
                    VBox.setVgrow(scroll, Priority.ALWAYS);

                    employeeDashboard.getChildren().addAll(headerBox, workloadRow, statsRow, filterRow, scroll);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    employeeDashboard.getChildren().clear();
                    Label err = new Label("Failed to load tasks: " + e.getMessage());
                    err.setStyle("-fx-text-fill: #FF6B6B;");
                    employeeDashboard.getChildren().add(err);
                });
            }
        });
    }

    /**
     * Build a stat card for the employee dashboard header.
     */
    private VBox buildStatCard(String icon, String label, String value, String color) {
        VBox card = new VBox(4);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 20, 12, 20));
        card.getStyleClass().add("emp-stat-card");
        card.setStyle("-fx-border-color: " + color + "33; -fx-border-width: 0 0 3 0;");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20;");
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 22; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("emp-stat-label");

        card.getChildren().addAll(iconLabel, valueLabel, nameLabel);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /**
     * Builds an individual task card for the employee dashboard.
     */
    private HBox buildEmployeeTaskCard(Task task, String projectName) {
        HBox card = new HBox(12);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("emp-task-card");

        // Priority indicator stripe
        String priorityColor;
        switch (task.getPriority() != null ? task.getPriority() : "MEDIUM") {
            case "HIGH": priorityColor = "#FF6B6B"; break;
            case "LOW": priorityColor = "#34D399"; break;
            default: priorityColor = "#FBBF24"; break;
        }
        Region stripe = new Region();
        stripe.setPrefWidth(4);
        stripe.setMinWidth(4);
        stripe.setMaxWidth(4);
        stripe.setStyle("-fx-background-color: " + priorityColor + "; -fx-background-radius: 2;");
        VBox.setVgrow(stripe, Priority.ALWAYS);

        // ── Main content ──
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(task.getTitle());
        titleLabel.getStyleClass().add("emp-task-title");
        titleLabel.setMaxWidth(300);

        // Status badge
        Label statusBadge = new Label(formatStatus(task.getStatus()));
        statusBadge.getStyleClass().addAll("emp-status-badge", "emp-status-" + task.getStatus().toLowerCase().replace("_", "-"));
        titleRow.getChildren().addAll(titleLabel, statusBadge);

        // Meta row
        HBox metaRow = new HBox(12);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        if (projectName != null && !projectName.isEmpty()) {
            Label projLabel = new Label("📁 " + projectName);
            projLabel.getStyleClass().add("emp-task-meta");
            metaRow.getChildren().add(projLabel);
        }
        if (task.getPriority() != null) {
            Label priLabel = new Label(task.getPriority());
            priLabel.getStyleClass().addAll("emp-priority-badge", "emp-priority-" + task.getPriority().toLowerCase());
            metaRow.getChildren().add(priLabel);
        }
        if (task.getDueDate() != null) {
            Label dueLabel = new Label("📅 " + task.getDueDate().toString());
            dueLabel.getStyleClass().add("emp-task-meta");
            // Highlight overdue
            if (task.getDueDate().toLocalDate().isBefore(LocalDate.now()) && !"DONE".equals(task.getStatus())) {
                dueLabel.setStyle("-fx-text-fill: #FF4444; -fx-font-weight: bold;");
                dueLabel.setText("⚠️ OVERDUE " + task.getDueDate().toString());
            }
            metaRow.getChildren().add(dueLabel);
        }

        // Review feedback (if any)
        if (task.getReviewFeedback() != null && !task.getReviewFeedback().isEmpty()
                && ("TODO".equals(task.getStatus()) || "IN_PROGRESS".equals(task.getStatus()))) {
            Label fbLabel = new Label("💬 Feedback: " + truncate(task.getReviewFeedback(), 80));
            fbLabel.getStyleClass().add("emp-task-feedback");
            fbLabel.setWrapText(true);
            info.getChildren().addAll(titleRow, metaRow, fbLabel);
        } else {
            info.getChildren().addAll(titleRow, metaRow);
        }

        // ── Action buttons ──
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);

        switch (task.getStatus()) {
            case "TODO":
                Button startBtn = new Button("▶ Start");
                startBtn.getStyleClass().addAll("emp-action-btn", "emp-btn-start");
                startBtn.setOnAction(e -> {
                    AppThreadPool.io(() -> {
                        try {
                            serviceTask.updateStatus(task.getId(), "IN_PROGRESS");
                            Platform.runLater(() -> {
                                SoundManager.getInstance().play(SoundManager.TASK_CREATED);
                                refreshEmployeeDashboard();
                            });
                        } catch (SQLException ex) {
                            Platform.runLater(() -> showError("Failed: " + ex.getMessage()));
                        }
                    });
                });
                actions.getChildren().add(startBtn);
                break;
            case "IN_PROGRESS":
                Button submitBtn = new Button("📤 Submit");
                submitBtn.getStyleClass().addAll("emp-action-btn", "emp-btn-submit");
                submitBtn.setOnAction(e -> submitTaskForReview(task));
                actions.getChildren().add(submitBtn);
                break;
            case "IN_REVIEW":
                Label waitLabel = new Label("⏳ Awaiting review");
                waitLabel.setStyle("-fx-text-fill: #FBBF24; -fx-font-size: 11;");
                actions.getChildren().add(waitLabel);
                break;
            case "DONE":
                if (task.getReviewRating() != null && task.getReviewRating() > 0) {
                    HBox ratingStars = createReadOnlyStarRating(task.getReviewRating(), 5);
                    actions.getChildren().add(ratingStars);
                }
                break;
        }

        card.getChildren().addAll(stripe, info, actions);
        card.setOnMouseEntered(e -> card.getStyleClass().add("emp-task-card-hover"));
        card.setOnMouseExited(e -> card.getStyleClass().remove("emp-task-card-hover"));
        return card;
    }

    // ════════════════════════════════════════════════════════
    //  ImgBB FILE UPLOAD
    // ════════════════════════════════════════════════════════

    /**
     * Uploads a file to ImgBB and returns the URL.
     * Uses the free ImgBB API (https://api.imgbb.com/).
     */
    private String uploadFileToImgBB(File file) {
        try {
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String base64 = java.util.Base64.getEncoder().encodeToString(fileBytes);

            // ImgBB free API key (you can get one at https://api.imgbb.com/)
            String apiKey = AppConfig.get("imgbb.api_key", "d8b0507847c7b8e8c528f0c17ed6a675");
            String boundary = "----FormBoundary" + System.currentTimeMillis();

            StringBuilder body = new StringBuilder();
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"key\"\r\n\r\n");
            body.append(apiKey).append("\r\n");
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"image\"\r\n\r\n");
            body.append(base64).append("\r\n");
            body.append("--").append(boundary).append("--\r\n");

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.imgbb.com/1/upload"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonObject json = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.has("data") && json.getAsJsonObject("data").has("url")) {
                    return json.getAsJsonObject("data").get("url").getAsString();
                }
            }
            System.err.println("ImgBB upload failed: " + resp.statusCode() + " " + resp.body());
            return null;
        } catch (Exception e) {
            System.err.println("ImgBB upload error: " + e.getMessage());
            return null;
        }
    }

    // ════════════════════════════════════════════════════════
    //  MANAGER WORKLOAD PANEL
    // ════════════════════════════════════════════════════════

    /**
     * Adds workload indicators to the team view for the project manager.
     */
    private void addWorkloadToTeamView(List<ServiceProjectMember.ProjectMember> members, VBox container) {
        Label sectionTitle = new Label("📊 Team Workload");
        sectionTitle.getStyleClass().add("pm-section-title");
        sectionTitle.setStyle("-fx-padding: 8 0 4 0;");

        VBox workloadList = new VBox(8);
        workloadList.setPadding(new Insets(4, 0, 12, 0));

        for (ServiceProjectMember.ProjectMember m : members) {
            try {
                int active = serviceTask.getActiveTaskCount(m.userId);
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 12, 8, 12));
                row.getStyleClass().add("emp-workload-row");

                Label name = new Label(m.firstName + " " + m.lastName);
                name.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 13;");
                name.setPrefWidth(160);

                ProgressBar bar = new ProgressBar((double) active / MAX_ACTIVE_TASKS);
                bar.setPrefWidth(140);
                bar.setPrefHeight(8);
                bar.getStyleClass().add("emp-workload-bar");
                if (active >= MAX_ACTIVE_TASKS) bar.getStyleClass().add("emp-workload-full");
                else if (active >= WARN_THRESHOLD) bar.getStyleClass().add("emp-workload-warn");

                Label count = new Label(active + "/" + MAX_ACTIVE_TASKS);
                count.setStyle("-fx-text-fill: " + (active >= MAX_ACTIVE_TASKS ? "#FF4444" : active >= WARN_THRESHOLD ? "#FF6B35" : "#8A8A9A") + "; -fx-font-size: 12;");

                row.getChildren().addAll(name, bar, count);
                workloadList.getChildren().add(row);
            } catch (Exception ignored) {}
        }

        if (!workloadList.getChildren().isEmpty()) {
            container.getChildren().addAll(sectionTitle, workloadList);
        }
    }

    // ════════════════════════════════════════════════════════
    //  MUI-STYLE STAR RATING COMPONENT
    // ════════════════════════════════════════════════════════

    /**
     * Creates an interactive MUI-style star rating bar.
     * @param maxStars number of stars (typically 5)
     * @param initialValue initial selected rating (0 = none)
     * @param ratingHolder int[1] array to store the selected value
     * @return HBox containing the interactive star labels
     */
    private HBox createInteractiveStarRating(int maxStars, int initialValue, int[] ratingHolder) {
        HBox container = new HBox(2);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("mui-rating");

        Label[] starLabels = new Label[maxStars];
        Label valueLabel = new Label(initialValue > 0 ? String.valueOf(initialValue) + "/5" : "");
        valueLabel.getStyleClass().add("mui-rating-value");

        ratingHolder[0] = initialValue;

        for (int i = 0; i < maxStars; i++) {
            final int starIndex = i;
            Label star = new Label(i < initialValue ? "\u2605" : "\u2606");
            star.getStyleClass().add("mui-rating-star");
            if (i < initialValue) star.getStyleClass().add("mui-rating-star-filled");

            // Hover: preview fill up to this star
            star.setOnMouseEntered(e -> {
                for (int j = 0; j < maxStars; j++) {
                    starLabels[j].setText(j <= starIndex ? "\u2605" : "\u2606");
                    starLabels[j].getStyleClass().remove("mui-rating-star-hover");
                    if (j <= starIndex) starLabels[j].getStyleClass().add("mui-rating-star-hover");
                }
            });

            // Mouse exit: revert to current selected rating
            star.setOnMouseExited(e -> {
                for (int j = 0; j < maxStars; j++) {
                    starLabels[j].setText(j < ratingHolder[0] ? "\u2605" : "\u2606");
                    starLabels[j].getStyleClass().remove("mui-rating-star-hover");
                }
            });

            // Click: set the rating (click same star again to clear)
            star.setOnMouseClicked(e -> {
                int newVal = starIndex + 1;
                if (ratingHolder[0] == newVal) {
                    ratingHolder[0] = 0; // toggle off
                } else {
                    ratingHolder[0] = newVal;
                }
                for (int j = 0; j < maxStars; j++) {
                    boolean filled = j < ratingHolder[0];
                    starLabels[j].setText(filled ? "\u2605" : "\u2606");
                    starLabels[j].getStyleClass().remove("mui-rating-star-filled");
                    starLabels[j].getStyleClass().remove("mui-rating-star-hover");
                    if (filled) starLabels[j].getStyleClass().add("mui-rating-star-filled");
                }
                valueLabel.setText(ratingHolder[0] > 0 ? ratingHolder[0] + "/5" : "");
                SoundManager.getInstance().play(SoundManager.STAR_RATING);
            });

            starLabels[i] = star;
            container.getChildren().add(star);
        }

        container.getChildren().add(valueLabel);
        return container;
    }

    /**
     * Creates a read-only MUI-style star rating display.
     * @param rating the rating value (1-5)
     * @param maxStars number of stars (typically 5)
     * @return HBox containing the read-only stars
     */
    private HBox createReadOnlyStarRating(int rating, int maxStars) {
        HBox container = new HBox(2);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("mui-rating");
        container.getStyleClass().add("mui-rating-readonly");

        for (int i = 0; i < maxStars; i++) {
            Label star = new Label(i < rating ? "\u2605" : "\u2606");
            star.getStyleClass().add("mui-rating-star");
            if (i < rating) star.getStyleClass().add("mui-rating-star-filled");
            container.getChildren().add(star);
        }

        Label valueLabel = new Label("(" + rating + "/" + maxStars + ")");
        valueLabel.getStyleClass().add("mui-rating-value");
        container.getChildren().add(valueLabel);
        return container;
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
