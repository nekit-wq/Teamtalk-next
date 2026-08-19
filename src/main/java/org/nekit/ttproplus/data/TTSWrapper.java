package org.nekit.ttproplus.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import androidx.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TTSWrapper {
    private static final String TAG = "bearware";
    public static final String defaultEngineName = "com.google.android.tts";
    private Context mContext;
    private String mCurrentEngineName;
    private final List<String> mPendingSpeeches;
    private boolean mTtsReady;
    private TextToSpeech tts;
    public Boolean useAnnouncements;

    public TTSWrapper(Context context) {
        this.useAnnouncements = false;
        this.mCurrentEngineName = defaultEngineName;
        this.mTtsReady = false;
        this.mPendingSpeeches = new ArrayList<>();
        this.mContext = context;
        this.tts = new TextToSpeech(context, this::onTtsInit);
    }

    public TTSWrapper(Context context, String engineName) {
        this.useAnnouncements = false;
        this.mCurrentEngineName = engineName;
        this.mTtsReady = false;
        this.mPendingSpeeches = new ArrayList<>();
        this.mContext = context;
        this.tts = new TextToSpeech(context, this::onTtsInit, engineName);
    }

    private Locale getCurrentAppLocale(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        }
        return context.getResources().getConfiguration().locale;
    }

    public void onTtsInit(int status) {
        this.mTtsReady = (status == TextToSpeech.SUCCESS);
        if (this.mTtsReady) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
            String behavior = prefs.getString("pref_tts_language_behavior", "follow_app");
            if ("follow_app".equals(behavior)) {
                Locale locale = getCurrentAppLocale(this.mContext);
                int result = this.tts.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language " + locale + " is NOT supported or missing data");
                } else {
                    Log.d(TAG, "TTS initialized for language: " + locale);
                }
            } else {
                Log.d(TAG, "TTS initialized using system default language (behavior: " + behavior + ")");
            }
            if (!this.mPendingSpeeches.isEmpty()) {
                Log.d(TAG, "TTS ready, flushing " + this.mPendingSpeeches.size() + " pending speeches");
                for (String text : this.mPendingSpeeches) {
                    this.tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
                }
                this.mPendingSpeeches.clear();
            }
        }
    }

    public void setLanguage(Locale locale) {
        if (this.tts != null) {
            int result = this.tts.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language " + locale + " is NOT supported or missing data during update");
            } else {
                Log.d(TAG, "TTS updated to language: " + locale);
            }
        }
    }

    public void shutdown() {
        this.mPendingSpeeches.clear();
        if (this.tts != null) {
            this.tts.shutdown();
        }
    }

    public void setAccessibilityStream(boolean bEnable) {
        if (this.tts == null) {
            return;
        }
        try {
            this.tts.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(bEnable ? AudioAttributes.CONTENT_TYPE_SPEECH : AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setUsage(bEnable ? AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY : AudioAttributes.USAGE_UNKNOWN)
                .build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to set TTS audio attributes", e);
        }
    }

    public void speak(String text) {
        if (Boolean.TRUE.equals(this.useAnnouncements)) {
            AccessibilityManager manager = (AccessibilityManager) this.mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager != null && manager.isEnabled()) {
                AccessibilityEvent e = AccessibilityEvent.obtain();
                e.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
                e.getText().add(text);
                manager.sendAccessibilityEvent(e);
                return;
            }
        }
        if (!this.mTtsReady) {
            Log.d(TAG, "TTS not ready, queuing: " + text);
            this.mPendingSpeeches.add(text);
        } else if (this.tts != null) {
            this.tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
        }
    }

    public List<EngineInfo> getEngines() {
        return this.tts == null ? new ArrayList<>() : this.tts.getEngines();
    }

    public void reinitialize(Context context, String engineName) {
        Log.d(TAG, "Reinitializing TTS engine: " + engineName);
        this.mTtsReady = false;
        this.mPendingSpeeches.clear();
        if (this.tts != null) {
            this.tts.shutdown();
        }
        this.mContext = context;
        this.mCurrentEngineName = engineName;
        this.tts = new TextToSpeech(context, this::onTtsInit, engineName);
    }

    public TTSWrapper switchEngine(String engineName) {
        if (engineName.equals(this.mCurrentEngineName)) {
            return this;
        }
        reinitialize(this.mContext, engineName);
        return this;
    }
}
