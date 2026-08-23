package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class LocaleHelper {

    public static final String LANGUAGE_PREF_KEY = "app_language";

    public static void applyLanguage(Context context) {
        if (context == null) return;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lang = prefs.getString(LANGUAGE_PREF_KEY, "system");
        applyLanguage(lang);
    }

    public static void applyLanguage(String lang) {
        if (TextUtils.isEmpty(lang) || "system".equalsIgnoreCase(lang) || "default".equalsIgnoreCase(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));
        }
    }
}
