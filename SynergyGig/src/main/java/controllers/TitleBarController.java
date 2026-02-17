package controllers;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class TitleBarController {

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    private void close(ActionEvent event) {
        Platform.exit();
    }

    @FXML
    private void minimize(ActionEvent event) {
        getStage(event).setIconified(true);
    }

    @FXML
    private void handleMousePressed(MouseEvent event) {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    private Stage getStage(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }

    @FXML
    private Button btnTheme;

    private boolean isLightMode = false;

    @FXML
    private void toggleTheme() {
        isLightMode = !isLightMode;
        Scene scene = btnTheme.getScene();

        if (isLightMode) {
            btnTheme.setText("☀️");
            // Add light theme stylesheet
            scene.getStylesheets().add(getClass().getResource("/css/light-theme.css").toExternalForm());
        } else {
            btnTheme.setText("🌙");
            // Remove light theme stylesheet
            scene.getStylesheets().remove(getClass().getResource("/css/light-theme.css").toExternalForm());
        }
    }
}
