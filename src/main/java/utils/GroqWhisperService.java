package utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sends audio chunks to Groq's Whisper API for real-time speech-to-text.
 * <p>
 * Accepts raw PCM (16 kHz, 16-bit, mono, little-endian), wraps it in a
 * minimal WAV header, and POSTs it as multipart/form-data to the
 * {@code /openai/v1/audio/transcriptions} endpoint.
 * </p>
 */
public class GroqWhisperService {

    private static final String API_URL = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String MODEL = "whisper-large-v3-turbo";
    private static final Gson gson = new Gson();

    /** Minimum gap between Whisper API calls in ms (20 RPM ≈ 3s, use 4s for safety). */
    private static final long MIN_REQUEST_INTERVAL_MS = 4_000;
    /** Max retries on 429 before giving up on a chunk. */
    private static final int MAX_RETRIES = 2;

    private final HttpClient httpClient;
    private final String apiKey;

    /** Timestamp of the last Whisper API request (shared across threads). */
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    /** Consecutive 429 errors — drives exponential backoff. */
    private final AtomicInteger consecutive429 = new AtomicInteger(0);
    /** Whether we already logged a 429 (suppress repeats). */
    private volatile boolean logged429 = false;

    public GroqWhisperService() {
        this.apiKey = AppConfig.get("groq.api.key", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Result of a transcription request.
     */
    public static class TranscriptionResult {
        public final String text;
        public final String language;

        public TranscriptionResult(String text, String language) {
            this.text = text;
            this.language = language;
        }

        public boolean isEmpty() {
            return text == null || text.isBlank();
        }
    }

    /**
     * Transcribe raw PCM audio bytes.
     *
     * @param pcmData  Raw PCM: 16 kHz, 16-bit, mono, signed, little-endian
     * @param length   Number of valid bytes in pcmData
     * @return TranscriptionResult with text and detected language, or empty result on failure
     */
    /**
     * Wait until the rate limiter allows the next request.
     * Enforces MIN_REQUEST_INTERVAL_MS between calls, plus extra backoff on 429 streaks.
     */
    private void waitForRateLimit() {
        long backoffMs = MIN_REQUEST_INTERVAL_MS + consecutive429.get() * 3_000L;
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long wait = (last + backoffMs) - now;
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        lastRequestTime.set(System.currentTimeMillis());
    }

    public TranscriptionResult transcribe(byte[] pcmData, int length) {
        if (apiKey.isBlank()) {
            System.err.println("[Whisper] No Groq API key configured");
            return new TranscriptionResult("", "");
        }

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                waitForRateLimit();

                // Wrap PCM in WAV
                byte[] wav = pcmToWav(pcmData, length, 16000, 16, 1);

                // Build multipart body
                String boundary = "----WavBoundary" + UUID.randomUUID().toString().substring(0, 8);
                byte[] body = buildMultipartBody(boundary, wav);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    consecutive429.set(0);
                    logged429 = false;
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    String text = json.has("text") ? json.get("text").getAsString().trim() : "";
                    String lang = json.has("language") ? json.get("language").getAsString() : "";
                    return new TranscriptionResult(text, lang);
                } else if (response.statusCode() == 429) {
                    int streak = consecutive429.incrementAndGet();
                    if (!logged429) {
                        logged429 = true;
                        System.err.println("[Whisper] Rate limited (429) — backing off "
                                + (MIN_REQUEST_INTERVAL_MS / 1000 + streak * 3) + "s between requests");
                    }
                    // Let the loop retry after waitForRateLimit applies the new backoff
                    if (attempt < MAX_RETRIES) continue;
                    return new TranscriptionResult("", "");
                } else {
                    System.err.println("[Whisper] API error " + response.statusCode() + ": " + response.body());
                    return new TranscriptionResult("", "");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new TranscriptionResult("", "");
            } catch (Exception e) {
                System.err.println("[Whisper] Transcription failed: " + e.getMessage());
                return new TranscriptionResult("", "");
            }
        }
        return new TranscriptionResult("", "");
    }

    /**
     * Translate text from one language to English (or a target language)
     * using Groq's chat completion API.
     *
     * @param text           The text to translate
     * @param sourceLang     Detected source language code (e.g. "fr", "ar")
     * @param targetLang     Target language (e.g. "en", "fr")
     * @return Translated text, or original text on failure
     */
    public String translate(String text, String sourceLang, String targetLang) {
        if (apiKey.isBlank() || text.isBlank()) return text;
        if (sourceLang.equalsIgnoreCase(targetLang)) return text;

        try {
            String prompt = "Translate the following " + sourceLang + " text to " + targetLang
                    + ". Return ONLY the translation, no explanations:\n\n" + text;

            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", prompt);

            JsonObject body = new JsonObject();
            body.addProperty("model", "llama-3.3-70b-versatile");
            body.add("messages", gson.toJsonTree(new JsonObject[]{message}));
            body.addProperty("temperature", 0.1);
            body.addProperty("max_tokens", 512);

            // Fix: build proper messages array
            String jsonBody = "{\"model\":\"llama-3.3-70b-versatile\",\"messages\":[{\"role\":\"user\",\"content\":"
                    + gson.toJson(prompt) + "}],\"temperature\":0.1,\"max_tokens\":512}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                return json.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();
            } else {
                System.err.println("[Translate] API error " + response.statusCode() + ": " + response.body());
                return text;
            }

        } catch (Exception e) {
            System.err.println("[Translate] Failed: " + e.getMessage());
            return text;
        }
    }

    // ────────────────────────────────────────────────
    //  WAV encoding
    // ────────────────────────────────────────────────

    /**
     * Wraps raw PCM bytes in a standard RIFF/WAV header.
     */
    private static byte[] pcmToWav(byte[] pcmData, int pcmLength, int sampleRate, int bitsPerSample, int channels) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        // RIFF header
        header.put("RIFF".getBytes());
        header.putInt(36 + pcmLength);       // file size - 8
        header.put("WAVE".getBytes());
        // fmt sub-chunk
        header.put("fmt ".getBytes());
        header.putInt(16);                   // sub-chunk size (PCM)
        header.putShort((short) 1);          // audio format = PCM
        header.putShort((short) channels);
        header.putInt(sampleRate);
        header.putInt(byteRate);
        header.putShort((short) blockAlign);
        header.putShort((short) bitsPerSample);
        // data sub-chunk
        header.put("data".getBytes());
        header.putInt(pcmLength);

        byte[] wav = new byte[44 + pcmLength];
        System.arraycopy(header.array(), 0, wav, 0, 44);
        System.arraycopy(pcmData, 0, wav, 44, pcmLength);
        return wav;
    }

    // ────────────────────────────────────────────────
    //  Multipart form-data builder
    // ────────────────────────────────────────────────

    /**
     * Builds a multipart/form-data body for the Whisper API.
     * Fields: file (WAV), model, response_format, language (optional).
     */
    private byte[] buildMultipartBody(String boundary, byte[] wavData) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String CRLF = "\r\n";

        // ── file field ──
        bos.write(("--" + boundary + CRLF).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + CRLF).getBytes());
        bos.write(("Content-Type: audio/wav" + CRLF).getBytes());
        bos.write(CRLF.getBytes());
        bos.write(wavData);
        bos.write(CRLF.getBytes());

        // ── model field ──
        bos.write(("--" + boundary + CRLF).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"model\"" + CRLF).getBytes());
        bos.write(CRLF.getBytes());
        bos.write(MODEL.getBytes());
        bos.write(CRLF.getBytes());

        // ── response_format (verbose_json gives us the language) ──
        bos.write(("--" + boundary + CRLF).getBytes());
        bos.write(("Content-Disposition: form-data; name=\"response_format\"" + CRLF).getBytes());
        bos.write(CRLF.getBytes());
        bos.write("verbose_json".getBytes());
        bos.write(CRLF.getBytes());

        // ── closing boundary ──
        bos.write(("--" + boundary + "--" + CRLF).getBytes());
        return bos.toByteArray();
    }
}
