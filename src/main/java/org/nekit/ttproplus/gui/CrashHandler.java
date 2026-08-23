package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "TeamTalkCrash";
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void init(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread " + thread.getName(), throwable);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTrace = sw.toString();

            String versionName = "Unknown";
            int versionCode = 0;
            try {
                PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                versionName = pInfo.versionName;
                versionCode = pInfo.versionCode;
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            String deviceInfo = "TeamTalk Pro+ v" + versionName + " (" + versionCode + ")\n"
                    + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")\n"
                    + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                    + "ABI: " + Build.CPU_ABI + "\n"
                    + "Thread: " + thread.getName() + "\n"
                    + "Time: " + dateStr;

            Intent crashIntent = new Intent(context, CrashReportActivity.class);
            crashIntent.putExtra(CrashReportActivity.EXTRA_ERROR_TEXT, stackTrace);
            crashIntent.putExtra(CrashReportActivity.EXTRA_DEVICE_INFO, deviceInfo);
            crashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            context.startActivity(crashIntent);

            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        } catch (Throwable t) {
            Log.e(TAG, "Failed in CrashHandler", t);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }
}
