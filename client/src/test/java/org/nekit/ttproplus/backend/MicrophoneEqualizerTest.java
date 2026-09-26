package org.nekit.ttproplus.backend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class MicrophoneEqualizerTest {

    @Before
    public void setUp() {
        MicrophoneEqualizer.setBands(new float[MicrophoneEqualizer.NUM_BANDS]);
        MicrophoneEqualizer.resetState();
    }

    @Test
    public void testFlatEqualizerPassThrough() {
        assertFalse(MicrophoneEqualizer.hasActiveFilters());

        byte[] buffer = new byte[32];
        for (int i = 0; i < buffer.length; i += 2) {
            short sample = (short) (1000 * (i + 1));
            buffer[i] = (byte) (sample & 0xFF);
            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        byte[] copy = buffer.clone();

        MicrophoneEqualizer.process(buffer, 0, buffer.length, 48000, 1);

        for (int i = 0; i < buffer.length; i++) {
            assertEquals("Sample byte at " + i + " should be untouched when EQ is flat", copy[i], buffer[i]);
        }
    }

    @Test
    public void testEqualizerModifiesAudioWhenGainApplied() {
        float[] gains = new float[MicrophoneEqualizer.NUM_BANDS];
        gains[4] = 6.0f; // +6 dB at 1000 Hz
        MicrophoneEqualizer.setBands(gains);

        assertTrue(MicrophoneEqualizer.hasActiveFilters());

        // Create a 1000 Hz sine wave PCM signal at 48000 Hz sample rate
        int sampleRate = 48000;
        int numSamples = 480;
        byte[] buffer = new byte[numSamples * 2];
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * 1000.0 * i / sampleRate;
            short sample = (short) (10000.0 * Math.sin(angle));
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        byte[] original = buffer.clone();

        MicrophoneEqualizer.process(buffer, 0, buffer.length, sampleRate, 1);

        // Verify that the output was modified
        boolean modified = false;
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] != original[i]) {
                modified = true;
                break;
            }
        }
        assertTrue("Equalizer should modify 1000 Hz signal when +6dB is applied", modified);
    }

    @Test
    public void testStereoProcessing() {
        float[] gains = new float[MicrophoneEqualizer.NUM_BANDS];
        gains[2] = -6.0f; // -6 dB at 250 Hz
        MicrophoneEqualizer.setBands(gains);

        int sampleRate = 48000;
        int numFrames = 480;
        byte[] buffer = new byte[numFrames * 4]; // 2 channels * 2 bytes
        for (int i = 0; i < numFrames; i++) {
            double angle = 2.0 * Math.PI * 250.0 * i / sampleRate;
            short sample = (short) (10000.0 * Math.sin(angle));
            buffer[i * 4] = (byte) (sample & 0xFF);
            buffer[i * 4 + 1] = (byte) ((sample >> 8) & 0xFF);
            buffer[i * 4 + 2] = (byte) (sample & 0xFF);
            buffer[i * 4 + 3] = (byte) ((sample >> 8) & 0xFF);
        }
        byte[] original = buffer.clone();

        MicrophoneEqualizer.process(buffer, 0, buffer.length, sampleRate, 2);

        boolean modified = false;
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] != original[i]) {
                modified = true;
                break;
            }
        }
        assertTrue("Stereo processing should modify audio", modified);
    }

    @Test
    public void testLowSampleRateNyquistSafety() {
        float[] gains = new float[MicrophoneEqualizer.NUM_BANDS];
        gains[7] = 6.0f;  // 8000 Hz band
        gains[8] = 10.0f; // 16000 Hz band
        MicrophoneEqualizer.setBands(gains);

        // At 8000 Hz sample rate, Nyquist is 4000 Hz. 8000 Hz and 16000 Hz must be bypassed without errors/NaN.
        byte[] buffer = new byte[160];
        MicrophoneEqualizer.process(buffer, 0, buffer.length, 8000, 1);

        // Should not crash and buffer should remain zeros
        for (byte b : buffer) {
            assertEquals(0, b);
        }
    }
}
