package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.LruCache;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class IpGeoLocator {

    private static final String CACHE_PREFIX = "geo_";
    private static final LruCache<String, String> memoryCache = new LruCache<>(200);

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

        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                HttpURLConnection conn = null;
                BufferedReader reader = null;
                try {
                    URL url = new URL("http://ip-api.com/json/" + ip + "?fields=country,city");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "TeamTalkAndroid");

                    reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8), 1024);
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    JSONObject json = new JSONObject(response.toString());
                    String country = json.optString("country", "");
                    String city = json.optString("city", "");

                    if (!country.isEmpty() && !city.isEmpty()) {
                        String result = city + ", " + country;
                        memoryCache.put(ip, result);
                        prefs.edit().putString(CACHE_PREFIX + ip, result).apply();
                        return result;
                    } else if (!country.isEmpty()) {
                        memoryCache.put(ip, country);
                        prefs.edit().putString(CACHE_PREFIX + ip, country).apply();
                        return country;
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
                return null;
            }

            @Override
            protected void onPostExecute(String result) {
                callback.onLocationResult(result);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
