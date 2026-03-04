package utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects the user's country based on their public IP address.
 * Uses ip-api.com (free, no API key required).
 * Caches the result after the first successful lookup.
 */
public class GeoLocationService {

    private static final AtomicReference<String> cachedCountryCode = new AtomicReference<>(null);
    private static final AtomicReference<String> cachedCountryName = new AtomicReference<>(null);
    private static final String DEFAULT_COUNTRY_CODE = "TN";
    private static final String DEFAULT_COUNTRY_NAME = "Tunisia";

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Gets the 2-letter ISO country code based on the user's IP.
     * Returns cached value if already resolved, or default "TN" on failure.
     */
    public static String getCountryCode() {
        String cached = cachedCountryCode.get();
        return cached != null ? cached : DEFAULT_COUNTRY_CODE;
    }

    /**
     * Gets the full country name based on the user's IP.
     */
    public static String getCountryName() {
        String cached = cachedCountryName.get();
        return cached != null ? cached : DEFAULT_COUNTRY_NAME;
    }

    /**
     * Asynchronously resolves the country from the user's public IP.
     * Call this once; subsequent calls to getCountryCode() use the cache.
     * If already resolved, returns immediately from cache.
     */
    public static CompletableFuture<String> resolveCountry() {
        // If already resolved, return immediately
        if (cachedCountryCode.get() != null) {
            return CompletableFuture.completedFuture(cachedCountryCode.get());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://ip-api.com/json?fields=status,countryCode,country"))
                .GET()
                .timeout(Duration.ofSeconds(8))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                            if ("success".equals(json.get("status").getAsString())) {
                                String code = json.get("countryCode").getAsString();
                                String name = json.get("country").getAsString();
                                cachedCountryCode.set(code);
                                cachedCountryName.set(name);
                                System.out.println("[GeoLocation] Detected country: " + name + " (" + code + ")");
                                return code;
                            }
                        } catch (Exception e) {
                            System.err.println("[GeoLocation] Parse error: " + e.getMessage());
                        }
                    }
                    return DEFAULT_COUNTRY_CODE;
                })
                .exceptionally(ex -> {
                    System.err.println("[GeoLocation] Failed to detect country: " + ex.getMessage());
                    return DEFAULT_COUNTRY_CODE;
                });
    }

    /**
     * Converts ISO 3166-1 alpha-2 country code to flag emoji.
     * Works by converting each letter to its regional indicator symbol.
     */
    public static String countryCodeToFlag(String code) {
        if (code == null || code.length() != 2) return "\uD83C\uDF10"; // 🌐 globe
        code = code.toUpperCase();
        int codePoint1 = 0x1F1E6 + (code.charAt(0) - 'A');
        int codePoint2 = 0x1F1E6 + (code.charAt(1) - 'A');
        return new String(Character.toChars(codePoint1)) + new String(Character.toChars(codePoint2));
    }
}
