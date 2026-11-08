package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CrashHistoryManager {
    private static final String TAG = "CrashHistoryManager";
    private static final String DIR_NAME = "crash_logs";
    private static final int MAX_ENTRIES = 100;

    public static class CrashEntry implements Comparable<CrashEntry> {
        public String id;
        public long timestamp;
        public String dateStr;
        public boolean fatal;
        public String threadName;
        public String exceptionClass;
        public String message;
        public String stackTrace;
        public String deviceInfo;

        @Override
        public int compareTo(CrashEntry o) {
            return Long.compare(o.timestamp, this.timestamp);
        }

        public String getFormattedText() {
            StringBuilder sb = new StringBuilder();
            sb.append("==================================================\n");
            sb.append(fatal ? "[CRASH / ВЫЛЕТ] " : "[ERROR / ОШИБКА] ")
              .append(dateStr != null ? dateStr : "").append("\n");
            sb.append("Тип: ").append(exceptionClass != null ? exceptionClass : "Unknown").append("\n");
            if (message != null && !message.isEmpty()) {
                sb.append("Сообщение: ").append(message).append("\n");
            }
            sb.append("Поток: ").append(threadName != null ? threadName : "unknown").append("\n");
            sb.append("--------------------------------------------------\n");
            sb.append("Информация об устройстве:\n");
            sb.append(deviceInfo != null ? deviceInfo : "No device info").append("\n");
            sb.append("--------------------------------------------------\n");
            sb.append("Стек вызовов (Stack Trace):\n");
            sb.append(stackTrace != null ? stackTrace : "No stack trace").append("\n");
            sb.append("==================================================\n");
            return sb.toString();
        }
    }

    public static File getCrashDir(Context context) {
        File dir = new File(context.getFilesDir(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String buildDeviceInfo(Context context, Thread thread) {
        String versionName = "Unknown";
        int versionCode = 0;
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = pInfo.versionName;
            versionCode = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String abiStr = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "unknown";
        return "TeamTalk Next v" + versionName + " (" + versionCode + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n"
                + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "ABI: " + abiStr + "\n"
                + "Thread: " + (thread != null ? thread.getName() : "unknown") + "\n"
                + "Time: " + dateStr;
    }

    public static void saveCrash(Context context, Thread thread, Throwable throwable, String stackTrace, String deviceInfo, boolean fatal) {
        if (context == null) return;
        try {
            long now = System.currentTimeMillis();
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(now));
            String threadName = thread != null ? thread.getName() : "unknown";
            String exceptionClass = throwable != null ? throwable.getClass().getName() : "UnknownException";
            String message = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "";

            if (stackTrace == null && throwable != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                stackTrace = sw.toString();
            }

            if (deviceInfo == null) {
                deviceInfo = buildDeviceInfo(context, thread);
            }

            String id = "crash_" + now + "_" + ((int) (Math.random() * 1000));

            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("timestamp", now);
            json.put("dateStr", dateStr);
            json.put("fatal", fatal);
            json.put("threadName", threadName);
            json.put("exceptionClass", exceptionClass);
            json.put("message", message);
            json.put("stackTrace", stackTrace);
            json.put("deviceInfo", deviceInfo);

            File dir = getCrashDir(context);
            File file = new File(dir, id + ".json");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }

            trimOldFiles(dir, MAX_ENTRIES);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to save crash log", t);
        }
    }

    public static void logHandledException(Context context, Throwable throwable, String note) {
        if (context == null || throwable == null) return;
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sw.toString();
            if (note != null && !note.isEmpty()) {
                stackTrace = "Note: " + note + "\n\n" + stackTrace;
            }
            saveCrash(context, Thread.currentThread(), throwable, stackTrace, null, false);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to log handled exception", t);
        }
    }

    public static List<CrashEntry> getCrashes(Context context) {
        List<CrashEntry> list = new ArrayList<>();
        if (context == null) return list;
        try {
            File dir = getCrashDir(context);
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) return list;

            for (File f : files) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] data = new byte[(int) f.length()];
                    int read = fis.read(data);
                    if (read <= 0) continue;
                    String content = new String(data, 0, read, StandardCharsets.UTF_8);
                    JSONObject json = new JSONObject(content);

                    CrashEntry entry = new CrashEntry();
                    entry.id = json.optString("id", f.getName());
                    entry.timestamp = json.optLong("timestamp", f.lastModified());
                    entry.dateStr = json.optString("dateStr", "");
                    entry.fatal = json.optBoolean("fatal", true);
                    entry.threadName = json.optString("threadName", "");
                    entry.exceptionClass = json.optString("exceptionClass", "");
                    entry.message = json.optString("message", "");
                    entry.stackTrace = json.optString("stackTrace", "");
                    entry.deviceInfo = json.optString("deviceInfo", "");

                    list.add(entry);
                } catch (Throwable e) {
                    Log.e(TAG, "Failed reading crash file: " + f.getName(), e);
                }
            }
            Collections.sort(list);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to list crashes", t);
        }
        return list;
    }

    public static String getAllCrashesFormatted(Context context) {
        List<CrashEntry> list = getCrashes(context);
        if (list.isEmpty()) {
            return "История ошибок пуста (вылетов не зафиксировано).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append("       TEAMTALK NEXT — ЖУРНАЛ ОШИБОК И ВЫЛЕТОВ\n");
        sb.append("==================================================\n");
        sb.append("Всего записей: ").append(list.size()).append("\n");
        sb.append("Сгенерировано: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
        for (CrashEntry entry : list) {
            sb.append(entry.getFormattedText()).append("\n\n");
        }
        return sb.toString();
    }

    public static void clearAll(Context context) {
        if (context == null) return;
        try {
            File dir = getCrashDir(context);
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to clear crash files", t);
        }
    }

    public static boolean deleteCrash(Context context, String id) {
        if (context == null || id == null) return false;
        try {
            File dir = getCrashDir(context);
            File f = new File(dir, id.endsWith(".json") ? id : id + ".json");
            return f.delete();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to delete crash " + id, t);
            return false;
        }
    }

    private static void trimOldFiles(File dir, int max) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null && files.length > max) {
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            int toDelete = files.length - max;
            for (int i = 0; i < toDelete; i++) {
                files[i].delete();
            }
        }
    }
}
