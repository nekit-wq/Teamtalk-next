package org.nekit.ttproplus.gui;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.View;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MaterialListPreference extends ListPreference {

    public MaterialListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MaterialListPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MaterialListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaterialListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        PreferenceDependencyHelper.onBindPreferenceView(this, view);
    }

    @Override
    protected void onClick() {
        CharSequence[] entries = getEntries();
        CharSequence[] entryValues = getEntryValues();
        if (entries == null || entryValues == null || entries.length == 0) {
            return;
        }

        int selectedIndex = findIndexOfValue(getValue());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext());
        CharSequence title = getDialogTitle();
        if (title == null || title.length() == 0) {
            title = getTitle();
        }
        builder.setTitle(title);
        if (getDialogIcon() != null) {
            builder.setIcon(getDialogIcon());
        }

        builder.setSingleChoiceItems(entries, selectedIndex, (dialog, which) -> {
            if (which >= 0 && which < entryValues.length) {
                String val = entryValues[which].toString();
                if (callChangeListener(val)) {
                    setValue(val);
                }
            }
            dialog.dismiss();
        });

        CharSequence negText = getNegativeButtonText();
        if (negText == null || negText.length() == 0) {
            negText = getContext().getString(android.R.string.cancel);
        }
        builder.setNegativeButton(negText, (dialog, which) -> dialog.dismiss());

        builder.show();
    }
}
