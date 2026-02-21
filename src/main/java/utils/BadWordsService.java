package utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Content moderation using the APILayer Bad Words API.
 * Checks text for profanity and returns a censored version if needed.
 */
public class BadWordsService {

    private static final String API_URL = "https://api.apilayer.com/bad_words";
    private static final String API_KEY = "YgbeLs1Let62752r83R0gWVaXdhZMBPj";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Result of a bad words check.
     */
    public static class CheckResult {
        public final boolean hasBadWords;
        public final int badWordsCount;
        public final String censoredContent;
        public final String originalContent;

        public CheckResult(boolean hasBadWords, int badWordsCount, String censoredContent, String originalContent) {
            this.hasBadWords = hasBadWords;
            this.badWordsCount = badWordsCount;
            this.censoredContent = censoredContent;
            this.originalContent = originalContent;
        }
    }

    /**
     * Check the given text for bad words using the API.
     * Returns a CheckResult with censored content if bad words are found.
     * If the API call fails, returns a result indicating no bad words (fail-open).
     */
    public static CheckResult check(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new CheckResult(false, 0, text, text);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "?censor_character=*"))
                    .header("apikey", API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                int badWordsTotal = json.has("bad_words_total") ? json.get("bad_words_total").getAsInt() : 0;
                String censored = json.has("censored_content") ? json.get("censored_content").getAsString() : text;

                return new CheckResult(badWordsTotal > 0, badWordsTotal, censored, text);
            } else {
                System.err.println("Bad Words API error: " + resp.statusCode() + " - " + resp.body());
                // Fail-open: allow the content if API is down
                return new CheckResult(false, 0, text, text);
            }
        } catch (Exception e) {
            System.err.println("Bad Words API call failed: " + e.getMessage());
            // Fail-open: allow the content if API is unreachable
            return new CheckResult(false, 0, text, text);
        }
    }

    /**
     * Quick convenience: returns true if the text contains bad words.
     */
    public static boolean containsBadWords(String text) {
        return check(text).hasBadWords;
    }
}
