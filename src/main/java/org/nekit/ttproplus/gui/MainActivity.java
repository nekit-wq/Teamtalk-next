package org.nekit.ttproplus.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import org.nekit.ttproplus.data.AppInfo;
import org.nekit.ttproplus.data.ServerEntry;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.ListFragment;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import android.text.InputType;
import dk.bearware.Channel;
import dk.bearware.ChannelType;
import dk.bearware.ClientFlag;
import dk.bearware.ClientStatistics;
import dk.bearware.Constants;
import dk.bearware.MediaFileInfo;
import dk.bearware.MediaFilePlayback;
import dk.bearware.MediaFilePlaybackConstants;
import dk.bearware.MediaFileStatus;
import dk.bearware.RemoteFile;
import dk.bearware.ServerProperties;
import dk.bearware.SoundLevel;
import dk.bearware.StreamType;
import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.TextMsgType;
import dk.bearware.User;
import dk.bearware.UserAccount;
import dk.bearware.UserRight;
import dk.bearware.UserState;
import dk.bearware.UserType;
import dk.bearware.VideoCodec;
import dk.bearware.events.ClientEventListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.text.TextUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.function.Consumer;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.OnVoiceTransmissionToggleListener;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.FileListAdapter;
import org.nekit.ttproplus.data.MediaAdapter;
import org.nekit.ttproplus.data.MyTextMessage;
import org.nekit.ttproplus.data.Permissions;
import org.nekit.ttproplus.data.Preferences;
import org.nekit.ttproplus.data.ServerEntry;
import org.nekit.ttproplus.data.TTSWrapper;
import org.nekit.ttproplus.data.TextMessageAdapter;
import org.nekit.ttproplus.gui.MainActivity;
import org.nekit.ttproplus.utils.PrefsHelper;

public class MainActivity extends AppCompatActivity implements TeamTalkConnectionListener, AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, PopupMenu.OnMenuItemClickListener, SensorEventListener, OnVoiceTransmissionToggleListener, ClientEventListener.OnConnectionLostListener, ClientEventListener.OnCmdProcessingListener, ClientEventListener.OnCmdMyselfLoggedInListener, ClientEventListener.OnCmdMyselfLoggedOutListener, ClientEventListener.OnCmdMyselfKickedFromChannelListener, ClientEventListener.OnCmdUserUpdateListener, ClientEventListener.OnCmdUserLeftChannelListener, ClientEventListener.OnCmdChannelNewListener, ClientEventListener.OnCmdUserTextMessageListener, ClientEventListener.OnCmdUserJoinedChannelListener, ClientEventListener.OnCmdChannelRemoveListener, ClientEventListener.OnCmdChannelUpdateListener, ClientEventListener.OnCmdUserLoggedOutListener, ClientEventListener.OnCmdUserLoggedInListener, ClientEventListener.OnCmdFileRemoveListener, ClientEventListener.OnUserStateChangeListener, ClientEventListener.OnVoiceActivationListener, ClientEventListener.OnCmdFileNewListener {
    static final String MESSAGE_NOTIFICATION_TAG = "incoming_message";
    private static final String MSG_NOTIFICATION_CHANNEL_ID = "TT_PM";
    public static final String TAG = "bearware";
    AccessibilityAssistant accessibilityAssistant;
    SoundPool audioIcons;
    AudioManager audioManager;
    ChannelListAdapter channelsAdapter;
    ChannelsSectionFragment channelsFragment;
    ChatSectionFragment chatFragment;
    private Context ctx;
    Channel curchannel;
    FileListAdapter filesAdapter;
    FilesSectionFragment filesFragment;
    TeamTalkConnection mConnection;
    SectionsPagerAdapter mSectionsPagerAdapter;
    Sensor mSensor;
    SensorManager mSensorManager;
    TabLayout mTabLayout;
    ViewPager mViewPager;
    MediaAdapter mediaAdapter;
    MediaSectionFragment mediaFragment;
    Channel mychannel;
    NotificationManager notificationManager;
    private PrefsHelper prefs;
    PowerManager.WakeLock proximityWakeLock;
    boolean restarting;
    Channel selectedChannel;
    User selectedUser;
    TextMessageAdapter textmsgAdapter;
    VidcapSectionFragment vidcapFragment;
    PowerManager.WakeLock wakeLock;
    public final int REQUEST_EDITCHANNEL = 1;
    public final int REQUEST_NEWCHANNEL = 2;
    public final int REQUEST_EDITUSER = 3;
    public final int REQUEST_SELECT_FILE = 4;
    CountDownTimer stats_timer = null;
    SparseArray<CmdComplete> activecmds = new SparseArray<>();
    TTSWrapper ttsWrapper = null;
    private final Handler micActivityHandler = new Handler(Looper.getMainLooper());
    private final Runnable micActivityRunnable = new Runnable() { 
        @Override
        public void run() {
            if (MainActivity.this.prefs != null && ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_SHOW_MIC_ACTIVITY, false)).booleanValue() && MainActivity.this.mConnection.isBound() && MainActivity.this.getClient() != null) {
                ProgressBar micBar = (ProgressBar) MainActivity.this.findViewById(R.id.mic_activity_bar);
                if (micBar != null) {
                    micBar.setVisibility(0);
                    int level = MainActivity.this.getClient().getSoundInputLevel();
                    micBar.setProgress(level);
                }
            } else {
                ProgressBar micBar2 = (ProgressBar) MainActivity.this.findViewById(R.id.mic_activity_bar);
                if (micBar2 != null) {
                    micBar2.setVisibility(8);
                }
            }
            MainActivity.this.micActivityHandler.postDelayed(this, 100L);
        }
    };
    boolean isProximitySensorRegistered = false;
    Map<Integer, User> users = new HashMap();
    final Handler handler = new Handler(Looper.getMainLooper());
    final int SOUND_VOICETXON = 1;
    final int SOUND_VOICETXOFF = 2;
    final int SOUND_USERMSG = 3;
    final int SOUND_CHANMSG = 4;
    final int SOUND_BCASTMSG = 5;
    final int SOUND_SERVERLOST = 6;
    final int SOUND_FILESUPDATE = 7;
    final int SOUND_VOXENABLE = 8;
    final int SOUND_VOXDISABLE = 9;
    final int SOUND_VOXON = 10;
    final int SOUND_VOXOFF = 11;
    final int SOUND_TXREADY = 12;
    final int SOUND_TXSTOP = 13;
    final int SOUND_USERJOIN = 14;
    final int SOUND_USERLEFT = 15;
    final int SOUND_USERLOGGEDIN = 16;
    final int SOUND_USERLOGGEDOFF = 17;
    final int SOUND_INTERCEPTON = 18;
    final int SOUND_INTERCEPTOFF = 19;
    final int SOUND_CHANMSGSENT = 20;
    final int SOUND_TYPING = 21;
    SparseIntArray sounds = new SparseIntArray();
    List<Integer> userIDS = new ArrayList();
    final Map<Integer, Vector<MyTextMessage>> txtmsgMergeBuffer = new HashMap<>();

    private interface OnButtonInteractionListener extends View.OnTouchListener, View.OnClickListener {
    }

    private static final Map<String, String[]> SOUND_CANDIDATES = new HashMap<>();
    static {
        SOUND_CANDIDATES.put("serverlost", new String[]{"serverlost.wav", "serverlost.ogg"});
        SOUND_CANDIDATES.put("on", new String[]{"on.wav", "on.ogg", "hotkey.wav", "hotkey.ogg"});
        SOUND_CANDIDATES.put("off", new String[]{"off.wav", "off.ogg", "hotkey.wav", "hotkey.ogg"});
        SOUND_CANDIDATES.put("user_message", new String[]{"user_msg.wav", "user_message.ogg", "user_msg.ogg", "personal_message.ogg", "user_message.wav"});
        SOUND_CANDIDATES.put("channel_message", new String[]{"channel_msg.wav", "channel_message.ogg", "channel_msg.ogg", "channel_message.wav"});
        SOUND_CANDIDATES.put("channel_message_sent", new String[]{"channel_msg_sent.wav", "channel_message_sent.ogg", "user_msg_sent.wav", "user_message_sent.wav", "user_message_sent.ogg"});
        SOUND_CANDIDATES.put("broadcast_message", new String[]{"broadcast_msg.wav", "broadcast_message.ogg", "broadcast_msg.ogg", "broadcast_message.wav"});
        SOUND_CANDIDATES.put("fileupdate", new String[]{"fileupdate.wav", "fileupdate.ogg", "filetx_complete.wav", "filetx_complete.ogg"});
        SOUND_CANDIDATES.put("voiceact_enable", new String[]{"vox_enable.wav", "voiceact_enable.ogg", "vox_me_enable.wav", "voiceact_enable.wav"});
        SOUND_CANDIDATES.put("voiceact_disable", new String[]{"vox_disable.wav", "voiceact_disable.ogg", "vox_me_disable.wav", "voiceact_disable.wav"});
        SOUND_CANDIDATES.put("voiceact_on", new String[]{"voiceact_on.wav", "voiceact_on.ogg"});
        SOUND_CANDIDATES.put("voiceact_off", new String[]{"voiceact_off.wav", "voiceact_off.ogg"});
        SOUND_CANDIDATES.put("intercept", new String[]{"intercept.wav", "intercept.ogg"});
        SOUND_CANDIDATES.put("interceptend", new String[]{"interceptEnd.wav", "interceptend.ogg", "intercept_end.ogg", "interceptEnd.ogg", "interceptend.wav"});
        SOUND_CANDIDATES.put("txqueue_start", new String[]{"txqueue_start.wav", "txqueue_start.ogg"});
        SOUND_CANDIDATES.put("txqueue_stop", new String[]{"txqueue_stop.wav", "txqueue_stop.ogg"});
        SOUND_CANDIDATES.put("user_join", new String[]{"newuser.wav", "user_join.ogg", "newuser.ogg", "user_join.wav"});
        SOUND_CANDIDATES.put("user_left", new String[]{"removeuser.wav", "user_left.ogg", "removeuser.ogg", "user_left.wav"});
        SOUND_CANDIDATES.put("logged_on", new String[]{"logged_on.wav", "logged_on.ogg"});
        SOUND_CANDIDATES.put("logged_off", new String[]{"logged_off.wav", "logged_off.ogg"});
        SOUND_CANDIDATES.put("typing", new String[]{"typing.wav", "typing.ogg"});
    }

    public ChannelListAdapter getChannelsAdapter() {
        return this.channelsAdapter;
    }

    public FileListAdapter getFilesAdapter() {
        return this.filesAdapter;
    }

    public TextMessageAdapter getTextMessagesAdapter() {
        return this.textmsgAdapter;
    }

    public MediaAdapter getMediaAdapter() {
        return this.mediaAdapter;
    }

    private String appliedTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        this.ctx = getApplicationContext();
        this.prefs = new PrefsHelper(this.ctx);
        this.appliedTheme = (String) this.prefs.get(ThemeHelper.THEME_PREF_KEY, "dark");
        this.accessibilityAssistant = new AccessibilityAssistant(this);
        this.channelsAdapter = new ChannelListAdapter(this);
        this.filesAdapter = new FileListAdapter(this, this, this.accessibilityAssistant);
        this.textmsgAdapter = new TextMessageAdapter(this, this.accessibilityAssistant);
        this.mediaAdapter = new MediaAdapter(this);
        this.mConnection = new TeamTalkConnection(this);
        this.ttsWrapper = new TTSWrapper(this, (String) this.prefs.get("pref_speech_engine", TTSWrapper.defaultEngineName));

        this.mSensorManager = (SensorManager) getSystemService("sensor");
        if (this.mSensorManager != null) {
            this.mSensor = this.mSensorManager.getDefaultSensor(8);
        }
        this.restarting = savedInstanceState != null;
        this.audioManager = (AudioManager) getSystemService("audio");
        this.notificationManager = (NotificationManager) getSystemService("notification");
        PowerManager pm = (PowerManager) getSystemService("power");
        if (pm != null) {
            this.wakeLock = pm.newWakeLock(1, "bearware:TeamTalk5");
            this.proximityWakeLock = pm.newWakeLock(32, "bearware:TeamTalk5");
            this.wakeLock.setReferenceCounted(false);
            this.proximityWakeLock.setReferenceCounted(false);
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        EdgeToEdgeHelper.enableEdgeToEdge(this);
        String serverName = getIntent().getStringExtra(ServerEntry.KEY_SERVERNAME);
        if (serverName != null && !serverName.isEmpty()) {
            setTitle(serverName);
        }
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
        this.mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        this.mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        this.mViewPager = (ViewPager) findViewById(R.id.pager);
        this.mViewPager.setAdapter(this.mSectionsPagerAdapter);
        this.mViewPager.addOnPageChangeListener(this.mSectionsPagerAdapter);
        this.mTabLayout.setupWithViewPager(this.mViewPager);
        setupButtons();
        if (Build.VERSION.SDK_INT >= 26) {
            final MediaPlayer mMediaPlayer = MediaPlayer.create(this.ctx, R.raw.silence);
            if (mMediaPlayer != null) {
                mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { 
                    @Override
                    public final void onCompletion(MediaPlayer mediaPlayer) {
                        mMediaPlayer.release();
                    }
                });
                mMediaPlayer.start();
            }
        }

        View netStatsContainer = findViewById(R.id.network_stats_container);
        if (netStatsContainer != null) {
            netStatsContainer.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, NetworkMonitorActivity.class);
                startActivity(intent);
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (getClient() == null) {
            return super.onPrepareOptionsMenu(menu);
        }
        UserAccount myuseraccount = new UserAccount();
        getClient().getMyUserAccount(myuseraccount);
        boolean z = false;
        boolean uploadRight = (myuseraccount.uUserRights & 512) != 0;
        boolean broadcastRight = (myuseraccount.uUserRights & 16) != 0;
        boolean isEditable = this.curchannel != null;
        boolean isLeaveable = getClient().getMyChannelID() > 0;
        boolean isMyChannel = this.curchannel != null && getClient().getMyChannelID() == this.curchannel.nChannelID;
        boolean canEditServer = (myuseraccount.uUserRights & 2048) != 0;
        boolean hasUserAccounts = (myuseraccount.uUserType & 2) != 0;
        boolean canBan = (myuseraccount.uUserRights & 64) != 0;

        MenuItem muteTtsItem = menu.findItem(R.id.action_mute_tts);
        if (muteTtsItem != null) {
            boolean isTtsMuted = this.ttsWrapper != null && this.ttsWrapper.isMuted();
            muteTtsItem.setTitle(isTtsMuted ? R.string.action_unmute_tts : R.string.action_mute_tts);
            muteTtsItem.setIcon(isTtsMuted ? R.drawable.mute_blue : R.drawable.speaker_blue);
        }

        MenuItem chanToggleItem = menu.findItem(R.id.action_channel_toggle);
        if (chanToggleItem != null) {
            if (isMyChannel) {
                chanToggleItem.setTitle(R.string.action_leave);
            } else {
                chanToggleItem.setTitle(R.string.action_join_channel);
            }
            chanToggleItem.setEnabled(true).setVisible(true);
        }

        MenuItem editItem = menu.findItem(R.id.action_edit);
        if (editItem != null) editItem.setEnabled(isEditable).setVisible(isEditable);
        MenuItem uploadItem = menu.findItem(R.id.action_upload);
        if (uploadItem != null) uploadItem.setEnabled(uploadRight).setVisible(uploadRight);
        MenuItem bcastItem = menu.findItem(R.id.action_broadcast);
        if (bcastItem != null) bcastItem.setEnabled(broadcastRight).setVisible(broadcastRight);
        MenuItem streamItem = menu.findItem(R.id.action_stream);
        MediaFileInfo mfi = getService() != null ? getService().getCurrentMediaFileInfo() : null;
        int flags = getClient().getFlags();
        boolean isPaused = (mfi != null && mfi.nStatus == MediaFileStatus.MFS_PAUSED);
        boolean isStreaming = (flags & ClientFlag.CLIENT_STREAM_AUDIO) == ClientFlag.CLIENT_STREAM_AUDIO || (flags & ClientFlag.CLIENT_STREAM_VIDEO) == ClientFlag.CLIENT_STREAM_VIDEO;

        MenuItem pauseItem = menu.findItem(R.id.action_pause);
        if (pauseItem != null) {
            pauseItem.setEnabled(isStreaming).setVisible(isStreaming);
            pauseItem.setTitle(isPaused ? R.string.action_resume : R.string.action_pause);
        }
        if (streamItem != null) {
            streamItem.setEnabled(isMyChannel || isStreaming).setVisible(isMyChannel || isStreaming);
            streamItem.setTitle(isStreaming ? R.string.action_stop : R.string.action_stream);
        }
        MenuItem statusNickItem = menu.findItem(R.id.action_statusnick);
        if (statusNickItem != null) {
            statusNickItem.setEnabled(isLeaveable).setVisible(isLeaveable);
        }
        MenuItem userAccItem = menu.findItem(R.id.action_user_accounts);
        if (userAccItem != null) userAccItem.setEnabled(hasUserAccounts).setVisible(hasUserAccounts && isLeaveable);
        MenuItem srvPropItem = menu.findItem(R.id.action_server_properties);
        if (srvPropItem != null) srvPropItem.setEnabled(canEditServer).setVisible(canEditServer && isLeaveable);
        MenuItem bannedItem = menu.findItem(R.id.action_banned_users);
        if (bannedItem != null) bannedItem.setEnabled(canBan).setVisible(canBan && isLeaveable);
        MenuItem statsItem = menu.findItem(R.id.action_server_stats);
        if (statsItem != null) statsItem.setEnabled(hasUserAccounts && isLeaveable).setVisible(hasUserAccounts && isLeaveable);
        MenuItem chatHistoryItem = menu.findItem(R.id.action_chat_history);
        if (chatHistoryItem != null) chatHistoryItem.setEnabled(true).setVisible(true);
        MenuItem netMonItem = menu.findItem(R.id.action_network_monitor);
        if (netMonItem != null) netMonItem.setEnabled(true).setVisible(true);
        boolean isRecording = getService() != null && getService().isRecording();
        MenuItem startRecItem = menu.findItem(R.id.action_start_recording);
        if (startRecItem != null) startRecItem.setEnabled(isLeaveable && !isRecording).setVisible(isLeaveable && !isRecording);
        MenuItem stopRecItem = menu.findItem(R.id.action_stop_recording);
        if (stopRecItem != null) {
            stopRecItem.setEnabled(isLeaveable && isRecording);
            stopRecItem.setVisible(isLeaveable && isRecording);
        }
        MenuItem recItem = menu.findItem(R.id.action_recordings);
        if (recItem != null) recItem.setEnabled(true).setVisible(true);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Channel parentChannel;
        File recordedFile;
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        int itemId = item.getItemId();
        if (itemId == R.id.action_statusnick) {
            showChangeNicknameStatusDialog();
            return true;
        }
        if (itemId == R.id.action_pause) {
            MediaFileInfo currentMfi = getService() != null ? getService().getCurrentMediaFileInfo() : null;
            MediaFilePlayback pb = new MediaFilePlayback();
            VideoCodec vc = new VideoCodec();
            pb.uOffsetMSec = MediaFilePlaybackConstants.TT_MEDIAPLAYBACK_OFFSET_IGNORE;
            pb.bPaused = (currentMfi != null && currentMfi.nStatus == MediaFileStatus.MFS_PLAYING);
            getClient().updateStreamingMediaFileToChannel(pb, vc);
            invalidateOptionsMenu();
            return true;
        }
        if (itemId == R.id.action_stream) {
            int flags = getClient() != null ? getClient().getFlags() : 0;
            boolean isStreaming = (flags & ClientFlag.CLIENT_STREAM_AUDIO) != 0 || (flags & ClientFlag.CLIENT_STREAM_VIDEO) != 0;
            if (isStreaming) {
                if (getClient() != null) {
                    getClient().stopStreamingMediaFileToChannel();
                }
                if (getService() != null) {
                    getService().setStreamingMedia(false);
                    getService().setCurrentStreamPath("");
                    getService().setCurrentMediaFileInfo(null);
                    getService().setCurrentPlayback(null);
                }
                Toast.makeText(this, R.string.msg_stream_stopped, Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
                return true;
            }
            Intent intent = new Intent(this, (Class<?>) StreamMediaActivity.class);
            startActivity(intent);
            return true;
        }
        if (itemId == R.id.action_mute_tts) {
            if (this.ttsWrapper != null) {
                boolean newMuted = !this.ttsWrapper.isMuted();
                this.ttsWrapper.setMuted(newMuted);
                String msg = getString(newMuted ? R.string.msg_tts_muted : R.string.msg_tts_unmuted);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                if (!newMuted) {
                    this.ttsWrapper.speak(msg);
                }
                invalidateOptionsMenu();
            }
            return true;
        }
        if (itemId == R.id.action_channel_toggle) {
            boolean isMyChannel = this.curchannel != null && getClient() != null && getClient().getMyChannelID() == this.curchannel.nChannelID;
            if (isMyChannel) {
                leaveChannel();
            } else {
                if (this.curchannel != null && this.curchannel.nChannelID > 0) {
                    joinChannel(this.curchannel);
                } else if (getClient() != null && getService() != null) {
                    int rootId = getClient().getRootChannelID();
                    Channel root = getService().getChannels().get(Integer.valueOf(rootId));
                    if (root != null) {
                        joinChannel(root);
                    }
                }
            }
            invalidateOptionsMenu();
            return true;
        }
        if (itemId == R.id.action_upload) {
            if (Build.VERSION.SDK_INT >= 33) {
                if (!requestMediaPermissions()) {
                    return true;
                }
            } else if (!Permissions.READ_EXTERNAL_STORAGE.request(this)) {
                return true;
            }
            fileSelectionStart();
            return true;
        }
        if (itemId == R.id.action_broadcast) {
            alert.setTitle(R.string.action_broadcast);
            alert.setMessage(R.string.text_broadcast_message);
            final EditText input = new EditText(this);
            input.setInputType(131073);
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$onOptionsItemSelected$1(input, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.setView(input);
            alert.show();
            return true;
        }
        if (itemId == R.id.action_stream) {
            Intent intent = new Intent(this, (Class<?>) StreamMediaActivity.class);
            startActivity(intent);
            return true;
        }
        if (itemId == R.id.action_edit) {
            if (this.curchannel != null) {
                editChannelProperties(this.curchannel);
                return true;
            }
            return true;
        }
        if (itemId == R.id.action_newchannel) {
            Intent intent2 = new Intent(this, (Class<?>) ChannelPropActivity.class);
            int parent_chan_id = getClient().getRootChannelID();
            if (this.curchannel != null) {
                parent_chan_id = this.curchannel.nChannelID;
            }
            startActivityForResult(intent2.putExtra(ChannelPropActivity.EXTRA_PARENTID, parent_chan_id), 2);
            return true;
        }
        if (itemId == R.id.action_user_accounts) {
            Intent intent3 = new Intent(this, (Class<?>) UserAccountsActivity.class);
            startActivity(intent3);
            return true;
        }
        if (itemId == R.id.action_server_properties) {
            Intent intent4 = new Intent(this, (Class<?>) ServerPropActivity.class);
            startActivity(intent4);
            return true;
        }
        if (itemId == R.id.action_banned_users) {
            Intent intent5 = new Intent(this, (Class<?>) ServerBannedUsersActivity.class);
            startActivity(intent5);
            return true;
        }
        if (itemId == R.id.action_server_stats) {
            Intent intent6 = new Intent(this, (Class<?>) ServerStatsActivity.class);
            startActivity(intent6);
            return true;
        }
        if (itemId == R.id.action_chat_history) {
            Intent intentHistory = new Intent(this, (Class<?>) ChatHistoryActivity.class);
            startActivity(intentHistory);
            return true;
        }
        if (itemId == R.id.action_network_monitor) {
            Intent intentNet = new Intent(this, (Class<?>) NetworkMonitorActivity.class);
            startActivity(intentNet);
            return true;
        }
        if (itemId == R.id.action_settings) {
            Intent intent7 = new Intent(this, (Class<?>) PreferencesActivity.class);
            startActivity(intent7);
            return true;
        }
        if (itemId == R.id.action_start_recording) {
            if (getService() != null) {
                getService().startRecording();
                return true;
            }
            return true;
        }
        if (itemId == R.id.action_stop_recording) {
            if (getService() != null && (recordedFile = getService().stopRecording()) != null && getService().shouldShowRecordingDialog()) {
                showRecordingCompleteDialog(recordedFile);
                return true;
            }
            return true;
        }
        if (itemId == R.id.action_recordings) {
            Intent intentRec = new Intent(this, (Class<?>) RecordingsActivity.class);
            startActivity(intentRec);
            return true;
        }
        if (itemId == R.id.action_online_users) {
            Intent intent8 = new Intent(this, (Class<?>) OnlineUsersActivity.class);
            startActivity(intent8);
            return true;
        }
        if (itemId == R.id.action_publishsrv) {
            publishCurrentServer();
            return true;
        }
        if (itemId == 16908332 || itemId == android.R.id.home) {
            int currentPage = this.mViewPager.getCurrentItem();
            if (currentPage != 0) {
                this.mViewPager.setCurrentItem(0);
                return true;
            }
            if (this.curchannel != null) {
                parentChannel = (this.curchannel.nParentID > 0 && getService() != null)
                        ? getService().getChannels().get(Integer.valueOf(this.curchannel.nParentID))
                        : null;
                setCurrentChannel(parentChannel);
                if (this.channelsAdapter != null) {
                    this.channelsAdapter.notifyDataSetChanged();
                }
                return true;
            }
            if (this.filesAdapter != null && this.filesAdapter.getActiveTransfersCount() > 0) {
                alert.setMessage(R.string.disconnect_alert);
                alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { 
                    @Override
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.lambda$onOptionsItemSelected$2(dialogInterface, i);
                    }
                });
                alert.setNegativeButton(android.R.string.cancel, (DialogInterface.OnClickListener) null);
                alert.show();
                return true;
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showChangeNicknameStatusDialog() {
        if (getService() == null || getClient() == null) return;
        User myself = getService().getUsers().get(Integer.valueOf(getClient().getMyUserID()));
        if (myself == null) {
            Toast.makeText(this, R.string.text_con_cmderr, Toast.LENGTH_SHORT).show();
            return;
        }

        final int[] modeValues = {
                0, // STATUSMODE_AVAILABLE
                1, // STATUSMODE_AWAY
                2  // STATUSMODE_QUESTION
        };
        int checkedItem = 0;
        int currentMode = myself.nStatusMode & 3;
        for (int i = 0; i < modeValues.length; i++) {
            if (modeValues[i] == currentMode) {
                checkedItem = i;
                break;
            }
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 20);
        layout.setPadding(padding, padding / 2, padding, 0);

        TextView nicknameLabel = new TextView(this);
        nicknameLabel.setText(R.string.pref_title_nickname);
        layout.addView(nicknameLabel);

        final EditText nicknameInput = new EditText(this);
        nicknameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nicknameInput.setSingleLine();
        nicknameInput.setText(myself.szNickname != null ? myself.szNickname : "");
        nicknameInput.setSelection(nicknameInput.getText().length());
        layout.addView(nicknameInput);

        TextView messageLabel = new TextView(this);
        messageLabel.setText(R.string.text_status_message);
        layout.addView(messageLabel);

        final EditText statusMessageInput = new EditText(this);
        statusMessageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        statusMessageInput.setSingleLine();
        statusMessageInput.setText(myself.szStatusMsg != null ? myself.szStatusMsg : "");
        statusMessageInput.setSelection(statusMessageInput.getText().length());
        layout.addView(statusMessageInput);

        TextView modeLabel = new TextView(this);
        modeLabel.setText(R.string.text_status_mode);
        layout.addView(modeLabel);

        final RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);

        RadioButton availableButton = new RadioButton(this);
        availableButton.setId(View.generateViewId());
        availableButton.setText(R.string.status_mode_available);
        modeGroup.addView(availableButton);

        RadioButton awayButton = new RadioButton(this);
        awayButton.setId(View.generateViewId());
        awayButton.setText(R.string.status_mode_away);
        modeGroup.addView(awayButton);

        RadioButton questionButton = new RadioButton(this);
        questionButton.setId(View.generateViewId());
        questionButton.setText(R.string.status_mode_question);
        modeGroup.addView(questionButton);

        modeGroup.check(modeGroup.getChildAt(checkedItem).getId());
        layout.addView(modeGroup);

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_statusnick)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int selectedIndex = modeGroup.indexOfChild(
                                modeGroup.findViewById(modeGroup.getCheckedRadioButtonId()));
                        int selectedMode = (selectedIndex >= 0 && selectedIndex < modeValues.length) ? modeValues[selectedIndex] : modeValues[0];
                        applyNicknameStatusChange(
                                nicknameInput.getText().toString(),
                                selectedMode,
                                statusMessageInput.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyNicknameStatusChange(String nickname, int mode, String statusMessage) {
        if (getService() == null || getClient() == null) return;
        User myself = getService().getUsers().get(Integer.valueOf(getClient().getMyUserID()));
        if (myself == null) return;

        ServerEntry serverEntry = getService().getServerEntry();
        if (serverEntry != null) {
            serverEntry.nickname = nickname;
            serverEntry.statusmsg = statusMessage;
            getService().setServerEntry(serverEntry);
        }

        if (!TextUtils.equals(nickname, myself.szNickname)) {
            getClient().doChangeNickname(nickname);
        }

        int statusMode = (myself.nStatusMode & ~3) | mode;
        getClient().doChangeStatus(statusMode, statusMessage);
    }

    private void publishCurrentServer() {
        ServerEntry entry = null;
        if (getService() != null) {
            entry = getService().getServerEntry();
        }
        if (entry == null) {
            Toast.makeText(this, R.string.err_publish_server_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String username = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
        String token = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, "");

        if (username.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, R.string.err_publish_server_login, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, WebLoginActivity.class);
            startActivity(intent);
            return;
        }

        final ServerEntry finalEntry = entry;
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.title_publish_server);
        alert.setMessage(getString(R.string.msg_publish_server_confirmation, finalEntry.servername));
        alert.setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
            new Thread(() -> {
                String serverXml = Utils.generateServerEntryXml(finalEntry);
                String response = Utils.postURL(AppInfo.getPublishServerUrl(MainActivity.this, username, token), serverXml);
                final boolean finalSuccess = response != null && !response.isEmpty();

                runOnUiThread(() -> {
                    if (finalSuccess) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.text_publish_server_success);
                        builder.setMessage(R.string.msg_publish_server_verification_detail);
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setNeutralButton(R.string.action_copy_tag, (d, w) -> {
                            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText(getString(R.string.tag_clipboard), getString(R.string.tag_publish_server));
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(clip);
                            }
                            Toast.makeText(MainActivity.this, getString(R.string.text_copied_to_clipboard, getString(R.string.tag_publish_server)), Toast.LENGTH_SHORT).show();
                        });
                        builder.show();
                    } else {
                        Toast.makeText(MainActivity.this, R.string.err_publish_server_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }).start();
        });
        alert.setNegativeButton(android.R.string.no, null);
        alert.show();
    }

        public void lambda$onOptionsItemSelected$1(final EditText input, DialogInterface dialog, int whichButton) {
        getClient().doTextMessage(new TextMessage() { 
            {
                this.nMsgType = 3;
                this.szMessage = input.getText().toString();
            }
        });
    }

        public void lambda$onOptionsItemSelected$2(DialogInterface dialog, int whichButton) {
        this.filesAdapter.cancelAllTransfers();
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (this.ttsWrapper == null) {
            this.ttsWrapper = new TTSWrapper(this, (String) this.prefs.get("pref_speech_engine", TTSWrapper.defaultEngineName));
        }
        if (this.mConnection.isBound() && getService() != null && getService().getFloatingWindowManager() != null) {
            getService().getFloatingWindowManager().hideIfAppOpened();
        }
        if (!this.mConnection.isBound() || getService() == null || getClient() == null) {
            Intent intent = new Intent(this.ctx, (Class<?>) TeamTalkService.class);
            Log.d("bearware", "Binding TeamTalk service");
            if (!bindService(intent, this.mConnection, 1)) {
                Log.e("bearware", "Failed to bind to TeamTalk service");
                return;
            }
            return;
        }
        adjustSoundSystem();
        if (((Boolean) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_BLUETOOTH_HEADSET, false)).booleanValue()) {
            if (Permissions.BLUETOOTH.request(this)) {
                getService().watchBluetoothHeadset();
            }
        } else {
            getService().unwatchBluetoothHeadset();
        }
        int mastervol = ((Integer) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_MASTERVOLUME, Integer.valueOf(SoundLevel.SOUND_VOLUME_DEFAULT))).intValue();
        int gain = ((Integer) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, Integer.valueOf(SoundLevel.SOUND_GAIN_DEFAULT))).intValue();
        int voxlevel = ((Integer) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 5)).intValue();
        boolean voxState = getService().isVoiceActivationEnabled();
        boolean txState = getService().isVoiceTransmitting();
        if (getClient().getSoundOutputVolume() != mastervol) {
            getClient().setSoundOutputVolume(mastervol);
        }
        if (getClient().getSoundInputGainLevel() != gain) {
            getClient().setSoundInputGainLevel(gain);
        }
        if (getClient().getVoiceActivationLevel() != voxlevel) {
            getClient().setVoiceActivationLevel(voxlevel);
        }
        adjustMuteButton((ImageButton) findViewById(R.id.speakerBtn));
        adjustVoxState(voxState, voxState ? voxlevel : gain);
        adjustTxState(txState);
        SeekBar masterSeekBar = (SeekBar) findViewById(R.id.master_volSeekBar);
        SeekBar micSeekBar = (SeekBar) findViewById(R.id.mic_gainSeekBar);
        if (masterSeekBar != null) {
            masterSeekBar.setProgress(Utils.refVolumeToPercent(getClient().getSoundOutputVolume()));
        }
        if (micSeekBar != null) {
            if (getService().isVoiceActivationEnabled()) {
                micSeekBar.setProgress(getClient().getVoiceActivationLevel());
            } else {
                micSeekBar.setProgress(Utils.refVolumeToPercent(getClient().getSoundInputGainLevel()));
            }
        }
        TextView volLevel = (TextView) findViewById(R.id.vollevel_text);
        if (volLevel != null) {
            volLevel.setText(Utils.refVolumeToPercent(mastervol) + "%");
            volLevel.setContentDescription(getString(R.string.speaker_volume_description, new Object[]{volLevel.getText()}));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentTheme = (String) this.prefs.get(ThemeHelper.THEME_PREF_KEY, "dark");
        if (this.appliedTheme != null && !this.appliedTheme.equals(currentTheme)) {
            this.appliedTheme = currentTheme;
            recreate();
            return;
        }
        this.micActivityHandler.post(this.micActivityRunnable);
        boolean proximitySensor = ((Boolean) this.prefs.get("proximity_sensor_checkbox", false)).booleanValue();
        if (proximitySensor && this.mSensorManager != null && this.mSensor != null) {
            this.mSensorManager.registerListener(this, this.mSensor, 3);
            this.isProximitySensorRegistered = true;
        }
        if (this.audioIcons != null) {
            this.audioIcons.release();
        }
        this.sounds.clear();
        this.audioIcons = new SoundPool(1, 3, 0);
        String soundPack = (String) this.prefs.get("sound_pack_preference", "default");
        boolean isSilentPack = "silent".equals(soundPack);
        if (!isSilentPack) {
            if (((Boolean) this.prefs.get("server_lost_audio_icon", true)).booleanValue()) {
                this.sounds.put(6, loadSound(this.prefs, this.ctx, soundPack, "serverlost", R.raw.serverlost));
            }
            if (((Boolean) this.prefs.get("rx_tx_audio_icon", true)).booleanValue()) {
                this.sounds.put(1, loadSound(this.prefs, this.ctx, soundPack, "on", R.raw.on));
                this.sounds.put(2, loadSound(this.prefs, this.ctx, soundPack, "off", R.raw.off));
            }
            if (((Boolean) this.prefs.get("private_message_audio_icon", true)).booleanValue()) {
                this.sounds.put(3, loadSound(this.prefs, this.ctx, soundPack, "user_message", R.raw.user_message));
            }
            if (((Boolean) this.prefs.get("channel_message_audio_icon", true)).booleanValue()) {
                this.sounds.put(4, loadSound(this.prefs, this.ctx, soundPack, "channel_message", R.raw.channel_message));
            }
            if (((Boolean) this.prefs.get("channel_message_sent_audio_icon", true)).booleanValue()) {
                this.sounds.put(20, loadSound(this.prefs, this.ctx, soundPack, "channel_message_sent", R.raw.channel_message_sent));
            }
            if (((Boolean) this.prefs.get("broadcast_message_audio_icon", true)).booleanValue()) {
                this.sounds.put(5, loadSound(this.prefs, this.ctx, soundPack, "broadcast_message", R.raw.broadcast_message));
            }
            if (((Boolean) this.prefs.get("files_updated_audio_icon", true)).booleanValue()) {
                this.sounds.put(7, loadSound(this.prefs, this.ctx, soundPack, "fileupdate", R.raw.fileupdate));
            }
            if (((Boolean) this.prefs.get("voiceact_audio_icon", true)).booleanValue()) {
                this.sounds.put(8, loadSound(this.prefs, this.ctx, soundPack, "voiceact_enable", R.raw.voiceact_enable));
                this.sounds.put(9, loadSound(this.prefs, this.ctx, soundPack, "voiceact_disable", R.raw.voiceact_disable));
            }
            if (((Boolean) this.prefs.get("voiceact_triggered_icon", true)).booleanValue()) {
                this.sounds.put(10, loadSound(this.prefs, this.ctx, soundPack, "voiceact_on", R.raw.voiceact_on));
                this.sounds.put(11, loadSound(this.prefs, this.ctx, soundPack, "voiceact_off", R.raw.voiceact_off));
            }
            if (((Boolean) this.prefs.get("intercept_audio_icon", true)).booleanValue()) {
                this.sounds.put(18, loadSound(this.prefs, this.ctx, soundPack, "intercept", R.raw.intercept));
                this.sounds.put(19, loadSound(this.prefs, this.ctx, soundPack, "interceptend", R.raw.interceptend));
            }
            if (((Boolean) this.prefs.get("transmitready_icon", true)).booleanValue()) {
                this.sounds.put(12, loadSound(this.prefs, this.ctx, soundPack, "txqueue_start", R.raw.txqueue_start));
                this.sounds.put(13, loadSound(this.prefs, this.ctx, soundPack, "txqueue_stop", R.raw.txqueue_stop));
            }
            if (((Boolean) this.prefs.get("userjoin_icon", true)).booleanValue()) {
                this.sounds.put(14, loadSound(this.prefs, this.ctx, soundPack, "user_join", R.raw.user_join));
            }
            if (((Boolean) this.prefs.get("userleft_icon", true)).booleanValue()) {
                this.sounds.put(15, loadSound(this.prefs, this.ctx, soundPack, "user_left", R.raw.user_left));
            }
            if (((Boolean) this.prefs.get("userloggedin_icon", true)).booleanValue()) {
                this.sounds.put(16, loadSound(this.prefs, this.ctx, soundPack, "logged_on", R.raw.logged_on));
            }
            if (((Boolean) this.prefs.get("userloggedoff_icon", true)).booleanValue()) {
                this.sounds.put(17, loadSound(this.prefs, this.ctx, soundPack, "logged_off", R.raw.logged_off));
            }
            if (((Boolean) this.prefs.get("typing_icon", true)).booleanValue()) {
                this.sounds.put(SOUND_TYPING, loadSound(this.prefs, this.ctx, soundPack, "typing", R.raw.typing));
            }
        }
        if (getTextMessagesAdapter() != null) {
            getTextMessagesAdapter().showLogMessages(((Boolean) this.prefs.get("show_log_messages", true)).booleanValue());
        }
        getWindow().getDecorView().setKeepScreenOn(((Boolean) this.prefs.get("keep_screen_on_checkbox", false)).booleanValue());
        createStatusTimer();
        if (this.ttsWrapper != null) {
            this.ttsWrapper.useAnnouncements = (Boolean) this.prefs.get("pref_use_announcements", false);
            this.ttsWrapper.setAccessibilityStream(((Boolean) this.prefs.get("pref_a11y_volume", false)).booleanValue());
            this.ttsWrapper.switchEngine((String) this.prefs.get("pref_speech_engine", TTSWrapper.defaultEngineName));
        }
    }

    private int loadSound(PrefsHelper prefs, Context context, String soundPack, String key, int defaultResId) {
        if ("custom".equals(soundPack)) {
            String customPath = (String) prefs.get("custom_sound_" + key, "");
            if (!customPath.isEmpty()) {
                File file = new File(customPath);
                if (file.exists() && file.canRead()) {
                    int soundId = this.audioIcons.load(file.getAbsolutePath(), 1);
                    if (soundId != 0) {
                        return soundId;
                    }
                }
            }
        } else if (soundPack != null && !soundPack.isEmpty() && !"default".equals(soundPack)) {
            String[] candidates = SOUND_CANDIDATES.get(key);
            if (candidates != null) {
                for (String candidate : candidates) {
                    File cachedSound = getCachedAssetSound(context, soundPack, candidate);
                    if (cachedSound != null && cachedSound.exists() && cachedSound.length() > 0) {
                        int soundId = this.audioIcons.load(cachedSound.getAbsolutePath(), 1);
                        if (soundId != 0) {
                            return soundId;
                        }
                    }
                }
            }
        }
        return this.audioIcons.load(context, defaultResId, 1);
    }

    private File getCachedAssetSound(Context context, String soundPack, String filename) {
        File soundDir = new File(context.getCacheDir(), "sounds/" + soundPack);
        if (!soundDir.exists()) {
            soundDir.mkdirs();
        }
        File soundFile = new File(soundDir, filename);
        if (soundFile.exists() && soundFile.length() > 0) {
            return soundFile;
        }
        try (java.io.InputStream is = context.getAssets().open("sounds/" + soundPack + "/" + filename);
             java.io.OutputStream os = new java.io.FileOutputStream(soundFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
            return soundFile;
        } catch (java.io.IOException e) {
            if (soundFile.exists()) {
                soundFile.delete();
            }
            return null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.micActivityHandler.removeCallbacks(this.micActivityRunnable);
        if (this.stats_timer != null) {
            this.stats_timer.cancel();
            this.stats_timer = null;
        }
    }

    @Override
    public void onBackPressed() {
        int currentPage = this.mViewPager != null ? this.mViewPager.getCurrentItem() : 0;
        if (currentPage != 0) {
            this.mViewPager.setCurrentItem(0);
            return;
        }
        if (this.curchannel != null && this.curchannel.nParentID > 0) {
            Channel parentChannel = (getService() != null && getService().getChannels() != null)
                    ? getService().getChannels().get(Integer.valueOf(this.curchannel.nParentID))
                    : null;
            setCurrentChannel(parentChannel);
            if (this.channelsAdapter != null) {
                this.channelsAdapter.notifyDataSetChanged();
            }
            return;
        }
        if (this.filesAdapter != null && this.filesAdapter.getActiveTransfersCount() > 0) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setMessage(R.string.disconnect_alert);
            alert.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                this.filesAdapter.cancelAllTransfers();
                finish();
            });
            alert.setNegativeButton(android.R.string.cancel, null);
            alert.show();
            return;
        }
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing()) {
            if (this.audioIcons != null) {
                this.audioIcons.release();
                this.audioIcons = null;
            }
            if (this.ttsWrapper != null) {
                this.ttsWrapper.shutdown();
                this.ttsWrapper = null;
            }
            if (this.audioManager != null) {
                this.audioManager.setMode(0);
            }
            if (this.mConnection != null && this.mConnection.isBound()) {
                Log.d("bearware", "Unbinding TeamTalk service");
                TeamTalkService service = getService();
                if (service != null) {
                    service.disablePhoneCallReaction();
                    service.unwatchBluetoothHeadset();
                    service.resetState();
                    onServiceDisconnected(service);
                }
                try {
                    unbindService(this.mConnection);
                } catch (Exception ignored) {
                }
                this.mConnection.setBound(false);
            }
            if (this.notificationManager != null) {
                this.notificationManager.cancelAll();
            }
            if (this.mViewPager != null && this.mSectionsPagerAdapter != null) {
                this.mViewPager.removeOnPageChangeListener(this.mSectionsPagerAdapter);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (this.isProximitySensorRegistered && this.mSensorManager != null) {
            this.mSensorManager.unregisterListener(this);
            this.isProximitySensorRegistered = false;
        }
        if (this.mConnection != null && this.mConnection.isBound()) {
            Log.d("bearware", "Unbinding TeamTalk service");
            TeamTalkService service = getService();
            if (service != null) {
                if (isFinishing()) {
                    service.disablePhoneCallReaction();
                    service.unwatchBluetoothHeadset();
                    service.resetState();
                }
                onServiceDisconnected(service);
            }
            try {
                unbindService(this.mConnection);
            } catch (Exception ignored) {
            }
            this.mConnection.setBound(false);
        }
        Log.d("bearware", "Activity destroyed " + hashCode());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 4 && resultCode == -1) {
            Uri uri = data.getData();
            String path = AbsolutePathHelper.getRealPath(getBaseContext(), uri);
            if (path != null) {
                File localFile = new File(path);
                if (localFile.canRead()) {
                    startFileUpload(path);
                    return;
                } else {
                    Toast.makeText(this, getString(R.string.upload_failed, new Object[]{path}), 1).show();
                    return;
                }
            }
            new FileCopyingTask().execute(uri);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

        public boolean startFileUpload(String path) {
        String remoteName = this.filesAdapter.getRemoteName(path);
        if (remoteName != null) {
            Toast.makeText(this, getString(R.string.remote_file_exists, new Object[]{remoteName}), 1).show();
        } else {
            int targetChanId = this.curchannel != null ? this.curchannel.nChannelID : (getClient() != null ? getClient().getMyChannelID() : 0);
            if (getClient() != null && getClient().doSendFile(targetChanId, path) > 0) {
                Toast.makeText(this, R.string.upload_started, 0).show();
                return true;
            }
            Toast.makeText(this, getString(R.string.upload_failed, new Object[]{path}), 1).show();
        }
        return false;
    }

        private class FileCopyingTask extends AsyncTask<Uri, Void, String> {
        private FileCopyingTask() {
        }

                @Override
        public String doInBackground(Uri... uris) {
            Uri uri = uris[0];
            Cursor cursor = MainActivity.this.getContentResolver().query(uri, null, null, null, null);
            int columnIndex = (cursor == null || !cursor.moveToFirst()) ? -1 : cursor.getColumnIndex("_display_name");
            if (columnIndex >= 0) {
                File transitFile = new File(MainActivity.this.getCacheDir(), cursor.getString(columnIndex));
                cursor.close();
                try {
                    if ((transitFile.exists() && !transitFile.delete()) || !transitFile.createNewFile()) {
                        return null;
                    }
                    transitFile.deleteOnExit();
                    try {
                        InputStream src = MainActivity.this.getContentResolver().openInputStream(uri);
                        try {
                            FileOutputStream dest = new FileOutputStream(transitFile);
                            try {
                                byte[] buffer = new byte[1024];
                                while (true) {
                                    int read = src.read(buffer);
                                    if (read <= 0) {
                                        break;
                                    }
                                    dest.write(buffer, 0, read);
                                }
                                dest.close();
                                if (src != null) {
                                    src.close();
                                }
                                return transitFile.getPath();
                            } finally {
                            }
                        } finally {
                        }
                    } catch (Exception e) {
                        return null;
                    }
                } catch (Exception e2) {
                    return null;
                }
            } else {
                if (cursor != null) {
                    cursor.close();
                }
                return null;
            }
        }

                @Override
        public void onPostExecute(String path) {
            if (path != null && !MainActivity.this.startFileUpload(path)) {
                File transitFile = new File(path);
                transitFile.delete();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean proximitySensor = this.prefs != null && ((Boolean) this.prefs.get("proximity_sensor_checkbox", false)).booleanValue();
        TeamTalkService service = getService();
        if (proximitySensor && this.mConnection != null && this.mConnection.isBound() && service != null && !service.isInPhoneCall()) {
            float f = event.values[0];
            PowerManager.WakeLock wakeLock = this.proximityWakeLock;
            if (f == 0.0f) {
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                }
                if (this.audioManager != null) {
                    this.audioManager.setMode(3);
                    this.audioManager.setSpeakerphoneOn(false);
                }
                service.enableVoiceTransmission(true);
                return;
            }
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            adjustSoundSystem();
            if (service.isVoiceTransmissionEnabled()) {
                service.enableVoiceTransmission(false);
            }
        }
    }

        public class SectionsPagerAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener {
        public static final int CHANNELS_PAGE = 0;
        public static final int CHAT_PAGE = 1;
        public static final int FILES_PAGE = 3;
        public static final int MEDIA_PAGE = 2;
        public static final int PAGE_COUNT = 4;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm, 1);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            switch (position) {
                case 0:
                    MainActivity.this.channelsFragment = (ChannelsSectionFragment) fragment;
                    break;
                case 1:
                    MainActivity.this.chatFragment = (ChatSectionFragment) fragment;
                    break;
                case 2:
                    MainActivity.this.mediaFragment = (MediaSectionFragment) fragment;
                    break;
                case 3:
                    MainActivity.this.filesFragment = (FilesSectionFragment) fragment;
                    break;
            }
            return fragment;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 1:
                    MainActivity.this.chatFragment = new ChatSectionFragment();
                    return MainActivity.this.chatFragment;
                case 2:
                    MainActivity.this.mediaFragment = new MediaSectionFragment();
                    return MainActivity.this.mediaFragment;
                case 3:
                    MainActivity.this.filesFragment = new FilesSectionFragment();
                    return MainActivity.this.filesFragment;
                default:
                    MainActivity.this.channelsFragment = new ChannelsSectionFragment();
                    return MainActivity.this.channelsFragment;
            }
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return MainActivity.this.getString(R.string.title_section_channels).toUpperCase(l);
                case 1:
                    return MainActivity.this.getString(R.string.title_section_chat).toUpperCase(l);
                case 2:
                    return MainActivity.this.getString(R.string.title_section_media).toUpperCase(l);
                case 3:
                    return MainActivity.this.getString(R.string.title_section_files).toUpperCase(l);
                default:
                    return null;
            }
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            InputMethodManager imm = (InputMethodManager) MainActivity.this.getSystemService("input_method");
            View v = MainActivity.this.getCurrentFocus();
            if (v != null) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            MainActivity.this.accessibilityAssistant.setVisiblePage(position);
        }

        @Override
        public void onPageScrollStateChanged(int state) {
        }
    }

    private void fileSelectionStart() {
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        Intent i = Intent.createChooser(intent, "File");
        startActivityForResult(i, 4);
    }

    private boolean requestMediaPermissions() {
        Permissions.READ_MEDIA_IMAGES.request(this, true);
        Permissions.READ_MEDIA_VIDEO.request(this, true);
        Permissions.READ_MEDIA_AUDIO.request(this, true);
        return areMediaPermissionsComplete();
    }

    private boolean areMediaPermissionsComplete() {
        return (Permissions.READ_MEDIA_IMAGES.isPending() || Permissions.READ_MEDIA_VIDEO.isPending() || Permissions.READ_MEDIA_AUDIO.isPending()) ? false : true;
    }

    private void editChannelProperties(Channel channel) {
        Intent intent = new Intent(this, (Class<?>) ChannelPropActivity.class);
        startActivityForResult(intent.putExtra(ChannelPropActivity.EXTRA_CHANNELID, channel.nChannelID), 1);
    }

    private void leaveChannel() {
        if (getClient() != null) {
            getClient().doLeaveChannel();
        }
        if (this.accessibilityAssistant != null) {
            this.accessibilityAssistant.lockEvents();
        }
        if (this.channelsAdapter != null) {
            this.channelsAdapter.notifyDataSetChanged();
        }
        if (this.accessibilityAssistant != null) {
            this.accessibilityAssistant.unlockEvents();
        }
    }

    private void joinChannelUnsafe(Channel channel, String passwd) {
        if (getClient() == null || getService() == null) {
            return;
        }
        int cmdid = getClient().doJoinChannelByID(channel.nChannelID, passwd);
        if (cmdid > 0) {
            this.activecmds.put(cmdid, CmdComplete.CMD_COMPLETE_JOIN);
            channel.szPassword = passwd;
            getService().setJoinChannel(channel);
            return;
        }
        Toast.makeText(this, R.string.text_con_cmderr, Toast.LENGTH_SHORT).show();
    }

    private void joinChannel(final Channel channel, final String passwd) {
        if (this.filesAdapter.getActiveTransfersCount() > 0) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setMessage(R.string.channel_change_alert);
            alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$joinChannel$3(channel, passwd, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.cancel, (DialogInterface.OnClickListener) null);
            alert.show();
            return;
        }
        joinChannelUnsafe(channel, passwd);
    }

        public void lambda$joinChannel$3(Channel channel, String passwd, DialogInterface dialog, int whichButton) {
        this.filesAdapter.cancelAllTransfers();
        joinChannelUnsafe(channel, passwd);
    }

        public void joinChannel(final Channel channel) {
        if (channel.bPassword) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.pref_title_join_channel);
            alert.setMessage(R.string.channel_password_prompt);
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setText(channel.szPassword);
            input.requestFocus();
            alert.setView(input);
            alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$joinChannel$4(input, channel, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$joinChannel$5(input, dialogInterface, i);
                }
            });
            AlertDialog dialog = alert.create();
            dialog.getWindow().setSoftInputMode(5);
            dialog.show();
            return;
        }
        joinChannel(channel, "");
    }

        public void lambda$joinChannel$4(EditText input, Channel channel, DialogInterface dialog, int whichButton) {
        InputMethodManager im = (InputMethodManager) getSystemService("input_method");
        im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        joinChannel(channel, input.getText().toString());
    }

    public void lambda$joinChannel$5(EditText input, DialogInterface dialog, int whichButton) {
        InputMethodManager im = (InputMethodManager) getSystemService("input_method");
        im.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void subscriptionChange(User user) {
        User olduser = this.users.get(Integer.valueOf(user.nUserID));
        if (olduser != null && this.ttsWrapper != null) {
            Utils.ttsSubscriptionChanged(getBaseContext(), olduser, user).ifPresent(this.ttsWrapper::speak);
        }
        if (olduser != null && this.sounds.get(18) != 0 && this.sounds.get(19) != 0) {
            Utils.subscriptionChanged(olduser, user, 65536).ifPresent(this::lambda$subscriptionChange$7);
            Utils.subscriptionChanged(olduser, user, 131072).ifPresent(this::lambda$subscriptionChange$8);
            Utils.subscriptionChanged(olduser, user, 1048576).ifPresent(this::lambda$subscriptionChange$9);
            Utils.subscriptionChanged(olduser, user, 2097152).ifPresent(this::lambda$subscriptionChange$10);
            Utils.subscriptionChanged(olduser, user, 4194304).ifPresent(this::lambda$subscriptionChange$11);
            Utils.subscriptionChanged(olduser, user, 16777216).ifPresent(this::lambda$subscriptionChange$12);
        }
    }

        public void lambda$subscriptionChange$6(String text) {
        this.ttsWrapper.speak(text);
    }

        public void lambda$subscriptionChange$7(Boolean isOn) {
        this.audioIcons.play(this.sounds.get(isOn.booleanValue() ? 18 : 19), 1.0f, 1.0f, 0, 0, 1.0f);
    }

        public void lambda$subscriptionChange$8(Boolean isOn) {
        this.audioIcons.play(this.sounds.get(isOn.booleanValue() ? 18 : 19), 1.0f, 1.0f, 0, 0, 1.0f);
    }

        public void lambda$subscriptionChange$9(Boolean isOn) {
        this.audioIcons.play(this.sounds.get(isOn.booleanValue() ? 18 : 19), 1.0f, 1.0f, 0, 0, 1.0f);
    }

        public void lambda$subscriptionChange$10(Boolean isOn) {
        this.audioIcons.play(this.sounds.get(isOn.booleanValue() ? 18 : 19), 1.0f, 1.0f, 0, 0, 1.0f);
    }

        public void lambda$subscriptionChange$11(Boolean isOn) {
        this.audioIcons.play(this.sounds.get(isOn.booleanValue() ? 18 : 19), 1.0f, 1.0f, 0, 0, 1.0f);
    }

        public void lambda$subscriptionChange$12(Boolean isOn) {
        this.audioIcons.play(this.sounds.get(isOn.booleanValue() ? 18 : 19), 1.0f, 1.0f, 0, 0, 1.0f);
    }

    private boolean isVisibleChannel(int chanid) {
        if (getClient() == null || getService() == null || getService().getChannels() == null) {
            return false;
        }
        if (this.curchannel == null) {
            return chanid == getClient().getRootChannelID();
        }
        if (this.curchannel.nParentID == chanid) {
            return true;
        }
        Channel channel = getService().getChannels().get(Integer.valueOf(chanid));
        return channel != null && this.curchannel.nChannelID == channel.nParentID;
    }

        public static class ChannelsSectionFragment extends Fragment {
        MainActivity mainActivity;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            if (context instanceof MainActivity) {
                this.mainActivity = (MainActivity) context;
            }
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            if (activity instanceof MainActivity) {
                this.mainActivity = (MainActivity) activity;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main_channels, container, false);
            if (this.mainActivity == null && getActivity() instanceof MainActivity) {
                this.mainActivity = (MainActivity) getActivity();
            }
            if (this.mainActivity != null) {
                if (this.mainActivity.accessibilityAssistant != null) {
                    this.mainActivity.accessibilityAssistant.registerPage(rootView, 0);
                }
                ListView channelsList = (ListView) rootView.findViewById(R.id.listChannels);
                if (channelsList != null && this.mainActivity.getChannelsAdapter() != null) {
                    channelsList.setAdapter((ListAdapter) this.mainActivity.getChannelsAdapter());
                    channelsList.setOnItemClickListener(this.mainActivity);
                    channelsList.setOnItemLongClickListener(this.mainActivity);
                }
            }
            return rootView;
        }
    }

        public static class ChatSectionFragment extends Fragment {
        MainActivity mainActivity;
        private EditText newmsg;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            if (context instanceof MainActivity) {
                this.mainActivity = (MainActivity) context;
            }
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            if (activity instanceof MainActivity) {
                this.mainActivity = (MainActivity) activity;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main_chat, container, false);
            if (this.mainActivity == null && getActivity() instanceof MainActivity) {
                this.mainActivity = (MainActivity) getActivity();
            }
            if (this.mainActivity != null && this.mainActivity.accessibilityAssistant != null) {
                this.mainActivity.accessibilityAssistant.registerPage(rootView, 1);
            }
            this.newmsg = (EditText) rootView.findViewById(R.id.channel_im_edittext);
            if (this.newmsg != null) {
                this.newmsg.setOnEditorActionListener(new TextView.OnEditorActionListener() { 
                    @Override
                    public final boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                        return MainActivity.ChatSectionFragment.this.lambda$onCreateView$0(textView, i, keyEvent);
                    }
                });
            }
            ListView chatlog = (ListView) rootView.findViewById(R.id.channel_im_listview);
            if (chatlog != null && this.mainActivity != null && this.mainActivity.getTextMessagesAdapter() != null) {
                chatlog.setTranscriptMode(2);
                chatlog.setAdapter((ListAdapter) this.mainActivity.getTextMessagesAdapter());
            }
            ImageButton historyBtn = (ImageButton) rootView.findViewById(R.id.channel_im_historybtn);
            if (historyBtn != null) {
                historyBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (getActivity() != null) {
                            Intent intent = new Intent(getActivity(), ChatHistoryActivity.class);
                            startActivity(intent);
                        }
                    }
                });
            }
            Button sendBtn = (Button) rootView.findViewById(R.id.channel_im_sendbtn);
            if (sendBtn != null) {
                sendBtn.setOnClickListener(new View.OnClickListener() { 
                    @Override
                    public final void onClick(View view) {
                        MainActivity.ChatSectionFragment.this.lambda$onCreateView$1(view);
                    }
                });
            }
            return rootView;
        }

        public boolean lambda$onCreateView$0(TextView v, int actionId, KeyEvent event) {
            if (actionId == 4 || actionId == 0) {
                sendMsgToChannel();
                return true;
            }
            return false;
        }

        public void lambda$onCreateView$1(View arg0) {
            sendMsgToChannel();
        }

        private void sendMsgToChannel() {
            if (this.newmsg == null || this.mainActivity == null || this.mainActivity.getClient() == null) {
                return;
            }
            String text = this.newmsg.getText().toString();
            if (text.isEmpty()) {
                return;
            }
            MyTextMessage textmsg = new MyTextMessage();
            textmsg.nMsgType = 2;
            textmsg.nChannelID = this.mainActivity.getClient().getMyChannelID();
            textmsg.szMessage = text;
            int cmdid = 0;
            Iterator<MyTextMessage> it = textmsg.split().iterator();
            while (it.hasNext()) {
                MyTextMessage m = it.next();
                cmdid = this.mainActivity.getClient().doTextMessage(m);
            }
            MainActivity mainActivity = this.mainActivity;
            if (cmdid > 0) {
                mainActivity.activecmds.put(cmdid, CmdComplete.CMD_COMPLETE_TEXTMSG);
                this.newmsg.setText("");
            } else {
                Toast.makeText(mainActivity, getResources().getString(R.string.text_con_cmderr), Toast.LENGTH_SHORT).show();
            }
        }
    }

        public static class VidcapSectionFragment extends Fragment {
        MainActivity mainActivity;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            if (context instanceof MainActivity) {
                this.mainActivity = (MainActivity) context;
            }
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            if (activity instanceof MainActivity) {
                this.mainActivity = (MainActivity) activity;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_main_vidcap, container, false);
        }
    }

        public static class MediaSectionFragment extends Fragment {
        MainActivity mainActivity;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            if (context instanceof MainActivity) {
                this.mainActivity = (MainActivity) context;
            }
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            if (activity instanceof MainActivity) {
                this.mainActivity = (MainActivity) activity;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main_media, container, false);
            if (this.mainActivity == null && getActivity() instanceof MainActivity) {
                this.mainActivity = (MainActivity) getActivity();
            }
            if (this.mainActivity != null) {
                if (this.mainActivity.accessibilityAssistant != null) {
                    this.mainActivity.accessibilityAssistant.registerPage(rootView, 2);
                }
                ExpandableListView mediaview = (ExpandableListView) rootView.findViewById(R.id.media_elist_view);
                if (mediaview != null && this.mainActivity.getMediaAdapter() != null) {
                    mediaview.setAdapter(this.mainActivity.getMediaAdapter());
                }
            }
            return rootView;
        }
    }

        public static class FilesSectionFragment extends ListFragment {
        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                if (mainActivity.accessibilityAssistant != null) {
                    mainActivity.accessibilityAssistant.registerPage(view, 3);
                }
                if (mainActivity.getFilesAdapter() != null) {
                    setListAdapter(mainActivity.getFilesAdapter());
                }
            }
        }
    }

    private void setCurrentChannel(Channel channel) {
        this.curchannel = channel;
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            if (channel != null) {
                String name = (channel.szName != null && !channel.szName.isEmpty()) ? channel.szName : "/";
                ab.setSubtitle(name);
            } else {
                ServerProperties srvprop = new ServerProperties();
                if (getClient() != null) {
                    getClient().getServerProperties(srvprop);
                    String srvName = (srvprop.szServerName != null && !srvprop.szServerName.isEmpty()) ? srvprop.szServerName : "/";
                    ab.setSubtitle(srvName);
                } else {
                    ab.setSubtitle("/");
                }
            }
        }
        invalidateOptionsMenu();
    }

    private void setMyChannel(Channel channel) {
        this.mychannel = channel;
        adjustVoiceGain();
        invalidateOptionsMenu();
    }

    public class ChannelListAdapter extends BaseAdapter {
        private static final int CHANNEL_VIEW_TYPE = 1;
        private static final int INFO_VIEW_TYPE = 3;
        private static final int PARENT_CHANNEL_VIEW_TYPE = 0;
        private static final int USER_VIEW_TYPE = 2;
        private static final int VIEW_TYPE_COUNT = 4;
        private final LayoutInflater inflater;
        Vector<Channel> subchannels = new Vector<>();
        Vector<Channel> stickychannels = new Vector<>();
        Vector<User> currentusers = new Vector<>();
        private boolean pendingUpdate = false;

        ChannelListAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        public void doNotifyDataSetChanged() {
            this.pendingUpdate = false;
            if (MainActivity.this.getService() == null || MainActivity.this.getClient() == null || MainActivity.this.getService().getChannels() == null || MainActivity.this.getService().getUsers() == null) {
                if (MainActivity.this.accessibilityAssistant != null) {
                    MainActivity.this.accessibilityAssistant.lockEvents();
                }
                this.subchannels.clear();
                this.stickychannels.clear();
                this.currentusers.clear();
                super.notifyDataSetChanged();
                if (MainActivity.this.accessibilityAssistant != null) {
                    MainActivity.this.accessibilityAssistant.unlockEvents();
                }
                return;
            }
            if (MainActivity.this.accessibilityAssistant != null) {
                MainActivity.this.accessibilityAssistant.lockEvents();
            }
            this.subchannels.clear();
            this.stickychannels.clear();
            this.currentusers.clear();
            Channel channel = MainActivity.this.curchannel;
            if (channel != null) {
                int chanid = channel.nChannelID;
                this.subchannels = Utils.getSubChannels(chanid, MainActivity.this.getService().getChannels());
                this.stickychannels = Utils.getStickyChannels(chanid, MainActivity.this.getService().getChannels());
                if (chanid == MainActivity.this.getClient().getRootChannelID() && MainActivity.this.prefs != null && !((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_SHOW_ROOT_USERS, true)).booleanValue()) {
                    this.currentusers = new Vector<>();
                } else {
                    this.currentusers = Utils.getUsers(chanid, MainActivity.this.getService().getUsers());
                }
            } else {
                int rootChanId = MainActivity.this.getClient().getRootChannelID();
                boolean showRootUsers = MainActivity.this.prefs != null ? ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_SHOW_ROOT_USERS, true)).booleanValue() : true;
                if (showRootUsers) {
                    this.currentusers = Utils.getUsers(0, MainActivity.this.getService().getUsers());
                } else {
                    this.currentusers = new Vector<>();
                }
                Channel root = MainActivity.this.getService().getChannels().get(Integer.valueOf(rootChanId));
                if (root != null) {
                    this.subchannels.add(root);
                }
            }
            Collections.sort(this.subchannels, new Comparator() { 
                @Override
                public final int compare(Object obj, Object obj2) {
                    String name1 = ((Channel) obj).szName != null ? ((Channel) obj).szName : "";
                    String name2 = ((Channel) obj2).szName != null ? ((Channel) obj2).szName : "";
                    return name1.compareToIgnoreCase(name2);
                }
            });
            Collections.sort(this.stickychannels, new Comparator() { 
                @Override
                public final int compare(Object obj, Object obj2) {
                    String name1 = ((Channel) obj).szName != null ? ((Channel) obj).szName : "";
                    String name2 = ((Channel) obj2).szName != null ? ((Channel) obj2).szName : "";
                    return name1.compareToIgnoreCase(name2);
                }
            });
            final Map<Integer, String> nameCache = new HashMap<>();
            Iterator<User> it = this.currentusers.iterator();
            while (it.hasNext()) {
                User u = it.next();
                nameCache.put(Integer.valueOf(u.nUserID), Utils.getDisplayName(MainActivity.this.getBaseContext(), u));
            }
            Collections.sort(this.currentusers, new Comparator() { 
                @Override
                public final int compare(Object obj, Object obj2) {
                    int lambda$doNotifyDataSetChanged$2;
                    lambda$doNotifyDataSetChanged$2 = MainActivity.ChannelListAdapter.this.lambda$doNotifyDataSetChanged$2(nameCache, (User) obj, (User) obj2);
                    return lambda$doNotifyDataSetChanged$2;
                }
            });
            super.notifyDataSetChanged();
            MainActivity.this.accessibilityAssistant.unlockEvents();
        }

        public int lambda$doNotifyDataSetChanged$2(Map nameCache, User u1, User u2) {
            if (((Boolean) MainActivity.this.prefs.get("movetalk_checkbox", true)).booleanValue()) {
                if ((u1.uUserState & 1) != 0 && (u2.uUserState & 1) == 0) {
                    return -1;
                }
                if ((u1.uUserState & 1) == 0 && (u2.uUserState & 1) != 0) {
                    return 1;
                }
            }
            String name1 = (String) nameCache.get(Integer.valueOf(u1.nUserID));
            String name2 = (String) nameCache.get(Integer.valueOf(u2.nUserID));
            if (name1 == null) name1 = "";
            if (name2 == null) name2 = "";
            return name1.compareToIgnoreCase(name2);
        }

        @Override
        public void notifyDataSetChanged() {
            if (!this.pendingUpdate) {
                this.pendingUpdate = true;
                MainActivity.this.handler.post(new Runnable() { 
                    @Override
                    public final void run() {
                        MainActivity.ChannelListAdapter.this.doNotifyDataSetChanged();
                    }
                });
            }
        }

        private boolean shouldShowBackItem() {
            if (MainActivity.this.curchannel == null) {
                return false;
            }
            if (MainActivity.this.curchannel.nParentID > 0) {
                return true;
            }
            return MainActivity.this.prefs != null && ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_SHOW_ROOT_SERVER_BACK_BTN, false)).booleanValue();
        }

        @Override
        public int getCount() {
            int count = this.currentusers.size() + this.subchannels.size() + this.stickychannels.size();
            if (shouldShowBackItem()) {
                return count + 1;
            }
            return count;
        }

        @Override
        public Object getItem(int position) {
            if (MainActivity.this.curchannel == null) {
                if (position < this.currentusers.size()) {
                    return this.currentusers.get(position);
                }
                int p = position - this.currentusers.size();
                if (p < this.subchannels.size()) {
                    return this.subchannels.get(p);
                }
                p -= this.subchannels.size();
                return this.stickychannels.get(p);
            }

            if (position < this.stickychannels.size()) {
                return this.stickychannels.get(position);
            }
            int position2 = position - this.stickychannels.size();
            if (position2 < this.currentusers.size()) {
                return this.currentusers.get(position2);
            }
            int position3 = position2 - this.currentusers.size();
            if (shouldShowBackItem()) {
                if (position3 == 0) {
                    if (MainActivity.this.curchannel.nParentID > 0 && MainActivity.this.getService() != null && MainActivity.this.getService().getChannels() != null) {
                        Channel parent = MainActivity.this.getService().getChannels().get(Integer.valueOf(MainActivity.this.curchannel.nParentID));
                        if (parent != null) {
                            return parent;
                        }
                    }
                    Channel rootParent = new Channel();
                    rootParent.szName = "";
                    rootParent.szTopic = "";
                    return rootParent;
                }
                position3--;
            }
            return this.subchannels.get(position3);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            if (MainActivity.this.curchannel == null) {
                if (position < this.currentusers.size()) {
                    return 2;
                }
                int p = position - this.currentusers.size();
                if (p < this.subchannels.size()) {
                    return 1;
                }
                return 3;
            }

            if (position < this.stickychannels.size()) {
                return 3;
            }
            int position2 = position - this.stickychannels.size();
            if (position2 < this.currentusers.size()) {
                return 2;
            }
            int position3 = position2 - this.currentusers.size();
            if (shouldShowBackItem()) {
                if (position3 == 0) {
                    return 0;
                }
                position3--;
            }
            return 1;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            boolean neutral;
            boolean isOperator;
            String str;
            String speaking;
            int icon_resource;
            View convertView2;
            String finalChanName;
            int population;
            String str2;
            View convertView3 = convertView;
            Object item = getItem(position);
            if (item instanceof Channel) {
                final Channel channel = (Channel) item;
                switch (getItemViewType(position)) {
                    case 0:
                        if (convertView3 == null || convertView3.findViewById(R.id.parentname) == null) {
                            convertView3 = this.inflater.inflate(R.layout.item_channel_back, parent, false);
                        }
                        TextView pName = (TextView) convertView3.findViewById(R.id.parentname);
                        TextView pTopic = (TextView) convertView3.findViewById(R.id.chantopic);
                        String parentNameStr;
                        if (channel.nChannelID == 0 || (MainActivity.this.curchannel != null && MainActivity.this.curchannel.nParentID == 0)) {
                            parentNameStr = MainActivity.this.getString(R.string.root_server);
                        } else {
                            parentNameStr = (channel.szName != null && !channel.szName.isEmpty()) ? channel.szName : MainActivity.this.getString(R.string.root_channel);
                        }
                        pName.setText(MainActivity.this.getString(R.string.back_to_channel, new Object[]{parentNameStr}));
                        if (pTopic != null) {
                            pTopic.setText(channel.szTopic != null ? channel.szTopic : "");
                        }
                        convertView3.setContentDescription(MainActivity.this.getString(R.string.back_to_channel, new Object[]{parentNameStr}) + (channel.szTopic != null && !channel.szTopic.isEmpty() ? ", " + channel.szTopic : ""));
                        break;
                    case 1:
                        if (convertView3 == null || convertView3.findViewById(R.id.channelname) == null) {
                            convertView3 = this.inflater.inflate(R.layout.item_channel, parent, false);
                        }
                        ImageView chanicon = (ImageView) convertView3.findViewById(R.id.channelicon);
                        TextView name = (TextView) convertView3.findViewById(R.id.channelname);
                        TextView topic = (TextView) convertView3.findViewById(R.id.chantopic);
                        Button join = (Button) convertView3.findViewById(R.id.join_btn);
                        int icon_resource2 = R.drawable.channel_orange;
                        if (channel.bPassword) {
                            icon_resource2 = R.drawable.channel_pink;
                            chanicon.setContentDescription(MainActivity.this.getString(R.string.text_passwdprot));
                            chanicon.setImportantForAccessibility(1);
                        } else {
                            chanicon.setContentDescription(null);
                            chanicon.setImportantForAccessibility(2);
                        }
                        chanicon.setImageResource(icon_resource2);
                        if (channel.nParentID == 0) {
                            ServerProperties srvprop = new ServerProperties();
                            MainActivity.this.getClient().getServerProperties(srvprop);
                            name.setText(srvprop.szServerName);
                            finalChanName = srvprop.szServerName;
                        } else {
                            name.setText(channel.szName);
                            finalChanName = channel.szName;
                        }
                        topic.setText(channel.szTopic);
                        View.OnClickListener listener = new View.OnClickListener() { 
                            @Override
                            public final void onClick(View view) {
                                MainActivity.ChannelListAdapter.this.lambda$getView$3(channel, view);
                            }
                        };
                        join.setOnClickListener(listener);
                        join.setAccessibilityDelegate(MainActivity.this.accessibilityAssistant);
                        join.setEnabled(channel.nChannelID != MainActivity.this.getClient().getMyChannelID());
                        int population2 = 0;
                        if (channel.nMaxUsers > 0) {
                            int population3 = Utils.getUsers(channel.nChannelID, MainActivity.this.getService().getUsers()).size();
                            TextView textView = (TextView) convertView3.findViewById(R.id.population);
                            if (population3 > 0) {
                                textView.setText(String.format(Locale.ROOT, "(%d)", Integer.valueOf(population3)));
                            } else {
                                textView.setText("");
                            }
                            population2 = population3;
                        }
                        StringBuilder descBuilder = new StringBuilder(finalChanName);
                        if (channel.bPassword) {
                            descBuilder.append(", ").append(MainActivity.this.getString(R.string.text_passwdprot));
                        }
                        if (population2 > 0) {
                            descBuilder.append(", ").append(MainActivity.this.getString(R.string.desc_users_count, new Object[]{Integer.valueOf(population2)}));
                        }
                        String finalChanName2 = channel.szTopic;
                        if (finalChanName2 != null && !channel.szTopic.isEmpty()) {
                            descBuilder.append(", ").append(MainActivity.this.getString(R.string.channel_prop_title_topic)).append(": ").append(channel.szTopic);
                        }
                        convertView3.setContentDescription(descBuilder.toString());
                        break;
                    case 3:
                        if (convertView3 == null || convertView3.findViewById(R.id.titletext) == null) {
                            convertView3 = this.inflater.inflate(R.layout.item_info, parent, false);
                        }
                        TextView title = (TextView) convertView3.findViewById(R.id.titletext);
                        TextView details = (TextView) convertView3.findViewById(R.id.infodetails);
                        title.setText(channel.szName);
                        details.setText(channel.szTopic);
                        break;
                }
            } else if (item instanceof User) {
                final User user = (User) item;
                if (convertView3 == null || convertView3.findViewById(R.id.nickname) == null) {
                    convertView3 = this.inflater.inflate(R.layout.item_user, parent, false);
                }
                ImageView usericon = (ImageView) convertView3.findViewById(R.id.usericon);
                TextView nickname = (TextView) convertView3.findViewById(R.id.nickname);
                TextView status = (TextView) convertView3.findViewById(R.id.status);
                String name2 = Utils.getDisplayName(MainActivity.this.getBaseContext(), user);
                nickname.setText(name2);
                status.setText(user.szStatusMsg != null ? user.szStatusMsg : "");
                boolean selected = MainActivity.this.userIDS.contains(Integer.valueOf(user.nUserID));
                boolean isOperator2 = MainActivity.this.getClient() != null && MainActivity.this.getClient().isChannelOperator(user.nUserID, user.nChannelID);
                boolean talking = (user.uUserState & 1) != 0;
                boolean female = (user.nStatusMode & 256) != 0;
                boolean neutral2 = (user.nStatusMode & 4096) != 0;
                boolean isAway = (user.nStatusMode & 1) != 0;
                boolean isAdmin = (user.uUserType & UserType.USERTYPE_ADMIN) != 0;
                if (MainActivity.this.getService() != null && MainActivity.this.getClient() != null && user.nUserID == MainActivity.this.getClient().getMyUserID()) {
                    talking = MainActivity.this.getService().isVoiceTransmitting();
                }
                String move = selected ? MainActivity.this.getString(R.string.user_state_selected) : "";
                speaking = talking ? MainActivity.this.getString(R.string.user_state_now_speaking, new Object[]{name2}) : name2;
                String gender = female ? MainActivity.this.getString(R.string.user_state_female) : neutral2 ? MainActivity.this.getString(R.string.user_state_neutral) : MainActivity.this.getString(R.string.user_state_male);
                String op = isOperator2 ? MainActivity.this.getString(R.string.user_state_operator) : "";
                String admin = isAdmin ? MainActivity.this.getString(R.string.user_state_admin) : "";
                String away = isAway ? MainActivity.this.getString(R.string.user_state_away) : "";
                nickname.setContentDescription(move + " " + speaking + " " + gender + " " + op + " " + admin);
                if (talking) {
                    if (female) {
                        icon_resource = R.drawable.woman_green;
                    } else {
                        icon_resource = R.drawable.man_green;
                    }
                } else if (female) {
                    icon_resource = isAway ? R.drawable.woman_orange : R.drawable.woman_blue;
                } else {
                    icon_resource = isAway ? R.drawable.man_orange : R.drawable.man_blue;
                }
                status.setContentDescription(away + " " + (user.szStatusMsg != null ? user.szStatusMsg : ""));
                usericon.setImageResource(icon_resource);
                usericon.setImportantForAccessibility(2);
                Button sndmsg = (Button) convertView3.findViewById(R.id.msg_btn);
                View.OnClickListener listener2 = new View.OnClickListener() { 
                    @Override
                    public final void onClick(View view) {
                        MainActivity.ChannelListAdapter.this.lambda$getView$4(user, view);
                    }
                };
                sndmsg.setOnClickListener(listener2);
                sndmsg.setAccessibilityDelegate(MainActivity.this.accessibilityAssistant);
                View quickActionsContainer = convertView3.findViewById(R.id.quick_actions_container);
                if (quickActionsContainer == null) {
                    convertView2 = convertView3;
                } else {
                    boolean showQuickActions = ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_SHOW_USER_QUICK_ACTIONS, true)).booleanValue();
                    boolean isMe = MainActivity.this.getClient() != null && user.nUserID == MainActivity.this.getClient().getMyUserID();
                    if (!showQuickActions || isMe) {
                        convertView2 = convertView3;
                        quickActionsContainer.setVisibility(8);
                    } else {
                        UserAccount myuseraccount = new UserAccount();
                        MainActivity.this.getClient().getMyUserAccount(myuseraccount);
                        boolean banRight = (myuseraccount.uUserRights & 64) != 0;
                        boolean kickRight = (myuseraccount.uUserRights & 32) != 0;
                        int myuserid = MainActivity.this.getClient().getMyUserID();
                        boolean kickRight2 = kickRight;
                        TeamTalkBase client = MainActivity.this.getClient();
                        int icon_resource3 = user.nChannelID;
                        boolean operatorRight = client.isChannelOperator(myuserid, icon_resource3);
                        Button btnKickSrv = (Button) convertView3.findViewById(R.id.btn_quick_kick_srv);
                        int myuserid2 = R.id.btn_quick_ban_srv;
                        Button btnBanSrv = (Button) convertView3.findViewById(myuserid2);
                        Button btnKickChan = (Button) convertView3.findViewById(R.id.btn_quick_kick_chan);
                        Button btnBanChan = (Button) convertView3.findViewById(R.id.btn_quick_ban_chan);
                        boolean anyVisible = false;
                        if (btnKickSrv == null) {
                            convertView2 = convertView3;
                        } else {
                            convertView2 = convertView3;
                            boolean visible = ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_QUICK_KICK_SRV, true)).booleanValue() && kickRight2;
                            btnKickSrv.setVisibility(visible ? 0 : 8);
                            if (visible) {
                                anyVisible = true;
                                btnKickSrv.setOnClickListener(new View.OnClickListener() { 
                                    @Override
                                    public final void onClick(View view) {
                                        MainActivity.ChannelListAdapter.this.lambda$getView$6(user, view);
                                    }
                                });
                            }
                        }
                        if (btnBanSrv != null) {
                            boolean visible2 = ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_QUICK_BAN_SRV, true)).booleanValue() && banRight;
                            btnBanSrv.setVisibility(visible2 ? 0 : 8);
                            if (visible2) {
                                anyVisible = true;
                                btnBanSrv.setOnClickListener(new View.OnClickListener() { 
                                    @Override
                                    public final void onClick(View view) {
                                        MainActivity.ChannelListAdapter.this.lambda$getView$8(user, view);
                                    }
                                });
                            }
                        }
                        if (btnKickChan != null) {
                            boolean visible3 = ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_QUICK_KICK_CHAN, true)).booleanValue() && (kickRight2 || operatorRight);
                            btnKickChan.setVisibility(visible3 ? 0 : 8);
                            if (visible3) {
                                anyVisible = true;
                                btnKickChan.setOnClickListener(new View.OnClickListener() { 
                                    @Override
                                    public final void onClick(View view) {
                                        MainActivity.ChannelListAdapter.this.lambda$getView$10(user, view);
                                    }
                                });
                            }
                        }
                        if (btnBanChan != null) {
                            boolean visible4 = ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_QUICK_BAN_CHAN, true)).booleanValue() && (banRight || operatorRight);
                            btnBanChan.setVisibility(visible4 ? 0 : 8);
                            if (visible4) {
                                anyVisible = true;
                                btnBanChan.setOnClickListener(new View.OnClickListener() { 
                                    @Override
                                    public final void onClick(View view) {
                                        MainActivity.ChannelListAdapter.this.lambda$getView$12(user, view);
                                    }
                                });
                            }
                        }
                        quickActionsContainer.setVisibility(anyVisible ? 0 : 8);
                    }
                }
                convertView3 = convertView2;
            }
            convertView3.setAccessibilityDelegate(MainActivity.this.accessibilityAssistant);
            return convertView3;
        }

                public void lambda$getView$3(Channel channel, View v) {
            if (v.getId() == R.id.join_btn) {
                MainActivity.this.joinChannel(channel);
            }
        }

                public void lambda$getView$4(User user, View v) {
            if (v.getId() == R.id.msg_btn) {
                Intent intent = new Intent(MainActivity.this, (Class<?>) TextMessageActivity.class);
                MainActivity.this.startActivity(intent.putExtra("userid", user.nUserID));
            }
        }

                public void lambda$getView$6(final User user, View v) {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setMessage(MainActivity.this.getString(R.string.kick_confirmation, new Object[]{user.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.ChannelListAdapter.this.lambda$getView$5(user, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        }

                public void lambda$getView$5(User user, DialogInterface dialog, int whichButton) {
            MainActivity.this.getClient().doKickUser(user.nUserID, 0);
        }

                public void lambda$getView$8(final User user, View v) {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setMessage(MainActivity.this.getString(R.string.ban_confirmation, new Object[]{user.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.ChannelListAdapter.this.lambda$getView$7(user, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        }

                public void lambda$getView$7(User user, DialogInterface dialog, int whichButton) {
            MainActivity.this.getClient().doBanUser(user.nUserID, 0);
            MainActivity.this.getClient().doKickUser(user.nUserID, 0);
        }

                public void lambda$getView$10(final User user, View v) {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setMessage(MainActivity.this.getString(R.string.kick_confirmation, new Object[]{user.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.ChannelListAdapter.this.lambda$getView$9(user, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        }

                public void lambda$getView$9(User user, DialogInterface dialog, int whichButton) {
            MainActivity.this.getClient().doKickUser(user.nUserID, user.nChannelID);
        }

                public void lambda$getView$12(final User user, View v) {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setMessage(MainActivity.this.getString(R.string.ban_confirmation, new Object[]{user.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.ChannelListAdapter.this.lambda$getView$11(user, dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        }

                public void lambda$getView$11(User user, DialogInterface dialog, int whichButton) {
            MainActivity.this.getClient().doBanUser(user.nUserID, user.nChannelID);
            MainActivity.this.getClient().doKickUser(user.nUserID, user.nChannelID);
        }
    }

    void createStatusTimer() {
        TextView connection = (TextView) findViewById(R.id.connectionstat_textview);
        TextView ping = (TextView) findViewById(R.id.pingstat_textview);
        TextView total = (TextView) findViewById(R.id.totalstat_textview);
        int defcolor = connection.getTextColors().getDefaultColor();
        if (this.stats_timer == null) {
            this.stats_timer = new AnonymousClass3(10000L, 1000L, connection, ping, defcolor, total).start();
        }
    }

        /* renamed from: org.nekit.ttproplus.gui.MainActivity$3, reason: invalid class name */
        public class AnonymousClass3 extends CountDownTimer {
        ClientStatistics prev_stats;
        final TextView val$connection;
        final int val$defcolor;
        final TextView val$ping;
        final TextView val$total;

                AnonymousClass3(long arg0, long arg1, TextView textView, TextView textView2, int i, TextView textView3) {
            super(arg0, arg1);
            this.val$connection = textView;
            this.val$ping = textView2;
            this.val$defcolor = i;
            this.val$total = textView3;
        }

        @Override
        public void onTick(long millisUntilFinished) {
            char c;
            if (MainActivity.this.getClient() == null || MainActivity.this.getService() == null) {
                return;
            }
            if (MainActivity.this.accessibilityAssistant != null) {
                MainActivity.this.accessibilityAssistant.lockEvents();
                if (MainActivity.this.accessibilityAssistant.isUiUpdateDiscouraged()) {
                    MainActivity.this.accessibilityAssistant.unlockEvents();
                    return;
                }
            }
            if (MainActivity.this.filesAdapter != null) {
                MainActivity.this.filesAdapter.performPendingUpdate();
            }
            String con = MainActivity.this.getString(R.string.stat_offline);
            int con_color = 0xffff0000;
            int flags = MainActivity.this.getClient().getFlags();
            if ((flags & 8192) == 8192) {
                con = MainActivity.this.getString(R.string.stat_connecting);
            } else if ((flags & 32768) == 0) {
                con = MainActivity.this.getString(R.string.stat_unauthorized);
            } else if ((flags & 32768) == 32768) {
                con = MainActivity.this.getString(R.string.stat_online);
                con_color = -16711936;
            } else if ((flags & 8192) == 8192) {
                con = MainActivity.this.getString(R.string.stat_connecting);
            }
            this.val$connection.setText(MainActivity.this.getString(R.string.label_connection) + " " + con);
            this.val$connection.setTextColor(con_color);
            if ((flags & 32768) != 32768) {
                if (MainActivity.this.prefs != null && ((Boolean) MainActivity.this.prefs.get(Preferences.PREF_DISPLAY_SHOW_PING_NO_SERVER, false)).booleanValue()) {
                    ServerEntry entry = MainActivity.this.getService() != null ? MainActivity.this.getService().getServerEntry() : null;
                    if (entry != null && entry.ipaddr != null) {
                        final String ip = entry.ipaddr;
                        final TextView textView = this.val$ping;
                        final int i = this.val$defcolor;
                        new Thread(new Runnable() { 
                            @Override
                            public final void run() {
                                MainActivity.AnonymousClass3.this.lambda$onTick$2(ip, textView, i);
                            }
                        }).start();
                    }
                } else {
                    this.val$ping.setText("");
                }
                MainActivity.this.accessibilityAssistant.unlockEvents();
                return;
            }
            ClientStatistics stats = new ClientStatistics();
            if (!MainActivity.this.getClient().getClientStatistics(stats)) {
                MainActivity.this.accessibilityAssistant.unlockEvents();
                return;
            }
            if (this.prev_stats == null) {
                this.prev_stats = stats;
            }
            long totalrx = stats.nUdpBytesRecv - this.prev_stats.nUdpBytesRecv;
            long totaltx = stats.nUdpBytesSent - this.prev_stats.nUdpBytesSent;
            if (stats.nUdpPingTimeMs < 0) {
                c = 0;
            } else {
                String str = String.format(Locale.ROOT, "%1$d", Integer.valueOf(stats.nUdpPingTimeMs));
                c = 0;
                this.val$ping.setText(MainActivity.this.getString(R.string.label_ping) + " " + str);
                int i2 = stats.nUdpPingTimeMs;
                TextView textView2 = this.val$ping;
                if (i2 > 250) {
                    textView2.setTextColor(0xffff0000);
                } else {
                    textView2.setTextColor(this.val$defcolor);
                }
            }
            Locale locale = Locale.ROOT;
            Long valueOf = Long.valueOf(totalrx / PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID);
            Long valueOf2 = Long.valueOf(totaltx / PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID);
            Object[] objArr = new Object[2];
            objArr[c] = valueOf;
            objArr[1] = valueOf2;
            String str2 = String.format(locale, "%1$d/%2$d KB", objArr);
            this.val$total.setText(MainActivity.this.getString(R.string.label_rxtx) + " " + str2);
            this.prev_stats = stats;
            MainActivity.this.accessibilityAssistant.unlockEvents();
        }

                public void lambda$onTick$2(String ip, final TextView ping, final int defcolor) {
            try {
                Process p = Runtime.getRuntime().exec("ping -c 1 -w 1 " + ip);
                int status = p.waitFor();
                if (status == 0) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String timeStr = "";
                    while (true) {
                        String line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        if (line.contains("time=")) {
                            timeStr = line.substring(line.indexOf("time=") + 5);
                            if (timeStr.contains(" ")) {
                                timeStr = timeStr.substring(0, timeStr.indexOf(" "));
                            }
                        }
                    }
                    final String finalTime = timeStr;
                    MainActivity.this.runOnUiThread(new Runnable() { 
                        @Override
                        public final void run() {
                            MainActivity.AnonymousClass3.this.lambda$onTick$0(finalTime, ping, defcolor);
                        }
                    });
                    return;
                }
                MainActivity.this.runOnUiThread(new Runnable() { 
                    @Override
                    public final void run() {
                        MainActivity.AnonymousClass3.this.lambda$onTick$1(ping);
                    }
                });
            } catch (Exception e) {
            }
        }

                public void lambda$onTick$0(String finalTime, TextView ping, int defcolor) {
            if (!finalTime.isEmpty()) {
                ping.setText(MainActivity.this.getString(R.string.label_ping) + " " + finalTime + "ms");
                ping.setTextColor(defcolor);
            }
        }

                public void lambda$onTick$1(TextView ping) {
            ping.setText(MainActivity.this.getString(R.string.label_ping) + " timeout");
            ping.setTextColor(0xffff0000);
        }

        @Override
        public void onFinish() {
            start();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        Object item = this.channelsAdapter.getItem(position);
        if (item instanceof User) {
            User user = (User) item;
            Intent intent = new Intent(this, (Class<?>) UserPropActivity.class);
            startActivityForResult(intent.putExtra("userid", user.nUserID), 3);
        } else if (item instanceof Channel) {
            Channel channel = (Channel) item;
            setCurrentChannel(channel.nChannelID > 0 ? channel : null);
            this.channelsAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> l, View v, int position, long id) {
        Object item = this.channelsAdapter.getItem(position);
        if (item instanceof User) {
            this.selectedUser = (User) item;
            UserAccount myuseraccount = new UserAccount();
            getClient().getMyUserAccount(myuseraccount);
            boolean banRight = (myuseraccount.uUserRights & 64) != 0;
            boolean moveRight = (myuseraccount.uUserRights & 128) != 0;
            boolean kickRight = (myuseraccount.uUserRights & 32) != 0;
            int myuserid = getClient().getMyUserID();
            boolean operatorRight = getClient().isChannelOperator(myuserid, this.selectedUser.nChannelID);
            PopupMenu userActions = new PopupMenu(this, v);
            userActions.setOnMenuItemClickListener(this);
            userActions.inflate(R.menu.user_actions);
            userActions.getMenu().findItem(R.id.action_kickchan).setEnabled(kickRight | operatorRight).setVisible(kickRight | operatorRight);
            userActions.getMenu().findItem(R.id.action_kicksrv).setEnabled(kickRight).setVisible(kickRight);
            userActions.getMenu().findItem(R.id.action_banchan).setEnabled(banRight | operatorRight).setVisible(banRight | operatorRight);
            userActions.getMenu().findItem(R.id.action_bansrv).setEnabled(banRight).setVisible(banRight);
            userActions.getMenu().findItem(R.id.action_makeop).setTitle(getClient().isChannelOperator(this.selectedUser.nUserID, this.selectedUser.nChannelID) ? R.string.action_revoke_operator : R.string.action_make_operator);
            userActions.getMenu().findItem(R.id.action_select).setTitle(this.userIDS.contains(Integer.valueOf(this.selectedUser.nUserID)) ? R.string.action_deselect : R.string.action_select);
            userActions.getMenu().findItem(R.id.action_select).setEnabled(moveRight).setVisible(moveRight);
            userActions.show();
            return true;
        }
        if (!(item instanceof Channel)) {
            return false;
        }
        this.selectedChannel = (Channel) item;
        UserAccount myuseraccount2 = new UserAccount();
        getClient().getMyUserAccount(myuseraccount2);
        boolean moveRight2 = (myuseraccount2.uUserRights & UserRight.USERRIGHT_MOVE_USERS) != 0;
        boolean modifyRight = (myuseraccount2.uUserRights & UserRight.USERRIGHT_MODIFY_CHANNELS) != 0;
        boolean operatorRight = getClient().isChannelOperator(getClient().getMyUserID(), this.selectedChannel.nChannelID);
        boolean isClassroom = (this.selectedChannel.uChannelType & ChannelType.CHANNEL_CLASSROOM) != 0;
        User everyone = new User();
        everyone.nUserID = Constants.TT_CLASSROOM_FREEFORALL;

        PopupMenu channelActions = new PopupMenu(this, v);
        channelActions.setOnMenuItemClickListener(this);
        channelActions.inflate(R.menu.channel_actions);
        boolean canMove = moveRight2 && !this.userIDS.isEmpty();
        channelActions.getMenu().findItem(R.id.action_move).setEnabled(canMove).setVisible(canMove);
        if (channelActions.getMenu().findItem(R.id.action_allowvoice) != null) {
            channelActions.getMenu().findItem(R.id.action_allowvoice).setEnabled(isClassroom && (modifyRight || operatorRight)).setVisible(isClassroom && (modifyRight || operatorRight));
            channelActions.getMenu().findItem(R.id.action_allowvoice).setTitle(Utils.isTransmitAllowed(everyone, this.selectedChannel, StreamType.STREAMTYPE_VOICE) ? R.string.action_disallowvoice : R.string.action_allowvoice);
            channelActions.getMenu().findItem(R.id.action_allowvideo).setEnabled(isClassroom && (modifyRight || operatorRight)).setVisible(isClassroom && (modifyRight || operatorRight));
            channelActions.getMenu().findItem(R.id.action_allowvideo).setTitle(Utils.isTransmitAllowed(everyone, this.selectedChannel, StreamType.STREAMTYPE_VIDEOCAPTURE) ? R.string.action_disallowvideo : R.string.action_allowvideo);
            channelActions.getMenu().findItem(R.id.action_allowdesktop).setEnabled(isClassroom && (modifyRight || operatorRight)).setVisible(isClassroom && (modifyRight || operatorRight));
            channelActions.getMenu().findItem(R.id.action_allowdesktop).setTitle(Utils.isTransmitAllowed(everyone, this.selectedChannel, StreamType.STREAMTYPE_DESKTOP) ? R.string.action_disallowdesktop : R.string.action_allowdesktop);
            channelActions.getMenu().findItem(R.id.action_allowmedia).setEnabled(isClassroom && (modifyRight || operatorRight)).setVisible(isClassroom && (modifyRight || operatorRight));
            channelActions.getMenu().findItem(R.id.action_allowmedia).setTitle(Utils.isTransmitAllowed(everyone, this.selectedChannel, StreamType.STREAMTYPE_MEDIAFILE) ? R.string.action_disallowmedia : R.string.action_allowmedia);
            channelActions.getMenu().findItem(R.id.action_allowchanmsg).setEnabled(isClassroom && (modifyRight || operatorRight)).setVisible(isClassroom && (modifyRight || operatorRight));
            channelActions.getMenu().findItem(R.id.action_allowchanmsg).setTitle(Utils.isTransmitAllowed(everyone, this.selectedChannel, StreamType.STREAMTYPE_CHANNELMSG) ? R.string.action_disallowchanmsg : R.string.action_allowchanmsg);
        }
        channelActions.show();
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        int itemId = item.getItemId();
        if (itemId == R.id.action_banchan) {
            alert.setMessage(getString(R.string.ban_confirmation, new Object[]{this.selectedUser.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$onMenuItemClick$13(dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        } else if (itemId == R.id.action_bansrv) {
            alert.setMessage(getString(R.string.ban_confirmation, new Object[]{this.selectedUser.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$onMenuItemClick$14(dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        } else if (itemId == R.id.action_edit) {
            editChannelProperties(this.selectedChannel);
        } else if (itemId == R.id.action_edituser) {
            startActivityForResult(new Intent(this, (Class<?>) UserPropActivity.class).putExtra("userid", this.selectedUser.nUserID), 3);
        } else if (itemId == R.id.action_message) {
            startActivity(new Intent(this, (Class<?>) TextMessageActivity.class).putExtra("userid", this.selectedUser.nUserID));
        } else if (itemId == R.id.action_kickchan) {
            alert.setMessage(getString(R.string.kick_confirmation, new Object[]{this.selectedUser.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$onMenuItemClick$15(dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        } else if (itemId == R.id.action_kicksrv) {
            alert.setMessage(getString(R.string.kick_confirmation, new Object[]{this.selectedUser.szNickname}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$onMenuItemClick$16(dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        } else if (itemId == R.id.action_makeop) {
            UserAccount myuseraccount = new UserAccount();
            getClient().getMyUserAccount(myuseraccount);
            if ((myuseraccount.uUserRights & 256) != 0) {
                getClient().doChannelOp(this.selectedUser.nUserID, this.selectedUser.nChannelID, !getClient().isChannelOperator(this.selectedUser.nUserID, this.selectedUser.nChannelID));
            } else {
                alert.setTitle(getClient().isChannelOperator(this.selectedUser.nUserID, this.selectedUser.nChannelID) ? R.string.action_revoke_operator : R.string.action_make_operator);
                alert.setMessage(R.string.text_operator_password);
                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                    @Override
                    public final void onClick(DialogInterface dialogInterface, int i) {
                        MainActivity.this.lambda$onMenuItemClick$17(input, dialogInterface, i);
                    }
                });
                alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
                alert.setView(input);
                alert.show();
            }
        } else if (itemId == R.id.action_move) {
            for (Integer userID : this.userIDS) {
                getClient().doMoveUser(userID.intValue(), this.selectedChannel.nChannelID);
            }
            this.userIDS.clear();
        } else if (itemId == R.id.action_select) {
            boolean contains = this.userIDS.contains(Integer.valueOf(this.selectedUser.nUserID));
            List<Integer> list = this.userIDS;
            if (contains) {
                list.remove(Integer.valueOf(this.selectedUser.nUserID));
            } else {
                list.add(Integer.valueOf(this.selectedUser.nUserID));
            }
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        } else if (itemId == R.id.action_remove) {
            alert.setMessage(getString(R.string.channel_remove_confirmation, new Object[]{this.selectedChannel.szName}));
            alert.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() { 
                @Override
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.lambda$onMenuItemClick$18(dialogInterface, i);
                }
            });
            alert.setNegativeButton(android.R.string.no, (DialogInterface.OnClickListener) null);
            alert.show();
        } else if (itemId == R.id.action_allowvoice) {
            User everyone2 = new User();
            everyone2.nUserID = Constants.TT_CLASSROOM_FREEFORALL;
            boolean allowed = Utils.isTransmitAllowed(everyone2, this.selectedChannel, StreamType.STREAMTYPE_VOICE);
            Utils.toggleTransmitUsers(everyone2, this.selectedChannel, StreamType.STREAMTYPE_VOICE, !allowed);
            getClient().doUpdateChannel(this.selectedChannel);
        } else if (itemId == R.id.action_allowvideo) {
            User everyone2 = new User();
            everyone2.nUserID = Constants.TT_CLASSROOM_FREEFORALL;
            boolean allowed = Utils.isTransmitAllowed(everyone2, this.selectedChannel, StreamType.STREAMTYPE_VIDEOCAPTURE);
            Utils.toggleTransmitUsers(everyone2, this.selectedChannel, StreamType.STREAMTYPE_VIDEOCAPTURE, !allowed);
            getClient().doUpdateChannel(this.selectedChannel);
        } else if (itemId == R.id.action_allowdesktop) {
            User everyone2 = new User();
            everyone2.nUserID = Constants.TT_CLASSROOM_FREEFORALL;
            boolean allowed = Utils.isTransmitAllowed(everyone2, this.selectedChannel, StreamType.STREAMTYPE_DESKTOP);
            Utils.toggleTransmitUsers(everyone2, this.selectedChannel, StreamType.STREAMTYPE_DESKTOP, !allowed);
            getClient().doUpdateChannel(this.selectedChannel);
        } else if (itemId == R.id.action_allowmedia) {
            User everyone2 = new User();
            everyone2.nUserID = Constants.TT_CLASSROOM_FREEFORALL;
            boolean allowed = Utils.isTransmitAllowed(everyone2, this.selectedChannel, StreamType.STREAMTYPE_MEDIAFILE);
            Utils.toggleTransmitUsers(everyone2, this.selectedChannel, StreamType.STREAMTYPE_MEDIAFILE, !allowed);
            getClient().doUpdateChannel(this.selectedChannel);
        } else if (itemId == R.id.action_allowchanmsg) {
            User everyone2 = new User();
            everyone2.nUserID = Constants.TT_CLASSROOM_FREEFORALL;
            boolean allowed = Utils.isTransmitAllowed(everyone2, this.selectedChannel, StreamType.STREAMTYPE_CHANNELMSG);
            Utils.toggleTransmitUsers(everyone2, this.selectedChannel, StreamType.STREAMTYPE_CHANNELMSG, !allowed);
            getClient().doUpdateChannel(this.selectedChannel);
        } else {
            if (itemId != R.id.action_banned_users) {
                return false;
            }
            if (this.selectedChannel != null) {
                Intent intent = new Intent(this, (Class<?>) ChannelBannedUsersActivity.class);
                intent.putExtra("channel_id", this.selectedChannel.nChannelID);
                startActivity(intent);
            }
        }
        return true;
    }

        public void lambda$onMenuItemClick$13(DialogInterface dialog, int whichButton) {
        getClient().doBanUser(this.selectedUser.nUserID, this.selectedUser.nChannelID);
        getClient().doKickUser(this.selectedUser.nUserID, this.selectedUser.nChannelID);
    }

        public void lambda$onMenuItemClick$14(DialogInterface dialog, int whichButton) {
        getClient().doBanUser(this.selectedUser.nUserID, 0);
        getClient().doKickUser(this.selectedUser.nUserID, 0);
    }

        public void lambda$onMenuItemClick$15(DialogInterface dialog, int whichButton) {
        getClient().doKickUser(this.selectedUser.nUserID, this.selectedUser.nChannelID);
    }

        public void lambda$onMenuItemClick$16(DialogInterface dialog, int whichButton) {
        getClient().doKickUser(this.selectedUser.nUserID, 0);
    }

        public void lambda$onMenuItemClick$17(EditText input, DialogInterface dialog, int whichButton) {
        getClient().doChannelOpEx(this.selectedUser.nUserID, this.selectedUser.nChannelID, input.getText().toString(), !getClient().isChannelOperator(this.selectedUser.nUserID, this.selectedUser.nChannelID));
    }

        public void lambda$onMenuItemClick$18(DialogInterface dialog, int whichButton) {
        if (getClient().doRemoveChannel(this.selectedChannel.nChannelID) <= 0) {
            Toast.makeText(this, getString(R.string.err_channel_remove, new Object[]{this.selectedChannel.szName}), 1).show();
        }
    }

    private void adjustSoundSystem() {
        if (this.audioManager.isBluetoothA2dpOn()) {
            return;
        }
        boolean z = false;
        boolean aec = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("eq_mic_aec", false);
        boolean voiceProcessing = ((Boolean) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_VOICEPROCESSING, false)).booleanValue() || aec;
        this.audioManager.setMode(voiceProcessing ? AudioManager.MODE_IN_COMMUNICATION : AudioManager.MODE_NORMAL);
        if (voiceProcessing) {
            AudioManager audioManager = this.audioManager;
            if (((Boolean) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_SPEAKERPHONE, false)).booleanValue() && !this.audioManager.isWiredHeadsetOn()) {
                z = true;
            }
            audioManager.setSpeakerphoneOn(z);
        }
    }

        public void adjustMuteButton(ImageButton btn) {
        if (btn == null) return;
        boolean isMute = getService() != null && getService().getCurrentMuteState();
        if (isMute) {
            btn.setImageResource(R.drawable.mute_blue);
            btn.setContentDescription(getString(R.string.speaker_unmute));
        } else {
            btn.setImageResource(R.drawable.speaker_blue);
            btn.setContentDescription(getString(R.string.speaker_mute));
        }
    }

    private void adjustVoxState(boolean voiceActivationEnabled, int level) {
        ImageButton voxSwitch = (ImageButton) findViewById(R.id.voxSwitch);
        TextView micLevel = (TextView) findViewById(R.id.miclevel_text);
        SeekBar micGainSeekBar = (SeekBar) findViewById(R.id.mic_gainSeekBar);
        if (voiceActivationEnabled) {
            if (micLevel != null) {
                micLevel.setText(level + "%");
                micLevel.setContentDescription(getString(R.string.vox_level_description, new Object[]{micLevel.getText()}));
            }
            if (voxSwitch != null) {
                voxSwitch.setImageResource(R.drawable.microphone);
                voxSwitch.setContentDescription(getString(R.string.voice_activation_off));
            }
            if (micGainSeekBar != null && getClient() != null) {
                micGainSeekBar.setProgress(getClient().getVoiceActivationLevel());
                micGainSeekBar.setContentDescription(getString(R.string.voxlevel));
            }
            return;
        }
        if (micLevel != null) {
            micLevel.setText(Utils.refVolumeToPercent(level) + "%");
            micLevel.setContentDescription(getString(R.string.mic_gain_description, new Object[]{micLevel.getText()}));
        }
        if (voxSwitch != null) {
            voxSwitch.setImageResource(R.drawable.mic_green);
            voxSwitch.setContentDescription(getString(R.string.voice_activation_on));
        }
        if (micGainSeekBar != null && getClient() != null) {
            micGainSeekBar.setProgress(Utils.refVolumeToPercent(getClient().getSoundInputGainLevel()));
            micGainSeekBar.setContentDescription(getString(R.string.micgain));
        }
    }

    private void adjustTxState(boolean txEnabled) {
        if (this.accessibilityAssistant != null) {
            this.accessibilityAssistant.lockEvents();
        }
        View txBtn = findViewById(R.id.transmit_voice);
        if (txBtn != null) {
            txBtn.setBackgroundColor(txEnabled ? -16711936 : 0xffff0000);
            txBtn.setContentDescription(getString(txEnabled ? R.string.tx_on : R.string.tx_off));
        }
        if (this.curchannel != null && getClient() != null && getClient().getMyChannelID() == this.curchannel.nChannelID) {
            if (this.channelsAdapter != null) {
                this.channelsAdapter.notifyDataSetChanged();
            }
        }
        if (this.accessibilityAssistant != null) {
            this.accessibilityAssistant.unlockEvents();
        }
    }

    private void adjustVoiceGain() {
        boolean voiceActivationEnabled = getService() != null && getService().isVoiceActivationEnabled();
        boolean showMicSeekBar = this.mychannel == null || !this.mychannel.audiocfg.bEnableAGC || voiceActivationEnabled;
        View micSeekBar = findViewById(R.id.mic_gainSeekBar);
        if (micSeekBar != null) {
            micSeekBar.setVisibility(showMicSeekBar ? View.VISIBLE : View.GONE);
        }
    }

    private void setupButtons() {
        Button tx_btn = (Button) findViewById(R.id.transmit_voice);
        if (tx_btn != null && this.accessibilityAssistant != null) {
            tx_btn.setAccessibilityDelegate(this.accessibilityAssistant);
        }
        OnButtonInteractionListener txButtonListener = new OnButtonInteractionListener() { 
            boolean tx_state = false;
            long tx_down_start = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean tx = event.getAction() != 1;
                if (tx != this.tx_state) {
                    if (!tx) {
                        if (System.currentTimeMillis() - this.tx_down_start < 800) {
                            tx = true;
                            this.tx_down_start = 0L;
                        } else {
                            this.tx_down_start = System.currentTimeMillis();
                        }
                    }
                    TeamTalkService service = MainActivity.this.getService();
                    if (service != null) {
                        if (service.isVoiceActivationEnabled()) {
                            service.enableVoiceActivation(false);
                        }
                        service.enableVoiceTransmission(tx);
                    }
                }
                this.tx_state = tx;
                return true;
            }

            @Override
            public void onClick(View v) {
                if (System.currentTimeMillis() - this.tx_down_start < 800) {
                    this.tx_state = true;
                    this.tx_down_start = 0L;
                } else {
                    this.tx_state = false;
                    this.tx_down_start = System.currentTimeMillis();
                }
                TeamTalkService service = MainActivity.this.getService();
                if (service != null) {
                    if (service.isVoiceActivationEnabled()) {
                        service.enableVoiceActivation(false);
                    }
                    service.enableVoiceTransmission(this.tx_state);
                }
            }
        };
        if (tx_btn != null) {
            tx_btn.setOnTouchListener(txButtonListener);
            if (Build.VERSION.SDK_INT >= 26 && this.accessibilityAssistant != null && this.accessibilityAssistant.isServiceActive()) {
                tx_btn.setOnClickListener(txButtonListener);
            }
        }
        final SeekBar masterSeekBar = (SeekBar) findViewById(R.id.master_volSeekBar);
        final SeekBar micSeekBar = (SeekBar) findViewById(R.id.mic_gainSeekBar);
        final TextView micLevel = (TextView) findViewById(R.id.miclevel_text);
        final TextView volLevel = (TextView) findViewById(R.id.vollevel_text);
        if (masterSeekBar != null) {
            masterSeekBar.setMax(100);
        }
        if (micSeekBar != null) {
            micSeekBar.setMax(100);
        }
        SeekBar.OnSeekBarChangeListener volListener = new SeekBar.OnSeekBarChangeListener() { 
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                TeamTalkService service = MainActivity.this.getService();
                TeamTalkBase client = MainActivity.this.getClient();
                if (seekBar == masterSeekBar) {
                    if (service != null && service.isMute()) {
                        service.setMute(false);
                        ImageButton speakerBtn = (ImageButton) MainActivity.this.findViewById(R.id.speakerBtn);
                        MainActivity.this.adjustMuteButton(speakerBtn);
                    }
                    int outputVolume = Utils.refVolume(progress);
                    if (client != null) {
                        client.setSoundOutputVolume(outputVolume);
                    }
                    if (MainActivity.this.prefs != null) {
                        MainActivity.this.prefs.put(Preferences.PREF_SOUNDSYSTEM_MASTERVOLUME, Integer.valueOf(outputVolume));
                    }
                    if (volLevel != null) {
                        volLevel.setText(progress + "%");
                        volLevel.setContentDescription(MainActivity.this.getString(R.string.speaker_volume_description, new Object[]{volLevel.getText()}));
                    }
                    return;
                }
                if (seekBar == micSeekBar) {
                    if (service != null && service.isVoiceActivationEnabled()) {
                        if (client != null) {
                            client.setVoiceActivationLevel(progress);
                        }
                        if (MainActivity.this.prefs != null) {
                            MainActivity.this.prefs.put(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, Integer.valueOf(progress));
                        }
                        if (micLevel != null) {
                            micLevel.setText(progress + "%");
                            micLevel.setContentDescription(MainActivity.this.getString(R.string.vox_level_description, new Object[]{micLevel.getText()}));
                        }
                        return;
                    }
                    int inputGain = Utils.refGain(progress);
                    if (client != null) {
                        client.setSoundInputGainLevel(inputGain);
                    }
                    if (MainActivity.this.prefs != null) {
                        MainActivity.this.prefs.put(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, Integer.valueOf(inputGain));
                    }
                    if (micLevel != null) {
                        micLevel.setText(progress + "%");
                        micLevel.setContentDescription(MainActivity.this.getString(R.string.mic_gain_description, new Object[]{micLevel.getText()}));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
        if (masterSeekBar != null) {
            masterSeekBar.setOnSeekBarChangeListener(volListener);
        }
        if (micSeekBar != null) {
            micSeekBar.setOnSeekBarChangeListener(volListener);
        }
        ImageButton speakerBtn = (ImageButton) findViewById(R.id.speakerBtn);
        if (speakerBtn != null) {
            speakerBtn.setOnClickListener(new View.OnClickListener() { 
                @Override
                public final void onClick(View view) {
                    MainActivity.this.lambda$setupButtons$19(volLevel, view);
                }
            });
        }
        ImageButton voxSwitch = (ImageButton) findViewById(R.id.voxSwitch);
        if (voxSwitch != null) {
            voxSwitch.setOnClickListener(new View.OnClickListener() { 
                @Override
                public final void onClick(View view) {
                    MainActivity.this.lambda$setupButtons$20(view);
                }
            });
        }
    }

        public void lambda$setupButtons$19(TextView volLevel, View v) {
        int level;
        TeamTalkService service = getService();
        TeamTalkBase client = getClient();
        if (service != null && client != null) {
            service.setMute(!service.isMute());
            adjustMuteButton((ImageButton) v);
            if (service.isMute()) {
                level = 0;
            } else {
                level = Utils.refVolumeToPercent(client.getSoundOutputVolume());
            }
            if (volLevel != null) {
                volLevel.setText(level + "%");
                volLevel.setContentDescription(getString(R.string.speaker_volume_description, new Object[]{volLevel.getText()}));
            }
        }
    }

        public void lambda$setupButtons$20(View v) {
        TeamTalkService service = getService();
        if (service != null) {
            if (service.isVoiceTransmissionEnabled()) {
                service.enableVoiceTransmission(false);
            }
            service.enableVoiceActivation(!service.isVoiceActivationEnabled());
            adjustVoiceGain();
        }
    }

    private void initializeTeamTalkService(TeamTalkService service) {
        this.users = new HashMap(service.getUsers());
        int mychanid = getClient().getMyChannelID();
        if (mychanid > 0) {
            setCurrentChannel(service.getChannels().get(Integer.valueOf(mychanid)));
        }
        setMyChannel(service.getChannels().get(Integer.valueOf(mychanid)));
        this.mSectionsPagerAdapter.onPageSelected(this.mViewPager.getCurrentItem());
        this.channelsAdapter.notifyDataSetChanged();
        this.textmsgAdapter.setTextMessages(service.getChatLogTextMsgs());
        this.textmsgAdapter.setMyUserID(getClient().getMyUserID());
        this.textmsgAdapter.notifyDataSetChanged();
        this.mediaAdapter.setTeamTalkService(service);
        this.mediaAdapter.notifyDataSetChanged();
        this.filesAdapter.setTeamTalkService(service);
        this.filesAdapter.update(mychanid);
        int flags = getClient().getFlags();
        if ((flags & 2) == 0 && !getClient().initSoundOutputDevice(0)) {
            Toast.makeText(this, R.string.err_init_sound_output, 1).show();
        }
        if (!this.restarting) {
            service.setMute(false);
            service.enableVoiceTransmission(false);
            service.enableVoiceActivation(false);
            if (Permissions.READ_PHONE_STATE.request(this)) {
                service.enablePhoneCallReaction();
            }
        }
        service.getEventHandler().registerOnConnectionLostListener(this, true);
        service.getEventHandler().registerOnCmdProcessing(this, true);
        service.getEventHandler().registerOnCmdMyselfLoggedIn(this, true);
        service.getEventHandler().registerOnCmdMyselfLoggedOut(this, true);
        service.getEventHandler().registerOnCmdMyselfKickedFromChannel(this, true);
        service.getEventHandler().registerOnCmdUserLoggedIn(this, true);
        service.getEventHandler().registerOnCmdUserLoggedOut(this, true);
        service.getEventHandler().registerOnCmdUserUpdate(this, true);
        service.getEventHandler().registerOnCmdUserJoinedChannel(this, true);
        service.getEventHandler().registerOnCmdUserLeftChannel(this, true);
        service.getEventHandler().registerOnCmdUserTextMessage(this, true);
        service.getEventHandler().registerOnCmdChannelNew(this, true);
        service.getEventHandler().registerOnCmdChannelUpdate(this, true);
        service.getEventHandler().registerOnCmdChannelRemove(this, true);
        service.getEventHandler().registerOnCmdFileNew(this, true);
        service.getEventHandler().registerOnCmdFileRemove(this, true);
        service.getEventHandler().registerOnUserStateChange(this, true);
        service.getEventHandler().registerOnVoiceActivation(this, true);
        service.setOnVoiceTransmissionToggleListener(this);
        adjustSoundSystem();
        if (((Boolean) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_BLUETOOTH_HEADSET, false)).booleanValue() && Permissions.BLUETOOTH.request(this)) {
            service.watchBluetoothHeadset();
        }
        if (Permissions.WAKE_LOCK.request(this)) {
            this.wakeLock.acquire();
        }
        int mastervol = ((Integer) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_MASTERVOLUME, Integer.valueOf(SoundLevel.SOUND_VOLUME_DEFAULT))).intValue();
        int gain = ((Integer) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, Integer.valueOf(SoundLevel.SOUND_GAIN_DEFAULT))).intValue();
        int voxlevel = ((Integer) this.prefs.get(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 5)).intValue();
        boolean voxState = service.isVoiceActivationEnabled();
        boolean txState = service.isVoiceTransmitting();
        if (getClient().getSoundOutputVolume() != mastervol) {
            getClient().setSoundOutputVolume(mastervol);
        }
        if (getClient().getSoundInputGainLevel() != gain) {
            getClient().setSoundInputGainLevel(gain);
        }
        if (getClient().getVoiceActivationLevel() != voxlevel) {
            getClient().setVoiceActivationLevel(voxlevel);
        }
        adjustMuteButton((ImageButton) findViewById(R.id.speakerBtn));
        adjustVoxState(voxState, voxState ? voxlevel : getClient().getSoundInputGainLevel());
        adjustTxState(txState);
        SeekBar masterSeekBar = (SeekBar) findViewById(R.id.master_volSeekBar);
        SeekBar micSeekBar = (SeekBar) findViewById(R.id.mic_gainSeekBar);
        masterSeekBar.setProgress(Utils.refVolumeToPercent(getClient().getSoundOutputVolume()));
        if (service.isVoiceActivationEnabled()) {
            micSeekBar.setProgress(getClient().getVoiceActivationLevel());
        } else {
            micSeekBar.setProgress(Utils.refVolumeToPercent(getClient().getSoundInputGainLevel()));
        }
        TextView volLevel = (TextView) findViewById(R.id.vollevel_text);
        volLevel.setText(Utils.refVolumeToPercent(mastervol) + "%");
        volLevel.setContentDescription(getString(R.string.speaker_volume_description, new Object[]{volLevel.getText()}));
    }

    private void closeTeamTalkService(TeamTalkService service) {
        if (this.wakeLock != null && this.wakeLock.isHeld()) {
            this.wakeLock.release();
        }
        if (service != null) {
            service.setOnVoiceTransmissionToggleListener(null);
            if (service.getEventHandler() != null) {
                service.getEventHandler().unregisterListener(this);
            }
            if (this.mediaAdapter != null) {
                this.mediaAdapter.clearTeamTalkService(service);
            }
        }
        if (this.filesAdapter != null) {
            this.filesAdapter.setTeamTalkService(null);
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        initializeTeamTalkService(service);
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        closeTeamTalkService(service);
    }

    TeamTalkService getService() {
        return this.mConnection != null ? this.mConnection.getService() : null;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Permissions granted = Permissions.onRequestResult(this, requestCode, grantResults);
        if (granted == null && (granted = Permissions.fromRequestCode(requestCode)) != Permissions.READ_MEDIA_IMAGES && granted != Permissions.READ_MEDIA_VIDEO && granted != Permissions.READ_MEDIA_AUDIO) {
            return;
        }
        switch (granted) {
            case READ_EXTERNAL_STORAGE:
            case READ_MEDIA_IMAGES:
            case READ_MEDIA_VIDEO:
            case READ_MEDIA_AUDIO:
                if (Build.VERSION.SDK_INT < 33 || areMediaPermissionsComplete()) {
                    fileSelectionStart();
                    return;
                }
                return;
            case WAKE_LOCK:
                this.wakeLock.acquire();
                return;
            case READ_PHONE_STATE:
                if (this.mConnection != null && this.mConnection.isBound()) {
                    getService().enablePhoneCallReaction();
                    return;
                }
                return;
            case BLUETOOTH:
                if (this.mConnection != null && this.mConnection.isBound()) {
                    getService().watchBluetoothHeadset();
                    return;
                }
                return;
            default:
                return;
        }
    }

    @Override
    public void onCmdProcessing(int cmdId, boolean complete) {
        if (complete) {
            this.activecmds.remove(cmdId);
        }
    }

    @Override
    public void onCmdMyselfLoggedIn(int my_userid, UserAccount useraccount) {
        this.textmsgAdapter.setMyUserID(my_userid);
    }

    @Override
    public void onCmdMyselfLoggedOut() {
        this.accessibilityAssistant.lockEvents();
        this.channelsAdapter.notifyDataSetChanged();
        this.accessibilityAssistant.unlockEvents();
    }

    @Override
    public void onCmdMyselfKickedFromChannel() {
        this.accessibilityAssistant.lockEvents();
        this.channelsAdapter.notifyDataSetChanged();
        this.accessibilityAssistant.unlockEvents();
    }

    @Override
    public void onCmdMyselfKickedFromChannel(User kicker) {
        this.accessibilityAssistant.lockEvents();
        this.channelsAdapter.notifyDataSetChanged();
        this.accessibilityAssistant.unlockEvents();
    }

    @Override
    public void onCmdUserLoggedIn(User user) {
        this.users.put(Integer.valueOf(user.nUserID), user);
        this.accessibilityAssistant.lockEvents();
        this.textmsgAdapter.notifyDataSetChanged();
        this.accessibilityAssistant.unlockEvents();
        if (this.sounds.get(16) != 0) {
            this.audioIcons.play(this.sounds.get(16), 1.0f, 1.0f, 0, 0, 1.0f);
        }
        if (this.ttsWrapper != null && ((Boolean) this.prefs.get("server_login_checkbox", false)).booleanValue()) {
            String name = Utils.getDisplayName(getBaseContext(), user);
            this.ttsWrapper.speak(name + " " + getResources().getString(R.string.text_tts_loggedin));
        }
    }

    @Override
    public void onCmdUserLoggedOut(User user) {
        this.users.remove(Integer.valueOf(user.nUserID));
        this.accessibilityAssistant.lockEvents();
        this.textmsgAdapter.notifyDataSetChanged();
        this.accessibilityAssistant.unlockEvents();
        if (this.sounds.get(17) != 0) {
            this.audioIcons.play(this.sounds.get(17), 1.0f, 1.0f, 0, 0, 1.0f);
        }
        if (this.ttsWrapper != null && ((Boolean) this.prefs.get("server_logout_checkbox", false)).booleanValue()) {
            String name = Utils.getDisplayName(getBaseContext(), user);
            this.ttsWrapper.speak(name + " " + getResources().getString(R.string.text_tts_loggedout));
        }
    }

    @Override
    public void onCmdUserUpdate(User user) {
        if (this.curchannel != null && this.curchannel.nChannelID == user.nChannelID) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
        subscriptionChange(user);
        this.users.put(Integer.valueOf(user.nUserID), user);
    }

    @Override
    public void onCmdUserJoinedChannel(User user) {
        this.users.put(Integer.valueOf(user.nUserID), user);

        int myUserId = getClient() != null ? getClient().getMyUserID() : 0;
        int myChannelId = getClient() != null ? getClient().getMyChannelID() : (this.mychannel != null ? this.mychannel.nChannelID : 0);

        if (user.nUserID == myUserId) {
            Channel chan = null;
            if (getService() != null && getService().getChannels() != null) {
                chan = getService().getChannels().get(Integer.valueOf(user.nChannelID));
            }
            if (chan == null && getClient() != null) {
                Channel c = new Channel();
                if (getClient().getChannel(user.nChannelID, c)) {
                    chan = c;
                }
            }
            setCurrentChannel(chan);
            this.filesAdapter.update(this.curchannel);
            setMyChannel(chan);
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();

            if (this.ttsWrapper != null && ((Boolean) this.prefs.get("pref_tts_myself_join", false)).booleanValue()) {
                if (chan != null && chan.nParentID == 0) {
                    this.ttsWrapper.speak(getString(R.string.text_cmd_joinroot));
                } else if (chan != null && chan.szName != null && !chan.szName.isEmpty()) {
                    this.ttsWrapper.speak(getString(R.string.text_cmd_joinchan) + " " + chan.szName);
                }
            }
            return;
        }

        boolean isMyChannel = (myChannelId > 0 && myChannelId == user.nChannelID);

        if (isMyChannel) {
            this.accessibilityAssistant.lockEvents();
            this.textmsgAdapter.notifyDataSetChanged();
            this.channelsAdapter.notifyDataSetChanged();
            if (this.sounds.get(14) != 0) {
                this.audioIcons.play(this.sounds.get(14), 1.0f, 1.0f, 0, 0, 1.0f);
            }
            if (this.ttsWrapper != null && ((Boolean) this.prefs.get("channel_join_checkbox", false)).booleanValue()) {
                String name = Utils.getDisplayName(getBaseContext(), user);
                this.ttsWrapper.speak(name + " " + getResources().getString(R.string.text_tts_joined_chan));
            }
            this.accessibilityAssistant.unlockEvents();
            return;
        } else if (this.ttsWrapper != null && (((Boolean) this.prefs.get("all_channel_join_checkbox", false)).booleanValue() || ((Boolean) this.prefs.get("all_users_channel_movement_checkbox", false)).booleanValue())) {
            String name = Utils.getDisplayName(getBaseContext(), user);
            Channel targetChan = null;
            if (getService() != null && getService().getChannels() != null) {
                targetChan = getService().getChannels().get(Integer.valueOf(user.nChannelID));
            }
            if (targetChan == null && getClient() != null) {
                Channel c = new Channel();
                if (getClient().getChannel(user.nChannelID, c)) {
                    targetChan = c;
                }
            }
            String chanName = (targetChan != null && targetChan.szName != null && !targetChan.szName.isEmpty()) ? targetChan.szName : getString(R.string.text_root_chan);
            this.ttsWrapper.speak(getString(R.string.text_tts_user_joined_other_channel, name, chanName));
        }

        if (isVisibleChannel(user.nChannelID)) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
    }

    @Override
    public void onCmdUserLeftChannel(int channelid, User user) {
        this.users.put(Integer.valueOf(user.nUserID), user);

        int myUserId = getClient() != null ? getClient().getMyUserID() : 0;
        int myChannelId = getClient() != null ? getClient().getMyChannelID() : (this.mychannel != null ? this.mychannel.nChannelID : 0);

        if (user.nUserID == myUserId) {
            this.textmsgAdapter.notifyDataSetChanged();
            setMyChannel(null);
            Channel chan = null;
            if (getService() != null && getService().getChannels() != null) {
                chan = getService().getChannels().get(Integer.valueOf(channelid));
            }
            if (chan == null && getClient() != null) {
                Channel c = new Channel();
                if (getClient().getChannel(channelid, c)) {
                    chan = c;
                }
            }
            if (this.curchannel == null && chan != null) {
                setCurrentChannel(chan);
            }
            if (this.channelsAdapter != null) {
                this.accessibilityAssistant.lockEvents();
                this.channelsAdapter.notifyDataSetChanged();
                this.accessibilityAssistant.unlockEvents();
            }

            if (this.ttsWrapper != null && ((Boolean) this.prefs.get("pref_tts_myself_leave", false)).booleanValue()) {
                if (chan != null && chan.nParentID == 0) {
                    this.ttsWrapper.speak(getString(R.string.text_cmd_leftroot));
                } else if (chan != null && chan.szName != null && !chan.szName.isEmpty()) {
                    this.ttsWrapper.speak(getString(R.string.text_cmd_leftchan) + " " + chan.szName);
                }
            }
            return;
        }

        if (this.curchannel != null && channelid == this.curchannel.nChannelID) {
            this.accessibilityAssistant.lockEvents();
            this.textmsgAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }

        boolean isMyChannel = (myChannelId > 0 && myChannelId == channelid);

        if (isMyChannel) {
            this.accessibilityAssistant.lockEvents();
            this.textmsgAdapter.notifyDataSetChanged();
            this.channelsAdapter.notifyDataSetChanged();
            if (this.sounds.get(15) != 0) {
                this.audioIcons.play(this.sounds.get(15), 1.0f, 1.0f, 0, 0, 1.0f);
            }
            if (this.ttsWrapper != null && ((Boolean) this.prefs.get("channel_leave_checkbox", false)).booleanValue()) {
                String name = Utils.getDisplayName(getBaseContext(), user);
                this.ttsWrapper.speak(name + " " + getResources().getString(R.string.text_tts_left_chan));
            }
            this.accessibilityAssistant.unlockEvents();
            return;
        } else if (this.ttsWrapper != null && (((Boolean) this.prefs.get("all_channel_leave_checkbox", false)).booleanValue() || ((Boolean) this.prefs.get("all_users_channel_movement_checkbox", false)).booleanValue())) {
            String name = Utils.getDisplayName(getBaseContext(), user);
            Channel targetChan = null;
            if (getService() != null && getService().getChannels() != null) {
                targetChan = getService().getChannels().get(Integer.valueOf(channelid));
            }
            if (targetChan == null && getClient() != null) {
                Channel c = new Channel();
                if (getClient().getChannel(channelid, c)) {
                    targetChan = c;
                }
            }
            String chanName = (targetChan != null && targetChan.szName != null && !targetChan.szName.isEmpty()) ? targetChan.szName : getString(R.string.text_root_chan);
            this.ttsWrapper.speak(getString(R.string.text_tts_user_left_other_channel, name, chanName));
        }

        if (isVisibleChannel(channelid)) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
    }

    @Override
    public void onCmdUserTextMessage(TextMessage textmessage) {
        if (textmessage.nMsgType == TextMsgType.MSGTYPE_CUSTOM) {
            if (textmessage.szMessage != null && textmessage.szMessage.startsWith("typing\r\n")) {
                boolean typing = textmessage.szMessage.endsWith("1");
                if (typing) {
                    if (this.sounds.get(SOUND_TYPING) != 0) {
                        this.audioIcons.play(this.sounds.get(SOUND_TYPING), 1.0f, 1.0f, 0, 0, 1.0f);
                    }
                    if (this.ttsWrapper != null && ((Boolean) this.prefs.get("pref_tts_typing", true)).booleanValue()) {
                        User sender = getService() != null ? getService().getUsers().get(Integer.valueOf(textmessage.nFromUserID)) : null;
                        String name = sender != null ? Utils.getDisplayName(getBaseContext(), sender) : "";
                        this.ttsWrapper.speak(getString(R.string.text_tts_user_typing, name));
                    }
                }
            }
            return;
        }

        MyTextMessage completemsg = MyTextMessage.mergeMessage(this.txtmsgMergeBuffer, new MyTextMessage(textmessage, ""));
        if (completemsg == null) {
            return;
        }

        switch (completemsg.nMsgType) {
            case 1:
                if (this.sounds.get(3) != 0) {
                    this.audioIcons.play(this.sounds.get(3), 1.0f, 1.0f, 0, 0, 1.0f);
                }
                User sender = getService() != null ? getService().getUsers().get(Integer.valueOf(completemsg.nFromUserID)) : null;
                String name = Utils.getDisplayName(getBaseContext(), sender);
                if (this.ttsWrapper != null && ((Boolean) this.prefs.get("private_message_checkbox", false)).booleanValue()) {
                    this.ttsWrapper.speak(getString(R.string.text_tts_private_message, new Object[]{name, completemsg.szMessage}));
                }
                Intent action = new Intent(this, (Class<?>) TextMessageActivity.class);
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel mChannel = new NotificationChannel(MSG_NOTIFICATION_CHANNEL_ID, "Teamtalk incoming message", 4);
                    mChannel.enableVibration(false);
                    mChannel.setVibrationPattern(null);
                    mChannel.enableLights(false);
                    mChannel.setSound(null, null);
                    this.notificationManager.createNotificationChannel(mChannel);
                }
                Notification notification = new NotificationCompat.Builder(this, MSG_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.message)
                        .setContentTitle(getString(R.string.private_message_notification, new Object[]{name}))
                        .setContentText(getString(R.string.private_message_notification_hint))
                        .setContentIntent(PendingIntent.getActivity(this, completemsg.nFromUserID, action.putExtra("userid", completemsg.nFromUserID), AccessibilityEventCompat.TYPE_VIEW_TARGETED_BY_SCROLL))
                        .setAutoCancel(true)
                        .build();
                this.notificationManager.notify(MESSAGE_NOTIFICATION_TAG, completemsg.nFromUserID, notification);
                return;
            case 2:
                this.accessibilityAssistant.lockEvents();
                this.textmsgAdapter.notifyDataSetChanged();
                this.accessibilityAssistant.unlockEvents();
                if (completemsg.nFromUserID != getService().getTTInstance().getMyUserID()) {
                    if (this.sounds.get(4) != 0) {
                        this.audioIcons.play(this.sounds.get(4), 1.0f, 1.0f, 0, 0, 1.0f);
                    }
                    if (this.ttsWrapper != null && ((Boolean) this.prefs.get("channel_message_checkbox", false)).booleanValue()) {
                        User sender2 = getService() != null ? getService().getUsers().get(Integer.valueOf(completemsg.nFromUserID)) : null;
                        this.ttsWrapper.speak(getString(R.string.text_tts_channel_message, new Object[]{Utils.getDisplayName(getBaseContext(), sender2), completemsg.szMessage}));
                    }
                } else if (completemsg.nFromUserID == getService().getTTInstance().getMyUserID()) {
                    if (this.sounds.get(20) != 0) {
                        this.audioIcons.play(this.sounds.get(20), 1.0f, 1.0f, 0, 0, 1.0f);
                    }
                    if (this.ttsWrapper != null && ((Boolean) this.prefs.get("channel_message_sent_checkbox", false)).booleanValue()) {
                        this.ttsWrapper.speak(getString(R.string.text_tts_channel_message_sent, new Object[]{completemsg.szMessage}));
                    }
                }
                Log.d("bearware", "Channel message in " + hashCode());
                return;
            case 3:
                this.accessibilityAssistant.lockEvents();
                this.textmsgAdapter.notifyDataSetChanged();
                this.accessibilityAssistant.unlockEvents();
                if (this.sounds.get(5) != 0) {
                    this.audioIcons.play(this.sounds.get(5), 1.0f, 1.0f, 0, 0, 1.0f);
                }
                if (this.ttsWrapper != null && ((Boolean) this.prefs.get("broadcast_message_checkbox", false)).booleanValue()) {
                    User sender3 = getService() != null ? getService().getUsers().get(Integer.valueOf(completemsg.nFromUserID)) : null;
                    this.ttsWrapper.speak(getString(R.string.text_tts_broadcast_message, new Object[]{Utils.getDisplayName(getBaseContext(), sender3), completemsg.szMessage}));
                }
                Log.d("bearware", "Broadcast message in " + hashCode());
                return;
            default:
                return;
        }
    }

    @Override
    public void onCmdChannelNew(Channel channel) {
        if (this.curchannel != null && this.curchannel.nChannelID == channel.nParentID) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
    }

    @Override
    public void onCmdChannelUpdate(Channel channel) {
        if (this.curchannel != null && this.curchannel.nChannelID == channel.nParentID) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
        if (this.mychannel != null && this.mychannel.nChannelID == channel.nChannelID) {
            if (this.ttsWrapper != null) {
                Utils.ttsTransmitUsersToggled(getBaseContext(), this.mychannel, channel, getService().getUsers()).ifPresent(this.ttsWrapper::speak);
            }
            int myuserid = getClient().getMyUserID();
            if (channel.transmitUsersQueue[0] == myuserid && this.mychannel.transmitUsersQueue[0] != myuserid && this.sounds.get(12) != 0) {
                this.audioIcons.play(this.sounds.get(12), 1.0f, 1.0f, 0, 0, 1.0f);
            }
            if (this.mychannel.transmitUsersQueue[0] == myuserid && channel.transmitUsersQueue[0] != myuserid && this.sounds.get(13) != 0) {
                this.audioIcons.play(this.sounds.get(13), 1.0f, 1.0f, 0, 0, 1.0f);
            }
            setMyChannel(channel);
        }
    }

        public void lambda$onCmdChannelUpdate$21(String text) {
        this.ttsWrapper.speak(text);
    }

    @Override
    public void onCmdChannelRemove(Channel channel) {
        if (this.curchannel != null && this.curchannel.nChannelID == channel.nParentID) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
    }

    @Override
    public void onCmdFileNew(RemoteFile remotefile) {
        this.filesAdapter.update();
        if (this.activecmds.size() == 0 && getClient().getMyChannelID() == remotefile.nChannelID && this.sounds.get(7) != 0) {
            this.audioIcons.play(this.sounds.get(7), 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    @Override
    public void onCmdFileRemove(RemoteFile remotefile) {
        this.filesAdapter.update();
        if (this.activecmds.size() == 0 && getClient().getMyChannelID() == remotefile.nChannelID && this.sounds.get(7) != 0) {
            this.audioIcons.play(this.sounds.get(7), 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    @Override
    public void onConnectionLost() {
        if (this.sounds.get(6) != 0) {
            this.audioIcons.play(this.sounds.get(6), 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    @Override
    public void onUserStateChange(User user) {
        this.users.put(Integer.valueOf(user.nUserID), user);
        if (this.curchannel != null && user.nChannelID == this.curchannel.nChannelID) {
            this.accessibilityAssistant.lockEvents();
            this.channelsAdapter.notifyDataSetChanged();
            this.accessibilityAssistant.unlockEvents();
        }
    }

    @Override
    public void onVoiceTransmissionToggle(boolean voiceTransmissionEnabled, boolean isSuspended) {
        adjustTxState(voiceTransmissionEnabled);
        if (!isSuspended) {
            boolean ptt_vibrate = ((Boolean) this.prefs.get("vibrate_checkbox", true)).booleanValue() && Permissions.VIBRATE.request(this);
            if (voiceTransmissionEnabled) {
                this.accessibilityAssistant.shutUp();
                if (this.sounds.get(1) != 0) {
                    this.audioIcons.play(this.sounds.get(1), 1.0f, 1.0f, 0, 0, 1.0f);
                }
                if (ptt_vibrate) {
                    Vibrator vibrat = (Vibrator) getSystemService("vibrator");
                    vibrat.vibrate(50L);
                    return;
                }
                return;
            }
            if (this.sounds.get(2) != 0) {
                this.audioIcons.play(this.sounds.get(2), 1.0f, 1.0f, 0, 0, 1.0f);
            }
            if (ptt_vibrate) {
                Vibrator vibrat2 = (Vibrator) getSystemService("vibrator");
                long[] pattern = {0, 20, 80, 20};
                vibrat2.vibrate(pattern, -1);
            }
        }
    }

    @Override
    public void onVoiceActivationToggle(boolean voiceActivationEnabled, boolean isSuspended) {
        TeamTalkBase client = getClient();
        adjustVoxState(voiceActivationEnabled, voiceActivationEnabled ? client.getVoiceActivationLevel() : client.getSoundInputGainLevel());
        SparseIntArray sparseIntArray = this.sounds;
        if (voiceActivationEnabled) {
            if (sparseIntArray.get(8) != 0) {
                this.audioIcons.play(this.sounds.get(8), 1.0f, 1.0f, 0, 0, 1.0f);
            }
        } else if (sparseIntArray.get(9) != 0) {
            this.audioIcons.play(this.sounds.get(9), 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }

    private void showRecordingCompleteDialog(final File recordedFile) {
        String fileName = recordedFile.getName();
        final String nameWithoutExt = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(46)) : fileName;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.recording_rename_title);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(32, 16, 32, 16);
        final EditText nameInput = new EditText(this);
        nameInput.setHint(R.string.recording_rename_hint);
        nameInput.setText(nameWithoutExt);
        nameInput.setSelectAllOnFocus(true);
        linearLayout.addView(nameInput);
        builder.setView(linearLayout);
        builder.setPositiveButton(R.string.recording_rename_save, new DialogInterface.OnClickListener() { 
            @Override
            public final void onClick(DialogInterface dialogInterface, int i3) {
                String newName = nameInput.getText().toString().trim();
                if (newName.isEmpty()) {
                    newName = nameWithoutExt;
                }
                File newFile = new File(recordedFile.getParent(), newName + ".ogg");
                if (newFile.getAbsolutePath().equals(recordedFile.getAbsolutePath())) {
                    return;
                }
                boolean renamed = recordedFile.renameTo(newFile);
                if (renamed) {
                    try {
                        MediaScannerConnection.scanFile(MainActivity.this, new String[]{newFile.getAbsolutePath(), recordedFile.getAbsolutePath()}, null, null);
                    } catch (Exception e) {
                        Log.e("bearware", "Failed to scan renamed file into media store", e);
                    }
                    Toast.makeText(MainActivity.this, getString(R.string.recording_renamed_success, new Object[]{newFile.getName()}), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, R.string.recording_rename_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(R.string.recording_rename_skip, (DialogInterface.OnClickListener) null);
        builder.show();
    }

    @Override
    public void onVoiceActivation(boolean bVoiceActive) {
        adjustTxState(bVoiceActive);
        int sound = this.sounds.get(bVoiceActive ? 10 : 11);
        if (sound != 0) {
            this.audioIcons.play(sound, 1.0f, 1.0f, 0, 0, 1.0f);
        }
    }
}
