package org.nekit.ttproplus.gui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
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
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.Permissions;

public class StreamMediaActivity extends AppCompatActivity implements TeamTalkConnectionListener {
    public static final int REQUEST_STREAM_MEDIA = 1;
    public static final String TAG = "bearware";
    private static final String lastMedia = "last_media_file";
    private Button btnPlayPause;
    private Button btnSelectFile;
    private Button btnStop;
    private Button btnStream;
    private EditText file_path;
    private boolean isStreaming;
    private ClientEventListener.OnLocalMediaFileListener localMediaFileListener;
    private int localPlaybackId;
    TeamTalkConnection mConnection;
    private MediaFileInfo mMediaFileInfo;
    private MediaFilePlayback mPlayback;
    private Runnable progressUpdater;
    private SeekBar seekBar;
    private boolean seekBarTouching;
    private ClientEventListener.OnStreamMediaFileListener streamMediaFileListener;
    private TextView txtDuration;
    private TextView txtMediaInfo;
    private TextView txtPosition;
    private Handler handler = new Handler();
    private List<Uri> playlistUris = new ArrayList();
    private int playlistIndex = 0;

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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        this.file_path = (EditText) findViewById(R.id.file_path_txt);
        this.file_path.setText(PreferenceManager.getDefaultSharedPreferences(getBaseContext()).getString(lastMedia, ""));
        this.btnSelectFile = (Button) findViewById(R.id.media_file_select_btn);
        this.btnStream = (Button) findViewById(R.id.btn_stream);
        this.btnPlayPause = (Button) findViewById(R.id.btn_play_pause);
        this.btnStop = (Button) findViewById(R.id.btn_stop);
        this.txtMediaInfo = (TextView) findViewById(R.id.media_info);
        this.txtPosition = (TextView) findViewById(R.id.txt_position);
        this.txtDuration = (TextView) findViewById(R.id.txt_duration);
        this.seekBar = (SeekBar) findViewById(R.id.seek_bar);
        this.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { 
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && StreamMediaActivity.this.mMediaFileInfo != null && StreamMediaActivity.this.mMediaFileInfo.uDurationMSec > 0) {
                    int pos = (int) ((StreamMediaActivity.this.mMediaFileInfo.uDurationMSec * progress) / seekBar.getMax());
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
                int offset = (int) ((StreamMediaActivity.this.mMediaFileInfo.uDurationMSec * seekBar.getProgress()) / seekBar.getMax());
                if (StreamMediaActivity.this.mPlayback == null) {
                    StreamMediaActivity.this.mPlayback = new MediaFilePlayback();
                }
                StreamMediaActivity.this.mPlayback.uOffsetMSec = offset;
                StreamMediaActivity.this.mPlayback.bPaused = false;
                if (StreamMediaActivity.this.localPlaybackId > 0) {
                    StreamMediaActivity.this.getClient().updateLocalPlayback(StreamMediaActivity.this.localPlaybackId, StreamMediaActivity.this.mPlayback);
                } else if (StreamMediaActivity.this.isStreaming) {
                    VideoCodec vc = new VideoCodec();
                    vc.nCodec = (StreamMediaActivity.this.mMediaFileInfo.videoFmt != null && StreamMediaActivity.this.mMediaFileInfo.videoFmt.picFourCC != 0) ? 128 : 0;
                    StreamMediaActivity.this.getClient().updateStreamingMediaFileToChannel(StreamMediaActivity.this.mPlayback, vc);
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.text_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        if (id == 16908332) {
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
            Intent intent = new Intent(getApplicationContext(), (Class<?>) TeamTalkService.class);
            if (!bindService(intent, this.mConnection, 1)) {
                Log.e("bearware", "Failed to bind to TeamTalk service");
            }
        }
    }

        @Override
    public void onPause() {
        super.onPause();
        if (this.file_path != null) {
            String path = this.file_path.getText().toString();
            PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit().putString(lastMedia, path).apply();
        }
    }

        @Override
    public void onStop() {
        super.onStop();
        if (this.mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(this.mConnection);
            this.mConnection.setBound(false);
        }
        this.handler.removeCallbacks(this.progressUpdater);
    }

        @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissions granted = Permissions.onRequestResult(this, requestCode, grantResults);
        if (granted == null) {
            return;
        }
        if (granted == Permissions.READ_EXTERNAL_STORAGE || granted == Permissions.READ_MEDIA_VIDEO || granted == Permissions.READ_MEDIA_AUDIO) {
            if (Build.VERSION.SDK_INT < 33 || areMediaPermissionsComplete()) {
                mediaSelectionStart();
            }
        }
    }

        public void lambda$onServiceConnected$1(final MediaFileInfo mfi) {
        runOnUiThread(new Runnable() { 
            @Override
            public final void run() {
                StreamMediaActivity.this.lambda$onServiceConnected$0(mfi);
            }
        });
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        String path;
        this.streamMediaFileListener = new ClientEventListener.OnStreamMediaFileListener() { 
            @Override
            public final void onStreamMediaFile(MediaFileInfo mediaFileInfo) {
                StreamMediaActivity.this.lambda$onServiceConnected$1(mediaFileInfo);
            }
        };
        this.localMediaFileListener = new ClientEventListener.OnLocalMediaFileListener() { 
            @Override
            public final void onLocalMediaFile(MediaFileInfo mediaFileInfo) {
                StreamMediaActivity.this.lambda$onServiceConnected$3(mediaFileInfo);
            }
        };
        getService().getEventHandler().registerOnStreamMediaFile(this.streamMediaFileListener, true);
        getService().getEventHandler().registerOnLocalMediaFile(this.localMediaFileListener, true);
        int clientFlags = getClient() != null ? getClient().getFlags() : 0;
        boolean clientIsStreaming = (clientFlags & ClientFlag.CLIENT_STREAM_AUDIO) != 0 || (clientFlags & ClientFlag.CLIENT_STREAM_VIDEO) != 0;
        if (service.isStreamingMedia() || clientIsStreaming || service.getLocalPlaybackId() > 0) {
            path = service.getCurrentStreamPath();
            if (path != null && !path.isEmpty()) {
                this.file_path.setText(path);
            }
            this.mMediaFileInfo = service.getCurrentMediaFileInfo();
            this.mPlayback = service.getCurrentPlayback();
            this.localPlaybackId = service.getLocalPlaybackId();
            this.isStreaming = service.isStreamingMedia() || clientIsStreaming;
            if (this.mMediaFileInfo != null) {
                showMediaFileInfo(this.mMediaFileInfo);
                updateSeekBar();
                updateSeekBarPosition(this.mMediaFileInfo.uElapsedMSec);
            }
            if (this.isStreaming) {
                this.btnStream.setText(R.string.action_stop);
                this.btnStop.setEnabled(true);
                this.btnPlayPause.setEnabled(true);
                if (this.mPlayback != null) {
                    this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_resume : R.string.action_pause);
                } else {
                    this.btnPlayPause.setText(R.string.action_pause);
                }
                startProgressUpdater();
            } else if (this.localPlaybackId > 0) {
                this.btnStop.setEnabled(true);
                if (this.mPlayback != null) {
                    this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_play : R.string.action_pause);
                }
                startProgressUpdater();
            }
        }
        this.btnSelectFile.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                StreamMediaActivity.this.lambda$onServiceConnected$4(view);
            }
        });
        this.btnStream.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                StreamMediaActivity.this.lambda$onServiceConnected$5(view);
            }
        });
        this.btnPlayPause.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                StreamMediaActivity.this.lambda$onServiceConnected$6(view);
            }
        });
        this.btnStop.setOnClickListener(new View.OnClickListener() { 
            @Override
            public final void onClick(View view) {
                StreamMediaActivity.this.lambda$onServiceConnected$7(view);
            }
        });
    }

        public void lambda$onServiceConnected$3(final MediaFileInfo mfi) {
        runOnUiThread(new Runnable() { 
            @Override
            public final void run() {
                StreamMediaActivity.this.lambda$onServiceConnected$2(mfi);
            }
        });
    }

    public static final int REQUEST_CUSTOM_FILE_PICKER = 2;

    public void lambda$onServiceConnected$4(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.choose_file_picker_title);
        String[] options = new String[]{
                getString(R.string.file_picker_internal),
                getString(R.string.file_picker_system)
        };
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    Intent intent = new Intent(StreamMediaActivity.this, CustomFilePickerActivity.class);
                    intent.putExtra(CustomFilePickerActivity.EXTRA_FOLDER_MODE, false);
                    startActivityForResult(intent, REQUEST_CUSTOM_FILE_PICKER);
                } else {
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (!requestMediaPermissions()) {
                            return;
                        }
                    } else if (!Permissions.READ_EXTERNAL_STORAGE.request(StreamMediaActivity.this)) {
                        return;
                    }
                    mediaSelectionStart();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    public void lambda$onServiceConnected$5(View v) {
        toggleStreaming();
    }

    public void lambda$onServiceConnected$6(View v) {
        togglePlayPause();
    }

    public void lambda$onServiceConnected$7(View v) {
        stopLocalPlayback();
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CUSTOM_FILE_PICKER && resultCode == RESULT_OK && data != null) {
            String selectedPath = data.getStringExtra(CustomFilePickerActivity.EXTRA_FILE_PATH);
            if (selectedPath != null && !selectedPath.isEmpty()) {
                this.file_path.setText(selectedPath);
                PreferenceManager.getDefaultSharedPreferences(getBaseContext())
                        .edit().putString(lastMedia, selectedPath).apply();
                loadMediaFileInfo(selectedPath);
                this.playlistUris.clear();
                this.playlistIndex = 0;
                this.playlistUris.add(Uri.fromFile(new File(selectedPath)));
                if (this.btnPlayPause != null) {
                    this.btnPlayPause.setEnabled(true);
                }
                return;
            }
        }
        if (requestCode == 1 && resultCode == -1) {
            if (data == null) {
                return;
            }
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
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String getFileNameAndExtension(Uri uri) {
        Uri uri2;
        int nameIndex;
        int cut;
        String result = null;
        if (!"content".equals(uri.getScheme())) {
            uri2 = uri;
        } else {
            try {
                uri2 = uri;
                try {
                    Cursor cursor = getContentResolver().query(uri2, null, null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst() && (nameIndex = cursor.getColumnIndex("_display_name")) >= 0) {
                                result = cursor.getString(nameIndex);
                            }
                        } finally {
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                } catch (Exception e) {
                }
            } catch (Exception e2) {
                uri2 = uri;
            }
        }
        if (result == null) {
            String result2 = uri2.getPath();
            if (result2 != null && (cut = result2.lastIndexOf(47)) != -1) {
                return result2.substring(cut + 1);
            }
            return result2;
        }
        return result;
    }

    private String copyUriToCache(Uri uri) {
        int dotIndex;
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                return null;
            }
            String fileName = getFileNameAndExtension(uri);
            String extension = ".tmp";
            String prefix = "media_";
            if (fileName != null && (dotIndex = fileName.lastIndexOf(46)) != -1) {
                extension = fileName.substring(dotIndex);
                prefix = fileName.substring(0, dotIndex);
                if (prefix.length() < 3) {
                    prefix = "media_" + prefix;
                }
            }
            File cacheDir = getCacheDir();
            File tempFile = File.createTempFile(prefix + "_", extension, cacheDir);
            FileOutputStream os = new FileOutputStream(tempFile);
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len > 0) {
                    os.write(buf, 0, len);
                } else {
                    os.close();
                    is.close();
                    return tempFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            Log.e("bearware", "Failed to copy URI to cache", e);
            return null;
        }
    }

    private void mediaSelectionStart() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.ALLOW_MULTIPLE", true);
        Intent i = Intent.createChooser(intent, "File");
        startActivityForResult(i, 1);
    }

    private boolean requestMediaPermissions() {
        boolean video = Permissions.READ_MEDIA_VIDEO.request(this);
        boolean audio = Permissions.READ_MEDIA_AUDIO.request(this);
        return areMediaPermissionsComplete() && (video || audio);
    }

    private boolean areMediaPermissionsComplete() {
        return (Permissions.READ_MEDIA_VIDEO.isPending() || Permissions.READ_MEDIA_AUDIO.isPending()) ? false : true;
    }

    private void loadMediaFileInfo(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            this.mMediaFileInfo = null;
            this.txtMediaInfo.setText(R.string.msg_streaming_url);
            this.txtMediaInfo.setVisibility(0);
            this.txtDuration.setText("--:--");
            this.seekBar.setEnabled(false);
            return;
        }
        MediaFileInfo info = new MediaFileInfo();
        if (TeamTalkBase.getMediaFileInfo(path, info)) {
            this.mMediaFileInfo = info;
            showMediaFileInfo(info);
            updateSeekBar();
        } else {
            this.mMediaFileInfo = null;
            this.txtMediaInfo.setVisibility(8);
            this.txtDuration.setText("00:00");
        }
    }

    private void showMediaFileInfo(MediaFileInfo info) {
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
        sb.append("\n").append(getString(R.string.label_duration)).append(": ").append(formatDuration(info.uDurationMSec));
        this.txtMediaInfo.setText(sb.toString());
        this.txtMediaInfo.setVisibility(0);
        this.txtDuration.setText(formatDuration(info.uDurationMSec));
    }

    private void toggleStreaming() {
        if (this.isStreaming) {
            getClient().stopStreamingMediaFileToChannel();
            this.isStreaming = false;
            this.btnStream.setText(R.string.button_stream_media_file);
            this.btnPlayPause.setText(R.string.action_play);
            if (this.localPlaybackId <= 0) {
                this.btnStop.setEnabled(false);
                this.handler.removeCallbacks(this.progressUpdater);
            }
            Toast.makeText(this, R.string.msg_stream_stopped, Toast.LENGTH_SHORT).show();
            if (getService() != null) {
                getService().setStreamingMedia(false);
                getService().setCurrentStreamPath("");
                getService().setCurrentMediaFileInfo(null);
                getService().setCurrentPlayback(null);
            }
            return;
        }
        String path = this.file_path.getText().toString();
        if (path.isEmpty()) {
            return;
        }
        if (this.mMediaFileInfo == null) {
            loadMediaFileInfo(path);
        }
        VideoCodec videocodec = new VideoCodec();
        videocodec.nCodec = 0;
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.videoFmt != null && this.mMediaFileInfo.videoFmt.picFourCC != 0) {
            videocodec.nCodec = 128;
        }
        if (!getClient().startStreamingMediaFileToChannel(path, videocodec)) {
            Toast.makeText(this, R.string.err_stream_media, Toast.LENGTH_LONG).show();
            return;
        }
        this.mPlayback = new MediaFilePlayback();
        this.mPlayback.bPaused = false;
        this.mPlayback.uOffsetMSec = 0;
        this.isStreaming = true;
        this.btnStream.setText(R.string.action_stop);
        this.btnPlayPause.setText(R.string.action_pause);
        this.btnStop.setEnabled(true);
        startProgressUpdater();
        Toast.makeText(this, R.string.msg_stream_started, Toast.LENGTH_SHORT).show();
        if (getService() != null) {
            getService().setStreamingMedia(true);
            getService().setCurrentStreamPath(path);
            getService().setCurrentMediaFileInfo(this.mMediaFileInfo);
            getService().setCurrentPlayback(this.mPlayback);
        }
    }

    private void togglePlayPause() {
        String path = this.file_path.getText().toString();
        if (path.isEmpty()) {
            return;
        }
        if (this.isStreaming) {
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
        if (this.localPlaybackId > 0) {
            this.mPlayback.bPaused = !this.mPlayback.bPaused;
            this.mPlayback.uOffsetMSec = -1;
            if (getClient().updateLocalPlayback(this.localPlaybackId, this.mPlayback)) {
                this.btnPlayPause.setText(this.mPlayback.bPaused ? R.string.action_play : R.string.action_pause);
                if (getService() != null) {
                    getService().setCurrentPlayback(this.mPlayback);
                }
            }
            return;
        }
        if (this.mMediaFileInfo == null && !path.startsWith("http://") && !path.startsWith("https://")) {
            loadMediaFileInfo(path);
        }
        this.mPlayback = new MediaFilePlayback();
        this.mPlayback.bPaused = false;
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.uDurationMSec > 0 && this.seekBar.getMax() > 0) {
            this.mPlayback.uOffsetMSec = (int) ((this.mMediaFileInfo.uDurationMSec * this.seekBar.getProgress()) / this.seekBar.getMax());
        } else {
            this.mPlayback.uOffsetMSec = 0;
        }
        this.localPlaybackId = getClient().initLocalPlayback(path, this.mPlayback);
        if (this.localPlaybackId > 0) {
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
        Toast.makeText(this, R.string.err_play_media, Toast.LENGTH_LONG).show();
    }

    private void stopLocalPlayback() {
        if (this.isStreaming) {
            toggleStreaming();
        }
        if (this.localPlaybackId > 0) {
            getClient().stopLocalPlayback(this.localPlaybackId);
            this.localPlaybackId = 0;
            if (getService() != null) {
                getService().setLocalPlaybackId(0);
                getService().setCurrentPlayback(null);
                getService().setCurrentMediaFileInfo(null);
            }
        }
        this.btnPlayPause.setText(R.string.action_play);
        this.btnStop.setEnabled(false);
        this.seekBar.setProgress(0);
        this.txtPosition.setText("00:00");
        this.handler.removeCallbacks(this.progressUpdater);
    }

        /* renamed from: onStreamMediaFile, reason: merged with bridge method [inline-methods] */
    public void lambda$onServiceConnected$0(MediaFileInfo mfi) {
        this.mMediaFileInfo = mfi;
        if (getService() != null) {
            getService().setCurrentMediaFileInfo(mfi);
        }
        if (mfi.uDurationMSec > 0) {
            this.txtDuration.setText(formatDuration(mfi.uDurationMSec));
            if (!this.seekBarTouching) {
                updateSeekBarPosition(mfi.uElapsedMSec);
            }
        }
        switch (mfi.nStatus) {
            case MediaFileStatus.MFS_CLOSED:
            case MediaFileStatus.MFS_FINISHED:
            case MediaFileStatus.MFS_ERROR:
                this.isStreaming = false;
                this.btnStream.setText(R.string.button_stream_media_file);
                this.btnPlayPause.setText(R.string.action_play);
                if (this.localPlaybackId <= 0) {
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
                    return;
                }
                return;
            case MediaFileStatus.MFS_PLAYING:
                this.isStreaming = true;
                this.btnStream.setText(R.string.action_stop);
                this.btnPlayPause.setText(R.string.action_pause);
                this.btnStop.setEnabled(true);
                startProgressUpdater();
                return;
            case MediaFileStatus.MFS_PAUSED:
                this.btnPlayPause.setText(R.string.action_resume);
                return;
            default:
                return;
        }
    }

        /* renamed from: onLocalMediaFile, reason: merged with bridge method [inline-methods] */
    public void lambda$onServiceConnected$2(MediaFileInfo mfi) {
        this.mMediaFileInfo = mfi;
        if (getService() != null) {
            getService().setCurrentMediaFileInfo(mfi);
        }
        if (!this.seekBarTouching && mfi.uDurationMSec > 0) {
            updateSeekBarPosition(mfi.uElapsedMSec);
        }
        switch (mfi.nStatus) {
            case 1:
            case 3:
            case 4:
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
                    return;
                }
                return;
            case 2:
            case 6:
                this.btnPlayPause.setText(R.string.action_pause);
                return;
            case 5:
                this.btnPlayPause.setText(R.string.action_play);
                return;
            default:
                return;
        }
    }

    private void startProgressUpdater() {
        this.handler.removeCallbacks(this.progressUpdater);
        this.progressUpdater = new Runnable() { 
            @Override
            public void run() {
                if ((StreamMediaActivity.this.localPlaybackId > 0 || StreamMediaActivity.this.isStreaming) && !StreamMediaActivity.this.seekBarTouching && StreamMediaActivity.this.mMediaFileInfo != null) {
                    StreamMediaActivity.this.updateSeekBarPosition(StreamMediaActivity.this.mMediaFileInfo.uElapsedMSec);
                }
                StreamMediaActivity.this.handler.postDelayed(this, 250L);
            }
        };
        this.handler.postDelayed(this.progressUpdater, 250L);
    }

        public void updateSeekBarPosition(int elapsed) {
        this.txtPosition.setText(formatDuration(elapsed));
        if (this.mMediaFileInfo != null && this.mMediaFileInfo.uDurationMSec > 0) {
            int progress = (int) ((elapsed * this.seekBar.getMax()) / this.mMediaFileInfo.uDurationMSec);
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
            Toast.makeText(this, getString(R.string.msg_playlist_playing, new Object[]{Integer.valueOf(this.playlistIndex + 1), Integer.valueOf(this.playlistUris.size())}), 0).show();
            if (this.isStreaming) {
                getClient().stopStreamingMediaFileToChannel();
                VideoCodec videocodec = new VideoCodec();
                videocodec.nCodec = 0;
                if (this.mMediaFileInfo != null && this.mMediaFileInfo.videoFmt != null && this.mMediaFileInfo.videoFmt.picFourCC != 0) {
                    videocodec.nCodec = 128;
                }
                if (!getClient().startStreamingMediaFileToChannel(path, videocodec)) {
                    Toast.makeText(this, R.string.err_stream_media, 1).show();
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
}
