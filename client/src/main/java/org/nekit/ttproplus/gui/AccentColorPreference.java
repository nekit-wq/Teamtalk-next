package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.preference.Preference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.nekit.ttproplus.R;

import java.util.Locale;

public class AccentColorPreference extends Preference {

    private String selectedKey;
    private String selectedCustomHex;
    private View widgetPreview;

    public AccentColorPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public AccentColorPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public AccentColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AccentColorPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_color_widget);
        updateSummaryAndState();
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        widgetPreview = view.findViewById(R.id.color_preview_widget);
        updateWidgetColor();
    }

    private void updateSummaryAndState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        selectedKey = prefs.getString(ThemeHelper.ACCENT_COLOR_PREF_KEY, "blue");
        selectedCustomHex = prefs.getString(ThemeHelper.ACCENT_COLOR_CUSTOM_HEX_KEY, null);

        if (!TextUtils.isEmpty(selectedCustomHex)) {
            setSummary(selectedCustomHex.toUpperCase(Locale.ROOT));
        } else {
            ThemeHelper.AccentColorEntry entry = ThemeHelper.getAccentEntry(selectedKey);
            setSummary(entry.name);
        }
        updateWidgetColor();
    }

    private void updateWidgetColor() {
        if (widgetPreview != null) {
            int color = ThemeHelper.getPrimaryColor(getContext());
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            widgetPreview.setBackground(circle);
        }
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_accent_color_picker, null);
        LinearLayout paletteRows = dialogView.findViewById(R.id.palette_rows);
        View customPreview = dialogView.findViewById(R.id.custom_color_preview);
        EditText hexEdit = dialogView.findViewById(R.id.custom_hex_edit);

        final String[] tempKey = new String[]{selectedKey};
        final String[] tempCustomHex = new String[]{selectedCustomHex};
        final ImageView[] checkViews = new ImageView[ThemeHelper.ACCENT_COLORS.length];
        final boolean[] isInternalTextChange = new boolean[]{false};

        int dp40 = (int) (40 * context.getResources().getDisplayMetrics().density);
        int dp36 = (int) (36 * context.getResources().getDisplayMetrics().density);
        int dp20 = (int) (20 * context.getResources().getDisplayMetrics().density);
        int dp4 = (int) (4 * context.getResources().getDisplayMetrics().density);

        int cols = 6;
        int rows = (ThemeHelper.ACCENT_COLORS.length + cols - 1) / cols;

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            row.setGravity(Gravity.CENTER);
            row.setPadding(0, dp4, 0, dp4);

            for (int c = 0; c < cols; c++) {
                int index = r * cols + c;
                if (index >= ThemeHelper.ACCENT_COLORS.length) break;

                final int i = index;
                final ThemeHelper.AccentColorEntry entry = ThemeHelper.ACCENT_COLORS[index];

                FrameLayout frame = new FrameLayout(context);
                LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, dp40, 1.0f);
                flp.gravity = Gravity.CENTER;
                frame.setLayoutParams(flp);

                View circleView = new View(context);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp36, dp36, Gravity.CENTER);
                circleView.setLayoutParams(clp);
                GradientDrawable circle = new GradientDrawable();
                circle.setShape(GradientDrawable.OVAL);
                circle.setColor(entry.color);
                circleView.setBackground(circle);
                frame.addView(circleView);

                ImageView check = new ImageView(context);
                check.setImageResource(R.drawable.ic_check_white);
                FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(dp20, dp20, Gravity.CENTER);
                check.setLayoutParams(chlp);

                boolean isCurrent = TextUtils.isEmpty(tempCustomHex[0]) && entry.key.equalsIgnoreCase(tempKey[0]);
                check.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
                checkViews[i] = check;
                frame.addView(check);

                frame.setOnClickListener(v -> {
                    tempKey[0] = entry.key;
                    tempCustomHex[0] = null;
                    for (int j = 0; j < checkViews.length; j++) {
                        if (checkViews[j] != null) {
                            checkViews[j].setVisibility(j == i ? View.VISIBLE : View.GONE);
                        }
                    }
                    isInternalTextChange[0] = true;
                    hexEdit.clearFocus();
                    hexEdit.setText(String.format(Locale.ROOT, "#%06X", (0xFFFFFF & entry.color)));
                    isInternalTextChange[0] = false;
                    updateCirclePreview(customPreview, entry.color);
                });

                row.addView(frame);
            }
            paletteRows.addView(row);
        }

        int initialColor = ThemeHelper.getPrimaryColor(context);
        updateCirclePreview(customPreview, initialColor);

        isInternalTextChange[0] = true;
        if (!TextUtils.isEmpty(selectedCustomHex)) {
            hexEdit.setText(selectedCustomHex);
        } else {
            ThemeHelper.AccentColorEntry activeEntry = ThemeHelper.getAccentEntry(selectedKey);
            hexEdit.setText(String.format(Locale.ROOT, "#%06X", (0xFFFFFF & activeEntry.color)));
        }
        isInternalTextChange[0] = false;

        hexEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isInternalTextChange[0]) return;
                String str = s != null ? s.toString().trim() : "";
                if (!str.startsWith("#") && !str.isEmpty()) {
                    str = "#" + str;
                }
                if (str.matches("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")) {
                    try {
                        int parsed = Color.parseColor(str);
                        updateCirclePreview(customPreview, parsed);
                        tempCustomHex[0] = str;
                        tempKey[0] = ThemeHelper.getClosestPresetKey(parsed);
                        for (ImageView iv : checkViews) {
                            if (iv != null) iv.setVisibility(View.GONE);
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_title_accent_color)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String finalHex = hexEdit.getText().toString().trim();
                    if (!finalHex.startsWith("#") && !finalHex.isEmpty()) {
                        finalHex = "#" + finalHex;
                    }
                    if (!TextUtils.isEmpty(finalHex) && finalHex.matches("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")) {
                        try {
                            int parsed = Color.parseColor(finalHex);
                            tempCustomHex[0] = finalHex;
                            tempKey[0] = ThemeHelper.getClosestPresetKey(parsed);
                        } catch (Exception e) {
                            Toast.makeText(context, R.string.accent_color_invalid_hex, Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else if (!TextUtils.isEmpty(finalHex)) {
                        Toast.makeText(context, R.string.accent_color_invalid_hex, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    prefs.edit()
                            .putString(ThemeHelper.ACCENT_COLOR_PREF_KEY, tempKey[0])
                            .putString(ThemeHelper.ACCENT_COLOR_CUSTOM_HEX_KEY, tempCustomHex[0])
                            .apply();

                    updateSummaryAndState();

                    Activity activity = (context instanceof Activity) ? (Activity) context : null;
                    if (activity != null) {
                        ThemeHelper.applyTheme(activity);
                        activity.recreate();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateCirclePreview(View view, int color) {
        if (view != null) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(color);
            view.setBackground(circle);
        }
    }
}
