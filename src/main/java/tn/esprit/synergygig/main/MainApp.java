package tn.esprit.synergygig.main;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import tn.esprit.synergygig.utils.DatabaseSetup;

public class MainApp extends Application {

        private double xOffset = 0;
        private double yOffset = 0;

        @Override
        public void start(Stage stage) throws Exception {
                // Run Database Setup and Seeding
                DatabaseSetup.main(null);

                // 1️⃣ D'abord Atlantafx
                Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

                // Load RootLayout
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/tn/esprit/synergygig/gui/RootLayout.fxml"));
                Parent root = loader.load();

                // Set Scene
                Scene scene = new Scene(root);
                scene.setFill(javafx.scene.paint.Color.TRANSPARENT); // Set scene fill to transparent

                // Load CSS
                String css = getClass().getResource("/tn/esprit/synergygig/gui/app.css").toExternalForm();
                scene.getStylesheets().add(css);
                
                String dashboardCss = getClass().getResource("/tn/esprit/synergygig/gui/dashboard.css").toExternalForm();
                scene.getStylesheets().add(dashboardCss);

                // Set stage style to transparent
                stage.initStyle(StageStyle.TRANSPARENT);
                stage.setScene(scene);
                stage.setTitle("SynergyGig");

                // Allow dragging the stage
                root.setOnMousePressed((MouseEvent event) -> {
                        xOffset = event.getSceneX();
                        yOffset = event.getSceneY();
                });

                root.setOnMouseDragged((MouseEvent event) -> {
                        stage.setX(event.getScreenX() - xOffset);
                        stage.setY(event.getScreenY() - yOffset);
                });

                stage.show();
        }

        public static void main(String[] args) {
                launch(args);
        }
}
