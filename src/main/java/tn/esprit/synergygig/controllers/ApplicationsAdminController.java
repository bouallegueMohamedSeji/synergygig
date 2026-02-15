package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import tn.esprit.synergygig.entities.Application;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import tn.esprit.synergygig.services.ApplicationService;

public class ApplicationsAdminController {

    @FXML private TableView<Application> applicationTable;
    @FXML private TableColumn<Application, String> colOffer;
    @FXML private TableColumn<Application, String> colApplicant;
    @FXML private TableColumn<Application, ApplicationStatus> colStatus;
    @FXML private TableColumn<Application, Void> colActions;

    private final ApplicationService service = new ApplicationService();
    private final ObservableList<Application> applications = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colOffer.setCellValueFactory(new PropertyValueFactory<>("offerTitle"));
        colApplicant.setCellValueFactory(new PropertyValueFactory<>("applicantName"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        loadApplications();
        addActionButtons();
    }

    private void loadApplications() {
        try {
            applications.setAll(service.getAll());
            applicationTable.setItems(applications);
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void addActionButtons() {

        colActions.setCellFactory(param -> new TableCell<>() {

            private final Button btnAccept = new Button("✅ Accepter");
            private final Button btnReject = new Button("❌ Refuser");
            private final HBox box = new HBox(8, btnAccept, btnReject);

            {
                btnAccept.setOnAction(e -> {
                    Application app = getTableView().getItems().get(getIndex());
                    accept(app);
                });

                btnReject.setOnAction(e -> {
                    Application app = getTableView().getItems().get(getIndex());
                    reject(app);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                Application app = getTableView().getItems().get(getIndex());

                boolean pending = app.getStatus() == ApplicationStatus.PENDING;
                btnAccept.setDisable(!pending);
                btnReject.setDisable(!pending);

                setGraphic(box);
            }
        });
    }

    private void accept(Application app) {
        try {
            service.accept(app);
            loadApplications();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void reject(Application app) {
        try {
            service.reject(app);
            loadApplications();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(msg);
        alert.show();
    }
}
