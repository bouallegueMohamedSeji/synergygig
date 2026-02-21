package utils;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Manages audio device detection, selection, and volume for voice calls.
 * Stores user preferences using java.util.prefs.Preferences.
 */
public class AudioDeviceManager {

    private static final AudioDeviceManager INSTANCE = new AudioDeviceManager();
    private static final Preferences PREFS = Preferences.userNodeForPackage(AudioDeviceManager.class);

    private static final String PREF_MIC = "selected_mic";
    private static final String PREF_SPEAKER = "selected_speaker";
    private static final String PREF_MIC_VOL = "mic_volume";
    private static final String PREF_SPEAKER_VOL = "speaker_volume";

    // Audio format: 16kHz, 16-bit, mono, signed, little-endian (must match AudioCallService)
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000, 16, 1, true, false);

    private AudioDeviceManager() {}

    public static AudioDeviceManager getInstance() { return INSTANCE; }

    // ═══════════════════════════════════════════
    //  DEVICE ENUMERATION
    // ═══════════════════════════════════════════

    /** List all available microphone (capture) devices. */
    public List<Mixer.Info> getInputDevices() {
        List<Mixer.Info> result = new ArrayList<>();
        DataLine.Info targetInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(targetInfo)) {
                result.add(info);
            }
        }
        return result;
    }

    /** List all available speaker (playback) devices. */
    public List<Mixer.Info> getOutputDevices() {
        List<Mixer.Info> result = new ArrayList<>();
        DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.isLineSupported(sourceInfo)) {
                result.add(info);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════
    //  DEVICE SELECTION (persisted in prefs)
    // ═══════════════════════════════════════════

    public String getSelectedMicName() {
        return PREFS.get(PREF_MIC, "");
    }

    public void setSelectedMicName(String name) {
        PREFS.put(PREF_MIC, name != null ? name : "");
    }

    public String getSelectedSpeakerName() {
        return PREFS.get(PREF_SPEAKER, "");
    }

    public void setSelectedSpeakerName(String name) {
        PREFS.put(PREF_SPEAKER, name != null ? name : "");
    }

    // ═══════════════════════════════════════════
    //  VOLUME (0.0 - 1.0)
    // ═══════════════════════════════════════════

    public double getMicVolume() {
        return PREFS.getDouble(PREF_MIC_VOL, 0.8);
    }

    public void setMicVolume(double vol) {
        PREFS.putDouble(PREF_MIC_VOL, Math.max(0, Math.min(1, vol)));
    }

    public double getSpeakerVolume() {
        return PREFS.getDouble(PREF_SPEAKER_VOL, 1.5);
    }

    public void setSpeakerVolume(double vol) {
        PREFS.putDouble(PREF_SPEAKER_VOL, Math.max(0, Math.min(3.0, vol)));
    }

    // ═══════════════════════════════════════════
    //  OPEN LINE FROM SELECTED DEVICE
    // ═══════════════════════════════════════════

    /** Open the selected microphone (or system default). */
    public TargetDataLine openMicLine() throws LineUnavailableException {
        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        String selectedName = getSelectedMicName();

        if (!selectedName.isEmpty()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(selectedName)) {
                    Mixer mixer = AudioSystem.getMixer(info);
                    if (mixer.isLineSupported(lineInfo)) {
                        TargetDataLine line = (TargetDataLine) mixer.getLine(lineInfo);
                        line.open(AUDIO_FORMAT, 640 * 4);
                        return line;
                    }
                }
            }
        }

        // Fallback to system default
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(lineInfo);
        line.open(AUDIO_FORMAT, 640 * 4);
        return line;
    }

    /** Open the selected speaker (or system default). */
    public SourceDataLine openSpeakerLine() throws LineUnavailableException {
        DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        String selectedName = getSelectedSpeakerName();

        if (!selectedName.isEmpty()) {
            for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                if (info.getName().equals(selectedName)) {
                    Mixer mixer = AudioSystem.getMixer(info);
                    if (mixer.isLineSupported(lineInfo)) {
                        SourceDataLine line = (SourceDataLine) mixer.getLine(lineInfo);
                        line.open(AUDIO_FORMAT, 640 * 8);
                        return line;
                    }
                }
            }
        }

        // Fallback to system default
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(lineInfo);
        line.open(AUDIO_FORMAT, 640 * 8);
        return line;
    }

    /** Apply volume gain to raw audio buffer (in-place). Supports amplification > 1.0. */
    public static void applyVolume(byte[] buffer, int length, double volume) {
        if (volume >= 0.99 && volume <= 1.01) return; // no change needed at ~1.0
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            int amplified = (int) (sample * volume);
            // Clamp to prevent distortion
            if (amplified > Short.MAX_VALUE) amplified = Short.MAX_VALUE;
            else if (amplified < Short.MIN_VALUE) amplified = Short.MIN_VALUE;
            buffer[i] = (byte) (amplified & 0xFF);
            buffer[i + 1] = (byte) ((amplified >> 8) & 0xFF);
        }
    }

    /** Calculate RMS level from audio buffer (0.0 - 1.0 normalized). */
    public static double calculateLevel(byte[] buffer, int length) {
        long sum = 0;
        int samples = length / 2;
        for (int i = 0; i < length - 1; i += 2) {
            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
            sum += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sum / samples);
        return Math.min(1.0, rms / 16384.0); // normalize
    }
}
