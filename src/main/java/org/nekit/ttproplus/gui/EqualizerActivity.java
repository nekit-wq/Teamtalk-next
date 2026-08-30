package org.nekit.ttproplus.gui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import dk.bearware.SoundLevel;
import java.util.Locale;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.AudioEffectsManager;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.Preferences;

public class EqualizerActivity extends AppCompatActivity implements TeamTalkConnectionListener {
    private BassBoost bassBoost;
    private Button btnReset;
    private CheckBox chkEqEnable;
    private CheckBox chkMicAec;
    private CheckBox chkMicAgc;
    private CheckBox chkMicNs;
    private LinearLayout containerBands;
    private Equalizer equalizer;
    private TextView labelBass;
    private TextView labelMicGain;
    private TextView labelMicHpf;
    private TextView labelVirtualizer;
    private TextView labelVox;
    private TeamTalkConnection mConnection;
    private SharedPreferences prefs;
    private SeekBar seekBarBass;
    private SeekBar seekBarMicGain;
    private SeekBar seekBarMicHpf;
    private SeekBar seekBarVirtualizer;
    private SeekBar seekBarVox;
    private Spinner spinnerPreset;
    private short minEqLevel = -1500;
    private short maxEqLevel = 1500;
    private boolean isUpdatingPresets = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_equalizer);
        }
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        initViews();
        bindAudioEffects();
        this.mConnection = new TeamTalkConnection(this);
        Intent intent = new Intent(this, TeamTalkService.class);
        bindService(intent, this.mConnection, BIND_AUTO_CREATE);
    }

    private void initViews() {
        this.chkEqEnable = findViewById(R.id.eq_enable_checkbox);
        this.spinnerPreset = findViewById(R.id.eq_preset_spinner);
        this.containerBands = findViewById(R.id.eq_bands_container);
        this.labelBass = findViewById(R.id.eq_bass_label);
        this.seekBarBass = findViewById(R.id.eq_bass_seekbar);
        this.labelVirtualizer = findViewById(R.id.eq_virtualizer_label);
        this.seekBarVirtualizer = findViewById(R.id.eq_virtualizer_seekbar);
        this.chkMicNs = findViewById(R.id.eq_mic_ns_checkbox);
        this.chkMicAec = findViewById(R.id.eq_mic_aec_checkbox);
        this.chkMicAgc = findViewById(R.id.eq_mic_agc_checkbox);
        this.labelMicHpf = findViewById(R.id.eq_mic_hpf_label);
        this.seekBarMicHpf = findViewById(R.id.eq_mic_hpf_seekbar);
        this.labelMicGain = findViewById(R.id.eq_mic_gain_label);
        this.seekBarMicGain = findViewById(R.id.eq_mic_gain_seekbar);
        this.labelVox = findViewById(R.id.eq_vox_label);
        this.seekBarVox = findViewById(R.id.eq_vox_seekbar);
        this.btnReset = findViewById(R.id.eq_reset_button);

        String[] presets = {
            getString(R.string.eq_preset_flat),
            getString(R.string.eq_preset_bass),
            getString(R.string.eq_preset_speech),
            getString(R.string.eq_preset_treble),
            getString(R.string.eq_preset_music),
            getString(R.string.eq_preset_custom)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.spinnerPreset.setAdapter(adapter);
        int selectedPreset = this.prefs.getInt("eq_selected_preset", 5);
        if (selectedPreset >= 0 && selectedPreset < presets.length) {
            this.spinnerPreset.setSelection(selectedPreset);
        }
        this.spinnerPreset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!EqualizerActivity.this.isUpdatingPresets) {
                    EqualizerActivity.this.applyPreset(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        this.chkEqEnable.setChecked(this.prefs.getBoolean("eq_enabled", true));
        this.chkEqEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AudioEffectsManager.getInstance(this).setEnabled(isChecked);
            updateControlStates(isChecked);
        });
        updateControlStates(this.chkEqEnable.isChecked());

        this.seekBarBass.setMax(1000);
        int savedBass = this.prefs.getInt("eq_bass_boost", 0);
        this.seekBarBass.setProgress(savedBass);
        updateBassLabel(savedBass);
        this.seekBarBass.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateBassLabel(progress);
                if (fromUser) {
                    AudioEffectsManager.getInstance(EqualizerActivity.this).setBassBoostStrength((short) progress);
                }
            }
        });

        this.seekBarVirtualizer.setMax(1000);
        int savedVirt = this.prefs.getInt("eq_virtualizer_level", 0);
        this.seekBarVirtualizer.setProgress(savedVirt);
        updateVirtLabel(savedVirt);
        this.seekBarVirtualizer.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateVirtLabel(progress);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt("eq_virtualizer_level", progress).apply();
                }
            }
        });

        this.chkMicNs.setChecked(this.prefs.getBoolean("eq_mic_ns", false));
        this.chkMicNs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean("eq_mic_ns", isChecked).apply();
            notifyServiceAudioChange();
        });

        this.chkMicAec.setChecked(this.prefs.getBoolean("eq_mic_aec", false));
        this.chkMicAec.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean("eq_mic_aec", isChecked).apply();
            notifyServiceAudioChange();
        });

        this.chkMicAgc.setChecked(this.prefs.getBoolean("eq_mic_agc", false));
        this.chkMicAgc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean("eq_mic_agc", isChecked).apply();
            notifyServiceAudioChange();
        });

        this.seekBarMicHpf.setMax(100);
        int savedHpf = this.prefs.getInt("eq_mic_hpf", 0);
        this.seekBarMicHpf.setProgress(savedHpf);
        updateHpfLabel(savedHpf);
        this.seekBarMicHpf.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateHpfLabel(progress);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt("eq_mic_hpf", progress).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        final int defaultGain = SoundLevel.SOUND_GAIN_DEFAULT;
        int maxGain = defaultGain * 2;
        this.seekBarMicGain.setMax(maxGain);
        int savedGain = this.prefs.getInt(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, defaultGain);
        this.seekBarMicGain.setProgress(savedGain);
        updateGainLabel(savedGain, defaultGain);
        this.seekBarMicGain.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateGainLabel(progress, defaultGain);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, progress).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        this.seekBarVox.setMax(100);
        int savedVox = this.prefs.getInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 0);
        this.seekBarVox.setProgress(savedVox);
        updateVoxLabel(savedVox);
        this.seekBarVox.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateVoxLabel(progress);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, progress).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        this.btnReset.setOnClickListener(v -> resetToDefaults());
    }

    private void updateControlStates(boolean enabled) {
        this.containerBands.setAlpha(enabled ? 1.0f : 0.5f);
        this.spinnerPreset.setEnabled(enabled);
        this.seekBarBass.setEnabled(enabled);
        this.seekBarVirtualizer.setEnabled(enabled);
        for (int i = 0; i < this.containerBands.getChildCount(); i++) {
            View child = this.containerBands.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout bandLayout = (LinearLayout) child;
                for (int j = 0; j < bandLayout.getChildCount(); j++) {
                    View v = bandLayout.getChildAt(j);
                    if (v instanceof SeekBar) {
                        v.setEnabled(enabled);
                    }
                }
            }
        }
    }

    private void bindAudioEffects() {
        AudioEffectsManager manager = AudioEffectsManager.getInstance(this);
        this.equalizer = manager.getEqualizer();
        this.bassBoost = manager.getBassBoost();
        this.containerBands.removeAllViews();
        if (this.equalizer != null) {
            short[] range = this.equalizer.getBandLevelRange();
            if (range != null && range.length >= 2) {
                this.minEqLevel = range[0];
                this.maxEqLevel = range[1];
            }
            short numBands = this.equalizer.getNumberOfBands();
            for (short i = 0; i < numBands; i = (short) (i + 1)) {
                final short bandIndex = i;
                int freq = this.equalizer.getCenterFreq(i);
                final String freqText = (freq < 1000000) ? (freq / 1000) + " Hz" : String.format(Locale.US, "%.1f kHz", freq / 1000000.0f);
                LinearLayout bandLayout = new LinearLayout(this);
                bandLayout.setOrientation(LinearLayout.VERTICAL);
                bandLayout.setPadding(0, 0, 0, 16);
                final TextView tv = new TextView(this);
                tv.setTextSize(16.0f);
                tv.setPadding(0, 0, 0, 4);
                bandLayout.addView(tv);
                SeekBar seekBar = new SeekBar(this);
                int maxProgress = this.maxEqLevel - this.minEqLevel;
                seekBar.setMax(maxProgress);
                int savedLevel = this.prefs.getInt("eq_band_" + ((int) i), 0);
                int initialProgress = savedLevel - this.minEqLevel;
                if (initialProgress < 0) {
                    initialProgress = 0;
                }
                if (initialProgress > maxProgress) {
                    initialProgress = maxProgress;
                }
                seekBar.setProgress(initialProgress);
                updateBandLabel(tv, seekBar, freqText, savedLevel);
                seekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar2, int progress, boolean fromUser) {
                        short newLevel = (short) (EqualizerActivity.this.minEqLevel + progress);
                        EqualizerActivity.this.updateBandLabel(tv, seekBar2, freqText, newLevel);
                        if (fromUser) {
                            AudioEffectsManager.getInstance(EqualizerActivity.this).setBandLevel(bandIndex, newLevel);
                            EqualizerActivity.this.setCustomPresetSelection();
                        }
                    }
                });
                bandLayout.addView(seekBar);
                this.containerBands.addView(bandLayout);
            }
        }
        updateControlStates(this.chkEqEnable.isChecked());
    }

    private void updateBandLabel(TextView tv, SeekBar seekBar, String freqText, int level) {
        String text = freqText + ": " + (level / 100) + " dB";
        tv.setText(text);
        seekBar.setContentDescription(text);
    }

    private void updateBassLabel(int value) {
        String text = getString(R.string.eq_bass_boost) + ": " + (value / 10) + "%";
        this.labelBass.setText(text);
        this.seekBarBass.setContentDescription(text);
    }

    private void updateVirtLabel(int value) {
        String text = getString(R.string.eq_virtualizer) + ": " + (value / 10) + "%";
        this.labelVirtualizer.setText(text);
        this.seekBarVirtualizer.setContentDescription(text);
    }

    private void updateHpfLabel(int value) {
        String text = getString(R.string.eq_mic_hpf) + ": " + value + " Hz";
        this.labelMicHpf.setText(text);
        this.seekBarMicHpf.setContentDescription(text);
    }

    private void updateGainLabel(int value, int defaultGain) {
        int percent = (value * 100) / defaultGain;
        String text = getString(R.string.eq_mic_gain) + ": " + percent + "%";
        this.labelMicGain.setText(text);
        this.seekBarMicGain.setContentDescription(text);
    }

    private void updateVoxLabel(int value) {
        String text = getString(R.string.eq_vox_level) + ": " + value;
        this.labelVox.setText(text);
        this.seekBarVox.setContentDescription(text);
    }

    public void applyPreset(int position) {
        if (this.equalizer == null) {
            return;
        }
        this.prefs.edit().putInt("eq_selected_preset", position).apply();
        short numBands = this.equalizer.getNumberOfBands();
        if (position == 5) {
            return;
        }
        for (short i = 0; i < numBands; i = (short) (i + 1)) {
            short level = 0;
            if (position == 0) {
                level = 0;
            } else if (position == 1) {
                level = (short) (i < numBands / 2 ? 600 : 0);
            } else if (position == 2) {
                level = (short) ((i >= numBands / 3 && i <= (numBands * 2) / 3) ? 500 : -200);
            } else if (position == 3) {
                level = (short) (i >= numBands / 2 ? 600 : 0);
            } else if (position == 4) {
                level = (short) ((i == 0 || i == numBands - 1) ? 500 : -100);
            }
            if (level < this.minEqLevel) {
                level = this.minEqLevel;
            }
            if (level > this.maxEqLevel) {
                level = this.maxEqLevel;
            }
            AudioEffectsManager.getInstance(this).setBandLevel(i, level);
        }
        this.isUpdatingPresets = true;
        bindAudioEffects();
        this.isUpdatingPresets = false;
    }

    public void setCustomPresetSelection() {
        this.isUpdatingPresets = true;
        this.spinnerPreset.setSelection(5);
        this.prefs.edit().putInt("eq_selected_preset", 5).apply();
        this.isUpdatingPresets = false;
    }

    private void resetToDefaults() {
        this.isUpdatingPresets = true;
        this.spinnerPreset.setSelection(0);
        applyPreset(0);
        this.chkEqEnable.setChecked(true);
        AudioEffectsManager.getInstance(this).setEnabled(true);
        this.seekBarBass.setProgress(0);
        AudioEffectsManager.getInstance(this).setBassBoostStrength((short) 0);
        updateBassLabel(0);
        this.seekBarVirtualizer.setProgress(0);
        this.prefs.edit().putInt("eq_virtualizer_level", 0).apply();
        updateVirtLabel(0);
        this.chkMicNs.setChecked(false);
        this.prefs.edit().putBoolean("eq_mic_ns", false).apply();
        this.chkMicAec.setChecked(false);
        this.prefs.edit().putBoolean("eq_mic_aec", false).apply();
        this.chkMicAgc.setChecked(false);
        this.prefs.edit().putBoolean("eq_mic_agc", false).apply();
        this.seekBarMicHpf.setProgress(0);
        this.prefs.edit().putInt("eq_mic_hpf", 0).apply();
        updateHpfLabel(0);
        int defaultGain = SoundLevel.SOUND_GAIN_DEFAULT;
        this.seekBarMicGain.setProgress(defaultGain);
        this.prefs.edit().putInt(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, defaultGain).apply();
        updateGainLabel(defaultGain, defaultGain);
        this.seekBarVox.setProgress(0);
        this.prefs.edit().putInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 0).apply();
        updateVoxLabel(0);
        notifyServiceAudioChange();
        this.isUpdatingPresets = false;
    }

    private void notifyServiceAudioChange() {
        if (this.mConnection != null && this.mConnection.getService() != null) {
            this.mConnection.getService().applyRealTimeAudioProcessing();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.mConnection != null && this.mConnection.isBound()) {
            unbindService(this.mConnection);
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        bindAudioEffects();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
    }

    private static abstract class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
