package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatDelegate;
import org.nekit.ttproplus.R;

public class ThemeHelper {

    public static final String THEME_PREF_KEY = "theme_preference";
    public static final String ACCENT_COLOR_PREF_KEY = "accent_color_preference";
    public static final String ACCENT_COLOR_CUSTOM_HEX_KEY = "accent_color_custom_hex";

    public static class AccentColorEntry {
        public final String key;
        public final String name;
        public final int color;
        public final int styleRes;

        public AccentColorEntry(String key, String name, int color, int styleRes) {
            this.key = key;
            this.name = name;
            this.color = color;
            this.styleRes = styleRes;
        }
    }

    public static final AccentColorEntry[] ACCENT_COLORS = new AccentColorEntry[] {
            new AccentColorEntry("blue", "Синий (TeamTalk)", 0xFF0B57D0, R.style.ThemeOverlay_Accent_Blue),
            new AccentColorEntry("indigo", "Индиго", 0xFF3F51B5, R.style.ThemeOverlay_Accent_Indigo),
            new AccentColorEntry("deep_purple", "Фиолетовый", 0xFF673AB7, R.style.ThemeOverlay_Accent_DeepPurple),
            new AccentColorEntry("purple", "Пурпурный", 0xFF9C27B0, R.style.ThemeOverlay_Accent_Purple),
            new AccentColorEntry("pink", "Розовый", 0xFFE91E63, R.style.ThemeOverlay_Accent_Pink),
            new AccentColorEntry("red", "Красный", 0xFFD32F2F, R.style.ThemeOverlay_Accent_Red),
            new AccentColorEntry("crimson", "Бордовый", 0xFF880E4F, R.style.ThemeOverlay_Accent_Crimson),
            new AccentColorEntry("deep_orange", "Тёмно-оранжевый", 0xFFF4511E, R.style.ThemeOverlay_Accent_DeepOrange),
            new AccentColorEntry("orange", "Оранжевый", 0xFFFB8C00, R.style.ThemeOverlay_Accent_Orange),
            new AccentColorEntry("amber", "Янтарный", 0xFFFFA000, R.style.ThemeOverlay_Accent_Amber),
            new AccentColorEntry("yellow", "Жёлтый", 0xFFFBC02D, R.style.ThemeOverlay_Accent_Yellow),
            new AccentColorEntry("lime", "Лайм", 0xFF689F38, R.style.ThemeOverlay_Accent_Lime),
            new AccentColorEntry("green", "Зелёный", 0xFF2E7D32, R.style.ThemeOverlay_Accent_Green),
            new AccentColorEntry("teal", "Изумрудный", 0xFF00897B, R.style.ThemeOverlay_Accent_Teal),
            new AccentColorEntry("cyan", "Морская волна", 0xFF0097A7, R.style.ThemeOverlay_Accent_Cyan),
            new AccentColorEntry("light_blue", "Голубой", 0xFF0288D1, R.style.ThemeOverlay_Accent_LightBlue),
            new AccentColorEntry("brown", "Коричневый", 0xFF6D4C41, R.style.ThemeOverlay_Accent_Brown),
            new AccentColorEntry("blue_grey", "Сине-серый", 0xFF546E7A, R.style.ThemeOverlay_Accent_BlueGrey),
    };

    public static AccentColorEntry getAccentEntry(String key) {
        if (key != null) {
            for (AccentColorEntry entry : ACCENT_COLORS) {
                if (entry.key.equalsIgnoreCase(key)) {
                    return entry;
                }
            }
        }
        return ACCENT_COLORS[0];
    }

    public static int getAccentThemeOverlay(String key) {
        return getAccentEntry(key).styleRes;
    }

    public static String getClosestPresetKey(int colorInt) {
        int r1 = Color.red(colorInt);
        int g1 = Color.green(colorInt);
        int b1 = Color.blue(colorInt);
        double minDistance = Double.MAX_VALUE;
        String closestKey = "blue";
        for (AccentColorEntry entry : ACCENT_COLORS) {
            int r2 = Color.red(entry.color);
            int g2 = Color.green(entry.color);
            int b2 = Color.blue(entry.color);
            long rmean = (r1 + r2) / 2;
            long dr = r1 - r2;
            long dg = g1 - g2;
            long db = b1 - b2;
            double dist = (((512 + rmean) * dr * dr) >> 8) + (4 * dg * dg) + (((767 - rmean) * db * db) >> 8);
            if (dist < minDistance) {
                minDistance = dist;
                closestKey = entry.key;
            }
        }
        return closestKey;
    }

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
        LocaleHelper.applyLanguage(context);

        if (context instanceof Activity) {
            String accentKey = prefs.getString(ACCENT_COLOR_PREF_KEY, "blue");
            int overlay = getAccentThemeOverlay(accentKey);
            ((Activity) context).getTheme().applyStyle(overlay, true);
        }
    }

    public static int resolveThemeColor(Context context, int attr, int defaultColor) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            return typedValue.data;
        }
        return defaultColor;
    }

    public static int getPrimaryColor(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String customHex = prefs.getString(ACCENT_COLOR_CUSTOM_HEX_KEY, null);
        if (customHex != null && !customHex.isEmpty()) {
            try {
                return Color.parseColor(customHex);
            } catch (Exception ignored) {}
        }
        String key = prefs.getString(ACCENT_COLOR_PREF_KEY, "blue");
        return getAccentEntry(key).color;
    }

    public static android.view.View wrapForDialog(android.view.View view, Context context) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        int marginHorizontal = (int) (24 * context.getResources().getDisplayMetrics().density);
        int marginVertical = (int) (12 * context.getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical);
        view.setLayoutParams(lp);
        container.addView(view);
        return container;
    }
}
