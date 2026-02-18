package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.Department;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.services.UserService;
import tn.esprit.synergygig.services.DepartmentService;

import java.util.Optional;

public class UserListController {

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colDepartment;
    @FXML private TableColumn<User, String> colGigs;
    @FXML private TextField searchField;
    @FXML private Button sortButton;

    private UserService userService;
    private DepartmentService departmentService;
    private ObservableList<User> userList;
    private javafx.collections.transformation.FilteredList<User> filteredData;
    private javafx.collections.transformation.SortedList<User> sortedData;
    private boolean sortAscending = true;

    public void initialize() {
        userService = new UserService();
        departmentService = new DepartmentService();
        userList = FXCollections.observableArrayList();

        setupTable();
        
        // Wrap the ObservableList in a FilteredList (initially display all data)
        filteredData = new javafx.collections.transformation.FilteredList<>(userList, p -> true);

        // Set the filter Predicate whenever the filter changes.
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(user -> {
                // If filter text is empty, display all users.
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();

                if (user.getFullName().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Match Name
                } else if (user.getEmail().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Match Email
                } else if (user.getRole().toString().toLowerCase().contains(lowerCaseFilter)) {
                    return true; // Match Role
                }
                return false; // Does not match.
            });
        });

        // Wrap the FilteredList in a SortedList. 
        sortedData = new javafx.collections.transformation.SortedList<>(filteredData);

        // Bind the SortedList comparator to the TableView comparator.
        sortedData.comparatorProperty().bind(userTable.comparatorProperty());

        // Add sorted (and filtered) data to the table.
        userTable.setItems(sortedData);
        
        loadUsers();
    }

    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        
        colDepartment.setCellValueFactory(cellData -> {
            if (cellData.getValue().getDepartment() != null) {
                return new javafx.beans.property.SimpleStringProperty(cellData.getValue().getDepartment().getName());
            } else {
                return new javafx.beans.property.SimpleStringProperty("-");
            }
        });
        
        colGigs.setCellValueFactory(new PropertyValueFactory<>("activeGigsSummary"));

        // Context Menu
        userTable.setRowFactory(tv -> {
            TableRow<User> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();
            
            MenuItem addItem = new MenuItem("Add Employee");
            addItem.setOnAction(event -> showAddUserDialog());
            
            MenuItem editItem = new MenuItem("Edit Employee");
            editItem.setOnAction(event -> showEditUserDialog(row.getItem()));
            
            MenuItem deleteItem = new MenuItem("Delete Employee");
            deleteItem.setOnAction(event -> deleteUser(row.getItem()));
            
            contextMenu.getItems().addAll(addItem, editItem, deleteItem);
            
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                .then((ContextMenu) null)
                .otherwise(contextMenu)
            );
            return row;
        });
    }

    private void loadUsers() {
        userList.setAll(userService.getAllUsers());
        // Items are already bound to sortedData -> filteredData -> userList
    }
    
    @FXML
    public void showAddUserDialog() {
        javafx.scene.control.Dialog<User> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Add New Employee");
        dialog.setHeaderText("Enter employee details");

        // Set the button types.
        ButtonType loginButtonType = new ButtonType("Add", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the username and password labels and fields.
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField nameField = new TextField();
        nameField.setPromptText("Full Name");
        TextField emailField = new TextField();
        emailField.setPromptText("Email");
        javafx.scene.control.PasswordField passwordField = new javafx.scene.control.PasswordField();
        passwordField.setPromptText("Password");
        
        javafx.scene.control.TextField hourlyRateField = new javafx.scene.control.TextField();
        hourlyRateField.setPromptText("Hourly Rate (e.g., 20.0)");
        
        javafx.scene.control.ComboBox<Role> roleCombo = new javafx.scene.control.ComboBox<>(FXCollections.observableArrayList(Role.values()));
        roleCombo.setPromptText("Select Role");
        
        javafx.scene.control.ComboBox<Department> deptCombo = new javafx.scene.control.ComboBox<>();
        deptCombo.setPromptText("Select Department (Optional)");
        
        // Load departments
        java.util.List<Department> depts = departmentService.getAllDepartments();
        deptCombo.getItems().setAll(depts);
        
        // Converter for Department ComboBox to show names
        deptCombo.setConverter(new javafx.util.StringConverter<Department>() {
            @Override
            public String toString(Department object) {
                return object != null ? object.getName() : "";
            }

            @Override
            public Department fromString(String string) {
                return deptCombo.getItems().stream().filter(d -> d.getName().equals(string)).findFirst().orElse(null);
            }
        });

        grid.add(new javafx.scene.control.Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new javafx.scene.control.Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new javafx.scene.control.Label("Password:"), 0, 2);
        grid.add(passwordField, 1, 2);
        grid.add(new javafx.scene.control.Label("Hourly Rate:"), 0, 3);
        grid.add(hourlyRateField, 1, 3);
        grid.add(new javafx.scene.control.Label("Role:"), 0, 4);
        grid.add(roleCombo, 1, 4);
        grid.add(new javafx.scene.control.Label("Department:"), 0, 5);
        grid.add(deptCombo, 1, 5);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the name field by default.
        javafx.application.Platform.runLater(() -> nameField.requestFocus());

        // Convert the result to a user-password-pair when the login button is clicked.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                String name = nameField.getText();
                String email = emailField.getText();
                String password = passwordField.getText();
                String rateStr = hourlyRateField.getText();
                Role role = roleCombo.getValue();
                Department dept = deptCombo.getValue();
                
                if (name.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
                    return null; 
                }
                
                Double hourlyRate = 0.0;
                try {
                    if (!rateStr.isEmpty()) {
                        hourlyRate = Double.parseDouble(rateStr);
                    }
                } catch (NumberFormatException e) {
                    // Ignore or handle
                }
                
                // Default monthly salary to 0.0 or calculate based on hourly rate * 160
                Double monthlySalary = 0.0;
                
                User newUser = new User(name, email, password, role, hourlyRate, monthlySalary);
                if (dept != null) {
                    newUser.setDepartment(dept);
                }
                return newUser;
            }
            return null;
        });

        Optional<User> result = dialog.showAndWait();

        result.ifPresent(newUser -> {
            if (userService.register(newUser)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Success");
                alert.setHeaderText(null);
                alert.setContentText("User added successfully!");
                alert.showAndWait();
                loadUsers();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText("Failed to add user. Email might already exist.");
                alert.showAndWait();
            }
        });
    }
    
    private void showEditUserDialog(User user) {
        if (user == null) return;

        javafx.scene.control.Dialog<User> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Edit details for: " + user.getFullName());

        ButtonType saveButtonType = new ButtonType("Save", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        javafx.scene.control.TextField nameField = new javafx.scene.control.TextField(user.getFullName());
        javafx.scene.control.TextField emailField = new javafx.scene.control.TextField(user.getEmail());
        javafx.scene.control.TextField hourlyRateField = new javafx.scene.control.TextField(String.valueOf(user.getHourlyRate() != null ? user.getHourlyRate() : 0.0));

        javafx.scene.control.ComboBox<Role> roleCombo = new javafx.scene.control.ComboBox<>(FXCollections.observableArrayList(Role.values()));
        roleCombo.setValue(user.getRole());

        grid.add(new javafx.scene.control.Label("Full Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new javafx.scene.control.Label("Email:"), 0, 1);
        grid.add(emailField, 1, 1);
        grid.add(new javafx.scene.control.Label("Hourly Rate:"), 0, 2);
        grid.add(hourlyRateField, 1, 2);
        grid.add(new javafx.scene.control.Label("Role:"), 0, 3);
        grid.add(roleCombo, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                user.setFullName(nameField.getText());
                user.setEmail(emailField.getText());
                user.setRole(roleCombo.getValue());
                try {
                    user.setHourlyRate(Double.parseDouble(hourlyRateField.getText()));
                } catch (NumberFormatException e) {
                    // keep old value or set 0
                }
                return user;
            }
            return null;
        });

        Optional<User> result = dialog.showAndWait();

        result.ifPresent(updatedUser -> {
            userService.update(updatedUser);
            loadUsers(); // Refresh table
        });
    }
    
    private void deleteUser(User user) {
        if (user != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Delete User");
            alert.setHeaderText("Delete " + user.getFullName() + "?");
            alert.setContentText("Are you sure you want to delete this user?");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                userService.delete(user.getId());
                loadUsers();
            }
        }
    }
    @FXML
    void toggleSort(javafx.event.ActionEvent event) {
        if (sortAscending) {
            userTable.getSortOrder().add(colName);
            colName.setSortType(TableColumn.SortType.ASCENDING);
            sortButton.setText("Sort Name (Z-A)");
        } else {
            userTable.getSortOrder().add(colName);
            colName.setSortType(TableColumn.SortType.DESCENDING);
            sortButton.setText("Sort Name (A-Z)");
        }
        userTable.sort();
        sortAscending = !sortAscending;
    }
}
