package tn.esprit.synergygig.test;

import tn.esprit.synergygig.services.OllamaService;

public class TestOllama {

    public static void main(String[] args) {

        OllamaService ollama = new OllamaService();

        System.out.println(
                ollama.summarize(
                        "Client may delay payment and legal conflict possible."
                )
        );
    }
}

