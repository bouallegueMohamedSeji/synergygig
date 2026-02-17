package controllers;

import entities.User;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import services.ServiceUser;
import utils.SessionManager;

import java.sql.SQLException;
import java.text.SimpleDateFormat;

public class ProfileController {

    // Profile identity card
    @FXML
    private Label profileAvatarInitial;
    @FXML
    private Label profileFullName;
    @FXML
    private Label profileEmail;
    @FXML
    private Label profileRoleBadge;

    // Account details card
    @FXML
    private Label detailFirstName;
    @FXML
    private Label detailLastName;
    @FXML
    private Label detailEmail;
    @FXML
    private Label detailRole;
    @FXML
    private Label detailCreatedAt;

    // Edit form fields
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
            populateProfile(currentUser);
        }
    }

    private void populateProfile(User user) {
        // Profile identity card
        String initial = user.getFirstName().substring(0, 1).toUpperCase();
        profileAvatarInitial.setText(initial);
        profileFullName.setText(user.getFirstName() + " " + user.getLastName());
        profileEmail.setText(user.getEmail());
        profileRoleBadge.setText(user.getRole().replace("_", " "));

        // Account details card
        detailFirstName.setText(user.getFirstName());
        detailLastName.setText(user.getLastName());
        detailEmail.setText(user.getEmail());
        detailRole.setText(user.getRole().replace("_", " "));
        if (user.getCreatedAt() != null) {
            detailCreatedAt.setText(new SimpleDateFormat("MMMM dd, yyyy").format(user.getCreatedAt()));
        } else {
            detailCreatedAt.setText("N/A");
        }

        // Pre-fill edit form
        firstNameField.setText(user.getFirstName());
        lastNameField.setText(user.getLastName());
        emailField.setText(user.getEmail());
        roleField.setText(user.getRole().replace("_", " "));
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

            // Refresh the display cards with new data
            populateProfile(currentUser);

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
