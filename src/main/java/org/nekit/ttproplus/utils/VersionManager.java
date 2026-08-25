package org.nekit.ttproplus.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import dk.bearware.TeamTalkBase;
import org.nekit.ttproplus.BuildConfig;
import org.nekit.ttproplus.R;

public class VersionManager {
    public static final String PREF_CUSTOM_VERSION = "custom_client_version";

    public static String getCustomVersion(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(PREF_CUSTOM_VERSION, null);
    }

    public static String getEffectiveVersion(Context context) {
        String custom = getCustomVersion(context);
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        return BuildConfig.VERSION_NAME;
    }

    public static String getEffectiveDllVersion(Context context) {
        String custom = getCustomVersion(context);
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        try {
            return TeamTalkBase.getVersion();
        } catch (Throwable t) {
            return BuildConfig.VERSION_NAME;
        }
    }

    public static boolean setCustomVersion(Context context, String newVersion) {
        if (newVersion == null) return false;
        newVersion = newVersion.trim();
        if (newVersion.isEmpty()) return false;

        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(PREF_CUSTOM_VERSION, newVersion)
                    .apply();
        }
        return true;
    }

    public static boolean resetToDefault(Context context) {
        if (context != null) {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .remove(PREF_CUSTOM_VERSION)
                    .apply();
        }
        return true;
    }

    public static void showChangeVersionDialog(final Activity activity, final Runnable onVersionChanged) {
        if (activity == null || activity.isFinishing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.pref_custom_version_title);

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        String current = getEffectiveDllVersion(activity);
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
