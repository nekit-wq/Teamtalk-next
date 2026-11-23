package org.nekit.ttproplus.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.data.SoundTheme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoundThemeManager {

    public static final String PREF_SOUND_PACK = "sound_pack_preference";
    private static final String PREF_CUSTOM_THEMES_JSON = "custom_sound_themes_list";
    private static final String SOUND_THEMES_DIR = "sound_themes";

    private static final String[] SUPPORTED_EXTENSIONS = {".ogg", ".wav", ".mp3", ".m4a", ".aac"};

    public static class EventInfo {
        public final String key;
        public final int titleResId;
        public final int categoryResId;
        public final int defaultRawId;
        public final String[] assetCandidates;

        public EventInfo(String key, int titleResId, int categoryResId, int defaultRawId, String[] assetCandidates) {
            this.key = key;
            this.titleResId = titleResId;
            this.categoryResId = categoryResId;
            this.defaultRawId = defaultRawId;
            this.assetCandidates = assetCandidates;
        }
    }

    public static final List<EventInfo> ALL_EVENTS = Collections.unmodifiableList(Arrays.asList(
            // Transmission
            new EventInfo("on", R.string.sound_event_tx_on, R.string.sound_cat_transmission, R.raw.on,
                    new String[]{"on.wav", "on.ogg", "hotkey.wav", "hotkey.ogg"}),
            new EventInfo("off", R.string.sound_event_tx_off, R.string.sound_cat_transmission, R.raw.off,
                    new String[]{"off.wav", "off.ogg", "hotkey.wav", "hotkey.ogg"}),
            new EventInfo("txqueue_start", R.string.sound_event_txqueue_start, R.string.sound_cat_transmission, R.raw.txqueue_start,
                    new String[]{"txqueue_start.wav", "txqueue_start.ogg"}),
            new EventInfo("txqueue_stop", R.string.sound_event_txqueue_stop, R.string.sound_cat_transmission, R.raw.txqueue_stop,
                    new String[]{"txqueue_stop.wav", "txqueue_stop.ogg"}),

            // Messages
            new EventInfo("user_message", R.string.sound_event_user_msg, R.string.sound_cat_messages, R.raw.user_message,
                    new String[]{"user_msg.wav", "user_message.ogg", "user_msg.ogg", "personal_message.ogg", "user_message.wav"}),
            new EventInfo("channel_message", R.string.sound_event_chan_msg, R.string.sound_cat_messages, R.raw.channel_message,
                    new String[]{"channel_msg.wav", "channel_message.ogg", "channel_msg.ogg", "channel_message.wav"}),
            new EventInfo("channel_message_sent", R.string.sound_event_chan_msg_sent, R.string.sound_cat_messages, R.raw.channel_message_sent,
                    new String[]{"channel_msg_sent.wav", "channel_message_sent.ogg", "user_msg_sent.wav", "user_message_sent.wav", "user_message_sent.ogg"}),
            new EventInfo("broadcast_message", R.string.sound_event_broadcast_msg, R.string.sound_cat_messages, R.raw.broadcast_message,
                    new String[]{"broadcast_msg.wav", "broadcast_message.ogg", "broadcast_msg.ogg", "broadcast_message.wav"}),
            new EventInfo("typing", R.string.sound_event_typing, R.string.sound_cat_messages, R.raw.typing,
                    new String[]{"typing.wav", "typing.ogg"}),

            // Users and channels
            new EventInfo("user_join", R.string.sound_event_user_join, R.string.sound_cat_users_channels, R.raw.user_join,
                    new String[]{"newuser.wav", "user_join.ogg", "newuser.ogg", "user_join.wav"}),
            new EventInfo("user_left", R.string.sound_event_user_left, R.string.sound_cat_users_channels, R.raw.user_left,
                    new String[]{"removeuser.wav", "user_left.ogg", "removeuser.ogg", "user_left.wav"}),
            new EventInfo("logged_on", R.string.sound_event_logged_on, R.string.sound_cat_users_channels, R.raw.logged_on,
                    new String[]{"logged_on.wav", "logged_on.ogg"}),
            new EventInfo("logged_off", R.string.sound_event_logged_off, R.string.sound_cat_users_channels, R.raw.logged_off,
                    new String[]{"logged_off.wav", "logged_off.ogg"}),

            // Connection and system
            new EventInfo("serverlost", R.string.sound_event_serverlost, R.string.sound_cat_system_connection, R.raw.serverlost,
                    new String[]{"serverlost.wav", "serverlost.ogg"}),
            new EventInfo("fileupdate", R.string.sound_event_fileupdate, R.string.sound_cat_system_connection, R.raw.fileupdate,
                    new String[]{"fileupdate.wav", "fileupdate.ogg", "filetx_complete.wav", "filetx_complete.ogg"}),
            new EventInfo("intercept", R.string.sound_event_intercept, R.string.sound_cat_system_connection, R.raw.intercept,
                    new String[]{"intercept.wav", "intercept.ogg"}),
            new EventInfo("interceptend", R.string.sound_event_interceptend, R.string.sound_cat_system_connection, R.raw.interceptend,
                    new String[]{"interceptEnd.wav", "interceptend.ogg", "intercept_end.ogg", "interceptEnd.ogg", "interceptend.wav"}),

            // Voice Activation (VOX)
            new EventInfo("voiceact_enable", R.string.sound_event_voiceact_enable, R.string.sound_cat_voice_activation, R.raw.voiceact_enable,
                    new String[]{"vox_enable.wav", "voiceact_enable.ogg", "vox_me_enable.wav", "voiceact_enable.wav"}),
            new EventInfo("voiceact_disable", R.string.sound_event_voiceact_disable, R.string.sound_cat_voice_activation, R.raw.voiceact_disable,
                    new String[]{"vox_disable.wav", "voiceact_disable.ogg", "vox_me_disable.wav", "voiceact_disable.wav"}),
            new EventInfo("voiceact_on", R.string.sound_event_voiceact_on, R.string.sound_cat_voice_activation, R.raw.voiceact_on,
                    new String[]{"voiceact_on.wav", "voiceact_on.ogg"}),
            new EventInfo("voiceact_off", R.string.sound_event_voiceact_off, R.string.sound_cat_voice_activation, R.raw.voiceact_off,
                    new String[]{"voiceact_off.wav", "voiceact_off.ogg"})
    ));

    public static List<SoundTheme> getBuiltInThemes(Context context) {
        List<SoundTheme> builtIns = new ArrayList<>();
        builtIns.add(new SoundTheme("default", context.getString(R.string.sound_theme_default_name), "default", false));
        builtIns.add(new SoundTheme("Default Windows", "Default Windows", "Default Windows", false));
        builtIns.add(new SoundTheme("Legacy Android", "Legacy Android", "Legacy Android", false));
        builtIns.add(new SoundTheme("Majorly-G", "Majorly-G", "Majorly-G", false));
        builtIns.add(new SoundTheme("Old", "Old", "Old", false));
        builtIns.add(new SoundTheme("Team Talk Pro", "Team Talk Pro", "Team Talk Pro", false));
        builtIns.add(new SoundTheme("silent", context.getString(R.string.sound_theme_silent_name), "silent", false));
        return builtIns;
    }

    public static synchronized List<SoundTheme> getAllThemes(Context context) {
        List<SoundTheme> result = new ArrayList<>(getBuiltInThemes(context));
        result.addAll(getCustomThemes(context));
        return result;
    }

    public static synchronized List<SoundTheme> getCustomThemes(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(PREF_CUSTOM_THEMES_JSON, null);
        List<SoundTheme> list = new ArrayList<>();
        if (json != null && !json.trim().isEmpty()) {
            try {
                Type listType = new TypeToken<ArrayList<SoundTheme>>() {}.getType();
                List<SoundTheme> parsed = new Gson().fromJson(json, listType);
                if (parsed != null) {
                    list.addAll(parsed);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Backward compatibility migration: if legacy "custom" sound pack was configured,
        // create a custom theme for it so user doesn't lose previously imported sounds
        boolean hasMigratedLegacy = prefs.getBoolean("migrated_legacy_custom_sound_pack", false);
        if (!hasMigratedLegacy) {
            boolean hasAnyCustomSound = false;
            for (EventInfo event : ALL_EVENTS) {
                String legacyPath = prefs.getString("custom_sound_" + event.key, null);
                if (legacyPath != null && new File(legacyPath).exists()) {
                    hasAnyCustomSound = true;
                    break;
                }
            }
            if (hasAnyCustomSound) {
                SoundTheme legacyTheme = new SoundTheme("custom_legacy", context.getString(R.string.sound_theme_custom_legacy_name), "default", true);
                File themeDir = getThemeDir(context, legacyTheme.getId());
                themeDir.mkdirs();
                for (EventInfo event : ALL_EVENTS) {
                    String legacyPath = prefs.getString("custom_sound_" + event.key, null);
                    if (legacyPath != null) {
                        File src = new File(legacyPath);
                        if (src.exists()) {
                            File dest = new File(themeDir, event.key + ".ogg");
                            copyFile(src, dest);
                        }
                    }
                }
                list.add(legacyTheme);
                saveCustomThemes(context, list);
                if ("custom".equals(prefs.getString(PREF_SOUND_PACK, "default"))) {
                    prefs.edit().putString(PREF_SOUND_PACK, legacyTheme.getId()).apply();
                }
            }
            prefs.edit().putBoolean("migrated_legacy_custom_sound_pack", true).apply();
        }

        return list;
    }

    private static synchronized void saveCustomThemes(Context context, List<SoundTheme> themes) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = new Gson().toJson(themes);
        prefs.edit().putString(PREF_CUSTOM_THEMES_JSON, json).apply();
    }

    public static synchronized SoundTheme createTheme(Context context, String name, String baseTheme) {
        List<SoundTheme> current = getCustomThemes(context);
        String id = "theme_" + System.currentTimeMillis();
        SoundTheme theme = new SoundTheme(id, name, baseTheme, true);
        current.add(theme);
        saveCustomThemes(context, current);

        File themeDir = getThemeDir(context, id);
        themeDir.mkdirs();
        return theme;
    }

    public static synchronized boolean renameTheme(Context context, String themeId, String newName) {
        List<SoundTheme> current = getCustomThemes(context);
        for (SoundTheme theme : current) {
            if (theme.getId().equals(themeId)) {
                theme.setName(newName);
                saveCustomThemes(context, current);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean deleteTheme(Context context, String themeId) {
        List<SoundTheme> current = getCustomThemes(context);
        SoundTheme toRemove = null;
        for (SoundTheme theme : current) {
            if (theme.getId().equals(themeId)) {
                toRemove = theme;
                break;
            }
        }
        if (toRemove != null) {
            current.remove(toRemove);
            saveCustomThemes(context, current);

            File themeDir = getThemeDir(context, themeId);
            deleteRecursive(themeDir);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (themeId.equals(prefs.getString(PREF_SOUND_PACK, "default"))) {
                prefs.edit().putString(PREF_SOUND_PACK, "default").apply();
            }
            return true;
        }
        return false;
    }

    public static SoundTheme getThemeById(Context context, String themeId) {
        for (SoundTheme theme : getAllThemes(context)) {
            if (theme.getId().equals(themeId)) {
                return theme;
            }
        }
        return new SoundTheme("default", context.getString(R.string.sound_theme_default_name), "default", false);
    }

    public static String getActiveThemeId(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SOUND_PACK, "default");
    }

    public static void setActiveThemeId(Context context, String themeId) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(PREF_SOUND_PACK, themeId).apply();
    }

    public static File getThemeDir(Context context, String themeId) {
        File dir = new File(context.getFilesDir(), SOUND_THEMES_DIR + File.separator + themeId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getSoundFileForEvent(Context context, String themeId, String eventKey) {
        if (themeId == null || "silent".equals(themeId) || "default".equals(themeId)) {
            return null;
        }

        File themeDir = new File(context.getFilesDir(), SOUND_THEMES_DIR + File.separator + themeId);
        if (themeDir.exists() && themeDir.isDirectory()) {
            for (String ext : SUPPORTED_EXTENSIONS) {
                File candidate = new File(themeDir, eventKey + ext);
                if (candidate.exists() && candidate.isFile() && candidate.length() > 0) {
                    return candidate;
                }
            }
        }

        return null;
    }

    public static boolean setEventSound(Context context, String themeId, String eventKey, Uri sourceUri) {
        File themeDir = getThemeDir(context, themeId);
        resetEventSound(context, themeId, eventKey);

        String ext = ".ogg";
        File dest = new File(themeDir, eventKey + ext);
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) return false;
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            if (dest.exists()) {
                dest.delete();
            }
            return false;
        }
    }

    public static boolean resetEventSound(Context context, String themeId, String eventKey) {
        File themeDir = new File(context.getFilesDir(), SOUND_THEMES_DIR + File.separator + themeId);
        if (!themeDir.exists()) return false;
        boolean deleted = false;
        for (String ext : SUPPORTED_EXTENSIONS) {
            File f = new File(themeDir, eventKey + ext);
            if (f.exists()) {
                f.delete();
                deleted = true;
            }
        }
        return deleted;
    }

    public static int getCustomizedEventsCount(Context context, String themeId) {
        File themeDir = new File(context.getFilesDir(), SOUND_THEMES_DIR + File.separator + themeId);
        if (!themeDir.exists()) return 0;
        int count = 0;
        for (EventInfo event : ALL_EVENTS) {
            for (String ext : SUPPORTED_EXTENSIONS) {
                File f = new File(themeDir, event.key + ext);
                if (f.exists() && f.length() > 0) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    public static String getBasePack(Context context, String themeId) {
        if (themeId == null) return "default";
        SoundTheme theme = getThemeById(context, themeId);
        return theme.getBaseTheme() != null ? theme.getBaseTheme() : "default";
    }

    private static MediaPlayer sCurrentPlayer = null;

    public static synchronized void playEventSoundPreview(Context context, String themeId, EventInfo event) {
        try {
            if (sCurrentPlayer != null) {
                sCurrentPlayer.stop();
                sCurrentPlayer.release();
                sCurrentPlayer = null;
            }

            if ("silent".equals(themeId)) {
                return;
            }

            File customFile = getSoundFileForEvent(context, themeId, event.key);
            if (customFile != null && customFile.exists()) {
                sCurrentPlayer = new MediaPlayer();
                sCurrentPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                sCurrentPlayer.setDataSource(customFile.getAbsolutePath());
                sCurrentPlayer.prepare();
                sCurrentPlayer.start();
                return;
            }

            String basePack = getBasePack(context, themeId);
            if (!"default".equals(basePack) && !"silent".equals(basePack) && event.assetCandidates != null) {
                for (String candidate : event.assetCandidates) {
                    try {
                        AssetFileDescriptor afd = context.getAssets().openFd("sounds/" + basePack + "/" + candidate);
                        sCurrentPlayer = new MediaPlayer();
                        sCurrentPlayer.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                        sCurrentPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                        sCurrentPlayer.prepare();
                        sCurrentPlayer.start();
                        return;
                    } catch (Exception ignored) {
                    }
                }
            }

            // Fallback to default raw resource
            sCurrentPlayer = MediaPlayer.create(context, event.defaultRawId);
            if (sCurrentPlayer != null) {
                sCurrentPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void stopPreview() {
        if (sCurrentPlayer != null) {
            try {
                sCurrentPlayer.stop();
                sCurrentPlayer.release();
            } catch (Exception ignored) {}
            sCurrentPlayer = null;
        }
    }

    private static void copyFile(File src, File dest) {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
}
