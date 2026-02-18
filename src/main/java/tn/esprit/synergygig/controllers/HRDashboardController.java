package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import tn.esprit.synergygig.services.AttendanceService;
import tn.esprit.synergygig.services.DepartmentService;
import tn.esprit.synergygig.services.LeaveService;
import tn.esprit.synergygig.entities.enums.AttendanceStatus;

import java.time.LocalDate;

public class HRDashboardController {

    @FXML
    private Label totalDepartments;
    @FXML
    private Label presentToday;
    @FXML
    private Label pendingLeaves;

    private DepartmentService departmentService;
    private AttendanceService attendanceService;
    private LeaveService leaveService;
    private HRMainController mainController;

    public void initialize() {
        departmentService = new DepartmentService();
        attendanceService = new AttendanceService();
        leaveService = new LeaveService();

        loadStats();
    }

    public void setMainController(HRMainController mainController) {
        this.mainController = mainController;
    }

    private void loadStats() {
        totalDepartments.setText(String.valueOf(departmentService.getAllDepartments().size()));
        pendingLeaves.setText(String.valueOf(leaveService.getPendingLeaves().size()));
        
        long presentCount = attendanceService.getAllAttendance().stream()
                .filter(a -> a.getDate().toString().equals(java.sql.Date.valueOf(LocalDate.now()).toString()))
                .filter(a -> a.getStatus() == AttendanceStatus.PRESENT || a.getStatus() == AttendanceStatus.LATE)
                .count();
        presentToday.setText(String.valueOf(presentCount));
    }
    
    @FXML
    private void navigateToDepartments(MouseEvent event) {
        if (mainController != null) {
            mainController.showDepartments();
        }
    }

    @FXML
    private void navigateToAttendance(MouseEvent event) {
        if (mainController != null) {
            mainController.showAttendance();
        }
    }

    @FXML
    private void navigateToLeaves(MouseEvent event) {
        if (mainController != null) {
            mainController.showLeaves();
        }
    }
}
