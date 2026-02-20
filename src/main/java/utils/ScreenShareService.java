package utils;

import javafx.application.Platform;
import javafx.scene.image.Image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Handles screen capture, JPEG compression, and WebSocket streaming
 * for live screen-sharing during voice calls.
 *
 * Architecture:
 *  - Both call participants connect() to /ws/video/{callId}/{userId}
 *  - Either side can startCapture() to begin sending frames
 *  - Incoming frames are decoded and delivered via the onFrame callback
 *  - Resolution, FPS, and JPEG quality are user-configurable
 */
public class ScreenShareService {

    private static final Preferences prefs = Preferences.userNodeForPackage(ScreenShareService.class);

    // ═══════════════════════════════════════════
    //  RESOLUTION PRESETS
    // ═══════════════════════════════════════════

    public enum Resolution {
        RES_1080P("1080p (1920×1080)", 1920, 1080),
        RES_720P("720p (1280×720)", 1280, 720),
        RES_480P("480p (854×480)", 854, 480),
        RES_360P("360p (640×360)", 640, 360);

        public final String label;
        public final int width;
        public final int height;

        Resolution(String label, int w, int h) {
            this.label = label;
            this.width = w;
            this.height = h;
        }

        @Override
        public String toString() { return label; }
    }

    // ═══════════════════════════════════════════
    //  STATE
    // ═══════════════════════════════════════════

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean capturing = new AtomicBoolean(false);

    private WebSocket webSocket;
    private Thread captureThread;
    private Robot robot;

    private Consumer<Image> onFrameReceived;
    private Runnable onDisconnected;

    // ── Settings ──
    private Resolution resolution;
    private int fps;
    private float jpegQuality;
    private Rectangle captureRegion = null; // null = full screen

    // ═══════════════════════════════════════════
    //  CONSTRUCTOR
    // ═══════════════════════════════════════════

    public ScreenShareService() {
        try {
            robot = new Robot();
        } catch (AWTException e) {
            System.err.println("Cannot create Robot for screen capture: " + e.getMessage());
        }
        loadPrefs();
    }

    private void loadPrefs() {
        String res = prefs.get("screen_resolution", "RES_720P");
        try { resolution = Resolution.valueOf(res); } catch (Exception e) { resolution = Resolution.RES_720P; }
        fps = prefs.getInt("screen_fps", 10);
        jpegQuality = prefs.getFloat("screen_quality", 0.5f);
    }

    private void savePrefs() {
        prefs.put("screen_resolution", resolution.name());
        prefs.putInt("screen_fps", fps);
        prefs.putFloat("screen_quality", jpegQuality);
    }

    // Getters & setters
    public Resolution getResolution() { return resolution; }
    public void setResolution(Resolution r) { this.resolution = r; savePrefs(); }

    public int getFps() { return fps; }
    public void setFps(int f) { this.fps = Math.max(1, Math.min(30, f)); savePrefs(); }

    public float getJpegQuality() { return jpegQuality; }
    public void setJpegQuality(float q) { this.jpegQuality = Math.max(0.1f, Math.min(1.0f, q)); savePrefs(); }

    public boolean isConnected() { return connected.get(); }
    public boolean isCapturing() { return capturing.get(); }

    /** Set a specific region to capture (for window/area sharing). */
    public void setCaptureRegion(Rectangle region) { this.captureRegion = region; }

    /** Clear capture region (back to full screen). */
    public void clearCaptureRegion() { this.captureRegion = null; }

    // ═══════════════════════════════════════════
    //  CONNECT — join the video relay channel
    // ═══════════════════════════════════════════

    /**
     * Connect to the video WebSocket relay for the given call.
     * Incoming frames are decoded and passed to onFrame on the FX thread.
     *
     * @param callId         The active call ID
     * @param userId         Current user ID
     * @param onFrame        Callback for each received video frame (FX thread)
     * @param onDisconnected Callback when the WebSocket disconnects
     */
    public void connect(int callId, int userId, Consumer<Image> onFrame, Runnable onDisconnected) {
        if (connected.get()) return;
        this.onFrameReceived = onFrame;
        this.onDisconnected = onDisconnected;

        try {
            String baseUrl = AppConfig.getRestBaseUrl();
            String wsBase = baseUrl
                    .replace("https://", "wss://")
                    .replace("http://", "ws://")
                    .replaceAll("/api$", "");
            String wsUrl = wsBase + "/ws/video/" + callId + "/" + userId;

            System.out.println("[ScreenShare] Connecting to: " + wsUrl);

            HttpClient client = HttpClient.newHttpClient();
            webSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                        // Accumulate fragments until 'last' flag is set
                        private final ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();

                        @Override
                        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                            byte[] bytes = new byte[data.remaining()];
                            data.get(bytes);
                            frameBuffer.write(bytes, 0, bytes.length);

                            if (last) {
                                byte[] frameData = frameBuffer.toByteArray();
                                frameBuffer.reset();
                                try {
                                    Image img = new Image(new ByteArrayInputStream(frameData));
                                    if (!img.isError() && onFrameReceived != null) {
                                        Platform.runLater(() -> onFrameReceived.accept(img));
                                    }
                                } catch (Exception e) {
                                    // Skip corrupted frames silently
                                }
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            System.out.println("[ScreenShare] WebSocket closed: " + statusCode);
                            handleDisconnect();
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            System.err.println("[ScreenShare] WebSocket error: " + error.getMessage());
                            handleDisconnect();
                        }
                    })
                    .join();

            connected.set(true);
            System.out.println("[ScreenShare] Connected to video relay (callId=" + callId + ")");

        } catch (Exception e) {
            System.err.println("[ScreenShare] Failed to connect: " + e.getMessage());
        }
    }

    private void handleDisconnect() {
        boolean wasActive = connected.getAndSet(false);
        capturing.set(false);
        if (wasActive && onDisconnected != null) {
            Platform.runLater(onDisconnected);
        }
    }

    // ═══════════════════════════════════════════
    //  CAPTURE — start/stop sending frames
    // ═══════════════════════════════════════════

    /** Start capturing and streaming the screen. */
    public void startCapture() {
        if (!connected.get() || capturing.get() || robot == null) return;
        capturing.set(true);

        captureThread = new Thread(this::captureLoop, "screen-capture");
        captureThread.setDaemon(true);
        captureThread.start();

        System.out.println("[ScreenShare] Capture started (" + resolution.label
                + ", " + fps + " fps, q=" + jpegQuality + ")");
    }

    /** Stop capturing (but keep WebSocket open for receiving). */
    public void stopCapture() {
        capturing.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        System.out.println("[ScreenShare] Capture stopped");
    }

    /** Disconnect entirely — close WebSocket, stop capture. */
    public void disconnect() {
        capturing.set(false);
        connected.set(false);

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped").join(); }
            catch (Exception ignored) {}
            webSocket = null;
        }

        System.out.println("[ScreenShare] Disconnected");
    }

    // ═══════════════════════════════════════════
    //  CAPTURE LOOP
    // ═══════════════════════════════════════════

    private void captureLoop() {
        long frameInterval = 1000 / fps;

        while (capturing.get()) {
            long start = System.currentTimeMillis();
            try {
                BufferedImage screenshot = captureScreen();
                if (screenshot != null) {
                    BufferedImage scaled = scaleToResolution(screenshot);
                    byte[] jpeg = compressJpeg(scaled);

                    if (webSocket != null && capturing.get()) {
                        // Send in 64KB chunks (WebSocket framing)
                        int chunkSize = 64 * 1024;
                        for (int offset = 0; offset < jpeg.length; offset += chunkSize) {
                            int len = Math.min(chunkSize, jpeg.length - offset);
                            boolean isLast = (offset + len) >= jpeg.length;
                            webSocket.sendBinary(ByteBuffer.wrap(jpeg, offset, len), isLast).join();
                        }
                    }
                }
            } catch (Exception e) {
                if (capturing.get()) {
                    System.err.println("[ScreenShare] Capture error: " + e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            long sleepTime = frameInterval - elapsed;
            if (sleepTime > 0) {
                try { Thread.sleep(sleepTime); } catch (InterruptedException e) { break; }
            }
        }
    }

    private BufferedImage captureScreen() {
        if (captureRegion != null) {
            return robot.createScreenCapture(captureRegion);
        }
        // Full primary screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return robot.createScreenCapture(new Rectangle(screenSize));
    }

    private BufferedImage scaleToResolution(BufferedImage source) {
        int targetW = resolution.width;
        int targetH = resolution.height;

        // Don't upscale
        if (source.getWidth() <= targetW && source.getHeight() <= targetH) {
            return source;
        }

        double scale = Math.min((double) targetW / source.getWidth(),
                                (double) targetH / source.getHeight());
        int newW = (int) (source.getWidth() * scale);
        int newH = (int) (source.getHeight() * scale);

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();
        return scaled;
    }

    private byte[] compressJpeg(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new RuntimeException("No JPEG writer found");

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(jpegQuality);

        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        writer.write(null, new IIOImage(image, null, null), param);
        ios.close();
        writer.dispose();
        return baos.toByteArray();
    }

    // ═══════════════════════════════════════════
    //  STATIC HELPERS
    // ═══════════════════════════════════════════

    /** Get all resolution presets. */
    public static Resolution[] allResolutions() {
        return Resolution.values();
    }
}
