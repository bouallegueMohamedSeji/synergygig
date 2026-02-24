package tn.esprit.synergygig.services;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class OpenAIService {

    private static final String API_KEY = "";
    private static final String ENDPOINT =
            "https://api.openai.com/v1/chat/completions";

    public String summarize(String terms) {

        try {

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization",
                    "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type",
                    "application/json");
            conn.setDoOutput(true);

            String jsonInput = """
            {
              "model": "gpt-4o-mini",
              "messages": [
                {"role": "system",
                 "content": "You are a legal assistant."},
                {"role": "user",
                 "content": "Summarize this contract clearly in bullet points and provide recommendations:\\n%s"}
              ],
              "temperature": 0.7
            }
            """.formatted(terms.replace("\"", "'"));

            OutputStream os = conn.getOutputStream();
            os.write(jsonInput.getBytes());
            os.flush();
            os.close();

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            return response.toString(); // on parsera après

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public String improve(String terms) {

        try {

            URL url = new URL(ENDPOINT);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization",
                    "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type",
                    "application/json");
            conn.setDoOutput(true);

            String jsonInput = """
        {
          "model": "gpt-4o-mini",
          "messages": [
            {"role": "system",
             "content": "You are a legal contract optimizer."},
            {"role": "user",
             "content": "Rewrite this contract to make it legally stronger and clearer:\\n%s"}
          ]
        }
        """.formatted(terms.replace("\"", "'"));

            OutputStream os = conn.getOutputStream();
            os.write(jsonInput.getBytes());
            os.flush();
            os.close();

            BufferedReader br =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            return response.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
