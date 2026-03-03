package utils;

import javafx.application.Platform;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Manages real-time audio buffering and speech-to-text transcription
 * for live calls. Accumulates PCM chunks, sends them to the Groq Whisper
 * API, optionally translates, and fires a callback with the result.
 *
 * <p>Typical usage:</p>
 * <pre>
 *   var mgr = new LiveTranscriptionManager();
 *   mgr.setTranscriptCallback((original, translated) -> updateSubtitle(original, translated));
 *   mgr.setTargetLanguage("en");
 *   mgr.start();
 *   // feed audio from AudioCallService:
 *   mgr.feedAudio(pcmBytes, length, true);  // true = local mic
 *   // ...later:
 *   mgr.stop();
 * </pre>
 */
public class LiveTranscriptionManager {

    // Buffer ~5s of 16kHz 16-bit mono = 160,000 bytes
    // Keeps us well under Groq's 20 RPM free-tier limit
    private static final int BUFFER_THRESHOLD = 160_000;

    private final GroqWhisperService whisperService = new GroqWhisperService();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean transcribing = new AtomicBoolean(false);

    // Separate buffers for local (mic) and remote (speaker) audio
    private final ByteArrayOutputStream localBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream remoteBuffer = new ByteArrayOutputStream();

    /**
     * Callback receives (originalText, translatedText).
     * translatedText is null if no translation needed.
     * Called on JavaFX thread.
     */
    private BiConsumer<TranscriptEntry, TranscriptEntry> transcriptCallback;

    /** Target language code for translation (e.g. "en", "fr"). Empty = no translation. */
    private String targetLanguage = "en";

    /** Whether to transcribe local mic audio (off by default to halve RPM) */
    private boolean transcribeLocal = false;

    /** Whether to transcribe remote audio */
    private boolean transcribeRemote = true;

    /**
     * Holds one transcription result with optional translation.
     */
    public static class TranscriptEntry {
        public final String originalText;
        public final String translatedText;
        public final String detectedLanguage;
        public final boolean isLocal;  // true = my mic, false = remote

        public TranscriptEntry(String originalText, String translatedText, String detectedLanguage, boolean isLocal) {
            this.originalText = originalText;
            this.translatedText = translatedText;
            this.detectedLanguage = detectedLanguage;
            this.isLocal = isLocal;
        }

        /** Returns translation if available, otherwise original text */
        public String getDisplayText() {
            return (translatedText != null && !translatedText.isBlank()) ? translatedText : originalText;
        }

        public boolean hasTranslation() {
            return translatedText != null && !translatedText.isBlank()
                    && !translatedText.equals(originalText);
        }
    }

    // ────────────────────────────────────────────────
    //  Config
    // ────────────────────────────────────────────────

    public void setTranscriptCallback(BiConsumer<TranscriptEntry, TranscriptEntry> callback) {
        this.transcriptCallback = callback;
    }

    public void setTargetLanguage(String lang) {
        this.targetLanguage = (lang == null) ? "" : lang.trim().toLowerCase();
    }

    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTranscribeLocal(boolean val) { this.transcribeLocal = val; }
    public void setTranscribeRemote(boolean val) { this.transcribeRemote = val; }
    public boolean isRunning() { return running.get(); }

    // ────────────────────────────────────────────────
    //  Lifecycle
    // ────────────────────────────────────────────────

    /** Start accepting audio and processing transcriptions. */
    public void start() {
        running.set(true);
        synchronized (localBuffer) { localBuffer.reset(); }
        synchronized (remoteBuffer) { remoteBuffer.reset(); }
        System.out.println("[Transcription] Started — target language: " + targetLanguage);
    }

    /** Stop and discard any buffered audio. */
    public void stop() {
        running.set(false);
        synchronized (localBuffer) { localBuffer.reset(); }
        synchronized (remoteBuffer) { remoteBuffer.reset(); }
        System.out.println("[Transcription] Stopped");
    }

    // ────────────────────────────────────────────────
    //  Audio feeding
    // ────────────────────────────────────────────────

    /**
     * Feed raw PCM audio data. Called from audio capture/playback threads.
     *
     * @param data    Raw PCM bytes (16 kHz, 16-bit, mono, LE)
     * @param length  Valid byte count
     * @param isLocal true = local microphone, false = remote speaker
     */
    public void feedAudio(byte[] data, int length, boolean isLocal) {
        if (!running.get()) return;
        if (isLocal && !transcribeLocal) return;
        if (!isLocal && !transcribeRemote) return;

        ByteArrayOutputStream buffer = isLocal ? localBuffer : remoteBuffer;

        synchronized (buffer) {
            buffer.write(data, 0, length);

            if (buffer.size() >= BUFFER_THRESHOLD) {
                byte[] pcm = buffer.toByteArray();
                buffer.reset();
                // Process in background
                boolean local = isLocal;
                AppThreadPool.io(() -> processChunk(pcm, pcm.length, local));
            }
        }
    }

    // ────────────────────────────────────────────────
    //  Processing
    // ────────────────────────────────────────────────

    /**
     * Transcribe a PCM chunk and fire the callback.
     * Runs on IO thread pool.
     */
    private void processChunk(byte[] pcm, int length, boolean isLocal) {
        if (!running.get()) return;

        try {
            // Step 1: Transcribe
            GroqWhisperService.TranscriptionResult result = whisperService.transcribe(pcm, length);

            if (result.isEmpty()) return;

            // Step 2: Translate if needed
            String translated = null;
            String detectedLang = result.language;

            if (!targetLanguage.isEmpty() && !detectedLang.isEmpty()
                    && !detectedLang.equalsIgnoreCase(targetLanguage)) {
                translated = whisperService.translate(result.text, detectedLang, targetLanguage);
            }

            // Step 3: Fire callback on FX thread
            TranscriptEntry entry = new TranscriptEntry(result.text, translated, detectedLang, isLocal);
            if (transcriptCallback != null) {
                Platform.runLater(() -> transcriptCallback.accept(entry, null));
            }

        } catch (Exception e) {
            System.err.println("[Transcription] Error processing chunk: " + e.getMessage());
        }
    }

    /**
     * Force-flush any buffered audio and transcribe it immediately.
     * Useful when ending a call to capture the last few seconds.
     */
    public void flush() {
        flushBuffer(localBuffer, true);
        flushBuffer(remoteBuffer, false);
    }

    private void flushBuffer(ByteArrayOutputStream buffer, boolean isLocal) {
        byte[] pcm;
        synchronized (buffer) {
            if (buffer.size() < 3200) return; // at least ~100ms of audio
            pcm = buffer.toByteArray();
            buffer.reset();
        }
        AppThreadPool.io(() -> processChunk(pcm, pcm.length, isLocal));
    }
}
