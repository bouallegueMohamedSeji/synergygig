package tn.esprit.SynergyGig.Controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import tn.esprit.SynergyGig.Services.ProjetService;
import tn.esprit.SynergyGig.models.Projet;
import tn.esprit.SynergyGig.Controller.TacheController;

import java.time.LocalDate;
import java.util.Optional;

public class ProjetController {

    // ===== TABLE =====
    @FXML private TableView<Projet> projetTable;
    @FXML private TableColumn<Projet, Integer>   colId;
    @FXML private TableColumn<Projet, String>    colNom;
    @FXML private TableColumn<Projet, String>    colStatut;
    @FXML private TableColumn<Projet, Double>    colBudget;
    @FXML private TableColumn<Projet, LocalDate> colDateDebut;
    @FXML private TableColumn<Projet, LocalDate> colDateFin;
    @FXML private TableColumn<Projet, Void>      colActions;

    // ===== FORM =====
    @FXML private TextField        nomField;
    @FXML private TextField        budgetField;
    @FXML private ComboBox<String> statutBox;
    @FXML private DatePicker       dateDebutPicker;
    @FXML private DatePicker       dateFinPicker;
    @FXML private Button           btnAjouter;

    // ===== SERVICE =====
    private final ProjetService projetService = new ProjetService();
    private final ObservableList<Projet> projetList = FXCollections.observableArrayList();
    private Projet projetEnCoursModification = null;

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colBudget.setCellValueFactory(new PropertyValueFactory<>("budget"));
        colDateDebut.setCellValueFactory(new PropertyValueFactory<>("dateDebut"));
        colDateFin.setCellValueFactory(new PropertyValueFactory<>("dateFin"));

        statutBox.setItems(FXCollections.observableArrayList(
                "EN_COURS", "TERMINE", "ANNULE"
        ));

        ajouterBoutonsActions();
        loadProjets();
    }

    /**
     * ✅ MODIFIÉ : utilise les classes CSS galaxy au lieu des styles inline
     * btn-edit    → Modifier  (cyan néon)
     * btn-delete  → Supprimer (rose néon)
     * btn-tasks   → Voir Tâches (violet)
     */
    private void ajouterBoutonsActions() {
        colActions.setCellFactory(new Callback<>() {
            @Override
            public TableCell<Projet, Void> call(TableColumn<Projet, Void> param) {
                return new TableCell<>() {

                    private final Button btnModifier   = new Button("✎ Modifier");
                    private final Button btnSupprimer  = new Button("✕ Supprimer");
                    private final Button btnVoirTaches = new Button("◈ Voir Tâches");

                    {
                        // ✅ Classes CSS galaxy (plus de style inline)
                        btnModifier.getStyleClass().add("btn-edit");
                        btnSupprimer.getStyleClass().add("btn-delete");
                        btnVoirTaches.getStyleClass().add("btn-tasks");

                        HBox hbox = new HBox(8, btnModifier, btnSupprimer, btnVoirTaches);
                        hbox.setAlignment(Pos.CENTER_LEFT);
                        hbox.setPadding(new Insets(4, 0, 4, 6));

                        // === Actions (identiques à avant) ===
                        btnModifier.setOnAction(e -> {
                            Projet projet = getTableView().getItems().get(getIndex());
                            remplirFormulaireModification(projet);
                        });

                        btnSupprimer.setOnAction(e -> {
                            Projet projet = getTableView().getItems().get(getIndex());
                            supprimerProjet(projet);
                        });

                        btnVoirTaches.setOnAction(e -> {
                            Projet projet = getTableView().getItems().get(getIndex());
                            ouvrirFenetreTaches(projet);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            HBox hbox = new HBox(8, btnModifier, btnSupprimer, btnVoirTaches);
                            hbox.setAlignment(Pos.CENTER_LEFT);
                            hbox.setPadding(new Insets(4, 0, 4, 6));
                            setGraphic(hbox);
                        }
                    }
                };
            }
        });
    }

    /**
     * Ouvre la fenêtre tâches (inchangé)
     */
    private void ouvrirFenetreTaches(Projet projet) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/tn/esprit/SynergyGig/gui/fxml/tache-view.fxml")
            );

            Stage stageTaches = new Stage();
            stageTaches.initModality(Modality.WINDOW_MODAL);
            stageTaches.initOwner(projetTable.getScene().getWindow());
            stageTaches.setTitle("◈ Tâches — " + projet.getNom());
            stageTaches.setScene(new Scene(loader.load(), 1000, 650));

            TacheController tacheController = loader.getController();
            tacheController.initialiserAvecProjet(projet);

            stageTaches.show();

        } catch (Exception e) {
            showError("Impossible d'ouvrir les tâches : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadProjets() {
        projetList.setAll(projetService.afficherProjets());
        projetTable.setItems(projetList);
    }

    @FXML
    private void ajouterProjet() {
        if (!validerChamps()) return;

        try {
            if (projetEnCoursModification == null) {
                Projet p = new Projet(
                        nomField.getText().trim(),
                        null,
                        dateDebutPicker.getValue(),
                        dateFinPicker.getValue(),
                        statutBox.getValue(),
                        Double.parseDouble(budgetField.getText())
                );
                projetService.ajouterProjet(p);
                showSuccess("Projet ajouté avec succès ! ✅");
            } else {
                projetEnCoursModification.setNom(nomField.getText().trim());
                projetEnCoursModification.setStatut(statutBox.getValue());
                projetEnCoursModification.setBudget(Double.parseDouble(budgetField.getText()));
                projetEnCoursModification.setDateDebut(dateDebutPicker.getValue());
                projetEnCoursModification.setDateFin(dateFinPicker.getValue());

                projetService.modifierProjet(projetEnCoursModification);
                showSuccess("Projet modifié avec succès ! ✅");

                projetEnCoursModification = null;
                btnAjouter.setText("✦ Ajouter");
            }

            loadProjets();
            clearForm();

        } catch (NumberFormatException e) {
            showError("Le budget doit être un nombre valide !");
        } catch (Exception e) {
            showError("Erreur : " + e.getMessage());
        }
    }

    private void remplirFormulaireModification(Projet projet) {
        projetEnCoursModification = projet;
        nomField.setText(projet.getNom());
        budgetField.setText(String.valueOf(projet.getBudget()));
        statutBox.setValue(projet.getStatut());
        dateDebutPicker.setValue(projet.getDateDebut());
        dateFinPicker.setValue(projet.getDateFin());
        btnAjouter.setText("💾 Enregistrer");
        nomField.requestFocus();
    }

    private void supprimerProjet(Projet projet) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirmation");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Supprimer \"" + projet.getNom() + "\" ?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            projetService.supprimerProjet(projet.getId());
            loadProjets();
            showSuccess("Projet supprimé ! ✕");
            if (projetEnCoursModification != null &&
                    projetEnCoursModification.getId() == projet.getId()) {
                annulerModification();
            }
        }
    }

    @FXML
    private void annulerModification() {
        projetEnCoursModification = null;
        btnAjouter.setText("✦ Ajouter");
        clearForm();
    }

    private boolean validerChamps() {
        if (nomField.getText().trim().isEmpty()) {
            showError("Le nom du projet est obligatoire !");
            nomField.requestFocus();
            return false;
        }
        if (statutBox.getValue() == null) {
            showError("Veuillez sélectionner un statut !");
            statutBox.requestFocus();
            return false;
        }
        if (budgetField.getText().trim().isEmpty()) {
            showError("Le budget est obligatoire !");
            budgetField.requestFocus();
            return false;
        }
        try {
            double budget = Double.parseDouble(budgetField.getText());
            if (budget < 0) {
                showError("Le budget ne peut pas être négatif !");
                budgetField.requestFocus();
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Le budget doit être un nombre valide !");
            budgetField.requestFocus();
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
        nomField.clear();
        budgetField.clear();
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
