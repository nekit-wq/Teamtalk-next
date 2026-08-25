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
    private static final int MAX_VERSION_CHARS = 11; // 12-byte buffer with \0

    private static volatile long cachedTargetAddress = 0;

    private static final byte[] ANCHOR_ARM64 = "abusePrevent\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ANCHOR_OTHER = "webm_vp8\0".getBytes(StandardCharsets.US_ASCII);

    public static String getCustomVersion(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_CUSTOM_VERSION, null);
    }

    public static boolean setCustomVersion(Context context, String newVersion) {
        if (newVersion == null) return false;
        newVersion = newVersion.trim();
        if (newVersion.isEmpty()) return false;

        if (newVersion.length() > MAX_VERSION_CHARS) {
            newVersion = newVersion.substring(0, MAX_VERSION_CHARS);
        }

        boolean success = patchMemory(newVersion);
        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PREF_CUSTOM_VERSION, newVersion)
                    .apply();
        }
        return success;
    }

    public static boolean resetToDefault(Context context) {
        String defaultVer = BuildConfig.VERSION_NAME;
        boolean success = patchMemory(defaultVer);
        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .remove(PREF_CUSTOM_VERSION)
                    .apply();
        }
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

        if (newVersion.length() > MAX_VERSION_CHARS) {
            newVersion = newVersion.substring(0, MAX_VERSION_CHARS);
        }

        byte[] newVerBytes = newVersion.getBytes(StandardCharsets.US_ASCII);
        byte[] writeBuf = new byte[12];
        Arrays.fill(writeBuf, (byte) 0);
        System.arraycopy(newVerBytes, 0, writeBuf, 0, newVerBytes.length);

        File memFile = new File("/proc/self/mem");
        if (!memFile.exists()) {
            Log.e(TAG, "/proc/self/mem does not exist");
            return false;
        }

        // 1. Try fast path if address is already cached in this process
        if (cachedTargetAddress > 0) {
            try (RandomAccessFile memRaf = new RandomAccessFile(memFile, "rw")) {
                memRaf.seek(cachedTargetAddress);
                memRaf.write(writeBuf);
                String current = TeamTalkBase.getVersion();
                if (newVersion.equals(current)) {
                    Log.i(TAG, "Cached memory write verified: " + current);
                    return true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Cached write failed, falling back to full scan: " + e.getMessage());
                cachedTargetAddress = 0;
            }
        }

        // 2. Full memory scan via /proc/self/maps
        File mapsFile = new File("/proc/self/maps");
        if (!mapsFile.exists()) {
            Log.e(TAG, "/proc/self/maps does not exist");
            return false;
        }

        boolean patched = false;
        String currentDllVersion = null;
        try {
            currentDllVersion = TeamTalkBase.getVersion();
        } catch (Throwable ignored) {}

        try (BufferedReader mapsReader = new BufferedReader(new FileReader(mapsFile));
             RandomAccessFile memRaf = new RandomAccessFile(memFile, "rw")) {

            String line;
            Pattern mapPattern = Pattern.compile("^([0-9a-fA-F]+)-([0-9a-fA-F]+)\\s+([rwxsp-]+)\\s+");

            while ((line = mapsReader.readLine()) != null) {
                // Focus on libTeamTalk5-jni.so mappings or anonymous readable regions
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

                        int foundIndex = findVersionOffset(buffer, currentDllVersion);
                        if (foundIndex != -1) {
                            long targetAddr = start + foundIndex;
                            Log.i(TAG, "Found target version offset at 0x" + Long.toHexString(targetAddr));

                            memRaf.seek(targetAddr);
                            memRaf.write(writeBuf);
                            cachedTargetAddress = targetAddr;
                            patched = true;

                            String updated = TeamTalkBase.getVersion();
                            Log.i(TAG, "Successfully patched native memory! TeamTalkBase.getVersion() is now: " + updated);
                            break;
                        }
                    } catch (Exception ignored) {
                        // Skip unreadable memory pages
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to patch memory: " + e.getMessage(), e);
        }

        return patched;
    }

    private static int findVersionOffset(byte[] data, String currentVersion) {
        // Priority 1: Check ARM64 anchor "abusePrevent\0"
        int idx = indexOf(data, ANCHOR_ARM64);
        if (idx != -1) {
            int verOffset = idx + ANCHOR_ARM64.length;
            if (verOffset + 12 <= data.length) {
                return verOffset;
            }
        }

        // Priority 2: Check ARM32/x86 anchor "webm_vp8\0"
        idx = indexOf(data, ANCHOR_OTHER);
        if (idx != -1) {
            int verOffset = idx + ANCHOR_OTHER.length;
            if (verOffset + 12 <= data.length) {
                return verOffset;
            }
        }

        // Priority 3: Search for currentVersion string in memory
        if (currentVersion != null && !currentVersion.isEmpty()) {
            byte[] curBytes = (currentVersion + "\0").getBytes(StandardCharsets.US_ASCII);
            idx = indexOf(data, curBytes);
            if (idx != -1) {
                return idx;
            }
        }

        return -1;
    }

    private static int indexOf(byte[] array, byte[] target) {
        if (target.length == 0 || array.length < target.length) return -1;
        int limit = array.length - target.length;
        for (int i = 0; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
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
                    String applied = TeamTalkBase.getVersion();
                    Toast.makeText(activity, activity.getString(R.string.msg_custom_version_applied, applied), Toast.LENGTH_SHORT).show();
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
                String defVer = TeamTalkBase.getVersion();
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
