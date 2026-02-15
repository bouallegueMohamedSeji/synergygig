package tn.esprit.synergygig.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.services.UserService;

public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label errorLabel;

    private final UserService userService;

    public LoginController() {
        this.userService = new UserService();
    }

    private RootLayoutController rootLayoutController;

    public void setRootLayoutController(RootLayoutController rootLayoutController) {
        this.rootLayoutController = rootLayoutController;
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please fill all fields.");
            return;
        }

        if (!email.matches("^[\\w-\\.]+@([\\w-]+\\.)+[\\w-]{2,4}$")) {
            errorLabel.setText("Invalid email format.");
            return;
        }

        User user = userService.authenticate(email, password);
        if (user != null) {
            // Set User Session
            tn.esprit.synergygig.utils.UserSession.getInstance(user);

            // Navigate via RootLayout if available
            if (rootLayoutController != null) {
                rootLayoutController.showDashboard();
            } else {
                System.err.println("RootLayoutController is null!");
            }
        } else {
            errorLabel.setText("Invalid email or password.");
        }
    }

    @FXML
    private void handleSignupLink(ActionEvent event) {
        if (rootLayoutController != null) {
            rootLayoutController.showSignup();
        } else {
            System.err.println("RootLayoutController is null!");
        }
    }
}
