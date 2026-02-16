package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Callback;
import tn.esprit.synergygig.entities.Forum;
import tn.esprit.synergygig.services.ForumService;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

public class ForumsController {

    @FXML
    private ListView<Forum> forumsListView;

    @FXML
    private TextField searchField;

    private ForumService forumService;
    private List<Forum> allForums;

    public ForumsController() {
        forumService = new ForumService();
    }

    @FXML
    public void initialize() {
        loadForums();

        // Custom Cell Factory using FXML Card
        forumsListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Forum item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    try {
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/tn/esprit/synergygig/gui/ForumItem.fxml"));
                        Parent root = loader.load();
                        ForumItemController controller = loader.getController();
                        controller.setData(item);
                        setGraphic(root);
                    } catch (IOException e) {
                        e.printStackTrace();
                        setText("Error loading item");
                    }
                }
            }
        });

        // Handle single click to view details (card style interaction)
        forumsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1) { // Changed to single click
                Forum selectedForum = forumsListView.getSelectionModel().getSelectedItem();
                if (selectedForum != null) {
                    showForumDetails(selectedForum);
                }
            }
        });
    }

    private void loadForums() {
        try {
            allForums = forumService.getAllForums();
            forumsListView.getItems().setAll(allForums);
        } catch (SQLException e) {
            e.printStackTrace();
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Database Error");
            alert.setHeaderText("Could not load forums");
            alert.setContentText(
                    "Error: " + e.getMessage() + "\n\nPlease ensure you have run the database update script.");
            alert.showAndWait();
        }
    }

    @FXML
    private void onSearch() {
        String query = searchField.getText().toLowerCase().trim();
        if (allForums == null)
            return;

        if (query.isEmpty()) {
            forumsListView.getItems().setAll(allForums);
        } else {
            List<Forum> filtered = allForums.stream()
                    .filter(f -> f.getTitle().toLowerCase().contains(query) ||
                            f.getContent().toLowerCase().contains(query))
                    .collect(Collectors.toList());
            forumsListView.getItems().setAll(filtered);
        }
    }

    @FXML
    private void showAddForum() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/tn/esprit/synergygig/gui/AddForumView.fxml"));
            Parent root = loader.load();
            navigate(root);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showForumDetails(Forum forum) {
        javafx.application.Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource("/tn/esprit/synergygig/gui/ForumDetailView.fxml"));
                Parent root = loader.load();

                ForumDetailController controller = loader.getController();
                controller.setForum(forum);

                navigate(root);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void navigate(Parent root) {
        // Find the parent BorderPane to navigate
        javafx.scene.Node node = forumsListView;
        while (node != null) {
            if (node instanceof javafx.scene.layout.BorderPane) {
                ((javafx.scene.layout.BorderPane) node).setCenter(root);
                return;
            }
            node = node.getParent();
        }

        // Fallback: Try to find by ID if class check failed (unlikely for MainLayout
        // but good for robustness)
        node = forumsListView;
        while (node != null) {
            if ("centerContainer".equals(node.getId()) && node instanceof javafx.scene.layout.BorderPane) {
                ((javafx.scene.layout.BorderPane) node).setCenter(root);
                return;
            }
            node = node.getParent();
        }
        System.err.println("Could not find MainLayout BorderPane for navigation.");
    }
}
