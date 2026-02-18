package tn.esprit.synergygig.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import tn.esprit.synergygig.entities.Attendance;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.services.AttendanceService;
import tn.esprit.synergygig.utils.UserSession;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;

public class AttendanceController {

    @FXML private TableView<Attendance> attendanceTable;
    @FXML private TableColumn<Attendance, Date> colDate;
    @FXML private TableColumn<Attendance, Time> colCheckIn;
    @FXML private TableColumn<Attendance, Time> colCheckOut;
    @FXML private TableColumn<Attendance, String> colStatus;
    @FXML private TableColumn<Attendance, String> colUser;
    
    @FXML private CheckBox viewAllCheck;
    @FXML private DatePicker dateFilter;

    private AttendanceService attendanceService;
    private ObservableList<Attendance> attendanceList;
    private User currentUser;

    public void initialize() {
        attendanceService = new AttendanceService();
        attendanceList = FXCollections.observableArrayList();
        
        if (UserSession.getInstance() != null) {
            currentUser = UserSession.getInstance().getUser();
        }

        setupTable();
        
        // Hide "View All" if not HR/Admin
        if (currentUser != null && (currentUser.getRole() == Role.EMPLOYEE || currentUser.getRole() == Role.GIG_WORKER)) {
            viewAllCheck.setVisible(false);
        }
        
        // Add listener for date filter
        if (dateFilter != null) {
            dateFilter.valueProperty().addListener((obs, oldDate, newDate) -> filterByDate());
        }
        
        loadAttendance();
    }

    private void setupTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colCheckIn.setCellValueFactory(new PropertyValueFactory<>("checkIn"));
        colCheckOut.setCellValueFactory(new PropertyValueFactory<>("checkOut"));
        colStatus.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().toString()));
        
        colUser.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUser().getFullName()));
    }

    private void loadAttendance() {
        attendanceList.clear();
        if (viewAllCheck != null && viewAllCheck.isSelected()) {
            attendanceList.addAll(attendanceService.getAllAttendance());
        } else {
            if (currentUser != null) {
                attendanceList.addAll(attendanceService.getAttendanceHistory(currentUser.getId()));
            }
        }
        attendanceTable.setItems(attendanceList);
        
        // Apply filter if date is selected
        if (dateFilter != null && dateFilter.getValue() != null) {
            filterByDate();
        }
    }

    @FXML
    private void filterByDate() {
        if (dateFilter != null && dateFilter.getValue() != null) {
            LocalDate selectedDate = dateFilter.getValue();
            ObservableList<Attendance> filteredList = FXCollections.observableArrayList();
            
            // Reload full list to filter from
            ObservableList<Attendance> fullList = FXCollections.observableArrayList();
             if (viewAllCheck != null && viewAllCheck.isSelected()) {
                fullList.addAll(attendanceService.getAllAttendance());
            } else {
                if (currentUser != null) {
                    fullList.addAll(attendanceService.getAttendanceHistory(currentUser.getId()));
                }
            }

            for (Attendance att : fullList) {
                if (att.getDate() != null && att.getDate().toLocalDate().equals(selectedDate)) {
                    filteredList.add(att);
                }
            }
            attendanceTable.setItems(filteredList);
        } else {
            // Reset to full list
            loadAttendance();
        }
    }

    @FXML
    private void checkIn() {
        if (currentUser != null) {
            boolean success = attendanceService.markCheckIn(currentUser);
            if (success) {
                showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Success", "Checked In Successfully!");
                loadAttendance();
            } else {
                showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Warning", "You have already checked in today.");
            }
        }
    }

    @FXML
    private void checkOut() {
        if (currentUser != null) {
            boolean success = attendanceService.markCheckOut(currentUser);
            if (success) {
                showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Success", "Checked Out Successfully!");
                loadAttendance();
            } else {
                showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Warning", "Cannot Check Out. Either you haven't checked in or already checked out.");
            }
        }
    }
    
    private void showAlert(javafx.scene.control.Alert.AlertType type, String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    @FXML
    private void toggleViewAll() {
        loadAttendance();
    }
}
