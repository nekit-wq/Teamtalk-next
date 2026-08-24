package org.nekit.ttproplus.gui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.nekit.ttproplus.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecordingsActivity extends AppCompatActivity {

    public static class RecordingItem {
        public File file;
        public String name;
        public String folderPath;
        public long size;
        public long lastModified;
        public String sizeFormatted;
        public String dateFormatted;

        public RecordingItem(File file, File baseDir) {
            this.file = file;
            this.name = file.getName();
            this.size = file.length();
            this.lastModified = file.lastModified();

            if (this.size < 1024 * 1024) {
                this.sizeFormatted = String.format(Locale.US, "%.1f KB", this.size / 1024.0);
            } else {
                this.sizeFormatted = String.format(Locale.US, "%.1f MB", this.size / (1024.0 * 1024.0));
            }

            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            this.dateFormatted = sdf.format(new Date(this.lastModified));

            try {
                String parent = file.getParent();
                String base = baseDir.getAbsolutePath();
                if (parent != null && parent.startsWith(base) && parent.length() > base.length()) {
                    String rel = parent.substring(base.length());
                    if (rel.startsWith(File.separator)) {
                        rel = rel.substring(1);
                    }
                    this.folderPath = rel.replace(File.separatorChar, '·').trim();
                } else {
                    this.folderPath = "";
                }
            } catch (Exception e) {
                this.folderPath = "";
            }
        }
    }

    private EditText searchEdit;
    private ImageButton btnClearSearch;
    private TextView statsText;
    private Spinner filterSpinner;
    private ListView listView;
    private TextView emptyText;

    // Player views
    private LinearLayout playerPanel;
    private TextView playerTitle;
    private TextView playerCurTime;
    private TextView playerTotalTime;
    private SeekBar playerSeekBar;
    private ImageButton btnPlayPause;
    private ImageButton btnClosePlayer;
    private Button btnRewind;
    private Button btnForward;

    private final List<RecordingItem> allItems = new ArrayList<>();
    private final List<RecordingItem> displayedItems = new ArrayList<>();
    private RecordingsAdapter adapter;

    private MediaPlayer mediaPlayer = null;
    private RecordingItem currentPlayingItem = null;
    private final Handler playerHandler = new Handler(Looper.getMainLooper());
    private boolean isUserSeeking = false;

    private final Runnable playerProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && mediaPlayer.isPlaying() && !isUserSeeking) {
                int current = mediaPlayer.getCurrentPosition();
                playerSeekBar.setProgress(current);
                playerCurTime.setText(formatDuration(current));
            }
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                playerHandler.postDelayed(this, 250);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.title_activity_recordings);
        }

        initViews();
        setupListeners();
        loadRecordings();
    }

    private void initViews() {
        this.searchEdit = findViewById(R.id.rec_search_edit);
        this.btnClearSearch = findViewById(R.id.rec_btn_clear_search);
        this.statsText = findViewById(R.id.rec_stats_text);
        this.filterSpinner = findViewById(R.id.rec_filter_spinner);
        this.listView = findViewById(R.id.rec_list_view);
        this.emptyText = findViewById(R.id.rec_empty_text);

        this.playerPanel = findViewById(R.id.rec_player_panel);
        this.playerTitle = findViewById(R.id.rec_player_title);
        this.playerCurTime = findViewById(R.id.rec_player_cur_time);
        this.playerTotalTime = findViewById(R.id.rec_player_total_time);
        this.playerSeekBar = findViewById(R.id.rec_player_seekbar);
        this.btnPlayPause = findViewById(R.id.rec_player_btn_play_pause);
        this.btnClosePlayer = findViewById(R.id.rec_player_btn_close);
        this.btnRewind = findViewById(R.id.rec_player_btn_rewind);
        this.btnForward = findViewById(R.id.rec_player_btn_forward);

        this.adapter = new RecordingsAdapter();
        this.listView.setAdapter(this.adapter);
    }

    private void setupListeners() {
        this.searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        this.btnClearSearch.setOnClickListener(v -> searchEdit.setText(""));

        this.filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        this.btnPlayPause.setOnClickListener(v -> togglePlayPause());

        this.btnClosePlayer.setOnClickListener(v -> stopPlayback());

        this.btnRewind.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int pos = Math.max(0, mediaPlayer.getCurrentPosition() - 5000);
                mediaPlayer.seekTo(pos);
                playerSeekBar.setProgress(pos);
                playerCurTime.setText(formatDuration(pos));
            }
        });

        this.btnForward.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                int pos = Math.min(mediaPlayer.getDuration(), mediaPlayer.getCurrentPosition() + 5000);
                mediaPlayer.seekTo(pos);
                playerSeekBar.setProgress(pos);
                playerCurTime.setText(formatDuration(pos));
            }
        });

        this.playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    playerCurTime.setText(formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });

        this.listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < displayedItems.size()) {
                playItem(displayedItems.get(position));
            }
        });
    }

    private void loadRecordings() {
        this.allItems.clear();
        File baseDir = Utils.getRecordingsBaseDirectory(this);
        scanDirectory(baseDir, baseDir);

        Collections.sort(this.allItems, (o1, o2) -> Long.compare(o2.lastModified, o1.lastModified));

        setupFilterSpinner();
        applyFilter();
    }

    private void scanDirectory(File dir, File baseDir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectory(f, baseDir);
            } else if (f.isFile() && isAudioFile(f.getName())) {
                this.allItems.add(new RecordingItem(f, baseDir));
            }
        }
    }

    private boolean isAudioFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ogg") || lower.endsWith(".mp3") || lower.endsWith(".wav");
    }

    private void setupFilterSpinner() {
        List<String> folders = new ArrayList<>();
        folders.add(getString(R.string.recordings_filter_all));

        for (RecordingItem item : this.allItems) {
            if (!TextUtils.isEmpty(item.folderPath) && !folders.contains(item.folderPath)) {
                folders.add(item.folderPath);
            }
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, folders);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.filterSpinner.setAdapter(spinnerAdapter);
    }

    private void applyFilter() {
        String query = this.searchEdit.getText().toString().trim().toLowerCase(Locale.getDefault());
        String selectedFilter = (String) this.filterSpinner.getSelectedItem();
        boolean filterAll = selectedFilter == null || selectedFilter.equals(getString(R.string.recordings_filter_all));

        this.displayedItems.clear();
        long totalSize = 0;

        for (RecordingItem item : this.allItems) {
            boolean matchesSearch = TextUtils.isEmpty(query) ||
                    item.name.toLowerCase(Locale.getDefault()).contains(query) ||
                    item.folderPath.toLowerCase(Locale.getDefault()).contains(query);

            boolean matchesFolder = filterAll || item.folderPath.equals(selectedFilter);

            if (matchesSearch && matchesFolder) {
                this.displayedItems.add(item);
                totalSize += item.size;
            }
        }

        this.adapter.notifyDataSetChanged();
        this.emptyText.setVisibility(this.displayedItems.isEmpty() ? View.VISIBLE : View.GONE);

        String totalSizeStr;
        if (totalSize < 1024 * 1024) {
            totalSizeStr = String.format(Locale.US, "%.1f KB", totalSize / 1024.0);
        } else {
            totalSizeStr = String.format(Locale.US, "%.1f MB", totalSize / (1024.0 * 1024.0));
        }

        this.statsText.setText(getString(R.string.recordings_count_format, this.displayedItems.size(), totalSizeStr));
    }

    private void playItem(RecordingItem item) {
        if (item == null || item.file == null || !item.file.exists()) {
            Toast.makeText(this, R.string.recordings_play_error, Toast.LENGTH_SHORT).show();
            return;
        }

        if (this.currentPlayingItem != null && this.currentPlayingItem.file.equals(item.file) && this.mediaPlayer != null) {
            togglePlayPause();
            return;
        }

        stopPlayback();

        try {
            this.mediaPlayer = new MediaPlayer();
            this.mediaPlayer.setDataSource(item.file.getAbsolutePath());
            this.mediaPlayer.prepare();
            this.mediaPlayer.start();

            this.currentPlayingItem = item;
            this.playerPanel.setVisibility(View.VISIBLE);
            this.playerTitle.setText(item.name);
            this.btnPlayPause.setImageResource(R.drawable.ic_pause);

            int duration = this.mediaPlayer.getDuration();
            this.playerSeekBar.setMax(duration);
            this.playerSeekBar.setProgress(0);
            this.playerCurTime.setText("00:00");
            this.playerTotalTime.setText(formatDuration(duration));

            this.mediaPlayer.setOnCompletionListener(mp -> {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                playerSeekBar.setProgress(playerSeekBar.getMax());
                playerCurTime.setText(playerTotalTime.getText());
                adapter.notifyDataSetChanged();
            });

            this.playerHandler.post(this.playerProgressRunnable);
            this.adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e("bearware", "Error playing recording", e);
            Toast.makeText(this, R.string.recordings_play_error, Toast.LENGTH_SHORT).show();
            stopPlayback();
        }
    }

    private void togglePlayPause() {
        if (this.mediaPlayer == null) return;
        if (this.mediaPlayer.isPlaying()) {
            this.mediaPlayer.pause();
            this.btnPlayPause.setImageResource(R.drawable.ic_play);
        } else {
            this.mediaPlayer.start();
            this.btnPlayPause.setImageResource(R.drawable.ic_pause);
            this.playerHandler.post(this.playerProgressRunnable);
        }
        this.adapter.notifyDataSetChanged();
    }

    private void stopPlayback() {
        if (this.mediaPlayer != null) {
            try {
                if (this.mediaPlayer.isPlaying()) {
                    this.mediaPlayer.stop();
                }
                this.mediaPlayer.release();
            } catch (Exception ignored) {}
            this.mediaPlayer = null;
        }
        this.currentPlayingItem = null;
        this.playerPanel.setVisibility(View.GONE);
        this.playerHandler.removeCallbacks(this.playerProgressRunnable);
        this.adapter.notifyDataSetChanged();
    }

    private void shareFile(RecordingItem item) {
        if (item == null || item.file == null || !item.file.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", item.file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("audio/*");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.name);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.recordings_action_share)));
        } catch (Exception e) {
            Log.e("bearware", "Failed to share recording file", e);
            Toast.makeText(this, "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showMoreMenu(View anchor, final RecordingItem item) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.recordings_action_share);
        popup.getMenu().add(0, 2, 1, R.string.recordings_action_rename);
        popup.getMenu().add(0, 3, 2, R.string.recordings_action_delete);

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == 1) {
                shareFile(item);
                return true;
            } else if (id == 2) {
                showRenameDialog(item);
                return true;
            } else if (id == 3) {
                showDeleteDialog(item);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRenameDialog(final RecordingItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.recordings_rename_title);

        String currentName = item.file.getName();
        String ext = "";
        int dot = currentName.lastIndexOf('.');
        if (dot != -1) {
            ext = currentName.substring(dot);
            currentName = currentName.substring(0, dot);
        }
        final String fileExt = ext;

        final EditText input = new EditText(this);
        input.setText(currentName);
        input.setSelection(currentName.length());
        input.setHint(R.string.recordings_rename_hint);
        builder.setView(input);

        builder.setPositiveButton(R.string.recording_rename_save, (dialog, which) -> {
            String newBase = input.getText().toString().trim();
            if (TextUtils.isEmpty(newBase)) return;

            String newName = newBase + fileExt;
            File newFile = new File(item.file.getParentFile(), newName);
            if (item.file.renameTo(newFile)) {
                MediaScannerConnection.scanFile(this, new String[]{newFile.getAbsolutePath(), item.file.getAbsolutePath()}, null, null);
                if (currentPlayingItem != null && currentPlayingItem.file.equals(item.file)) {
                    stopPlayback();
                }
                loadRecordings();
                Toast.makeText(RecordingsActivity.this, getString(R.string.recording_renamed_success, newName), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(RecordingsActivity.this, R.string.recording_rename_failed, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showDeleteDialog(final RecordingItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.recordings_delete_title);
        builder.setMessage(getString(R.string.recordings_delete_message, item.name));

        builder.setPositiveButton(R.string.recordings_action_delete, (dialog, which) -> {
            if (currentPlayingItem != null && currentPlayingItem.file.equals(item.file)) {
                stopPlayback();
            }
            if (item.file.delete()) {
                MediaScannerConnection.scanFile(this, new String[]{item.file.getAbsolutePath()}, null, null);
                loadRecordings();
                Toast.makeText(RecordingsActivity.this, R.string.recordings_deleted_toast, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private String formatDuration(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        int hours = (millis / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home || item.getItemId() == 16908332) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPlayback();
    }

    private class RecordingsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return displayedItems.size();
        }

        @Override
        public Object getItem(int position) {
            return displayedItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(RecordingsActivity.this).inflate(R.layout.item_recording, parent, false);
                holder = new ViewHolder();
                holder.icon = convertView.findViewById(R.id.rec_item_icon);
                holder.name = convertView.findViewById(R.id.rec_item_name);
                holder.folder = convertView.findViewById(R.id.rec_item_folder);
                holder.details = convertView.findViewById(R.id.rec_item_details);
                holder.btnPlay = convertView.findViewById(R.id.rec_btn_play_item);
                holder.btnShare = convertView.findViewById(R.id.rec_btn_share_item);
                holder.btnMore = convertView.findViewById(R.id.rec_btn_more_item);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final RecordingItem item = displayedItems.get(position);
            holder.name.setText(item.name);

            if (!TextUtils.isEmpty(item.folderPath)) {
                holder.folder.setVisibility(View.VISIBLE);
                holder.folder.setText(item.folderPath);
            } else {
                holder.folder.setVisibility(View.GONE);
            }

            holder.details.setText(item.dateFormatted + " · " + item.sizeFormatted);

            boolean isThisPlaying = (mediaPlayer != null && mediaPlayer.isPlaying() && currentPlayingItem != null && currentPlayingItem.file.equals(item.file));
            holder.btnPlay.setImageResource(isThisPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

            holder.btnPlay.setOnClickListener(v -> playItem(item));
            holder.btnShare.setOnClickListener(v -> shareFile(item));
            holder.btnMore.setOnClickListener(v -> showMoreMenu(holder.btnMore, item));

            return convertView;
        }
    }

    private static class ViewHolder {
        ImageView icon;
        TextView name;
        TextView folder;
        TextView details;
        ImageButton btnPlay;
        ImageButton btnShare;
        ImageButton btnMore;
    }
}
