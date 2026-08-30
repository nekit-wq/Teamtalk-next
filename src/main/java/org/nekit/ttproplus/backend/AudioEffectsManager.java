package org.nekit.ttproplus.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioEffectsManager {
    private static final String TAG = "AudioEffectsManager";
    private static AudioEffectsManager instance;
    private BassBoost bassBoost;
    private Equalizer equalizer;
    private SharedPreferences prefs;

    private AudioEffectsManager(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        initEffects();
    }

    public static synchronized AudioEffectsManager getInstance(Context context) {
        if (instance == null) {
            instance = new AudioEffectsManager(context);
        }
        return instance;
    }

    private void initEffects() {
        try {
            this.equalizer = new Equalizer(0, 0);
            this.equalizer.setEnabled(this.prefs.getBoolean("eq_enabled", true));
            short bands = this.equalizer.getNumberOfBands();
            for (short i = 0; i < bands; i++) {
                int defaultLevel = this.equalizer.getBandLevel(i);
                int savedLevel = this.prefs.getInt("eq_band_" + i, defaultLevel);
                short minLevel = this.equalizer.getBandLevelRange()[0];
                short maxLevel = this.equalizer.getBandLevelRange()[1];
                if (savedLevel >= minLevel && savedLevel <= maxLevel) {
                    this.equalizer.setBandLevel(i, (short) savedLevel);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Equalizer: " + e.getMessage());
            this.equalizer = null;
        }
        try {
            this.bassBoost = new BassBoost(0, 0);
            if (this.bassBoost.getStrengthSupported()) {
                this.bassBoost.setEnabled(this.prefs.getBoolean("eq_enabled", true));
                int savedStrength = this.prefs.getInt("eq_bass_boost", 0);
                this.bassBoost.setStrength((short) savedStrength);
            }
        } catch (Exception e2) {
            Log.e(TAG, "Failed to initialize BassBoost: " + e2.getMessage());
            this.bassBoost = null;
        }
    }

    public Equalizer getEqualizer() {
        if (this.equalizer == null) {
            initEffects();
        }
        return this.equalizer;
    }

    public BassBoost getBassBoost() {
        return this.bassBoost;
    }

    public void setEnabled(boolean enabled) {
        this.prefs.edit().putBoolean("eq_enabled", enabled).apply();
        if (this.equalizer != null) {
            try {
                this.equalizer.setEnabled(enabled);
            } catch (Exception ignored) {
            }
        }
        if (this.bassBoost != null && this.bassBoost.getStrengthSupported()) {
            try {
                this.bassBoost.setEnabled(enabled);
            } catch (Exception ignored) {
            }
        }
    }

    public boolean isEnabled() {
        return this.prefs.getBoolean("eq_enabled", true);
    }

    public void setBandLevel(short band, short level) {
        this.prefs.edit().putInt("eq_band_" + band, level).apply();
        if (this.equalizer != null) {
            try {
                this.equalizer.setBandLevel(band, level);
            } catch (Exception e) {
                Log.e(TAG, "Error setting band level: " + e.getMessage());
            }
        }
    }

    public void setBassBoostStrength(short strength) {
        this.prefs.edit().putInt("eq_bass_boost", strength).apply();
        if (this.bassBoost != null && this.bassBoost.getStrengthSupported()) {
            try {
                this.bassBoost.setStrength(strength);
            } catch (Exception e) {
                Log.e(TAG, "Error setting bass boost strength: " + e.getMessage());
            }
        }
    }

    public void release() {
        if (this.equalizer != null) {
            try {
                this.equalizer.release();
            } catch (Exception ignored) {
            }
            this.equalizer = null;
        }
        if (this.bassBoost != null) {
            try {
                this.bassBoost.release();
            } catch (Exception ignored) {
            }
            this.bassBoost = null;
        }
    }
}
