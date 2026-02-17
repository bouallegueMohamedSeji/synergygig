package controllers;

import entities.User;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import services.ServiceUser;
import utils.SessionManager;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;

public class AdminUsersController {

    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> filterRoleCombo;
    @FXML
    private TableView<User> usersTable;
    @FXML
    private TableColumn<User, Integer> colId;
    @FXML
    private TableColumn<User, String> colFirstName;
    @FXML
    private TableColumn<User, String> colLastName;
    @FXML
    private TableColumn<User, String> colEmail;
    @FXML
    private TableColumn<User, String> colRole;
    @FXML
    private TableColumn<User, String> colCreatedAt;
    @FXML
    private TableColumn<User, Void> colActions;

    private ServiceUser serviceUser = new ServiceUser();
    private ObservableList<User> userList = FXCollections.observableArrayList();
    private FilteredList<User> filteredList;

    @FXML
    public void initialize() {
        // Setup role filter
        filterRoleCombo.setItems(FXCollections.observableArrayList(
                "ALL", "ADMIN", "HR_MANAGER", "EMPLOYEE", "PROJECT_OWNER", "GIG_WORKER"));
        filterRoleCombo.setValue("ALL");

        // Setup table columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFirstName.setCellValueFactory(new PropertyValueFactory<>("firstName"));
        colLastName.setCellValueFactory(new PropertyValueFactory<>("lastName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));

        colCreatedAt.setCellValueFactory(cellData -> {
            Timestamp ts = cellData.getValue().getCreatedAt();
            String formatted = ts != null
                    ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(ts)
                    : "-";
            return new SimpleStringProperty(formatted);
        });

        // Setup actions column
        setupActionsColumn();

        // Load data
        refreshTable();
    }

    private void setupActionsColumn() {
        colActions.setCellFactory(param -> new TableCell<>() {
            private final ComboBox<String> roleCombo = new ComboBox<>(
                    FXCollections.observableArrayList("EMPLOYEE", "HR_MANAGER", "PROJECT_OWNER", "GIG_WORKER",
                            "ADMIN"));
            private final Button applyBtn = new Button("Apply");
            private final Button deleteBtn = new Button("Delete");
            private final HBox hbox = new HBox(6, roleCombo, applyBtn, deleteBtn);

            {
                applyBtn.getStyleClass().add("btn-small");
                deleteBtn.getStyleClass().addAll("btn-small", "btn-danger");
                roleCombo.getStyleClass().add("combo-small");

                applyBtn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
                    String newRole = roleCombo.getValue();
                    if (newRole != null) {
                        try {
                            serviceUser.updateRole(user.getId(), newRole);
                            refreshTable();
                        } catch (SQLException ex) {
                            showAlert("Error", ex.getMessage());
                        }
                    }
                });

                deleteBtn.setOnAction(e -> {
                    User user = getTableView().getItems().get(getIndex());
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
                                refreshTable();
                            } catch (SQLException ex) {
                                showAlert("Error", ex.getMessage());
                            }
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    User user = getTableView().getItems().get(getIndex());
                    roleCombo.setValue(user.getRole());
                    setGraphic(hbox);
                }
            }
        });
    }

    @FXML
    public void refreshTable() {
        try {
            List<User> users = serviceUser.recuperer();
            userList.setAll(users);
            filteredList = new FilteredList<>(userList, p -> true);
            usersTable.setItems(filteredList);
        } catch (SQLException e) {
            showAlert("Error", "Failed to load users: " + e.getMessage());
        }
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().toLowerCase().trim();
        String roleFilter = filterRoleCombo.getValue();

        filteredList.setPredicate(user -> {
            boolean matchesSearch = query.isEmpty()
                    || user.getFirstName().toLowerCase().contains(query)
                    || user.getLastName().toLowerCase().contains(query)
                    || user.getEmail().toLowerCase().contains(query);

            boolean matchesRole = "ALL".equals(roleFilter) || user.getRole().equals(roleFilter);

            return matchesSearch && matchesRole;
        });
    }

    @FXML
    private void handleFilter() {
        handleSearch(); // Reapply both search + filter
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
