package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IpGeoLocator {

    private static final String CACHE_PREFIX = "geo_";
    private static final LruCache<String, String> memoryCache = new LruCache<>(200);
    private static final ExecutorService geoExecutor = Executors.newFixedThreadPool(2);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LocationCallback {
        void onLocationResult(String location);
    }

    public static void getLocation(String ip, Context context, LocationCallback callback) {
        if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || ip.equals("localhost")) {
            callback.onLocationResult(null);
            return;
        }

        String cached = memoryCache.get(ip);
        if (cached != null) {
            callback.onLocationResult(cached);
            return;
        }

        final Context appContext = context.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        String saved = prefs.getString(CACHE_PREFIX + ip, null);
        if (saved != null) {
            memoryCache.put(ip, saved);
            callback.onLocationResult(saved);
            return;
        }

        geoExecutor.execute(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            String result = null;
            try {
                URL url = new URL("http://ip-api.com/json/" + ip + "?fields=country,city");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "TeamTalkAndroid");

                reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8), 1024);
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                JSONObject json = new JSONObject(response.toString());
                String country = json.optString("country", "");
                String city = json.optString("city", "");

                if (!country.isEmpty() && !city.isEmpty()) {
                    result = city + ", " + country;
                    memoryCache.put(ip, result);
                    prefs.edit().putString(CACHE_PREFIX + ip, result).apply();
                } else if (!country.isEmpty()) {
                    result = country;
                    memoryCache.put(ip, result);
                    prefs.edit().putString(CACHE_PREFIX + ip, result).apply();
                }
            } catch (Exception ignored) {
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {}
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }

            final String finalResult = result;
            mainHandler.post(() -> callback.onLocationResult(finalResult));
        });
    }
}
