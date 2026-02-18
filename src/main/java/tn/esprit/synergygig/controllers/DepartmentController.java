package tn.esprit.synergygig.controllers;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import tn.esprit.synergygig.entities.Department;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.services.DepartmentService;

import tn.esprit.synergygig.services.UserService;

import java.util.Optional;

public class DepartmentController {

    @FXML private TextField deptNameField;
    @FXML private TextField deptDescField;
    @FXML private TextField deptBudgetField;
    @FXML private ComboBox<User> managerCombo;
    @FXML private TableView<Department> departmentTable;
    @FXML private TableColumn<Department, Integer> colId;
    @FXML private TableColumn<Department, String> colName;
    @FXML private TableColumn<Department, String> colDesc;
    @FXML private TableColumn<Department, Double> colBudget;
    @FXML private TableColumn<Department, String> colManager;
    @FXML private TextField searchField;
    @FXML private Button sortButton;

    private DepartmentService departmentService;
    private UserService userService;
    private ObservableList<Department> departmentList;
    private javafx.collections.transformation.FilteredList<Department> filteredData;
    private javafx.collections.transformation.SortedList<Department> sortedData;
    private boolean sortAscending = true;

    public void initialize() {
        departmentService = new DepartmentService();
        userService = new UserService();
        departmentList = FXCollections.observableArrayList();

        setupTable();
        loadManagers();
        
        // Wrap the ObservableList in a FilteredList (initially display all data)
        filteredData = new javafx.collections.transformation.FilteredList<>(departmentList, p -> true);

        // Set the filter Predicate whenever the filter changes.
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(department -> {
                // If filter text is empty, display all departments.
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();

                if (department.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Filter matches name.
                } else if (department.getDescription() != null && department.getDescription().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Filter matches description.
                }
                return false; // Does not match.
            });
        });

        // Wrap the FilteredList in a SortedList. 
        sortedData = new javafx.collections.transformation.SortedList<>(filteredData);

        // Bind the SortedList comparator to the TableView comparator.
        sortedData.comparatorProperty().bind(departmentTable.comparatorProperty());

        // Add sorted (and filtered) data to the table.
        departmentTable.setItems(sortedData);
        loadDepartments();
    }

    private void setupTable() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colBudget.setCellValueFactory(new PropertyValueFactory<>("allocatedBudget"));
        
        // Format budget as currency
        colBudget.setCellFactory(tc -> new TableCell<Department, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(String.format("$%.2f", price));
                }
            }
        });
        
        colManager.setCellValueFactory(cellData -> {
            if (cellData.getValue().getManager() != null) {
                return new SimpleStringProperty(cellData.getValue().getManager().getFullName());
            } else {
                return new SimpleStringProperty("N/A");
            }
        });

        // Initialize Context Menu
        departmentTable.setRowFactory(tv -> {
            TableRow<Department> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem modifyBudgetItem = new MenuItem("Modify Budget");
            modifyBudgetItem.setOnAction(event -> modifyDepartmentBudget(row.getItem()));

            MenuItem assignItem = new MenuItem("Assign Employee");
            assignItem.setOnAction(event -> assignEmployeeToDepartment(row.getItem()));

            MenuItem assignManagerItem = new MenuItem("Assign Manager");
            assignManagerItem.setOnAction(event -> assignManagerToDepartment(row.getItem()));
            
            MenuItem removeItem = new MenuItem("Remove Employee");
            removeItem.setOnAction(event -> removeEmployeeFromDepartment(row.getItem()));

            MenuItem viewEmployeesItem = new MenuItem("View Employees");
            viewEmployeesItem.setOnAction(event -> viewDepartmentEmployees(row.getItem()));

            contextMenu.getItems().addAll(modifyBudgetItem, assignItem, assignManagerItem, removeItem, viewEmployeesItem);

            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );
            return row;
        });
    }

    private void assignEmployeeToDepartment(Department department) {
        if (department == null) return;

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Assign Employee");
        dialog.setHeaderText("Assign an employee to " + department.getName());

        ButtonType assignButtonType = new ButtonType("Assign", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);

        ComboBox<User> userComboBox = new ComboBox<>();
        userComboBox.getItems().setAll(userService.getAllUsers());
        // Simple String Converter for User
        userComboBox.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                 return user != null ? user.getFullName() + " (" + user.getEmail() + ")" : "";
            }
            @Override
            public User fromString(String string) { return null; }
        });

        VBox content = new VBox(10);
        content.getChildren().add(new Label("Select Employee:"));
        content.getChildren().add(userComboBox);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == assignButtonType) {
                return userComboBox.getValue();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(user -> {
            if (user != null) {
                boolean success = userService.assignUserToDepartment(user, department);
                if (success) {
                    showAlert("Success", "User " + user.getFullName() + " assigned to " + department.getName());
                } else {
                    showAlert("Error", "Failed to assign user.");
                }
            }
        });
    }

    private void viewDepartmentEmployees(Department department) {
        if (department == null) return;

        ObservableList<User> employees = FXCollections.observableArrayList(userService.getUsersByDepartment(department.getId()));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Department Employees");
        dialog.setHeaderText("Employees in " + department.getName());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ListView<String> listView = new ListView<>();
        for (User u : employees) {
            listView.getItems().add(u.getFullName() + " - " + u.getRole());
        }

        VBox content = new VBox(10);
        content.getChildren().add(listView);
        dialog.getDialogPane().setContent(content);
        
        dialog.show();
    }

    private void loadDepartments() {
        departmentList.setAll(departmentService.getAllDepartments());
        // Table items are bound to sortedData -> filteredData -> departmentList, so no need to set items again
    }
    
    private void loadManagers() {
        // Filter: Only ADMIN or HR can be managers
        java.util.List<User> eligibleManagers = userService.getAllUsers().stream()
                .filter(u -> u.getRole() == Role.ADMIN || u.getRole() == Role.HR)
                .collect(java.util.stream.Collectors.toList());

        managerCombo.getItems().setAll(eligibleManagers);
        managerCombo.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getFullName() : "";
            }
            @Override
            public User fromString(String string) {
                return managerCombo.getItems().stream()
                        .filter(user -> user.getFullName().equals(string))
                        .findFirst().orElse(null);
            }
        });
    }

    @FXML
    void addDepartment(javafx.event.ActionEvent event) {
        String name = deptNameField.getText();
        String description = deptDescField.getText();
        String budgetStr = deptBudgetField.getText();
        User manager = managerCombo.getValue();

        if (name == null || name.trim().isEmpty() || description == null || description.trim().isEmpty()) {
            showAlert("Error", "Name and Description cannot be empty.");
            return;
        }
        
        Double budget = 0.0;
        try {
            if (budgetStr != null && !budgetStr.isEmpty()) {
                budget = Double.parseDouble(budgetStr);
                if (budget < 0) {
                    showAlert("Error", "Budget cannot be negative.");
                    return;
                }
            }
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid budget format.");
            return;
        }

        // Check for duplicates if needed, or rely on DB constraints
        if (departmentService.existsByName(name)) {
             showAlert("Error", "Department with this name already exists.");
             return;
        }

        Department department = new Department();
        department.setName(name);
        department.setDescription(description);
        department.setAllocatedBudget(budget);
        department.setManager(manager);

        departmentService.addDepartment(department);
        loadDepartments();
        clearFields();
    }

    @FXML
    void deleteDepartment(javafx.event.ActionEvent event) {
        Department selectedDepartment = departmentTable.getSelectionModel().getSelectedItem();
        if (selectedDepartment != null) {
            departmentService.deleteDepartment(selectedDepartment);
            loadDepartments();
            clearFields();
        } else {
            showAlert("Error", "No department selected.");
        }
    }

    @FXML
    void toggleSort(javafx.event.ActionEvent event) {
        if (sortAscending) {
            departmentTable.getSortOrder().add(colName);
            colName.setSortType(TableColumn.SortType.ASCENDING);
            sortButton.setText("Sort Name (Z-A)");
        } else {
            departmentTable.getSortOrder().add(colName);
            colName.setSortType(TableColumn.SortType.DESCENDING);
            sortButton.setText("Sort Name (A-Z)");
        }
        departmentTable.sort();
        sortAscending = !sortAscending;
    }

    private void assignManagerToDepartment(Department department) {
        if (department == null) return;

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Assign Manager");
        dialog.setHeaderText("Assign a Manager to " + department.getName());

        ButtonType assignButtonType = new ButtonType("Assign", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(assignButtonType, ButtonType.CANCEL);

        ComboBox<User> userComboBox = new ComboBox<>();
        userComboBox.getItems().setAll(userService.getAllUsers());
        
        userComboBox.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getFullName() : "";
            }
            @Override
            public User fromString(String string) { return null; }
        });

        VBox content = new VBox(10);
        content.getChildren().add(new Label("Select Manager:"));
        content.getChildren().add(userComboBox);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == assignButtonType) {
                return userComboBox.getValue();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(manager -> {
            if (manager != null) {
                department.setManager(manager);
                departmentService.updateDepartment(department);
                loadDepartments();
                showAlert("Success", "Manager assigned successfully.");
            }
        });
    }

    private void removeEmployeeFromDepartment(Department department) {
        if (department == null) return;

        ObservableList<User> deptEmployees = FXCollections.observableArrayList(userService.getUsersByDepartment(department.getId()));

        if (deptEmployees.isEmpty()) {
             showAlert("Info", "No employees in this department to remove.");
             return;
        }

        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("Remove Employee");
        dialog.setHeaderText("Remove an employee from " + department.getName());

        ButtonType removeButtonType = new ButtonType("Remove", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(removeButtonType, ButtonType.CANCEL);

        ComboBox<User> userComboBox = new ComboBox<>();
        userComboBox.getItems().setAll(deptEmployees);
        
        userComboBox.setConverter(new javafx.util.StringConverter<User>() {
            @Override
            public String toString(User user) {
                return user != null ? user.getFullName() : "";
            }
            @Override
            public User fromString(String string) { return null; }
        });

        VBox content = new VBox(10);
        content.getChildren().add(new Label("Select Employee to Remove:"));
        content.getChildren().add(userComboBox);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == removeButtonType) {
                return userComboBox.getValue();
            }
            return null;
        });

        dialog.showAndWait().ifPresent(userToRemove -> {
            if (userToRemove != null) {
                boolean success = userService.removeUserFromDepartment(userToRemove.getId());
                if (success) {
                    showAlert("Success", "User " + userToRemove.getFullName() + " removed from department.");
                } else {
                    showAlert("Error", "Failed to remove user from department.");
                }
            }
        });
    }

    private void clearFields() {
        deptNameField.clear();
        deptDescField.clear();
        deptBudgetField.clear();
        managerCombo.getSelectionModel().clearSelection();
    }

    private void modifyDepartmentBudget(Department department) {
        if (department == null) return;

        TextInputDialog dialog = new TextInputDialog(String.valueOf(department.getAllocatedBudget()));
        dialog.setTitle("Modify Budget");
        dialog.setHeaderText("Update Budget for " + department.getName());
        dialog.setContentText("Enter new budget:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(budgetStr -> {
            try {
                Double newBudget = Double.parseDouble(budgetStr);
                if (newBudget < 0) {
                    showAlert("Error", "Budget cannot be negative.");
                } else {
                    department.setAllocatedBudget(newBudget);
                    departmentService.updateDepartment(department);
                    loadDepartments(); // Refresh table
                    showAlert("Success", "Budget updated successfully.");
                }
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid budget format. Please enter a valid number.");
            }
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.show();
    }
}
