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
import javafx.scene.layout.HBox;
import javafx.scene.control.Button;
import javafx.geometry.Pos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.control.TableCell;
import tn.esprit.synergygig.entities.enums.ContractStatus;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;




public class ApplicationsAdminController {

    @FXML private TableView<Application> applicationTable;
    @FXML private TableColumn<Application, String> colOffer;
    @FXML private TableColumn<Application, String> colApplicant;
    @FXML private TableColumn<Application, ApplicationStatus> colStatus;
    @FXML private TableColumn<Application, Void> colActions;
    @FXML
    private TextArea aiResultArea;

    @FXML
    private Label aiBadge;

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
            private final Button btnAnalyze = new Button("🤖 Analyze");
            private final Button btnImprove = new Button("✨ Improve");

            private final HBox box =
                    new HBox(12, btnAccept, btnReject, btnAnalyze, btnImprove);

            {
                box.setAlignment(Pos.CENTER);
                box.setStyle("-fx-padding:8;");

                btnAccept.setPrefWidth(110);
                btnReject.setPrefWidth(110);
                btnAnalyze.setPrefWidth(110);
                btnImprove.setPrefWidth(110);

                btnAccept.getStyleClass().add("btn-accept");
                btnReject.getStyleClass().add("btn-reject");
                btnAnalyze.getStyleClass().add("btn-ai");
                btnImprove.getStyleClass().add("btn-improve");

                btnAccept.setOnAction(e -> {
                    Application app =
                            getTableView().getItems().get(getIndex());
                    accept(app);
                    getTableView().refresh();
                });

                btnReject.setOnAction(e -> {
                    Application app =
                            getTableView().getItems().get(getIndex());
                    reject(app);
                    getTableView().refresh();
                });

                btnAnalyze.setOnAction(e -> {
                    Application app =
                            getTableView().getItems().get(getIndex());
                    if ("ACCEPTED".equals(app.getStatus().name())) {
                        analyzeContract(app);
                    }
                });

                btnImprove.setOnAction(e -> {
                    Application app =
                            getTableView().getItems().get(getIndex());
                    if ("ACCEPTED".equals(app.getStatus().name())) {
                        improveContract(app);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {

                    Application app =
                            getTableView().getItems().get(getIndex());

                    boolean isPending =
                            "PENDING".equals(app.getStatus().name());

                    boolean isAccepted =
                            "ACCEPTED".equals(app.getStatus().name());

                    btnAccept.setDisable(!isPending);
                    btnReject.setDisable(!isPending);

                    btnAnalyze.setDisable(!isAccepted);
                    btnImprove.setDisable(!isAccepted);

                    setGraphic(box);
                }
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
            contract.setStatus(ContractStatus.GENERATED);

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

    private void improveContract(Application app) {

        try {

            ContractService service = new ContractService();
            Contract contract =
                    service.getByApplicationId(app.getId());

            if (contract == null) {
                showGalaxyPopup("Error",
                        "No contract found.",
                        "❌ ERROR");
                return;
            }

            javafx.concurrent.Task<Void> task =
                    new javafx.concurrent.Task<>() {
                        @Override
                        protected Void call() {
                            service.improveWithAI(contract);
                            return null;
                        }
                    };

            task.setOnSucceeded(e -> {

                // 🔥 UTILISER L'OBJET EN MÉMOIRE (PAS RELOAD DB)
                String improved = contract.getAiImproved();

                if (improved == null || improved.isBlank()) {
                    showGalaxyPopup(
                            "AI Improve",
                            "Improvement failed.",
                            "❌ ERROR"
                    );
                    return;
                }

                showGalaxyPopup(
                        "✨ Improved Contract Version",
                        improved,
                        "⚖ LEGAL OPTIMIZATION COMPLETE"
                );
            });

            new Thread(task).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void showGalaxyPopup(String title, String content, String riskStatus) {

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);

        Label riskLabel = new Label(riskStatus);
        riskLabel.setStyle("""
        -fx-text-fill: #00ffcc;
        -fx-font-size: 16px;
        -fx-font-weight: bold;
    """);

        TextArea textArea = new TextArea(content);
        textArea.setWrapText(true);
        textArea.setEditable(false);
        textArea.setPrefHeight(500);

        textArea.setStyle("""
        -fx-control-inner-background: #0f0f2d;
        -fx-text-fill: #4da6ff;
        -fx-font-size: 14px;
        -fx-font-family: 'Consolas';
        -fx-background-radius: 20;
        -fx-border-color: #00ccff;
        -fx-border-radius: 20;
        -fx-border-width: 2;
    """);

        VBox container = new VBox(20, riskLabel, textArea);
        container.setStyle("""
        -fx-background-color: linear-gradient(to bottom right, #0f0f2d, #1a1a40);
        -fx-padding: 30;
    """);

        Scene scene = new Scene(container, 900, 650);
        stage.setScene(scene);
        stage.showAndWait();
    }
    private void analyzeContract(Application app) {

        try {

            ContractService service = new ContractService();
            Contract contract =
                    service.getByApplicationId(app.getId());

            if (contract == null) {
                showGalaxyPopup("Error",
                        "No contract found.",
                        "❌ ERROR");
                return;
            }

            javafx.concurrent.Task<Void> task =
                    new javafx.concurrent.Task<>() {
                        @Override
                        protected Void call() {
                            service.analyzeWithAI(contract);
                            return null;
                        }
                    };

            task.setOnSucceeded(e -> {

                // 🔥 UTILISER L'OBJET EN MÉMOIRE (PAS RELOAD DB)
                String summary = contract.getAiSummary();
                double risk = contract.getRiskScore();

                if (summary == null || summary.isBlank()) {
                    showGalaxyPopup(
                            "AI Analysis",
                            "AI analysis failed.",
                            "❌ ERROR"
                    );
                    return;
                }

                String riskLevel;

                if (risk < 0.3) {
                    riskLevel = "🟢 LOW RISK (" + risk + ")";
                } else if (risk < 0.7) {
                    riskLevel = "🟡 MEDIUM RISK (" + risk + ")";
                } else {
                    riskLevel = "🔴 HIGH RISK (" + risk + ")";
                }

                showGalaxyPopup(
                        "🤖 AI Contract Analysis",
                        summary,
                        riskLevel
                );
            });

            new Thread(task).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
