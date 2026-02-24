package tn.esprit.synergygig.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import tn.esprit.synergygig.services.ContractService;

public class VerifyContractController {

    @FXML private TextField hashField;
    @FXML private Label resultLabel;

    private final ContractService contractService = new ContractService();

    @FXML
    public void handleVerify() {

        try {

            String input = hashField.getText().trim();

            // 🔥 Si format QR = ID|HASH
            if (input.contains("|")) {
                String[] parts = input.split("\\|");
                input = parts[1]; // On prend seulement le hash
            }
            System.out.println("HASH ENTERED: " + input);

            boolean valid = contractService.verifyContract(input);

            if (valid) {
                resultLabel.setText("✅ CONTRACT VALID");
                resultLabel.setStyle("-fx-text-fill: #00ff88;");
            } else {
                resultLabel.setText("❌ CONTRACT INVALID OR TAMPERED");
                resultLabel.setStyle("-fx-text-fill: red;");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
