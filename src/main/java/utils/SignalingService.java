package utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * WebRTC signaling client — connects to wss://<host>/ws/signal/{userId}
 * and relays JSON messages for:
 *   - "offer"      : SDP offer
 *   - "answer"     : SDP answer
 *   - "ice"        : ICE candidate
 *   - "call-state" : incoming-call, accepted, rejected, ended
 *
 * Replaces the 2-second HTTP polling used by pollIncomingCallsBackground().
 *
 * Thread safety: all callbacks are dispatched on the JavaFX Application Thread
 * via Platform.runLater() unless noted otherwise.
 *
 * Usage:
 *   SignalingService.getInstance().connect(userId);
 *   SignalingService.getInstance().onMessage("call-state", msg -> { ... });
 *   SignalingService.getInstance().send(targetUserId, "offer", sdpJson);
 *   SignalingService.getInstance().disconnect();
 */
public class SignalingService {

    // ═══════════════════════════════════════════
    //  SINGLETON
    // ═══════════════════════════════════════════

    private static final SignalingService INSTANCE = new SignalingService();
    public static SignalingService getInstance() { return INSTANCE; }
    private SignalingService() {}

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private WebSocket webSocket;
    private int currentUserId;

    /** Registered handlers: message type → list of callbacks(JsonObject) */
    private final ConcurrentHashMap<String, List<Consumer<JsonObject>>> handlers = new ConcurrentHashMap<>();

    /** Callback when connection drops unexpectedly. */
    private Runnable onDisconnected;

    private static final int RECONNECT_DELAY_MS = 3000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private int reconnectAttempts = 0;

    private static final Gson GSON = new Gson();

    // ═══════════════════════════════════════════
    //  CONNECT / DISCONNECT
    // ═══════════════════════════════════════════

    /**
     * Connect to the signaling WebSocket for the given user.
     * Safe to call multiple times — reconnects if already connected.
     */
    public void connect(int userId) {
        if (connected.get() && currentUserId == userId && webSocket != null) {
            System.out.println("[Signaling] Already connected for user " + userId);
            return;
        }

        // Disconnect any existing connection first
        disconnectQuietly();

        this.currentUserId = userId;
        reconnectAttempts = 0;

        doConnect();
    }

    private void doConnect() {
        try {
            String baseUrl = AppConfig.getRestBaseUrl(); // https://rest.benzaitsue.work.gd/api
            String wsBase = baseUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                    .replaceAll("/api$", "");
            String wsUrl = wsBase + "/ws/signal/" + currentUserId;

            System.out.println("[Signaling] Connecting to " + wsUrl);

            HttpClient client = HttpClient.newHttpClient();
            webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new SignalingListener())
                    .join();

            connected.set(true);
            reconnecting.set(false);
            reconnectAttempts = 0;
            System.out.println("[Signaling] Connected successfully for user " + currentUserId);

        } catch (Exception e) {
            System.err.println("[Signaling] Connection failed: " + e.getMessage());
            connected.set(false);
            scheduleReconnect();
        }
    }

    /** Disconnect gracefully. */
    public void disconnect() {
        reconnecting.set(false); // stop reconnect attempts
        reconnectAttempts = MAX_RECONNECT_ATTEMPTS; // prevent further reconnects
        disconnectQuietly();
    }

    private void disconnectQuietly() {
        connected.set(false);
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
            } catch (Exception ignored) {}
            webSocket = null;
        }
    }

    public boolean isConnected() { return connected.get(); }

    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }

    // ═══════════════════════════════════════════
    //  SEND MESSAGES
    // ═══════════════════════════════════════════

    /**
     * Send a signaling message to a target user.
     *
     * @param toUserId  target user ID
     * @param type      message type: "offer", "answer", "ice", "call-state"
     * @param data      payload (SDP, ICE candidate, or call state data)
     */
    public void send(int toUserId, String type, JsonObject data) {
        if (!connected.get() || webSocket == null) {
            System.err.println("[Signaling] Cannot send — not connected");
            return;
        }

        JsonObject msg = new JsonObject();
        msg.addProperty("type", type);
        msg.addProperty("from", currentUserId);
        msg.addProperty("to", toUserId);
        msg.add("data", data);

        String json = GSON.toJson(msg);

        try {
            webSocket.sendText(json, true).join();
        } catch (Exception e) {
            System.err.println("[Signaling] Send failed: " + e.getMessage());
        }
    }

    /**
     * Convenience: send a call state notification.
     * Used for incoming call alert, accepted, rejected, ended.
     */
    public void sendCallState(int toUserId, String state, int callId) {
        JsonObject data = new JsonObject();
        data.addProperty("state", state);
        data.addProperty("callId", callId);
        send(toUserId, "call-state", data);
    }

    /**
     * Convenience: send an SDP offer or answer.
     */
    public void sendSdp(int toUserId, String type, String sdp, String sdpType) {
        JsonObject data = new JsonObject();
        data.addProperty("sdp", sdp);
        data.addProperty("sdpType", sdpType);
        send(toUserId, type, data);
    }

    /**
     * Convenience: send an ICE candidate.
     */
    public void sendIce(int toUserId, String candidate, String sdpMid, int sdpMLineIndex) {
        JsonObject data = new JsonObject();
        data.addProperty("candidate", candidate);
        data.addProperty("sdpMid", sdpMid);
        data.addProperty("sdpMLineIndex", sdpMLineIndex);
        send(toUserId, "ice", data);
    }

    // ═══════════════════════════════════════════
    //  REGISTER MESSAGE HANDLERS
    // ═══════════════════════════════════════════

    /**
     * Register a handler for a specific message type.
     * Callback receives the full message JsonObject and runs on the FX thread.
     *
     * @param type     "offer", "answer", "ice", "call-state"
     * @param handler  callback
     */
    public void onMessage(String type, Consumer<JsonObject> handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Remove a specific handler for a message type. */
    public void removeHandler(String type, Consumer<JsonObject> handler) {
        List<Consumer<JsonObject>> list = handlers.get(type);
        if (list != null) {
            list.remove(handler);
            if (list.isEmpty()) handlers.remove(type);
        }
    }

    /** Remove all handlers for a specific message type. */
    public void removeHandler(String type) {
        handlers.remove(type);
    }

    /** Remove all handlers. */
    public void clearHandlers() {
        handlers.clear();
    }

    // ═══════════════════════════════════════════
    //  RECONNECT LOGIC
    // ═══════════════════════════════════════════

    private void scheduleReconnect() {
        if (reconnecting.get()) return;
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            System.err.println("[Signaling] Max reconnect attempts reached. Giving up.");
            if (onDisconnected != null) {
                Platform.runLater(onDisconnected);
            }
            return;
        }

        reconnecting.set(true);
        reconnectAttempts++;
        System.out.println("[Signaling] Reconnecting in " + RECONNECT_DELAY_MS + "ms (attempt " +
                reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");

        Thread reconnectThread = new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
                if (reconnecting.get()) {
                    doConnect();
                }
            } catch (InterruptedException ignored) {}
        }, "signaling-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    // ═══════════════════════════════════════════
    //  WEBSOCKET LISTENER
    // ═══════════════════════════════════════════

    private class SignalingListener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);

            if (last) {
                String fullMessage = textBuffer.toString();
                textBuffer.setLength(0);

                try {
                    JsonObject msg = JsonParser.parseString(fullMessage).getAsJsonObject();
                    String type = msg.has("type") ? msg.get("type").getAsString() : "unknown";

                    System.out.println("[Signaling] Received: type=" + type +
                            " from=" + (msg.has("from") ? msg.get("from").getAsInt() : "?"));

                    // Dispatch to registered handlers on FX thread
                    List<Consumer<JsonObject>> handlerList = handlers.get(type);
                    if (handlerList != null && !handlerList.isEmpty()) {
                        Platform.runLater(() -> {
                            for (Consumer<JsonObject> h : handlerList) {
                                try {
                                    h.accept(msg);
                                } catch (Exception ex) {
                                    System.err.println("[Signaling] Handler error for '" + type + "': " + ex.getMessage());
                                }
                            }
                        });
                    } else {
                        System.out.println("[Signaling] No handler for type: " + type);
                    }

                } catch (Exception e) {
                    System.err.println("[Signaling] Failed to parse message: " + e.getMessage());
                }
            }

            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            System.out.println("[Signaling] WebSocket closed: " + statusCode + " " + reason);
            connected.set(false);
            webSocket = null;

            // Attempt reconnect if not intentionally disconnected
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            System.err.println("[Signaling] WebSocket error: " + error.getMessage());
            connected.set(false);
            webSocket = null;

            // Attempt reconnect
            scheduleReconnect();
        }

        @Override
        public void onOpen(WebSocket ws) {
            System.out.println("[Signaling] WebSocket opened");
            ws.request(1); // request first message
        }
    }
}
