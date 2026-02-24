package tn.esprit.synergygig.services;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class WeatherService {

    private static final String API_KEY = "";

    public String getWeather(String city) {

        try {

            String urlString =
                    "https://api.openweathermap.org/data/2.5/weather?q="
                            + city +
                            "&appid=" + API_KEY +
                            "&units=metric";

            URL url = new URL(urlString);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");

            BufferedReader br =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            br.close();

            JSONObject obj = new JSONObject(response.toString());

            double temp = obj.getJSONObject("main").getDouble("temp");
            String description =
                    obj.getJSONArray("weather")
                            .getJSONObject(0)
                            .getString("description");

            return "Temperature: " + temp + "°C\nCondition: " + description;

        } catch (Exception e) {
            e.printStackTrace();
            return "Weather unavailable";
        }
    }
    public String getRawWeather(String city) {

        try {

            String urlString =
                    "https://api.openweathermap.org/data/2.5/weather?q="
                            + city +
                            "&appid=" + API_KEY +
                            "&units=metric";

            URL url = new URL(urlString);
            HttpURLConnection conn =
                    (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");

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