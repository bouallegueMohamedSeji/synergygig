package tn.esprit.SynergyGig.main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/tn/esprit/SynergyGig/gui/fxml/projetView.fxml")
        );

        stage.setScene(new Scene(loader.load()));
        stage.setTitle("Gestion des Projets");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
