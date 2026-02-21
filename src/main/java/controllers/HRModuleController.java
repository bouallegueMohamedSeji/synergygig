package controllers;

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
import utils.DialogHelper;
import utils.SessionManager;
import utils.SoundManager;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
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

    private User currentUser;
    private boolean isHrOrAdmin;
    private Button activeTab;

    // Cached data
    private List<User> allUsers = new ArrayList<>();
    private Map<Integer, String> userNameMap = new HashMap<>();

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

        for (String[] tab : tabs) {
            Button btn = new Button(tab[0] + "  " + tab[1]);
            btn.getStyleClass().add("hr-tab-btn");
            btn.setOnAction(e -> {
                SoundManager.getInstance().play(SoundManager.TAB_SWITCH);
                switchTab(btn, tab[1]);
            });
            tabBar.getChildren().add(btn);
        }

        // Select first tab
        if (!tabBar.getChildren().isEmpty()) {
            Button first = (Button) tabBar.getChildren().get(0);
            switchTab(first, "Overview");
        }
    }

    private void loadUserNames() {
        try {
            allUsers = serviceUser.recuperer();
            for (User u : allUsers) {
                userNameMap.put(u.getId(), u.getFirstName() + " " + u.getLastName());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String getUserName(int userId) {
        return userNameMap.getOrDefault(userId, "User #" + userId);
    }

    private void switchTab(Button btn, String tabName) {
        if (activeTab != null) activeTab.getStyleClass().remove("hr-tab-active");
        btn.getStyleClass().add("hr-tab-active");
        activeTab = btn;

        switch (tabName) {
            case "Overview":     showOverview(); break;
            case "Departments":  showDepartments(); break;
            case "Attendance":   showAttendance(); break;
            case "Leaves":       showLeaves(); break;
            case "Payroll":      showPayroll(); break;
        }
    }

    // ==================== OVERVIEW TAB ====================

    private void showOverview() {
        VBox view = new VBox(20);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        Label title = new Label("HR Overview");
        title.getStyleClass().add("hr-view-title");

        // Stats cards row
        HBox statsRow = new HBox(16);
        statsRow.setAlignment(Pos.CENTER_LEFT);

        try {
            List<Department> depts = serviceDepartment.recuperer();
            List<Attendance> attendance = serviceAttendance.recuperer();
            List<Leave> leaves = serviceLeave.recuperer();
            List<Payroll> payrolls = servicePayroll.recuperer();

            long totalEmployees = allUsers.stream().filter(u -> !"ADMIN".equals(u.getRole())).count();
            long presentToday = attendance.stream()
                    .filter(a -> a.getDate() != null && a.getDate().toLocalDate().equals(LocalDate.now()))
                    .filter(a -> "PRESENT".equals(a.getStatus()) || "LATE".equals(a.getStatus()))
                    .count();
            long pendingLeaves = leaves.stream().filter(l -> "PENDING".equals(l.getStatus())).count();
            long pendingPayrolls = payrolls.stream().filter(p -> "PENDING".equals(p.getStatus())).count();

            statsRow.getChildren().addAll(
                    createStatCard("👥", "Total Employees", String.valueOf(totalEmployees), "hr-stat-blue"),
                    createStatCard("🏢", "Departments", String.valueOf(depts.size()), "hr-stat-green"),
                    createStatCard("✅", "Present Today", String.valueOf(presentToday), "hr-stat-purple"),
                    createStatCard("📋", "Pending Leaves", String.valueOf(pendingLeaves), "hr-stat-orange"),
                    createStatCard("💰", "Pending Payroll", String.valueOf(pendingPayrolls), "hr-stat-red")
            );
        } catch (SQLException e) {
            statsRow.getChildren().add(new Label("Error loading stats: " + e.getMessage()));
        }

        // Recent activity sections
        VBox recentSection = new VBox(16);

        // Recent leaves
        VBox recentLeaves = createRecentLeavesSection();
        // Recent attendance
        VBox recentAttendance = createRecentAttendanceSection();

        recentSection.getChildren().addAll(recentLeaves, recentAttendance);

        view.getChildren().addAll(title, statsRow, recentSection);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);
    }

    private VBox createStatCard(String icon, String label, String value, String styleClass) {
        VBox card = new VBox(6);
        card.getStyleClass().addAll("hr-stat-card", styleClass);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(180);
        card.setPadding(new Insets(16));

        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 28px;");

        Label valueLbl = new Label(value);
        valueLbl.getStyleClass().add("hr-stat-value");

        Label nameLbl = new Label(label);
        nameLbl.getStyleClass().add("hr-stat-label");

        card.getChildren().addAll(iconLbl, valueLbl, nameLbl);
        return card;
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
                            refreshDepartmentList(container);
                        } catch (SQLException ex) {
                            showError("Failed to delete department: " + ex.getMessage());
                        }
                    });
                });

                actions.getChildren().addAll(editBtn, deleteBtn);
                card.getChildren().addAll(info, actions);
                container.getChildren().add(card);
            }
        } catch (SQLException e) {
            container.getChildren().add(new Label("Error loading departments: " + e.getMessage()));
        }
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
        for (User u : allUsers) {
            managerCombo.getItems().add(u.getId() + " - " + u.getFirstName() + " " + u.getLastName());
        }
        if (existing != null && existing.getManagerId() != null) {
            String sel = existing.getManagerId() + " - " + getUserName(existing.getManagerId());
            managerCombo.setValue(sel);
        } else {
            managerCombo.setValue("None");
        }
        managerCombo.getStyleClass().add("hr-form-control");

        TextField budgetField = new TextField(existing != null ? String.valueOf(existing.getAllocatedBudget()) : "0.0");
        budgetField.setPromptText("Budget (TND)");
        budgetField.getStyleClass().add("hr-form-control");

        content.getChildren().addAll(
                header,
                new Label("Name") {{ getStyleClass().add("hr-form-label"); }}, nameField,
                new Label("Description") {{ getStyleClass().add("hr-form-label"); }}, descField,
                new Label("Manager") {{ getStyleClass().add("hr-form-label"); }}, managerCombo,
                new Label("Budget (TND)") {{ getStyleClass().add("hr-form-label"); }}, budgetField
        );

        dp.setContent(content);

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
                try {
                    d.setAllocatedBudget(Double.parseDouble(budgetField.getText().trim()));
                } catch (NumberFormatException ex) {
                    d.setAllocatedBudget(0.0);
                }
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
        VBox view = new VBox(16);
        view.getStyleClass().add("hr-view");
        view.setPadding(new Insets(24));

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Leave Requests");
        title.getStyleClass().add("hr-view-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

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

        header.getChildren().addAll(title, spacer, statusFilter, requestBtn);

        VBox leaveList = new VBox(8);

        view.getChildren().addAll(header, leaveList);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);

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

            if (leaves.isEmpty()) {
                Label empty = new Label("No leave requests found.");
                empty.getStyleClass().add("hr-empty-label");
                container.getChildren().add(empty);
                return;
            }

            // Header
            HBox headerRow = new HBox(12);
            headerRow.getStyleClass().add("hr-table-header");
            headerRow.setPadding(new Insets(8, 12, 8, 12));
            headerRow.getChildren().addAll(
                    createColLabel("Employee", 140),
                    createColLabel("Type", 80),
                    createColLabel("From", 90),
                    createColLabel("To", 90),
                    createColLabel("Days", 45),
                    createColLabel("Reason", 140),
                    createColLabel("Status", 80),
                    createColLabel("Actions", 120)
            );
            container.getChildren().add(headerRow);

            for (Leave l : leaves) {
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                Label name = createColLabel(getUserName(l.getUserId()), 140);
                Label type = createColLabel(l.getType(), 80);
                Label from = createColLabel(l.getStartDate() != null ? l.getStartDate().toString() : "—", 90);
                Label to = createColLabel(l.getEndDate() != null ? l.getEndDate().toString() : "—", 90);
                Label days = createColLabel(String.valueOf(l.getDays()), 45);
                Label reason = createColLabel(l.getReason() != null ? l.getReason() : "—", 140);

                Label status = new Label(l.getStatus());
                status.getStyleClass().addAll("hr-status-badge", "hr-status-" + l.getStatus().toLowerCase());
                status.setPrefWidth(80);

                HBox actions = new HBox(4);
                actions.setPrefWidth(120);
                actions.setAlignment(Pos.CENTER_LEFT);

                if (isHrOrAdmin && "PENDING".equals(l.getStatus())) {
                    Button approveBtn = new Button("✓");
                    approveBtn.getStyleClass().addAll("hr-icon-btn", "hr-success-btn");
                    approveBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.LEAVE_APPROVED);
                        l.setStatus("APPROVED");
                        try {
                            serviceLeave.modifier(l);
                            showLeaves();
                        } catch (SQLException ex) {
                            showError("Failed to approve: " + ex.getMessage());
                        }
                    });

                    Button rejectBtn = new Button("✗");
                    rejectBtn.getStyleClass().addAll("hr-icon-btn", "hr-danger-btn");
                    rejectBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.LEAVE_REJECTED);
                        l.setStatus("REJECTED");
                        try {
                            serviceLeave.modifier(l);
                            showLeaves();
                        } catch (SQLException ex) {
                            showError("Failed to reject: " + ex.getMessage());
                        }
                    });
                    actions.getChildren().addAll(approveBtn, rejectBtn);
                }

                Button deleteBtn = new Button("🗑");
                deleteBtn.getStyleClass().addAll("hr-icon-btn", "hr-danger-btn");
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

        content.getChildren().addAll(
                header,
                new Label("Employee") {{ getStyleClass().add("hr-form-label"); }}, userCombo,
                new Label("Type") {{ getStyleClass().add("hr-form-label"); }}, typeCombo,
                new Label("Start Date") {{ getStyleClass().add("hr-form-label"); }}, startPicker,
                new Label("End Date") {{ getStyleClass().add("hr-form-label"); }}, endPicker,
                new Label("Reason") {{ getStyleClass().add("hr-form-label"); }}, reasonField
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
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button generateBtn = new Button("⚡ Generate Payroll");
        generateBtn.getStyleClass().add("hr-primary-btn");
        generateBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.PAYROLL_GENERATED);
            showGeneratePayrollDialog();
        });

        Button addBtn = new Button("+ Add Entry");
        addBtn.getStyleClass().add("hr-primary-btn");
        addBtn.setOnAction(e -> {
            SoundManager.getInstance().play(SoundManager.BUTTON_CLICK);
            showPayrollDialog(null);
        });

        header.getChildren().addAll(title, spacer, generateBtn, addBtn);

        VBox payrollList = new VBox(8);

        view.getChildren().addAll(header, payrollList);

        ScrollPane scroll = new ScrollPane(view);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("hr-scroll");
        contentArea.getChildren().setAll(scroll);

        refreshPayrollList(payrollList);
    }

    private void refreshPayrollList(VBox container) {
        container.getChildren().clear();
        try {
            List<Payroll> payrolls = servicePayroll.recuperer();

            if (payrolls.isEmpty()) {
                Label empty = new Label("No payroll records yet.");
                empty.getStyleClass().add("hr-empty-label");
                container.getChildren().add(empty);
                return;
            }

            // Header
            HBox headerRow = new HBox(10);
            headerRow.getStyleClass().add("hr-table-header");
            headerRow.setPadding(new Insets(8, 12, 8, 12));
            headerRow.getChildren().addAll(
                    createColLabel("Employee", 130),
                    createColLabel("Month", 80),
                    createColLabel("Base", 70),
                    createColLabel("Bonus", 60),
                    createColLabel("Deductions", 75),
                    createColLabel("Net", 70),
                    createColLabel("Hours", 50),
                    createColLabel("Status", 70),
                    createColLabel("Actions", 100)
            );
            container.getChildren().add(headerRow);

            for (Payroll p : payrolls) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("hr-list-row");
                row.setPadding(new Insets(8, 12, 8, 12));

                Label name = createColLabel(getUserName(p.getUserId()), 130);
                Label month = createColLabel(p.getMonth() != null ? p.getMonth().toString() : "—", 80);
                Label base = createColLabel(String.format("%.0f", p.getBaseSalary()), 70);
                Label bonus = createColLabel(String.format("%.0f", p.getBonus()), 60);
                Label ded = createColLabel(String.format("%.0f", p.getDeductions()), 75);
                Label net = createColLabel(String.format("%.0f", p.getNetSalary()), 70);
                net.getStyleClass().add("hr-highlight");
                Label hours = createColLabel(String.format("%.1f", p.getTotalHoursWorked()), 50);

                Label status = new Label(p.getStatus());
                status.getStyleClass().addAll("hr-status-badge", "hr-status-" + p.getStatus().toLowerCase());
                status.setPrefWidth(70);

                HBox actions = new HBox(4);
                actions.setPrefWidth(100);

                if ("PENDING".equals(p.getStatus())) {
                    Button payBtn = new Button("💵");
                    payBtn.getStyleClass().addAll("hr-icon-btn", "hr-success-btn");
                    payBtn.setOnAction(e -> {
                        SoundManager.getInstance().play(SoundManager.PAYROLL_GENERATED);
                        p.setStatus("PAID");
                        try {
                            servicePayroll.modifier(p);
                            refreshPayrollList(container);
                        } catch (SQLException ex) {
                            showError("Failed to mark paid: " + ex.getMessage());
                        }
                    });
                    actions.getChildren().add(payBtn);
                }

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
                            refreshPayrollList(container);
                        } catch (SQLException ex) {
                            showError("Failed to delete: " + ex.getMessage());
                        }
                    });
                });

                actions.getChildren().addAll(editBtn, deleteBtn);
                row.getChildren().addAll(name, month, base, bonus, ded, net, hours, status, actions);
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

                // Fetch existing payrolls if skip is checked
                Set<Integer> existingUserIds = new HashSet<>();
                if (skipExisting.isSelected()) {
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
                final double baseDefaultRate = defaultRate;

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
                            // Monthly salaried employee
                            baseSalary = u.getMonthlySalary();
                        } else {
                            // Hourly employee: pay = hours * rate
                            baseSalary = totalHours * hourlyRate;
                        }

                        // Count absent days for deductions
                        long absentDays = userAtt.stream()
                                .filter(a -> a.getDate() != null &&
                                        a.getDate().toLocalDate().getMonth() == selectedMonth.getMonth() &&
                                        a.getDate().toLocalDate().getYear() == selectedMonth.getYear() &&
                                        "ABSENT".equals(a.getStatus()))
                                .count();
                        // Deduction: ~daily rate * absent days (assume 22 working days)
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
                showInfo("Payroll generated for " + generated + " employees." +
                        (skipped > 0 ? "\n" + skipped + " skipped (already existed)." : ""));
                showPayroll();
            }
        });
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
