package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import tn.esprit.synergygig.entities.ForumComment;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.services.ForumCommentService;
import tn.esprit.synergygig.services.UserService;
import tn.esprit.synergygig.utils.UserSession;

import java.sql.SQLException;

public class CommentItemController {

    @FXML
    private Label authorLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label contentLabel;

    @FXML
    private HBox actionBox;

    @FXML
    private VBox editContainer;

    @FXML
    private TextArea editArea;

    private ForumComment currentComment;
    private Runnable onUpdateCallback;
    private final UserService userService = new UserService();
    private final ForumCommentService commentService = new ForumCommentService();

    public void setData(ForumComment comment, Runnable onUpdateCallback) {
        this.currentComment = comment;
        this.onUpdateCallback = onUpdateCallback;

        User user = userService.getUserById(comment.getCreatedBy());
        if (user != null) {
            authorLabel.setText(user.getFullName());
        } else {
            authorLabel.setText("User " + comment.getCreatedBy());
        }

        dateLabel.setText(comment.getCreatedAt().toString());
        contentLabel.setText(comment.getContent());

        // Check permission
        if (UserSession.getInstance() != null && UserSession.getInstance().getUser() != null &&
                UserSession.getInstance().getUser().getId() == comment.getCreatedBy()) {
            actionBox.setVisible(true);
        } else {
            actionBox.setVisible(false);
        }
    }

    // Fallback for compatibility if needed, though we will update caller
    public void setData(ForumComment comment) {
        setData(comment, null);
    }

    @FXML
    private void onEdit() {
        editArea.setText(currentComment.getContent());
        contentLabel.setVisible(false);
        contentLabel.setManaged(false);
        editContainer.setVisible(true);
        editContainer.setManaged(true);
    }

    @FXML
    private void onDelete() {
        try {
            commentService.deleteComment(currentComment);
            if (onUpdateCallback != null)
                onUpdateCallback.run();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onSaveEdit() {
        String newContent = editArea.getText();
        if (newContent.isEmpty())
            return;

        currentComment.setContent(newContent);
        try {
            commentService.updateComment(currentComment);
            contentLabel.setText(newContent);
            onCancelEdit(); // Hide form
            // Optional: callback if needed, but we updated UI locally
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onCancelEdit() {
        editContainer.setVisible(false);
        editContainer.setManaged(false);
        contentLabel.setVisible(true);
        contentLabel.setManaged(true);
    }
}
