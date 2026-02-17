package tn.esprit.SynergyGig.Controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import tn.esprit.SynergyGig.Services.TacheService;
import tn.esprit.SynergyGig.models.Projet;
import tn.esprit.SynergyGig.models.Tache;

import java.time.LocalDate;
import java.util.Optional;

public class TacheController {

    // ===== TABLE =====
    @FXML private TableView<Tache>              tacheTable;
    @FXML private TableColumn<Tache, Integer>   colId;
    @FXML private TableColumn<Tache, String>    colTitre;
    @FXML private TableColumn<Tache, String>    colPriorite;
    @FXML private TableColumn<Tache, String>    colStatut;
    @FXML private TableColumn<Tache, LocalDate> colDateDebut;
    @FXML private TableColumn<Tache, LocalDate> colDateFin;
    @FXML private TableColumn<Tache, Integer>   colProjetId;
    @FXML private TableColumn<Tache, Void>      colActions;

    // ===== FORM =====
    @FXML private TextField        titreField;
    @FXML private TextField        descriptionField;
    @FXML private TextField        employeIdField;
    @FXML private ComboBox<String> prioriteBox;
    @FXML private ComboBox<String> statutBox;
    @FXML private DatePicker       dateDebutPicker;
    @FXML private DatePicker       dateFinPicker;
    @FXML private Button           btnAjouter;

    // ===== HEADER (affiche le nom du projet) =====
    @FXML private Label labelProjet;

    // ===== SERVICE =====
    private final TacheService tacheService = new TacheService();
    private final ObservableList<Tache> tacheList = FXCollections.observableArrayList();
    private Tache tacheEnCoursModification = null;

    // Le projet parent (transmis depuis ProjetController)
    private Projet projetCourant = null;

    // ===== INITIALISATION STANDARD =====
    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colPriorite.setCellValueFactory(new PropertyValueFactory<>("priorite"));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colDateDebut.setCellValueFactory(new PropertyValueFactory<>("dateDebut"));
        colDateFin.setCellValueFactory(new PropertyValueFactory<>("dateFin"));
        colProjetId.setCellValueFactory(new PropertyValueFactory<>("projetId"));

        prioriteBox.setItems(FXCollections.observableArrayList("HAUTE", "MOYENNE", "BASSE"));
        statutBox.setItems(FXCollections.observableArrayList("A_FAIRE", "EN_COURS", "TERMINEE"));

        ajouterBoutonsActions();
    }

    /**
     * ✅ MÉTHODE CLÉ : appelée depuis ProjetController après le chargement du FXML
     * Elle reçoit le projet sélectionné et charge uniquement ses tâches
     */
    public void initialiserAvecProjet(Projet projet) {
        this.projetCourant = projet;

        // Met à jour le label d'en-tête avec le nom du projet
        if (labelProjet != null) {
            labelProjet.setText("📋 Tâches du projet : " + projet.getNom()
                    + "  (ID : " + projet.getId() + ")");
        }

        // Charge uniquement les tâches de CE projet
        loadTachesDuProjet();
    }

    /**
     * Charge les tâches filtrées par projet via le Service
     */
    private void loadTachesDuProjet() {
        if (projetCourant == null) return;

        // Controller → Service → DAO → BDD
        tacheList.setAll(tacheService.afficherTachesParProjet(projetCourant.getId()));
        tacheTable.setItems(tacheList);
    }

    /**
     * Boutons Modifier / Supprimer dans chaque ligne
     */
    private void ajouterBoutonsActions() {
        colActions.setCellFactory(new Callback<>() {
            @Override
            public TableCell<Tache, Void> call(TableColumn<Tache, Void> param) {
                return new TableCell<>() {
                    private final Button btnModifier  = new Button("✏️ Modifier");
                    private final Button btnSupprimer = new Button("🗑️ Supprimer");

                    {
                        btnModifier.setStyle(
                                "-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                                        "-fx-cursor: hand; -fx-background-radius: 4;");
                        btnSupprimer.setStyle(
                                "-fx-background-color: #f44336; -fx-text-fill: white; " +
                                        "-fx-cursor: hand; -fx-background-radius: 4;");

                        btnModifier.setOnAction(e -> {
                            Tache t = getTableView().getItems().get(getIndex());
                            remplirFormulaireModification(t);
                        });
                        btnSupprimer.setOnAction(e -> {
                            Tache t = getTableView().getItems().get(getIndex());
                            supprimerTache(t);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            HBox hbox = new HBox(5);
                            hbox.setAlignment(Pos.CENTER);
                            hbox.getChildren().addAll(btnModifier, btnSupprimer);
                            setGraphic(hbox);
                        }
                    }
                };
            }
        });
    }

    /**
     * AJOUTER ou MODIFIER une tâche
     */
    @FXML
    private void ajouterTache() {
        if (!validerChamps()) return;

        try {
            int employeId = Integer.parseInt(employeIdField.getText().trim());

            if (tacheEnCoursModification == null) {
                // MODE AJOUT : projet_id = projetCourant.getId() (automatique !)
                Tache t = new Tache(
                        titreField.getText().trim(),
                        descriptionField.getText().trim(),
                        projetCourant.getId(),   // ← Automatiquement lié au projet ouvert
                        employeId,
                        statutBox.getValue(),
                        prioriteBox.getValue(),
                        dateDebutPicker.getValue(),
                        dateFinPicker.getValue()
                );
                tacheService.ajouterTache(t);
                showSuccess("Tâche ajoutée avec succès ! ✅");

            } else {
                // MODE MODIFICATION
                tacheEnCoursModification.setTitre(titreField.getText().trim());
                tacheEnCoursModification.setDescription(descriptionField.getText().trim());
                tacheEnCoursModification.setEmployeId(employeId);
                tacheEnCoursModification.setStatut(statutBox.getValue());
                tacheEnCoursModification.setPriorite(prioriteBox.getValue());
                tacheEnCoursModification.setDateDebut(dateDebutPicker.getValue());
                tacheEnCoursModification.setDateFin(dateFinPicker.getValue());

                tacheService.modifierTache(tacheEnCoursModification);
                showSuccess("Tâche modifiée avec succès ! ✅");

                tacheEnCoursModification = null;
                btnAjouter.setText("➕ Ajouter");
            }

            loadTachesDuProjet();
            clearForm();

        } catch (NumberFormatException e) {
            showError("L'ID employé doit être un nombre valide !");
        } catch (Exception e) {
            showError("Erreur : " + e.getMessage());
        }
    }

    private void remplirFormulaireModification(Tache tache) {
        tacheEnCoursModification = tache;
        titreField.setText(tache.getTitre());
        descriptionField.setText(tache.getDescription());
        employeIdField.setText(String.valueOf(tache.getEmployeId()));
        prioriteBox.setValue(tache.getPriorite());
        statutBox.setValue(tache.getStatut());
        dateDebutPicker.setValue(tache.getDateDebut());
        dateFinPicker.setValue(tache.getDateFin());
        btnAjouter.setText("💾 Enregistrer");
        titreField.requestFocus();
    }

    private void supprimerTache(Tache tache) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText("Supprimer la tâche ?");
        confirmation.setContentText("Voulez-vous supprimer \"" + tache.getTitre() + "\" ?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            tacheService.supprimerTache(tache.getId());
            loadTachesDuProjet();
            showSuccess("Tâche supprimée ! 🗑️");
            if (tacheEnCoursModification != null &&
                    tacheEnCoursModification.getId() == tache.getId()) {
                annulerModification();
            }
        }
    }

    @FXML
    private void annulerModification() {
        tacheEnCoursModification = null;
        btnAjouter.setText("➕ Ajouter");
        clearForm();
    }

    private boolean validerChamps() {
        if (titreField.getText().trim().isEmpty()) {
            showError("Le titre est obligatoire !");
            titreField.requestFocus();
            return false;
        }
        if (prioriteBox.getValue() == null) {
            showError("Veuillez sélectionner une priorité !");
            prioriteBox.requestFocus();
            return false;
        }
        if (statutBox.getValue() == null) {
            showError("Veuillez sélectionner un statut !");
            statutBox.requestFocus();
            return false;
        }
        if (employeIdField.getText().trim().isEmpty()) {
            showError("L'ID de l'employé est obligatoire !");
            employeIdField.requestFocus();
            return false;
        }
        try {
            int id = Integer.parseInt(employeIdField.getText().trim());
            if (id <= 0) {
                showError("L'ID employé doit être positif !");
                employeIdField.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            showError("L'ID employé doit être un nombre !");
            employeIdField.requestFocus();
            return false;
        }
        if (dateDebutPicker.getValue() == null) {
            showError("La date de début est obligatoire !");
            dateDebutPicker.requestFocus();
            return false;
        }
        if (dateFinPicker.getValue() == null) {
            showError("La date de fin est obligatoire !");
            dateFinPicker.requestFocus();
            return false;
        }
        if (dateFinPicker.getValue().isBefore(dateDebutPicker.getValue())) {
            showError("La date de fin doit être après la date de début !");
            dateFinPicker.requestFocus();
            return false;
        }
        return true;
    }

    private void clearForm() {
        titreField.clear();
        descriptionField.clear();
        employeIdField.clear();
        prioriteBox.getSelectionModel().clearSelection();
        statutBox.getSelectionModel().clearSelection();
        dateDebutPicker.setValue(null);
        dateFinPicker.setValue(null);
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private void showSuccess(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succès");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }
}
