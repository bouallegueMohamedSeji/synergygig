package tn.esprit.SynergyGig.main;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/tn/esprit/SynergyGig/gui/fxml/MainLayout.fxml")
        );

        Scene scene = new Scene(loader.load(), 1200, 750);
        stage.setScene(scene);
        stage.setTitle("SynergyGig — Galaxy Edition");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
