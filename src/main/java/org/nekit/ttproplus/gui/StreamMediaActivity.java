package org.nekit.ttproplus.gui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import dk.bearware.ClientFlag;
import dk.bearware.MediaFileInfo;
import dk.bearware.MediaFilePlayback;
import dk.bearware.MediaFilePlaybackConstants;
import dk.bearware.MediaFileStatus;
import dk.bearware.TeamTalkBase;
import dk.bearware.VideoCodec;
import dk.bearware.events.ClientEventListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.Permissions;

public class StreamMediaActivity extends AppCompatActivity implements TeamTalkConnectionListener {
    public static final int REQUEST_STREAM_MEDIA = 1;
    public static final int REQUEST_CUSTOM_FILE_PICKER = 2;
    public static final String TAG = "bearware";
    private static final String PREF_LAST_MEDIA_FILE = "last_media_file";
    private static final String PREF_LAST_STREAM_URL = "last_stream_url";
    private static final String PREF_LAST_STREAM_MODE = "last_stream_mode";
    private static final String PREF_CUSTOM_STATIONS = "pref_custom_radio_stations_json";

    public static class RadioStation {
        public String name;
        public String url;
        public String genre;
        public boolean isCustom;

        public RadioStation(String name, String url, String genre) {
            this(name, url, genre, false);
        }

        public RadioStation(String name, String url, String genre, boolean isCustom) {
            this.name = name;
            this.url = url;
            this.genre = genre;
            this.isCustom = isCustom;
        }

        @Override
        public String toString() {
            if (genre != null && !genre.isEmpty()) {
                return name + " (" + genre + ")";
            }
            return name;
        }
    }

    private static final RadioStation[] DEFAULT_PRESETS = new RadioStation[]{
            new RadioStation("Radio Record (Главный)", "http://radiorecord.hostingradio.ru/rr_main96.aacp", "Dance / EDM"),
            new RadioStation("Record Russian Mix", "http://radiorecord.hostingradio.ru/rus96.aacp", "Русский танцевальный"),
            new RadioStation("Европа Плюс (Europa Plus)", "http://ep256.hostingradio.ru:8052/europaplus256.mp3", "Хиты / Pop")
    };

    private RadioGroup modeRadioGroup;
    private RadioButton rbModeFile;
    private RadioButton rbModeRadio;
    private LinearLayout containerFileMode;
    private LinearLayout containerRadioMode;
    private EditText file_path;
    private EditText webStreamUrlTxt;
    private Spinner spinnerRadioStations;
    private Button btnPasteUrl;
    private Button btnAddStation;
    private Button btnSelectFile;
    private Button btnStream;
    private Button btnPlayPause;
    private Button btnStop;
    private TextView txtMediaInfo;
    private TextView txtPosition;
    private TextView txtDuration;
    private SeekBar seekBar;
    private LinearLayout containerSeekBar;

    private boolean isStreaming;
    private volatile boolean stoppingStream = false;
    private int localPlaybackId;
    private MediaPlayer localMediaPlayer;
    private boolean isLocalMediaPlaying = false;
    private TeamTalkConnection mConnection;
    private MediaFileInfo mMediaFileInfo;
    private MediaFilePlayback mPlayback;
    private Runnable progressUpdater;
    private boolean seekBarTouching;
    private ClientEventListener.OnStreamMediaFileListener streamMediaFileListener;
    private ClientEventListener.OnLocalMediaFileListener localMediaFileListener;
    private Handler handler = new Handler();
    private List<Uri> playlistUris = new ArrayList<>();
    private int playlistIndex = 0;
    private List<RadioStation> allStations = new ArrayList<>();
    private ArrayAdapter<RadioStation> stationsAdapter;
    private long liveStreamStartTime = 0;

    TeamTalkService getService() {
        return this.mConnection != null ? this.mConnection.getService() : null;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        this.mConnection = new TeamTalkConnection(this);
        setContentView(R.layout.activity_stream_media);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.stream_mode_radio);
        }

        this.modeRadioGroup = findViewById(R.id.mode_radio_group);
        this.rbModeFile = findViewById(R.id.rb_mode_file);
        this.rbModeRadio = findViewById(R.id.rb_mode_radio);
        this.containerFileMode = findViewById(R.id.container_file_mode);
        this.containerRadioMode = findViewById(R.id.container_radio_mode);
        this.file_path = findViewById(R.id.file_path_txt);
        this.webStreamUrlTxt = findViewById(R.id.web_stream_url_txt);
        this.spinnerRadioStations = findViewById(R.id.spinner_radio_stations);
        this.btnPasteUrl = findViewById(R.id.btn_paste_url);
        this.btnAddStation = findViewById(R.id.btn_add_station);
        this.btnSelectFile = findViewById(R.id.media_file_select_btn);
        this.btnStream = findViewById(R.id.btn_stream);
        this.btnPlayPause = findViewById(R.id.btn_play_pause);
        this.btnStop = findViewById(R.id.btn_stop);
        this.txtMediaInfo = findViewById(R.id.media_info);
        this.txtPosition = findViewById(R.id.txt_position);
        this.txtDuration = findViewById(R.id.txt_duration);
        this.seekBar = findViewById(R.id.seek_bar);
        this.containerSeekBar = findViewById(R.id.container_seek_bar);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        this.file_path.setText(prefs.getString(PREF_LAST_MEDIA_FILE, ""));
        this.webStreamUrlTxt.setText(prefs.getString(PREF_LAST_STREAM_URL, DEFAULT_PRESETS[0].url));

        String savedMode = prefs.getString(PREF_LAST_STREAM_MODE, "radio");
        if ("file".equals(savedMode)) {
            this.rbModeFile.setChecked(true);
            switchMode(false);
        } else {
            this.rbModeRadio.setChecked(true);
            switchMode(true);
        }

        this.modeRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean isRadio = (checkedId == R.id.rb_mode_radio);
                switchMode(isRadio);
                PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                        .edit().putString(PREF_LAST_STREAM_MODE, isRadio ? "radio" : "file").apply();
            }
        });

        initRadioStations();

        this.btnPasteUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteUrlFromClipboard();
            }
        });

        this.btnAddStation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddStationDialog();
            }
        });

        this.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && StreamMediaActivity.this.mMediaFileInfo != null && StreamMediaActivity.this.mMediaFileInfo.uDurationMSec > 0) {
                    int pos = (int) ((StreamMediaActivity.this.mMediaFileInfo.uDurationMSec * (long) progress) / seekBar.getMax());
                    StreamMediaActivity.this.txtPosition.setText(StreamMediaActivity.this.formatDuration(pos));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                StreamMediaActivity.this.seekBarTouching = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                StreamMediaActivity.this.seekBarTouching = false;
                if (StreamMediaActivity.this.mMediaFileInfo == null || StreamMediaActivity.this.mMediaFileInfo.uDurationMSec <= 0) {
                    return;
                }
                int offset = (int) ((StreamMediaActivity.this.mMediaFileInfo.uDurationMSec * (long) seekBar.getProgress()) / seekBar.getMax());
                if (StreamMediaActivity.this.mPlayback == null) {
                    StreamMediaActivity.this.mPlayback = new MediaFilePlayback();
                }
                StreamMediaActivity.this.mPlayback.uOffsetMSec = offset;
                StreamMediaActivity.this.mPlayback.bPaused = false;
                if (StreamMediaActivity.this.localPlaybackId > 0 && getClient() != null) {
                    getClient().updateLocalPlayback(StreamMediaActivity.this.localPlaybackId, StreamMediaActivity.this.mPlayback);
                } else if (StreamMediaActivity.this.isStreaming && getClient() != null) {
                    VideoCodec vc = new VideoCodec();
                    vc.nCodec = (StreamMediaActivity.this.mMediaFileInfo.videoFmt != null && StreamMediaActivity.this.mMediaFileInfo.videoFmt.picFourCC != 0) ? 128 : 0;
                    getClient().updateStreamingMediaFileToChannel(StreamMediaActivity.this.mPlayback, vc);
                }
            }
        });
    }

    private void switchMode(boolean isRadio) {
        if (isRadio) {
            this.containerFileMode.setVisibility(View.GONE);
            this.containerRadioMode.setVisibility(View.VISIBLE);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.stream_mode_radio);
            }
            if (!this.isStreaming && this.localPlaybackId <= 0 && !this.isLocalMediaPlaying) {
                this.containerSeekBar.setVisibility(View.GONE);
            }
        } else {
            this.containerFileMode.setVisibility(View.VISIBLE);
            this.containerRadioMode.setVisibility(View.GONE);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.action_stream);
            }
            this.containerSeekBar.setVisibility(View.VISIBLE);
        }
    }

    private void initRadioStations() {
        this.allStations.clear();
        for (RadioStation st : DEFAULT_PRESETS) {
            this.allStations.add(st);
        }
        loadCustomStations();

        this.stationsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, this.allStations);
        this.spinnerRadioStations.setAdapter(this.stationsAdapter);

        String currentUrl = this.webStreamUrlTxt.getText().toString();
        for (int i = 0; i < this.allStations.size(); i++) {
            if (this.allStations.get(i).url.equalsIgnoreCase(currentUrl)) {
                this.spinnerRadioStations.setSelection(i);
                break;
            }
        }

        this.spinnerRadioStations.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < StreamMediaActivity.this.allStations.size()) {
                    RadioStation st = StreamMediaActivity.this.allStations.get(position);
                    StreamMediaActivity.this.webStreamUrlTxt.setText(st.url);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadCustomStations() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String json = prefs.getString(PREF_CUSTOM_STATIONS, "");
        if (!json.isEmpty()) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    this.allStations.add(new RadioStation(
                            obj.optString("name", "Custom Station"),
                            obj.optString("url", ""),
                            obj.optString("genre", "Custom"),
                            true
                    ));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse custom stations", e);
            }
        }
    }

    private void saveCustomStations() {
        JSONArray arr = new JSONArray();
        for (RadioStation st : this.allStations) {
            if (st.isCustom) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", st.name);
                    obj.put("url", st.url);
                    obj.put("genre", st.genre);
                    arr.put(obj);
                } catch (Exception e) {
                    Log.e(TAG, "Error serializing station", e);
                }
            }
        }
        PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                .edit().putString(PREF_CUSTOM_STATIONS, arr.toString()).apply();
    }

    private void pasteUrlFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String text = item.getText().toString().trim();
                if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("rtsp://")) {
                    this.webStreamUrlTxt.setText(text);
                    Toast.makeText(this, R.string.btn_paste_from_clipboard, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        Toast.makeText(this, R.string.msg_invalid_url, Toast.LENGTH_SHORT).show();
    }

    private void showAddStationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_add_station_title);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.dialog_add_station_name);
        layout.addView(nameInput);

        final EditText urlInput = new EditText(this);
        urlInput.setHint(R.string.dialog_add_station_url);
        String currentUrl = this.webStreamUrlTxt.getText().toString().trim();
        if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://")) {
            urlInput.setText(currentUrl);
        }
        layout.addView(urlInput);

        final EditText genreInput = new EditText(this);
        genreInput.setHint(R.string.dialog_add_station_genre);
        layout.addView(genreInput);

        builder.setView(layout);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = nameInput.getText().toString().trim();
                String url = urlInput.getText().toString().trim();
                String genre = genreInput.getText().toString().trim();

                if (name.isEmpty()) {
                    name = "Radio " + (StreamMediaActivity.this.allStations.size() + 1);
                }
                if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("rtsp://")) {
                    Toast.makeText(StreamMediaActivity.this, R.string.msg_invalid_url, Toast.LENGTH_SHORT).show();
                    return;
                }

                RadioStation newStation = new RadioStation(name, url, genre.isEmpty() ? "User" : genre, true);
                StreamMediaActivity.this.allStations.add(newStation);
                StreamMediaActivity.this.stationsAdapter.notifyDataSetChanged();
                StreamMediaActivity.this.spinnerRadioStations.setSelection(StreamMediaActivity.this.allStations.size() - 1);
                StreamMediaActivity.this.webStreamUrlTxt.setText(url);
                saveCustomStations();
                Toast.makeText(StreamMediaActivity.this, R.string.msg_station_saved, Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private String getActiveStreamPath() {
        if (this.rbModeRadio.isChecked()) {
            return this.webStreamUrlTxt.getText().toString().trim();
        } else {
            return this.file_path.getText().toString().trim();
        }
    }

    private boolean isNetworkStream(String path) {
        return path != null && (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("rtsp://") || path.startsWith("mms://"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.text_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!this.mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            if (!bindService(intent, this.mConnection, Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Failed to bind to TeamTalk service");
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        String filePath = this.file_path.getText().toString();
        String webUrl = this.webStreamUrlTxt.getText().toString();
        PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
                .putString(PREF_LAST_MEDIA_FILE, filePath)
                .putString(PREF_LAST_STREAM_URL, webUrl)
                .apply();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopLocalRadio();
        if (this.mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(this.mConnection);
            this.mConnection.setBound(false);
        }
        this.handler.removeCallbacks(this.progressUpdater);
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        this.streamMediaFileListener = new ClientEventListener.OnStreamMediaFileListener() {
            @Override
            public final void onStreamMediaFile(MediaFileInfo mediaFileInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StreamMediaActivity.this.handleStreamMediaFile(mediaFileInfo);
                    }
                });
            }
        };
        this.localMediaFileListener = new ClientEventListener.OnLocalMediaFileListener() {
            @Override
            public final void onLocalMediaFile(MediaFileInfo mediaFileInfo) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StreamMediaActivity.this.handleLocalMediaFile(mediaFileInfo);
                    }
                });
            }
        };
        if (service != null && service.getEventHandler() != null) {
            service.getEventHandler().registerOnStreamMediaFile(this.streamMediaFileListener, true);
            service.getEventHandler().registerOnLocalMediaFile(this.localMediaFileListener, true);
        }

        int clientFlags = getClient() != null ? getClient().getFlags() : 0;
        boolean clientIsStreaming = (clientFlags & ClientFlag.CLIENT_STREAM_AUDIO) != 0 || (clientFlags & ClientFlag.CLIENT_STREAM_VIDEO) != 0;
        if (service != null && (service.isStreamingMedia() || clientIsStreaming || service.getLocalPlaybackId() > 0)) {
            String path = service.getCurrentStreamPath();
            if (path != null && !path.isEmpty()) {
                if (isNetworkStream(path)) {
                    this.rbModeRadio.setChecked(true);
                    this.webStreamUrlTxt.setText(path);
                    switchMode(true);
                } else {
                    this.rbModeFile.setChecked(true);
                    this.file_path.setText(path);
                    switchMode(false);
                }
            }
            this.mMediaFileInfo = service.getCurrentMediaFileInfo();
            this.mPlayback = service.getCurrentPlayback();
            this.localPlaybackId = service.getLocalPlaybackId();
            this.isStreaming = service.isStreamingMedia() || clientIsStreaming;

            if (this.isStreaming) {
                this.btnStream.setText(R.string.action_stop);
                this.btnStop.setEnabled(true);
                this.btnPlayPause.setEnabled(true);
                if (this.mPlayback != null) {
                    this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_resume : R.string.action_pause);
                }
                startProgressUpdater();
            } else if (this.localPlaybackId > 0) {
                this.btnStop.setEnabled(true);
                if (this.mPlayback != null) {
                    this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_play : R.string.action_pause);
                }
                startProgressUpdater();
            }

            if (this.mMediaFileInfo != null) {
                showMediaFileInfo(this.mMediaFileInfo);
                updateSeekBar();
                updateSeekBarPosition(this.mMediaFileInfo.uElapsedMSec);
            }
        }

        this.btnSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseFilePicker();
            }
        });

        this.btnStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleStreaming();
            }
        });

        this.btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        this.btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAll();
            }
        });
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        if (service != null && service.getEventHandler() != null) {
            if (this.streamMediaFileListener != null) {
                service.getEventHandler().registerOnStreamMediaFile(this.streamMediaFileListener, false);
            }
            if (this.localMediaFileListener != null) {
                service.getEventHandler().registerOnLocalMediaFile(this.localMediaFileListener, false);
            }
        }
    }

    private void chooseFilePicker() {
        Utils.showFilePicker(this, REQUEST_STREAM_MEDIA, REQUEST_CUSTOM_FILE_PICKER, false, "*/*");
    }

    private boolean requestMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            boolean hasAudio = checkSelfPermission("android.permission.READ_MEDIA_AUDIO") == 0;
            boolean hasVideo = checkSelfPermission("android.permission.READ_MEDIA_VIDEO") == 0;
            if (!hasAudio || !hasVideo) {
                requestPermissions(new String[]{"android.permission.READ_MEDIA_AUDIO", "android.permission.READ_MEDIA_VIDEO"}, 100);
                return false;
            }
        }
        return true;
    }

    private void mediaSelectionStart() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{"audio/*", "video/*"});
        intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        intent.addCategory("android.intent.category.OPENABLE");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.button_select_media_file)), REQUEST_STREAM_MEDIA);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CUSTOM_FILE_PICKER && resultCode == RESULT_OK && data != null) {
            String selectedPath = data.getStringExtra(CustomFilePickerActivity.EXTRA_FILE_PATH);
            if (selectedPath != null && !selectedPath.isEmpty()) {
                this.file_path.setText(selectedPath);
                PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                        .edit().putString(PREF_LAST_MEDIA_FILE, selectedPath).apply();
                loadMediaFileInfo(selectedPath);
                this.playlistUris.clear();
                this.playlistIndex = 0;
                this.playlistUris.add(Uri.fromFile(new File(selectedPath)));
                this.btnPlayPause.setEnabled(true);
                return;
            }
        }
        if (requestCode == REQUEST_STREAM_MEDIA && resultCode == RESULT_OK && data != null) {
            this.playlistUris.clear();
            this.playlistIndex = 0;
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    this.playlistUris.add(data.getClipData().getItemAt(i).getUri());
                }
                playPlaylistCurrent();
                return;
            }
            if (data.getData() != null) {
                this.playlistUris.add(data.getData());
                playPlaylistCurrent();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void loadMediaFileInfo(String path) {
        if (path == null || path.isEmpty()) {
            this.mMediaFileInfo = null;
            this.txtMediaInfo.setVisibility(View.GONE);
            this.txtDuration.setText("00:00");
            this.seekBar.setProgress(0);
            this.seekBar.setEnabled(false);
            return;
        }
        if (isNetworkStream(path)) {
            this.mMediaFileInfo = null;
            this.txtMediaInfo.setText(getString(R.string.radio_live_indicator) + "\n" + path);
            this.txtMediaInfo.setVisibility(View.VISIBLE);
            this.txtDuration.setText("LIVE");
            this.containerSeekBar.setVisibility(View.GONE);
            return;
        }

        MediaFileInfo info = new MediaFileInfo();
        if (TeamTalkBase.getMediaFileInfo(path, info)) {
            this.mMediaFileInfo = info;
            showMediaFileInfo(info);
            updateSeekBar();
            this.containerSeekBar.setVisibility(View.VISIBLE);
        } else {
            this.mMediaFileInfo = null;
            this.txtMediaInfo.setVisibility(View.GONE);
            this.txtDuration.setText("00:00");
        }
    }

    private void showMediaFileInfo(MediaFileInfo info) {
        if (info == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.label_audio)).append(": ");
        if (info.audioFmt != null && info.audioFmt.nAudioFmt > 0) {
            sb.append(info.audioFmt.nSampleRate).append(" Hz, ");
            sb.append(info.audioFmt.nChannels).append(" ch");
        } else {
            sb.append(getString(R.string.msg_no_audio));
        }
        sb.append("\n").append(getString(R.string.label_video)).append(": ");
        if (info.videoFmt != null && info.videoFmt.picFourCC != 0) {
            sb.append(info.videoFmt.nWidth).append("x").append(info.videoFmt.nHeight);
        } else {
            sb.append(getString(R.string.msg_no_video));
        }
        if (info.uDurationMSec > 0) {
            sb.append("\n").append(getString(R.string.label_duration)).append(": ").append(formatDuration(info.uDurationMSec));
            this.txtDuration.setText(formatDuration(info.uDurationMSec));
        } else {
            sb.append("\n").append(getString(R.string.radio_live_indicator));
            this.txtDuration.setText("LIVE");
        }
        this.txtMediaInfo.setText(sb.toString());
        this.txtMediaInfo.setVisibility(View.VISIBLE);
    }

    private void toggleStreaming() {
        if (getClient() == null) {
            Toast.makeText(this, R.string.err_stream_media, Toast.LENGTH_SHORT).show();
            return;
        }

        int flags = getClient().getFlags();
        boolean clientIsStreaming = (flags & ClientFlag.CLIENT_STREAM_AUDIO) != 0 || (flags & ClientFlag.CLIENT_STREAM_VIDEO) != 0;
        boolean serviceStreaming = getService() != null && getService().isStreamingMedia();

        // Если стрим уже идет в канал — останавливаем его с одного нажатия
        if (this.isStreaming || clientIsStreaming || serviceStreaming) {
            stopAll();
            Toast.makeText(this, R.string.msg_stream_stopped, Toast.LENGTH_SHORT).show();
            return;
        }

        final String path = getActiveStreamPath();
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.msg_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        final boolean isWeb = isNetworkStream(path);
        if (!isWeb && this.mMediaFileInfo == null) {
            loadMediaFileInfo(path);
        }

        // Глушим локальное предпрослушивание перед стартом в канал
        stopLocalRadio();
        if (this.localPlaybackId > 0) {
            getClient().stopLocalPlayback(this.localPlaybackId);
            this.localPlaybackId = 0;
        }

        final VideoCodec videocodec = new VideoCodec();
        videocodec.nCodec = 0;
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.videoFmt != null && this.mMediaFileInfo.videoFmt.picFourCC != 0) {
            videocodec.nCodec = 128;
        }

        this.btnStream.setEnabled(false);
        Toast.makeText(this, R.string.msg_stream_started, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = getClient() != null && getClient().startStreamingMediaFileToChannel(path, videocodec);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StreamMediaActivity.this.btnStream.setEnabled(true);
                        if (!success) {
                            Toast.makeText(StreamMediaActivity.this, R.string.err_stream_media, Toast.LENGTH_LONG).show();
                            StreamMediaActivity.this.isStreaming = false;
                            StreamMediaActivity.this.btnStream.setText(R.string.button_stream_media_file);
                            return;
                        }

                        StreamMediaActivity.this.liveStreamStartTime = SystemClock.elapsedRealtime();
                        StreamMediaActivity.this.mPlayback = new MediaFilePlayback();
                        StreamMediaActivity.this.mPlayback.bPaused = false;
                        StreamMediaActivity.this.mPlayback.uOffsetMSec = 0;
                        StreamMediaActivity.this.isStreaming = true;
                        StreamMediaActivity.this.btnStream.setText(R.string.action_stop);
                        StreamMediaActivity.this.btnPlayPause.setText(R.string.action_pause);
                        StreamMediaActivity.this.btnStop.setEnabled(true);
                        StreamMediaActivity.this.startProgressUpdater();

                        if (isWeb) {
                            StreamMediaActivity.this.txtMediaInfo.setText(getString(R.string.msg_stream_live_playing) + "\n" + path);
                            StreamMediaActivity.this.txtMediaInfo.setVisibility(View.VISIBLE);
                            StreamMediaActivity.this.txtDuration.setText("LIVE");
                            StreamMediaActivity.this.containerSeekBar.setVisibility(View.GONE);
                        } else if (StreamMediaActivity.this.mMediaFileInfo != null) {
                            StreamMediaActivity.this.showMediaFileInfo(StreamMediaActivity.this.mMediaFileInfo);
                            StreamMediaActivity.this.containerSeekBar.setVisibility(View.VISIBLE);
                        }

                        Toast.makeText(StreamMediaActivity.this, isWeb ? R.string.msg_stream_live_playing : R.string.msg_stream_started, Toast.LENGTH_SHORT).show();
                        if (getService() != null) {
                            getService().setStreamingMedia(true);
                            getService().setCurrentStreamPath(path);
                            getService().setCurrentMediaFileInfo(StreamMediaActivity.this.mMediaFileInfo);
                            getService().setCurrentPlayback(StreamMediaActivity.this.mPlayback);
                        }
                    }
                });
            }
        }).start();
    }

    private void togglePlayPause() {
        String path = getActiveStreamPath();
        if (path.isEmpty()) {
            return;
        }

        // Если активна трансляция в канал — пауза / продолжение трансляции
        if (this.isStreaming && getClient() != null) {
            if (this.mPlayback == null) {
                this.mPlayback = new MediaFilePlayback();
            }
            this.mPlayback.bPaused = !this.mPlayback.bPaused;
            this.mPlayback.uOffsetMSec = MediaFilePlaybackConstants.TT_MEDIAPLAYBACK_OFFSET_IGNORE;
            VideoCodec vc = new VideoCodec();
            vc.nCodec = (this.mMediaFileInfo != null && this.mMediaFileInfo.videoFmt != null && this.mMediaFileInfo.videoFmt.picFourCC != 0) ? 128 : 0;
            if (getClient().updateStreamingMediaFileToChannel(this.mPlayback, vc)) {
                this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_resume : R.string.action_pause);
                if (getService() != null) {
                    getService().setCurrentPlayback(this.mPlayback);
                }
            }
            return;
        }

        // Интернет-радио / веб-поток — асинхронный локальный MediaPlayer
        if (isNetworkStream(path)) {
            if (this.localMediaPlayer != null) {
                if (this.localMediaPlayer.isPlaying()) {
                    this.localMediaPlayer.pause();
                    this.btnPlayPause.setText(R.string.action_play);
                } else {
                    this.localMediaPlayer.start();
                    this.btnPlayPause.setText(R.string.action_pause);
                }
                return;
            }
            startLocalRadio(path);
            return;
        }

        // Локальный файл на диске
        if (this.localPlaybackId > 0 && getClient() != null) {
            this.mPlayback.bPaused = !this.mPlayback.bPaused;
            this.mPlayback.uOffsetMSec = MediaFilePlaybackConstants.TT_MEDIAPLAYBACK_OFFSET_IGNORE;
            if (getClient().updateLocalPlayback(this.localPlaybackId, this.mPlayback)) {
                this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_play : R.string.action_pause);
                if (getService() != null) {
                    getService().setCurrentPlayback(this.mPlayback);
                }
            }
            return;
        }

        if (this.mMediaFileInfo == null) {
            loadMediaFileInfo(path);
        }

        this.mPlayback = new MediaFilePlayback();
        this.mPlayback.bPaused = false;
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.uDurationMSec > 0 && this.seekBar.getMax() > 0) {
            this.mPlayback.uOffsetMSec = (int) ((this.mMediaFileInfo.uDurationMSec * (long) this.seekBar.getProgress()) / this.seekBar.getMax());
        } else {
            this.mPlayback.uOffsetMSec = 0;
        }

        if (getClient() != null) {
            this.localPlaybackId = getClient().initLocalPlayback(path, this.mPlayback);
            if (this.localPlaybackId > 0) {
                this.liveStreamStartTime = SystemClock.elapsedRealtime();
                this.btnPlayPause.setText(R.string.action_pause);
                this.btnStop.setEnabled(true);
                startProgressUpdater();
                if (getService() != null) {
                    getService().setLocalPlaybackId(this.localPlaybackId);
                    getService().setCurrentStreamPath(path);
                    getService().setCurrentMediaFileInfo(this.mMediaFileInfo);
                    getService().setCurrentPlayback(this.mPlayback);
                }
                return;
            }
        }
        Toast.makeText(this, R.string.err_play_media, Toast.LENGTH_LONG).show();
    }

    private void startLocalRadio(final String url) {
        stopLocalRadio();
        this.btnPlayPause.setEnabled(false);
        this.txtPosition.setText("...");

        try {
            this.localMediaPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                this.localMediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                );
            }
            this.localMediaPlayer.setDataSource(url);
            this.localMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    StreamMediaActivity.this.btnPlayPause.setEnabled(true);
                    StreamMediaActivity.this.btnPlayPause.setText(R.string.action_pause);
                    StreamMediaActivity.this.btnStop.setEnabled(true);
                    StreamMediaActivity.this.isLocalMediaPlaying = true;
                    StreamMediaActivity.this.liveStreamStartTime = SystemClock.elapsedRealtime();
                    StreamMediaActivity.this.startProgressUpdater();
                    mp.start();
                    Toast.makeText(StreamMediaActivity.this, R.string.msg_stream_live_playing, Toast.LENGTH_SHORT).show();
                }
            });
            this.localMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                    StreamMediaActivity.this.btnPlayPause.setEnabled(true);
                    StreamMediaActivity.this.stopLocalRadio();
                    Toast.makeText(StreamMediaActivity.this, R.string.err_play_media, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            this.localMediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error starting local radio MediaPlayer", e);
            this.btnPlayPause.setEnabled(true);
            stopLocalRadio();
            Toast.makeText(this, R.string.err_play_media, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopLocalRadio() {
        if (this.localMediaPlayer != null) {
            try {
                if (this.localMediaPlayer.isPlaying()) {
                    this.localMediaPlayer.stop();
                }
                this.localMediaPlayer.release();
            } catch (Exception ignored) {}
            this.localMediaPlayer = null;
        }
        this.isLocalMediaPlaying = false;
    }

    // Мгновенная остановка ВСЕГО (и локального плеера, и стрима в канал) с 1 нажатия
    private void stopAll() {
        this.stoppingStream = true;
        this.isStreaming = false;
        stopLocalRadio();

        if (getClient() != null) {
            getClient().stopStreamingMediaFileToChannel();
        }

        if (this.localPlaybackId > 0 && getClient() != null) {
            getClient().stopLocalPlayback(this.localPlaybackId);
            this.localPlaybackId = 0;
        }

        this.btnStream.setText(R.string.button_stream_media_file);
        this.btnPlayPause.setText(R.string.action_play);
        this.btnStop.setEnabled(false);
        this.seekBar.setProgress(0);
        this.txtPosition.setText("00:00");
        this.handler.removeCallbacks(this.progressUpdater);

        if (getService() != null) {
            getService().setStreamingMedia(false);
            getService().setLocalPlaybackId(0);
            getService().setCurrentStreamPath("");
            getService().setCurrentMediaFileInfo(null);
            getService().setCurrentPlayback(null);
        }
    }

    private void handleStreamMediaFile(MediaFileInfo mfi) {
        if (this.stoppingStream) {
            if (mfi.nStatus == MediaFileStatus.MFS_CLOSED || mfi.nStatus == MediaFileStatus.MFS_FINISHED || mfi.nStatus == MediaFileStatus.MFS_ERROR) {
                this.stoppingStream = false;
            }
            this.isStreaming = false;
            this.btnStream.setText(R.string.button_stream_media_file);
            this.btnPlayPause.setText(R.string.action_play);
            this.btnStop.setEnabled(false);
            return;
        }

        this.mMediaFileInfo = mfi;
        if (getService() != null) {
            getService().setCurrentMediaFileInfo(mfi);
        }
        if (mfi.uDurationMSec > 0) {
            this.txtDuration.setText(formatDuration(mfi.uDurationMSec));
            if (!this.seekBarTouching) {
                updateSeekBarPosition(mfi.uElapsedMSec);
            }
        } else {
            this.txtDuration.setText("LIVE");
        }

        switch (mfi.nStatus) {
            case MediaFileStatus.MFS_CLOSED:
            case MediaFileStatus.MFS_FINISHED:
            case MediaFileStatus.MFS_ERROR:
                this.isStreaming = false;
                this.stoppingStream = false;
                this.btnStream.setText(R.string.button_stream_media_file);
                this.btnPlayPause.setText(R.string.action_play);
                if (this.localPlaybackId <= 0 && !this.isLocalMediaPlaying) {
                    this.btnStop.setEnabled(false);
                    this.handler.removeCallbacks(this.progressUpdater);
                }
                if (getService() != null) {
                    getService().setStreamingMedia(false);
                    getService().setCurrentStreamPath("");
                    getService().setCurrentMediaFileInfo(null);
                    getService().setCurrentPlayback(null);
                }
                if (!this.playlistUris.isEmpty() && this.playlistIndex < this.playlistUris.size() - 1) {
                    this.playlistIndex++;
                    playPlaylistCurrent();
                }
                break;
            case MediaFileStatus.MFS_PLAYING:
                this.isStreaming = true;
                this.btnStream.setText(R.string.action_stop);
                this.btnPlayPause.setText(R.string.action_pause);
                this.btnStop.setEnabled(true);
                startProgressUpdater();
                break;
            case MediaFileStatus.MFS_PAUSED:
                this.btnPlayPause.setText(R.string.action_resume);
                break;
        }
    }

    private void handleLocalMediaFile(MediaFileInfo mfi) {
        this.mMediaFileInfo = mfi;
        if (getService() != null) {
            getService().setCurrentMediaFileInfo(mfi);
        }
        if (!this.seekBarTouching && mfi.uDurationMSec > 0) {
            updateSeekBarPosition(mfi.uElapsedMSec);
        }

        switch (mfi.nStatus) {
            case MediaFileStatus.MFS_CLOSED:
            case MediaFileStatus.MFS_FINISHED:
            case MediaFileStatus.MFS_ERROR:
                this.localPlaybackId = 0;
                this.btnPlayPause.setText(R.string.action_play);
                this.btnStop.setEnabled(false);
                this.handler.removeCallbacks(this.progressUpdater);
                if (!this.seekBarTouching) {
                    this.seekBar.setProgress(0);
                    this.txtPosition.setText("00:00");
                }
                if (getService() != null) {
                    getService().setLocalPlaybackId(0);
                    getService().setCurrentPlayback(null);
                    getService().setCurrentMediaFileInfo(null);
                }
                break;
            case MediaFileStatus.MFS_PLAYING:
                this.btnPlayPause.setText(R.string.action_pause);
                break;
            case MediaFileStatus.MFS_PAUSED:
                this.btnPlayPause.setText(R.string.action_play);
                break;
        }
    }

    private void startProgressUpdater() {
        this.handler.removeCallbacks(this.progressUpdater);
        this.progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (StreamMediaActivity.this.localPlaybackId > 0 || StreamMediaActivity.this.isStreaming || StreamMediaActivity.this.isLocalMediaPlaying) {
                    if (StreamMediaActivity.this.mMediaFileInfo != null && StreamMediaActivity.this.mMediaFileInfo.uDurationMSec > 0) {
                        if (!StreamMediaActivity.this.seekBarTouching) {
                            StreamMediaActivity.this.updateSeekBarPosition(StreamMediaActivity.this.mMediaFileInfo.uElapsedMSec);
                        }
                    } else if (StreamMediaActivity.this.liveStreamStartTime > 0) {
                        long elapsed = SystemClock.elapsedRealtime() - StreamMediaActivity.this.liveStreamStartTime;
                        StreamMediaActivity.this.txtPosition.setText(StreamMediaActivity.this.formatDuration((int) elapsed));
                    }
                }
                StreamMediaActivity.this.handler.postDelayed(this, 500L);
            }
        };
        this.handler.postDelayed(this.progressUpdater, 500L);
    }

    public void updateSeekBarPosition(int elapsed) {
        this.txtPosition.setText(formatDuration(elapsed));
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.uDurationMSec > 0) {
            int progress = (int) ((elapsed * (long) this.seekBar.getMax()) / this.mMediaFileInfo.uDurationMSec);
            this.seekBar.setProgress(progress);
        }
    }

    private void updateSeekBar() {
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.uDurationMSec > 0) {
            this.seekBar.setEnabled(true);
        } else {
            this.seekBar.setEnabled(false);
        }
    }

    public String formatDuration(int msec) {
        int sec = msec / 1000;
        int min = sec / 60;
        int hours = min / 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", Integer.valueOf(hours), Integer.valueOf(min % 60), Integer.valueOf(sec % 60));
        }
        return String.format("%02d:%02d", Integer.valueOf(min), Integer.valueOf(sec % 60));
    }

    private void playPlaylistCurrent() {
        if (this.playlistUris.isEmpty() || this.playlistIndex >= this.playlistUris.size()) {
            return;
        }
        Uri uri = this.playlistUris.get(this.playlistIndex);
        String path = AbsolutePathHelper.getRealPath(getBaseContext(), uri);
        if (path == null && uri != null) {
            path = copyUriToCache(uri);
        }
        if (path != null) {
            this.file_path.setText(path);
            loadMediaFileInfo(path);
            Toast.makeText(this, getString(R.string.msg_playlist_playing, new Object[]{Integer.valueOf(this.playlistIndex + 1), Integer.valueOf(this.playlistUris.size())}), Toast.LENGTH_SHORT).show();
            if (this.isStreaming && getClient() != null) {
                getClient().stopStreamingMediaFileToChannel();
                VideoCodec videocodec = new VideoCodec();
                videocodec.nCodec = 0;
                if (this.mMediaFileInfo != null && this.mMediaFileInfo.videoFmt != null && this.mMediaFileInfo.videoFmt.picFourCC != 0) {
                    videocodec.nCodec = 128;
                }
                if (!getClient().startStreamingMediaFileToChannel(path, videocodec)) {
                    Toast.makeText(this, R.string.err_stream_media, Toast.LENGTH_SHORT).show();
                    this.isStreaming = false;
                    this.btnStream.setText(R.string.button_stream_media_file);
                    return;
                }
                this.isStreaming = true;
                this.btnStream.setText(R.string.action_stop);
                if (getService() != null) {
                    getService().setStreamingMedia(true);
                    getService().setCurrentStreamPath(path);
                    getService().setCurrentMediaFileInfo(this.mMediaFileInfo);
                }
            }
        }
    }

    private String copyUriToCache(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            File outFile = new File(getCacheDir(), "stream_temp_" + System.currentTimeMillis() + ".dat");
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Error copying uri to cache", e);
            return null;
        }
    }
}
