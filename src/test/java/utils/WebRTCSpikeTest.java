package utils;

import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.media.video.CustomVideoSource;
import dev.onvoid.webrtc.media.video.VideoDesktopSource;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrack;
import dev.onvoid.webrtc.media.video.VideoTrackSink;
import dev.onvoid.webrtc.media.video.NativeI420Buffer;
import dev.onvoid.webrtc.media.video.desktop.DesktopSource;
import dev.onvoid.webrtc.media.video.desktop.ScreenCapturer;
import dev.onvoid.webrtc.media.video.desktop.WindowCapturer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SPIKE TEST: Proves that dev.onvoid.webrtc can:
 *   A) Accept custom BufferedImage frames via CustomVideoSource.pushFrame()
 *   B) Use built-in VideoDesktopSource for screen/window capture
 *
 * This is the Phase 0 blocker for the WebRTC migration.
 * Run: mvn exec:java -D"exec.mainClass=utils.WebRTCSpikeTest"
 */
public class WebRTCSpikeTest {

    // ==================== TEST A: Custom Frame Push ====================

    /**
     * Converts a BufferedImage (any type) to I420 (YUV 4:2:0) NativeI420Buffer
     * for use with WebRTC's CustomVideoSource.
     *
     * I420 layout: Y plane (w*h), U plane (w/2 * h/2), V plane (w/2 * h/2)
     */
    public static NativeI420Buffer bufferedImageToI420(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // Ensure even dimensions (I420 requires it)
        int ew = (w % 2 == 0) ? w : w - 1;
        int eh = (h % 2 == 0) ? h : h - 1;

        NativeI420Buffer buffer = NativeI420Buffer.allocate(ew, eh);

        ByteBuffer yBuf = buffer.getDataY();
        ByteBuffer uBuf = buffer.getDataU();
        ByteBuffer vBuf = buffer.getDataV();

        int chromaW = ew / 2;

        for (int y = 0; y < eh; y++) {
            for (int x = 0; x < ew; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // BT.601 RGB to YUV conversion
                int yVal = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                yBuf.put(y * buffer.getStrideY() + x, (byte) clamp(yVal));

                // Subsample chroma: only compute for even coordinates
                if (y % 2 == 0 && x % 2 == 0) {
                    int uVal = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                    int vVal = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                    int ci = (y / 2) * buffer.getStrideU() + (x / 2);
                    uBuf.put(ci, (byte) clamp(uVal));
                    vBuf.put(ci, (byte) clamp(vVal));
                }
            }
        }

        return buffer;
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    /**
     * Test A: Capture screen with Robot, convert to I420, push into
     * CustomVideoSource, verify a VideoTrackSink receives the frame.
     */
    public static boolean testCustomVideoSource(PeerConnectionFactory factory) {
        System.out.println("\n=== TEST A: CustomVideoSource + Robot.createScreenCapture() ===");

        CustomVideoSource videoSource = new CustomVideoSource();
        VideoTrack videoTrack = factory.createVideoTrack("spike-custom", videoSource);

        // Track received frames
        CountDownLatch frameLatch = new CountDownLatch(3); // wait for 3 frames
        AtomicInteger receivedFrames = new AtomicInteger(0);
        AtomicReference<String> lastFrameInfo = new AtomicReference<>("none");

        VideoTrackSink sink = frame -> {
            int count = receivedFrames.incrementAndGet();
            lastFrameInfo.set(String.format("frame #%d: %dx%d ts=%dns",
                    count,
                    frame.buffer.getWidth(),
                    frame.buffer.getHeight(),
                    frame.timestampNs));
            System.out.println("  [SINK] " + lastFrameInfo.get());
            frameLatch.countDown();
        };
        videoTrack.addSink(sink);

        try {
            // Capture screen with Robot
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            // Use a smaller region for speed (top-left 640x480)
            Rectangle captureRect = new Rectangle(0, 0,
                    Math.min(640, screenRect.width),
                    Math.min(480, screenRect.height));

            System.out.println("  Capturing " + captureRect.width + "x" + captureRect.height + " from screen...");

            // Push 5 frames
            for (int i = 0; i < 5; i++) {
                BufferedImage screenshot = robot.createScreenCapture(captureRect);
                NativeI420Buffer i420 = bufferedImageToI420(screenshot);
                VideoFrame frame = new VideoFrame(i420, System.nanoTime());
                videoSource.pushFrame(frame);
                frame.release();
                Thread.sleep(66); // ~15fps
            }

            // Wait for sink to receive frames
            boolean received = frameLatch.await(5, TimeUnit.SECONDS);

            System.out.println("  Frames pushed: 5");
            System.out.println("  Frames received by sink: " + receivedFrames.get());
            System.out.println("  Last frame: " + lastFrameInfo.get());
            System.out.println("  TEST A RESULT: " + (received ? "PASS ✓" : "FAIL ✗"));

            return received;

        } catch (Exception e) {
            System.err.println("  TEST A ERROR: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            videoTrack.removeSink(sink);
            videoTrack.dispose();
            videoSource.dispose();
        }
    }

    // ==================== TEST B: Built-in Desktop Capture ====================

    /**
     * Test B: Use VideoDesktopSource to capture the screen natively
     * (no Robot needed). Verify frames arrive at a sink.
     */
    public static boolean testDesktopVideoSource(PeerConnectionFactory factory) {
        System.out.println("\n=== TEST B: VideoDesktopSource (built-in screen capture) ===");

        // List available screens
        ScreenCapturer screenCapturer = new ScreenCapturer();
        List<DesktopSource> screens = screenCapturer.getDesktopSources();
        System.out.println("  Available screens: " + screens.size());
        for (DesktopSource s : screens) {
            System.out.println("    Screen: \"" + s.title + "\" (id=" + s.id + ")");
        }
        screenCapturer.dispose();

        if (screens.isEmpty()) {
            System.out.println("  No screens found. TEST B RESULT: SKIP");
            return true; // not a hard failure
        }

        // List available windows
        WindowCapturer windowCapturer = new WindowCapturer();
        List<DesktopSource> windows = windowCapturer.getDesktopSources();
        System.out.println("  Available windows: " + windows.size());
        for (DesktopSource w : windows) {
            System.out.println("    Window: \"" + w.title + "\" (id=" + w.id + ")");
        }
        windowCapturer.dispose();

        // Create desktop video source using first screen
        VideoDesktopSource desktopSource = new VideoDesktopSource();
        desktopSource.setFrameRate(15);
        desktopSource.setMaxFrameSize(1280, 720);
        desktopSource.setSourceId(screens.get(0).id, false); // screen, not window

        VideoTrack videoTrack = factory.createVideoTrack("spike-desktop", desktopSource);

        CountDownLatch frameLatch = new CountDownLatch(3);
        AtomicInteger receivedFrames = new AtomicInteger(0);

        VideoTrackSink sink = frame -> {
            int count = receivedFrames.incrementAndGet();
            System.out.println("  [SINK] desktop frame #" + count + ": " +
                    frame.buffer.getWidth() + "x" + frame.buffer.getHeight());
            frameLatch.countDown();
        };
        videoTrack.addSink(sink);

        try {
            // Start capture
            desktopSource.start();
            System.out.println("  Desktop capture started, waiting for frames...");

            boolean received = frameLatch.await(10, TimeUnit.SECONDS);

            System.out.println("  Frames received by sink: " + receivedFrames.get());
            System.out.println("  TEST B RESULT: " + (received ? "PASS ✓" : "FAIL ✗"));

            return received;

        } catch (Exception e) {
            System.err.println("  TEST B ERROR: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            videoTrack.removeSink(sink);
            desktopSource.stop();
            videoTrack.dispose();
            desktopSource.dispose();
        }
    }

    // ==================== TEST C: Audio Device Enumeration ====================

    /**
     * Test C: Quick sanity check that the library can see audio devices.
     */
    public static boolean testAudioDevices() {
        System.out.println("\n=== TEST C: Audio Device Enumeration ===");
        try {
            var audioCapture = dev.onvoid.webrtc.media.MediaDevices.getAudioCaptureDevices();
            var audioRender  = dev.onvoid.webrtc.media.MediaDevices.getAudioRenderDevices();

            System.out.println("  Audio capture devices (mics): " + audioCapture.size());
            for (var d : audioCapture) {
                System.out.println("    Mic: " + d.getName());
            }
            System.out.println("  Audio render devices (speakers): " + audioRender.size());
            for (var d : audioRender) {
                System.out.println("    Speaker: " + d.getName());
            }

            boolean hasMic = !audioCapture.isEmpty();
            boolean hasSpeaker = !audioRender.isEmpty();
            System.out.println("  TEST C RESULT: " + ((hasMic && hasSpeaker) ? "PASS ✓" : "WARN (no devices)"));
            return hasMic && hasSpeaker;

        } catch (Exception e) {
            System.err.println("  TEST C ERROR: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== MAIN ====================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     WebRTC Spike Test — dev.onvoid.webrtc       ║");
        System.out.println("║     Phase 0 Blocker for WebRTC Migration        ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        PeerConnectionFactory factory = null;
        boolean allPassed = true;

        try {
            System.out.println("\nInitializing PeerConnectionFactory...");
            factory = new PeerConnectionFactory();
            System.out.println("  PeerConnectionFactory created successfully ✓");

            // Test A: Custom frame push (the critical spike question)
            boolean testA = testCustomVideoSource(factory);
            allPassed &= testA;

            // Test B: Built-in desktop capture
            boolean testB = testDesktopVideoSource(factory);
            allPassed &= testB;

            // Test C: Audio device enumeration
            boolean testC = testAudioDevices();
            // Don't block on audio (headless CI might not have devices)

        } catch (Exception e) {
            System.err.println("\nFATAL: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        } finally {
            if (factory != null) {
                factory.dispose();
                System.out.println("\nPeerConnectionFactory disposed.");
            }
        }

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║  SPIKE VERDICT: " + (allPassed
                ? "ALL TESTS PASSED ✓              ║"
                : "SOME TESTS FAILED ✗             ║"));
        System.out.println("╚══════════════════════════════════════════════════╝");

        System.exit(allPassed ? 0 : 1);
    }
}
