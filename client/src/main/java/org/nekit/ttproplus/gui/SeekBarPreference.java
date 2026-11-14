package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SeekBarPreference extends DialogPreference implements OnSeekBarChangeListener {

    int value = 100;
    int defaultValue = 100;

    public SeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        defaultValue = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "defaultValue", 100);
        value = defaultValue;
    }

    public SeekBarPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        defaultValue = attrs.getAttributeIntValue("http://schemas.android.com/apk/res/android", "defaultValue", 100);
        value = defaultValue;
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defVal) {
        if (restorePersistedValue) {
            value = getPersistedInt(defaultValue);
        } else {
            value = (defVal instanceof Integer) ? (Integer) defVal : defaultValue;
            persistInt(value);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInt(index, 100);
    }

    @Override
    protected void onBindView(android.view.View view) {
        super.onBindView(view);
        PreferenceDependencyHelper.onBindPreferenceView(this, view);
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        final int initialValue = getPersistedInt(value);
        final int[] currentValue = new int[]{ initialValue };

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (24 * context.getResources().getDisplayMetrics().density);
        int padV = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padH, padV, padH, padV);

        final TextView valText = new TextView(context);
        valText.setGravity(Gravity.CENTER_HORIZONTAL);
        valText.setText(initialValue + "%");
        valText.setTextSize(20f);
        valText.setTypeface(null, Typeface.BOLD);
        valText.setTextColor(ThemeHelper.getPrimaryColor(context));
        valText.setPadding(0, 0, 0, (int) (16 * context.getResources().getDisplayMetrics().density));
        layout.addView(valText);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setSplitTrack(false);
        seekBar.setMax(100);
        seekBar.setProgress(initialValue);
        seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                currentValue[0] = progress;
                valText.setText(progress + "%");
            }
            @Override
            public void onStartTrackingTouch(SeekBar sb) {}
            @Override
            public void onStopTrackingTouch(SeekBar sb) {}
        });
        layout.addView(seekBar);

        CharSequence title = getDialogTitle();
        if (title == null || title.length() == 0) {
            title = getTitle();
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setIcon(getDialogIcon())
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (callChangeListener(currentValue[0])) {
                        value = currentValue[0];
                        persistInt(value);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        value = seekBar.getProgress();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seek) {}

    @Override
    public void onStopTrackingTouch(SeekBar seek) {}
}
