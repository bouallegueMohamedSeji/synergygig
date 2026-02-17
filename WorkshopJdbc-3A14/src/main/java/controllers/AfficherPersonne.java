package controllers;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;

public class AfficherPersonne {
    @FXML
    private TextField rList;

    @FXML
    private TextField rnom;

    @FXML
    private TextField rprenom;

    public void setrList(String rList) {
        this.rList.setText(rList);
    }

    public void setRnom(String rnom) {
        this.rnom.setText(rnom);
    }

    public void setRprenom(String rprenom) {
        this.rprenom.setText(rprenom);
    }
}
