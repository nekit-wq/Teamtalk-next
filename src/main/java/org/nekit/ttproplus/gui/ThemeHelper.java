package org.nekit.ttproplus.gui;
import org.nekit.ttproplus.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeHelper {

    public static final String THEME_PREF_KEY = "theme_preference";

    public static void applyTheme(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = prefs.getString(THEME_PREF_KEY, "dark");
        int targetMode;
        switch (theme) {
            case "white":
                targetMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case "auto":
                targetMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                break;
            case "dark":
            default:
                targetMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode);
        }
    }
}
