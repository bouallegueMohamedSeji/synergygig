package tn.esprit.synergygig.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import tn.esprit.synergygig.entities.Leave;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.LeaveStatus;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.services.LeaveService;
import tn.esprit.synergygig.utils.UserSession;

import java.sql.Date;

public class LeaveController {

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private ComboBox<String> reasonCombo;
    @FXML private VBox adminPanel;

    @FXML private TableView<Leave> leaveTable;
    @FXML private TableColumn<Leave, String> colUser;
    @FXML private TableColumn<Leave, Date> colStart;
    @FXML private TableColumn<Leave, Date> colEnd;
    @FXML private TableColumn<Leave, String> colReason;
    @FXML private TableColumn<Leave, String> colStatus;

    private LeaveService leaveService;
    private ObservableList<Leave> leaveList;
    private User currentUser;

    public void initialize() {
        leaveService = new LeaveService();
        leaveList = FXCollections.observableArrayList();
        
        if (UserSession.getInstance() != null) {
            currentUser = UserSession.getInstance().getUser();
        }

        setupTable();
        setupReasonCombo();
        
        // Hide Admin Panel if not HR/Admin
        if (currentUser != null && (currentUser.getRole() == Role.EMPLOYEE || currentUser.getRole() == Role.GIG_WORKER)) {
            adminPanel.setVisible(false);
            adminPanel.setManaged(false); // Remove space
        }
        
        loadLeaves();
    }
    
    private void setupReasonCombo() {
        reasonCombo.getItems().addAll("Sick Leave", "Vacation", "Maternity Leave", "Teletravail", "Other");
    }

    private void setupTable() {
        colStart.setCellValueFactory(new PropertyValueFactory<>("startDate"));
        colEnd.setCellValueFactory(new PropertyValueFactory<>("endDate"));
        colReason.setCellValueFactory(new PropertyValueFactory<>("reason"));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        colUser.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUser().getFullName()));
    }

    private void loadLeaves() {
        leaveList.clear();
        if (currentUser != null) {
            if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.HR) {
                // Admin/HR sees all leaves by default
                leaveList.addAll(leaveService.getAllLeaves());
            } else {
                // Employees see only their own
                leaveList.addAll(leaveService.getMyLeaves(currentUser.getId()));
            }
        }
        leaveTable.setItems(leaveList);
    }

    @FXML
    private void requestLeave() {
        if (currentUser == null) return;
        
        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null || reasonCombo.getValue() == null) {
            showAlert("Error", "Please fill all fields");
            return;
        }
        
        String reason = reasonCombo.getValue();
        Date sqlStartDate = Date.valueOf(startDatePicker.getValue());
        Date sqlEndDate = Date.valueOf(endDatePicker.getValue());
        
        // Telework Check
        if ("Teletravail".equals(reason)) {
            long teleworkCount = leaveService.getMyLeaves(currentUser.getId()).stream()
                    .filter(l -> "Teletravail".equals(l.getReason()))
                    .filter(l -> {
                        // Simple check: is it in the same ISO week?
                        // For robust prod app, use WeekFields.
                        java.time.LocalDate date = l.getStartDate().toLocalDate();
                        java.time.LocalDate reqDate = startDatePicker.getValue();
                        java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault());
                        return date.get(weekFields.weekOfWeekBasedYear()) == reqDate.get(weekFields.weekOfWeekBasedYear()) &&
                                date.getYear() == reqDate.getYear();
                    })
                    .count();
            
            if (teleworkCount >= 2) {
                showAlert("Restriction", "You can only request Teletravail twice a week.");
                return;
            }
        }

        Leave leave = new Leave();
        leave.setUser(currentUser);
        leave.setStartDate(sqlStartDate);
        leave.setEndDate(sqlEndDate);
        leave.setReason(reason);
        
        leaveService.requestLeave(leave);
        
        // Notification
        tn.esprit.synergygig.utils.EmailService.send("admin@synergygig.com", 
                "New Leave Request from " + currentUser.getFullName(), 
                "User " + currentUser.getFullName() + " requested " + reason + " from " + sqlStartDate + " to " + sqlEndDate);
        
        loadLeaves();
        clearFields();
    }
    
    @FXML
    private void approveLeave() {
        processLeave(LeaveStatus.APPROVED);
    }
    
    @FXML
    private void rejectLeave() {
        processLeave(LeaveStatus.REJECTED);
    }
    
    private void processLeave(LeaveStatus status) {
        Leave selected = leaveTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.getStatus() == LeaveStatus.PENDING) {
                leaveService.updateLeaveStatus(selected, status);
                loadLeaves();
            } else {
                showAlert("Info", "Only PENDING leaves can be processed.");
            }
        } else {
             showAlert("Warning", "Select a leave request.");
        }
    }

    private void clearFields() {
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        reasonCombo.getSelectionModel().clearSelection();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }
}
