package utils;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
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
 * Handles screen capture, webcam capture, JPEG compression, and WebSocket streaming
 * for live video during voice/video calls.
 *
 * Architecture:
 *  - Both call participants connect() to /ws/video/{callId}/{userId}
 *  - Either side can startCapture() to begin sending frames
 *  - Incoming frames are decoded and delivered via the onFrame callback
 *  - Supports two capture modes: SCREEN (Robot) and WEBCAM (camera)
 *  - Resolution, FPS, and JPEG quality are user-configurable
 */
public class ScreenShareService {

    private static final Preferences prefs = Preferences.userNodeForPackage(ScreenShareService.class);

    // ═══════════════════════════════════════════
    //  CAPTURE MODE
    // ═══════════════════════════════════════════

    public enum CaptureMode {
        SCREEN, WEBCAM
    }

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

    // ── Capture mode ──
    private CaptureMode captureMode = CaptureMode.SCREEN;

    // ── Webcam ──
    private Object webcam; // com.github.sarxos.webcam.Webcam
    private boolean webcamOpen = false;
    private Consumer<Image> onLocalFrame; // callback for local camera preview

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
        fps = prefs.getInt("screen_fps", 15);
        jpegQuality = prefs.getFloat("screen_quality", 0.6f);
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

    public CaptureMode getCaptureMode() { return captureMode; }
    public void setCaptureMode(CaptureMode mode) { this.captureMode = mode; }

    /** Set a specific region to capture (for window/area sharing). */
    public void setCaptureRegion(Rectangle region) { this.captureRegion = region; }

    /** Clear capture region (back to full screen). */
    public void clearCaptureRegion() { this.captureRegion = null; }

    /** Set callback for local camera preview frames (PiP). */
    public void setOnLocalFrame(Consumer<Image> onLocalFrame) { this.onLocalFrame = onLocalFrame; }

    // ═══════════════════════════════════════════
    //  WEBCAM ACCESS
    // ═══════════════════════════════════════════

    /** Open the default webcam. Returns true if successful. */
    public boolean openWebcam() {
        if (webcamOpen) return true;
        try {
            com.github.sarxos.webcam.Webcam cam = com.github.sarxos.webcam.Webcam.getDefault();
            if (cam == null) {
                System.err.println("[Video] No webcam detected");
                return false;
            }
            // Set resolution (try VGA, fall back to largest below 1280)
            java.awt.Dimension[] sizes = cam.getViewSizes();
            java.awt.Dimension best = null;
            for (java.awt.Dimension d : sizes) {
                if (d.width == 640 && d.height == 480) { best = d; break; }
                if (d.width <= 1280 && (best == null || d.width > best.width)) best = d;
            }
            if (best != null) cam.setViewSize(best);

            cam.open();
            webcam = cam;
            webcamOpen = true;
            System.out.println("[Video] Webcam opened: " + cam.getName()
                    + " (" + cam.getViewSize().width + "x" + cam.getViewSize().height + ")");
            return true;
        } catch (Exception e) {
            System.err.println("[Video] Failed to open webcam: " + e.getMessage());
            return false;
        }
    }

    /** Close the webcam. */
    public void closeWebcam() {
        if (webcam != null && webcamOpen) {
            try {
                ((com.github.sarxos.webcam.Webcam) webcam).close();
            } catch (Exception ignored) {}
            webcam = null;
            webcamOpen = false;
            System.out.println("[Video] Webcam closed");
        }
    }

    /** Check if a webcam is available on this system. */
    public static boolean isWebcamAvailable() {
        try {
            return com.github.sarxos.webcam.Webcam.getDefault() != null;
        } catch (Exception e) {
            return false;
        }
    }

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

        // Connect on a background thread to avoid blocking the FX thread
        Thread connectThread = new Thread(() -> {
            try {
                String baseUrl = AppConfig.getRestBaseUrl();
                String wsBase = baseUrl
                        .replace("https://", "wss://")
                        .replace("http://", "ws://")
                        .replaceAll("/api$", "");
                String wsUrl = wsBase + "/ws/video/" + callId + "/" + userId;

                System.out.println("[Video] Connecting to: " + wsUrl);

                HttpClient client = HttpClient.newHttpClient();
                webSocket = client.newWebSocketBuilder()
                        .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {

                            // Accumulate fragments until 'last' flag is set
                            private final ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
                            private long frameCount = 0;

                            @Override
                            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                                byte[] bytes = new byte[data.remaining()];
                                data.get(bytes);
                                frameBuffer.write(bytes, 0, bytes.length);

                                if (last) {
                                    byte[] frameData = frameBuffer.toByteArray();
                                    frameBuffer.reset();
                                    frameCount++;
                                    if (frameCount <= 3) {
                                        System.out.println("[Video] Received frame #" + frameCount + " (" + frameData.length + " bytes)");
                                    }
                                    try {
                                        Image img = new Image(new ByteArrayInputStream(frameData));
                                        if (!img.isError() && onFrameReceived != null) {
                                            Platform.runLater(() -> onFrameReceived.accept(img));
                                        } else if (img.isError()) {
                                            System.err.println("[Video] Frame decode error (" + frameData.length + " bytes)");
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[Video] Frame decode exception: " + e.getMessage());
                                    }
                                }
                                ws.request(1);
                                return null;
                            }

                            @Override
                            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                                System.out.println("[Video] WebSocket closed: " + statusCode + " " + reason);
                                handleDisconnect();
                                return null;
                            }

                            @Override
                            public void onError(WebSocket ws, Throwable error) {
                                System.err.println("[Video] WebSocket error: " + error.getMessage());
                                error.printStackTrace();
                                handleDisconnect();
                            }
                        })
                        .join();

                connected.set(true);
                System.out.println("[Video] Connected to video relay (callId=" + callId + ", userId=" + userId + ")");

            } catch (Exception e) {
                System.err.println("[Video] Failed to connect: " + e.getMessage());
                e.printStackTrace();
            }
        }, "video-ws-connect");
        connectThread.setDaemon(true);
        connectThread.start();
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

    /** Start capturing and streaming (screen or webcam based on captureMode). */
    public void startCapture() {
        if (capturing.get()) return;

        // Wait up to 5 seconds for WebSocket connection (connection is async now)
        if (!connected.get()) {
            System.out.println("[Video] Waiting for WebSocket connection before starting capture...");
            for (int i = 0; i < 50 && !connected.get(); i++) {
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
            if (!connected.get()) {
                System.err.println("[Video] Cannot start capture — WebSocket not connected after 5s");
                return;
            }
        }

        if (captureMode == CaptureMode.WEBCAM) {
            if (!openWebcam()) {
                System.err.println("[Video] Cannot start webcam capture — no camera");
                return;
            }
        } else {
            if (robot == null) return;
        }

        capturing.set(true);

        captureThread = new Thread(this::captureLoop, "video-capture");
        captureThread.setDaemon(true);
        captureThread.start();

        String modeStr = captureMode == CaptureMode.WEBCAM ? "webcam" : "screen";
        System.out.println("[Video] Capture started (" + modeStr + ", "
                + resolution.label + ", " + fps + " fps, q=" + jpegQuality + ")");
    }

    /** Stop capturing (but keep WebSocket open for receiving). */
    public void stopCapture() {
        capturing.set(false);
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        if (captureMode == CaptureMode.WEBCAM) {
            closeWebcam();
        }
        System.out.println("[Video] Capture stopped");
    }

    /** Disconnect entirely — close WebSocket, stop capture, close webcam. */
    public void disconnect() {
        capturing.set(false);
        connected.set(false);

        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }

        closeWebcam();

        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "stopped").join(); }
            catch (Exception ignored) {}
            webSocket = null;
        }

        System.out.println("[Video] Disconnected");
    }

    // ═══════════════════════════════════════════
    //  CAPTURE LOOP
    // ═══════════════════════════════════════════

    private void captureLoop() {
        long frameInterval = 1000 / fps;

        while (capturing.get()) {
            long start = System.currentTimeMillis();
            try {
                BufferedImage frame = (captureMode == CaptureMode.WEBCAM)
                        ? captureWebcam()
                        : captureScreen();

                if (frame != null) {
                    BufferedImage scaled = scaleToResolution(frame);

                    // Deliver local preview on FX thread (for webcam PiP)
                    if (captureMode == CaptureMode.WEBCAM && onLocalFrame != null) {
                        Image fxImage = SwingFXUtils.toFXImage(scaled, null);
                        Platform.runLater(() -> onLocalFrame.accept(fxImage));
                    }

                    byte[] jpeg = compressJpeg(scaled);

                    if (webSocket != null && capturing.get()) {
                        // Send entire JPEG as one complete WebSocket message
                        // (avoids fragmentation issues through proxies)
                        webSocket.sendBinary(ByteBuffer.wrap(jpeg), true).join();
                    }
                }
            } catch (Exception e) {
                if (capturing.get()) {
                    System.err.println("[Video] Capture error: " + e.getMessage());
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            long sleepTime = frameInterval - elapsed;
            if (sleepTime > 0) {
                try { Thread.sleep(sleepTime); } catch (InterruptedException e) { break; }
            }
        }
    }

    private BufferedImage captureWebcam() {
        if (webcam == null || !webcamOpen) return null;
        try {
            return ((com.github.sarxos.webcam.Webcam) webcam).getImage();
        } catch (Exception e) {
            return null;
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
