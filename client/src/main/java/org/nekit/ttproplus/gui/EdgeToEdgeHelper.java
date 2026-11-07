/*
 * Copyright (c) 2005-2018, BearWare.dk
 * 
 * Contact Information:
 *
 * Bjoern D. Rasmussen
 * Kirketoften 5
 * DK-8260 Viby J
 * Denmark
 * Email: contact@bearware.dk
 * Phone: +45 20 20 54 59
 * Web: http://www.bearware.dk
 *
 * This source code is part of the TeamTalk SDK owned by
 * BearWare.dk. Use of this file, or its compiled unit, requires a
 * TeamTalk SDK License Key issued by BearWare.dk.
 *
 * The TeamTalk SDK License Agreement along with its Terms and
 * Conditions are outlined in the file License.txt included with the
 * TeamTalk SDK distribution.
 *
 */

package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class EdgeToEdgeHelper {

    public static void enableEdgeToEdge(Activity activity) {
        if (activity == null) {
            return;
        }

        View contentView = activity.findViewById(android.R.id.content);
        if (contentView == null) {
            return;
        }

        // On modern Android (API 35+ / Android 15 & 16), edge-to-edge is enforced by the platform.
        // If an Activity has an ActionBar / Toolbar at the top, the ActionBar is already below the status bar.
        // We must NOT apply the status bar topInset to android.R.id.content, otherwise it creates
        // a massive blank / gray gap between the top of the content and the ActionBar!
        ViewCompat.setOnApplyWindowInsetsListener(contentView, (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            boolean hasActionBar = false;
            if (activity instanceof AppCompatActivity) {
                androidx.appcompat.app.ActionBar ab = ((AppCompatActivity) activity).getSupportActionBar();
                hasActionBar = (ab != null && ab.isShowing());
            } else if (activity instanceof PreferencesActivity) {
                hasActionBar = true;
            } else if (activity.getActionBar() != null && activity.getActionBar().isShowing()) {
                hasActionBar = true;
            }

            int topPadding = hasActionBar ? 0 : sysBars.top;
            int bottomPadding = sysBars.bottom;

            v.setPadding(sysBars.left, topPadding, sysBars.right, bottomPadding);
            return insets;
        });
    }
}
