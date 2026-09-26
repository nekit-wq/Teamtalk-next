package org.nekit.ttproplus.backend;

import android.content.SharedPreferences;
import java.util.Arrays;
import org.nekit.ttproplus.data.Preferences;

/**
 * Real-time 9-band parametric peaking equalizer for microphone PCM audio.
 * Implements RBJ Audio EQ Cookbook biquad IIR filters per band in Direct Form II Transposed.
 */
public final class MicrophoneEqualizer {
    public static final int[] FREQUENCIES = {60, 120, 250, 500, 1000, 2000, 4000, 8000, 16000};
    public static final int NUM_BANDS = FREQUENCIES.length;
    private static final float Q = 1.41421356f; // ~1 octave bandwidth

    private static final Object lock = new Object();
    private static final float[] bandGainsDb = new float[NUM_BANDS];

    // Filter coefficients and state per band
    private static final class BiquadBand {
        final int centerFreq;
        float gainDb = 0.0f;
        boolean active = false;

        float b0 = 1.0f;
        float b1 = 0.0f;
        float b2 = 0.0f;
        float a1 = 0.0f;
        float a2 = 0.0f;

        // Direct Form II Transposed state for Left (or Mono) and Right channels
        float s1L = 0.0f;
        float s2L = 0.0f;
        float s1R = 0.0f;
        float s2R = 0.0f;

        BiquadBand(int centerFreq) {
            this.centerFreq = centerFreq;
        }

        void resetState() {
            s1L = 0.0f;
            s2L = 0.0f;
            s1R = 0.0f;
            s2R = 0.0f;
        }

        void updateCoefficients(int sampleRate) {
            if (sampleRate <= 0) {
                active = false;
                return;
            }
            // Nyquist check: cannot filter frequencies at or above Nyquist frequency (fs / 2)
            if (centerFreq >= (sampleRate * 0.49f)) {
                active = false;
                return;
            }
            // If gain is essentially zero, bypass filter
            if (Math.abs(gainDb) < 0.05f) {
                active = false;
                return;
            }

            double omega = 2.0 * Math.PI * centerFreq / sampleRate;
            double sinOmega = Math.sin(omega);
            double cosOmega = Math.cos(omega);
            double alpha = sinOmega / (2.0 * Q);
            double aVal = Math.pow(10.0, gainDb / 40.0); // A = 10^(dB / 40)

            double a0 = 1.0 + alpha / aVal;
            this.b0 = (float) ((1.0 + alpha * aVal) / a0);
            this.b1 = (float) ((-2.0 * cosOmega) / a0);
            this.b2 = (float) ((1.0 - alpha * aVal) / a0);
            this.a1 = (float) ((-2.0 * cosOmega) / a0);
            this.a2 = (float) ((1.0 - alpha / aVal) / a0);
            this.active = true;
        }

        float processMono(float x) {
            float y = b0 * x + s1L;
            s1L = b1 * x - a1 * y + s2L;
            s2L = b2 * x - a2 * y;
            return y;
        }

        float processLeft(float x) {
            float y = b0 * x + s1L;
            s1L = b1 * x - a1 * y + s2L;
            s2L = b2 * x - a2 * y;
            return y;
        }

        float processRight(float x) {
            float y = b0 * x + s1R;
            s1R = b1 * x - a1 * y + s2R;
            s2R = b2 * x - a2 * y;
            return y;
        }
    }

    private static final BiquadBand[] bands = new BiquadBand[NUM_BANDS];
    private static volatile boolean anyBandActive = false;
    private static int lastSampleRate = 0;

    static {
        for (int i = 0; i < NUM_BANDS; i++) {
            bands[i] = new BiquadBand(FREQUENCIES[i]);
        }
    }

    private MicrophoneEqualizer() {
    }

    public static void loadFromPreferences(SharedPreferences prefs) {
        if (prefs == null) return;
        float[] gains = new float[NUM_BANDS];
        for (int i = 0; i < NUM_BANDS; i++) {
            gains[i] = prefs.getInt(Preferences.PREF_EQ_MIC_BAND_PREFIX + i, 0);
        }
        setBands(gains);
    }

    public static void setBands(float[] gainsDb) {
        if (gainsDb == null) return;
        synchronized (lock) {
            int len = Math.min(gainsDb.length, NUM_BANDS);
            for (int i = 0; i < len; i++) {
                bandGainsDb[i] = gainsDb[i];
                bands[i].gainDb = gainsDb[i];
            }
            if (lastSampleRate > 0) {
                recomputeFiltersLocked(lastSampleRate);
            }
        }
    }

    public static void setBandGain(int bandIndex, float gainDb) {
        if (bandIndex < 0 || bandIndex >= NUM_BANDS) return;
        synchronized (lock) {
            bandGainsDb[bandIndex] = gainDb;
            bands[bandIndex].gainDb = gainDb;
            if (lastSampleRate > 0) {
                recomputeFiltersLocked(lastSampleRate);
            }
        }
    }

    public static float[] getBandGains() {
        synchronized (lock) {
            return Arrays.copyOf(bandGainsDb, NUM_BANDS);
        }
    }

    public static boolean hasActiveFilters() {
        return anyBandActive;
    }

    public static void resetState() {
        synchronized (lock) {
            for (BiquadBand band : bands) {
                band.resetState();
            }
        }
    }

    private static void recomputeFiltersLocked(int sampleRate) {
        boolean hasActive = false;
        for (BiquadBand band : bands) {
            band.updateCoefficients(sampleRate);
            if (band.active) {
                hasActive = true;
            }
        }
        anyBandActive = hasActive;
        lastSampleRate = sampleRate;
    }

    /**
     * Process 16-bit PCM samples in-place.
     *
     * @param buf byte buffer containing 16-bit PCM little-endian
     * @param offset offset into buffer
     * @param length length of bytes to process
     * @param sampleRate sample rate in Hz
     * @param channels channel count (1 = mono, 2 = stereo)
     */
    public static void process(byte[] buf, int offset, int length, int sampleRate, int channels) {
        if (buf == null || length < (channels * 2) || (channels != 1 && channels != 2) || sampleRate <= 0) {
            return;
        }

        synchronized (lock) {
            if (sampleRate != lastSampleRate) {
                recomputeFiltersLocked(sampleRate);
            }

            if (!anyBandActive) {
                return;
            }

            int numSamplesPerChannel = length / (channels * 2);
            int idx = offset;

            if (channels == 1) {
                for (int s = 0; s < numSamplesPerChannel; s++) {
                    short rawSample = (short) ((buf[idx] & 0xFF) | (buf[idx + 1] << 8));
                    float val = rawSample;
                    for (int b = 0; b < NUM_BANDS; b++) {
                        BiquadBand band = bands[b];
                        if (band.active) {
                            val = band.processMono(val);
                        }
                    }
                    short outSample = VoiceChanger.clampToShort(val);
                    buf[idx] = (byte) (outSample & 0xFF);
                    buf[idx + 1] = (byte) ((outSample >> 8) & 0xFF);
                    idx += 2;
                }
            } else {
                for (int s = 0; s < numSamplesPerChannel; s++) {
                    short rawL = (short) ((buf[idx] & 0xFF) | (buf[idx + 1] << 8));
                    short rawR = (short) ((buf[idx + 2] & 0xFF) | (buf[idx + 3] << 8));
                    float valL = rawL;
                    float valR = rawR;
                    for (int b = 0; b < NUM_BANDS; b++) {
                        BiquadBand band = bands[b];
                        if (band.active) {
                            valL = band.processLeft(valL);
                            valR = band.processRight(valR);
                        }
                    }
                    short outL = VoiceChanger.clampToShort(valL);
                    short outR = VoiceChanger.clampToShort(valR);
                    buf[idx] = (byte) (outL & 0xFF);
                    buf[idx + 1] = (byte) ((outL >> 8) & 0xFF);
                    buf[idx + 2] = (byte) (outR & 0xFF);
                    buf[idx + 3] = (byte) ((outR >> 8) & 0xFF);
                    idx += 4;
                }
            }
        }
    }
}
