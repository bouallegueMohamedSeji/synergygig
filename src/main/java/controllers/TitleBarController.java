package controllers;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class TitleBarController {

    @FXML private Button maximizeBtn;

    private double xOffset = 0;
    private double yOffset = 0;

    /* Saved bounds for restoring from maximized state */
    private double savedX, savedY, savedW, savedH;
    private boolean isMaximized = false;

    @FXML
    private void close(ActionEvent event) {
        Platform.exit();
    }

    @FXML
    private void minimize(ActionEvent event) {
        getStage(event).setIconified(true);
    }

    @FXML
    private void maximize(ActionEvent event) {
        toggleMaximize(getStage(event));
    }

    /** Double-click on title bar toggles maximize */
    @FXML
    private void handleMouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
            toggleMaximize(getStageFromMouse(event));
        }
    }

    @FXML
    private void handleMousePressed(MouseEvent event) {
        if (isMaximized) return;          // don't start drag while maximized
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        Stage stage = getStageFromMouse(event);

        /* If maximized, snap out of maximize on drag */
        if (isMaximized) {
            double pctX = event.getSceneX() / stage.getWidth();
            restoreFromMaximize(stage);
            // Re-anchor so mouse stays proportionally on title bar
            xOffset = savedW * pctX;
            yOffset = event.getSceneY();
        }

        stage.setX(event.getScreenX() - xOffset);
        stage.setY(event.getScreenY() - yOffset);
    }

    /* ---- helpers ---- */

    private void toggleMaximize(Stage stage) {
        if (isMaximized) {
            restoreFromMaximize(stage);
        } else {
            // Save current bounds
            savedX = stage.getX();
            savedY = stage.getY();
            savedW = stage.getWidth();
            savedH = stage.getHeight();

            // Maximize to the visual bounds of the screen the window is on
            Rectangle2D bounds = Screen.getScreensForRectangle(
                    stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()
            ).get(0).getVisualBounds();

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            isMaximized = true;
            if (maximizeBtn != null) maximizeBtn.setText("❐");
        }
    }

    private void restoreFromMaximize(Stage stage) {
        stage.setX(savedX);
        stage.setY(savedY);
        stage.setWidth(savedW);
        stage.setHeight(savedH);
        isMaximized = false;
        if (maximizeBtn != null) maximizeBtn.setText("☐");
    }

    private Stage getStage(ActionEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }

    private Stage getStageFromMouse(MouseEvent event) {
        return (Stage) ((Node) event.getSource()).getScene().getWindow();
    }
}
