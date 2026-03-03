package utils;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches weather data from wttr.in (100% free, no API key).
 * Used by the Dashboard weather widget popup.
 */
public class WeatherService {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Current weather data for a city. */
    public static class CurrentWeather {
        public String city;
        public String country;
        public String region;
        public int    tempC;
        public int    tempF;
        public int    feelsLikeC;
        public String condition;
        public int    humidity;
        public int    windKmph;
        public String windDir;
        public int    visibility;
        public int    uvIndex;
        public int    pressure;
        public int    cloudCover;
        public int    chanceOfRain;
        public String sunrise;
        public String sunset;
        public long   sunlightMinutes;
        public List<DayForecast> forecast = new ArrayList<>();
    }

    /** Daily forecast (next 7 days from wttr.in). */
    public static class DayForecast {
        public String dayName;   // "Mon", "Tue", etc.
        public String date;      // "2026-02-21"
        public int    maxTempC;
        public int    minTempC;
        public String condition;
        public int    chanceOfRain;

        public String getConditionEmoji() {
            return mapConditionEmoji(condition);
        }
    }

    /**
     * Fetch current weather + 3-day forecast for a city.
     * @param city city name (e.g. "Tunis", "Paris", "New+York")
     * @return CurrentWeather or null on failure
     */
    public static CurrentWeather fetch(String city) {
        try {
            String url = "https://wttr.in/" + city.replace(" ", "+") + "?format=j1";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "SynergyGig/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject current = root.getAsJsonArray("current_condition").get(0).getAsJsonObject();
            JsonObject area = root.getAsJsonArray("nearest_area").get(0).getAsJsonObject();

            CurrentWeather w = new CurrentWeather();
            w.city = area.getAsJsonArray("areaName").get(0).getAsJsonObject().get("value").getAsString();
            w.country = area.getAsJsonArray("country").get(0).getAsJsonObject().get("value").getAsString();
            w.region = area.getAsJsonArray("region").get(0).getAsJsonObject().get("value").getAsString();
            w.tempC = current.get("temp_C").getAsInt();
            w.tempF = current.get("temp_F").getAsInt();
            w.feelsLikeC = current.get("FeelsLikeC").getAsInt();
            w.condition = current.getAsJsonArray("weatherDesc").get(0).getAsJsonObject().get("value").getAsString();
            w.humidity = current.get("humidity").getAsInt();
            w.windKmph = current.get("windspeedKmph").getAsInt();
            w.windDir = current.get("winddir16Point").getAsString();
            w.visibility = current.get("visibility").getAsInt();
            w.uvIndex = current.get("uvIndex").getAsInt();
            w.pressure = current.get("pressure").getAsInt();
            w.cloudCover = current.get("cloudcover").getAsInt();

            // Astronomy (sunrise/sunset)
            JsonArray weatherArr = root.getAsJsonArray("weather");
            if (weatherArr.size() > 0) {
                JsonObject today = weatherArr.get(0).getAsJsonObject();
                JsonObject astro = today.getAsJsonArray("astronomy").get(0).getAsJsonObject();
                w.sunrise = astro.get("sunrise").getAsString().trim();
                w.sunset = astro.get("sunset").getAsString().trim();

                // Calculate sunlight hours
                try {
                    w.sunlightMinutes = parseSunlightMinutes(w.sunrise, w.sunset);
                } catch (Exception ignored) {
                    w.sunlightMinutes = 0;
                }

                // Hourly data → chance of rain for today
                JsonArray hourly = today.getAsJsonArray("hourly");
                int rainTotal = 0;
                int count = 0;
                for (JsonElement h : hourly) {
                    rainTotal += h.getAsJsonObject().get("chanceofrain").getAsInt();
                    count++;
                }
                w.chanceOfRain = count > 0 ? rainTotal / count : 0;
            }

            // 3-day forecast (wttr.in gives 3 days)
            String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            for (int i = 0; i < weatherArr.size(); i++) {
                JsonObject day = weatherArr.get(i).getAsJsonObject();
                DayForecast df = new DayForecast();
                df.date = day.get("date").getAsString();
                df.maxTempC = day.get("maxtempC").getAsInt();
                df.minTempC = day.get("mintempC").getAsInt();

                // Parse day name from date
                try {
                    java.time.LocalDate ld = java.time.LocalDate.parse(df.date);
                    df.dayName = i == 0 ? "Today" : ld.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
                } catch (Exception e) {
                    df.dayName = "Day " + (i + 1);
                }

                // Average condition from hourly
                JsonArray hourly = day.getAsJsonArray("hourly");
                if (hourly.size() > 4) {
                    // Use midday (index 4 = noon) condition
                    df.condition = hourly.get(4).getAsJsonObject()
                            .getAsJsonArray("weatherDesc").get(0).getAsJsonObject()
                            .get("value").getAsString();
                } else if (hourly.size() > 0) {
                    df.condition = hourly.get(0).getAsJsonObject()
                            .getAsJsonArray("weatherDesc").get(0).getAsJsonObject()
                            .get("value").getAsString();
                }

                // Chance of rain (max across hours)
                int maxRain = 0;
                for (JsonElement h : hourly) {
                    int r = h.getAsJsonObject().get("chanceofrain").getAsInt();
                    if (r > maxRain) maxRain = r;
                }
                df.chanceOfRain = maxRain;

                w.forecast.add(df);
            }

            return w;
        } catch (Exception e) {
            System.err.println("WeatherService.fetch failed: " + e.getMessage());
            return null;
        }
    }

    /** Map weather condition text to an emoji. */
    public static String mapConditionEmoji(String condition) {
        if (condition == null) return "🌤️";
        String c = condition.toLowerCase();
        if (c.contains("sunny") || c.contains("clear"))     return "☀️";
        if (c.contains("partly cloudy"))                      return "⛅";
        if (c.contains("cloud") || c.contains("overcast"))   return "☁️";
        if (c.contains("thunder") || c.contains("storm"))    return "⛈️";
        if (c.contains("rain") || c.contains("drizzle") || c.contains("shower")) return "🌧️";
        if (c.contains("snow") || c.contains("sleet") || c.contains("blizzard")) return "🌨️";
        if (c.contains("fog") || c.contains("mist") || c.contains("haze"))       return "🌫️";
        return "🌤️";
    }

    private static long parseSunlightMinutes(String sunrise, String sunset) {
        // Parse formats like "06:53 AM" and "05:47 PM"
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
        java.time.LocalTime rise = java.time.LocalTime.parse(sunrise.toUpperCase().trim(), fmt);
        java.time.LocalTime set = java.time.LocalTime.parse(sunset.toUpperCase().trim(), fmt);
        return java.time.Duration.between(rise, set).toMinutes();
    }
}
