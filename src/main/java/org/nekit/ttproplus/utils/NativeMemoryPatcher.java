package org.nekit.ttproplus.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;
import dk.bearware.TeamTalkBase;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.nekit.ttproplus.BuildConfig;
import org.nekit.ttproplus.R;

public class NativeMemoryPatcher {
    public static final String TAG = "NativeMemoryPatcher";
    public static final String PREF_CUSTOM_VERSION = "custom_client_version";
    private static final int MAX_VERSION_BUFFER_LEN = 16;

    public static String getCustomVersion(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_CUSTOM_VERSION, null);
    }

    public static boolean setCustomVersion(Context context, String newVersion) {
        if (newVersion == null) return false;
        newVersion = newVersion.trim();
        if (newVersion.isEmpty()) return false;

        boolean success = patchMemory(newVersion);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_CUSTOM_VERSION, newVersion)
                .apply();
        return success;
    }

    public static boolean resetToDefault(Context context) {
        String defaultVer = BuildConfig.VERSION_NAME;
        boolean success = patchMemory(defaultVer);
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .remove(PREF_CUSTOM_VERSION)
                .apply();
        return success;
    }

    public static void applyCustomVersion(Context context) {
        String customVer = getCustomVersion(context);
        if (customVer != null && !customVer.isEmpty()) {
            patchMemory(customVer);
        }
    }

    public static synchronized boolean patchMemory(String newVersion) {
        if (newVersion == null || newVersion.isEmpty()) {
            return false;
        }

        byte[] newVerBytes = newVersion.getBytes(StandardCharsets.US_ASCII);
        if (newVerBytes.length > MAX_VERSION_BUFFER_LEN - 1) {
            Log.e(TAG, "Version string too long: " + newVersion);
            return false;
        }

        File mapsFile = new File("/proc/self/maps");
        File memFile = new File("/proc/self/mem");
        if (!mapsFile.exists() || !memFile.exists()) {
            Log.e(TAG, "/proc/self/maps or mem not accessible");
            return false;
        }

        boolean patched = false;
        try (BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
             RandomAccessFile memRaf = new RandomAccessFile(memFile, "rw")) {

            String line;
            Pattern mapPattern = Pattern.compile("^([0-9a-fA-F]+)-([0-9a-fA-F]+)\\s+([rwxsp-]+)\\s+");

            while ((line = mapsReader.readLine()) != null) {
                // Look for libTeamTalk5-jni mappings or writable/readable app mappings
                if (!line.contains("libTeamTalk5-jni.so") && !line.contains("rw-p") && !line.contains("r--p")) {
                    continue;
                }

                Matcher matcher = mapPattern.matcher(line);
                if (matcher.find()) {
                    long start = Long.parseLong(matcher.group(1), 16);
                    long end = Long.parseLong(matcher.group(2), 16);
                    long size = end - start;

                    if (size <= 0 || size > 64 * 1024 * 1024) {
                        continue;
                    }

                    try {
                        byte[] buffer = new byte[(int) size];
                        memRaf.seek(start);
                        memRaf.readFully(buffer);

                        int foundIndex = findVersionPattern(buffer);
                        if (foundIndex != -1) {
                            long targetAddr = start + foundIndex;
                            Log.i(TAG, "Found target version pattern at offset: 0x" + Long.toHexString(targetAddr));

                            // Pad with null bytes up to 12 bytes
                            byte[] writeBuf = new byte[12];
                            Arrays.fill(writeBuf, (byte) 0);
                            System.arraycopy(newVerBytes, 0, writeBuf, 0, newVerBytes.length);

                            memRaf.seek(targetAddr);
                            memRaf.write(writeBuf);
                            patched = true;
                            Log.i(TAG, "Successfully patched native memory to version: " + newVersion);
                        }
                    } catch (Exception e) {
                        // Skip unreadable memory pages silently
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch memory: " + e.getMessage(), e);
        }

        return patched;
    }

    private static int findVersionPattern(byte[] data) {
        // Find 5.XX.XX pattern
        for (int i = 0; i < data.length - 8; i++) {
            if (data[i] == '5' && data[i + 1] == '.' && data[i + 2] == '2') {
                if (Character.isDigit(data[i + 3]) && data[i + 4] == '.') {
                    return i;
                }
            }
        }
        return -1;
    }

    public static void showChangeVersionDialog(final Activity activity, final Runnable onVersionChanged) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.pref_custom_version_title);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        String current = TeamTalkBase.getVersion();
        input.setText(current);
        input.setSelection(input.getText().length());
        builder.setView(input);

        builder.setMessage(R.string.pref_custom_version_dialog_msg);

        builder.setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newVer = input.getText().toString().trim();
                if (!newVer.isEmpty()) {
                    setCustomVersion(activity, newVer);
                    Toast.makeText(activity, activity.getString(R.string.msg_custom_version_applied, newVer), Toast.LENGTH_SHORT).show();
                    if (onVersionChanged != null) {
                        onVersionChanged.run();
                    }
                }
            }
        });

        builder.setNeutralButton(R.string.button_reset, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                resetToDefault(activity);
                String defVer = BuildConfig.VERSION_NAME;
                Toast.makeText(activity, activity.getString(R.string.msg_custom_version_reset, defVer), Toast.LENGTH_SHORT).show();
                if (onVersionChanged != null) {
                    onVersionChanged.run();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
}
