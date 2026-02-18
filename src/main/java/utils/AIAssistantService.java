package utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Client for the SynergyGig HR AI Assistant (Python Flask + Gemini).
 * <p>
 * Communicates with {@code http://localhost:5000/api/chat} to get
 * AI-generated responses for the "AI Assistant" chat room.
 * </p>
 *
 * <pre>
 *   AIAssistantService.chat(userId, "What questions should I ask for a Java interview?")
 *       .thenAccept(reply -> Platform.runLater(() -> showBubble(reply)));
 * </pre>
 */
public class AIAssistantService {

    private static final String BASE_URL = "http://localhost:5000";
    private static final Gson gson = new Gson();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Name of the special AI chat room. */
    public static final String AI_ROOM_NAME = "\uD83E\uDD16 AI Assistant";

    /**
     * Send a message to the AI assistant asynchronously.
     *
     * @param userId  current user's ID (for per-user memory)
     * @param message the user's question
     * @return a future that completes with the AI's reply text
     */
    public static CompletableFuture<String> chat(int userId, String message) {
        JsonObject body = new JsonObject();
        body.addProperty("userId", String.valueOf(userId));
        body.addProperty("message", message);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        return json.has("response") ? json.get("response").getAsString()
                                : "No response from AI.";
                    } else {
                        JsonObject err = gson.fromJson(response.body(), JsonObject.class);
                        String errMsg = err.has("error") ? err.get("error").getAsString()
                                : "HTTP " + response.statusCode();
                        return "⚠ AI Error: " + errMsg;
                    }
                })
                .exceptionally(ex -> "⚠ Could not reach AI assistant. Make sure the Python service is running.\n"
                        + "(python/hr_assistant.py on port 5000)");
    }

    /**
     * Check if the AI service is reachable.
     *
     * @return a future that resolves to true if healthy
     */
    public static CompletableFuture<Boolean> isHealthy() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/health"))
                .GET()
                .timeout(Duration.ofSeconds(3))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> r.statusCode() == 200)
                .exceptionally(ex -> false);
    }

    /**
     * Clear the AI's conversation memory for a user.
     */
    public static CompletableFuture<Void> clearHistory(int userId) {
        JsonObject body = new JsonObject();
        body.addProperty("userId", String.valueOf(userId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/clear"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .timeout(Duration.ofSeconds(5))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> null);
    }
}
