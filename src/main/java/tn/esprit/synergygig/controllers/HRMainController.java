package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HRMainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnOverview;
    @FXML private Button btnDepartments;
    @FXML private Button btnAttendance;
    @FXML private Button btnLeaves;
    @FXML private Button btnPayroll;
    @FXML private Button btnUsers;

    // Cache views to avoid reloading
    private Map<String, Parent> views = new HashMap<>();

    public void initialize() {
        // Load default view (Dashboard)
        showOverview();
    }

    @FXML
    public void showOverview() {
        loadView("HRDashboard.fxml", btnOverview);
    }

    @FXML
    public void showDepartments() {
        loadView("DepartmentView.fxml", btnDepartments);
    }

    @FXML
    public void showAttendance() {
        loadView("AttendanceView.fxml", btnAttendance);
    }

    @FXML
    public void showLeaves() {
        loadView("LeaveView.fxml", btnLeaves);
    }

    @FXML
    public void showPayroll() {
        loadView("PayrollView.fxml", btnPayroll);
    }
    
    @FXML
    public void showUsers() {
        loadView("UserListView.fxml", btnUsers);
    }

    private void loadView(String fxmlFile, Button activeButton) {
        try {
            Parent view;
            if (views.containsKey(fxmlFile) && !fxmlFile.equals("HRDashboard.fxml")) {
                view = views.get(fxmlFile);
            } else {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/tn/esprit/synergygig/gui/" + fxmlFile));
                view = loader.load();
                views.put(fxmlFile, view);
                
                // Special handling if we need to pass data to controllers can go here
                if(fxmlFile.equals("HRDashboard.fxml")) {
                     HRDashboardController dashboardController = loader.getController();
                     dashboardController.setMainController(this);
                }
            }
            
            contentArea.getChildren().setAll(view);
            setActiveButton(activeButton);

        } catch (IOException e) {
            System.err.println("Error loading view: " + fxmlFile);
            e.printStackTrace();
        }
    }

    private void setActiveButton(Button button) {
        // Reset all buttons
        btnOverview.getStyleClass().remove("active");
        btnDepartments.getStyleClass().remove("active");
        btnAttendance.getStyleClass().remove("active");
        btnLeaves.getStyleClass().remove("active");
        btnPayroll.getStyleClass().remove("active");
        if(btnUsers != null) btnUsers.getStyleClass().remove("active");

        // Set active
        button.getStyleClass().add("active");
    }
}
