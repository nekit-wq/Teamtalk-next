package org.nekit.ttproplus.gui;

import android.app.Application;

public class TeamTalkApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CrashHandler.init(this);
    }
}
