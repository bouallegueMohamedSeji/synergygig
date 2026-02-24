package tn.esprit.synergygig.test;

import tn.esprit.synergygig.services.OllamaService;

public class TestRisk {

    public static void main(String[] args) {

        OllamaService service = new OllamaService();

        String risky = """
Client may delay payment indefinitely without penalty.
Service provider has no legal recourse.
""";

        double score = service.analyzeRisk(risky);

        System.out.println("RISK SCORE = " + score);
    }
}