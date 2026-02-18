package tn.esprit.synergygig.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import tn.esprit.synergygig.entities.Payroll;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.services.PayrollService;
import tn.esprit.synergygig.services.UserService;
import tn.esprit.synergygig.utils.UserSession;

import java.time.LocalDate;
import java.util.Optional;

public class PayrollController {

    @FXML private VBox generatePanel;
    @FXML private ComboBox<User> userCombo;
    @FXML private ComboBox<String> monthCombo;
    @FXML private TextField yearField;
    @FXML private TextField baseSalaryField;
    @FXML private TextField bonusField;

    @FXML private TableView<Payroll> payrollTable;
    @FXML private TableColumn<Payroll, String> colUser;
    @FXML private TableColumn<Payroll, String> colMonth;
    @FXML private TableColumn<Payroll, Integer> colYear;
    @FXML private TableColumn<Payroll, Double> colHours;
    @FXML private TableColumn<Payroll, Double> colRate;
    @FXML private TableColumn<Payroll, Double> colBase;
    @FXML private TableColumn<Payroll, Double> colBonus;
    @FXML private TableColumn<Payroll, Double> colDeductions;
    @FXML private TableColumn<Payroll, Double> colNet;

    private PayrollService payrollService;
    private UserService userService;
    private ObservableList<Payroll> payrollList;
    private User currentUser;

    public void initialize() {
        payrollService = new PayrollService();
        userService = new UserService();
        payrollList = FXCollections.observableArrayList();
        
        if (UserSession.getInstance() != null) {
            currentUser = UserSession.getInstance().getUser();
        }

        setupTable();
        setupUserCombo();
        setupMonthCombo();
        
        // Hide Generate Panel if not HR/Admin
        if (currentUser != null && (currentUser.getRole() == Role.EMPLOYEE || currentUser.getRole() == Role.GIG_WORKER)) {
            generatePanel.setVisible(false);
            generatePanel.setManaged(false);
        }
        
        loadPayrolls();
        
        // Default month/year
        monthCombo.setValue(LocalDate.now().getMonth().toString());
        yearField.setText(String.valueOf(LocalDate.now().getYear()));
    }

    private void setupTable() {
        colUser.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUser().getFullName()));
        colMonth.setCellValueFactory(new PropertyValueFactory<>("month"));
        colYear.setCellValueFactory(new PropertyValueFactory<>("year"));
        colHours.setCellValueFactory(new PropertyValueFactory<>("totalHoursWorked"));
        colRate.setCellValueFactory(new PropertyValueFactory<>("hourlyRate"));
        colBase.setCellValueFactory(new PropertyValueFactory<>("baseSalary"));
        colBonus.setCellValueFactory(new PropertyValueFactory<>("bonus"));
        colDeductions.setCellValueFactory(new PropertyValueFactory<>("deductions"));
        colNet.setCellValueFactory(new PropertyValueFactory<>("netSalary"));
        
        // Context Menu for Editing
        if (currentUser != null && (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.HR)) {
            payrollTable.setRowFactory(tv -> {
                TableRow<Payroll> row = new TableRow<>();
                ContextMenu contextMenu = new ContextMenu();
                
                MenuItem editItem = new MenuItem("Edit Salary & Bonus");
                editItem.setOnAction(event -> showEditPayrollDialog(row.getItem()));
                
                MenuItem deleteItem = new MenuItem("Delete Record");
                deleteItem.setOnAction(event -> deletePayroll(row.getItem()));
                
                contextMenu.getItems().addAll(editItem, deleteItem);
                
                row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
                );
                return row;
            });
        }
    }

    private void setupMonthCombo() {
        ObservableList<String> months = FXCollections.observableArrayList();
        for (java.time.Month m : java.time.Month.values()) {
            months.add(m.toString());
        }
        monthCombo.setItems(months);
    }

    private void setupUserCombo() {
        userCombo.setItems(FXCollections.observableArrayList(userService.getAllUsers()));
        userCombo.setConverter(new StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getFullName() + " (" + user.getEmail() + ")" : "";
            }

            @Override
            public User fromString(String string) {
                return null;
            }
        });
        
        userCombo.setOnAction(event -> {
            User selected = userCombo.getValue();
            if (selected != null) {
                // Auto-fill base salary from user profile
                double salary = selected.getMonthlySalary() != null ? selected.getMonthlySalary() : 0.0;
                baseSalaryField.setText(String.valueOf(salary));
            }
        });
    }

    private void loadPayrolls() {
        payrollList.clear();
        if (currentUser != null && (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.HR)) {
            payrollList.addAll(payrollService.getAllPayrolls());
        } else if (currentUser != null) {
            payrollList.addAll(payrollService.getMyPayrolls(currentUser.getId()));
        }
        payrollTable.setItems(payrollList);
    }

    @FXML
    private void generatePayroll() {
        User selectedUser = userCombo.getValue();
        String month = monthCombo.getValue();
        String yearStr = yearField.getText();
        String baseStr = baseSalaryField.getText();
        String bonusStr = bonusField.getText();

        if (selectedUser == null || month == null || yearStr.isEmpty() || baseStr.isEmpty()) {
            showAlert("Error", "Please fill all required fields.");
            return;
        }

        try {
            int year = Integer.parseInt(yearStr);
            double base = Double.parseDouble(baseStr);
            double bonus = bonusStr.isEmpty() ? 0.0 : Double.parseDouble(bonusStr);

            Payroll p = payrollService.generatePayroll(selectedUser, month, year, base, bonus);
            
            showAlert("Success", "Payroll generated! Net Salary: " + p.getNetSalary());
            loadPayrolls();
            
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid number format.");
        }
    }
    
    private void showEditPayrollDialog(Payroll payroll) {
        if (payroll == null) return;

        Dialog<Payroll> dialog = new Dialog<>();
        dialog.setTitle("Edit Payroll");
        dialog.setHeaderText("Edit Salary for " + payroll.getUser().getFullName() + " (" + payroll.getMonth() + ")");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField baseField = new TextField(String.valueOf(payroll.getBaseSalary()));
        TextField bonusField = new TextField(String.valueOf(payroll.getBonus()));
        TextField deductionField = new TextField(String.valueOf(payroll.getDeductions()));

        grid.add(new Label("Base Salary:"), 0, 0);
        grid.add(baseField, 1, 0);
        grid.add(new Label("Bonus:"), 0, 1);
        grid.add(bonusField, 1, 1);
        grid.add(new Label("Deductions:"), 0, 2);
        grid.add(deductionField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    payroll.setBaseSalary(Double.parseDouble(baseField.getText()));
                    payroll.setBonus(Double.parseDouble(bonusField.getText()));
                    payroll.setDeductions(Double.parseDouble(deductionField.getText()));
                    return payroll;
                } catch (NumberFormatException e) {
                    showAlert("Error", "Invalid number format");
                    return null;
                }
            }
            return null;
        });

        Optional<Payroll> result = dialog.showAndWait();

        result.ifPresent(updatedPayroll -> {
            payrollService.updatePayroll(updatedPayroll);
            loadPayrolls();
            showAlert("Success", "Payroll updated successfully.");
        });
    }
    
    private void deletePayroll(Payroll payroll) {
         if (payroll == null) return;
         
         Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
         alert.setTitle("Delete Record");
         alert.setHeaderText("Delete this payroll record?");
         alert.setContentText("This action cannot be undone.");
         
         Optional<ButtonType> result = alert.showAndWait();
         if (result.isPresent() && result.get() == ButtonType.OK) {
             payrollService.deletePayroll(payroll.getUser().getId(), payroll.getMonth(), payroll.getYear());
             loadPayrolls();
         }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }
}
