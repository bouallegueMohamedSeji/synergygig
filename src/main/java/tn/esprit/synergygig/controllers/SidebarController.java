package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;

public class SidebarController {

    private MainLayoutController mainLayoutController;

    public void setMainLayoutController(MainLayoutController controller) {
        this.mainLayoutController = controller;
    }

    public void showDashboard() {
        mainLayoutController.showDashboard();
    }

    public void showOffers() {
        mainLayoutController.showOffers();
    }

    public void showApplications() {
        mainLayoutController.showApplications();
    }
    @FXML
    private void goGigOffers() {
        mainLayoutController.showGigOffers();
    }
    @FXML
    private void showApplicationsAdmin() {
        mainLayoutController.showApplicationsAdmin();
    }





}
