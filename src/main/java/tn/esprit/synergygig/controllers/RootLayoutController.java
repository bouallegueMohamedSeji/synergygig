package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.IOException;

public class RootLayoutController {

    @FXML
    private BorderPane mainContainer;

    @FXML
    private HBox titleBar;

    @FXML
    private Label resizeHandle;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
        // Drag window
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });

        // Resize window
        resizeHandle.setOnMouseDragged(event -> {
            Stage stage = (Stage) resizeHandle.getScene().getWindow();
            double newWidth = event.getScreenX() - stage.getX();
            double newHeight = event.getScreenY() - stage.getY();

            if (newWidth > 800)
                stage.setWidth(newWidth);
            if (newHeight > 600)
                stage.setHeight(newHeight);
        });

        // Initial view
        showLogin();
    }

    @FXML
    private void handleMinimize() {
        Stage stage = (Stage) titleBar.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleClose() {
        System.exit(0);
    }

    public void showLogin() {
        loadView("/tn/esprit/synergygig/gui/Login.fxml");
    }

    public void showSignup() {
        loadView("/tn/esprit/synergygig/gui/Signup.fxml");
    }

    public void showDashboard() {
        loadView("/tn/esprit/synergygig/gui/MainLayout.fxml");
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();
            mainContainer.setCenter(view);

            // If the loaded view is Login, we might need to pass this controller to it
            // so it can call showDashboard().
            // Ideally, we could use an EventBus or singleton, but for now let's check
            // controller type.
            Object controller = loader.getController();
            if (controller instanceof LoginController) {
                ((LoginController) controller).setRootLayoutController(this);
            } else if (controller instanceof SignupController) {
                ((SignupController) controller).setRootLayoutController(this);
            } else if (controller instanceof MainLayoutController) {
                ((MainLayoutController) controller).setRootLayoutController(this);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
