package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.entities.enums.Role;
import tn.esprit.synergygig.services.UserService;

public class SignupController {

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ComboBox<Role> roleComboBox;

    @FXML
    private Label errorLabel;

    private final UserService userService;

    public SignupController() {
        this.userService = new UserService();
    }

    private RootLayoutController rootLayoutController;

    public void setRootLayoutController(RootLayoutController rootLayoutController) {
        this.rootLayoutController = rootLayoutController;
    }

    @FXML
    public void initialize() {
        roleComboBox.setItems(FXCollections.observableArrayList(Role.values()));
    }

    @FXML
    private void handleSignup(ActionEvent event) {
        String fullName = fullNameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();
        Role role = roleComboBox.getValue();

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || role == null) {
            errorLabel.setText("Please fill all fields.");
            return;
        }

        if (fullName.length() < 3) {
            errorLabel.setText("Full name must be at least 3 characters.");
            return;
        }

        if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            errorLabel.setText("Invalid email format.");
            return;
        }

        if (password.length() < 6) {
            errorLabel.setText("Password must be at least 6 characters.");
            return;
        }

        User user = new User(fullName, email, password, role);
        if (userService.register(user)) {
            if (rootLayoutController != null) {
                rootLayoutController.showLogin();
            } else {
                System.err.println("RootLayoutController is null!");
            }
        } else {
            errorLabel.setText("Email already exists.");
        }
    }

    @FXML
    private void handleLoginLink(ActionEvent event) {
        if (rootLayoutController != null) {
            rootLayoutController.showLogin();
        } else {
            System.err.println("RootLayoutController is null!");
        }
    }
}
