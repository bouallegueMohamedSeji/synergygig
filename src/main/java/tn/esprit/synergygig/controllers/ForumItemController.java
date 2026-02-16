package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import tn.esprit.synergygig.entities.Forum;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.services.UserService;

public class ForumItemController {

    private final UserService userService = new UserService();
    private final tn.esprit.synergygig.services.ForumService forumService = new tn.esprit.synergygig.services.ForumService();
    private Forum currentForum;

    @FXML
    private Label authorLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label titleLabel;

    @FXML
    private Label contentLabel;

    @FXML
    private Button likeButton;

    @FXML
    private Button deleteButton;

    public void setData(Forum forum) {
        this.currentForum = forum;
        User user = userService.getUserById(forum.getCreatedBy());
        if (user != null) {
            authorLabel.setText(user.getFullName());

            // Check ownership
            if (tn.esprit.synergygig.utils.UserSession.getInstance() != null &&
                    tn.esprit.synergygig.utils.UserSession.getInstance().getUser() != null &&
                    tn.esprit.synergygig.utils.UserSession.getInstance().getUser().getId() == forum.getCreatedBy()) {
                deleteButton.setVisible(true);
                deleteButton.setManaged(true);
            } else {
                deleteButton.setVisible(false);
                deleteButton.setManaged(false);
            }
        } else {
            authorLabel.setText("Unknown User"); // Changed from "User " + forum.getCreatedBy() to "Unknown User" as per
                                                 // snippet
            deleteButton.setVisible(false); // Hide delete button for unknown users
            deleteButton.setManaged(false);
        }
        dateLabel.setText(forum.getCreatedAt().toString());
        titleLabel.setText(forum.getTitle());
        contentLabel.setText(forum.getContent());

        updateLikeStatus();
    }

    private void updateLikeStatus() {
        if (tn.esprit.synergygig.utils.UserSession.getInstance() != null) {
            int userId = tn.esprit.synergygig.utils.UserSession.getInstance().getUser().getId();
            try {
                int likes = forumService.getLikesCount(currentForum.getId());
                boolean hasLiked = forumService.hasLiked(currentForum.getId(), userId);

                if (hasLiked) {
                    likeButton.setText("👍 Liked (" + likes + ")");
                    likeButton.setStyle(
                            "-fx-background-color: transparent; -fx-text-fill: #396afc; -fx-cursor: hand; -fx-font-weight: bold;");
                } else {
                    likeButton.setText("👍 Like (" + likes + ")");
                    likeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #888888; -fx-cursor: hand;");
                }
            } catch (java.sql.SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void toggleLike(javafx.scene.input.MouseEvent event) {
        if (event != null) {
            event.consume();
        }
        if (tn.esprit.synergygig.utils.UserSession.getInstance() == null ||
                tn.esprit.synergygig.utils.UserSession.getInstance().getUser() == null) {
            showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Login Required",
                    "You must be logged in to like posts.");
            return;
        }
        int userId = tn.esprit.synergygig.utils.UserSession.getInstance().getUser().getId();
        try {
            forumService.toggleLike(currentForum.getId(), userId);
            updateLikeStatus();
        } catch (java.sql.SQLException e) {
            e.printStackTrace();
            showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Database Error",
                    "Could not update like: " + e.getMessage());
        }
    }

    private void showAlert(javafx.scene.control.Alert.AlertType type, String title, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void deleteForum(javafx.scene.input.MouseEvent event) {
        if (event != null)
            event.consume();

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Confirmation");
        alert.setHeaderText("Delete this post?");
        alert.setContentText("Are you sure you want to delete this post? This cannot be undone.");

        if (alert.showAndWait().get() == javafx.scene.control.ButtonType.OK) {
            try {
                forumService.deleteForum(currentForum);
                // Force refresh of the list
                if (tn.esprit.synergygig.controllers.MainLayoutController.getInstance() != null) {
                    tn.esprit.synergygig.controllers.MainLayoutController.getInstance().navigate("ForumsView.fxml");
                }
            } catch (java.sql.SQLException e) {
                e.printStackTrace();
                showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Error",
                        "Could not delete post: " + e.getMessage());
            } catch (Exception e) {
                // Fallback if MainLayoutController is not accessible statically or other issues
                e.printStackTrace();
                showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Deleted",
                        "Post deleted. Please refresh the page.");
            }
        }
    }
}
