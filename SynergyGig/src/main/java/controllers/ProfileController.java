package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import services.ServiceUser;
import utils.SessionManager;

import java.sql.SQLException;

public class ProfileController {

    @FXML
    private TextField firstNameField;
    @FXML
    private TextField lastNameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField roleField;
    @FXML
    private Label statusLabel;

    private ServiceUser serviceUser = new ServiceUser();

    @FXML
    public void initialize() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            firstNameField.setText(currentUser.getFirstName());
            lastNameField.setText(currentUser.getLastName());
            emailField.setText(currentUser.getEmail());
            roleField.setText(currentUser.getRole().replace("_", " "));
        }
    }

    @FXML
    private void handleSave() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null)
            return;

        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty()) {
            showStatus("Please fill in all required fields.", true);
            return;
        }

        try {
            // Check if email changed and already exists
            if (!email.equals(currentUser.getEmail()) && serviceUser.emailExists(email)) {
                showStatus("This email is already in use.", true);
                return;
            }

            currentUser.setFirstName(firstName);
            currentUser.setLastName(lastName);
            currentUser.setEmail(email);
            if (!password.isEmpty()) {
                currentUser.setPassword(password);
            }

            serviceUser.modifier(currentUser);

            // Update session
            SessionManager.getInstance().setCurrentUser(currentUser);

            showStatus("Profile updated successfully!", false);

        } catch (SQLException e) {
            showStatus("Error: " + e.getMessage(), true);
        }
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setManaged(true);
        statusLabel.setVisible(true);
        statusLabel.getStyleClass().removeAll("error-label", "success-label");
        statusLabel.getStyleClass().add(isError ? "error-label" : "success-label");
    }
}
