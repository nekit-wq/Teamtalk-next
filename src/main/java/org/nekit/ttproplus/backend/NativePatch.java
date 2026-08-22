package org.nekit.ttproplus.backend;

import android.util.Log;

public class NativePatch {
    private static final String TAG = "NativePatch";
    private static boolean sLoaded = false;

    static {
        try {
            System.loadLibrary("nativepatch");
            sLoaded = true;
            Log.i(TAG, "NativePatch library loaded successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load nativepatch library", t);
        }
    }

    public static native boolean setClientVersion(String versionStr);

    public static boolean applyVersion(String version) {
        if (!sLoaded || version == null || version.trim().isEmpty()) {
            return false;
        }
        try {
            return setClientVersion(version.trim());
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply native version: " + version, t);
            return false;
        }
    }
}
