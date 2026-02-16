package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.util.Callback;
import tn.esprit.synergygig.entities.Forum;
import tn.esprit.synergygig.entities.ForumComment;
import tn.esprit.synergygig.services.ForumCommentService;
import tn.esprit.synergygig.utils.UserSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class ForumDetailController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label userDateLabel;
    @FXML
    private Label contentLabel;

    @FXML
    private ListView<ForumComment> commentsListView;
    @FXML
    private TextField commentField;

    private Forum currentForum;
    private ForumCommentService commentService;

    public ForumDetailController() {
        commentService = new ForumCommentService();
    }

    public void setForum(Forum forum) {
        this.currentForum = forum;
        titleLabel.setText(forum.getTitle());
        contentLabel.setText(forum.getContent());
        userDateLabel.setText("Posted on " + forum.getCreatedAt());
        // Ideally we fetch User name using UserService or from Forum entity if likely
        // joined.

        loadComments();
    }

    @FXML
    public void initialize() {
        // Custom Cell Factory for Comments
        // Custom Cell Factory for Comments using FXML
        commentsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ForumComment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    try {
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/tn/esprit/synergygig/gui/CommentItem.fxml"));
                        Parent root = loader.load();
                        CommentItemController controller = loader.getController();
                        controller.setData(item, () -> loadComments());
                        setGraphic(root);
                    } catch (IOException e) {
                        e.printStackTrace();
                        setText("Error loading comment");
                    }
                }
            }
        });
    }

    private void loadComments() {
        if (currentForum == null)
            return;
        try {
            List<ForumComment> comments = commentService.getCommentsByForumId(currentForum.getId());
            commentsListView.getItems().setAll(comments);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void addComment() {
        String content = commentField.getText();
        if (content.isEmpty())
            return;

        if (UserSession.getInstance() == null || UserSession.getInstance().getUser() == null) {
            showAlert(Alert.AlertType.ERROR, "Authentication Error", "You must be logged in to comment.");
            return;
        }

        try {
            ForumComment comment = new ForumComment(currentForum.getId(), content,
                    UserSession.getInstance().getUser().getId());
            commentService.addComment(comment);
            commentField.clear();
            loadComments();
            commentsListView.refresh();
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not add comment: " + e.getMessage());
        }
    }

    @FXML
    private void goBack() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tn/esprit/synergygig/gui/ForumsView.fxml"));
            Parent root = loader.load();

            // Find center container and set it
            javafx.scene.Node node = titleLabel;
            while (node != null) {
                if (node instanceof javafx.scene.layout.BorderPane) {
                    ((javafx.scene.layout.BorderPane) node).setCenter(root);
                    return;
                }
                node = node.getParent();
            }

            // Fallback for ID "centerContainer"
            node = titleLabel;
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
