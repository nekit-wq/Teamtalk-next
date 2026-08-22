package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.app.AlertDialog;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.nekit.ttproplus.BuildConfig;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.data.Preferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/nekit-wq/Teamtalk-pro-plus/releases/latest";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void checkUpdate(final Activity activity, final boolean userInitiated) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        executor.execute(() -> {
            try {
                URL url = new URL(GITHUB_API_LATEST_RELEASE);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "TeamTalkProPlus-Android/" + BuildConfig.VERSION_NAME);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "GitHub API returned HTTP " + responseCode);
                    if (userInitiated) {
                        mainHandler.post(() -> {
                            if (!activity.isFinishing()) {
                                Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject json = new JSONObject(response.toString());
                final String tagName = json.optString("tag_name", "");
                final String releaseName = json.optString("name", tagName);
                final String releaseNotes = json.optString("body", "");

                // Find APK download URL from release assets
                String apkDownloadUrl = null;
                String apkName = null;
                long apkSize = 0;

                JSONArray assets = json.optJSONArray("assets");
                if (assets != null && assets.length() > 0) {
                    // Priority 1: Universal APK
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase().endsWith(".apk") && assetName.toLowerCase().contains("universal")) {
                            apkDownloadUrl = asset.optString("browser_download_url", null);
                            apkName = assetName;
                            apkSize = asset.optLong("size", 0);
                            break;
                        }
                    }
                    // Priority 2: ARM64 APK
                    if (apkDownloadUrl == null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "");
                            if (assetName.toLowerCase().endsWith(".apk") && assetName.toLowerCase().contains("arm64")) {
                                apkDownloadUrl = asset.optString("browser_download_url", null);
                                apkName = assetName;
                                apkSize = asset.optLong("size", 0);
                                break;
                            }
                        }
                    }
                    // Priority 3: Any APK
                    if (apkDownloadUrl == null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String assetName = asset.optString("name", "");
                            if (assetName.toLowerCase().endsWith(".apk")) {
                                apkDownloadUrl = asset.optString("browser_download_url", null);
                                apkName = assetName;
                                apkSize = asset.optLong("size", 0);
                                break;
                            }
                        }
                    }
                }

                final String finalApkUrl = apkDownloadUrl;
                final String finalApkName = !TextUtils.isEmpty(apkName) ? apkName : "TeamTalkProPlus-" + tagName + ".apk";
                final long finalApkSize = apkSize;

                String currentVersion = BuildConfig.VERSION_NAME;
                boolean isNewer = isVersionNewer(tagName, currentVersion);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                String ignoredVersion = prefs.getString(Preferences.PREF_IGNORED_UPDATE_VERSION, "");

                if ((isNewer || (userInitiated && !normalizeVersion(tagName).equalsIgnoreCase(normalizeVersion(currentVersion)))) && !TextUtils.isEmpty(finalApkUrl)) {
                    if (!userInitiated && tagName.equalsIgnoreCase(ignoredVersion)) {
                        Log.d(TAG, "Update " + tagName + " was ignored by user.");
                        return;
                    }

                    mainHandler.post(() -> {
                        if (!activity.isFinishing()) {
                            showUpdateDialog(activity, tagName, releaseName, releaseNotes, finalApkUrl, finalApkName, finalApkSize);
                        }
                    });
                } else {
                    if (userInitiated) {
                        mainHandler.post(() -> {
                            if (!activity.isFinishing()) {
                                Toast.makeText(activity, activity.getString(R.string.update_already_latest, currentVersion), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error checking for updates", e);
                if (userInitiated) {
                    mainHandler.post(() -> {
                        if (!activity.isFinishing()) {
                            Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private static String normalizeVersion(String v) {
        if (v == null) return "";
        return v.replaceAll("^[vV]", "").trim();
    }

    private static boolean isVersionNewer(String latestTag, String currentVersion) {
        if (TextUtils.isEmpty(latestTag) || TextUtils.isEmpty(currentVersion)) {
            return false;
        }

        String v1 = normalizeVersion(latestTag);
        String v2 = normalizeVersion(currentVersion);

        if (v1.equalsIgnoreCase(v2)) {
            return false;
        }

        String[] parts1 = v1.split("[.\\-_]");
        String[] parts2 = v2.split("[.\\-_]");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = 0;
            int num2 = 0;
            if (i < parts1.length) {
                try {
                    num1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {}
            }
            if (i < parts2.length) {
                try {
                    num2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
                } catch (NumberFormatException ignored) {}
            }
            if (num1 > num2) {
                return true;
            }
            if (num1 < num2) {
                return false;
            }
        }
        return parts1.length > parts2.length;
    }

    private static void showUpdateDialog(final Activity activity, final String tagName, final String releaseName,
                                         final String releaseNotes, final String downloadUrl, final String apkName,
                                         final long apkSize) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.update_available_title, tagName));

        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_update, null);
        TextView tvNotes = dialogView.findViewById(R.id.update_notes);
        CheckBox chkDontShow = dialogView.findViewById(R.id.update_dont_show_again);

        if (!TextUtils.isEmpty(releaseNotes)) {
            tvNotes.setText(releaseNotes);
        } else {
            tvNotes.setText(releaseName);
        }

        builder.setView(dialogView);

        builder.setPositiveButton(R.string.update_btn_now, (dialog, which) -> {
            downloadAndInstall(activity, downloadUrl, apkName, apkSize);
        });

        builder.setNegativeButton(R.string.update_btn_later, (dialog, which) -> {
            if (chkDontShow.isChecked()) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                prefs.edit().putString(Preferences.PREF_IGNORED_UPDATE_VERSION, tagName).apply();
            }
            dialog.dismiss();
        });

        builder.setCancelable(true);
        builder.show();
    }

    private static void downloadAndInstall(final Activity activity, final String downloadUrl, final String apkName, final long totalBytesExpected) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.update_downloading_title);
        builder.setCancelable(false);

        View progressView = LayoutInflater.from(activity).inflate(R.layout.dialog_download_progress, null);
        ProgressBar progressBar = progressView.findViewById(R.id.download_progress_bar);
        TextView tvStatus = progressView.findViewById(R.id.download_status_text);
        builder.setView(progressView);

        final AtomicBoolean isCancelled = new AtomicBoolean(false);
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            isCancelled.set(true);
            dialog.dismiss();
        });

        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        executor.execute(() -> {
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;
            File apkFile = null;
            try {
                URL url = new URL(downloadUrl);
                connection = openConnectionFollowRedirects(url);

                int fileLength = connection.getContentLength();
                if (fileLength <= 0 && totalBytesExpected > 0) {
                    fileLength = (int) totalBytesExpected;
                }

                File downloadsDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir == null) {
                    downloadsDir = new File(activity.getCacheDir(), "updates");
                }
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                apkFile = new File(downloadsDir, apkName);
                if (apkFile.exists()) {
                    apkFile.delete();
                }

                input = connection.getInputStream();
                output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                long lastUiUpdate = 0;

                while ((count = input.read(data)) != -1) {
                    if (isCancelled.get()) {
                        output.close();
                        input.close();
                        if (apkFile.exists()) apkFile.delete();
                        return;
                    }

                    total += count;
                    output.write(data, 0, count);

                    long now = System.currentTimeMillis();
                    if (now - lastUiUpdate > 100 || total == fileLength) {
                        lastUiUpdate = now;
                        final int progress = fileLength > 0 ? (int) (total * 100 / fileLength) : -1;
                        final long totalDownloaded = total;
                        final long totalSize = fileLength;

                        mainHandler.post(() -> {
                            if (progressDialog.isShowing()) {
                                if (progress >= 0) {
                                    progressBar.setIndeterminate(false);
                                    progressBar.setProgress(progress);
                                    tvStatus.setText(activity.getString(R.string.update_download_progress_format,
                                            progress,
                                            totalDownloaded / (1024.0 * 1024.0),
                                            totalSize / (1024.0 * 1024.0)));
                                } else {
                                    progressBar.setIndeterminate(true);
                                    tvStatus.setText(String.format("%.1f MB", totalDownloaded / (1024.0 * 1024.0)));
                                }
                            }
                        });
                    }
                }

                output.flush();
                output.close();
                input.close();

                final File finalFile = apkFile;
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installApk(activity, finalFile);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error downloading update APK", e);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    if (!isCancelled.get()) {
                        Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static HttpURLConnection openConnectionFollowRedirects(URL url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "TeamTalkProPlus-Android/" + BuildConfig.VERSION_NAME);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);

        int redirects = 0;
        while (redirects < 5) {
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                url = new URL(newUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "TeamTalkProPlus-Android/" + BuildConfig.VERSION_NAME);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                redirects++;
            } else {
                break;
            }
        }
        return conn;
    }

    private static void installApk(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) {
            return;
        }

        try {
            Intent installIntent = new Intent(Intent.ACTION_VIEW);
            installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Uri apkUri = FileProvider.getUriForFile(activity.getApplicationContext(),
                    activity.getPackageName() + ".fileprovider", apkFile);

            installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            activity.startActivity(installIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start APK installer", e);
            Toast.makeText(activity, R.string.update_install_failed, Toast.LENGTH_LONG).show();
        }
    }
}
