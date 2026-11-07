package org.nekit.ttproplus.gui;

import android.app.Application;
import android.util.Log;
import com.google.android.material.color.DynamicColors;
import org.nekit.ttproplus.plugin.PluginManager;

public class TeamTalkApp extends Application {

    private static final String TAG = "TeamTalkApp";

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.init(this);
        try {
            DynamicColors.applyToActivitiesIfAvailable(this);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply DynamicColors", t);
        }
        try {
            PluginManager.getInstance().init(this, null);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize PluginManager", t);
        }
    }
}
