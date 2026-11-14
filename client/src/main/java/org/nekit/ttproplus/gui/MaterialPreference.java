package org.nekit.ttproplus.gui;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;

public class MaterialPreference extends Preference {

    public MaterialPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MaterialPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MaterialPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaterialPreference(Context context) {
        super(context);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        PreferenceDependencyHelper.onBindPreferenceView(this, view);
    }
}
