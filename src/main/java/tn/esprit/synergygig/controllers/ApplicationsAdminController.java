package tn.esprit.synergygig.controllers;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import tn.esprit.synergygig.entities.Application;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.enums.ApplicationStatus;
import tn.esprit.synergygig.services.ApplicationService;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import tn.esprit.synergygig.services.OfferService;
import java.time.LocalDate;

import tn.esprit.synergygig.entities.Contract;
import tn.esprit.synergygig.entities.Offer;
import tn.esprit.synergygig.entities.User;

import tn.esprit.synergygig.services.ContractService;
import tn.esprit.synergygig.services.OfferService;
import tn.esprit.synergygig.services.UserService;
import java.time.LocalDate;




public class ApplicationsAdminController {

    @FXML private TableView<Application> applicationTable;
    @FXML private TableColumn<Application, String> colOffer;
    @FXML private TableColumn<Application, String> colApplicant;
    @FXML private TableColumn<Application, ApplicationStatus> colStatus;
    @FXML private TableColumn<Application, Void> colActions;
    @FXML
    private Pane animatedBackground;

    private final ApplicationService service = new ApplicationService();
    private final ObservableList<Application> applications = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        createStars();
        applicationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colOffer.prefWidthProperty().bind(applicationTable.widthProperty().multiply(0.30));
        colApplicant.prefWidthProperty().bind(applicationTable.widthProperty().multiply(0.25));
        colStatus.prefWidthProperty().bind(applicationTable.widthProperty().multiply(0.20));
        colActions.prefWidthProperty().bind(applicationTable.widthProperty().multiply(0.25));

        colOffer.setCellValueFactory(new PropertyValueFactory<>("offerTitle"));
        colApplicant.setCellValueFactory(new PropertyValueFactory<>("applicantName"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));



        // 🔥 ICI tu mets le badge
        colStatus.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(ApplicationStatus status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(status.name());
                badge.getStyleClass().clear();
                badge.getStyleClass().add("status-badge");

                switch (status) {
                    case PENDING -> badge.getStyleClass().add("badge-pending");
                    case ACCEPTED -> badge.getStyleClass().add("badge-accepted");
                    case REJECTED -> badge.getStyleClass().add("badge-rejected");
                }

                setGraphic(badge);
            }
        });

        addActionButtons();  // boutons Accept / Reject
        loadApplications();
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

            private final Button btnAccept = new Button("✔ Accepter");
            private final Button btnReject = new Button("✖ Refuser");
            private final HBox box = new HBox(10, btnAccept, btnReject);

            {
                // 🔥 ajouter styles galaxy
                btnAccept.getStyleClass().add("btn-accept");
                btnReject.getStyleClass().add("btn-reject");

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

            // 1️⃣ Update statut application
            service.accept(app);

            // 2️⃣ Récupérer l'offre liée
            OfferService offerService = new OfferService();
            Offer offer = offerService.getById(app.getOfferId());

            if (offer == null) {
                showError("Offer introuvable.");
                return;
            }

            // 3️⃣ Récupérer le client (créateur offre)
            UserService userService = new UserService();
            User client = userService.getById(offer.getCreatedBy());

            if (client == null) {
                showError("Client introuvable.");
                return;
            }

            // 4️⃣ Créer contrat
            Contract contract = new Contract(
                    app.getId(),
                    java.time.LocalDate.now(),
                    java.time.LocalDate.now().plusDays(30),
                    offer.getAmount(),
                    "Client may delay payment and legal conflict possible"
            );

            // 5️⃣ Générer contrat (IA + PDF + Email)
            ContractService contractService = new ContractService();
            contractService.generateContract(
                    contract,
                    client.getEmail(),
                    client.getFullName()
            );

            showSuccess("✅ Application acceptée. Contrat généré et email envoyé.");

            loadApplications();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Erreur lors de l'acceptation.");
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
    private void createStars() {

        for (int i = 0; i < 500; i++) {

            Circle star = new Circle(Math.random() * 2 + 0.5);

            star.setLayoutX(Math.random() * 1600);
            star.setLayoutY(Math.random() * 900);

            if (Math.random() > 0.5) {
                star.setStyle("-fx-fill: rgba(255,255,255,0.8);");
            } else {
                star.setStyle("-fx-fill: #396afc;");
            }

            FadeTransition fade = new FadeTransition(
                    Duration.seconds(2 + Math.random() * 3),
                    star
            );

            fade.setFromValue(0.2);
            fade.setToValue(1);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();

            animatedBackground.getChildren().add(star);
        }
    }
    private void showSuccess(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setContentText(msg);
        alert.show();
    }


}
