package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import tn.esprit.synergygig.dao.UserDAO;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.utils.UserSession;

import java.sql.SQLException;

public class ProfileController {

    @FXML
    private TextField fullNameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label roleLabel;

    @FXML
    private Label messageLabel;

    private User currentUser;
    private final UserDAO userDAO;

    public ProfileController() {
        this.userDAO = new UserDAO();
    }

    @FXML
    public void initialize() {
        if (UserSession.getInstance() != null) {
            currentUser = UserSession.getInstance().getUser();
            fullNameField.setText(currentUser.getFullName());
            emailField.setText(currentUser.getEmail());
            passwordField.setText(currentUser.getPassword());
            roleLabel.setText("Role: " + currentUser.getRole());

            // Email should not be editable as it is the unique identifier in this
            // simplistic version
            emailField.setEditable(false);
        }
    }

    @FXML
    private void handleSave() {
        String newFullName = fullNameField.getText();
        String newPassword = passwordField.getText();

        if (newFullName.isEmpty() || newPassword.isEmpty()) {
            messageLabel.setText("Fields cannot be empty.");
            messageLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        currentUser.setFullName(newFullName);
        currentUser.setPassword(newPassword);

        try {
            userDAO.updateOne(currentUser);
            UserSession.getInstance().setUser(currentUser); // Update session
            messageLabel.setText("Profile updated successfully!");
            messageLabel.setStyle("-fx-text-fill: green;");
        } catch (SQLException e) {
            e.printStackTrace();
            messageLabel.setText("Error updating profile: " + e.getMessage());
            messageLabel.setStyle("-fx-text-fill: red;");
        }
    }
}
