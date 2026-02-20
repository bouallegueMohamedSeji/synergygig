package utils;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Centralized sound manager for SynergyGig.
 * Handles loading, caching, and playing UI sound effects.
 * All preferences (enabled/disabled per category, master volume) are persisted.
 */
public class SoundManager {

    private static final SoundManager INSTANCE = new SoundManager();
    private final Preferences prefs = Preferences.userNodeForPackage(SoundManager.class);

    // ── Sound keys ──
    public static final String BUTTON_CLICK     = "button_click";
    public static final String CALL_CONNECTED   = "call_connected";
    public static final String CALL_ENDED       = "call_ended";
    public static final String ERROR            = "error";
    public static final String IMAGE_RECEIVED   = "image_received";
    public static final String INCOMING_CALL    = "incoming_call";
    public static final String MESSAGE_SENT     = "message_sent";
    public static final String NEW_MESSAGE      = "new_message";
    public static final String OUTGOING_CALL    = "outgoing_call";
    public static final String THEME_TOGGLE     = "theme_toggle";

    // ── Preference keys ──
    private static final String PREF_MASTER_VOLUME = "sound_master_volume";
    private static final String PREF_SOUNDS_ENABLED = "sounds_enabled";
    private static final String PREF_PREFIX = "sound_enabled_";

    // ── State ──
    private final Map<String, Media> mediaCache = new HashMap<>();
    private MediaPlayer loopingPlayer = null;
    private String loopingKey = null;

    private SoundManager() {
        // Preload all sounds
        String[] allSounds = {
            BUTTON_CLICK, CALL_CONNECTED, CALL_ENDED, ERROR,
            IMAGE_RECEIVED, INCOMING_CALL, MESSAGE_SENT,
            NEW_MESSAGE, OUTGOING_CALL, THEME_TOGGLE
        };
        for (String key : allSounds) {
            try {
                String path = "/sounds/" + key + ".mp3";
                var url = getClass().getResource(path);
                if (url != null) {
                    mediaCache.put(key, new Media(url.toExternalForm()));
                }
            } catch (Exception e) {
                System.err.println("SoundManager: failed to load " + key + ": " + e.getMessage());
            }
        }
    }

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    // ═══════════════════════════════════════════
    //  PLAY
    // ═══════════════════════════════════════════

    /** Play a sound once if enabled. */
    public void play(String soundKey) {
        if (!isSoundsEnabled()) return;
        if (!isSoundEnabled(soundKey)) return;

        Media media = mediaCache.get(soundKey);
        if (media == null) return;

        try {
            MediaPlayer player = new MediaPlayer(media);
            player.setVolume(getMasterVolume());
            player.setOnEndOfMedia(player::dispose);
            player.setOnError(player::dispose);
            player.play();
        } catch (Exception e) {
            // Silently ignore playback errors
        }
    }

    /** Play a sound in a loop (e.g., ringtone). Call stopLoop() to stop. */
    public void playLoop(String soundKey) {
        if (!isSoundsEnabled()) return;
        if (!isSoundEnabled(soundKey)) return;

        stopLoop(); // stop any previous loop

        Media media = mediaCache.get(soundKey);
        if (media == null) return;

        try {
            loopingPlayer = new MediaPlayer(media);
            loopingPlayer.setVolume(getMasterVolume());
            loopingPlayer.setCycleCount(MediaPlayer.INDEFINITE);
            loopingPlayer.setOnError(() -> {
                loopingPlayer.dispose();
                loopingPlayer = null;
                loopingKey = null;
            });
            loopingKey = soundKey;
            loopingPlayer.play();
        } catch (Exception e) {
            loopingPlayer = null;
            loopingKey = null;
        }
    }

    /** Stop the currently looping sound. */
    public void stopLoop() {
        if (loopingPlayer != null) {
            try {
                loopingPlayer.stop();
                loopingPlayer.dispose();
            } catch (Exception ignored) {}
            loopingPlayer = null;
            loopingKey = null;
        }
    }

    // ═══════════════════════════════════════════
    //  PREFERENCES
    // ═══════════════════════════════════════════

    /** Master on/off for all sounds. */
    public boolean isSoundsEnabled() {
        return prefs.getBoolean(PREF_SOUNDS_ENABLED, true);
    }

    public void setSoundsEnabled(boolean enabled) {
        prefs.putBoolean(PREF_SOUNDS_ENABLED, enabled);
        if (!enabled) stopLoop();
    }

    /** Per-sound enable/disable. */
    public boolean isSoundEnabled(String soundKey) {
        return prefs.getBoolean(PREF_PREFIX + soundKey, true);
    }

    public void setSoundEnabled(String soundKey, boolean enabled) {
        prefs.putBoolean(PREF_PREFIX + soundKey, enabled);
    }

    /** Master volume 0.0 to 1.0. */
    public double getMasterVolume() {
        return prefs.getDouble(PREF_MASTER_VOLUME, 0.7);
    }

    public void setMasterVolume(double volume) {
        prefs.putDouble(PREF_MASTER_VOLUME, Math.max(0, Math.min(1, volume)));
        // Update looping player volume in real-time
        if (loopingPlayer != null) {
            loopingPlayer.setVolume(volume);
        }
    }

    /** Get a human-friendly label for a sound key. */
    public static String getLabel(String soundKey) {
        switch (soundKey) {
            case BUTTON_CLICK:     return "Button Click";
            case CALL_CONNECTED:   return "Call Connected";
            case CALL_ENDED:       return "Call Ended";
            case ERROR:            return "Error / Validation";
            case IMAGE_RECEIVED:   return "Image Received";
            case INCOMING_CALL:    return "Incoming Call Ringtone";
            case MESSAGE_SENT:     return "Message Sent";
            case NEW_MESSAGE:      return "New Message";
            case OUTGOING_CALL:    return "Outgoing Call Ring";
            case THEME_TOGGLE:     return "Theme Toggle";
            default:               return soundKey;
        }
    }

    /** Get an emoji icon for a sound key. */
    public static String getIcon(String soundKey) {
        switch (soundKey) {
            case BUTTON_CLICK:     return "🖱";
            case CALL_CONNECTED:   return "✅";
            case CALL_ENDED:       return "📵";
            case ERROR:            return "⚠";
            case IMAGE_RECEIVED:   return "🖼";
            case INCOMING_CALL:    return "📞";
            case MESSAGE_SENT:     return "📤";
            case NEW_MESSAGE:      return "💬";
            case OUTGOING_CALL:    return "📲";
            case THEME_TOGGLE:     return "🎨";
            default:               return "🔊";
        }
    }

    /** All sound keys in display order. */
    public static String[] allSoundKeys() {
        return new String[] {
            NEW_MESSAGE, MESSAGE_SENT, IMAGE_RECEIVED,
            INCOMING_CALL, OUTGOING_CALL, CALL_CONNECTED, CALL_ENDED,
            ERROR, BUTTON_CLICK, THEME_TOGGLE
        };
    }
}
