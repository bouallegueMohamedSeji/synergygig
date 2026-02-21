package utils;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Centralized sound manager for SynergyGig.
 * Handles loading, caching, and playing UI sound effects.
 * Supports multiple sound variants per category with user-selectable preference.
 * All preferences (enabled/disabled per category, master volume, variant) are persisted.
 */
public class SoundManager {

    private final Preferences prefs = Preferences.userNodeForPackage(SoundManager.class);

    // ── Sound keys ──
    // Original
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

    // Notifications & Alerts
    public static final String NOTIFICATION_POP    = "notification_pop";
    public static final String NOTIFICATION_CLEAR  = "notification_clear";
    public static final String URGENT_ALERT        = "urgent_alert";
    public static final String REMINDER_CHIME      = "reminder_chime";

    // Tasks & Projects
    public static final String TASK_CREATED        = "task_created";
    public static final String TASK_COMPLETED      = "task_completed";
    public static final String TASK_SUBMITTED      = "task_submitted";
    public static final String TASK_MOVED          = "task_moved";
    public static final String TASK_DELETED        = "task_deleted";
    public static final String PROJECT_CREATED     = "project_created";
    public static final String PROJECT_DELETED     = "project_deleted";
    public static final String STAR_RATING         = "star_rating";
    public static final String REVIEW_SUBMITTED    = "review_submitted";
    public static final String REVIEW_APPROVED     = "review_approved";
    public static final String REVIEW_REVISION     = "review_revision";
    public static final String DEADLINE_WARNING    = "deadline_warning";

    // Team & HR
    public static final String MEMBER_ADDED        = "member_added";
    public static final String MEMBER_REMOVED      = "member_removed";
    public static final String ATTENDANCE_CHECKIN   = "attendance_checkin";
    public static final String ATTENDANCE_CHECKOUT  = "attendance_checkout";
    public static final String LEAVE_REQUESTED     = "leave_requested";
    public static final String LEAVE_APPROVED      = "leave_approved";
    public static final String LEAVE_REJECTED      = "leave_rejected";
    public static final String PAYROLL_GENERATED   = "payroll_generated";
    public static final String INTERVIEW_SCHEDULED = "interview_scheduled";
    public static final String INTERVIEW_ACCEPTED  = "interview_accepted";
    public static final String INTERVIEW_REJECTED  = "interview_rejected";

    // Communication
    public static final String TYPING_INDICATOR    = "typing_indicator";
    public static final String VOICE_CALL_RING     = "voice_call_ring";
    public static final String VIDEO_CALL_RING     = "video_call_ring";
    public static final String MISSED_CALL         = "missed_call";
    public static final String SCREEN_SHARE_START  = "screen_share_start";
    public static final String SCREEN_SHARE_END    = "screen_share_end";
    public static final String FILE_UPLOADED       = "file_uploaded";
    public static final String EMOJI_POP           = "emoji_pop";

    // Authentication & Navigation
    public static final String LOGIN_SUCCESS       = "login_success";
    public static final String LOGIN_FAILED        = "login_failed";
    public static final String LOGOUT              = "logout";
    public static final String FACE_RECOGNIZED     = "face_recognized";
    public static final String FACE_FAILED         = "face_failed";
    public static final String SIDEBAR_TOGGLE      = "sidebar_toggle";
    public static final String TAB_SWITCH          = "tab_switch";
    public static final String SEARCH_OPEN         = "search_open";

    // AI & System
    public static final String AI_THINKING         = "ai_thinking";
    public static final String AI_COMPLETE         = "ai_complete";
    public static final String WEATHER_REFRESH     = "weather_refresh";
    public static final String EXPORT_COMPLETE     = "export_complete";
    public static final String SETTINGS_SAVED      = "settings_saved";
    public static final String APP_STARTUP         = "app_startup";
    public static final String APP_MINIMIZE        = "app_minimize";

    // ── Preference keys ──
    private static final String PREF_MASTER_VOLUME  = "sound_master_volume";
    private static final String PREF_SOUNDS_ENABLED = "sounds_enabled";
    private static final String PREF_PREFIX         = "sound_enabled_";
    private static final String PREF_VARIANT_PREFIX = "sound_variant_";

    /**
     * Variant definitions: soundKey → list of resource suffixes.
     * The first entry is always the default (base file, suffix = "").
     */
    private static final Map<String, List<String>> VARIANTS = new LinkedHashMap<>();
    static {
        // Original
        VARIANTS.put(BUTTON_CLICK,       List.of("", "_v2", "_v3", "_v4"));
        VARIANTS.put(CALL_CONNECTED,     List.of(""));
        VARIANTS.put(CALL_ENDED,         List.of(""));
        VARIANTS.put(ERROR,              List.of("", "_v2", "_v3"));
        VARIANTS.put(IMAGE_RECEIVED,     List.of(""));
        VARIANTS.put(INCOMING_CALL,      List.of("", "_v2", "_v3"));
        VARIANTS.put(MESSAGE_SENT,       List.of(""));
        VARIANTS.put(NEW_MESSAGE,        List.of("", "_v2", "_v3"));
        VARIANTS.put(OUTGOING_CALL,      List.of("", "_v2", "_v3"));
        VARIANTS.put(THEME_TOGGLE,       List.of(""));
        // Notifications
        VARIANTS.put(NOTIFICATION_POP,   List.of("", "_v2", "_v3"));
        VARIANTS.put(NOTIFICATION_CLEAR, List.of(""));
        VARIANTS.put(URGENT_ALERT,       List.of(""));
        VARIANTS.put(REMINDER_CHIME,     List.of(""));
        // Tasks & Projects
        VARIANTS.put(TASK_CREATED,       List.of("", "_v2"));
        VARIANTS.put(TASK_COMPLETED,     List.of("", "_v2", "_v3"));
        VARIANTS.put(TASK_SUBMITTED,     List.of(""));
        VARIANTS.put(TASK_MOVED,         List.of(""));
        VARIANTS.put(TASK_DELETED,       List.of(""));
        VARIANTS.put(PROJECT_CREATED,    List.of(""));
        VARIANTS.put(PROJECT_DELETED,    List.of(""));
        VARIANTS.put(STAR_RATING,        List.of("", "_v2"));
        VARIANTS.put(REVIEW_SUBMITTED,   List.of(""));
        VARIANTS.put(REVIEW_APPROVED,    List.of(""));
        VARIANTS.put(REVIEW_REVISION,    List.of(""));
        VARIANTS.put(DEADLINE_WARNING,   List.of(""));
        // Team & HR
        VARIANTS.put(MEMBER_ADDED,       List.of(""));
        VARIANTS.put(MEMBER_REMOVED,     List.of(""));
        VARIANTS.put(ATTENDANCE_CHECKIN,  List.of(""));
        VARIANTS.put(ATTENDANCE_CHECKOUT, List.of(""));
        VARIANTS.put(LEAVE_REQUESTED,    List.of(""));
        VARIANTS.put(LEAVE_APPROVED,     List.of(""));
        VARIANTS.put(LEAVE_REJECTED,     List.of(""));
        VARIANTS.put(PAYROLL_GENERATED,  List.of(""));
        VARIANTS.put(INTERVIEW_SCHEDULED,List.of(""));
        VARIANTS.put(INTERVIEW_ACCEPTED, List.of(""));
        VARIANTS.put(INTERVIEW_REJECTED, List.of(""));
        // Communication
        VARIANTS.put(TYPING_INDICATOR,   List.of(""));
        VARIANTS.put(VOICE_CALL_RING,    List.of("", "_v2"));
        VARIANTS.put(VIDEO_CALL_RING,    List.of("", "_v2"));
        VARIANTS.put(MISSED_CALL,        List.of(""));
        VARIANTS.put(SCREEN_SHARE_START, List.of(""));
        VARIANTS.put(SCREEN_SHARE_END,   List.of(""));
        VARIANTS.put(FILE_UPLOADED,      List.of(""));
        VARIANTS.put(EMOJI_POP,          List.of("", "_v2"));
        // Auth & Navigation
        VARIANTS.put(LOGIN_SUCCESS,      List.of(""));
        VARIANTS.put(LOGIN_FAILED,       List.of(""));
        VARIANTS.put(LOGOUT,             List.of(""));
        VARIANTS.put(FACE_RECOGNIZED,    List.of(""));
        VARIANTS.put(FACE_FAILED,        List.of(""));
        VARIANTS.put(SIDEBAR_TOGGLE,     List.of(""));
        VARIANTS.put(TAB_SWITCH,         List.of(""));
        VARIANTS.put(SEARCH_OPEN,        List.of(""));
        // AI & System
        VARIANTS.put(AI_THINKING,        List.of(""));
        VARIANTS.put(AI_COMPLETE,        List.of(""));
        VARIANTS.put(WEATHER_REFRESH,    List.of(""));
        VARIANTS.put(EXPORT_COMPLETE,    List.of(""));
        VARIANTS.put(SETTINGS_SAVED,     List.of(""));
        VARIANTS.put(APP_STARTUP,        List.of(""));
        VARIANTS.put(APP_MINIMIZE,       List.of(""));
    }

    // Singleton – must come AFTER VARIANTS so the constructor can iterate it
    private static final SoundManager INSTANCE = new SoundManager();

    // ── State ──
    private final Map<String, Media> mediaCache = new HashMap<>();
    private MediaPlayer loopingPlayer = null;
    private String loopingKey = null;

    private SoundManager() {
        // Preload all sounds and their variants
        for (var entry : VARIANTS.entrySet()) {
            String key = entry.getKey();
            for (String suffix : entry.getValue()) {
                String fileKey = key + suffix;           // e.g. "button_click_v2"
                try {
                    String path = "/sounds/" + fileKey + ".mp3";
                    var url = getClass().getResource(path);
                    if (url != null) {
                        mediaCache.put(fileKey, new Media(url.toExternalForm()));
                    }
                } catch (Exception e) {
                    System.err.println("SoundManager: failed to load " + fileKey + ": " + e.getMessage());
                }
            }
        }
    }

    public static SoundManager getInstance() {
        return INSTANCE;
    }

    // ═══════════════════════════════════════════
    //  VARIANTS
    // ═══════════════════════════════════════════

    /** Returns the list of variant suffixes for a sound key (e.g. ["", "_v2", "_v3"]). */
    public static List<String> getVariants(String soundKey) {
        return VARIANTS.getOrDefault(soundKey, List.of(""));
    }

    /** Whether a sound key has more than one variant. */
    public static boolean hasVariants(String soundKey) {
        return getVariants(soundKey).size() > 1;
    }

    /** Get the currently selected variant suffix (persisted). */
    public String getSelectedVariant(String soundKey) {
        return prefs.get(PREF_VARIANT_PREFIX + soundKey, "");
    }

    /** Set the chosen variant suffix for a sound key. */
    public void setSelectedVariant(String soundKey, String suffix) {
        prefs.put(PREF_VARIANT_PREFIX + soundKey, suffix);
    }

    /** User-friendly label for a variant. */
    public static String variantLabel(String suffix) {
        if (suffix == null || suffix.isEmpty()) return "Default";
        // "_v2" → "Sound 2", "_v3" → "Sound 3", etc.
        return "Sound " + suffix.replace("_v", "");
    }

    /** Resolve the actual media-cache key taking the chosen variant into account. */
    private String resolveKey(String soundKey) {
        String suffix = getSelectedVariant(soundKey);
        String resolved = soundKey + suffix;
        return mediaCache.containsKey(resolved) ? resolved : soundKey; // fallback to default
    }

    // ═══════════════════════════════════════════
    //  PLAY
    // ═══════════════════════════════════════════

    /** Play a sound once if enabled (uses selected variant). */
    public void play(String soundKey) {
        if (!isSoundsEnabled()) return;
        if (!isSoundEnabled(soundKey)) return;

        Media media = mediaCache.get(resolveKey(soundKey));
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

    /** Play a specific variant directly (for preview). Always plays regardless of enable state. */
    public void playVariant(String soundKey, String suffix) {
        String fileKey = soundKey + (suffix != null ? suffix : "");
        Media media = mediaCache.get(fileKey);
        if (media == null) media = mediaCache.get(soundKey);
        if (media == null) return;
        try {
            MediaPlayer player = new MediaPlayer(media);
            player.setVolume(getMasterVolume());
            player.setOnEndOfMedia(player::dispose);
            player.setOnError(player::dispose);
            player.play();
        } catch (Exception ignored) {}
    }

    /** Play a sound in a loop (e.g., ringtone). Call stopLoop() to stop. */
    public void playLoop(String soundKey) {
        if (!isSoundsEnabled()) return;
        if (!isSoundEnabled(soundKey)) return;

        stopLoop(); // stop any previous loop

        Media media = mediaCache.get(resolveKey(soundKey));
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
            case BUTTON_CLICK:         return "Button Click";
            case CALL_CONNECTED:       return "Call Connected";
            case CALL_ENDED:           return "Call Ended";
            case ERROR:                return "Error / Validation";
            case IMAGE_RECEIVED:       return "Image Received";
            case INCOMING_CALL:        return "Incoming Call Ringtone";
            case MESSAGE_SENT:         return "Message Sent";
            case NEW_MESSAGE:          return "New Message";
            case OUTGOING_CALL:        return "Outgoing Call Ring";
            case THEME_TOGGLE:         return "Theme Toggle";
            // Notifications
            case NOTIFICATION_POP:     return "Notification Pop";
            case NOTIFICATION_CLEAR:   return "Notification Clear";
            case URGENT_ALERT:         return "Urgent Alert";
            case REMINDER_CHIME:       return "Reminder Chime";
            // Tasks & Projects
            case TASK_CREATED:         return "Task Created";
            case TASK_COMPLETED:       return "Task Completed";
            case TASK_SUBMITTED:       return "Task Submitted";
            case TASK_MOVED:           return "Task Moved";
            case TASK_DELETED:         return "Task Deleted";
            case PROJECT_CREATED:      return "Project Created";
            case PROJECT_DELETED:      return "Project Deleted";
            case STAR_RATING:          return "Star Rating";
            case REVIEW_SUBMITTED:     return "Review Submitted";
            case REVIEW_APPROVED:      return "Review Approved";
            case REVIEW_REVISION:      return "Review – Needs Revision";
            case DEADLINE_WARNING:     return "Deadline Warning";
            // Team & HR
            case MEMBER_ADDED:         return "Member Added";
            case MEMBER_REMOVED:       return "Member Removed";
            case ATTENDANCE_CHECKIN:   return "Attendance Check-In";
            case ATTENDANCE_CHECKOUT:  return "Attendance Check-Out";
            case LEAVE_REQUESTED:      return "Leave Requested";
            case LEAVE_APPROVED:       return "Leave Approved";
            case LEAVE_REJECTED:       return "Leave Rejected";
            case PAYROLL_GENERATED:    return "Payroll Generated";
            case INTERVIEW_SCHEDULED:  return "Interview Scheduled";
            case INTERVIEW_ACCEPTED:   return "Interview Accepted";
            case INTERVIEW_REJECTED:   return "Interview Rejected";
            // Communication
            case TYPING_INDICATOR:     return "Typing Indicator";
            case VOICE_CALL_RING:      return "Voice Call Ring";
            case VIDEO_CALL_RING:      return "Video Call Ring";
            case MISSED_CALL:          return "Missed Call";
            case SCREEN_SHARE_START:   return "Screen Share Start";
            case SCREEN_SHARE_END:     return "Screen Share End";
            case FILE_UPLOADED:        return "File Uploaded";
            case EMOJI_POP:            return "Emoji Pop";
            // Auth & Nav
            case LOGIN_SUCCESS:        return "Login Success";
            case LOGIN_FAILED:         return "Login Failed";
            case LOGOUT:               return "Logout";
            case FACE_RECOGNIZED:      return "Face Recognized";
            case FACE_FAILED:          return "Face Failed";
            case SIDEBAR_TOGGLE:       return "Sidebar Toggle";
            case TAB_SWITCH:           return "Tab Switch";
            case SEARCH_OPEN:          return "Search Open";
            // AI & System
            case AI_THINKING:          return "AI Thinking";
            case AI_COMPLETE:          return "AI Complete";
            case WEATHER_REFRESH:      return "Weather Refresh";
            case EXPORT_COMPLETE:      return "Export Complete";
            case SETTINGS_SAVED:       return "Settings Saved";
            case APP_STARTUP:          return "App Startup";
            case APP_MINIMIZE:         return "App Minimize";
            default:                   return soundKey;
        }
    }

    /** Get an emoji icon for a sound key. */
    public static String getIcon(String soundKey) {
        switch (soundKey) {
            case BUTTON_CLICK:         return "🖱";
            case CALL_CONNECTED:       return "✅";
            case CALL_ENDED:           return "📵";
            case ERROR:                return "⚠";
            case IMAGE_RECEIVED:       return "🖼";
            case INCOMING_CALL:        return "📞";
            case MESSAGE_SENT:         return "📤";
            case NEW_MESSAGE:          return "💬";
            case OUTGOING_CALL:        return "📲";
            case THEME_TOGGLE:         return "🎨";
            // Notifications
            case NOTIFICATION_POP:     return "🔔";
            case NOTIFICATION_CLEAR:   return "🔕";
            case URGENT_ALERT:         return "🚨";
            case REMINDER_CHIME:       return "⏰";
            // Tasks & Projects
            case TASK_CREATED:         return "📝";
            case TASK_COMPLETED:       return "✅";
            case TASK_SUBMITTED:       return "📨";
            case TASK_MOVED:           return "↔";
            case TASK_DELETED:         return "🗑";
            case PROJECT_CREATED:      return "🚀";
            case PROJECT_DELETED:      return "💥";
            case STAR_RATING:          return "⭐";
            case REVIEW_SUBMITTED:     return "📋";
            case REVIEW_APPROVED:      return "👍";
            case REVIEW_REVISION:      return "🔄";
            case DEADLINE_WARNING:     return "⚡";
            // Team & HR
            case MEMBER_ADDED:         return "👋";
            case MEMBER_REMOVED:       return "👤";
            case ATTENDANCE_CHECKIN:   return "🕐";
            case ATTENDANCE_CHECKOUT:  return "🕔";
            case LEAVE_REQUESTED:      return "📅";
            case LEAVE_APPROVED:       return "✈";
            case LEAVE_REJECTED:       return "🚫";
            case PAYROLL_GENERATED:    return "💰";
            case INTERVIEW_SCHEDULED:  return "📆";
            case INTERVIEW_ACCEPTED:   return "🤝";
            case INTERVIEW_REJECTED:   return "❌";
            // Communication
            case TYPING_INDICATOR:     return "⌨";
            case VOICE_CALL_RING:      return "📞";
            case VIDEO_CALL_RING:      return "📹";
            case MISSED_CALL:          return "📵";
            case SCREEN_SHARE_START:   return "🖥";
            case SCREEN_SHARE_END:     return "🔌";
            case FILE_UPLOADED:        return "📎";
            case EMOJI_POP:            return "😊";
            // Auth & Nav
            case LOGIN_SUCCESS:        return "🔓";
            case LOGIN_FAILED:         return "🔒";
            case LOGOUT:               return "🚪";
            case FACE_RECOGNIZED:      return "👁";
            case FACE_FAILED:          return "🚫";
            case SIDEBAR_TOGGLE:       return "📂";
            case TAB_SWITCH:           return "📑";
            case SEARCH_OPEN:          return "🔍";
            // AI & System
            case AI_THINKING:          return "🤖";
            case AI_COMPLETE:          return "✨";
            case WEATHER_REFRESH:      return "🌤";
            case EXPORT_COMPLETE:      return "📥";
            case SETTINGS_SAVED:       return "⚙";
            case APP_STARTUP:          return "🎵";
            case APP_MINIMIZE:         return "🔽";
            default:                   return "🔊";
        }
    }

    /** All sound keys in display order. */
    public static String[] allSoundKeys() {
        return VARIANTS.keySet().toArray(new String[0]);
    }
}
