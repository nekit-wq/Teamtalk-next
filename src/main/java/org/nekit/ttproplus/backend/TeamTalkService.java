package org.nekit.ttproplus.backend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import dk.bearware.AudioBlock;
import dk.bearware.AudioFileFormat;
import dk.bearware.AudioPreprocessor;
import dk.bearware.Channel;
import org.nekit.ttproplus.audio.AudioConverter;
import dk.bearware.ClientErrorMsg;
import dk.bearware.EncryptionContext;
import dk.bearware.FileTransfer;
import dk.bearware.MediaFileInfo;
import dk.bearware.MediaFilePlayback;
import dk.bearware.OpusConstants;
import dk.bearware.RemoteFile;
import dk.bearware.ServerProperties;
import dk.bearware.SoundLevel;
import dk.bearware.TeamTalk5;
import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.User;
import dk.bearware.UserAccount;
import dk.bearware.events.ClientEventListener;
import dk.bearware.events.TeamTalkEventHandler;
import org.nekit.ttproplus.gui.Utils;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.BluetoothHeadsetHelper;
import org.nekit.ttproplus.data.AppInfo;
import org.nekit.ttproplus.data.MyTextMessage;
import org.nekit.ttproplus.data.Preferences;
import org.nekit.ttproplus.data.ServerEntry;
import org.nekit.ttproplus.data.UserCached;
import org.nekit.ttproplus.gui.CmdComplete;
import org.nekit.ttproplus.gui.FloatingWindowManager;
import org.nekit.ttproplus.gui.MainActivity;
import org.nekit.ttproplus.gui.MediaButtonEventReceiver;
import org.nekit.ttproplus.gui.Utils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class TeamTalkService extends Service implements BluetoothHeadsetHelper.HeadsetConnectionListener, BluetoothHeadsetHelper.ScoAudioConnectionListener, ClientEventListener.OnConnectSuccessListener, ClientEventListener.OnConnectFailedListener, ClientEventListener.OnConnectionLostListener, ClientEventListener.OnEncryptionErrorListener, ClientEventListener.OnCmdSuccessListener, ClientEventListener.OnCmdProcessingListener, ClientEventListener.OnCmdMyselfLoggedInListener, ClientEventListener.OnCmdMyselfKickedFromChannelListener, ClientEventListener.OnCmdErrorListener, ClientEventListener.OnCmdUserLoggedInListener, ClientEventListener.OnCmdUserLoggedOutListener, ClientEventListener.OnCmdUserUpdateListener, ClientEventListener.OnCmdUserJoinedChannelListener, ClientEventListener.OnCmdUserLeftChannelListener, ClientEventListener.OnCmdUserTextMessageListener, ClientEventListener.OnCmdChannelNewListener, ClientEventListener.OnCmdChannelRemoveListener, ClientEventListener.OnCmdServerUpdateListener, ClientEventListener.OnCmdChannelUpdateListener, ClientEventListener.OnCmdFileNewListener, ClientEventListener.OnCmdFileRemoveListener, ClientEventListener.OnUserStateChangeListener, ClientEventListener.OnVoiceActivationListener, ClientEventListener.OnFileTransferListener, ClientEventListener.OnStreamMediaFileListener {
    static final boolean $assertionsDisabled = false;
    private static final long BLUETOOTH_SCO_RECONNECT_DELAY_MS = 500;
    public static final String CANCEL_TRANSFER = "cancel_transfer";
    public static final String TAG = "bearware";
    private static final String UI_CHANNEL_ID = "TeamtalkConnection";
    private static final int UI_WIDGET_ID = 1;
    private BluetoothHeadsetHelper bluetoothHeadsetHelper;
    private boolean currentMuteState;
    CountDownTimer eventTimer;
    private volatile boolean inPhoneCall;
    private AudioRecord internalAudioRecord;
    private Thread internalAudioThread;
    Channel joinchannel;
    private boolean listeningPhoneStateChanges;
    private FloatingWindowManager mFloatingWindowManager;
    private MediaProjection mediaProjection;
    private MediaSessionCompat mediaSession;
    private AudioRecord micAudioRecord;
    private PowerManager.WakeLock mServiceWakeLock;
    private WifiManager.WifiLock mServiceWifiLock;
    Channel mychannel;
    private NotificationManager notificationManager;
    OnVoiceTransmissionToggleListener onVoiceTransmissionToggleListener;
    private boolean permanentMuteState;
    private Runnable reconnectBluetoothScoAfterCall;
    private TelephonyManager telephonyManager;
    TeamTalkBase ttclient;
    ServerEntry ttserver;
    private boolean txSuspended;
    private boolean voxSuspended;
    private boolean pendingAutoRecord = false;
    private static int mediaProjectionResultCode = 0;
    private static Intent mediaProjectionData = null;
    private final IBinder mBinder = new LocalBinder();
    private Notification widget = null;
    Handler reconnectHandler = new Handler();
    Runnable reconnectTimer = new Runnable() { 
        @Override
        public final void run() {
            TeamTalkService.this.reconnect();
        }
    };
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network activeNetwork = null;
    private volatile boolean isSeamlessReconnecting = false;
    private final Runnable seamlessReconnectRunnable = new Runnable() { 
        @Override
        public final void run() {
            TeamTalkService.this.performSeamlessReconnect();
        }
    };
    private final TeamTalkEventHandler mEventHandler = new TeamTalkEventHandler();
    SparseArray<CmdComplete> activecmds = new SparseArray<>();
    Map<Integer, Channel> channels = new HashMap();
    Map<Integer, RemoteFile> remoteFiles = new HashMap();
    Map<Integer, FileTransfer> fileTransfers = new HashMap();
    Map<Integer, User> users = new HashMap();
    Map<Integer, Vector<MyTextMessage>> usertxtmsgs = new HashMap();
    Vector<MyTextMessage> chatlogtxtmsgs = new Vector<>();
    Map<String, UserCached> usercache = new HashMap<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() { 
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ((Preferences.PREF_BG_MGMT_ENABLED.equals(key) || Preferences.PREF_BG_MGMT_SHOW_VOICE.equals(key) || Preferences.PREF_BG_MGMT_SHOW_MUTE.equals(key) || Preferences.PREF_BG_MGMT_SHOW_PING.equals(key) || Preferences.PREF_BG_MGMT_SHOW_CHAT.equals(key) || Preferences.PREF_BG_MGMT_SHOW_CHANNELS.equals(key)) && TeamTalkService.this.mFloatingWindowManager != null) {
                TeamTalkService.this.mFloatingWindowManager.checkAndShow();
            }
            if ("eq_mic_ns".equals(key) || "eq_mic_aec".equals(key) || "eq_mic_agc".equals(key) || Preferences.PREF_SOUNDSYSTEM_VOICEPROCESSING.equals(key) || Preferences.PREF_SOUNDSYSTEM_SPEAKERPHONE.equals(key) || Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN.equals(key) || Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL.equals(key)) {
                TeamTalkService.this.applyRealTimeAudioProcessing();
            }
            if (Preferences.PREF_GENERAL_CLIENTNAME.equals(key) || Preferences.PREF_GENERAL_NICKNAME.equals(key)) {
                if (TeamTalkService.this.ttclient != null && (TeamTalkService.this.ttclient.getFlags() & 2) != 0) {
                    TeamTalkService.this.login();
                }
            }
        }
    };
    private long antispam_window_start = 0;
    private int antispam_count = 0;
    private boolean antispam_triggered = false;
    private final HashSet<Integer> antispam_blocked = new HashSet<>();
    private final HashMap<Integer, Integer> antispam_user_counts = new HashMap<>();
    private final MediaSessionCompat.Callback mMediaSessionCallback = new MediaSessionCompat.Callback() { 
        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
            KeyEvent event;
            super.onMediaButtonEvent(mediaButtonEvent);
            String intentAction = mediaButtonEvent.getAction();
            if (!"android.intent.action.MEDIA_BUTTON".equals(intentAction) || (event = (KeyEvent) mediaButtonEvent.getParcelableExtra("android.intent.extra.KEY_EVENT")) == null) {
                return false;
            }
            int keycode = event.getKeyCode();
            int action = event.getAction();
            if (event.getRepeatCount() == 0 && action == 0) {
                switch (keycode) {
                    case 79:
                    case 85:
                    case 126:
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        boolean isVoiceActivationEnabled = TeamTalkService.this.isVoiceActivationEnabled();
                        TeamTalkService teamTalkService = TeamTalkService.this;
                        if (!isVoiceActivationEnabled) {
                            teamTalkService.enableVoiceTransmission(!TeamTalkService.this.isVoiceTransmissionEnabled());
                            break;
                        } else {
                            teamTalkService.enableVoiceActivation(false);
                            break;
                        }
                }
                return true;
            }
            return false;
        }
    };
    private final PhoneStateListener phoneStateListener = new PhoneStateListener() { 
        int myStatus = 0;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            User myself = TeamTalkService.this.users.get(Integer.valueOf(TeamTalkService.this.ttclient.getMyUserID()));
            if (myself == null) {
                return;
            }
            switch (state) {
                case 0:
                    boolean z = TeamTalkService.this.voxSuspended;
                    TeamTalkService teamTalkService = TeamTalkService.this;
                    if (z) {
                        teamTalkService.enableVoiceActivation(true);
                    } else if (teamTalkService.txSuspended) {
                        TeamTalkService.this.enableVoiceTransmission(true);
                    }
                    TeamTalkService.this.setMute(TeamTalkService.this.permanentMuteState);
                    if (myself != null && (1 & this.myStatus) == 0) {
                        TeamTalkService.this.ttclient.doChangeStatus(myself.nStatusMode & (-2), myself.szStatusMsg);
                    }
                    TeamTalkService.this.inPhoneCall = false;
                    TeamTalkService.this.scheduleReconnectBluetoothScoAfterCall();
                    return;
                case 1:
                    TeamTalkService.this.inPhoneCall = true;
                    if (!TeamTalkService.this.isMute()) {
                        TeamTalkService.this.ttclient.setSoundOutputMute(true);
                        TeamTalkService.this.currentMuteState = true;
                    }
                    boolean isVoiceActivationEnabled = TeamTalkService.this.isVoiceActivationEnabled();
                    TeamTalkService teamTalkService2 = TeamTalkService.this;
                    if (isVoiceActivationEnabled) {
                        teamTalkService2.voxSuspended = true;
                        TeamTalkService.this.enableVoiceActivation(false);
                    } else if (teamTalkService2.isVoiceTransmissionEnabled()) {
                        TeamTalkService.this.txSuspended = true;
                        TeamTalkService.this.enableVoiceTransmission(false);
                    }
                    this.myStatus = myself.nStatusMode;
                    if ((this.myStatus & 1) == 0) {
                        TeamTalkService.this.ttclient.doChangeStatus(1 | this.myStatus, myself.szStatusMsg);
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    };
    private boolean isRecording = false;
    private volatile boolean manualDisconnect = false;
    private File currentRecordingFile = null;
    private File targetRecordingFile = null;
    private boolean isMp3Recording = false;
    private int targetMp3Bitrate = 128;
    private boolean isInternalAudioRunning = false;
    private String currentStreamPath = "";
    private boolean isStreamingMedia = false;
    private int localPlaybackId = 0;
    private MediaFileInfo currentMediaFileInfo = null;
    private MediaFilePlayback currentPlayback = null;
    public int HISTORY_CHATLOG_MSG_MAX = 100;
    public int HISTORY_USER_MSG_MAX = 100;

    public void updateFloatingWindow() {
        if (this.mFloatingWindowManager != null) {
            this.mFloatingWindowManager.updateUI();
        }
    }

    public FloatingWindowManager getFloatingWindowManager() {
        return this.mFloatingWindowManager;
    }

    public void resetState() {
        this.manualDisconnect = true;
        this.isSeamlessReconnecting = false;
        this.reconnectHandler.removeCallbacks(this.seamlessReconnectRunnable);
        this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        this.antispam_triggered = false;
        this.antispam_count = 0;
        this.antispam_window_start = 0L;
        this.antispam_blocked.clear();
        this.antispam_user_counts.clear();
        disablePhoneCallReaction();
        unwatchBluetoothHeadset();
        if (isRecording()) {
            stopRecording();
        }
        syncToUserCache();
        if (this.ttclient != null) {
            this.ttclient.closeSoundInputDevice();
            this.ttclient.closeSoundOutputDevice();
            this.ttclient.disconnect();
        }
        this.ttserver = null;
        displayNotification(false);
        releaseServiceLocks();
        this.joinchannel = null;
        setMyChannel(null);
        this.activecmds.clear();
        this.channels.clear();
        this.remoteFiles.clear();
        this.fileTransfers.clear();
        this.users.clear();
        this.usertxtmsgs.clear();
        this.chatlogtxtmsgs.clear();
        updateFloatingWindow();
    }

    public Map<Integer, Channel> getChannels() {
        return this.channels;
    }

    public Map<Integer, RemoteFile> getRemoteFiles() {
        return this.remoteFiles;
    }

    public Map<Integer, FileTransfer> getFileTransfers() {
        return this.fileTransfers;
    }

    public Map<Integer, User> getUsers() {
        return this.users;
    }

        public class LocalBinder extends Binder {
        public LocalBinder() {
        }

        public TeamTalkService getService() {
            return TeamTalkService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        TeamTalk5.loadLibrary();
        TeamTalk5.setLicenseInformation("", "");
        this.ttclient = new TeamTalk5();
        this.telephonyManager = (TelephonyManager) getSystemService("phone");
        this.listeningPhoneStateChanges = false;
        this.txSuspended = false;
        this.voxSuspended = false;
        this.permanentMuteState = false;
        this.currentMuteState = false;
        this.inPhoneCall = false;
        this.mEventHandler.registerOnConnectSuccessListener(this, true);
        this.mEventHandler.registerOnConnectFailedListener(this, true);
        this.mEventHandler.registerOnConnectionLostListener(this, true);
        this.mEventHandler.registerOnEncryptionErrorListener(this, true);
        this.mEventHandler.registerOnCmdError(this, true);
        this.mEventHandler.registerOnCmdSuccess(this, true);
        this.mEventHandler.registerOnCmdProcessing(this, true);
        this.mEventHandler.registerOnCmdMyselfLoggedIn(this, true);
        this.mEventHandler.registerOnCmdMyselfKickedFromChannel(this, true);
        this.mEventHandler.registerOnCmdUserLoggedIn(this, true);
        this.mEventHandler.registerOnCmdUserLoggedOut(this, true);
        this.mEventHandler.registerOnCmdUserUpdate(this, true);
        this.mEventHandler.registerOnCmdUserJoinedChannel(this, true);
        this.mEventHandler.registerOnCmdUserLeftChannel(this, true);
        this.mEventHandler.registerOnCmdUserTextMessage(this, true);
        this.mEventHandler.registerOnCmdChannelNew(this, true);
        this.mEventHandler.registerOnCmdChannelUpdate(this, true);
        this.mEventHandler.registerOnCmdChannelRemove(this, true);
        this.mEventHandler.registerOnCmdServerUpdate(this, true);
        this.mEventHandler.registerOnCmdFileNew(this, true);
        this.mEventHandler.registerOnCmdFileRemove(this, true);
        this.mEventHandler.registerOnUserStateChange(this, true);
        this.mEventHandler.registerOnVoiceActivation(this, true);
        this.mEventHandler.registerOnFileTransfer(this, true);
        this.mEventHandler.registerOnStreamMediaFile(this, true);
        createEventTimer();
        this.bluetoothHeadsetHelper = new BluetoothHeadsetHelper(this);
        this.reconnectBluetoothScoAfterCall = new Runnable() { 
            @Override
            public final void run() {
                TeamTalkService.this.reconnectBluetoothScoAfterCallRun();
            }
        };
        new ComponentName(getPackageName(), MediaButtonEventReceiver.class.getName());
        this.mediaSession = new MediaSessionCompat(this, "TeamTalkService");
        this.mediaSession.setFlags(3);
        this.mediaSession.setPlaybackState(new PlaybackStateCompat.Builder().setState(2, 0L, 0.0f).setActions(512L).build());
        this.mediaSession.setCallback(this.mMediaSessionCallback);
        AudioManager audioManager = (AudioManager) getSystemService("audio");
        audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() { 
            @Override
            public final void onAudioFocusChange(int i) {
                TeamTalkService.lambda$onCreate$0(i);
            }
        }, 3, 1);
        this.mediaSession.setActive(true);
        Log.d("bearware", "Created TeamTalk 5 service");
        this.mFloatingWindowManager = new FloatingWindowManager(this);
        this.mFloatingWindowManager.checkAndShow();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(this.mPrefListener);
        acquireServiceLocks();
        registerNetworkCallback();
    }

        public static void lambda$onCreate$0(int focusChange) {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(CANCEL_TRANSFER)) {
            int transferId = intent.getIntExtra(CANCEL_TRANSFER, 0);
            if (this.ttclient != null && this.ttclient.cancelFileTransfer(transferId)) {
                this.fileTransfers.remove(Integer.valueOf(transferId));
                Toast.makeText(this, R.string.transfer_stopped, 1).show();
            }
        }
        int state = this.mediaSession.getController().getPlaybackState().getState();
        MediaSessionCompat mediaSessionCompat = this.mediaSession;
        if (state == 3) {
            mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder().setState(2, 0L, 0.0f).setActions(512L).build());
        } else {
            mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder().setState(3, 0L, 1.0f).setActions(512L).build());
        }
        acquireServiceLocks();
        return 1;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    @Override
    public void onDestroy() {
        this.manualDisconnect = true;
        this.isSeamlessReconnecting = false;
        this.reconnectHandler.removeCallbacks(this.seamlessReconnectRunnable);
        unregisterNetworkCallback();
        this.eventTimer.cancel();
        this.mEventHandler.unregisterListener(this);
        this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        disablePhoneCallReaction();
        unwatchBluetoothHeadset();
        releaseServiceLocks();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this.mPrefListener);
        if (this.mFloatingWindowManager != null) {
            this.mFloatingWindowManager.hide();
            this.mFloatingWindowManager = null;
        }
        if (this.ttclient != null) {
            this.ttclient.closeSoundInputDevice();
            this.ttclient.closeSoundOutputDevice();
            this.ttclient.closeTeamTalk();
        }
        displayNotification(false);
        super.onDestroy();
        this.mediaSession.release();
        Log.d("bearware", "Destroyed TeamTalk 5 service");
    }

    private String getNotificationText() {
        Channel channel = this.mychannel;
        ServerEntry serverEntry = this.ttserver;
        if (serverEntry == null) {
            return getString(R.string.app_name);
        }
        return channel != null ? String.format("%s / %s", serverEntry.servername, this.mychannel.szName) : serverEntry.servername;
    }

    private void displayNotification(boolean enabled) {
        Notification notification = this.widget;
        if (enabled) {
            if (this.ttserver == null) {
                return;
            }
            if (notification == null) {
                this.notificationManager = (NotificationManager) getSystemService("notification");
                Intent ui = new Intent(this, (Class<?>) MainActivity.class);
                ui.setFlags(131072);
                if (Build.VERSION.SDK_INT >= 26) {
                    NotificationChannel mChannel = new NotificationChannel(UI_CHANNEL_ID, "Teamtalk connection", 3);
                    mChannel.enableVibration(false);
                    mChannel.setVibrationPattern(null);
                    mChannel.enableLights(false);
                    mChannel.setSound(null, null);
                    this.notificationManager.createNotificationChannel(mChannel);
                }
                this.widget = new NotificationCompat.Builder(this, UI_CHANNEL_ID).setSmallIcon(R.drawable.teamtalk_green).setContentTitle(getString(R.string.app_name)).setContentIntent(PendingIntent.getActivity(this, 0, ui, 201326592)).setOngoing(true).setAutoCancel(false).setContentText(getNotificationText()).setShowWhen(false).build();
                ServiceCompat.startForeground(this, 1, this.widget, getMyForegroundServiceType());
                return;
            }
            this.widget = new NotificationCompat.Builder(this, this.widget).setContentText(getNotificationText()).build();
            this.notificationManager.notify(1, this.widget);
            return;
        }
        if (this.notificationManager != null) {
            this.notificationManager.cancel(1);
        }
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        this.widget = null;
    }

    private int getMyForegroundServiceType() {
        int type = 128 | 2; // FOREGROUND_SERVICE_TYPE_MICROPHONE | FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        if (this.mediaProjection != null) {
            type |= 32; // FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        return type;
    }

    public synchronized void acquireServiceLocks() {
        try {
            if (this.mServiceWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    this.mServiceWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ttproplus:ServiceWakeLock");
                    this.mServiceWakeLock.setReferenceCounted(false);
                }
            }
            if (this.mServiceWakeLock != null && !this.mServiceWakeLock.isHeld()) {
                this.mServiceWakeLock.acquire();
            }

            if (this.mServiceWifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    this.mServiceWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ttproplus:ServiceWifiLock");
                    this.mServiceWifiLock.setReferenceCounted(false);
                }
            }
            if (this.mServiceWifiLock != null && !this.mServiceWifiLock.isHeld()) {
                this.mServiceWifiLock.acquire();
            }
        } catch (Exception e) {
            Log.w("bearware", "Failed to acquire service locks", e);
        }
    }

    public synchronized void releaseServiceLocks() {
        try {
            if (this.mServiceWakeLock != null && this.mServiceWakeLock.isHeld()) {
                this.mServiceWakeLock.release();
            }
            if (this.mServiceWifiLock != null && this.mServiceWifiLock.isHeld()) {
                this.mServiceWifiLock.release();
            }
        } catch (Exception e) {
            Log.w("bearware", "Failed to release service locks", e);
        }
    }

    private void adjustMuteOnTx(boolean txEnabled) {
        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(Preferences.PREF_SOUNDSYSTEM_MUTE_ON_TRANSMISSION, false)) {
            boolean isMuted = isMute();
            if ((txEnabled && !isMuted) || (isMuted && !txEnabled && !this.permanentMuteState)) {
                this.ttclient.setSoundOutputMute(txEnabled);
            }
        }
    }

    public void enablePhoneCallReaction() {
        this.txSuspended = false;
        this.voxSuspended = false;
        this.inPhoneCall = false;
        this.telephonyManager.listen(this.phoneStateListener, 32);
        this.listeningPhoneStateChanges = true;
    }

    public void disablePhoneCallReaction() {
        if (this.listeningPhoneStateChanges) {
            this.telephonyManager.listen(this.phoneStateListener, 0);
            this.listeningPhoneStateChanges = false;
        }
        this.txSuspended = false;
        this.voxSuspended = false;
        this.inPhoneCall = false;
    }

    public boolean isInPhoneCall() {
        return this.inPhoneCall;
    }

    public void watchBluetoothHeadset() {
        if (this.bluetoothHeadsetHelper.start()) {
            if (this.bluetoothHeadsetHelper.isHeadsetConnected()) {
                this.bluetoothHeadsetHelper.scoAudioConnect();
            }
            this.bluetoothHeadsetHelper.registerHeadsetConnectionListener(this);
            this.bluetoothHeadsetHelper.registerScoAudioConnectionListener(this);
        }
    }

    public void unwatchBluetoothHeadset() {
        this.reconnectHandler.removeCallbacks(this.reconnectBluetoothScoAfterCall);
        this.bluetoothHeadsetHelper.unregisterScoAudioConnectionListener(this);
        this.bluetoothHeadsetHelper.unregisterHeadsetConnectionListener(this);
        this.bluetoothHeadsetHelper.stop();
    }

        public void scheduleReconnectBluetoothScoAfterCall() {
        this.reconnectHandler.removeCallbacks(this.reconnectBluetoothScoAfterCall);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_BLUETOOTH_HEADSET, false) || this.bluetoothHeadsetHelper == null || !this.bluetoothHeadsetHelper.isStarted()) {
            return;
        }
        this.reconnectHandler.postDelayed(this.reconnectBluetoothScoAfterCall, BLUETOOTH_SCO_RECONNECT_DELAY_MS);
    }

        public void reconnectBluetoothScoAfterCallRun() {
        if (this.bluetoothHeadsetHelper != null && this.bluetoothHeadsetHelper.isStarted() && this.bluetoothHeadsetHelper.isHeadsetConnected() && !this.bluetoothHeadsetHelper.isOnHeadsetSco()) {
            this.bluetoothHeadsetHelper.scoAudioConnect();
        }
    }

    private int getPreferredSoundInputDeviceId() {
        if (shouldUseBluetoothVoiceCom()) {
            return 1;
        }
        return 0;
    }

    private boolean shouldUseBluetoothVoiceCom() {
        if (this.bluetoothHeadsetHelper == null || !this.bluetoothHeadsetHelper.isStarted()) {
            return false;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_BLUETOOTH_HEADSET, false) && this.bluetoothHeadsetHelper.isHeadsetConnected() && this.bluetoothHeadsetHelper.isOnHeadsetSco();
    }

    private void reinitSoundInputDevice() {
        if (this.ttclient == null) {
            return;
        }
        boolean tx = (this.ttclient.getFlags() & 256) != 0;
        boolean vox = (this.ttclient.getFlags() & 24) != 0;
        if (tx || vox) {
            this.ttclient.closeSoundInputDevice();
            int indevid = getPreferredSoundInputDeviceId();
            if (this.ttclient.initSoundInputDevice(indevid)) {
                applyRealTimeAudioProcessing();
                if (tx) {
                    this.ttclient.enableVoiceTransmission(true);
                }
                if (vox) {
                    this.ttclient.enableVoiceActivation(true);
                }
            }
        }
    }

    public boolean isRecording() {
        return this.isRecording;
    }

    public File getCurrentRecordingFile() {
        return this.currentRecordingFile;
    }

        private void showRecordingToast(int resId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getBoolean(Preferences.PREF_RECORDING_SHOW_TOAST, true)) {
            Toast.makeText(getApplicationContext(), resId, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRecordingToast(String message) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getBoolean(Preferences.PREF_RECORDING_SHOW_TOAST, true)) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    public void checkAndTriggerAutoRecord() {
        if (this.pendingAutoRecord && !this.isRecording && this.mychannel != null) {
            this.pendingAutoRecord = false;
            startRecording();
        }
    }

    public void startRecording() {
        this.pendingAutoRecord = false;
        if (this.isRecording || this.mychannel == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        File dir = Utils.getRecordingsDirectory(getApplicationContext());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String format = prefs.getString(Preferences.PREF_RECORDING_FORMAT, "wav");
        String extension;
        int audioFormat;
        this.isMp3Recording = "mp3".equalsIgnoreCase(format);

        if (this.isMp3Recording) {
            String bitrateStr = prefs.getString(Preferences.PREF_RECORDING_MP3_BITRATE, "128");
            try {
                this.targetMp3Bitrate = Integer.parseInt(bitrateStr);
            } catch (Exception e) {
                this.targetMp3Bitrate = 128;
            }
            extension = ".mp3";
            audioFormat = AudioFileFormat.AFF_WAVE_FORMAT;
        } else if ("codec".equalsIgnoreCase(format)) {
            extension = ".ogg";
            audioFormat = AudioFileFormat.AFF_CHANNELCODEC_FORMAT;
        } else {
            extension = ".wav";
            audioFormat = AudioFileFormat.AFF_WAVE_FORMAT;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        String baseName = this.mychannel.szName.replaceAll("[^a-zA-Z0-9_-]", "_") + "_" + sdf.format(new Date());

        File actualFileToRecord;
        if (this.isMp3Recording) {
            this.targetRecordingFile = new File(dir, baseName + ".mp3");
            actualFileToRecord = new File(dir, ".temp_" + System.currentTimeMillis() + "_" + baseName + ".wav");
        } else {
            actualFileToRecord = new File(dir, baseName + extension);
            this.targetRecordingFile = actualFileToRecord;
        }

        this.currentRecordingFile = actualFileToRecord;
        this.isRecording = this.ttclient.startRecordingMuxedAudioFile(this.mychannel.audiocodec, actualFileToRecord.getAbsolutePath(), audioFormat);

        if (this.isRecording) {
            Log.d("bearware", "Recording started: " + actualFileToRecord.getAbsolutePath() + " (Target: " + this.targetRecordingFile.getName() + ")");
            showRecordingToast(getString(R.string.recording_started, new Object[]{this.targetRecordingFile.getName()}));
        } else {
            Log.e("bearware", "Failed to start recording");
            showRecordingToast(R.string.recording_start_failed);
            this.currentRecordingFile = null;
            this.targetRecordingFile = null;
            this.isMp3Recording = false;
        }
    }

    public File stopRecording() {
        if (!this.isRecording) {
            return null;
        }
        this.ttclient.stopRecordingMuxedAudioFile();
        this.isRecording = false;
        File rawRecordedFile = this.currentRecordingFile;
        File finalFile = this.targetRecordingFile;
        boolean wasMp3 = this.isMp3Recording;

        this.currentRecordingFile = null;
        this.targetRecordingFile = null;
        this.isMp3Recording = false;

        if (rawRecordedFile == null || !rawRecordedFile.exists()) {
            Log.e("bearware", "Raw recording file does not exist");
            return null;
        }

        if (rawRecordedFile.length() <= 1024) {
            Log.d("bearware", "Discarding empty recording (" + rawRecordedFile.length() + " bytes): " + rawRecordedFile.getName());
            rawRecordedFile.delete();
            if (finalFile != null && finalFile.exists() && !finalFile.equals(rawRecordedFile)) {
                finalFile.delete();
            }
            return null;
        }

        File resultFile = rawRecordedFile;
        if (wasMp3 && finalFile != null) {
            Log.i("bearware", "Converting raw WAV (" + rawRecordedFile.length() + " bytes) to MP3 (" + this.targetMp3Bitrate + " kbps)...");
            boolean converted = AudioConverter.convertWavToMp3(rawRecordedFile, finalFile, this.targetMp3Bitrate);
            if (converted && finalFile.exists() && finalFile.length() > 0) {
                rawRecordedFile.delete();
                resultFile = finalFile;
                Log.i("bearware", "MP3 recording saved successfully: " + finalFile.getAbsolutePath() + " (" + finalFile.length() + " bytes)");
            } else {
                Log.e("bearware", "MP3 conversion failed, preserving original WAV recording");
                File fallbackWav = new File(rawRecordedFile.getParentFile(), rawRecordedFile.getName().replace(".temp_", "").replace(".wav", "") + ".wav");
                if (rawRecordedFile.renameTo(fallbackWav)) {
                    resultFile = fallbackWav;
                }
            }
        }

        try {
            MediaScannerConnection.scanFile(getApplicationContext(), new String[]{resultFile.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Log.e("bearware", "Failed to scan recorded file into media store", e);
        }

        Log.d("bearware", "Recording stopped: " + resultFile.getName());
        showRecordingToast(R.string.recording_stopped);
        return resultFile;
    }

    public boolean shouldShowRecordingDialog() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(Preferences.PREF_RECORDING_SHOW_DIALOG, true);
    }

    private void setMyChannel(Channel chan) {
        this.mychannel = chan;
        setupAudioPreprocessor();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (chan != null) {
            if (prefs.getBoolean(Preferences.PREF_RECORDING_AUTO, false)) {
                boolean someoneTalking = false;
                if (this.users != null) {
                    for (User u : this.users.values()) {
                        if (u != null && u.nChannelID == chan.nChannelID && (u.uUserState & 1) != 0) {
                            someoneTalking = true;
                            break;
                        }
                    }
                }
                if (someoneTalking) {
                    this.pendingAutoRecord = false;
                    startRecording();
                } else {
                    this.pendingAutoRecord = true;
                }
            } else {
                this.pendingAutoRecord = false;
            }
            return;
        }
        this.pendingAutoRecord = false;
        stopRecording();
    }

    public TeamTalkBase getTTInstance() {
        return this.ttclient;
    }

    public TeamTalkEventHandler getEventHandler() {
        return this.mEventHandler;
    }

    public ServerEntry getServerEntry() {
        return this.ttserver;
    }

    public void setServerEntry(ServerEntry entry) {
        this.ttserver = entry;
        if (entry != null) {
            this.manualDisconnect = false;
        }
    }

    public void setJoinChannel(Channel channel) {
        this.joinchannel = channel;
    }

    public void setOnVoiceTransmissionToggleListener(OnVoiceTransmissionToggleListener listener) {
        this.onVoiceTransmissionToggleListener = listener;
    }

    public boolean getCurrentMuteState() {
        return this.currentMuteState;
    }

    public boolean isMute() {
        return (this.ttclient.getFlags() & 32) != 0;
    }

    public boolean isVoiceTransmissionEnabled() {
        return (this.ttclient.getFlags() & 256) != 0;
    }

    public boolean isVoiceTransmitting() {
        int flags = this.ttclient.getFlags();
        return (flags & 256) != 0 || (flags & 24) == 24;
    }

    public boolean isVoiceActivationEnabled() {
        return (this.ttclient.getFlags() & 24) != 0;
    }

    public void setMute(boolean state) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        this.permanentMuteState = state;
        this.currentMuteState = state;
        if (isMute() != this.permanentMuteState && (!prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_MUTE_ON_TRANSMISSION, false) || !isVoiceTransmitting())) {
            this.ttclient.setSoundOutputMute(this.permanentMuteState);
        }
        updateFloatingWindow();
    }

    public void enableVoiceTransmission(boolean enable) {
        if (enable) {
            checkAndTriggerAutoRecord();
        }
        String inputSource = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Preferences.PREF_SOUNDSYSTEM_INPUT_SOURCE, "mic");
        boolean useInternal = "internal".equals(inputSource) || "mixed".equals(inputSource);
        if (enable) {
            this.txSuspended = false;
            this.voxSuspended = false;
            if (useInternal) {
                this.ttclient.enableVoiceTransmission(true);
                startInternalAudioCapture();
            } else {
                int indevid = getPreferredSoundInputDeviceId();
                if ((this.ttclient.getFlags() & 1) != 0 || this.ttclient.initSoundInputDevice(indevid)) {
                    applyRealTimeAudioProcessing();
                    this.ttclient.enableVoiceTransmission(true);
                }
            }
        } else {
            if (useInternal) {
                stopInternalAudioCapture();
            }
            this.ttclient.enableVoiceTransmission(false);
            this.ttclient.closeSoundInputDevice();
        }
        adjustMuteOnTx(enable);
        updateFloatingWindow();
    }

        public static void mixPcm(byte[] buffer1, byte[] buffer2, byte[] outBuffer, int length) {
        for (int i = 0; i < length; i += 2) {
            short s1 = (short) ((buffer1[i] & 0xFF) | (buffer1[i + 1] << 8));
            short s2 = (short) ((buffer2[i] & 0xFF) | (buffer2[i + 1] << 8));
            int mixed = s1 + s2;
            if (mixed > 32767) {
                mixed = 32767;
            } else if (mixed < -32768) {
                mixed = -32768;
            }
            outBuffer[i] = (byte) (mixed & 255);
            outBuffer[i + 1] = (byte) ((mixed >> 8) & 255);
        }
    }

    public void setMediaProjectionData(int resultCode, Intent data) {
        mediaProjectionResultCode = resultCode;
        mediaProjectionData = data;
    }

    public static boolean hasMediaProjectionData() {
        return mediaProjectionData != null;
    }

    private void startInternalAudioCapture() {
        if (this.isInternalAudioRunning) {
            return;
        }
        if (mediaProjectionData == null) {
            Log.e("bearware", "No media projection data available");
            return;
        }
        this.isInternalAudioRunning = true;
        this.internalAudioThread = new Thread(new Runnable() { 
            @Override
            public void run() {
                byte[] micBuffer;
                MediaProjectionManager projectionManager;
                AudioPlaybackCaptureConfiguration config;
                byte[] finalBuffer;
                int finalRead;
                MediaProjectionManager projectionManager2 = (MediaProjectionManager) TeamTalkService.this.getSystemService("media_projection");
                if (projectionManager2 == null) {
                    return;
                }
                if (TeamTalkService.this.mediaProjection == null) {
                    try {
                        TeamTalkService.this.mediaProjection = projectionManager2.getMediaProjection(TeamTalkService.mediaProjectionResultCode, (Intent) TeamTalkService.mediaProjectionData.clone());
                    } catch (Exception e) {
                        Log.e("bearware", "Failed to get MediaProjection", e);
                        TeamTalkService.this.isInternalAudioRunning = false;
                        return;
                    }
                }
                if (TeamTalkService.this.mediaProjection == null) {
                    TeamTalkService.this.isInternalAudioRunning = false;
                    return;
                }
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        try {
                            AudioPlaybackCaptureConfiguration config2 = new AudioPlaybackCaptureConfiguration.Builder(TeamTalkService.this.mediaProjection).addMatchingUsage(1).addMatchingUsage(14).addMatchingUsage(0).build();
                            int minBufSize = AudioRecord.getMinBufferSize(OpusConstants.DEFAULT_OPUS_SAMPLERATE, 16, 2);
                            if (minBufSize < 3840) {
                                minBufSize = 3840;
                            }
                            TeamTalkService.this.internalAudioRecord = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setEncoding(2).setSampleRate(OpusConstants.DEFAULT_OPUS_SAMPLERATE).setChannelMask(16).build()).setAudioPlaybackCaptureConfig(config2).setBufferSizeInBytes(minBufSize).build();
                            TeamTalkService.this.internalAudioRecord.startRecording();
                            boolean mixMic = "mixed".equals(PreferenceManager.getDefaultSharedPreferences(TeamTalkService.this.getApplicationContext()).getString(Preferences.PREF_SOUNDSYSTEM_INPUT_SOURCE, "mic"));
                            if (mixMic) {
                                try {
                                    int micMinBuf = AudioRecord.getMinBufferSize(OpusConstants.DEFAULT_OPUS_SAMPLERATE, 16, 2);
                                    if (micMinBuf < 3840) {
                                        micMinBuf = 3840;
                                    }
                                    try {
                                        TeamTalkService.this.micAudioRecord = new AudioRecord(1, OpusConstants.DEFAULT_OPUS_SAMPLERATE, 16, 2, micMinBuf);
                                        TeamTalkService.this.micAudioRecord.startRecording();
                                    } catch (Exception e2) {
                                        Log.e("bearware", "Failed to start microphone for mixed mode", e2);
                                        mixMic = false;
                                    }
                                } catch (IllegalArgumentException e) {
                                    Log.e("bearware", "Error recording internal audio", e);
                                    TeamTalkService.this.stopInternalAudioCapture();
                                    return;
                                } catch (SecurityException e) {
                                    Log.e("bearware", "Error recording internal audio", e);
                                    TeamTalkService.this.stopInternalAudioCapture();
                                    return;
                                } catch (Throwable th) {
                                    TeamTalkService.this.stopInternalAudioCapture();
                                    throw th;
                                }
                            }
                            byte[] buffer = new byte[960 * 2];
                            byte[] bArr = null;
                            if (mixMic) {
                                micBuffer = new byte[960 * 2];
                            } else {
                                micBuffer = null;
                            }
                            if (mixMic) {
                                bArr = new byte[960 * 2];
                            }
                            byte[] mixedBuffer = bArr;
                            int sampleIndex = 0;
                            while (TeamTalkService.this.isInternalAudioRunning) {
                                byte[] finalBuffer2 = null;
                                int finalRead2 = 0;
                                if (!mixMic || TeamTalkService.this.micAudioRecord == null) {
                                    projectionManager = projectionManager2;
                                    config = config2;
                                    int read = TeamTalkService.this.internalAudioRecord.read(buffer, 0, buffer.length);
                                    if (read <= 0) {
                                        finalBuffer = null;
                                        finalRead = 0;
                                    } else {
                                        finalBuffer = buffer;
                                        finalRead = read;
                                    }
                                } else {
                                    projectionManager = projectionManager2;
                                    try {
                                        int micRead = TeamTalkService.this.micAudioRecord.read(micBuffer, 0, micBuffer.length);
                                        if (micRead <= 0) {
                                            config = config2;
                                        } else {
                                            finalBuffer2 = micBuffer;
                                            finalRead2 = micRead;
                                            config = config2;
                                            int intRead = TeamTalkService.this.internalAudioRecord.read(buffer, 0, micRead, 1);
                                            if (intRead > 0) {
                                                int mixLen = Math.min(micRead, intRead);
                                                TeamTalkService.mixPcm(buffer, micBuffer, mixedBuffer, mixLen);
                                                finalBuffer2 = mixedBuffer;
                                                finalRead2 = mixLen;
                                            }
                                        }
                                        finalBuffer = finalBuffer2;
                                        finalRead = finalRead2;
                                    } catch (IllegalArgumentException e) {
                                        Log.e("bearware", "Error recording internal audio", e);
                                        TeamTalkService.this.stopInternalAudioCapture();
                                        return;
                                    } catch (SecurityException e) {
                                        Log.e("bearware", "Error recording internal audio", e);
                                        TeamTalkService.this.stopInternalAudioCapture();
                                        return;
                                    }
                                }
                                if (finalRead <= 0 || finalBuffer == null) {
                                    int sampleIndex2 = sampleIndex;
                                    try {
                                        Thread.sleep(10L);
                                        sampleIndex = sampleIndex2;
                                    } catch (InterruptedException e7) {
                                    }
                                } else {
                                    AudioBlock block = new AudioBlock();
                                    block.nStreamID = 0;
                                    block.nSampleRate = OpusConstants.DEFAULT_OPUS_SAMPLERATE;
                                    block.nChannels = 1;
                                    block.lpRawAudio = new byte[finalRead];
                                    System.arraycopy(finalBuffer, 0, block.lpRawAudio, 0, finalRead);
                                    block.nSamples = finalRead / 2;
                                    int sampleIndex3 = sampleIndex;
                                    block.uSampleIndex = sampleIndex3;
                                    block.uStreamTypes = 1;
                                    TeamTalkService.this.ttclient.insertAudioBlock(block);
                                    sampleIndex = sampleIndex3 + block.nSamples;
                                }
                                projectionManager2 = projectionManager;
                                config2 = config;
                            }
                        } catch (IllegalArgumentException e) {
                            Log.e("bearware", "Error recording internal audio", e);
                            TeamTalkService.this.stopInternalAudioCapture();
                            return;
                        } catch (SecurityException e) {
                            Log.e("bearware", "Error recording internal audio", e);
                            TeamTalkService.this.stopInternalAudioCapture();
                            return;
                        } catch (Throwable th2) {
                            TeamTalkService.this.stopInternalAudioCapture();
                        }
                        TeamTalkService.this.stopInternalAudioCapture();
                        return;
                    }
                    Log.e("bearware", "Internal audio capture requires Android 10+");
                    TeamTalkService.this.isInternalAudioRunning = false;
                } catch (Throwable th3) {
                    TeamTalkService.this.stopInternalAudioCapture();
                }
            }
        }, "InternalAudioCaptureThread");
        this.internalAudioThread.start();
    }

        public void stopInternalAudioCapture() {
        this.isInternalAudioRunning = false;
        if (this.internalAudioRecord != null) {
            try {
                if (this.internalAudioRecord.getRecordingState() == 3) {
                    this.internalAudioRecord.stop();
                }
            } catch (Exception e) {
            }
            this.internalAudioRecord.release();
            this.internalAudioRecord = null;
        }
        if (this.micAudioRecord != null) {
            try {
                if (this.micAudioRecord.getRecordingState() == 3) {
                    this.micAudioRecord.stop();
                }
            } catch (Exception e2) {
            }
            this.micAudioRecord.release();
            this.micAudioRecord = null;
        }
        if (this.internalAudioThread != null) {
            this.internalAudioThread.interrupt();
            this.internalAudioThread = null;
        }
    }

    public String getCurrentStreamPath() {
        return this.currentStreamPath;
    }

    public boolean isStreamingMedia() {
        return this.isStreamingMedia;
    }

    public int getLocalPlaybackId() {
        return this.localPlaybackId;
    }

    public MediaFileInfo getCurrentMediaFileInfo() {
        return this.currentMediaFileInfo;
    }

    public MediaFilePlayback getCurrentPlayback() {
        return this.currentPlayback;
    }

    public void setCurrentStreamPath(String path) {
        this.currentStreamPath = path;
    }

    public void setStreamingMedia(boolean streaming) {
        this.isStreamingMedia = streaming;
    }

    public void setLocalPlaybackId(int id) {
        this.localPlaybackId = id;
    }

    public void setCurrentMediaFileInfo(MediaFileInfo info) {
        this.currentMediaFileInfo = info;
    }

    public void setCurrentPlayback(MediaFilePlayback playback) {
        this.currentPlayback = playback;
    }

    public void enableVoiceActivation(boolean enable) {
        String inputSource = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Preferences.PREF_SOUNDSYSTEM_INPUT_SOURCE, "mic");
        boolean useInternal = "internal".equals(inputSource) || "mixed".equals(inputSource);
        if (enable) {
            this.txSuspended = false;
            this.voxSuspended = false;
            if (useInternal) {
                this.ttclient.enableVoiceActivation(true);
                startInternalAudioCapture();
            } else {
                int indevid = getPreferredSoundInputDeviceId();
                if ((this.ttclient.getFlags() & 1) != 0 || this.ttclient.initSoundInputDevice(indevid)) {
                    applyRealTimeAudioProcessing();
                    this.ttclient.enableVoiceActivation(true);
                }
            }
        } else {
            if (useInternal) {
                stopInternalAudioCapture();
            }
            this.ttclient.enableVoiceActivation(false);
            this.ttclient.closeSoundInputDevice();
        }
        adjustMuteOnTx(enable);
        updateFloatingWindow();
    }

    public void syncToUserCache(User user) {
        String cacheid = UserCached.getCacheID(user);
        if (!cacheid.isEmpty()) {
            this.usercache.put(cacheid, new UserCached(user));
        }
    }

    public void syncToUserCache() {
        for (Map.Entry<Integer, User> entry : this.users.entrySet()) {
            syncToUserCache(entry.getValue());
        }
    }

    public void syncFromUserCache(User user) {
        UserCached userprop;
        String cacheid = UserCached.getCacheID(user);
        if (!cacheid.isEmpty() && (userprop = this.usercache.get(cacheid)) != null) {
            userprop.sync(this.ttclient, user);
        }
    }

    public boolean reconnect() {
        if (this.ttserver == null || this.ttclient == null) {
            return false;
        }
        this.manualDisconnect = false;
        this.isSeamlessReconnecting = false;
        this.reconnectHandler.removeCallbacks(this.seamlessReconnectRunnable);
        this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        syncToUserCache();
        this.ttclient.disconnect();
        if (!setupEncryption()) {
            return false;
        }
        if (!this.ttclient.connect(this.ttserver.ipaddr, this.ttserver.tcpport, this.ttserver.udpport, 0, 0, this.ttserver.encrypted)) {
            this.ttclient.disconnect();
            return false;
        }
        return true;
    }

    private boolean setupEncryption() {
        if (!this.ttserver.encrypted) {
            return true;
        }
        File outputDir = getBaseContext().getCacheDir();
        try {
            File cacertfile = File.createTempFile("cacert", "pem", outputDir);
            File clientcertfile = File.createTempFile("clientcert", "pem", outputDir);
            File clientkeyfile = File.createTempFile("clientkey", "pem", outputDir);
            FileWriter cawriter = new FileWriter(cacertfile);
            try {
                FileWriter certwriter = new FileWriter(clientcertfile);
                try {
                    FileWriter keywriter = new FileWriter(clientkeyfile);
                    try {
                        cawriter.write(this.ttserver.cacert);
                        certwriter.write(this.ttserver.clientcert);
                        keywriter.write(this.ttserver.clientcertkey);
                        keywriter.close();
                        certwriter.close();
                        cawriter.close();
                        EncryptionContext context = new EncryptionContext();
                        if (!this.ttserver.cacert.isEmpty()) {
                            context.szCAFile = cacertfile.getAbsolutePath();
                        }
                        if (!this.ttserver.clientcert.isEmpty()) {
                            context.szCertificateFile = clientcertfile.getAbsolutePath();
                        }
                        if (!this.ttserver.clientcertkey.isEmpty()) {
                            context.szPrivateKeyFile = clientkeyfile.getAbsolutePath();
                        }
                        context.bVerifyPeer = this.ttserver.verifypeer;
                        if (!context.bVerifyPeer) {
                            context.nVerifyDepth = -1;
                        }
                        return this.ttclient.setEncryptionContext(context);
                    } finally {
                    }
                } finally {
                }
            } finally {
            }
        } catch (IOException e) {
            return false;
        }
    }

    public Vector<MyTextMessage> getUserTextMsgs(int userid) {
        if (this.usertxtmsgs.get(Integer.valueOf(userid)) == null) {
            this.usertxtmsgs.put(Integer.valueOf(userid), new Vector<>());
        }
        Vector<MyTextMessage> msgs = this.usertxtmsgs.get(Integer.valueOf(userid));
        if (msgs.size() > this.HISTORY_USER_MSG_MAX) {
            msgs.remove(0);
        }
        return msgs;
    }

    public Vector<MyTextMessage> getChatLogTextMsgs() {
        if (this.chatlogtxtmsgs.size() > this.HISTORY_CHATLOG_MSG_MAX) {
            this.chatlogtxtmsgs.remove(0);
        }
        return this.chatlogtxtmsgs;
    }

    void createEventTimer() {
        this.eventTimer = new CountDownTimer(10000L, 100L) { 
            private boolean prevVoiceActivationState;
            private boolean prevVoiceTransmissionState;

            {
                this.prevVoiceTransmissionState = TeamTalkService.this.isVoiceTransmissionEnabled();
                this.prevVoiceActivationState = TeamTalkService.this.isVoiceActivationEnabled();
            }

            @Override
            public void onTick(long millisUntilFinished) {
                int events = 0;
                while (true) {
                    int events2 = events + 1;
                    if (events >= 50 || !TeamTalkService.this.mEventHandler.processEvent(TeamTalkService.this.ttclient, 0)) {
                        break;
                    } else {
                        events = events2;
                    }
                }
                boolean newVoiceTransmissionState = TeamTalkService.this.isVoiceTransmissionEnabled();
                boolean newVoiceActivationState = TeamTalkService.this.isVoiceActivationEnabled();
                if (TeamTalkService.this.onVoiceTransmissionToggleListener != null) {
                    if (newVoiceTransmissionState != this.prevVoiceTransmissionState) {
                        TeamTalkService.this.onVoiceTransmissionToggleListener.onVoiceTransmissionToggle(newVoiceTransmissionState, TeamTalkService.this.txSuspended);
                        this.prevVoiceTransmissionState = newVoiceTransmissionState;
                    }
                    if (newVoiceActivationState != this.prevVoiceActivationState) {
                        TeamTalkService.this.onVoiceTransmissionToggleListener.onVoiceActivationToggle(newVoiceActivationState, TeamTalkService.this.voxSuspended);
                        this.prevVoiceActivationState = newVoiceActivationState;
                    }
                }
            }

            @Override
            public void onFinish() {
                start();
            }
        };
        this.eventTimer.start();
    }

    void createReconnectTimer(long delayMsec) {
        this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        this.reconnectHandler.postDelayed(this.reconnectTimer, delayMsec);
    }

    public void login() {
        if (this.ttclient == null || this.ttserver == null) {
            return;
        }
        String nickname = this.ttserver.nickname;
        if (TextUtils.isEmpty(nickname)) {
            nickname = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Preferences.PREF_GENERAL_NICKNAME, "");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String clientName = prefs.getString(Preferences.PREF_GENERAL_CLIENTNAME, "");

        String fullClientName;
        if (TextUtils.isEmpty(clientName)) {
            fullClientName = "TeamTalk Pro+";
        } else {
            fullClientName = clientName.trim();
        }

        int loginCmdId = this.ttclient.doLoginEx(nickname, this.ttserver.username, this.ttserver.password, fullClientName);
        if (loginCmdId < 0) {
            Toast.makeText(this, getResources().getString(R.string.text_cmderr_login), Toast.LENGTH_SHORT).show();
        } else {
            this.activecmds.put(loginCmdId, CmdComplete.CMD_COMPLETE_LOGIN);
        }
        MyTextMessage msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, getResources().getString(R.string.text_con_success));
        getChatLogTextMsgs().add(msg);
    }

    private void loginComplete() {
        if (this.joinchannel == null) {
            if (this.ttserver.channel != null && !this.ttserver.channel.isEmpty()) {
                int chanid = this.ttclient.getChannelIDFromPath(this.ttserver.channel);
                this.joinchannel = getChannels().get(Integer.valueOf(chanid));
                if (this.joinchannel != null) {
                    this.joinchannel.szPassword = this.ttserver.chanpasswd;
                }
            }
            UserAccount useraccount = new UserAccount();
            this.ttclient.getMyUserAccount(useraccount);
            if (this.joinchannel == null && !useraccount.szInitChannel.isEmpty()) {
                int chanid2 = this.ttclient.getChannelIDFromPath(useraccount.szInitChannel);
                this.joinchannel = getChannels().get(Integer.valueOf(chanid2));
            }
            boolean joinroot = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(Preferences.PREF_JOIN_ROOT_CHAN, true);
            if (joinroot && this.joinchannel == null) {
                this.joinchannel = getChannels().get(Integer.valueOf(this.ttclient.getRootChannelID()));
                if (this.joinchannel != null) {
                    this.joinchannel.szPassword = this.ttserver.chanpasswd;
                }
            }
        }
        if (this.joinchannel != null) {
            int cmdid = this.ttclient.doJoinChannel(this.joinchannel);
            this.activecmds.put(cmdid, CmdComplete.CMD_COMPLETE_JOIN);
        }
    }

    private void setupAudioPreprocessor() {
        applyRealTimeAudioProcessing();
    }

    public void applyRealTimeAudioProcessing() {
        if (this.ttclient == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        AudioPreprocessor ap = new AudioPreprocessor(4, true);
        boolean nsEnabled = prefs.getBoolean("eq_mic_ns", false);
        boolean aecEnabled = prefs.getBoolean("eq_mic_aec", false);
        boolean agcEnabled = prefs.getBoolean("eq_mic_agc", false);
        boolean vpEnabled = prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_VOICEPROCESSING, false);
        boolean speakerphone = prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_SPEAKERPHONE, false);

        ap.webrtc.noisesuppression.bEnable = nsEnabled || vpEnabled;
        ap.webrtc.noisesuppression.nLevel = 2;
        ap.webrtc.echocanceller.bEnable = aecEnabled || vpEnabled;

        if (ap.speexdsp != null) {
            ap.speexdsp.bEnableDenoise = nsEnabled || vpEnabled;
            ap.speexdsp.bEnableEchoCancellation = aecEnabled || vpEnabled;
            ap.speexdsp.nEchoSuppress = -40;
            ap.speexdsp.nEchoSuppressActive = -40;
        }

        if (this.mychannel != null && this.mychannel.audiocfg.bEnableAGC) {
            ap.webrtc.gaincontroller2.bEnable = true;
            float gainPercent = this.mychannel.audiocfg.nGainLevel / 32000.0f;
            ap.webrtc.gaincontroller2.fixeddigital.fGainDB = 49.9f * gainPercent;
        } else {
            ap.webrtc.gaincontroller2.bEnable = agcEnabled || vpEnabled;
        }

        this.ttclient.setSoundInputPreprocess(ap);

        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null && !audioManager.isBluetoothA2dpOn()) {
                if (aecEnabled || vpEnabled) {
                    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    if (speakerphone && !audioManager.isWiredHeadsetOn()) {
                        audioManager.setSpeakerphoneOn(true);
                    }
                } else {
                    audioManager.setMode(AudioManager.MODE_NORMAL);
                }
            }
        } catch (Exception e) {
            Log.e("bearware", "Failed to configure AudioManager mode", e);
        }

        int gain = (this.mychannel != null && this.mychannel.audiocfg.bEnableAGC)
                ? SoundLevel.SOUND_GAIN_DEFAULT
                : prefs.getInt(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, SoundLevel.SOUND_GAIN_DEFAULT);
        this.ttclient.setSoundInputGainLevel(gain);

        int vox = prefs.getInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 0);
        this.ttclient.setVoiceActivationLevel(vox);
    }

    @Override
    public void onConnectSuccess() {
        this.isSeamlessReconnecting = false;
        if (this.ttserver == null) {
            throw new AssertionError();
        }
        if (Utils.isWebLogin(this.ttserver.username)) {
            new WebLoginAccessToken().execute(new Void[0]);
        } else {
            login();
        }
        updateFloatingWindow();
    }

    @Override
    public void onEncryptionError(int opensslErrorNo, ClientErrorMsg errmsg) {
        if (this.manualDisconnect || this.ttserver == null) {
            return;
        }
        Log.i("bearware", "Encryption error: " + errmsg.szErrorMsg + " connecting to " + this.ttserver.ipaddr + ":" + this.ttserver.tcpport);
        Toast.makeText(this, getResources().getString(R.string.text_con_encryption_error, errmsg.szErrorMsg), 1).show();
    }

    @Override
    public void onConnectFailed() {
        if (this.manualDisconnect || this.ttserver == null) {
            return;
        }
        if (this.isSeamlessReconnecting) {
            Log.i("bearware", "Seamless reconnect attempt failed, retrying in 1.5s");
            createReconnectTimer(1500L);
            return;
        }
        Log.i("bearware", "Failed to connect " + this.ttserver.ipaddr + ":" + this.ttserver.tcpport);
        Toast.makeText(this, getResources().getString(R.string.text_con_failed), 0).show();
        createReconnectTimer(5000L);
        updateFloatingWindow();
    }

    @Override
    public void onConnectionLost() {
        if (this.manualDisconnect || this.ttserver == null) {
            return;
        }
        if (this.isSeamlessReconnecting) {
            Log.i("bearware", "Connection dropped during seamless network transition, retrying in 1.5s");
            createReconnectTimer(1500L);
            return;
        }
        Log.i("bearware", "Connection lost to " + this.ttserver.ipaddr + ":" + this.ttserver.tcpport);
        this.activecmds.clear();
        Toast.makeText(this, getResources().getString(R.string.text_con_lost), 1).show();
        createReconnectTimer(5000L);
        MyTextMessage msg = MyTextMessage.createLogMsg(1073741824, getResources().getString(R.string.text_con_lost));
        getChatLogTextMsgs().add(msg);
        updateFloatingWindow();
    }

    @Override
    public void onCmdError(int cmdId, ClientErrorMsg errmsg) {
        Utils.notifyError(this, errmsg);
        if (this.activecmds.get(cmdId) == CmdComplete.CMD_COMPLETE_LOGIN) {
            this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        }
    }

    @Override
    public void onCmdSuccess(int cmdId) {
        if (this.activecmds.get(cmdId) == CmdComplete.CMD_COMPLETE_LOGIN) {
            this.reconnectHandler.removeCallbacks(this.reconnectTimer);
            displayNotification(true);
        }
    }

    @Override
    public void onCmdProcessing(int cmdId, boolean complete) {
        if (!complete) {
            switch (this.activecmds.get(cmdId, CmdComplete.CMD_COMPLETE_NONE)) {
                case CMD_COMPLETE_LOGIN:
                    this.users.clear();
                    this.remoteFiles.clear();
                    this.fileTransfers.clear();
                    this.channels.clear();
                    return;
                default:
                    return;
            }
        }
        switch (this.activecmds.get(cmdId, CmdComplete.CMD_COMPLETE_NONE)) {
            case CMD_COMPLETE_LOGIN:
                loginComplete();
                break;
        }
        this.activecmds.delete(cmdId);
    }

    @Override
    public void onCmdMyselfLoggedIn(int my_userid, UserAccount useraccount) {
        MyTextMessage msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, getResources().getString(R.string.text_cmd_loggedin));
        getChatLogTextMsgs().add(msg);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        int statusmode = 0;
        String statusmsg = this.ttserver.statusmsg;
        if (TextUtils.isEmpty(statusmsg)) {
            statusmsg = prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, "");
        }
        if (prefs.getBoolean(Preferences.PREF_GENERAL_GENDER, false)) {
            statusmode = 0 | 256;
        }
        this.ttclient.doChangeStatus(statusmode, statusmsg);
        updateFloatingWindow();
    }

    @Override
    public void onCmdMyselfKickedFromChannel() {
    }

    @Override
    public void onCmdMyselfKickedFromChannel(User kicker) {
        this.users.put(Integer.valueOf(kicker.nUserID), kicker);
    }

    @Override
    public void onCmdUserLoggedIn(User user) {
        int cmdid;
        this.users.put(Integer.valueOf(user.nUserID), user);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int def_unsub = 0;
        if (!pref.getBoolean(Preferences.PREF_SUB_TEXTMESSAGE, true)) {
            def_unsub = 0 | 1;
        }
        if (!pref.getBoolean(Preferences.PREF_SUB_CHANMESSAGE, true)) {
            def_unsub |= 2;
        }
        if (!pref.getBoolean(Preferences.PREF_SUB_BCAST_MESSAGES, true)) {
            def_unsub |= 4;
        }
        if (!pref.getBoolean(Preferences.PREF_SUB_VOICE, true)) {
            def_unsub |= 16;
        }
        if (!pref.getBoolean(Preferences.PREF_SUB_VIDCAP, true)) {
            def_unsub |= 32;
        }
        if (!pref.getBoolean(Preferences.PREF_SUB_DESKTOP, true)) {
            def_unsub |= 64;
        }
        if (!pref.getBoolean(Preferences.PREF_SUB_MEDIAFILE, true)) {
            def_unsub |= 256;
        }
        if ((user.uLocalSubscriptions & def_unsub) != 0 && (cmdid = this.ttclient.doUnsubscribe(user.nUserID, def_unsub)) > 0) {
            this.activecmds.put(cmdid, CmdComplete.CMD_COMPLETE_UNSUBSCRIBE);
        }
        String name = Utils.getDisplayName(getBaseContext(), user);
        MyTextMessage msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, name + " " + getResources().getString(R.string.text_cmd_userloggedin));
        getChatLogTextMsgs().add(msg);
        syncFromUserCache(user);
    }

    @Override
    public void onCmdUserLoggedOut(User user) {
        this.users.remove(Integer.valueOf(user.nUserID));
        String name = Utils.getDisplayName(getBaseContext(), user);
        MyTextMessage msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, name + " " + getResources().getString(R.string.text_cmd_userloggedout));
        getChatLogTextMsgs().add(msg);
        syncToUserCache(user);
    }

    @Override
    public void onCmdUserUpdate(User user) {
        this.users.put(Integer.valueOf(user.nUserID), user);
        updateFloatingWindow();
    }

    @Override
    public void onCmdUserJoinedChannel(User user) {
        MyTextMessage msg;
        this.users.put(Integer.valueOf(user.nUserID), user);
        if (this.ttserver.rememberLastChannel && user.nUserID == this.ttclient.getMyUserID() && this.joinchannel != null) {
            this.ttserver.channel = this.ttclient.getChannelPath(this.joinchannel.nChannelID);
            this.ttserver.chanpasswd = this.joinchannel.szPassword;
        }
        if (user.nUserID == this.ttclient.getMyUserID()) {
            setMyChannel(getChannels().get(Integer.valueOf(user.nChannelID)));
            displayNotification(true);
            if (this.mychannel == null || this.mychannel.nParentID == 0) {
                msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, getResources().getString(R.string.text_cmd_joinroot));
            } else {
                msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, getResources().getString(R.string.text_cmd_joinchan) + " " + this.mychannel.szName);
            }
            getChatLogTextMsgs().add(msg);
        } else if (this.mychannel != null && this.mychannel.nChannelID == user.nChannelID) {
            String name = Utils.getDisplayName(getBaseContext(), user);
            MyTextMessage msg2 = MyTextMessage.createLogMsg(Integer.MIN_VALUE, name + " " + getResources().getString(R.string.text_cmd_userjoinchan));
            getChatLogTextMsgs().add(msg2);
        }
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int mf_volume = pref.getInt(Preferences.PREF_SOUNDSYSTEM_MEDIAFILE_VOLUME, 50);
        this.ttclient.setUserVolume(user.nUserID, 4, Utils.refVolume(mf_volume));
        this.ttclient.pumpMessage(500, user.nUserID);
        if (!UserCached.getCacheID(user).isEmpty()) {
            UserAccount myaccount = new UserAccount();
            if (this.ttclient.getMyUserAccount(myaccount) && (myaccount.uUserRights & 2) == 0) {
                syncFromUserCache(user);
            }
        }
    }

    @Override
    public void onCmdUserLeftChannel(int channelid, User user) {
        MyTextMessage msg;
        this.users.put(Integer.valueOf(user.nUserID), user);
        if (this.mychannel != null && this.mychannel.nChannelID == channelid) {
            Channel chan = getChannels().get(Integer.valueOf(channelid));
            if (user.nUserID == this.ttclient.getMyUserID()) {
                if (chan == null || chan.nParentID == 0) {
                    msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, getResources().getString(R.string.text_cmd_leftroot));
                } else {
                    msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, getResources().getString(R.string.text_cmd_leftchan) + " " + chan.szName);
                }
            } else {
                String name = Utils.getDisplayName(getBaseContext(), user);
                msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, name + " " + getResources().getString(R.string.text_cmd_userleftchan));
            }
            getChatLogTextMsgs().add(msg);
        }
        if (user.nUserID == this.ttclient.getMyUserID()) {
            setMyChannel(null);
        }
        String cacheid = UserCached.getCacheID(user);
        if (!cacheid.isEmpty()) {
            UserAccount myaccount = new UserAccount();
            if (this.ttclient.getMyUserAccount(myaccount) && (myaccount.uUserRights & 2) == 0) {
                syncToUserCache(user);
            }
        }
    }

    @Override
    public void onCmdUserTextMessage(TextMessage textmessage) {
        int limit;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (pref.getBoolean(Preferences.PREF_ANTISPAM_ENABLED, false) && !this.antispam_blocked.isEmpty() && this.antispam_blocked.contains(Integer.valueOf(textmessage.nFromUserID))) {
            return;
        }
        if (pref.getBoolean(Preferences.PREF_ANTISPAM_ENABLED, false)) {
            int uid = textmessage.nFromUserID;
            Integer cnt = this.antispam_user_counts.get(Integer.valueOf(uid));
            if (cnt == null) {
                cnt = 0;
            }
            this.antispam_user_counts.put(Integer.valueOf(uid), Integer.valueOf(cnt.intValue() + 1));
            long now = SystemClock.elapsedRealtime();
            if (now - this.antispam_window_start > 1000) {
                this.antispam_count = 0;
                this.antispam_window_start = now;
                this.antispam_user_counts.clear();
            }
            this.antispam_count++;
            try {
                limit = Integer.parseInt(pref.getString(Preferences.PREF_ANTISPAM_MSG_LIMIT, "20"));
            } catch (NumberFormatException e) {
                limit = 20;
            }
            if (cnt.intValue() + 1 >= 5 || this.antispam_count > limit) {
                this.antispam_triggered = true;
                this.antispam_blocked.add(Integer.valueOf(uid));
                Log.e("bearware", "ANTISPAM TRIGGERED uid=" + uid + " msgs_from_user=" + (cnt.intValue() + 1) + " total=" + this.antispam_count + " limit=" + limit);
                boolean z = pref.getBoolean(Preferences.PREF_ANTISPAM_UNSUB_ALL, true);
                TeamTalkBase teamTalkBase = this.ttclient;
                if (z) {
                    teamTalkBase.doUnsubscribe(uid, 24838655);
                } else {
                    teamTalkBase.doUnsubscribe(uid, 15);
                }
                int text_flags = R.string.antispam_triggered;
                String msg = String.format(getString(text_flags), Integer.valueOf(limit));
                MyTextMessage log = MyTextMessage.createLogMsg(1073741824, msg);
                getChatLogTextMsgs().add(log);
                return;
            }
        }
        User user = getUsers().get(Integer.valueOf(textmessage.nFromUserID));
        MyTextMessage newmsg = new MyTextMessage(textmessage, user == null ? "" : Utils.getDisplayName(getBaseContext(), user));
        switch (textmessage.nMsgType) {
            case 1:
                getUserTextMsgs(textmessage.nFromUserID).add(newmsg);
                return;
            case 2:
                getChatLogTextMsgs().add(newmsg);
                return;
            case 3:
                getChatLogTextMsgs().add(newmsg);
                return;
            default:
                return;
        }
    }

    @Override
    public void onCmdChannelNew(Channel channel) {
        this.channels.put(Integer.valueOf(channel.nChannelID), channel);
    }

    @Override
    public void onCmdChannelUpdate(Channel channel) {
        this.channels.put(Integer.valueOf(channel.nChannelID), channel);
        if (this.mychannel != null && this.mychannel.nChannelID == channel.nChannelID) {
            setMyChannel(channel);
        }
    }

    @Override
    public void onCmdChannelRemove(Channel channel) {
        this.channels.remove(Integer.valueOf(channel.nChannelID));
    }

    @Override
    public void onCmdServerUpdate(ServerProperties serverproperties) {
        MyTextMessage msg = MyTextMessage.createUserDefMsg(MyTextMessage.MSGTYPE_SERVERPROP, serverproperties);
        getChatLogTextMsgs().add(msg);
    }

    @Override
    public void onCmdFileNew(RemoteFile remotefile) {
        this.remoteFiles.put(Integer.valueOf(remotefile.nFileID), remotefile);
    }

    @Override
    public void onCmdFileRemove(RemoteFile remotefile) {
        this.remoteFiles.remove(Integer.valueOf(remotefile.nFileID));
    }

    @Override
    public void onUserStateChange(User user) {
        this.users.put(Integer.valueOf(user.nUserID), user);
        updateFloatingWindow();
        if (this.pendingAutoRecord && !this.isRecording && this.mychannel != null && user != null) {
            if (user.nChannelID == this.mychannel.nChannelID && (user.uUserState & 1) != 0) {
                checkAndTriggerAutoRecord();
            }
        }
    }

    @Override
    public void onVoiceActivation(boolean bVoiceActive) {
        adjustMuteOnTx(bVoiceActive);
        updateFloatingWindow();
        if (bVoiceActive) {
            checkAndTriggerAutoRecord();
        }
    }

    @Override
    public void onFileTransfer(FileTransfer transfer) {
        int i = transfer.nStatus;
        Map<Integer, FileTransfer> map = this.fileTransfers;
        if (i == 2) {
            map.put(Integer.valueOf(transfer.nTransferID), transfer);
        } else {
            map.remove(Integer.valueOf(transfer.nTransferID));
        }
    }

    @Override
    public void onStreamMediaFile(MediaFileInfo mediafileinfo) {
        User myself = this.users.get(Integer.valueOf(this.ttclient.getMyUserID()));
        if (myself == null) {
            return;
        }
        switch (mediafileinfo.nStatus) {
            case 1:
            case 3:
            case 4:
                this.ttclient.doChangeStatus(myself.nStatusMode & (-2049), myself.szStatusMsg);
                return;
            case 2:
                this.ttclient.doChangeStatus(myself.nStatusMode | 2048, myself.szStatusMsg);
                return;
            default:
                return;
        }
    }

    @Override
    public void onHeadsetConnected() {
        this.bluetoothHeadsetHelper.scoAudioConnect();
    }

    @Override
    public void onHeadsetDisconnected() {
        this.bluetoothHeadsetHelper.scoAudioDisconnect();
    }

    @Override
    public void onScoAudioConnected() {
        reinitSoundInputDevice();
    }

    @Override
    public void onScoAudioDisconnected() {
        reinitSoundInputDevice();
    }

        class WebLoginAccessToken extends AsyncTask<Void, Void, Void> {
        String username = "";
        String token = "";
        String accesstoken = "";

        WebLoginAccessToken() {
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TeamTalkService.this.getBaseContext());
            this.username = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
            this.token = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, "");
            ServerProperties srvprop = new ServerProperties();
            if (TeamTalkService.this.ttclient.getServerProperties(srvprop)) {
                this.accesstoken = srvprop.szAccessToken;
            }
        }

                @Override
        public void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            int length = this.username.length();
            TeamTalkService teamTalkService = TeamTalkService.this;
            if (length > 0) {
                teamTalkService.ttserver.username = this.username;
                TeamTalkService.this.login();
                return;
            }
            Toast.makeText(teamTalkService, TeamTalkService.this.getResources().getString(R.string.text_weblogin_authfailure), 1).show();
        }

                @Override
        public Void doInBackground(Void... voids) {
            String xml = Utils.getURL(AppInfo.getBearWareAccessTokenUrl(TeamTalkService.this.getBaseContext(), this.username, this.token, this.accesstoken));
            Log.d("bearware", xml);
            try {
                InputSource src = new InputSource(new StringReader(xml));
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                try {
                    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
                    dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                } catch (Exception e) {
                }
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document document = db.parse(src);
                XPathFactory factory = XPathFactory.newInstance();
                XPath xPath = factory.newXPath();
                this.username = (String) xPath.evaluate("/teamtalk/bearware/username", document, XPathConstants.STRING);
                return null;
            } catch (IOException e2) {
                Log.e("bearware", "XML IOException: " + e2);
                return null;
            } catch (ParserConfigurationException e3) {
                Log.e("bearware", "Parser cfg failed: " + e3);
                return null;
            } catch (XPathExpressionException e4) {
                Log.e("bearware", "XPath failed: " + e4);
                return null;
            } catch (SAXException e5) {
                Log.e("bearware", "XML SAXException: " + e5);
                return null;
            }
        }
    }

    private void registerNetworkCallback() {
        if (this.connectivityManager == null) {
            this.connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        if (this.connectivityManager == null) {
            return;
        }
        try {
            this.networkCallback = new ConnectivityManager.NetworkCallback() { 
                @Override
                public void onAvailable(Network network) {
                    TeamTalkService.this.handleNetworkAvailable(network);
                }

                @Override
                public void onLost(Network network) {
                    TeamTalkService.this.handleNetworkLost(network);
                }

                @Override
                public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
                    TeamTalkService.this.handleNetworkCapabilitiesChanged(network, capabilities);
                }
            };

            if (Build.VERSION.SDK_INT >= 24) {
                this.connectivityManager.registerDefaultNetworkCallback(this.networkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                this.connectivityManager.registerNetworkCallback(request, this.networkCallback);
            }
        } catch (Exception e) {
            Log.w("bearware", "Failed to register network callback", e);
        }
    }

    private void unregisterNetworkCallback() {
        if (this.connectivityManager != null && this.networkCallback != null) {
            try {
                this.connectivityManager.unregisterNetworkCallback(this.networkCallback);
            } catch (Exception e) {
                Log.w("bearware", "Failed to unregister network callback", e);
            }
            this.networkCallback = null;
        }
    }

    private void handleNetworkAvailable(Network network) {
        boolean seamlessEnabled = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(Preferences.PREF_CONNECTION_SEAMLESS_RECONNECT, true);
        if (!seamlessEnabled) {
            this.activeNetwork = network;
            return;
        }

        Network oldNetwork = this.activeNetwork;
        this.activeNetwork = network;

        if (this.ttserver != null && !this.manualDisconnect) {
            if (oldNetwork != null && !oldNetwork.equals(network)) {
                Log.i("bearware", "Active network changed (Wi-Fi/LTE/VPN switch). Scheduling seamless reconnect.");
                scheduleSeamlessReconnect(300L);
            } else if (oldNetwork == null && this.isSeamlessReconnecting) {
                Log.i("bearware", "Network restored. Scheduling seamless reconnect.");
                scheduleSeamlessReconnect(200L);
            }
        }
    }

    private void handleNetworkLost(Network network) {
        if (network != null && network.equals(this.activeNetwork)) {
            this.activeNetwork = null;
            if (this.ttserver != null && !this.manualDisconnect) {
                Log.i("bearware", "Current active network lost, awaiting new network connection (LTE/Wi-Fi)...");
                this.isSeamlessReconnecting = true;
            }
        }
    }

    private void handleNetworkCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        if (network != null && network.equals(this.activeNetwork) && capabilities != null) {
            boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if (hasInternet && this.ttserver != null && !this.manualDisconnect && this.isSeamlessReconnecting) {
                scheduleSeamlessReconnect(200L);
            }
        }
    }

    private synchronized void scheduleSeamlessReconnect(long delayMs) {
        this.reconnectHandler.removeCallbacks(this.seamlessReconnectRunnable);
        this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        this.isSeamlessReconnecting = true;
        this.reconnectHandler.postDelayed(this.seamlessReconnectRunnable, delayMs);
    }

    private void performSeamlessReconnect() {
        if (this.ttserver == null || this.manualDisconnect || this.ttclient == null) {
            this.isSeamlessReconnecting = false;
            return;
        }
        Log.i("bearware", "Performing seamless network reconnect to " + this.ttserver.ipaddr + ":" + this.ttserver.tcpport);

        // Preserve current channel to rejoin after login
        if (this.mychannel != null) {
            this.joinchannel = this.mychannel;
        }

        syncToUserCache();
        this.ttclient.disconnect();

        if (!setupEncryption()) {
            Log.w("bearware", "Seamless reconnect encryption setup failed");
            createReconnectTimer(2000L);
            return;
        }

        if (!this.ttclient.connect(this.ttserver.ipaddr, this.ttserver.tcpport, this.ttserver.udpport, 0, 0, this.ttserver.encrypted)) {
            Log.w("bearware", "Seamless connect() failed, scheduling quick retry");
            createReconnectTimer(1500L);
        } else {
            Log.i("bearware", "Seamless connect() started over new network interface");
        }
    }
}
