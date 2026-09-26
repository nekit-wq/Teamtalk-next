package org.nekit.ttproplus.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.nekit.ttproplus.R;

public final class ScreenShareAudioHelper {
    public static final int MODE_BOTH = 0;
    public static final int MODE_SCREEN_ONLY = 1;
    public static final int MODE_MIC_ONLY = 2;

    public static final class AudioModeOption {
        public final int mode;
        public final String title;
        public final String summary;

        public AudioModeOption(int mode, String title, String summary) {
            this.mode = mode;
            this.title = title;
            this.summary = summary;
        }
    }

    private ScreenShareAudioHelper() {
    }

    public static int getAudioMode(SharedPreferences prefs) {
        if (prefs == null) {
            return MODE_BOTH;
        }
        try {
            return prefs.getInt(Preferences.PREF_SCREENSHARE_AUDIO_MODE, MODE_BOTH);
        } catch (ClassCastException unused) {
            try {
                return Integer.parseInt(prefs.getString(Preferences.PREF_SCREENSHARE_AUDIO_MODE, "0"));
            } catch (NumberFormatException unused2) {
                return MODE_BOTH;
            }
        }
    }

    public static String getAudioModeLabel(Context context, int mode) {
        if (mode == MODE_SCREEN_ONLY) {
            return context.getString(R.string.screenshare_audio_mode_screen_only);
        }
        if (mode == MODE_MIC_ONLY) {
            return context.getString(R.string.screenshare_audio_mode_mic_only);
        }
        return context.getString(R.string.screenshare_audio_mode_both);
    }

    public static List<AudioModeOption> getAudioModeOptions(Context context) {
        ArrayList<AudioModeOption> list = new ArrayList<>();
        list.add(new AudioModeOption(MODE_BOTH, context.getString(R.string.screenshare_audio_mode_both), context.getString(R.string.screenshare_audio_mode_both_summary)));
        list.add(new AudioModeOption(MODE_SCREEN_ONLY, context.getString(R.string.screenshare_audio_mode_screen_only), context.getString(R.string.screenshare_audio_mode_screen_only_summary)));
        list.add(new AudioModeOption(MODE_MIC_ONLY, context.getString(R.string.screenshare_audio_mode_mic_only), context.getString(R.string.screenshare_audio_mode_mic_only_summary)));
        return list;
    }

    public static void setAudioMode(SharedPreferences prefs, int mode) {
        if (prefs == null) {
            return;
        }
        prefs.edit().putInt(Preferences.PREF_SCREENSHARE_AUDIO_MODE, mode).apply();
    }
}
