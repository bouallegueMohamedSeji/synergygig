package controllers;

import entities.Interview;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import services.ServiceInterview;
import utils.SessionManager;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public class InterviewController {

    @FXML
    private TableView<Interview> interviewTable;
    @FXML
    private TableColumn<Interview, Integer> colId;
    @FXML
    private TableColumn<Interview, String> colDate;
    @FXML
    private TableColumn<Interview, String> colStatus;
    @FXML
    private TableColumn<Interview, String> colLink;
    @FXML
    private TableColumn<Interview, Void> colActions;

    private ServiceInterview serviceInterview = new ServiceInterview();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colLink.setCellValueFactory(new PropertyValueFactory<>("meetLink"));

        colDate.setCellValueFactory(cellData -> {
            Timestamp ts = cellData.getValue().getDateTime();
            return new SimpleStringProperty(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(ts));
        });

        setupActions();
        loadInterviews();
    }

    private void loadInterviews() {
        try {
            int userId = SessionManager.getInstance().getCurrentUser().getId();
            // TODO: In a real app, check if user is organizer or candidate
            // For now, load all for demo/admin purposes
            interviewTable.setItems(FXCollections.observableArrayList(serviceInterview.recuperer()));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupActions() {
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnJoin = new Button("Join");

            {
                btnJoin.getStyleClass().add("btn-small");
                btnJoin.setOnAction(e -> {
                    Interview i = getTableView().getItems().get(getIndex());
                    // Open link logic here (Desktop.getDesktop().browse...)
                    System.out.println("Opening: " + i.getMeetLink());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setGraphic(null);
                else
                    setGraphic(btnJoin);
            }
        });
    }

    @FXML
    private void showScheduleDialog() {
        Dialog<Interview> dialog = new Dialog<>();
        dialog.setTitle("Schedule Interview");
        dialog.setHeaderText("Create a new interview session");

        ButtonType saveButtonType = new ButtonType("Schedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField candidateIdField = new TextField();
        candidateIdField.setPromptText("Candidate ID");
        DatePicker datePicker = new DatePicker();
        TextField timeField = new TextField();
        timeField.setPromptText("HH:MM (e.g. 14:30)");
        TextField linkField = new TextField();
        linkField.setPromptText("Zoom/Meet Link");

        grid.add(new Label("Candidate ID:"), 0, 0);
        grid.add(candidateIdField, 1, 0);
        grid.add(new Label("Date:"), 0, 1);
        grid.add(datePicker, 1, 1);
        grid.add(new Label("Time:"), 0, 2);
        grid.add(timeField, 1, 2);
        grid.add(new Label("Link:"), 0, 3);
        grid.add(linkField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    int candId = Integer.parseInt(candidateIdField.getText());
                    LocalDate date = datePicker.getValue();
                    LocalTime time = LocalTime.parse(timeField.getText());
                    Timestamp ts = Timestamp.valueOf(date.atTime(time));

                    int organizerId = SessionManager.getInstance().getCurrentUser().getId();

                    return new Interview(organizerId, candId, ts, linkField.getText());
                } catch (Exception e) {
                    return null;
                }
            }
            return null;
        });

        Optional<Interview> result = dialog.showAndWait();
        result.ifPresent(interview -> {
            try {
                serviceInterview.ajouter(interview);
                loadInterviews();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}
