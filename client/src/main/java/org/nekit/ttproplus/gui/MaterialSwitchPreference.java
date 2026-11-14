package org.nekit.ttproplus.gui;

import android.content.Context;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;

import com.google.android.material.materialswitch.MaterialSwitch;
import org.nekit.ttproplus.R;

public class MaterialSwitchPreference extends SwitchPreference {

    public MaterialSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public MaterialSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public MaterialSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MaterialSwitchPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_material_switch);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        PreferenceDependencyHelper.onBindPreferenceView(this, view);
        View widget = view.findViewById(R.id.material_switch_widget);
        if (widget instanceof MaterialSwitch) {
            MaterialSwitch switchView = (MaterialSwitch) widget;
            switchView.setOnCheckedChangeListener(null);
            switchView.setChecked(isChecked());
            switchView.setEnabled(isEnabled());
            switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!callChangeListener(isChecked)) {
                    buttonView.setChecked(!isChecked);
                    return;
                }
                setChecked(isChecked);
            });
        }
    }
}
