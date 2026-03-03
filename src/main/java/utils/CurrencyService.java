package utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time currency conversion using the free ExchangeRate-API.
 * Endpoint: https://open.er-api.com/v6/latest/{base}
 *
 * Features:
 *   - No API key required (free tier, 1 500 req/month)
 *   - In-memory cache with 30-minute TTL per base currency
 *   - Thread-safe
 */
public class CurrencyService {

    private static final String API_URL = "https://open.er-api.com/v6/latest/";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

    /** Supported currencies shown in the converter UI. */
    public static final String[] CURRENCIES = {
            "USD", "EUR", "GBP", "TND", "JPY", "CAD", "AUD", "CHF", "CNY",
            "INR", "BRL", "KRW", "SEK", "NOK", "DKK", "PLN", "CZK", "TRY"
    };

    // ── Cache: baseCurrency → { rates map, timestamp } ──
    private static final Map<String, CachedRates> cache = new ConcurrentHashMap<>();

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // ════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════

    /**
     * Convert an amount from one currency to another using live rates.
     *
     * @param amount the amount in the source currency
     * @param from   source currency code (e.g. "USD")
     * @param to     target currency code (e.g. "EUR")
     * @return the converted amount, or -1 if conversion failed
     */
    public static double convert(double amount, String from, String to) {
        if (from == null || to == null) return -1;
        if (from.equalsIgnoreCase(to)) return amount;

        Map<String, Double> rates = getRates(from.toUpperCase());
        if (rates == null) return -1;

        Double rate = rates.get(to.toUpperCase());
        if (rate == null) return -1;

        return amount * rate;
    }

    /**
     * Get the exchange rate from one currency to another.
     *
     * @return the rate, or -1 if unavailable
     */
    public static double getRate(String from, String to) {
        if (from == null || to == null) return -1;
        if (from.equalsIgnoreCase(to)) return 1.0;

        Map<String, Double> rates = getRates(from.toUpperCase());
        if (rates == null) return -1;

        Double rate = rates.get(to.toUpperCase());
        return rate != null ? rate : -1;
    }

    /**
     * Format a converted amount with currency symbol.
     */
    public static String formatAmount(double amount, String currency) {
        if (amount < 0) return "N/A";
        String symbol = getSymbol(currency);
        if (amount >= 1_000_000) {
            return String.format("%s%.2fM", symbol, amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format("%s%,.0f", symbol, amount);
        } else {
            return String.format("%s%.2f", symbol, amount);
        }
    }

    /**
     * @return the currency symbol for common codes
     */
    public static String getSymbol(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase()) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            case "TND" -> "DT ";
            case "CNY" -> "¥";
            case "INR" -> "₹";
            case "KRW" -> "₩";
            case "BRL" -> "R$";
            case "TRY" -> "₺";
            case "CHF" -> "CHF ";
            case "CAD" -> "C$";
            case "AUD" -> "A$";
            case "SEK", "NOK", "DKK" -> "kr ";
            case "PLN" -> "zł ";
            case "CZK" -> "Kč ";
            default -> code + " ";
        };
    }

    /** Clear all cached rates. */
    public static void clearCache() {
        cache.clear();
    }

    // ════════════════════════════════════════════════════════
    //  INTERNAL
    // ════════════════════════════════════════════════════════

    private static Map<String, Double> getRates(String base) {
        CachedRates cached = cache.get(base);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return cached.rates;
        }

        // Fetch from API
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + base))
                    .header("User-Agent", "SynergyGig/1.0")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("[CurrencyService] HTTP " + resp.statusCode() + " for " + base);
                return null;
            }

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            String result = root.has("result") ? root.get("result").getAsString() : "";
            if (!"success".equals(result)) {
                System.err.println("[CurrencyService] API error for " + base);
                return null;
            }

            JsonObject ratesObj = root.getAsJsonObject("rates");
            Map<String, Double> rates = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : ratesObj.entrySet()) {
                rates.put(entry.getKey(), entry.getValue().getAsDouble());
            }

            cache.put(base, new CachedRates(rates, System.currentTimeMillis()));
            System.out.println("[CurrencyService] Fetched " + rates.size() + " rates for " + base);
            return rates;

        } catch (Exception e) {
            System.err.println("[CurrencyService] Error fetching rates for " + base + ": " + e.getMessage());
            // Return stale cache if available
            if (cached != null) return cached.rates;
            return null;
        }
    }

    private static class CachedRates {
        final Map<String, Double> rates;
        final long timestamp;

        CachedRates(Map<String, Double> rates, long timestamp) {
            this.rates = rates;
            this.timestamp = timestamp;
        }
    }
}
