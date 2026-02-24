package tn.esprit.synergygig.services;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import tn.esprit.synergygig.entities.Contract;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class OllamaService {

    private static final String ENDPOINT =
            "http://localhost:11434/api/generate";

    public String summarize(String text) {

        try {

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInput = """
        {
          "model": "llama3",
          "prompt": "Summarize this contract professionally:\\n%s",
          "stream": false
        }
        """.formatted(text.replace("\"", "'"));

            OutputStream os = conn.getOutputStream();
            os.write(jsonInput.getBytes());
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            System.out.println("OLLAMA RESPONSE CODE: " + responseCode);

            if (responseCode != 200) {
                System.out.println("OLLAMA ERROR");
                return null;
            }

            BufferedReader br =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            String full = response.toString();

            System.out.println("OLLAMA RAW RESPONSE:");
            System.out.println(full);

            int start = full.indexOf("\"response\":\"");
            int end = full.lastIndexOf("\",\"done\"");

            if (start == -1 || end == -1) {
                System.out.println("Parsing failed.");
                return null;
            }

            start += 12;

            return full.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public String improve(String text) {

        try {

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInput = """
        {
          "model": "llama3",
          "prompt": "Rewrite this contract to make it legally stronger and clearer:\\n%s",
          "stream": false
        }
        """.formatted(text.replace("\"", "'"));

            OutputStream os = conn.getOutputStream();
            os.write(jsonInput.getBytes());
            os.close();

            InputStream inputStream =
                    (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
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

            String full = response.toString();

            if (!full.contains("\"response\"")) {
                return null;
            }

            int start = full.indexOf("\"response\":\"") + 12;
            int end = full.indexOf("\",\"done\"");
            String cleaned = full.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");

            return cleaned;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public String generateLegalContract(Contract contract) {

        try {

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String prompt = """
Generate a detailed, legally binding professional service contract 
with clear sections and legal language.

Include:

1. Parties Information
2. Scope of Services
3. Payment Terms
4. Late Payment & Penalties
5. Confidentiality Clause
6. Intellectual Property
7. Termination Clause
8. Liability & Indemnification
9. Governing Law
10. Dispute Resolution
11. Entire Agreement

Use formal legal structure with numbered clauses.

Contract Details:
Contract ID: %d
Start Date: %s
End Date: %s
Contract Amount: %.2f
Initial Terms: %s

Make the contract long, structured and professional.
""".formatted(
                    contract.getId(),
                    contract.getStartDate(),
                    contract.getEndDate(),
                    contract.getAmount(),
                    contract.getTerms()
            );

            String jsonInput = """
        {
          "model": "llama3",
          "prompt": "%s",
          "stream": false
        }
        """.formatted(prompt.replace("\"", "'"));

            OutputStream os = conn.getOutputStream();
            os.write(jsonInput.getBytes());
            os.flush();
            os.close();

            InputStream inputStream =
                    (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
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

            String full = response.toString();

            if (!full.contains("\"response\"")) return null;

            int start = full.indexOf("\"response\":\"") + 12;
            int end = full.indexOf("\",\"done\"");

            return full.substring(start, end)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public double analyzeRisk(String text) {

        try {

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String prompt = """
You are a legal risk evaluation system.

Rate the legal risk of this contract between 0 and 1.
Return ONLY a decimal number.

Contract:
""" + text;

            // 🔥 Utilisation propre de Gson
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("model", "llama3");
            json.addProperty("prompt", prompt);
            json.addProperty("stream", false);

            OutputStream os = conn.getOutputStream();
            os.write(json.toString().getBytes());
            os.close();

            InputStream inputStream =
                    (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
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

            String full = response.toString();

            System.out.println("OLLAMA RAW RISK RESPONSE:");
            System.out.println(full);

            if (!full.contains("\"response\""))
                return 0.0;

            int start = full.indexOf("\"response\":\"") + 12;
            int end = full.indexOf("\",\"done\"");

            String extracted =
                    full.substring(start, end)
                            .replace("\\n", " ")
                            .replace("\\\"", "\"")
                            .trim();

            // Regex extraction
            java.util.regex.Pattern pattern =
                    java.util.regex.Pattern.compile("(0(\\.\\d+)?|1(\\.0+)?)");

            java.util.regex.Matcher matcher =
                    pattern.matcher(extracted);

            if (matcher.find()) {
                return Double.parseDouble(matcher.group());
            }

            return 0.0;

        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }
}
