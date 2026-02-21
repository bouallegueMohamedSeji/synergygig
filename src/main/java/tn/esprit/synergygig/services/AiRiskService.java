package tn.esprit.synergygig.services;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class AiRiskService {

    private static final String API_KEY = "hf_xxxxxxxxxxxxxxxxxxxxx"; // ton token

    private static final String MODEL_URL =
            "https://router.huggingface.co/models/facebook/bart-large-mnli";

    public double analyzeRisk(String text) {

        try {

            URL url = new URL(MODEL_URL);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInput = """
            {
              "inputs": "%s",
              "parameters": {
                "candidate_labels": ["low risk", "medium risk", "high risk"]
              }
            }
            """.formatted(text.replace("\"", "'"));

            OutputStream os = conn.getOutputStream();
            os.write(jsonInput.getBytes());
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            System.out.println("HF Response Code: " + responseCode);

            InputStream inputStream =
                    (responseCode >= 200 && responseCode < 300)
                            ? conn.getInputStream()
                            : conn.getErrorStream();

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(inputStream)
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            String result = response.toString();
            System.out.println("HF RESPONSE: " + result);

            if (result.contains("\"high risk\""))
                return 0.9;

            if (result.contains("\"medium risk\""))
                return 0.5;

            return 0.1;

        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }
}
