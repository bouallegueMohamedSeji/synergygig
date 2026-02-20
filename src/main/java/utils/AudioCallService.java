package utils;

import javax.sound.sampled.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles audio capture, playback, and WebSocket streaming for voice calls.
 * Uses Java Sound API (javax.sound.sampled) for mic/speaker and
 * java.net.http.WebSocket for streaming to the relay server.
 */
public class AudioCallService {

    // Audio format: 16kHz, 16-bit, mono, signed, little-endian
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, false);
    private static final int BUFFER_SIZE = 640; // 20ms of audio at 16kHz 16-bit mono

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);

    private WebSocket webSocket;
    private TargetDataLine micLine;   // microphone input
    private SourceDataLine speakerLine; // speaker output
    private Thread captureThread;

    private Runnable onDisconnected; // callback when WS disconnects

    /**
     * Start the audio call: connect WebSocket, open mic + speaker, begin streaming.
     *
     * @param callId  The call ID from the server
     * @param userId  Current user's ID
     * @param onDisconnected  Callback when the connection drops
     */
    public void start(int callId, int userId, Runnable onDisconnected) {
        if (running.get()) return;
        this.onDisconnected = onDisconnected;
        running.set(true);

        try {
            AudioDeviceManager dm = AudioDeviceManager.getInstance();

            // Open speaker (playback) via device manager (uses selected device)
            try {
                speakerLine = dm.openSpeakerLine();
            } catch (LineUnavailableException e) {
                System.err.println("Speaker line not available: " + e.getMessage());
                running.set(false);
                return;
            }
            speakerLine.start();

            // Open microphone (capture) via device manager (uses selected device)
            try {
                micLine = dm.openMicLine();
            } catch (LineUnavailableException e) {
                System.err.println("Microphone line not available: " + e.getMessage());
                speakerLine.close();
                running.set(false);
                return;
            }
            micLine.start();

            // Build WebSocket URL
            String baseUrl = AppConfig.getRestBaseUrl(); // https://rest.benzaitsue.work.gd/api
            String wsBase = baseUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                    .replaceAll("/api$", "");
            String wsUrl = wsBase + "/ws/audio/" + callId + "/" + userId;

            System.out.println("Connecting to WebSocket: " + wsUrl);

            // Connect WebSocket
            HttpClient client = HttpClient.newHttpClient();
            webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                            // Play received audio with volume control
                            if (running.get() && speakerLine != null && speakerLine.isOpen()) {
                                byte[] audioData = new byte[data.remaining()];
                                data.get(audioData);
                                AudioDeviceManager.applyVolume(audioData, audioData.length,
                                        AudioDeviceManager.getInstance().getSpeakerVolume());
                                speakerLine.write(audioData, 0, audioData.length);
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            System.out.println("WebSocket closed: " + statusCode + " " + reason);
                            if (running.get()) {
                                running.set(false);
                                if (onDisconnected != null) onDisconnected.run();
                            }
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            System.err.println("WebSocket error: " + error.getMessage());
                            if (running.get()) {
                                running.set(false);
                                if (onDisconnected != null) onDisconnected.run();
                            }
                        }
                    })
                    .join();

            // Start capture thread — reads mic and sends via WebSocket
            captureThread = new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (running.get()) {
                    int read = micLine.read(buffer, 0, buffer.length);
                    if (read > 0 && !muted.get() && webSocket != null) {
                        // Apply mic volume before sending
                        AudioDeviceManager.applyVolume(buffer, read,
                                AudioDeviceManager.getInstance().getMicVolume());
                        try {
                            webSocket.sendBinary(ByteBuffer.wrap(buffer, 0, read), true).join();
                        } catch (Exception e) {
                            if (running.get()) {
                                System.err.println("Send error: " + e.getMessage());
                                break;
                            }
                        }
                    }
                }
            }, "audio-capture");
            captureThread.setDaemon(true);
            captureThread.start();

            System.out.println("Audio call started (callId=" + callId + ")");

        } catch (Exception e) {
            System.err.println("Failed to start audio call: " + e.getMessage());
            e.printStackTrace();
            stop();
        }
    }

    /** Stop the call: close WebSocket, mic, speaker. */
    public void stop() {
        running.set(false);

        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "call ended").join();
            } catch (Exception ignored) {}
            webSocket = null;
        }

        if (micLine != null) {
            try { micLine.stop(); micLine.close(); } catch (Exception ignored) {}
            micLine = null;
        }

        if (speakerLine != null) {
            try { speakerLine.drain(); speakerLine.stop(); speakerLine.close(); } catch (Exception ignored) {}
            speakerLine = null;
        }

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        System.out.println("Audio call stopped");
    }

    /** Toggle mute on/off. Returns new mute state. */
    public boolean toggleMute() {
        boolean newState = !muted.get();
        muted.set(newState);
        return newState;
    }

    public boolean isMuted() {
        return muted.get();
    }

    public boolean isRunning() {
        return running.get();
    }
}
