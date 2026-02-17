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
    private Button btnMaximize;

    @FXML
    private void maximize(ActionEvent event) {
        Stage stage = getStage(event);
        if (stage.isMaximized()) {
            stage.setMaximized(false);
            if (btnMaximize != null)
                btnMaximize.setText("☐");
        } else {
            stage.setMaximized(true);
            if (btnMaximize != null)
                btnMaximize.setText("❐");
        }
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        if (stage.isMaximized())
            return; // Disable drag if maximized

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
    public void initialize() {
        // Apply saved theme preference on load
        isLightMode = !utils.SessionManager.getInstance().isDarkMode();
        updateThemeUI();
    }

    @FXML
    private void toggleTheme() {
        isLightMode = !isLightMode;
        utils.SessionManager.getInstance().setDarkMode(!isLightMode);
        updateThemeUI();
    }

    private void updateThemeUI() {
        if (btnTheme == null || btnTheme.getScene() == null)
            return;

        Scene scene = btnTheme.getScene();
        if (isLightMode) {
            btnTheme.setText("☀️");
            if (!scene.getStylesheets().contains(getClass().getResource("/css/light-theme.css").toExternalForm())) {
                scene.getStylesheets().add(getClass().getResource("/css/light-theme.css").toExternalForm());
            }
        } else {
            btnTheme.setText("🌙");
            scene.getStylesheets().remove(getClass().getResource("/css/light-theme.css").toExternalForm());
        }
    }
}
