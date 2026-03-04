package controllers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entities.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import services.*;
import utils.ApiClient;
import utils.DialogHelper;
import utils.SessionManager;
import utils.SoundManager;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HR &amp; Employee Backlog — shows employee directory, pending leaves,
 * attendance, task backlog, and department/leave charts.
 */
public class HRBacklogController implements Stoppable {

    /* ═══════════════ FXML fields ═══════════════ */

    @FXML private Label summaryLabel;

    // Stat cards
    @FXML private Label totalEmployeesLabel;
    @FXML private Label pendingLeavesLabel;
    @FXML private Label presentTodayLabel;
    @FXML private Label pendingPayrollLabel;
    @FXML private Label openTasksLabel;

    // Employee table
    @FXML private TextField searchField;
    @FXML private TableView<User> employeeTable;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colDept;
    @FXML private TableColumn<User, String> colStatus;

    // Charts
    @FXML private PieChart deptChart;
    @FXML private PieChart leaveChart;
    @FXML private VBox deptLegendBox;
    @FXML private VBox leaveLegendBox;

    // Leave table
    @FXML private TableView<Leave> leaveTable;
    @FXML private TableColumn<Leave, String> colLeaveEmployee;
    @FXML private TableColumn<Leave, String> colLeaveType;
    @FXML private TableColumn<Leave, String> colLeaveStart;
    @FXML private TableColumn<Leave, String> colLeaveEnd;
    @FXML private TableColumn<Leave, String> colLeaveReason;
    @FXML private TableColumn<Leave, String> colLeaveActions;

    // Task table
    @FXML private ComboBox<String> taskFilterCombo;
    @FXML private TableView<Task> taskTable;
    @FXML private TableColumn<Task, String> colTaskTitle;
    @FXML private TableColumn<Task, String> colTaskAssignee;
    @FXML private TableColumn<Task, String> colTaskStatus;
    @FXML private TableColumn<Task, String> colTaskPriority;
    @FXML private TableColumn<Task, String> colTaskDue;

    // Attendance table
    @FXML private TableView<Attendance> attendanceTable;
    @FXML private TableColumn<Attendance, String> colAttEmployee;
    @FXML private TableColumn<Attendance, String> colAttCheckIn;
    @FXML private TableColumn<Attendance, String> colAttCheckOut;
    @FXML private TableColumn<Attendance, String> colAttStatus;

    /* ═══════════════ Services ═══════════════ */

    private final ServiceUser serviceUser = new ServiceUser();
    private final ServiceLeave serviceLeave = new ServiceLeave();
    private final ServiceAttendance serviceAttendance = new ServiceAttendance();
    private final ServicePayroll servicePayroll = new ServicePayroll();
    private final ServiceTask serviceTask = new ServiceTask();
    private final ServiceDepartment serviceDepartment = new ServiceDepartment();

    // Lookup: userId → name
    private Map<Integer, String> userNames = new HashMap<>();
    // Lookup: deptId → name
    private Map<Integer, String> deptNames = new HashMap<>();

    /* ═══════════════ Initialize ═══════════════ */

    @FXML
    private void initialize() {
        setupEmployeeTable();
        setupLeaveTable();
        setupTaskTable();
        setupAttendanceTable();
        setupTaskFilter();
        setupSearch();

        loadData();
    }

    /* ═══════════════ Table setup ═══════════════ */

    private void setupEmployeeTable() {
        colName.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFirstName() + " " + d.getValue().getLastName()));
        colEmail.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmail()));
        colRole.setCellValueFactory(d -> new SimpleStringProperty(formatRole(d.getValue().getRole())));
        colDept.setCellValueFactory(d -> {
            Integer did = d.getValue().getDepartmentId();
            return new SimpleStringProperty(did != null && deptNames.containsKey(did) ? deptNames.get(did) : "—");
        });
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isActive() ? "Active" : "Frozen"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                badge.setText(item);
                if ("Active".equals(item)) {
                    badge.setStyle("-fx-background-color: rgba(34,197,94,0.12); -fx-text-fill: #22C55E; " +
                        "-fx-padding: 2 10; -fx-background-radius: 100; -fx-font-size: 11; -fx-font-weight: 600;");
                } else {
                    badge.setStyle("-fx-background-color: rgba(239,68,68,0.12); -fx-text-fill: #EF4444; " +
                        "-fx-padding: 2 10; -fx-background-radius: 100; -fx-font-size: 11; -fx-font-weight: 600;");
                }
                setGraphic(badge);
                setText(null);
            }
        });
    }

    private void setupLeaveTable() {
        colLeaveEmployee.setCellValueFactory(d -> new SimpleStringProperty(
                userNames.getOrDefault(d.getValue().getUserId(), "User #" + d.getValue().getUserId())));
        colLeaveType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getType()));
        colLeaveStart.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStartDate() != null ? d.getValue().getStartDate().toString() : "—"));
        colLeaveEnd.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getEndDate() != null ? d.getValue().getEndDate().toString() : "—"));
        colLeaveReason.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getReason()));
        colLeaveActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnApprove = new Button("Approve");
            private final Button btnReject = new Button("Reject");
            private final HBox box = new HBox(8, btnApprove, btnReject);
            {
                box.setAlignment(Pos.CENTER);
                btnApprove.getStyleClass().add("hrb-btn-approve");
                btnReject.getStyleClass().add("hrb-btn-reject");
                btnApprove.setOnAction(e -> handleLeaveAction(getTableRow().getItem(), "APPROVED"));
                btnReject.setOnAction(e -> handleLeaveAction(getTableRow().getItem(), "REJECTED"));
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void setupTaskTable() {
        colTaskTitle.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTitle()));
        colTaskAssignee.setCellValueFactory(d -> new SimpleStringProperty(
                userNames.getOrDefault(d.getValue().getAssigneeId(), "Unassigned")));
        colTaskStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colTaskStatus.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                String display = item.replace("_", " ");
                badge.setText(display);
                String[] colors = switch (item) {
                    case "TODO" -> new String[]{"#F59E0B", "rgba(245,158,11,0.12)"};
                    case "IN_PROGRESS" -> new String[]{"#3B82F6", "rgba(59,130,246,0.12)"};
                    case "IN_REVIEW" -> new String[]{"#A855F7", "rgba(168,85,247,0.12)"};
                    case "DONE" -> new String[]{"#22C55E", "rgba(34,197,94,0.12)"};
                    default -> new String[]{"#71717A", "rgba(113,113,122,0.12)"};
                };
                badge.setStyle("-fx-text-fill: " + colors[0] + "; -fx-background-color: " + colors[1] + "; " +
                    "-fx-padding: 2 10; -fx-background-radius: 100; -fx-font-size: 11; -fx-font-weight: 600;");
                setGraphic(badge);
                setText(null);
            }
        });
        colTaskPriority.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPriority()));
        colTaskPriority.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                badge.setText(item);
                String[] colors = switch (item) {
                    case "HIGH" -> new String[]{"#EF4444", "rgba(239,68,68,0.12)"};
                    case "MEDIUM" -> new String[]{"#F59E0B", "rgba(245,158,11,0.12)"};
                    case "LOW" -> new String[]{"#22C55E", "rgba(34,197,94,0.12)"};
                    default -> new String[]{"#71717A", "rgba(113,113,122,0.12)"};
                };
                badge.setStyle("-fx-text-fill: " + colors[0] + "; -fx-background-color: " + colors[1] + "; " +
                    "-fx-padding: 2 10; -fx-background-radius: 100; -fx-font-size: 11; -fx-font-weight: 600;");
                setGraphic(badge);
                setText(null);
            }
        });
        colTaskDue.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getDueDate() != null ? d.getValue().getDueDate().toString() : "—"));
    }

    private void setupAttendanceTable() {
        colAttEmployee.setCellValueFactory(d -> new SimpleStringProperty(
                userNames.getOrDefault(d.getValue().getUserId(), "User #" + d.getValue().getUserId())));
        colAttCheckIn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCheckIn() != null ? d.getValue().getCheckIn().toString() : "—"));
        colAttCheckOut.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCheckOut() != null ? d.getValue().getCheckOut().toString() : "—"));
        colAttStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        colAttStatus.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                badge.setText(item);
                String[] colors = switch (item) {
                    case "PRESENT" -> new String[]{"#22C55E", "rgba(34,197,94,0.12)"};
                    case "LATE" -> new String[]{"#F59E0B", "rgba(245,158,11,0.12)"};
                    case "ABSENT" -> new String[]{"#EF4444", "rgba(239,68,68,0.12)"};
                    case "EXCUSED" -> new String[]{"#3B82F6", "rgba(59,130,246,0.12)"};
                    default -> new String[]{"#71717A", "rgba(113,113,122,0.12)"};
                };
                badge.setStyle("-fx-text-fill: " + colors[0] + "; -fx-background-color: " + colors[1] + "; " +
                    "-fx-padding: 2 10; -fx-background-radius: 100; -fx-font-size: 11; -fx-font-weight: 600;");
                setGraphic(badge);
                setText(null);
            }
        });
    }

    private void setupTaskFilter() {
        taskFilterCombo.getItems().addAll("All", "TODO", "IN_PROGRESS", "IN_REVIEW", "DONE");
        taskFilterCombo.setValue("All");
        taskFilterCombo.setOnAction(e -> applyTaskFilter());
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter());
    }

    /* ═══════════════ Data loading ═══════════════ */

    @FXML
    private void handleRefresh() {
        SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
        loadData();
    }

    private void loadData() {
        summaryLabel.setText("Loading data...");
        new Thread(() -> {
            try {
                // 1. Load departments
                List<Department> departments = serviceDepartment.recuperer();
                deptNames.clear();
                if (departments != null) {
                    for (Department d : departments) deptNames.put(d.getId(), d.getName());
                }

                // 2. Load all users
                List<User> allUsers = serviceUser.recuperer();
                userNames.clear();
                if (allUsers != null) {
                    for (User u : allUsers) {
                        userNames.put(u.getId(), u.getFirstName() + " " + u.getLastName());
                    }
                }

                // 3. Load pending leaves
                List<Leave> pendingLeaves;
                try {
                    pendingLeaves = serviceLeave.getByStatus("PENDING");
                } catch (Exception e) {
                    // Fallback: get all and filter
                    List<Leave> all = serviceLeave.recuperer();
                    pendingLeaves = all != null ? all.stream()
                            .filter(l -> "PENDING".equals(l.getStatus())).collect(Collectors.toList()) : List.of();
                }

                // All leaves for chart
                List<Leave> allLeaves = serviceLeave.recuperer();

                // 4. Load today's attendance
                List<Attendance> todayAtt;
                try {
                    todayAtt = serviceAttendance.getByDate(Date.valueOf(LocalDate.now()));
                } catch (Exception e) {
                    todayAtt = List.of();
                }

                // 5. Load payrolls
                List<Payroll> payrolls;
                try {
                    payrolls = servicePayroll.recuperer();
                } catch (Exception e) {
                    payrolls = List.of();
                }
                long unpaidPayrolls = payrolls != null ? payrolls.stream()
                        .filter(p -> "PENDING".equalsIgnoreCase(p.getStatus())).count() : 0;

                // 6. Load all tasks
                List<Task> allTasks = serviceTask.recuperer();
                long openTasks = allTasks != null ? allTasks.stream()
                        .filter(t -> !"DONE".equals(t.getStatus())).count() : 0;

                // Final copies for UI thread
                final List<User> fUsers = allUsers != null ? allUsers : List.of();
                final List<Leave> fPending = pendingLeaves != null ? pendingLeaves : List.of();
                final List<Leave> fAllLeaves = allLeaves != null ? allLeaves : List.of();
                final List<Attendance> fAtt = todayAtt != null ? todayAtt : List.of();
                final List<Task> fTasks = allTasks != null ? allTasks : List.of();
                final long fUnpaid = unpaidPayrolls;
                final long fOpen = openTasks;

                Platform.runLater(() -> {
                    // Stat cards
                    totalEmployeesLabel.setText(String.valueOf(fUsers.size()));
                    pendingLeavesLabel.setText(String.valueOf(fPending.size()));
                    presentTodayLabel.setText(String.valueOf(fAtt.stream()
                            .filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus())).count()));
                    pendingPayrollLabel.setText(String.valueOf(fUnpaid));
                    openTasksLabel.setText(String.valueOf(fOpen));

                    summaryLabel.setText(String.format(
                            "%d employees • %d pending leaves • %d present today • %d unpaid payrolls • %d open tasks",
                            fUsers.size(), fPending.size(),
                            fAtt.stream().filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus())).count(),
                            fUnpaid, fOpen));

                    // Employee table
                    allEmployees = FXCollections.observableArrayList(fUsers);
                    filteredEmployees = new FilteredList<>(allEmployees, p -> true);
                    employeeTable.setItems(filteredEmployees);
                    applySearchFilter();

                    // Leave table
                    leaveTable.setItems(FXCollections.observableArrayList(fPending));

                    // Task table
                    allTasks2 = FXCollections.observableArrayList(fTasks);
                    filteredTasks = new FilteredList<>(allTasks2, p -> true);
                    taskTable.setItems(filteredTasks);
                    applyTaskFilter();

                    // Attendance table
                    attendanceTable.setItems(FXCollections.observableArrayList(fAtt));

                    // Charts
                    buildDeptChart(fUsers);
                    buildLeaveChart(fAllLeaves);
                });

            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> summaryLabel.setText("⚠ Failed to load data: " + ex.getMessage()));
            }
        }, "hr-backlog-loader").start();
    }

    /* ═══════════════ Filtering ═══════════════ */

    private ObservableList<User> allEmployees = FXCollections.observableArrayList();
    private FilteredList<User> filteredEmployees;

    private ObservableList<Task> allTasks2 = FXCollections.observableArrayList();
    private FilteredList<Task> filteredTasks;

    private void applySearchFilter() {
        String query = searchField.getText();
        if (filteredEmployees == null) return;
        if (query == null || query.isBlank()) {
            filteredEmployees.setPredicate(p -> true);
        } else {
            String lower = query.toLowerCase();
            filteredEmployees.setPredicate(u ->
                    (u.getFirstName() + " " + u.getLastName()).toLowerCase().contains(lower)
                    || u.getEmail().toLowerCase().contains(lower)
                    || (u.getRole() != null && u.getRole().toLowerCase().contains(lower)));
        }
    }

    private void applyTaskFilter() {
        if (filteredTasks == null) return;
        String sel = taskFilterCombo.getValue();
        if (sel == null || "All".equals(sel)) {
            filteredTasks.setPredicate(p -> true);
        } else {
            filteredTasks.setPredicate(t -> sel.equals(t.getStatus()));
        }
    }

    /* ═══════════════ Charts ═══════════════ */

    private static final String[] CHART_COLORS = {
        "#3B82F6", "#22C55E", "#F59E0B", "#EF4444", "#A855F7", "#EC4899", "#06B6D4", "#F97316"
    };

    private void buildDeptChart(List<User> users) {
        Map<String, Long> counts = users.stream()
                .collect(Collectors.groupingBy(
                        u -> u.getDepartmentId() != null && deptNames.containsKey(u.getDepartmentId())
                                ? deptNames.get(u.getDepartmentId()) : "No Dept",
                        Collectors.counting()));
        deptChart.setData(FXCollections.observableArrayList(
                counts.entrySet().stream()
                        .map(e -> new PieChart.Data(e.getKey() + " (" + e.getValue() + ")", e.getValue()))
                        .collect(Collectors.toList())));
        deptChart.setLabelsVisible(false);
        Platform.runLater(() -> applyChartColors(deptChart, deptLegendBox));
    }

    private void buildLeaveChart(List<Leave> leaves) {
        Map<String, Long> counts = leaves.stream()
                .collect(Collectors.groupingBy(Leave::getType, Collectors.counting()));
        leaveChart.setData(FXCollections.observableArrayList(
                counts.entrySet().stream()
                        .map(e -> new PieChart.Data(e.getKey() + " (" + e.getValue() + ")", e.getValue()))
                        .collect(Collectors.toList())));
        leaveChart.setLabelsVisible(false);
        Platform.runLater(() -> applyChartColors(leaveChart, leaveLegendBox));
    }

    private void applyChartColors(PieChart chart, VBox legendBox) {
        legendBox.getChildren().clear();
        int i = 0;
        for (PieChart.Data d : chart.getData()) {
            String color = CHART_COLORS[i % CHART_COLORS.length];
            if (d.getNode() != null) {
                d.getNode().setStyle("-fx-pie-color: " + color + ";");
            }
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Region dot = new Region();
            dot.setMinSize(10, 10);
            dot.setMaxSize(10, 10);
            dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
            Label lbl = new Label(d.getName());
            lbl.getStyleClass().add("hrb-legend-label");
            row.getChildren().addAll(dot, lbl);
            legendBox.getChildren().add(row);
            i++;
        }
    }

    /* ═══════════════ Leave approve/reject ═══════════════ */

    private void handleLeaveAction(Leave leave, String newStatus) {
        if (leave == null) return;
        try {
            leave.setStatus(newStatus);
            if ("REJECTED".equals(newStatus)) {
                TextInputDialog dlg = new TextInputDialog();
                dlg.setTitle("Rejection Reason");
                dlg.setHeaderText("Why is this leave request being rejected?");
                dlg.setContentText("Reason:");
                DialogHelper.theme(dlg);
                Optional<String> reason = dlg.showAndWait();
                if (reason.isEmpty()) return; // cancelled
                leave.setRejectionReason(reason.get());
            }
            serviceLeave.modifier(leave);
            SoundManager.getInstance().play(SoundManager.TASK_COMPLETED);
            loadData(); // refresh
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to update leave: " + e.getMessage());
            DialogHelper.theme(alert);
            alert.show();
        }
    }

    /* ═══════════════ Helpers ═══════════════ */

    private String formatRole(String role) {
        if (role == null) return "—";
        return switch (role) {
            case "ADMIN" -> "Admin";
            case "HR_MANAGER" -> "HR Manager";
            case "EMPLOYEE" -> "Employee";
            case "PROJECT_OWNER" -> "Project Owner";
            case "GIG_WORKER" -> "Gig Worker";
            default -> role;
        };
    }

    @FXML
    private void handleBack() {
        DashboardController.getInstance().navigateTo("/fxml/HRModule.fxml");
    }

    /* ═══════════════ Cleanup ═══════════════ */

    @Override
    public void stop() {
        // no background threads to stop
    }
}
