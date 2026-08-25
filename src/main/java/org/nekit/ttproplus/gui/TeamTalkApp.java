package org.nekit.ttproplus.gui;

import android.app.Application;
import org.nekit.ttproplus.utils.NativeMemoryPatcher;

public class TeamTalkApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.init(this);
        NativeMemoryPatcher.applyCustomVersion(this);
    }
}
