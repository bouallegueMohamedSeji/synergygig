package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import tn.esprit.synergygig.entities.Forum;
import tn.esprit.synergygig.services.ForumService;
import tn.esprit.synergygig.utils.UserSession;

import java.io.IOException;
import java.sql.SQLException;

public class AddForumController {

    @FXML
    private TextField titleField;

    @FXML
    private TextArea contentArea;

    private ForumService forumService;

    public AddForumController() {
        forumService = new ForumService();
    }

    @FXML
    private void saveForum() {
        String title = titleField.getText();
        String content = contentArea.getText();

        if (title.isEmpty() || content.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please fill in all fields.");
            return;
        }

        if (UserSession.getInstance() == null || UserSession.getInstance().getUser() == null) {
            showAlert(Alert.AlertType.ERROR, "Authentication Error", "You must be logged in to post.");
            return;
        }

        try {
            Forum forum = new Forum(title, content, UserSession.getInstance().getUser().getId());
            forumService.addForum(forum);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Topic created successfully!");
            goBack();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not save topic: " + e.getMessage());
        }
    }

    @FXML
    private void cancel() {
        goBack();
    }

    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tn/esprit/synergygig/gui/ForumsView.fxml"));
            Parent root = loader.load();
            if (titleField.getScene().getRoot() instanceof javafx.scene.layout.BorderPane) {
                // Check if we can find the center container... similar hack to ForumsController
            }
            if (titleField.getParent().getParent().getParent() instanceof javafx.scene.layout.BorderPane) {
                // StackPane -> VBox -> StackPane(Root of this view?) -> BorderPane?
                // Let's iterate parents until we find BorderPane
                javafx.scene.Node node = titleField;
                while (node != null) {
                    if (node instanceof javafx.scene.layout.BorderPane) {
                        ((javafx.scene.layout.BorderPane) node).setCenter(root);
                        return;
                    }
                    node = node.getParent();
                }
            }

            // If we are here, we might need another check or just replace scene root?
            // Replacing scene root is safer if we can't find BorderPane but risk losing
            // Sidebar.
            // Let's look for "centerContainer" by ID.
            javafx.scene.Node node = titleField;
            while (node != null) {
                if (node.getId() != null && node.getId().equals("centerContainer")) {
                    if (node instanceof javafx.scene.layout.BorderPane) {
                        ((javafx.scene.layout.BorderPane) node).setCenter(root);
                        return;
                    }
                }
                node = node.getParent();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
