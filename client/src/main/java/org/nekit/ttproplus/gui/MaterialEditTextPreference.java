package org.nekit.ttproplus.gui;

import android.content.Context;
import android.preference.EditTextPreference;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MaterialEditTextPreference extends EditTextPreference {

    public MaterialEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MaterialEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MaterialEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaterialEditTextPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        Context context = getContext();

        TextInputLayout inputLayout = new TextInputLayout(context);
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        CharSequence title = getDialogTitle();
        if (title == null || title.length() == 0) {
            title = getTitle();
        }
        if (title != null) {
            inputLayout.setHint(title);
        }

        TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
        if (getEditText() != null) {
            editText.setInputType(getEditText().getInputType());
            editText.setFilters(getEditText().getFilters());
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
        }

        String curVal = getText();
        editText.setText(curVal);
        if (curVal != null && curVal.length() > 0) {
            editText.setSelection(curVal.length());
        }

        FrameLayout container = new FrameLayout(context);
        int padH = (int) (24 * context.getResources().getDisplayMetrics().density);
        int padV = (int) (12 * context.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(padH, padV, padH, 0);
        inputLayout.setLayoutParams(lp);
        inputLayout.addView(editText);
        container.addView(inputLayout);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        if (getDialogIcon() != null) {
            builder.setIcon(getDialogIcon());
        }
        builder.setView(container);

        CharSequence posText = getPositiveButtonText();
        if (posText == null || posText.length() == 0) {
            posText = context.getString(android.R.string.ok);
        }
        builder.setPositiveButton(posText, (dialog, which) -> {
            String val = editText.getText() != null ? editText.getText().toString() : "";
            if (callChangeListener(val)) {
                setText(val);
            }
        });

        CharSequence negText = getNegativeButtonText();
        if (negText == null || negText.length() == 0) {
            negText = context.getString(android.R.string.cancel);
        }
        builder.setNegativeButton(negText, (dialog, which) -> dialog.dismiss());

        builder.show();
    }
}
