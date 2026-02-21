package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import tn.esprit.synergygig.entities.Contract;
import tn.esprit.synergygig.entities.enums.ContractStatus;
import tn.esprit.synergygig.entities.enums.PaymentStatus;
import tn.esprit.synergygig.services.ContractService;
import tn.esprit.synergygig.services.UserService;
import tn.esprit.synergygig.entities.User;

import java.util.List;

public class ContractsAdminController {

    @FXML private TableView<Contract> contractTable;
    @FXML private TableColumn<Contract, String> colWorker;
    @FXML private TableColumn<Contract, Double> colAmount;
    @FXML private TableColumn<Contract, ContractStatus> colStatus;
    @FXML private TableColumn<Contract, PaymentStatus> colPayment;
    @FXML private TableColumn<Contract, Void> colActions;

    private final ContractService contractService = new ContractService();
    private final ObservableList<Contract> contracts = FXCollections.observableArrayList();

    @FXML
    public void initialize() {

        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colPayment.setCellValueFactory(new PropertyValueFactory<>("paymentStatus"));

        // 🔥 Worker name dynamique
        colWorker.setCellValueFactory(cellData -> {
            try {
                int applicationId = cellData.getValue().getApplicationId();
                // ici tu peux améliorer si besoin (ex: join plus tard)
                return javafx.beans.property.SimpleStringProperty.stringExpression(
                        new javafx.beans.property.SimpleStringProperty("Worker ID: " + applicationId)
                );
            } catch (Exception e) {
                return null;
            }
        });

        addActionButtons();
        loadContracts();
    }

    private void loadContracts() {
        try {
            List<Contract> list = contractService.getAllContracts();
            contracts.setAll(list);
            contractTable.setItems(contracts);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addActionButtons() {

        colActions.setCellFactory(param -> new TableCell<>() {

            private final Button btnStart = new Button("🚀 Start Work");
            private final Button btnComplete = new Button("🏁 Complete");
            private final HBox box = new HBox(10, btnStart, btnComplete);

            {
                btnStart.setOnAction(e -> {
                    Contract contract = getTableView().getItems().get(getIndex());
                    contractService.startWork(contract);
                    loadContracts();
                });

                btnComplete.setOnAction(e -> {
                    Contract contract = getTableView().getItems().get(getIndex());
                    contractService.completeContract(contract);
                    loadContracts();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                Contract contract = getTableView().getItems().get(getIndex());

                // 🔐 Start actif seulement si GENERATED
                boolean canStart =
                        contract.getStatus() == ContractStatus.GENERATED;

                // 🔐 Complete actif seulement si IN_PROGRESS
                boolean canComplete =
                        contract.getStatus() == ContractStatus.IN_PROGRESS;

                btnStart.setDisable(!canStart);
                btnComplete.setDisable(!canComplete);

                setGraphic(box);
            }

        });
    }

}
