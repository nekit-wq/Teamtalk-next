package org.nekit.ttproplus.gui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.nekit.ttproplus.R;
import org.nekit.ttproplus.data.SoundTheme;
import org.nekit.ttproplus.utils.SoundThemeManager;

import java.io.File;
import java.util.List;

public class SoundThemeEditActivity extends AppCompatActivity {

    public static final String EXTRA_THEME_ID = "extra_sound_theme_id";
    private static final int REQUEST_PICK_SYSTEM = 201;
    private static final int REQUEST_PICK_BUILTIN = 202;

    private String themeId;
    private SoundTheme soundTheme;
    private RecyclerView recyclerView;
    private SoundEventsAdapter adapter;
    private String pendingEventKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound_theme_edit);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        themeId = getIntent().getStringExtra(EXTRA_THEME_ID);
        if (themeId == null) {
            themeId = "default";
        }
        soundTheme = SoundThemeManager.getThemeById(this, themeId);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setTitle(getString(R.string.sound_theme_edit_title, soundTheme.getName()));
            toolbar.setSubtitle(R.string.sound_theme_edit_subtitle);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        recyclerView = findViewById(R.id.recycler_sound_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SoundEventsAdapter(this, SoundThemeManager.ALL_EVENTS);
        recyclerView.setAdapter(adapter);
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

    private void pickSoundForEvent(String eventKey) {
        this.pendingEventKey = eventKey;
        Utils.showFilePicker(this, REQUEST_PICK_SYSTEM, REQUEST_PICK_BUILTIN, false, "audio/*");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || pendingEventKey == null) {
            return;
        }

        Uri uri = null;
        if (requestCode == REQUEST_PICK_SYSTEM && data != null) {
            uri = data.getData();
        } else if (requestCode == REQUEST_PICK_BUILTIN && data != null) {
            String path = data.getStringExtra(CustomFilePickerActivity.EXTRA_FILE_PATH);
            if (path != null) {
                uri = Uri.fromFile(new File(path));
            }
        }

        if (uri != null) {
            boolean success = SoundThemeManager.setEventSound(this, themeId, pendingEventKey, uri);
            if (success) {
                Toast.makeText(this, getString(R.string.sound_theme_sound_updated, getEventTitle(pendingEventKey)), Toast.LENGTH_SHORT).show();
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            } else {
                Toast.makeText(this, "Failed to import audio file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getEventTitle(String key) {
        for (SoundThemeManager.EventInfo info : SoundThemeManager.ALL_EVENTS) {
            if (info.key.equals(key)) {
                return getString(info.titleResId);
            }
        }
        return key;
    }

    private class SoundEventsAdapter extends RecyclerView.Adapter<SoundEventsAdapter.ViewHolder> {
        private final Context context;
        private final List<SoundThemeManager.EventInfo> events;

        SoundEventsAdapter(Context context, List<SoundThemeManager.EventInfo> events) {
            this.context = context;
            this.events = events;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sound_event, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            SoundThemeManager.EventInfo event = events.get(position);

            holder.textCategory.setText(event.categoryResId);
            holder.textTitle.setText(event.titleResId);

            String statusText;
            File customFile = SoundThemeManager.getSoundFileForEvent(context, themeId, event.key);
            if (customFile != null && customFile.exists()) {
                statusText = context.getString(R.string.sound_theme_sound_custom, customFile.getName());
                holder.textStatus.setText(statusText);
                holder.btnReset.setVisibility(View.VISIBLE);
                holder.btnReset.setContentDescription(context.getString(R.string.sound_theme_reset_event, context.getString(event.titleResId)));
                holder.btnReset.setOnClickListener(v -> {
                    SoundThemeManager.resetEventSound(context, themeId, event.key);
                    notifyItemChanged(position);
                    Toast.makeText(context, context.getString(R.string.sound_theme_sound_reset, context.getString(event.titleResId)), Toast.LENGTH_SHORT).show();
                });
            } else {
                String base = SoundThemeManager.getBasePack(context, themeId);
                statusText = context.getString(R.string.sound_theme_sound_default) + " (" + base + ")";
                holder.textStatus.setText(statusText);
                holder.btnReset.setVisibility(View.GONE);
            }

            holder.btnPlay.setContentDescription(context.getString(R.string.sound_theme_play_event, context.getString(event.titleResId)));
            holder.btnPlay.setOnClickListener(v -> {
                SoundThemeManager.playEventSoundPreview(context, themeId, event);
            });

            View.OnClickListener pickListener = v -> {
                pickSoundForEvent(event.key);
            };

            String category = context.getString(event.categoryResId);
            String eventName = context.getString(event.titleResId);
            String cardDesc = category + ", " + eventName + ", " + statusText + ". " + context.getString(R.string.sound_theme_card_hint);
            holder.itemView.setContentDescription(cardDesc);
            holder.itemView.setOnClickListener(pickListener);
        }

        @Override
        public int getItemCount() {
            return events.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialButton btnPlay;
            TextView textCategory;
            TextView textTitle;
            TextView textStatus;
            ImageButton btnReset;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                btnPlay = itemView.findViewById(R.id.btn_play_event);
                textCategory = itemView.findViewById(R.id.text_event_category);
                textTitle = itemView.findViewById(R.id.text_event_title);
                textStatus = itemView.findViewById(R.id.text_event_status);
                btnReset = itemView.findViewById(R.id.btn_reset_event_sound);
            }
        }
    }
}
