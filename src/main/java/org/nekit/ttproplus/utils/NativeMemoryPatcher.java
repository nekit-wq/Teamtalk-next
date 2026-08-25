package org.nekit.ttproplus.utils;

import android.app.Activity;
import android.content.Context;

public class NativeMemoryPatcher {
    public static String getCustomVersion(Context context) {
        return VersionManager.getCustomVersion(context);
    }

    public static boolean setCustomVersion(Context context, String newVersion) {
        return VersionManager.setCustomVersion(context, newVersion);
    }

    public static boolean resetToDefault(Context context) {
        return VersionManager.resetToDefault(context);
    }

    public static void applyCustomVersion(Context context) {
        // No-op for safety and instant 0.01s startup
    }

    public static void showChangeVersionDialog(final Activity activity, final Runnable onVersionChanged) {
        VersionManager.showChangeVersionDialog(activity, onVersionChanged);
    }
}
