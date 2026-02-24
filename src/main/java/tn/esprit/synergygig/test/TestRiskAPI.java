package tn.esprit.synergygig.test;

import tn.esprit.synergygig.services.AiRiskService;

public class TestRiskAPI {

    public static void main(String[] args) {

        AiRiskService service = new AiRiskService();

        double high =
                service.analyzeRisk("Client may terminate without notice and has no liability.");

        double low =
                service.analyzeRisk("Both parties agree to clear payment and dispute resolution.");

        System.out.println("HIGH RISK SCORE: " + high);
        System.out.println("LOW RISK SCORE: " + low);
    }
}