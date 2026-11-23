package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.data.SoundTheme;
import org.nekit.ttproplus.utils.SoundThemeManager;

import java.util.ArrayList;
import java.util.List;

public class SoundThemesActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private SoundThemesAdapter adapter;
    private ExtendedFloatingActionButton fabCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_themes);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        recyclerView = findViewById(R.id.recycler_sound_themes);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SoundThemesAdapter(this);
        recyclerView.setAdapter(adapter);

        fabCreate = findViewById(R.id.fab_create_theme);
        if (fabCreate != null) {
            fabCreate.setOnClickListener(v -> showCreateThemeDialog(null));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.reloadThemes();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        SoundThemeManager.stopPreview();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showCreateThemeDialog(String initialBaseTheme) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_echo_reverb, null); // repurpose or build view
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);

        final EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.sound_theme_name_hint);
        nameInput.setSingleLine(true);
        container.addView(nameInput);

        TextView basePrompt = new TextView(this);
        basePrompt.setText(R.string.sound_theme_base_prompt);
        basePrompt.setPadding(0, pad / 2, 0, pad / 4);
        container.addView(basePrompt);

        final Spinner baseSpinner = new Spinner(this);
        final List<SoundTheme> builtIns = SoundThemeManager.getBuiltInThemes(this);
        List<String> baseNames = new ArrayList<>();
        int selectedIndex = 0;
        for (int i = 0; i < builtIns.size(); i++) {
            baseNames.add(builtIns.get(i).getName());
            if (initialBaseTheme != null && initialBaseTheme.equals(builtIns.get(i).getId())) {
                selectedIndex = i;
            }
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, baseNames);
        baseSpinner.setAdapter(spinnerAdapter);
        baseSpinner.setSelection(selectedIndex);
        container.addView(baseSpinner);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sound_theme_create_title)
                .setView(container)
                .setPositiveButton(R.string.action_create, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = getString(R.string.sound_theme_custom);
                    }
                    int pos = baseSpinner.getSelectedItemPosition();
                    String baseThemeId = (pos >= 0 && pos < builtIns.size()) ? builtIns.get(pos).getId() : "default";

                    SoundTheme created = SoundThemeManager.createTheme(SoundThemesActivity.this, name, baseThemeId);
                    SoundThemeManager.setActiveThemeId(SoundThemesActivity.this, created.getId());
                    adapter.reloadThemes();
                    Toast.makeText(SoundThemesActivity.this, getString(R.string.sound_theme_created, name), Toast.LENGTH_SHORT).show();

                    Intent editIntent = new Intent(SoundThemesActivity.this, SoundThemeEditActivity.class);
                    editIntent.putExtra(SoundThemeEditActivity.EXTRA_THEME_ID, created.getId());
                    startActivity(editIntent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRenameThemeDialog(SoundTheme theme) {
        final EditText nameInput = new EditText(this);
        nameInput.setText(theme.getName());
        nameInput.setSelection(theme.getName().length());
        nameInput.setSingleLine(true);

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout layout = new FrameLayout(this);
        layout.setPadding(pad, pad / 2, pad, pad / 2);
        layout.addView(nameInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sound_theme_rename)
                .setView(layout)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String newName = nameInput.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        SoundThemeManager.renameTheme(SoundThemesActivity.this, theme.getId(), newName);
                        adapter.reloadThemes();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteTheme(SoundTheme theme) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sound_theme_delete)
                .setMessage(getString(R.string.sound_theme_delete_confirm, theme.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    SoundThemeManager.deleteTheme(SoundThemesActivity.this, theme.getId());
                    adapter.reloadThemes();
                    Toast.makeText(SoundThemesActivity.this, getString(R.string.sound_theme_deleted, theme.getName()), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static class FrameLayout extends android.widget.FrameLayout {
        public FrameLayout(Context context) {
            super(context);
        }
    }

    private class SoundThemesAdapter extends RecyclerView.Adapter<SoundThemesAdapter.ViewHolder> {
        private final Context context;
        private List<SoundTheme> themes = new ArrayList<>();
        private String activeThemeId = "default";

        SoundThemesAdapter(Context context) {
            this.context = context;
            reloadThemes();
        }

        void reloadThemes() {
            this.themes = SoundThemeManager.getAllThemes(context);
            this.activeThemeId = SoundThemeManager.getActiveThemeId(context);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sound_theme, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SoundTheme theme = themes.get(position);
            boolean isActive = theme.getId().equals(activeThemeId);

            holder.textName.setText(theme.getName());
            holder.radioActive.setChecked(isActive);

            if (theme.isCustom()) {
                int count = SoundThemeManager.getCustomizedEventsCount(context, theme.getId());
                if (count > 0) {
                    holder.textDesc.setText(context.getString(R.string.sound_theme_custom) + " • " + context.getString(R.string.sound_theme_custom_count, count));
                } else {
                    holder.textDesc.setText(context.getString(R.string.sound_theme_custom));
                }
                holder.actionsLayout.setVisibility(View.VISIBLE);
                holder.btnEditSounds.setVisibility(View.VISIBLE);
                holder.btnMenu.setVisibility(View.VISIBLE);

                holder.btnEditSounds.setOnClickListener(v -> {
                    Intent intent = new Intent(context, SoundThemeEditActivity.class);
                    intent.putExtra(SoundThemeEditActivity.EXTRA_THEME_ID, theme.getId());
                    context.startActivity(intent);
                });

                holder.btnMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(context, holder.btnMenu);
                    popup.getMenu().add(0, 1, 0, R.string.sound_theme_rename);
                    popup.getMenu().add(0, 2, 1, R.string.sound_theme_delete);
                    popup.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == 1) {
                            showRenameThemeDialog(theme);
                            return true;
                        } else if (item.getItemId() == 2) {
                            confirmDeleteTheme(theme);
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            } else {
                holder.textDesc.setText(context.getString(R.string.sound_theme_builtin));
                holder.actionsLayout.setVisibility(View.GONE);
                holder.btnMenu.setVisibility(View.VISIBLE);

                holder.btnMenu.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(context, holder.btnMenu);
                    popup.getMenu().add(0, 1, 0, R.string.sound_theme_create_from_base);
                    popup.setOnMenuItemClickListener(item -> {
                        if (item.getItemId() == 1) {
                            showCreateThemeDialog(theme.getId());
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            }

            View.OnClickListener selectListener = v -> {
                if (!theme.getId().equals(activeThemeId)) {
                    SoundThemeManager.setActiveThemeId(context, theme.getId());
                    activeThemeId = theme.getId();
                    notifyDataSetChanged();
                    Toast.makeText(context, context.getString(R.string.sound_theme_applied, theme.getName()), Toast.LENGTH_SHORT).show();
                    if (!theme.getId().equals("silent")) {
                        SoundThemeManager.EventInfo onEvent = SoundThemeManager.ALL_EVENTS.get(0);
                        SoundThemeManager.playEventSoundPreview(context, theme.getId(), onEvent);
                    }
                }
            };

            holder.itemView.setOnClickListener(selectListener);
            holder.radioActive.setOnClickListener(selectListener);
        }

        @Override
        public int getItemCount() {
            return themes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textName;
            TextView textDesc;
            RadioButton radioActive;
            ImageButton btnMenu;
            LinearLayout actionsLayout;
            MaterialButton btnEditSounds;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                textName = itemView.findViewById(R.id.text_theme_name);
                textDesc = itemView.findViewById(R.id.text_theme_description);
                radioActive = itemView.findViewById(R.id.radio_active);
                btnMenu = itemView.findViewById(R.id.btn_theme_menu);
                actionsLayout = itemView.findViewById(R.id.actions_layout);
                btnEditSounds = itemView.findViewById(R.id.btn_edit_sounds);
            }
        }
    }
}
