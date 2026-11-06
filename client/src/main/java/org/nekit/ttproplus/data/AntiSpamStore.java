package org.nekit.ttproplus.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Persistent storage for Anti-Spam nickname lists (ignore list and whitelist)
 * plus small heuristics used by the message filters.
 */
public final class AntiSpamStore {

    private static final Pattern LINK_PATTERN =
            Pattern.compile("(?i)(https?://|www\\.|t\\.me/|telegram\\.me/|discord\\.gg/)");

    private AntiSpamStore() {
    }

    public static String normalizeNick(String nick) {
        return nick == null ? "" : nick.trim().toLowerCase(Locale.ROOT);
    }

    public static Set<String> getNicks(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> stored = prefs.getStringSet(key, Collections.<String>emptySet());
        return new LinkedHashSet<>(stored);
    }

    /**
     * Adds a nickname unless the same nickname (case-insensitive) is already present.
     *
     * @return true when the nickname was added
     */
    public static boolean addNick(Context context, String key, String nick) {
        String display = nick == null ? "" : nick.trim();
        if (display.isEmpty()) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> current = new LinkedHashSet<>(prefs.getStringSet(key, Collections.<String>emptySet()));
        String normalized = normalizeNick(display);
        for (String existing : current) {
            if (normalizeNick(existing).equals(normalized)) {
                return false;
            }
        }
        current.add(display);
        prefs.edit().putStringSet(key, current).apply();
        return true;
    }

    public static void removeNick(Context context, String key, String nick) {
        if (nick == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> current = new LinkedHashSet<>(prefs.getStringSet(key, Collections.<String>emptySet()));
        String normalized = normalizeNick(nick);
        Iterator<String> iterator = current.iterator();
        while (iterator.hasNext()) {
            if (normalizeNick(iterator.next()).equals(normalized)) {
                iterator.remove();
            }
        }
        prefs.edit().putStringSet(key, current).apply();
    }

    public static boolean hasLink(String text) {
        return text != null && LINK_PATTERN.matcher(text).find();
    }

    public static boolean isShouting(String text) {
        if (text == null) {
            return false;
        }
        int letters = 0;
        int uppercase = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    uppercase++;
                }
            }
        }
        return letters >= 12 && uppercase * 10 >= letters * 7;
    }
}
