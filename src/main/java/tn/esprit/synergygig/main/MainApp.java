package tn.esprit.synergygig.main;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.util.Objects;


public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        // 1️⃣ D'abord Atlantafx
        Application.setUserAgentStylesheet(
                new PrimerLight().getUserAgentStylesheet()
        );

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/tn/esprit/synergygig/gui/MainLayout.fxml")
        );

        Scene scene = new Scene(loader.load(), 1100, 650);

        // 2️⃣ ENSUITE tes CSS (ils écrasent Atlantafx)
        scene.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/tn/esprit/synergygig/gui/app.css")
                ).toExternalForm()
        );

        scene.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/tn/esprit/synergygig/gui/dashboard.css")
                ).toExternalForm()
        );

        stage.setTitle("SynergyGig — Offers");
        stage.setScene(scene);
        stage.show();
    }

}
