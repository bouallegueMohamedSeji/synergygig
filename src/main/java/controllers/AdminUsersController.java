package controllers;

import entities.User;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import services.ServiceUser;
import utils.SessionManager;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

public class AdminUsersController {

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> filterRoleCombo;
    @FXML
    private FlowPane usersFlowPane;
    @FXML
    private Label userCountLabel;

    private ServiceUser serviceUser = new ServiceUser();
    private List<User> allUsers;

    @FXML
    public void initialize() {
        filterRoleCombo.setItems(FXCollections.observableArrayList(
                "ALL", "ADMIN", "HR_MANAGER", "EMPLOYEE", "PROJECT_OWNER", "GIG_WORKER"));
        filterRoleCombo.setValue("ALL");

        refreshUsers();
    }

    @FXML
    public void refreshUsers() {
        try {
            allUsers = serviceUser.recuperer();
            applyFilters();
        } catch (SQLException e) {
            showAlert("Error", "Failed to load users: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        applyFilters();
    }

    @FXML
    private void handleFilter() {
        applyFilters();
    }

    private void applyFilters() {
        if (allUsers == null) return;

        String query = searchField.getText() == null ? "" : searchField.getText().toLowerCase().trim();
        String roleFilter = filterRoleCombo.getValue();

        List<User> filtered = allUsers.stream()
                .filter(user -> {
                    boolean matchesSearch = query.isEmpty()
                            || user.getFirstName().toLowerCase().contains(query)
                            || user.getLastName().toLowerCase().contains(query)
                            || user.getEmail().toLowerCase().contains(query);
                    boolean matchesRole = "ALL".equals(roleFilter) || user.getRole().equals(roleFilter);
                    return matchesSearch && matchesRole;
                })
                .collect(Collectors.toList());

        userCountLabel.setText(filtered.size() + " user" + (filtered.size() != 1 ? "s" : ""));
        buildUserCards(filtered);
    }

    private void buildUserCards(List<User> users) {
        usersFlowPane.getChildren().clear();

        for (User user : users) {
            VBox card = createUserCard(user);
            usersFlowPane.getChildren().add(card);
        }
    }

    private VBox createUserCard(User user) {
        VBox card = new VBox(0);
        card.getStyleClass().add("dashboard-card");
        card.setPrefWidth(300);
        card.setMinWidth(280);
        card.setMaxWidth(320);

        // -- Header section --
        VBox header = new VBox(2);
        header.getStyleClass().add("dashboard-card-header");

        String fullName = user.getFirstName() + " " + user.getLastName();
        Label nameLabel = new Label(fullName);
        nameLabel.getStyleClass().add("dashboard-card-title");

        Label emailLabel = new Label(user.getEmail());
        emailLabel.getStyleClass().add("dashboard-card-description");

        header.getChildren().addAll(nameLabel, emailLabel);

        // -- Content section --
        VBox content = new VBox(12);
        content.getStyleClass().add("dashboard-card-content");

        // Avatar + role row
        HBox avatarRow = new HBox(12);
        avatarRow.setAlignment(Pos.CENTER_LEFT);

        String initial = user.getFirstName().substring(0, 1).toUpperCase();
        Label avatarLabel = new Label(initial);
        avatarLabel.getStyleClass().add("avatar-initial");
        avatarLabel.setStyle("-fx-font-size: 20; -fx-font-weight: 700;");

        VBox avatarCircle = new VBox();
        avatarCircle.getStyleClass().add("avatar-circle");
        avatarCircle.setAlignment(Pos.CENTER);
        avatarCircle.setMinSize(48, 48);
        avatarCircle.setMaxSize(48, 48);
        avatarCircle.getChildren().add(avatarLabel);

        VBox infoCol = new VBox(4);
        Label roleBadge = new Label(user.getRole().replace("_", " "));
        roleBadge.getStyleClass().add("topbar-role-badge");

        String dateStr = "-";
        if (user.getCreatedAt() != null) {
            dateStr = new SimpleDateFormat("MMM dd, yyyy").format(user.getCreatedAt());
        }
        Label dateLabel = new Label("Joined " + dateStr);
        dateLabel.getStyleClass().add("stat-label");
        dateLabel.setStyle("-fx-font-size: 11;");

        infoCol.getChildren().addAll(roleBadge, dateLabel);
        avatarRow.getChildren().addAll(avatarCircle, infoCol);

        // Separator
        Separator sep = new Separator();
        sep.getStyleClass().add("card-separator");

        // ID row
        HBox idRow = new HBox(8);
        idRow.setAlignment(Pos.CENTER_LEFT);
        Label idIcon = new Label("🆔");
        idIcon.setStyle("-fx-font-size: 14;");
        Label idLabel = new Label("ID: " + user.getId());
        idLabel.getStyleClass().add("stat-label");
        Region idSpacer = new Region();
        HBox.setHgrow(idSpacer, Priority.ALWAYS);
        idRow.getChildren().addAll(idIcon, idLabel);

        // Actions section
        Separator sep2 = new Separator();
        sep2.getStyleClass().add("card-separator");

        // Role change combo + apply
        HBox roleRow = new HBox(8);
        roleRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList(
                "ADMIN", "HR_MANAGER", "EMPLOYEE", "PROJECT_OWNER", "GIG_WORKER"));
        roleCombo.setValue(user.getRole());
        roleCombo.getStyleClass().add("input-field");
        roleCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(roleCombo, Priority.ALWAYS);

        Button applyBtn = new Button("Apply");
        applyBtn.getStyleClass().add("btn-secondary");
        applyBtn.setStyle("-fx-padding: 6 14;");

        applyBtn.setOnAction(e -> {
            String newRole = roleCombo.getValue();
            if (newRole != null && !newRole.equals(user.getRole())) {
                try {
                    serviceUser.updateRole(user.getId(), newRole);
                    refreshUsers();
                } catch (SQLException ex) {
                    showAlert("Error", ex.getMessage());
                }
            }
        });

        roleRow.getChildren().addAll(roleCombo, applyBtn);

        // Delete button
        Button deleteBtn = new Button("Delete User");
        deleteBtn.getStyleClass().addAll("btn-primary");
        deleteBtn.setStyle("-fx-background-color: linear-gradient(to right, #dc2626, #b91c1c); -fx-padding: 8 0;");
        deleteBtn.setMaxWidth(Double.MAX_VALUE);

        deleteBtn.setOnAction(e -> {
            int currentUserId = SessionManager.getInstance().getCurrentUser().getId();
            if (user.getId() == currentUserId) {
                showAlert("Warning", "You cannot delete your own account.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete " + user.getFirstName() + " " + user.getLastName() + "?",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    try {
                        serviceUser.supprimer(user.getId());
                        refreshUsers();
                    } catch (SQLException ex) {
                        showAlert("Error", ex.getMessage());
                    }
                }
            });
        });

        content.getChildren().addAll(avatarRow, sep, idRow, sep2, roleRow, deleteBtn);

        card.getChildren().addAll(header, content);
        return card;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
