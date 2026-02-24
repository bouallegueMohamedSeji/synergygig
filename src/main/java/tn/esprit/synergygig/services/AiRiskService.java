package tn.esprit.synergygig.services;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class AiRiskService {

    private static final String API_KEY = ""; // ton token

    private static final String MODEL_URL =
            "https://router.huggingface.co/hf-inference/models/cross-encoder/nli-distilroberta-base";
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
          "inputs": {
            "premise": "%s",
            "hypothesis": "This contract has high legal risk."
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

            BufferedReader br =
                    new BufferedReader(new InputStreamReader(inputStream));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            String result = response.toString();
            System.out.println("HF RESPONSE: " + result);

            if (responseCode != 200) return 0.3;

            // Exemple réponse :
            // [{"label":"ENTAILMENT","score":0.87}]

            int scoreIndex = result.indexOf("\"score\":") + 8;
            int endIndex = result.indexOf("}", scoreIndex);

            String scoreStr =
                    result.substring(scoreIndex, endIndex);

            return Double.parseDouble(scoreStr);

        } catch (Exception e) {
            e.printStackTrace();
            return 0.3;
        }
    }
}
