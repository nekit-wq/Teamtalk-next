package org.nekit.ttproplus.audio;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import de.sciss.jump3r.Main;

/**
 * High-performance audio format converter for TeamTalk Pro+.
 * Converts WAV recordings to MP3 format using the built-in LAME MP3 engine.
 */
public class AudioConverter {
    private static final String TAG = "AudioConverter";

    /**
     * Converts a WAV audio file to MP3 at the specified bitrate (kbps).
     *
     * @param wavFile     Source WAV file
     * @param mp3File     Destination MP3 file
     * @param bitrateKbps Target bitrate (e.g. 16, 32, 64, 128, 256, 320)
     * @return true if conversion succeeded, false otherwise
     */
    public static boolean convertWavToMp3(File wavFile, File mp3File, int bitrateKbps) {
        if (wavFile == null || !wavFile.exists() || wavFile.length() <= 44) {
            Log.e(TAG, "Cannot convert: source WAV file does not exist or is too small: " + (wavFile != null ? wavFile.getAbsolutePath() : "null"));
            return false;
        }

        if (mp3File == null) {
            Log.e(TAG, "Cannot convert: target MP3 file is null");
            return false;
        }

        // Ensure target directory exists
        File parentDir = mp3File.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        int validBitrate = validateBitrate(bitrateKbps);

        Log.i(TAG, "Starting WAV -> MP3 conversion: " + wavFile.getName() + " -> " + mp3File.getName() + " (" + validBitrate + " kbps)");
        long startTime = System.currentTimeMillis();

        try {
            // Delete target file if it already exists
            if (mp3File.exists()) {
                mp3File.delete();
            }

            Main encoder = new Main();
            String[] args = new String[]{
                    "-b", String.valueOf(validBitrate),
                    "--silent",
                    wavFile.getAbsolutePath(),
                    mp3File.getAbsolutePath()
            };

            int result = encoder.run(args);
            long elapsed = System.currentTimeMillis() - startTime;

            if (mp3File.exists() && mp3File.length() > 0) {
                Log.i(TAG, "WAV -> MP3 conversion succeeded in " + elapsed + "ms. Output size: " + mp3File.length() + " bytes");
                return true;
            } else {
                Log.e(TAG, "Encoder returned " + result + " but output MP3 file is missing or empty.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during WAV -> MP3 conversion", e);
            return false;
        }
    }

    private static int validateBitrate(int bitrate) {
        switch (bitrate) {
            case 16:
            case 32:
            case 64:
            case 128:
            case 256:
            case 320:
                return bitrate;
            default:
                return 128;
        }
    }
}
