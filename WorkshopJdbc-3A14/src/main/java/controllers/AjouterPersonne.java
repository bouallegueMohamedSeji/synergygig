package controllers;

import entities.Personne;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.TextField;

import services.ServicePersonne;

import java.io.IOException;
import java.sql.SQLException;

public class AjouterPersonne {

    @FXML
    private TextField txtage;

    @FXML
    private TextField txtnom;

    @FXML
    private TextField txtprenom;

    @FXML
    void addPerson(ActionEvent event) {
        String nom=txtnom.getText();
        String prenom=txtprenom.getText();
        int age = Integer.parseInt(txtage.getText());
        ServicePersonne ps =new ServicePersonne();
        Personne p = new Personne(nom,prenom,age);
        try {
            ps.ajouter(p);
            FXMLLoader loader = new FXMLLoader(getClass()
                    .getResource("/AfficherPersonne.fxml"));
            Parent root = loader.load();
            AfficherPersonne ac = loader.getController();
            ac.setRnom(nom);
            ac.setRprenom(prenom);
            ac.setrList(ps.recuperer().toString());
            txtnom.getScene().setRoot(root);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
