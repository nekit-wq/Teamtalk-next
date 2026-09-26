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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class TTSWrapper {
    private static final String TAG = "bearware";
    public static final String defaultEngineName = "com.google.android.tts";
    private static final AtomicLong sUtteranceCounter = new AtomicLong(0);

    private Context mContext;
    private String mCurrentEngineName;
    private final List<String> mPendingSpeeches;
    private volatile boolean mTtsReady;
    private TextToSpeech tts;
    public Boolean useAnnouncements;
    private boolean mMuted = false;

    public TTSWrapper(Context context) {
        this(context, defaultEngineName);
    }

    public TTSWrapper(Context context, String engineName) {
        this.useAnnouncements = false;
        this.mCurrentEngineName = engineName;
        this.mTtsReady = false;
        this.mPendingSpeeches = Collections.synchronizedList(new ArrayList<>());
        this.mContext = context != null ? context.getApplicationContext() : null;
        initEngine(this.mContext, engineName);
    }

    private void initEngine(Context context, String engineName) {
        if (context == null) return;
        try {
            if (engineName != null && !engineName.isEmpty() && !defaultEngineName.equals(engineName)) {
                this.tts = new TextToSpeech(context, this::onTtsInit, engineName);
            } else {
                this.tts = new TextToSpeech(context, this::onTtsInit);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to instantiate TextToSpeech", e);
            try {
                this.tts = new TextToSpeech(context, this::onTtsInit);
            } catch (Exception ex) {
                Log.e(TAG, "Fallback default TextToSpeech failed", ex);
            }
        }
    }

    private Locale getCurrentAppLocale(Context context) {
        if (context == null) return Locale.getDefault();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.getResources().getConfiguration().getLocales().get(0);
        }
        return context.getResources().getConfiguration().locale;
    }

    public void onTtsInit(int status) {
        this.mTtsReady = (status == TextToSpeech.SUCCESS);
        if (this.mTtsReady && this.tts != null) {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);
                String behavior = prefs.getString("pref_tts_language_behavior", "follow_app");
                if ("follow_app".equals(behavior)) {
                    Locale locale = getCurrentAppLocale(this.mContext);
                    int result = this.tts.setLanguage(locale);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "Language " + locale + " is NOT supported or missing data, falling back to default");
                        this.tts.setLanguage(Locale.getDefault());
                    } else {
                        Log.d(TAG, "TTS initialized for language: " + locale);
                    }
                } else {
                    Log.d(TAG, "TTS initialized using system default language (behavior: " + behavior + ")");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error configuring TTS locale", e);
            }

            synchronized (this.mPendingSpeeches) {
                if (!this.mPendingSpeeches.isEmpty()) {
                    Log.d(TAG, "TTS ready, flushing " + this.mPendingSpeeches.size() + " pending speeches");
                    List<String> flushList = new ArrayList<>(this.mPendingSpeeches);
                    this.mPendingSpeeches.clear();
                    for (String text : flushList) {
                        speakInternal(text);
                    }
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: " + status);
        }
    }

    public void setLanguage(Locale locale) {
        if (this.tts != null && locale != null) {
            try {
                int result = this.tts.setLanguage(locale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Language " + locale + " is NOT supported or missing data during update");
                } else {
                    Log.d(TAG, "TTS updated to language: " + locale);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting TTS language", e);
            }
        }
    }

    public void shutdown() {
        this.mPendingSpeeches.clear();
        if (this.tts != null) {
            try {
                this.tts.stop();
                this.tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down TTS", e);
            }
            this.tts = null;
        }
        this.mTtsReady = false;
    }

    public void setAccessibilityStream(boolean bEnable) {
        if (this.tts == null) {
            return;
        }
        try {
            this.tts.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(bEnable ? AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY : AudioAttributes.USAGE_MEDIA)
                .build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to set TTS audio attributes", e);
        }
    }

    public boolean isMuted() {
        return this.mMuted;
    }

    public void setMuted(boolean muted) {
        this.mMuted = muted;
        if (muted) {
            this.mPendingSpeeches.clear();
            if (this.tts != null) {
                try {
                    this.tts.stop();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping TTS on mute", e);
                }
            }
        }
    }

    public void speak(String text) {
        if (this.mMuted || text == null || text.trim().isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(this.useAnnouncements) && this.mContext != null) {
            AccessibilityManager manager = (AccessibilityManager) this.mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager != null && manager.isEnabled()) {
                AccessibilityEvent e = AccessibilityEvent.obtain();
                e.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
                e.getText().add(text);
                manager.sendAccessibilityEvent(e);
                return;
            }
        }
        if (!this.mTtsReady || this.tts == null) {
            Log.d(TAG, "TTS not ready, queuing: " + text);
            synchronized (this.mPendingSpeeches) {
                if (this.mPendingSpeeches.size() >= 50) {
                    this.mPendingSpeeches.remove(0);
                }
                this.mPendingSpeeches.add(text);
            }
        } else {
            speakInternal(text);
        }
    }

    private void speakInternal(String text) {
        if (this.tts == null) return;
        try {
            String utteranceId = "tt_" + System.currentTimeMillis() + "_" + sUtteranceCounter.incrementAndGet();
            int res = this.tts.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId);
            if (res == TextToSpeech.ERROR) {
                Log.w(TAG, "TTS speak returned ERROR for text: " + text + ", reinitializing engine...");
                this.mPendingSpeeches.add(text);
                reinitialize(this.mContext, this.mCurrentEngineName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during TTS speakInternal", e);
        }
    }

    public List<EngineInfo> getEngines() {
        if (this.tts == null) return new ArrayList<>();
        try {
            return this.tts.getEngines();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get engines", e);
            return new ArrayList<>();
        }
    }

    public void reinitialize(Context context, String engineName) {
        Log.d(TAG, "Reinitializing TTS engine: " + engineName);
        this.mTtsReady = false;
        if (this.tts != null) {
            try {
                this.tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down TTS during reinit", e);
            }
            this.tts = null;
        }
        this.mContext = context != null ? context.getApplicationContext() : null;
        this.mCurrentEngineName = engineName;
        initEngine(this.mContext, engineName);
    }

    public TTSWrapper switchEngine(String engineName) {
        if (engineName != null && engineName.equals(this.mCurrentEngineName)) {
            return this;
        }
        reinitialize(this.mContext, engineName);
        return this;
    }
}
