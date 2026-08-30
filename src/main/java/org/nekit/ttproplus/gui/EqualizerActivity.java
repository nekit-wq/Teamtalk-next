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

    // Mic DSP controls
    private Spinner spinnerDspEngine;
    private CheckBox chkMicNs;
    private LinearLayout containerNsWebrtc;
    private Spinner spinnerNsLevel;
    private LinearLayout containerNsSpeex;
    private TextView labelSpeexNs;
    private SeekBar seekBarSpeexNs;

    private CheckBox chkMicAec;
    private LinearLayout containerAecSpeex;
    private TextView labelSpeexAec;
    private SeekBar seekBarSpeexAec;

    private CheckBox chkMicAgc;
    private LinearLayout containerAgc;
    private LinearLayout layoutAgcMode;
    private Spinner spinnerAgcMode;
    private LinearLayout layoutAgcFixed;
    private TextView labelAgcFixed;
    private SeekBar seekBarAgcFixed;
    private LinearLayout layoutAgcAdaptive;
    private TextView labelAgcMaxGain;
    private SeekBar seekBarAgcMaxGain;
    private TextView labelAgcHeadroom;
    private SeekBar seekBarAgcHeadroom;

    private CheckBox chkMicPreamp;
    private LinearLayout containerPreamp;
    private TextView labelPreamp;
    private SeekBar seekBarPreamp;
    private CheckBox chkMicHwEffects;
    private LinearLayout containerMicBands;

    // Playback and general controls
    private LinearLayout containerBands;
    private Equalizer equalizer;
    private TextView labelBass;
    private TextView labelMicGain;
    private TextView labelVirtualizer;
    private TextView labelVox;
    private TeamTalkConnection mConnection;
    private SharedPreferences prefs;
    private SeekBar seekBarBass;
    private SeekBar seekBarMicGain;
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
        // Playback Views
        this.chkEqEnable = findViewById(R.id.eq_enable_checkbox);
        this.spinnerPreset = findViewById(R.id.eq_preset_spinner);
        this.containerBands = findViewById(R.id.eq_bands_container);
        this.labelBass = findViewById(R.id.eq_bass_label);
        this.seekBarBass = findViewById(R.id.eq_bass_seekbar);
        this.labelVirtualizer = findViewById(R.id.eq_virtualizer_label);
        this.seekBarVirtualizer = findViewById(R.id.eq_virtualizer_seekbar);

        // DSP Engine Views
        this.spinnerDspEngine = findViewById(R.id.eq_dsp_engine_spinner);
        this.chkMicNs = findViewById(R.id.eq_mic_ns_checkbox);
        this.containerNsWebrtc = findViewById(R.id.eq_mic_ns_webrtc_container);
        this.spinnerNsLevel = findViewById(R.id.eq_mic_ns_level_spinner);
        this.containerNsSpeex = findViewById(R.id.eq_mic_ns_speex_container);
        this.labelSpeexNs = findViewById(R.id.eq_mic_speex_ns_label);
        this.seekBarSpeexNs = findViewById(R.id.eq_mic_speex_ns_seekbar);

        // Echo Cancellation Views
        this.chkMicAec = findViewById(R.id.eq_mic_aec_checkbox);
        this.containerAecSpeex = findViewById(R.id.eq_mic_aec_speex_container);
        this.labelSpeexAec = findViewById(R.id.eq_mic_speex_aec_label);
        this.seekBarSpeexAec = findViewById(R.id.eq_mic_speex_aec_seekbar);

        // AGC Views
        this.chkMicAgc = findViewById(R.id.eq_mic_agc_checkbox);
        this.containerAgc = findViewById(R.id.eq_mic_agc_container);
        this.layoutAgcMode = findViewById(R.id.eq_mic_agc_mode_layout);
        this.spinnerAgcMode = findViewById(R.id.eq_mic_agc_mode_spinner);
        this.layoutAgcFixed = findViewById(R.id.eq_mic_agc_fixed_layout);
        this.labelAgcFixed = findViewById(R.id.eq_mic_agc_fixed_label);
        this.seekBarAgcFixed = findViewById(R.id.eq_mic_agc_fixed_seekbar);
        this.layoutAgcAdaptive = findViewById(R.id.eq_mic_agc_adaptive_layout);
        this.labelAgcMaxGain = findViewById(R.id.eq_mic_agc_max_gain_label);
        this.seekBarAgcMaxGain = findViewById(R.id.eq_mic_agc_max_gain_seekbar);
        this.labelAgcHeadroom = findViewById(R.id.eq_mic_agc_headroom_label);
        this.seekBarAgcHeadroom = findViewById(R.id.eq_mic_agc_headroom_seekbar);

        // Preamp Views
        this.chkMicPreamp = findViewById(R.id.eq_mic_preamp_checkbox);
        this.containerPreamp = findViewById(R.id.eq_mic_preamp_container);
        this.labelPreamp = findViewById(R.id.eq_mic_preamp_label);
        this.seekBarPreamp = findViewById(R.id.eq_mic_preamp_seekbar);

        // Hardware Effects Views
        this.chkMicHwEffects = findViewById(R.id.eq_mic_hw_effects_checkbox);
        this.containerMicBands = findViewById(R.id.eq_mic_bands_container);
        buildMicrophoneBands();

        // General Mic Views
        this.labelMicGain = findViewById(R.id.eq_mic_gain_label);
        this.seekBarMicGain = findViewById(R.id.eq_mic_gain_seekbar);
        this.labelVox = findViewById(R.id.eq_vox_label);
        this.seekBarVox = findViewById(R.id.eq_vox_seekbar);
        this.btnReset = findViewById(R.id.eq_reset_button);

        // Setup Playback Presets
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

        this.chkEqEnable.setChecked(this.prefs.getBoolean(Preferences.PREF_EQ_ENABLED, true));
        this.chkEqEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AudioEffectsManager.getInstance(this).setEnabled(isChecked);
            updateControlStates(isChecked);
        });
        updateControlStates(this.chkEqEnable.isChecked());

        // Setup BassBoost & Virtualizer
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

        // 1. Setup DSP Engine Spinner
        String[] dspEngines = {
            getString(R.string.eq_dsp_engine_webrtc),
            getString(R.string.eq_dsp_engine_speex),
            getString(R.string.eq_dsp_engine_hardware)
        };
        ArrayAdapter<String> dspAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dspEngines);
        dspAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.spinnerDspEngine.setAdapter(dspAdapter);
        int savedEngine = this.prefs.getInt(Preferences.PREF_EQ_DSP_ENGINE, 0);
        if (savedEngine >= 0 && savedEngine < dspEngines.length) {
            this.spinnerDspEngine.setSelection(savedEngine);
        }
        this.spinnerDspEngine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_DSP_ENGINE, position).apply();
                EqualizerActivity.this.updateMicUiVisibility();
                EqualizerActivity.this.notifyServiceAudioChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 2. Setup Noise Suppression (NS)
        this.chkMicNs.setChecked(this.prefs.getBoolean(Preferences.PREF_EQ_MIC_NS, false));
        this.chkMicNs.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_NS, isChecked).apply();
            updateMicUiVisibility();
            notifyServiceAudioChange();
        });

        String[] nsLevels = {
            getString(R.string.eq_mic_ns_level_low),
            getString(R.string.eq_mic_ns_level_moderate),
            getString(R.string.eq_mic_ns_level_high),
            getString(R.string.eq_mic_ns_level_very_high)
        };
        ArrayAdapter<String> nsLevelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nsLevels);
        nsLevelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.spinnerNsLevel.setAdapter(nsLevelAdapter);
        int savedNsLevel = this.prefs.getInt(Preferences.PREF_EQ_MIC_NS_LEVEL, 2);
        if (savedNsLevel >= 0 && savedNsLevel < nsLevels.length) {
            this.spinnerNsLevel.setSelection(savedNsLevel);
        }
        this.spinnerNsLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_NS_LEVEL, position).apply();
                EqualizerActivity.this.notifyServiceAudioChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        this.seekBarSpeexNs.setMax(50); // -10 dB to -60 dB
        int savedSpeexNs = this.prefs.getInt(Preferences.PREF_EQ_MIC_SPEEX_NS_DB, -30);
        int speexNsProgress = Math.max(0, Math.min(50, (-savedSpeexNs) - 10));
        this.seekBarSpeexNs.setProgress(speexNsProgress);
        updateSpeexNsLabel(savedSpeexNs);
        this.seekBarSpeexNs.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int db = -(progress + 10);
                EqualizerActivity.this.updateSpeexNsLabel(db);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_SPEEX_NS_DB, db).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        // 3. Setup Acoustic Echo Cancellation (AEC)
        this.chkMicAec.setChecked(this.prefs.getBoolean(Preferences.PREF_EQ_MIC_AEC, false));
        this.chkMicAec.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_AEC, isChecked).apply();
            updateMicUiVisibility();
            notifyServiceAudioChange();
        });

        this.seekBarSpeexAec.setMax(50); // -10 dB to -60 dB
        int savedSpeexAec = this.prefs.getInt(Preferences.PREF_EQ_MIC_SPEEX_AEC_DB, -40);
        int speexAecProgress = Math.max(0, Math.min(50, (-savedSpeexAec) - 10));
        this.seekBarSpeexAec.setProgress(speexAecProgress);
        updateSpeexAecLabel(savedSpeexAec);
        this.seekBarSpeexAec.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int db = -(progress + 10);
                EqualizerActivity.this.updateSpeexAecLabel(db);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_SPEEX_AEC_DB, db).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        // 4. Setup AGC (Automatic Gain Control)
        this.chkMicAgc.setChecked(this.prefs.getBoolean(Preferences.PREF_EQ_MIC_AGC, false));
        this.chkMicAgc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_AGC, isChecked).apply();
            updateMicUiVisibility();
            notifyServiceAudioChange();
        });

        String[] agcModes = {
            getString(R.string.eq_mic_agc_mode_adaptive),
            getString(R.string.eq_mic_agc_mode_fixed)
        };
        ArrayAdapter<String> agcModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, agcModes);
        agcModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.spinnerAgcMode.setAdapter(agcModeAdapter);
        int savedAgcMode = this.prefs.getInt(Preferences.PREF_EQ_MIC_AGC_MODE, 0);
        if (savedAgcMode >= 0 && savedAgcMode < agcModes.length) {
            this.spinnerAgcMode.setSelection(savedAgcMode);
        }
        this.spinnerAgcMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_MODE, position).apply();
                EqualizerActivity.this.updateMicUiVisibility();
                EqualizerActivity.this.notifyServiceAudioChange();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        this.seekBarAgcFixed.setMax(49);
        int savedFixedGain = this.prefs.getInt(Preferences.PREF_EQ_MIC_AGC_FIXED_GAIN, 15);
        this.seekBarAgcFixed.setProgress(Math.max(0, Math.min(49, savedFixedGain)));
        updateAgcFixedLabel(savedFixedGain);
        this.seekBarAgcFixed.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateAgcFixedLabel(progress);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_FIXED_GAIN, progress).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        this.seekBarAgcMaxGain.setMax(49);
        int savedMaxGain = this.prefs.getInt(Preferences.PREF_EQ_MIC_AGC_MAX_GAIN, 30);
        this.seekBarAgcMaxGain.setProgress(Math.max(0, Math.min(49, savedMaxGain)));
        updateAgcMaxGainLabel(savedMaxGain);
        this.seekBarAgcMaxGain.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateAgcMaxGainLabel(progress);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_MAX_GAIN, progress).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        this.seekBarAgcHeadroom.setMax(15);
        int savedHeadroom = this.prefs.getInt(Preferences.PREF_EQ_MIC_AGC_HEADROOM, 5);
        this.seekBarAgcHeadroom.setProgress(Math.max(0, Math.min(15, savedHeadroom)));
        updateAgcHeadroomLabel(savedHeadroom);
        this.seekBarAgcHeadroom.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EqualizerActivity.this.updateAgcHeadroomLabel(progress);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_HEADROOM, progress).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        // 5. Setup Pre-Amplifier
        this.chkMicPreamp.setChecked(this.prefs.getBoolean(Preferences.PREF_EQ_MIC_PREAMP_ENABLE, false));
        this.chkMicPreamp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_PREAMP_ENABLE, isChecked).apply();
            updateMicUiVisibility();
            notifyServiceAudioChange();
        });

        this.seekBarPreamp.setMax(400); // 100 to 500 (1.0x to 5.0x)
        int savedPreamp = this.prefs.getInt(Preferences.PREF_EQ_MIC_PREAMP_GAIN, 100);
        this.seekBarPreamp.setProgress(Math.max(0, Math.min(400, savedPreamp - 100)));
        updatePreampLabel(savedPreamp);
        this.seekBarPreamp.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int factor = 100 + progress;
                EqualizerActivity.this.updatePreampLabel(factor);
                if (fromUser) {
                    EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_PREAMP_GAIN, factor).apply();
                    EqualizerActivity.this.notifyServiceAudioChange();
                }
            }
        });

        // 6. Setup Hardware Effects
        this.chkMicHwEffects.setChecked(this.prefs.getBoolean(Preferences.PREF_EQ_MIC_HW_EFFECTS, false));
        this.chkMicHwEffects.setOnCheckedChangeListener((buttonView, isChecked) -> {
            this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_HW_EFFECTS, isChecked).apply();
            notifyServiceAudioChange();
        });

        // 7. Setup Microphone Gain
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

        // 8. Setup VOX Activation Level
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

        // 9. Reset Button
        this.btnReset.setOnClickListener(v -> resetToDefaults());

        updateMicUiVisibility();
    }

    private void buildMicrophoneBands() {
        this.containerMicBands.removeAllViews();
        final int[] frequencies = {60, 120, 250, 500, 1000, 2000, 4000, 8000, 16000};
        for (int i = 0; i < frequencies.length; i++) {
            final int band = i;
            int saved = this.prefs.getInt(Preferences.PREF_EQ_MIC_BAND_PREFIX + band, 0);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(this);
            label.setText(formatMicFrequency(frequencies[band], saved));
            SeekBar seekBar = new SeekBar(this);
            seekBar.setMax(24);
            seekBar.setProgress(Math.max(0, Math.min(24, saved + 12)));
            seekBar.setContentDescription(label.getText());
            seekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int db = progress - 12;
                    String text = formatMicFrequency(frequencies[band], db);
                    label.setText(text);
                    seekBar.setContentDescription(text);
                    if (fromUser) {
                        EqualizerActivity.this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_BAND_PREFIX + band, db).apply();
                        EqualizerActivity.this.notifyServiceAudioChange();
                    }
                }
            });
            row.addView(label);
            row.addView(seekBar);
            this.containerMicBands.addView(row);
        }
    }

    private String formatMicFrequency(int frequency, int db) {
        String frequencyText = frequency >= 1000 ? (frequency / 1000) + " kHz" : frequency + " Hz";
        return frequencyText + " (" + db + " dB)";
    }

    private void updateMicUiVisibility() {
        int engine = this.spinnerDspEngine.getSelectedItemPosition();
        boolean isWebRtc = (engine == 0);
        boolean isSpeex = (engine == 1);
        boolean isHw = (engine == 2);

        boolean nsChecked = this.chkMicNs.isChecked();
        this.containerNsWebrtc.setVisibility((isWebRtc && nsChecked) ? View.VISIBLE : View.GONE);
        this.containerNsSpeex.setVisibility((isSpeex && nsChecked) ? View.VISIBLE : View.GONE);

        boolean aecChecked = this.chkMicAec.isChecked();
        this.containerAecSpeex.setVisibility((isSpeex && aecChecked) ? View.VISIBLE : View.GONE);

        boolean agcChecked = this.chkMicAgc.isChecked();
        this.containerAgc.setVisibility(agcChecked ? View.VISIBLE : View.GONE);

        if (agcChecked) {
            if (isWebRtc) {
                this.layoutAgcMode.setVisibility(View.VISIBLE);
                int agcMode = this.spinnerAgcMode.getSelectedItemPosition();
                this.layoutAgcFixed.setVisibility(agcMode == 1 ? View.VISIBLE : View.GONE);
                this.layoutAgcAdaptive.setVisibility(agcMode == 0 ? View.VISIBLE : View.GONE);
            } else {
                this.layoutAgcMode.setVisibility(View.GONE);
                this.layoutAgcFixed.setVisibility(View.GONE);
                this.layoutAgcAdaptive.setVisibility(View.VISIBLE);
            }
        }

        this.chkMicPreamp.setVisibility(isWebRtc ? View.VISIBLE : View.GONE);
        this.containerPreamp.setVisibility((isWebRtc && this.chkMicPreamp.isChecked()) ? View.VISIBLE : View.GONE);
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
        this.equalizer = AudioEffectsManager.getInstance(this).getEqualizer();
        this.bassBoost = AudioEffectsManager.getInstance(this).getBassBoost();
        if (this.equalizer != null) {
            try {
                this.containerBands.removeAllViews();
                short[] bandLevelRange = this.equalizer.getBandLevelRange();
                this.minEqLevel = bandLevelRange[0];
                this.maxEqLevel = bandLevelRange[1];
                short numBands = this.equalizer.getNumberOfBands();
                for (short i = 0; i < numBands; i = (short) (i + 1)) {
                    final short band = i;
                    int centerFreq = this.equalizer.getCenterFreq(i);
                    short currentLevel = this.equalizer.getBandLevel(i);

                    LinearLayout bandLayout = new LinearLayout(this);
                    bandLayout.setOrientation(LinearLayout.VERTICAL);
                    bandLayout.setPadding(0, 0, 0, 16);

                    final TextView freqLabel = new TextView(this);
                    String freqStr = (centerFreq < 1000000) ? (centerFreq / 1000) + " Hz" : (centerFreq / 1000000) + " kHz";
                    freqLabel.setText(freqStr + " (" + (currentLevel / 100) + " dB)");
                    freqLabel.setTextSize(14.0f);
                    bandLayout.addView(freqLabel);

                    SeekBar bandSeekBar = new SeekBar(this);
                    bandSeekBar.setMax(this.maxEqLevel - this.minEqLevel);
                    bandSeekBar.setProgress(currentLevel - this.minEqLevel);
                    bandSeekBar.setContentDescription(freqLabel.getText());

                    bandSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            short level = (short) (EqualizerActivity.this.minEqLevel + progress);
                            int freq = EqualizerActivity.this.equalizer.getCenterFreq(band);
                            String fStr = (freq < 1000000) ? (freq / 1000) + " Hz" : (freq / 1000000) + " kHz";
                            String text = fStr + " (" + (level / 100) + " dB)";
                            freqLabel.setText(text);
                            seekBar.setContentDescription(text);
                            if (fromUser) {
                                AudioEffectsManager.getInstance(EqualizerActivity.this).setBandLevel(band, level);
                                EqualizerActivity.this.setCustomPresetSelection();
                            }
                        }
                    });
                    bandLayout.addView(bandSeekBar);
                    this.containerBands.addView(bandLayout);
                }
            } catch (Exception e) {
            }
        }
    }

    private void updateBassLabel(int strength) {
        String text = getString(R.string.eq_preset_bass) + ": " + (strength / 10) + "%";
        this.labelBass.setText(text);
        this.seekBarBass.setContentDescription(text);
    }

    private void updateVirtLabel(int strength) {
        String text = getString(R.string.eq_virtualizer) + ": " + (strength / 10) + "%";
        this.labelVirtualizer.setText(text);
        this.seekBarVirtualizer.setContentDescription(text);
    }

    private void updateSpeexNsLabel(int db) {
        String text = getString(R.string.eq_mic_speex_ns_depth) + ": " + db + " dB";
        this.labelSpeexNs.setText(text);
        this.seekBarSpeexNs.setContentDescription(text);
    }

    private void updateSpeexAecLabel(int db) {
        String text = getString(R.string.eq_mic_speex_aec_depth) + ": " + db + " dB";
        this.labelSpeexAec.setText(text);
        this.seekBarSpeexAec.setContentDescription(text);
    }

    private void updateAgcFixedLabel(int db) {
        String text = getString(R.string.eq_mic_agc_fixed_gain) + ": " + db + " dB";
        this.labelAgcFixed.setText(text);
        this.seekBarAgcFixed.setContentDescription(text);
    }

    private void updateAgcMaxGainLabel(int db) {
        String text = getString(R.string.eq_mic_agc_max_gain) + ": " + db + " dB";
        this.labelAgcMaxGain.setText(text);
        this.seekBarAgcMaxGain.setContentDescription(text);
    }

    private void updateAgcHeadroomLabel(int db) {
        String text = getString(R.string.eq_mic_agc_headroom) + ": " + db + " dB";
        this.labelAgcHeadroom.setText(text);
        this.seekBarAgcHeadroom.setContentDescription(text);
    }

    private void updatePreampLabel(int factorRaw) {
        float factor = factorRaw / 100.0f;
        double db = 20.0 * Math.log10(factor);
        String text = getString(R.string.eq_mic_preamp_gain) + ": " + String.format(Locale.US, "%.1fx", factor) + String.format(Locale.US, " (+%.1f dB)", db);
        this.labelPreamp.setText(text);
        this.seekBarPreamp.setContentDescription(text);
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

        this.spinnerDspEngine.setSelection(0);
        this.prefs.edit().putInt(Preferences.PREF_EQ_DSP_ENGINE, 0).apply();

        this.chkMicNs.setChecked(false);
        this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_NS, false).apply();
        this.spinnerNsLevel.setSelection(2);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_NS_LEVEL, 2).apply();
        this.seekBarSpeexNs.setProgress(20);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_SPEEX_NS_DB, -30).apply();
        updateSpeexNsLabel(-30);

        this.chkMicAec.setChecked(false);
        this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_AEC, false).apply();
        this.seekBarSpeexAec.setProgress(30);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_SPEEX_AEC_DB, -40).apply();
        updateSpeexAecLabel(-40);

        this.chkMicAgc.setChecked(false);
        this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_AGC, false).apply();
        this.spinnerAgcMode.setSelection(0);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_MODE, 0).apply();
        this.seekBarAgcFixed.setProgress(15);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_FIXED_GAIN, 15).apply();
        updateAgcFixedLabel(15);
        this.seekBarAgcMaxGain.setProgress(30);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_MAX_GAIN, 30).apply();
        updateAgcMaxGainLabel(30);
        this.seekBarAgcHeadroom.setProgress(5);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_AGC_HEADROOM, 5).apply();
        updateAgcHeadroomLabel(5);

        this.chkMicPreamp.setChecked(false);
        this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_PREAMP_ENABLE, false).apply();
        this.seekBarPreamp.setProgress(0);
        this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_PREAMP_GAIN, 100).apply();
        updatePreampLabel(100);

        this.chkMicHwEffects.setChecked(false);
        this.prefs.edit().putBoolean(Preferences.PREF_EQ_MIC_HW_EFFECTS, false).apply();

        int defaultGain = SoundLevel.SOUND_GAIN_DEFAULT;
        this.seekBarMicGain.setProgress(defaultGain);
        this.prefs.edit().putInt(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, defaultGain).apply();
        updateGainLabel(defaultGain, defaultGain);

        for (int i = 0; i < 9; i++) {
            this.prefs.edit().putInt(Preferences.PREF_EQ_MIC_BAND_PREFIX + i, 0).apply();
        }
        buildMicrophoneBands();

        this.seekBarVox.setProgress(0);
        this.prefs.edit().putInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 0).apply();
        updateVoxLabel(0);

        updateMicUiVisibility();
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
            try {
                unbindService(this.mConnection);
            } catch (Exception ignored) {}
            this.mConnection.setBound(false);
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
