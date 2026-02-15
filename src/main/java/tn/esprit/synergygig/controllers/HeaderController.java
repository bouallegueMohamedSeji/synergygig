package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import tn.esprit.synergygig.entities.User;
import tn.esprit.synergygig.utils.UserSession;

public class HeaderController {

    @FXML
    private Label userNameLabel;

    @FXML
    private HBox userBox;

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    @FXML
    public void initialize() {
        // Load user name from session
        if (UserSession.getInstance() != null) {
            User user = UserSession.getInstance().getUser();
            if (user != null) {
                userNameLabel.setText(user.getFullName() + " (" + user.getRole() + ")");
            }
        }
    }

    @FXML
    private void handleProfileClick() {
        if (mainLayoutController != null) {
            mainLayoutController.showProfile();
        }
    }
}
