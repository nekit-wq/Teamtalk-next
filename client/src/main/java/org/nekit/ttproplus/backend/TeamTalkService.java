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
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.preference.PreferenceManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import dk.bearware.AudioBlock;
import dk.bearware.AudioCodec;
import dk.bearware.AudioFileFormat;
import dk.bearware.AudioPreprocessor;
import dk.bearware.AudioPreprocessorType;
import dk.bearware.Channel;
import dk.bearware.ClientErrorMsg;
import dk.bearware.Codec;
import dk.bearware.EncryptionContext;
import dk.bearware.FileTransfer;
import dk.bearware.MediaFileInfo;
import dk.bearware.MediaFilePlayback;
import dk.bearware.VideoCodec;
import dk.bearware.OpusConstants;
import dk.bearware.RemoteFile;
import dk.bearware.ServerProperties;
import dk.bearware.SoundDeviceEffects;
import dk.bearware.SoundLevel;
import dk.bearware.TeamTalk5;
import dk.bearware.TeamTalkBase;
import dk.bearware.TextMessage;
import dk.bearware.User;
import dk.bearware.UserAccount;
import org.nekit.ttproplus.data.MicrophoneInputHelper;
import org.nekit.ttproplus.data.ScreenShareAudioHelper;
import org.nekit.ttproplus.utils.ScreenShareManager;
import dk.bearware.events.ClientEventListener;
import dk.bearware.events.TeamTalkEventHandler;
import org.nekit.ttproplus.BuildConfig;
import org.nekit.ttproplus.data.ChatHistoryDbHelper;
import org.nekit.ttproplus.data.ChatMessageEntry;
import org.nekit.ttproplus.gui.Utils;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.RandomAccessFile;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static final int[] MIC_EQ_FREQUENCIES = {60, 120, 250, 500, 1000, 2000, 4000, 8000, 16000};
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
    private AudioManager audioManager;
    private boolean experimentalAudioSessionModeApplied = false;
    private boolean experimentalVoiceTransmissionRequested = false;
    private boolean voiceActivationRequested = false;
    private boolean isScreenSharingActive = false;
    private ExperimentalAudioCapture experimentalAudioCapture;
    private ScreenShareManager screenShareManager;
    private int nextExperimentalVoiceStreamId = 0;
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
    private static volatile TeamTalkService sInstance;

    public static TeamTalkService getInstance() {
        return sInstance;
    }

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
            if ("eq_mic_ns".equals(key) || "eq_mic_aec".equals(key) || "eq_mic_agc".equals(key) || key.startsWith(Preferences.PREF_EQ_MIC_BAND_PREFIX) || Preferences.PREF_SOUNDSYSTEM_VOICEPROCESSING.equals(key) || Preferences.PREF_SOUNDSYSTEM_SPEAKERPHONE.equals(key) || Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN.equals(key) || Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL.equals(key)) {
                TeamTalkService.this.applyRealTimeAudioProcessing();
            }
            if ((Preferences.PREF_SOUNDSYSTEM_EXPERIMENTAL_CAPTURE_MODE.equals(key)
                    || Preferences.PREF_SOUNDSYSTEM_EXPERIMENTAL_INPUT_DEVICE.equals(key)
                    || Preferences.PREF_VOICE_CHANGER_MODE.equals(key)
                    || Preferences.PREF_SOUNDSYSTEM_INPUT_SOURCE.equals(key)) && TeamTalkService.this.ttclient != null) {
                TeamTalkService.this.reinitSoundInputDevice();
            }
            if (Preferences.PREF_SOUNDSYSTEM_MIC_ENHANCEMENTS.equals(key)) {
                if (TeamTalkService.this.experimentalAudioCapture != null) {
                    TeamTalkService.this.experimentalAudioCapture.setMicEnhancementEnabled(sharedPreferences.getBoolean(key, false));
                }
            }
            if (Preferences.PREF_SCREENSHARE_AUDIO_MODE.equals(key)) {
                if (TeamTalkService.this.experimentalAudioCapture != null) {
                    TeamTalkService.this.experimentalAudioCapture.setScreenShareAudioMode(sharedPreferences.getInt(key, ScreenShareAudioHelper.MODE_BOTH));
                }
            }
            if (Preferences.PREF_CONNECTION_HEAR_MYSELF.equals(key)) {
                TeamTalkService.this.applyHearMyselfSubscription();
            }
            if (Preferences.PREF_GENERAL_CLIENTNAME.equals(key) || Preferences.PREF_GENERAL_NICKNAME.equals(key)) {
                if (TeamTalkService.this.ttclient != null && (TeamTalkService.this.ttclient.getFlags() & 2) != 0) {
                    TeamTalkService.this.login();
                }
            }
            if (Preferences.PREF_SOUNDSYSTEM_DUCKING_ENABLED.equals(key) || Preferences.PREF_SOUNDSYSTEM_DUCKING_LEVEL.equals(key) || Preferences.PREF_SOUNDSYSTEM_DUCKING_TRIGGER.equals(key)) {
                TeamTalkService.this.checkAudioDucking();
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
    private int savedPhoneCallStatus = 0;

    private void handleCallStateChanged(int state) {
        if (this.ttclient == null) return;
        User myself = this.users.get(Integer.valueOf(this.ttclient.getMyUserID()));
        if (myself == null) {
            return;
        }
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                if (this.inPhoneCall) {
                    if (this.voxSuspended) {
                        enableVoiceActivation(true);
                    } else if (this.txSuspended) {
                        enableVoiceTransmission(true);
                    }
                    setMute(this.permanentMuteState);
                    if ((1 & this.savedPhoneCallStatus) == 0) {
                        this.ttclient.doChangeStatus(myself.nStatusMode & (-2), myself.szStatusMsg);
                    }
                    this.inPhoneCall = false;
                    scheduleReconnectBluetoothScoAfterCall();
                }
                break;
            case TelephonyManager.CALL_STATE_RINGING:
            case TelephonyManager.CALL_STATE_OFFHOOK:
                if (!this.inPhoneCall) {
                    this.inPhoneCall = true;
                    if (!isMute()) {
                        this.ttclient.setSoundOutputMute(true);
                        this.currentMuteState = true;
                    }
                    if (isVoiceActivationEnabled()) {
                        this.voxSuspended = true;
                        enableVoiceActivation(false);
                    } else if (isVoiceTransmissionEnabled()) {
                        this.txSuspended = true;
                        enableVoiceTransmission(false);
                    }
                    this.savedPhoneCallStatus = myself.nStatusMode;
                    if ((this.savedPhoneCallStatus & 1) == 0) {
                        this.ttclient.doChangeStatus(1 | this.savedPhoneCallStatus, myself.szStatusMsg);
                    }
                }
                break;
        }
    }

    private final PhoneStateListener phoneStateListener = new PhoneStateListener() { 
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            handleCallStateChanged(state);
        }
    };

    private static class ServiceTelephonyCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        private final TeamTalkService service;
        ServiceTelephonyCallback(TeamTalkService service) {
            this.service = service;
        }
        @Override
        public void onCallStateChanged(int state) {
            service.handleCallStateChanged(state);
        }
    }

    private Object telephonyCallbackInstance = null;
    private boolean isRecording = false;
    private boolean isManualRecordingActive = false;
    private boolean isContinuousSessionActive = false;
    private boolean isRecordingPausedDueToSilence = false;
    private boolean isManualRecordingPaused = false;
    private long recordingStartTimeMs = 0;
    private long recordingAccumulatedDurationMs = 0;
    private long lastVoiceActivityTime = 0;
    private final Handler recordingSilenceHandler = new Handler(Looper.getMainLooper());
    private final Runnable silenceCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkSilenceTimeout();
            if (TeamTalkService.this.isRecording || TeamTalkService.this.isRecordingPausedDueToSilence) {
                TeamTalkService.this.recordingSilenceHandler.postDelayed(this, 2000);
            }
        }
    };
    private volatile boolean manualDisconnect = false;
    private File currentRecordingFile = null;
    private final List<File> recordingSessionSegments = new ArrayList<>();
    private File masterRecordingFile = null;
    private int recordingAudioFileFormat = AudioFileFormat.AFF_WAVE_FORMAT;
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

    public interface AudioDuckingListener {
        void onAudioDuckingChanged(boolean ducked, float duckFactor);
    }
    private final List<AudioDuckingListener> audioDuckingListeners = new CopyOnWriteArrayList<>();
    private final Set<Integer> talkingPeerUserIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> originalMediaFileVolumes = new java.util.concurrent.ConcurrentHashMap<>();
    private boolean isMyselfTalking = false;
    private boolean isAudioDucked = false;
    private final Handler duckingHandler = new Handler(Looper.getMainLooper());
    private final Runnable unduckRunnable = new Runnable() {
        @Override
        public void run() {
            setAudioDucked(false);
        }
    };
    private final Runnable duckingWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            checkAudioDucking();
        }
    };

    public void addAudioDuckingListener(AudioDuckingListener listener) {
        if (listener != null && !audioDuckingListeners.contains(listener)) {
            audioDuckingListeners.add(listener);
        }
    }

    public void removeAudioDuckingListener(AudioDuckingListener listener) {
        audioDuckingListeners.remove(listener);
    }

    public boolean isAudioDucked() {
        return isAudioDucked;
    }

    public float getAudioDuckingFactor() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        int levelPercent = 20;
        try {
            levelPercent = Integer.parseInt(pref.getString(Preferences.PREF_SOUNDSYSTEM_DUCKING_LEVEL, "20"));
        } catch (Exception ignored) {}
        return levelPercent / 100.0f;
    }

    public synchronized void checkAudioDucking() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!pref.getBoolean(Preferences.PREF_SOUNDSYSTEM_DUCKING_ENABLED, false)) {
            if (isAudioDucked) {
                duckingHandler.removeCallbacks(unduckRunnable);
                duckingHandler.removeCallbacks(duckingWatchdogRunnable);
                setAudioDucked(false);
            }
            return;
        }

        if (this.mychannel == null || this.ttclient == null) {
            this.talkingPeerUserIds.clear();
        } else {
            this.talkingPeerUserIds.removeIf(uid -> {
                User u = this.users.get(uid);
                return u == null || u.nChannelID != this.mychannel.nChannelID || (u.uUserState & 1) == 0;
            });
        }

        String trigger = pref.getString(Preferences.PREF_SOUNDSYSTEM_DUCKING_TRIGGER, "all");
        boolean voiceActive = false;
        boolean peerTalking = !talkingPeerUserIds.isEmpty();
        if ("all".equals(trigger)) {
            voiceActive = isMyselfTalking || peerTalking;
        } else if ("tx".equals(trigger)) {
            voiceActive = isMyselfTalking;
        } else if ("rx".equals(trigger)) {
            voiceActive = peerTalking;
        }

        if (voiceActive) {
            duckingHandler.removeCallbacks(unduckRunnable);
            if (!isAudioDucked) {
                setAudioDucked(true);
            }
            duckingHandler.removeCallbacks(duckingWatchdogRunnable);
            duckingHandler.postDelayed(duckingWatchdogRunnable, 3500);
        } else {
            duckingHandler.removeCallbacks(duckingWatchdogRunnable);
            if (isAudioDucked) {
                duckingHandler.removeCallbacks(unduckRunnable);
                duckingHandler.postDelayed(unduckRunnable, 1000);
            }
        }
    }

    private synchronized void setAudioDucked(boolean duck) {
        if (this.isAudioDucked == duck) {
            return;
        }
        this.isAudioDucked = duck;
        float factor = getAudioDuckingFactor();

        for (AudioDuckingListener listener : audioDuckingListeners) {
            try {
                listener.onAudioDuckingChanged(duck, factor);
            } catch (Exception e) {
                Log.e("bearware", "Error notifying ducking listener", e);
            }
        }

        if (this.ttclient != null) {
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int defaultMf = pref.getInt(Preferences.PREF_SOUNDSYSTEM_MEDIAFILE_VOLUME, 50);
            if (duck) {
                if (this.mychannel != null) {
                    for (User u : this.users.values()) {
                        if (u.nChannelID == this.mychannel.nChannelID) {
                            int basePercent;
                            if (originalMediaFileVolumes.containsKey(u.nUserID)) {
                                basePercent = originalMediaFileVolumes.get(u.nUserID);
                            } else {
                                basePercent = u.nVolumeMediaFile > 0 ? Utils.refVolumeToPercent(u.nVolumeMediaFile) : defaultMf;
                                originalMediaFileVolumes.put(u.nUserID, basePercent);
                            }
                            int targetPercent = Math.max(0, Math.min(100, Math.round(basePercent * factor)));
                            this.ttclient.setUserVolume(u.nUserID, 4, Utils.refVolume(targetPercent));
                        }
                    }
                }
            } else {
                if (this.mychannel != null) {
                    for (User u : this.users.values()) {
                        if (u.nChannelID == this.mychannel.nChannelID) {
                            int origVol = originalMediaFileVolumes.containsKey(u.nUserID)
                                    ? originalMediaFileVolumes.get(u.nUserID)
                                    : (u.nVolumeMediaFile > 0 ? Utils.refVolumeToPercent(u.nVolumeMediaFile) : defaultMf);
                            this.ttclient.setUserVolume(u.nUserID, 4, Utils.refVolume(origVol));
                        }
                    }
                }
                for (Map.Entry<Integer, Integer> entry : originalMediaFileVolumes.entrySet()) {
                    int uid = entry.getKey();
                    int origVol = entry.getValue();
                    this.ttclient.setUserVolume(uid, 4, Utils.refVolume(origVol));
                }
                originalMediaFileVolumes.clear();
            }
        }
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
        this.talkingPeerUserIds.clear();
        this.isMyselfTalking = false;
        this.duckingHandler.removeCallbacks(this.unduckRunnable);
        this.duckingHandler.removeCallbacks(this.duckingWatchdogRunnable);
        if (this.isAudioDucked) {
            setAudioDucked(false);
        }
        this.originalMediaFileVolumes.clear();
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
        this.sessionPrivateDialogs.clear();
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
        sInstance = this;
        try {
            org.nekit.ttproplus.plugin.PluginManager.getInstance().init(getApplicationContext(), this);
        } catch (Throwable t) {
            android.util.Log.e("TeamTalkService", "Failed to init PluginManager", t);
        }
        this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        TeamTalk5.loadLibrary();
        TeamTalk5.setLicenseInformation("", "");
        this.ttclient = new TeamTalk5();
        this.experimentalAudioCapture = new ExperimentalAudioCapture(this);
        this.experimentalAudioCapture.setService(this);
        this.experimentalAudioCapture.setVoiceActivationListener(new ExperimentalAudioCapture.VoiceActivationListener() {
            @Override
            public void onVoiceActivation(TeamTalkBase client, boolean transmitting) {
                if (voiceActivationRequested) {
                    adjustMuteOnTx(transmitting);
                    isMyselfTalking = transmitting;
                    checkAudioDucking();
                    updateFloatingWindow();
                    if (onVoiceTransmissionToggleListener != null) {
                        onVoiceTransmissionToggleListener.onVoiceActivationStateChanged(transmitting);
                    }
                    TeamTalkService.this.onVoiceActivation(transmitting);
                }
            }
        });
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
        this.experimentalAudioCapture = new ExperimentalAudioCapture(this);
        this.experimentalAudioCapture.setService(this);
        this.experimentalAudioCapture.setVoiceActivationListener(new ExperimentalAudioCapture.VoiceActivationListener() {
            @Override
            public void onVoiceActivation(TeamTalkBase client, boolean active) {
                adjustMuteOnTx(active);
                if (active) {
                    checkAndTriggerAutoRecord();
                }
            }
        });
        this.screenShareManager = new ScreenShareManager(this, this.ttclient);
        this.mFloatingWindowManager = new FloatingWindowManager(this);
        this.mFloatingWindowManager.checkAndShow();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(this.mPrefListener);
        MicrophoneEqualizer.loadFromPreferences(prefs);
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
        if (sInstance == this) sInstance = null;
        try {
            org.nekit.ttproplus.plugin.PluginManager.getInstance().setService(null);
        } catch (Throwable ignored) {}
        this.manualDisconnect = true;
        this.isSeamlessReconnecting = false;
        if (this.reconnectHandler != null) {
            this.reconnectHandler.removeCallbacksAndMessages(null);
        }
        if (this.recordingSilenceHandler != null) {
            this.recordingSilenceHandler.removeCallbacksAndMessages(null);
        }
        stopScreenShare();
        if (this.screenShareManager != null) {
            this.screenShareManager.stopShare();
            this.screenShareManager = null;
        }
        if (this.experimentalAudioCapture != null) {
            this.experimentalAudioCapture.shutdown();
            this.experimentalAudioCapture = null;
        }
        stopRecording();
        if (this.ttclient != null && this.isStreamingMedia) {
            this.ttclient.stopStreamingMediaFileToChannel();
            this.isStreamingMedia = false;
        }
        unregisterNetworkCallback();
        stopEventLoop();
        this.mEventHandler.unregisterListener(this);
        disablePhoneCallReaction();
        unwatchBluetoothHeadset();
        releaseServiceLocks();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this.mPrefListener);
        if (this.mFloatingWindowManager != null) {
            this.mFloatingWindowManager.hide();
            this.mFloatingWindowManager = null;
        }
        AudioEffectsManager.getInstance(this).release();
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

    public void displayNotification(boolean enabled) {
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
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, UI_CHANNEL_ID)
                        .setSmallIcon(R.drawable.teamtalk_green)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentIntent(PendingIntent.getActivity(this, 0, ui, 201326592))
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .setContentText(getNotificationText())
                        .setShowWhen(false);

                if (isStreamingMedia()) {
                    Intent streamIntent = new Intent(this, org.nekit.ttproplus.gui.StreamMediaActivity.class);
                    PendingIntent streamPi = PendingIntent.getActivity(this, 101, streamIntent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
                    builder.addAction(R.drawable.teamtalk_green, getString(R.string.action_stream_control), streamPi);
                }

                SharedPreferences pPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                if (pPrefs.getBoolean("pref_performance_high_priority", true)) {
                    builder.setPriority(NotificationCompat.PRIORITY_HIGH);
                }

                this.widget = builder.build();
                try {
                    ServiceCompat.startForeground(this, 1, this.widget, getMyForegroundServiceType());
                } catch (SecurityException e) {
                    Log.e("bearware", "SecurityException starting foreground service with type " + getMyForegroundServiceType() + ", falling back to basic types", e);
                    this.isScreenSharingActive = false;
                    try {
                        ServiceCompat.startForeground(this, 1, this.widget, 128 | 2);
                    } catch (Throwable t) {
                        Log.e("bearware", "Fatal fallback starting foreground service", t);
                    }
                }
                return;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, this.widget)
                    .setContentText(getNotificationText());
            if (isStreamingMedia()) {
                Intent streamIntent = new Intent(this, org.nekit.ttproplus.gui.StreamMediaActivity.class);
                PendingIntent streamPi = PendingIntent.getActivity(this, 101, streamIntent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
                builder.addAction(R.drawable.teamtalk_green, getString(R.string.action_stream_control), streamPi);
            }
            this.widget = builder.build();
            try {
                ServiceCompat.startForeground(this, 1, this.widget, getMyForegroundServiceType());
            } catch (SecurityException e) {
                Log.e("bearware", "SecurityException updating foreground service with type " + getMyForegroundServiceType() + ", falling back to basic types", e);
                this.isScreenSharingActive = false;
                try {
                    ServiceCompat.startForeground(this, 1, this.widget, 128 | 2);
                } catch (Throwable t) {
                    Log.e("bearware", "Fatal fallback updating foreground service", t);
                }
            }
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

    public int getMyForegroundServiceType() {
        int type = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            if (this.isScreenSharingActive) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            }
        }
        return type;
    }

    public void refreshForegroundServiceType() {
        if (this.widget == null) {
            displayNotification(true);
            return;
        }
        try {
            int type = getMyForegroundServiceType();
            ServiceCompat.startForeground(this, 1, this.widget, type);
        } catch (SecurityException e) {
            Log.e("bearware", "SecurityException updating foreground service with type " + getMyForegroundServiceType() + ", falling back to basic types", e);
            if (this.isScreenSharingActive) {
                this.isScreenSharingActive = false;
                try {
                    int basicType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                            (ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) : 0;
                    ServiceCompat.startForeground(this, 1, this.widget, basicType);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.w("bearware", "Unable to refresh foreground service type", t);
        }
    }

    public synchronized void acquireServiceLocks() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            boolean enableWakelock = prefs.getBoolean("pref_performance_cpu_wakelock", true);
            boolean enableWifiLock = prefs.getBoolean("pref_performance_wifi_lock", true);

            if (enableWakelock) {
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
            }

            if (enableWifiLock) {
                if (this.mServiceWifiLock == null) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wm != null) {
                        int wifiMode = Build.VERSION.SDK_INT >= 29 ? 4 /* WifiManager.WIFI_MODE_FULL_LOW_LATENCY */ : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                        this.mServiceWifiLock = wm.createWifiLock(wifiMode, "ttproplus:ServiceWifiLock");
                        this.mServiceWifiLock.setReferenceCounted(false);
                    }
                }
                if (this.mServiceWifiLock != null && !this.mServiceWifiLock.isHeld()) {
                    this.mServiceWifiLock.acquire();
                }
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
        if (this.telephonyManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (this.telephonyCallbackInstance == null) {
                    this.telephonyCallbackInstance = new ServiceTelephonyCallback(this);
                }
                try {
                    this.telephonyManager.registerTelephonyCallback(getMainExecutor(), (TelephonyCallback) this.telephonyCallbackInstance);
                    this.listeningPhoneStateChanges = true;
                } catch (Exception e) {
                    Log.w("bearware", "Failed to register TelephonyCallback", e);
                }
            } else {
                this.telephonyManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
                this.listeningPhoneStateChanges = true;
            }
        }
    }

    public void disablePhoneCallReaction() {
        if (this.listeningPhoneStateChanges && this.telephonyManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && this.telephonyCallbackInstance != null) {
                try {
                    this.telephonyManager.unregisterTelephonyCallback((TelephonyCallback) this.telephonyCallbackInstance);
                } catch (Exception ignored) {}
            } else {
                this.telephonyManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
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

    public void reinitSoundInputDevice() {
        applyConfiguredInputDeviceSelection();
    }

    public boolean isRecording() {
        return this.isRecording || this.isRecordingPausedDueToSilence || (this.isContinuousSessionActive && this.isManualRecordingActive) || this.isManualRecordingPaused;
    }

    public boolean isRecordingActive() {
        return this.isRecording;
    }

    public boolean isRecordingPaused() {
        return this.isManualRecordingPaused || this.isRecordingPausedDueToSilence;
    }

    public long getRecordingDurationMs() {
        if (this.isRecording) {
            return this.recordingAccumulatedDurationMs + Math.max(0, SystemClock.elapsedRealtime() - this.recordingStartTimeMs);
        } else if (this.isManualRecordingPaused) {
            return this.recordingAccumulatedDurationMs;
        }
        return 0;
    }

    public void pauseRecording() {
        if (this.isRecording && this.ttclient != null) {
            this.recordingAccumulatedDurationMs += Math.max(0, SystemClock.elapsedRealtime() - this.recordingStartTimeMs);
            this.ttclient.stopRecordingMuxedAudioFile();
            this.isRecording = false;
            this.isManualRecordingPaused = true;
            if (this.currentRecordingFile != null && this.currentRecordingFile.exists() && this.currentRecordingFile.length() > 44) {
                if (!this.recordingSessionSegments.contains(this.currentRecordingFile)) {
                    this.recordingSessionSegments.add(this.currentRecordingFile);
                }
            }
            this.currentRecordingFile = null;
            showRecordingToast(R.string.recording_silence_paused);
        }
    }

    public void resumeRecording() {
        if (this.isManualRecordingPaused && this.mychannel != null) {
            this.isManualRecordingPaused = false;
            this.recordingStartTimeMs = SystemClock.elapsedRealtime();
            startRecordingFileInternal(false);
            showRecordingToast(R.string.recording_silence_resumed);
        }
    }

    public void toggleRecordingPause() {
        if (this.isManualRecordingPaused) {
            resumeRecording();
        } else if (this.isRecording) {
            pauseRecording();
        }
    }

    public File getCurrentRecordingFile() {
        return this.currentRecordingFile != null ? this.currentRecordingFile : this.masterRecordingFile;
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

    private void checkSilenceTimeout() {
        if (!this.isRecording || this.mychannel == null) {
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String silenceVal = prefs.getString(Preferences.PREF_RECORDING_SILENCE_PAUSE, "0");
        int timeoutSec = 0;
        try {
            timeoutSec = Integer.parseInt(silenceVal);
        } catch (Exception ignored) {}
        if (timeoutSec <= 0) {
            return;
        }

        boolean someoneTalking = false;
        if (isVoiceTransmissionEnabled()) {
            someoneTalking = true;
        } else if (this.users != null) {
            for (User u : this.users.values()) {
                if (u != null && u.nChannelID == this.mychannel.nChannelID && (u.uUserState & 1) != 0) {
                    someoneTalking = true;
                    break;
                }
            }
        }

        long now = SystemClock.elapsedRealtime();
        if (someoneTalking) {
            this.lastVoiceActivityTime = now;
        } else if (this.lastVoiceActivityTime > 0 && (now - this.lastVoiceActivityTime >= timeoutSec * 1000L)) {
            Log.d("bearware", "Silence timeout reached (" + timeoutSec + "s), pausing recording");
            this.recordingAccumulatedDurationMs += Math.max(0, SystemClock.elapsedRealtime() - this.recordingStartTimeMs);
            this.ttclient.stopRecordingMuxedAudioFile();
            this.isRecording = false;
            this.isRecordingPausedDueToSilence = true;
            if (this.currentRecordingFile != null && this.currentRecordingFile.exists() && this.currentRecordingFile.length() > 44) {
                if (!this.recordingSessionSegments.contains(this.currentRecordingFile)) {
                    this.recordingSessionSegments.add(this.currentRecordingFile);
                }
            }
            this.currentRecordingFile = null;
            showRecordingToast(R.string.recording_silence_paused);
        }
    }

    public void onVoiceDetected() {
        this.lastVoiceActivityTime = SystemClock.elapsedRealtime();
        if (this.isRecordingPausedDueToSilence && this.mychannel != null) {
            Log.d("bearware", "Voice detected, resuming paused recording");
            this.isRecordingPausedDueToSilence = false;
            this.recordingStartTimeMs = SystemClock.elapsedRealtime();
            startRecordingFileInternal(false);
            showRecordingToast(R.string.recording_silence_resumed);
        } else if (this.pendingAutoRecord && !this.isRecording && this.mychannel != null) {
            this.pendingAutoRecord = false;
            startRecording();
        }
    }

    public void checkAndTriggerAutoRecord() {
        onVoiceDetected();
    }

    public void startRecording() {
        this.isManualRecordingActive = true;
        this.pendingAutoRecord = false;
        this.isManualRecordingPaused = false;
        this.recordingStartTimeMs = SystemClock.elapsedRealtime();
        this.recordingAccumulatedDurationMs = 0;
        this.recordingSessionSegments.clear();
        this.masterRecordingFile = null;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        this.isContinuousSessionActive = "continuous".equals(prefs.getString(Preferences.PREF_RECORDING_SESSION_MODE, "stop"));
        this.lastVoiceActivityTime = SystemClock.elapsedRealtime();
        this.isRecordingPausedDueToSilence = false;
        startRecordingFileInternal(true);
        this.recordingSilenceHandler.removeCallbacks(this.silenceCheckRunnable);
        this.recordingSilenceHandler.postDelayed(this.silenceCheckRunnable, 2000);
    }

    private void startRecordingFileInternal(boolean isNewSession) {
        if (this.isRecording || this.mychannel == null) {
            return;
        }
        String srvName = (this.ttserver != null && !TextUtils.isEmpty(this.ttserver.servername)) ? this.ttserver.servername : "Server";
        String chanName = (this.mychannel != null && !TextUtils.isEmpty(this.mychannel.szName)) ? this.mychannel.szName : "Channel";
        File dir = Utils.getRecordingsDirectory(getApplicationContext(), srvName, chanName);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean multitrack = prefs.getBoolean(Preferences.PREF_RECORDING_MULTITRACK, false);
        if (multitrack) {
            SimpleDateFormat sdfFolder = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            File sessionDir = new File(dir, "Session_" + Utils.sanitizeFilename(chanName) + "_" + sdfFolder.format(new Date()));
            if (!sessionDir.exists()) {
                sessionDir.mkdirs();
            }
            if (sessionDir.exists()) {
                dir = sessionDir;
            }
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
        String prefix = multitrack ? "Master_" : (Utils.sanitizeFilename(chanName) + "_");
        String recordingFormat = prefs.getString(Preferences.PREF_RECORDING_FORMAT, "wav");
        int audioFileFormat = AudioFileFormat.AFF_WAVE_FORMAT;
        String extension = ".wav";
        if ("mp3".equals(recordingFormat)) {
            int bitrate = 128;
            try {
                bitrate = Integer.parseInt(prefs.getString(Preferences.PREF_RECORDING_MP3_BITRATE, "128"));
            } catch (NumberFormatException ignored) {}
            switch (bitrate) {
                case 16: audioFileFormat = AudioFileFormat.AFF_MP3_16KBIT_FORMAT; break;
                case 32: audioFileFormat = AudioFileFormat.AFF_MP3_32KBIT_FORMAT; break;
                case 64: audioFileFormat = AudioFileFormat.AFF_MP3_64KBIT_FORMAT; break;
                case 256: audioFileFormat = AudioFileFormat.AFF_MP3_256KBIT_FORMAT; break;
                case 320: audioFileFormat = AudioFileFormat.AFF_MP3_320KBIT_FORMAT; break;
                default: audioFileFormat = AudioFileFormat.AFF_MP3_128KBIT_FORMAT; break;
            }
            extension = ".mp3";
        } else if ("codec".equals(recordingFormat)) {
            audioFileFormat = AudioFileFormat.AFF_CHANNELCODEC_FORMAT;
            extension = ".ogg";
        }
        this.recordingAudioFileFormat = audioFileFormat;

        File targetFile;
        if (isNewSession || this.masterRecordingFile == null) {
            String name = prefix + sdf.format(new Date()) + extension;
            targetFile = new File(dir, name);
            this.masterRecordingFile = targetFile;
            this.recordingSessionSegments.clear();
        } else {
            File parentDir = this.masterRecordingFile.getParentFile();
            if (parentDir == null || !parentDir.exists()) {
                parentDir = dir;
            }
            String segName = ".seg_" + System.currentTimeMillis() + "_" + this.masterRecordingFile.getName();
            targetFile = new File(parentDir, segName);
        }

        this.currentRecordingFile = targetFile;
        this.isRecording = this.ttclient.startRecordingMuxedAudioFile(this.mychannel.audiocodec, targetFile.getAbsolutePath(), audioFileFormat);

        if (this.isRecording) {
            Log.d("bearware", "Recording started: " + targetFile.getAbsolutePath() + " (isNew=" + isNewSession + ")");
            if (isNewSession) {
                showRecordingToast(getString(R.string.recording_started, new Object[]{targetFile.getName()}));
            }
        } else {
            Log.e("bearware", "Failed to start recording");
            if (isNewSession) {
                showRecordingToast(R.string.recording_start_failed);
            }
            this.currentRecordingFile = null;
        }
    }

    public File stopRecording() {
        this.isManualRecordingActive = false;
        this.isContinuousSessionActive = false;
        this.isRecordingPausedDueToSilence = false;
        this.isManualRecordingPaused = false;
        this.recordingStartTimeMs = 0;
        this.recordingAccumulatedDurationMs = 0;
        this.recordingSilenceHandler.removeCallbacks(this.silenceCheckRunnable);

        if (this.isRecording) {
            this.ttclient.stopRecordingMuxedAudioFile();
            this.isRecording = false;
            if (this.currentRecordingFile != null && this.currentRecordingFile.exists() && this.currentRecordingFile.length() > 44) {
                if (!this.recordingSessionSegments.contains(this.currentRecordingFile)) {
                    this.recordingSessionSegments.add(this.currentRecordingFile);
                }
            }
        } else if (this.currentRecordingFile != null && this.currentRecordingFile.exists() && this.currentRecordingFile.length() > 44) {
            if (!this.recordingSessionSegments.contains(this.currentRecordingFile)) {
                this.recordingSessionSegments.add(this.currentRecordingFile);
            }
        }
        this.currentRecordingFile = null;

        if (this.recordingSessionSegments.isEmpty() && this.masterRecordingFile != null && this.masterRecordingFile.exists() && this.masterRecordingFile.length() > 44) {
            this.recordingSessionSegments.add(this.masterRecordingFile);
        }

        if (this.recordingSessionSegments.isEmpty()) {
            this.masterRecordingFile = null;
            return null;
        }

        File finalRecordedFile = this.masterRecordingFile;
        if (finalRecordedFile == null) {
            finalRecordedFile = this.recordingSessionSegments.get(0);
        }

        if (this.recordingSessionSegments.size() > 1) {
            mergeAudioSegments(new ArrayList<>(this.recordingSessionSegments), finalRecordedFile, this.recordingAudioFileFormat);
        }

        this.recordingSessionSegments.clear();
        this.masterRecordingFile = null;

        if (finalRecordedFile == null || !finalRecordedFile.exists()) {
            return null;
        }

        if (finalRecordedFile.length() <= 1024) {
            Log.d("bearware", "Discarding empty recording (" + finalRecordedFile.length() + " bytes): " + finalRecordedFile.getName());
            finalRecordedFile.delete();
            return null;
        }

        try {
            MediaScannerConnection.scanFile(getApplicationContext(), new String[]{finalRecordedFile.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Log.e("bearware", "Failed to scan recorded file into media store", e);
        }

        Log.d("bearware", "Recording stopped: " + finalRecordedFile.getName());
        showRecordingToast(R.string.recording_stopped);
        return finalRecordedFile;
    }

    private void mergeAudioSegments(List<File> segments, File outputFile, int audioFormat) {
        if (segments == null || segments.isEmpty() || outputFile == null) {
            return;
        }

        Log.d("bearware", "Merging " + segments.size() + " segments into " + outputFile.getAbsolutePath());

        try {
            if (audioFormat == AudioFileFormat.AFF_WAVE_FORMAT) {
                mergeWavSegments(segments, outputFile);
            } else if (audioFormat == AudioFileFormat.AFF_CHANNELCODEC_FORMAT) {
                mergeGenericSegments(segments, outputFile);
            } else {
                mergeMp3Segments(segments, outputFile);
            }
        } catch (Exception e) {
            Log.e("bearware", "Failed to merge recording segments", e);
        } finally {
            for (File seg : segments) {
                if (seg != null && seg.exists() && !seg.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
                    try {
                        seg.delete();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private static int readIntLE(RandomAccessFile raf) throws IOException {
        int b1 = raf.read();
        int b2 = raf.read();
        int b3 = raf.read();
        int b4 = raf.read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new java.io.EOFException();
        }
        return (b1 & 0xFF) | ((b2 & 0xFF) << 8) | ((b3 & 0xFF) << 16) | ((b4 & 0xFF) << 24);
    }

    private static void writeIntLE(RandomAccessFile raf, int value) throws IOException {
        raf.write(value & 0xFF);
        raf.write((value >> 8) & 0xFF);
        raf.write((value >> 16) & 0xFF);
        raf.write((value >> 24) & 0xFF);
    }

    private static long findDataChunkSizeOffset(RandomAccessFile raf) throws IOException {
        raf.seek(12);
        long fileLen = raf.length();
        while (raf.getFilePointer() + 8 <= fileLen) {
            byte[] tagBytes = new byte[4];
            raf.readFully(tagBytes);
            int chunkSize = readIntLE(raf);
            String chunkTag = new String(tagBytes, 0, 4, "US-ASCII");
            if ("data".equalsIgnoreCase(chunkTag)) {
                return raf.getFilePointer() - 4;
            }
            if (chunkSize < 0) {
                break;
            }
            long nextOffset = raf.getFilePointer() + chunkSize + (chunkSize & 1);
            if (nextOffset > fileLen || nextOffset < raf.getFilePointer()) {
                break;
            }
            raf.seek(nextOffset);
        }
        return -1;
    }

    private static void copyFileContents(File src, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    private void mergeWavSegments(List<File> segments, File outputFile) throws IOException {
        File firstSeg = segments.get(0);
        if (!firstSeg.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
            copyFileContents(firstSeg, outputFile);
        }

        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            long dataSizeOffset = findDataChunkSizeOffset(raf);
            long totalPcmSize;
            long pcmStart;
            if (dataSizeOffset >= 0) {
                raf.seek(dataSizeOffset);
                totalPcmSize = ((long) readIntLE(raf)) & 0xFFFFFFFFL;
                pcmStart = dataSizeOffset + 4;
            } else {
                pcmStart = 44;
                totalPcmSize = Math.max(0, raf.length() - 44);
                dataSizeOffset = 40;
            }

            raf.seek(pcmStart + totalPcmSize);

            for (int i = 1; i < segments.size(); i++) {
                File seg = segments.get(i);
                if (seg == null || !seg.exists() || seg.length() <= 44) {
                    continue;
                }
                try (RandomAccessFile segRaf = new RandomAccessFile(seg, "r")) {
                    long segDataSizeOffset = findDataChunkSizeOffset(segRaf);
                    long segPcmStart;
                    long segDataSize;
                    if (segDataSizeOffset >= 0) {
                        segRaf.seek(segDataSizeOffset);
                        segDataSize = ((long) readIntLE(segRaf)) & 0xFFFFFFFFL;
                        segPcmStart = segDataSizeOffset + 4;
                        if (segPcmStart + segDataSize > segRaf.length()) {
                            segDataSize = Math.max(0, segRaf.length() - segPcmStart);
                        }
                    } else {
                        segPcmStart = 44;
                        segDataSize = Math.max(0, segRaf.length() - 44);
                    }

                    if (segDataSize > 0) {
                        segRaf.seek(segPcmStart);
                        byte[] buffer = new byte[65536];
                        long bytesLeft = segDataSize;
                        while (bytesLeft > 0) {
                            int toRead = (int) Math.min(buffer.length, bytesLeft);
                            int read = segRaf.read(buffer, 0, toRead);
                            if (read <= 0) break;
                            raf.write(buffer, 0, read);
                            bytesLeft -= read;
                            totalPcmSize += read;
                        }
                    }
                }
            }

            long totalFileLen = raf.length();
            raf.seek(4);
            writeIntLE(raf, (int) (totalFileLen - 8));
            raf.seek(dataSizeOffset);
            writeIntLE(raf, (int) totalPcmSize);
        }
    }

    private void mergeMp3Segments(List<File> segments, File outputFile) throws IOException {
        File firstSeg = segments.get(0);
        if (!firstSeg.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
            copyFileContents(firstSeg, outputFile);
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile, true);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            for (int i = 1; i < segments.size(); i++) {
                File seg = segments.get(i);
                if (seg == null || !seg.exists() || seg.length() == 0) {
                    continue;
                }
                try (FileInputStream fis = new FileInputStream(seg);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    bis.mark(10);
                    byte[] header = new byte[10];
                    int readHdr = bis.read(header);
                    if (readHdr == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                        int tagSize = ((header[6] & 0x7F) << 21)
                                | ((header[7] & 0x7F) << 14)
                                | ((header[8] & 0x7F) << 7)
                                | (header[9] & 0x7F);
                        long toSkip = tagSize;
                        while (toSkip > 0) {
                            long skipped = bis.skip(toSkip);
                            if (skipped <= 0) break;
                            toSkip -= skipped;
                        }
                    } else {
                        bis.reset();
                    }

                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = bis.read(buffer)) > 0) {
                        bos.write(buffer, 0, read);
                    }
                }
            }
            bos.flush();
        }
    }

    private void mergeGenericSegments(List<File> segments, File outputFile) throws IOException {
        File firstSeg = segments.get(0);
        if (!firstSeg.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
            copyFileContents(firstSeg, outputFile);
        }

        try (FileOutputStream fos = new FileOutputStream(outputFile, true);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            for (int i = 1; i < segments.size(); i++) {
                File seg = segments.get(i);
                if (seg == null || !seg.exists() || seg.length() == 0) {
                    continue;
                }
                try (FileInputStream fis = new FileInputStream(seg);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = bis.read(buffer)) > 0) {
                        bos.write(buffer, 0, read);
                    }
                }
            }
            bos.flush();
        }
    }

    public boolean shouldShowRecordingDialog() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(Preferences.PREF_RECORDING_SHOW_DIALOG, true);
    }

    private void setMyChannel(Channel chan) {
        this.mychannel = chan;
        this.talkingPeerUserIds.clear();
        checkAudioDucking();
        setupAudioPreprocessor();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String sessionMode = prefs.getString(Preferences.PREF_RECORDING_SESSION_MODE, "stop");
        boolean isAutoContinue = "continuous".equals(sessionMode);

        if (chan != null) {
            if (this.isContinuousSessionActive && this.isManualRecordingActive && isAutoContinue) {
                this.isRecordingPausedDueToSilence = false;
                startRecordingFileInternal(false);
                showRecordingToast(getString(R.string.recording_session_resumed, chan.szName));
            } else if (prefs.getBoolean(Preferences.PREF_RECORDING_AUTO, false)) {
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
                this.isManualRecordingActive = false;
                this.isContinuousSessionActive = false;
                this.pendingAutoRecord = false;
            }
            return;
        }

        this.pendingAutoRecord = false;
        if (this.isContinuousSessionActive && this.isManualRecordingActive && isAutoContinue) {
            if (this.isRecording) {
                this.ttclient.stopRecordingMuxedAudioFile();
                this.isRecording = false;
            }
        } else {
            stopRecording();
        }
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

    public SharedPreferences getSessionPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    private int getNextExperimentalVoiceStreamId() {
        int next = this.nextExperimentalVoiceStreamId + 1;
        this.nextExperimentalVoiceStreamId = next;
        if (next <= 0) {
            this.nextExperimentalVoiceStreamId = 1;
            return 1;
        }
        return next;
    }

    private float getExperimentalGainMultiplier() {
        int gain = getSessionPreferences().getInt(Preferences.PREF_SOUNDSYSTEM_MICROPHONEGAIN, SoundLevel.SOUND_GAIN_DEFAULT);
        return gain <= 0 ? 0.0f : (float) Math.min(4.0d, Math.max(0.0d, (double) gain / (double) SoundLevel.SOUND_GAIN_DEFAULT));
    }

    private int getExperimentalPreferredSampleRate() {
        if (this.mychannel != null && this.mychannel.audiocodec != null) {
            AudioCodec codec = this.mychannel.audiocodec;
            if (codec.nCodec == 1 && codec.speex != null) {
                return codec.speex.nBandmode == 0 ? 8000 : (codec.speex.nBandmode == 2 ? 32000 : 16000);
            }
            if (codec.nCodec == 2 && codec.speex_vbr != null) {
                return codec.speex_vbr.nBandmode == 0 ? 8000 : (codec.speex_vbr.nBandmode == 2 ? 32000 : 16000);
            }
            if (codec.nCodec == 3 && codec.opus != null && codec.opus.nSampleRate > 0) {
                return codec.opus.nSampleRate;
            }
        }
        return OpusConstants.DEFAULT_OPUS_SAMPLERATE;
    }

    private int getExperimentalFrameDurationMs() {
        if (this.mychannel != null && this.mychannel.audiocodec != null) {
            AudioCodec codec = this.mychannel.audiocodec;
            if (codec.nCodec == 1 && codec.speex != null) {
                return Math.max(20, codec.speex.nTxIntervalMSec);
            }
            if (codec.nCodec == 2 && codec.speex_vbr != null) {
                return Math.max(20, codec.speex_vbr.nTxIntervalMSec);
            }
            if (codec.nCodec == 3 && codec.opus != null) {
                return Math.max(10, codec.opus.nTxIntervalMSec);
            }
        }
        return 20;
    }

    private void applyExperimentalAudioSessionMode() {
        if (this.audioManager == null || this.experimentalAudioSessionModeApplied) {
            return;
        }
        SharedPreferences prefs = getSessionPreferences();
        try {
            this.audioManager.setMode(AudioManager.MODE_NORMAL);
            boolean speaker = prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_SPEAKERPHONE, false);
            boolean wired = this.audioManager.isWiredHeadsetOn();
            boolean sco = this.bluetoothHeadsetHelper != null && this.bluetoothHeadsetHelper.isOnHeadsetSco();
            this.audioManager.setSpeakerphoneOn(speaker && !wired && !sco);
            this.experimentalAudioSessionModeApplied = true;
        } catch (Throwable t) {
            Log.w("bearware", "Failed to apply experimental audio session mode", t);
        }
    }

    private void clearExperimentalAudioSessionModeIfIdle() {
        if (!this.experimentalAudioSessionModeApplied || isVoiceTransmitting() || isVoiceActivationEnabled() || this.experimentalVoiceTransmissionRequested) {
            return;
        }
        try {
            if (this.audioManager != null) {
                this.audioManager.setMode(AudioManager.MODE_NORMAL);
                this.audioManager.setSpeakerphoneOn(false);
            }
        } catch (Throwable t) {
            Log.w("bearware", "Failed to clear experimental audio session mode", t);
        } finally {
            this.experimentalAudioSessionModeApplied = false;
        }
    }

    public synchronized boolean startVoiceCapture(boolean vox) {
        TeamTalkBase client = this.ttclient;
        if (client == null || client.getMyChannelID() <= 0) {
            return false;
        }
        if (this.experimentalAudioCapture == null) {
            this.experimentalAudioCapture = new ExperimentalAudioCapture(this);
            this.experimentalAudioCapture.setService(this);
            this.experimentalAudioCapture.setVoiceActivationListener(new ExperimentalAudioCapture.VoiceActivationListener() {
                @Override
                public void onVoiceActivation(TeamTalkBase client, boolean transmitting) {
                    if (voiceActivationRequested) {
                        adjustMuteOnTx(transmitting);
                        isMyselfTalking = transmitting;
                        checkAudioDucking();
                        updateFloatingWindow();
                        if (onVoiceTransmissionToggleListener != null) {
                            onVoiceTransmissionToggleListener.onVoiceActivationStateChanged(transmitting);
                        }
                        TeamTalkService.this.onVoiceActivation(transmitting);
                    }
                }
            });
        }
        // Always close native input & disable native transmission so OpenSL ES never touches the hardware mic
        client.enableVoiceTransmission(false);
        client.enableVoiceActivation(false);
        client.closeSoundInputDevice();

        applyExperimentalAudioSessionMode();

        SharedPreferences prefs = getSessionPreferences();
        int sampleRate = getExperimentalPreferredSampleRate();
        int frameDurationMs = getExperimentalFrameDurationMs();
        int captureMode = MicrophoneInputHelper.getExperimentalCaptureMode(prefs);
        String inputDeviceId = MicrophoneInputHelper.getExperimentalInputDeviceId(prefs);
        int streamId = getNextExperimentalVoiceStreamId();
        float gainMultiplier = getExperimentalGainMultiplier();
        int voxLevel = prefs.getInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_LEVEL, 10);
        int voxStopDelay = prefs.getInt(Preferences.PREF_SOUNDSYSTEM_VOICEACTIVATION_STOP_DELAY, 500);

        int screenAudioMode = ScreenShareAudioHelper.getAudioMode(prefs);
        this.experimentalAudioCapture.setScreenShareAudioMode(screenAudioMode);
        boolean micEnhance = prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_MIC_ENHANCEMENTS, false);
        this.experimentalAudioCapture.setMicEnhancementEnabled(micEnhance);

        ExperimentalAudioCapture.Config config = new ExperimentalAudioCapture.Config(
                sampleRate,
                frameDurationMs,
                captureMode,
                inputDeviceId,
                streamId,
                gainMultiplier,
                vox,
                voxLevel,
                voxStopDelay
        );
        boolean start = this.experimentalAudioCapture.start(client, config);
        if (!start) {
            clearExperimentalAudioSessionModeIfIdle();
            return false;
        }
        return true;
    }

    public synchronized void stopExperimentalVoiceTransmission(boolean flushBlock) {
        if (this.experimentalAudioCapture != null) {
            this.experimentalAudioCapture.stop(this.ttclient, flushBlock);
        }
    }

    public boolean isVoiceTransmissionEnabled() {
        if (this.experimentalVoiceTransmissionRequested) {
            return true;
        }
        TeamTalkBase client = this.ttclient;
        return client != null && (client.getFlags() & 256) != 0;
    }

    public boolean isVoiceTransmitting() {
        if (this.experimentalVoiceTransmissionRequested) {
            return true;
        }
        ExperimentalAudioCapture cap = this.experimentalAudioCapture;
        if (cap != null && this.ttclient != null && cap.isVoiceTransmitting(this.ttclient)) {
            return true;
        }
        TeamTalkBase client = this.ttclient;
        int flags = client != null ? client.getFlags() : 0;
        return (flags & 256) != 0 || (flags & 24) == 24;
    }

    public boolean isVoiceActivationEnabled() {
        if (this.voiceActivationRequested) {
            return true;
        }
        TeamTalkBase client = this.ttclient;
        return client != null && (client.getFlags() & 24) != 0;
    }

    public boolean isConnected() {
        TeamTalkBase client = this.ttclient;
        return client != null && (client.getFlags() & 2) != 0;
    }

    public void setMute(boolean state) {
        SharedPreferences prefs = getSessionPreferences();
        this.permanentMuteState = state;
        this.currentMuteState = state;
        if (isMute() != this.permanentMuteState && (!prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_MUTE_ON_TRANSMISSION, false) || !isVoiceTransmitting())) {
            this.ttclient.setSoundOutputMute(this.permanentMuteState);
        }
        updateFloatingWindow();
    }

    public void enableVoiceTransmission(boolean enable) {
        if (enable) {
            acquireServiceLocks();
            checkAndTriggerAutoRecord();
            this.experimentalVoiceTransmissionRequested = true;
            this.txSuspended = false;
            startVoiceCapture(false);
        } else {
            this.experimentalVoiceTransmissionRequested = false;
            if (this.voiceActivationRequested) {
                startVoiceCapture(true);
            } else {
                stopExperimentalVoiceTransmission(true);
                clearExperimentalAudioSessionModeIfIdle();
            }
        }
        adjustMuteOnTx(enable || isVoiceTransmitting());
        this.isMyselfTalking = enable || isVoiceTransmitting();
        checkAudioDucking();
        updateFloatingWindow();
    }

    public void enableVoiceActivation(boolean enable) {
        this.voiceActivationRequested = enable;
        if (enable) {
            acquireServiceLocks();
            this.txSuspended = false;
            this.voxSuspended = false;
            if (!this.experimentalVoiceTransmissionRequested) {
                startVoiceCapture(true);
            }
            if (this.onVoiceTransmissionToggleListener != null) {
                this.onVoiceTransmissionToggleListener.onVoiceActivationToggle(true, false);
            }
        } else {
            if (this.onVoiceTransmissionToggleListener != null) {
                this.onVoiceTransmissionToggleListener.onVoiceActivationStateChanged(false);
                this.onVoiceTransmissionToggleListener.onVoiceActivationToggle(false, false);
            }
            if (!this.experimentalVoiceTransmissionRequested) {
                stopExperimentalVoiceTransmission(true);
                clearExperimentalAudioSessionModeIfIdle();
            }
        }
        adjustMuteOnTx(isVoiceTransmitting());
        this.isMyselfTalking = isVoiceTransmitting();
        updateFloatingWindow();
    }

    public void applyConfiguredInputDeviceSelection() {
        boolean tx = isVoiceTransmissionEnabled();
        boolean vox = isVoiceActivationEnabled();
        stopExperimentalVoiceTransmission(true);
        if (tx) {
            startVoiceCapture(false);
        } else if (vox) {
            startVoiceCapture(true);
        }
    }

    public void onVoiceChangerModeChanged() {
        if (this.ttclient == null) {
            return;
        }
        if (isVoiceTransmitting() || isVoiceTransmissionEnabled() || isVoiceActivationEnabled()) {
            applyConfiguredInputDeviceSelection();
        }
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

    public void setMediaProjection(MediaProjection mp) {
        this.mediaProjection = mp;
    }

    public synchronized MediaProjection getMediaProjection() {
        if (this.mediaProjection == null && mediaProjectionData != null && Build.VERSION.SDK_INT >= 21) {
            MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (projectionManager != null) {
                try {
                    this.mediaProjection = projectionManager.getMediaProjection(mediaProjectionResultCode, (Intent) mediaProjectionData.clone());
                } catch (Throwable t) {
                    Log.e("bearware", "Failed to get MediaProjection", t);
                }
            }
        }
        return this.mediaProjection;
    }

    public void setScreenSharingActive(boolean active) {
        if (this.isScreenSharingActive != active) {
            this.isScreenSharingActive = active;
            refreshForegroundServiceType();
        }
    }

    public boolean isScreenSharingActive() {
        return this.isScreenSharingActive || (this.screenShareManager != null && this.screenShareManager.isSharing());
    }

    public ScreenShareManager getScreenShareManager() {
        return this.screenShareManager;
    }

    public ExperimentalAudioCapture getExperimentalAudioCapture() {
        return this.experimentalAudioCapture;
    }

    public void setScreenShareAudioMode(int mode) {
        getSessionPreferences().edit().putInt(Preferences.PREF_SCREENSHARE_AUDIO_MODE, mode).apply();
        if (this.experimentalAudioCapture != null) {
            this.experimentalAudioCapture.setScreenShareAudioMode(mode);
        }
    }

    public void setMicEnhancementEnabled(boolean enabled) {
        getSessionPreferences().edit().putBoolean(Preferences.PREF_SOUNDSYSTEM_MIC_ENHANCEMENTS, enabled).apply();
        if (this.experimentalAudioCapture != null) {
            this.experimentalAudioCapture.setMicEnhancementEnabled(enabled);
        } else {
            MicEnhancement.setEnabled(enabled);
        }
    }

    public void startScreenShare(MediaProjection mediaProjection) {
        if (this.screenShareManager == null) {
            this.screenShareManager = new ScreenShareManager(this, new ScreenShareManager.ClientProvider() {
                @Override
                public List<TeamTalkBase> getClients() {
                    return ttclient != null ? Collections.singletonList(ttclient) : Collections.<TeamTalkBase>emptyList();
                }
            });
            this.screenShareManager.setOnStoppedBySystemListener(new ScreenShareManager.OnStoppedBySystemListener() {
                @Override
                public void onScreenShareStoppedBySystem() {
                    onScreenShareStoppedBySystem();
                }
            });
        }
        boolean isTx = isVoiceTransmissionEnabled();
        boolean isVox = isVoiceActivationEnabled();
        if (isTx || isVox) {
            stopExperimentalVoiceTransmission(true);
        }
        setScreenSharingActive(true);
        setMediaProjection(mediaProjection);
        acquireServiceLocks();
        this.screenShareManager.startShare(mediaProjection);
        if (isTx) {
            enableVoiceTransmission(true);
        } else if (isVox) {
            enableVoiceActivation(true);
        } else {
            enableVoiceTransmission(true);
        }
    }

    public void stopScreenShare() {
        boolean isTx = isVoiceTransmissionEnabled();
        boolean isVox = isVoiceActivationEnabled();
        if (isTx || isVox) {
            stopExperimentalVoiceTransmission(true);
        }
        setScreenSharingActive(false);
        setMediaProjection(null);
        if (this.screenShareManager != null) {
            this.screenShareManager.stopShare();
        }
        if (isTx) {
            enableVoiceTransmission(true);
        } else if (isVox) {
            enableVoiceActivation(true);
        }
    }

    public void onScreenShareStoppedBySystem() {
        Log.i("bearware", "Screen share stopped by system");
        stopScreenShare();
    }

    public void startInternalAudioCapture() {
        startVoiceCapture(false);
    }

    public void stopInternalAudioCapture() {
        stopExperimentalVoiceTransmission(false);
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
        if (streaming) {
            acquireServiceLocks();
        }
        displayNotification(true);
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

    public boolean isStreamingPaused() {
        return this.currentPlayback != null && this.currentPlayback.bPaused;
    }

    public void toggleStreamingPause() {
        if (!this.isStreamingMedia || this.ttclient == null) return;
        if (this.currentPlayback == null) {
            this.currentPlayback = new MediaFilePlayback();
        }
        this.currentPlayback.bPaused = !this.currentPlayback.bPaused;
        VideoCodec vc = new VideoCodec();
        if (this.currentMediaFileInfo != null && this.currentMediaFileInfo.videoFmt != null && this.currentMediaFileInfo.videoFmt.picFourCC != 0) {
            vc.nCodec = 128;
        } else {
            vc.nCodec = 0;
        }
        this.ttclient.updateStreamingMediaFileToChannel(this.currentPlayback, vc);
    }

    public void stopStreamingMedia() {
        if (this.ttclient != null) {
            this.ttclient.stopStreamingMediaFileToChannel();
        }
        this.isStreamingMedia = false;
        this.currentPlayback = null;
        this.currentMediaFileInfo = null;
        this.currentStreamPath = "";
        displayNotification(true);
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

    public interface ConnectionCallback {
        void onConnectionResult(boolean success);
    }

    private final java.util.concurrent.ExecutorService connectionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public void reconnectAsync(final ConnectionCallback callback) {
        connectionExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final boolean success = reconnect();
                if (callback != null) {
                    reconnectHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onConnectionResult(success);
                        }
                    });
                }
            }
        });
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
            Vector<MyTextMessage> msgs = new Vector<>();
            this.usertxtmsgs.put(Integer.valueOf(userid), msgs);
            if (this.ttserver != null) {
                String srvKey = this.ttserver.ipaddr + ":" + this.ttserver.tcpport;
                List<ChatMessageEntry> history = ChatHistoryDbHelper.getInstance(getApplicationContext())
                        .getRecentMessages(srvKey, ChatMessageEntry.TYPE_PRIVATE, 0, userid, 50);
                for (ChatMessageEntry entry : history) {
                    MyTextMessage m = new MyTextMessage(entry.getSenderDisplayName());
                    m.nMsgType = entry.getMsgType();
                    m.nFromUserID = entry.getFromUserId();
                    m.nToUserID = entry.getToUserId();
                    m.szFromUsername = entry.getFromUsername();
                    m.szMessage = entry.getMessageText();
                    m.time = new Date(entry.getTimestamp());
                    msgs.add(m);
                }
            }
        }
        Vector<MyTextMessage> msgs = this.usertxtmsgs.get(Integer.valueOf(userid));
        if (msgs.size() > this.HISTORY_USER_MSG_MAX) {
            msgs.remove(0);
        }
        return msgs;
    }

    public static class SessionDialogInfo {
        public int userId;
        public String username = "";
        public String nickname = "";
        public String lastMessage = "";
        public long lastTimestamp = 0;
        public boolean lastIsOutgoing = false;
        public int messageCount = 0;
    }

    private final Map<String, SessionDialogInfo> sessionPrivateDialogs = new LinkedHashMap<>();

    public synchronized Map<String, SessionDialogInfo> getSessionPrivateDialogs() {
        return new LinkedHashMap<>(this.sessionPrivateDialogs);
    }

    public synchronized void recordSessionPrivateMessage(int userId, String nickname, String message, boolean isOutgoing, long timestamp) {
        User u = this.users != null ? this.users.get(Integer.valueOf(userId)) : null;
        String username = (u != null && u.szUsername != null) ? u.szUsername.trim() : "";
        if (nickname == null || nickname.isEmpty()) {
            nickname = (u != null) ? Utils.getDisplayName(this, u) : ("ID: " + userId);
        }
        String key;
        if (!username.isEmpty()) {
            key = "user:" + username.toLowerCase(Locale.ROOT);
        } else if (!nickname.isEmpty()) {
            key = "nick:" + nickname.toLowerCase(Locale.ROOT);
        } else {
            key = "id:" + userId;
        }

        SessionDialogInfo info = this.sessionPrivateDialogs.get(key);
        if (info == null) {
            info = new SessionDialogInfo();
            info.userId = userId;
            info.username = username;
            info.nickname = nickname;
            info.lastMessage = message != null ? message : "";
            info.lastTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
            info.lastIsOutgoing = isOutgoing;
            info.messageCount = 1;
            this.sessionPrivateDialogs.put(key, info);
        } else {
            info.userId = userId;
            if (!username.isEmpty()) info.username = username;
            if (!nickname.isEmpty()) info.nickname = nickname;
            info.lastMessage = message != null ? message : "";
            info.lastTimestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
            info.lastIsOutgoing = isOutgoing;
            info.messageCount++;
        }
    }

    public synchronized void removeSessionPrivateDialog(int userId, String username, String nickname) {
        if (!TextUtils.isEmpty(username)) {
            this.sessionPrivateDialogs.remove("user:" + username.toLowerCase(Locale.ROOT));
        }
        if (!TextUtils.isEmpty(nickname)) {
            this.sessionPrivateDialogs.remove("nick:" + nickname.toLowerCase(Locale.ROOT));
        }
        this.sessionPrivateDialogs.remove("id:" + userId);
    }

    public Vector<MyTextMessage> getChatLogTextMsgs() {
        if (this.chatlogtxtmsgs.isEmpty() && this.ttserver != null) {
            String srvKey = this.ttserver.ipaddr + ":" + this.ttserver.tcpport;
            int chanId = this.mychannel != null ? this.mychannel.nChannelID : 0;
            List<ChatMessageEntry> history = ChatHistoryDbHelper.getInstance(getApplicationContext())
                    .getRecentMessages(srvKey, ChatMessageEntry.TYPE_CHANNEL, chanId, 0, 50);
            for (ChatMessageEntry entry : history) {
                MyTextMessage m = new MyTextMessage(entry.getSenderDisplayName());
                m.nMsgType = entry.getMsgType();
                m.nChannelID = entry.getChannelId();
                m.nFromUserID = entry.getFromUserId();
                m.szFromUsername = entry.getFromUsername();
                m.szMessage = entry.getMessageText();
                m.time = new Date(entry.getTimestamp());
                this.chatlogtxtmsgs.add(m);
            }
        }
        if (this.chatlogtxtmsgs.size() > this.HISTORY_CHATLOG_MSG_MAX) {
            this.chatlogtxtmsgs.remove(0);
        }
        return this.chatlogtxtmsgs;
    }

    private final Handler eventHandlerLooper = new Handler(Looper.getMainLooper());
    private boolean eventLoopRunning = false;
    private boolean prevVoiceActivationState;
    private boolean prevVoiceTransmissionState;

    private final Runnable eventLoopRunnable = new Runnable() {
        @Override
        public void run() {
            if (!eventLoopRunning || TeamTalkService.this.ttclient == null) {
                return;
            }

            int events = 0;
            while (events < 100 && TeamTalkService.this.mEventHandler.processEvent(TeamTalkService.this.ttclient, 0)) {
                events++;
            }

            boolean newVoiceTransmissionState = TeamTalkService.this.isVoiceTransmissionEnabled();
            boolean newVoiceActivationState = TeamTalkService.this.isVoiceActivationEnabled();
            if (TeamTalkService.this.onVoiceTransmissionToggleListener != null) {
                if (newVoiceTransmissionState != prevVoiceTransmissionState) {
                    TeamTalkService.this.onVoiceTransmissionToggleListener.onVoiceTransmissionToggle(newVoiceTransmissionState, TeamTalkService.this.txSuspended);
                    prevVoiceTransmissionState = newVoiceTransmissionState;
                }
                if (newVoiceActivationState != prevVoiceActivationState) {
                    TeamTalkService.this.onVoiceTransmissionToggleListener.onVoiceActivationToggle(newVoiceActivationState, TeamTalkService.this.voxSuspended);
                    prevVoiceActivationState = newVoiceActivationState;
                }
            }

            long delay = TeamTalkService.this.isConnected() ? 25L : 150L;
            if (eventLoopRunning) {
                eventHandlerLooper.postDelayed(this, delay);
            }
        }
    };

    void createEventTimer() {
        startEventLoop();
    }

    void startEventLoop() {
        if (!this.eventLoopRunning) {
            this.eventLoopRunning = true;
            this.prevVoiceTransmissionState = isVoiceTransmissionEnabled();
            this.prevVoiceActivationState = isVoiceActivationEnabled();
            this.eventHandlerLooper.post(this.eventLoopRunnable);
        }
    }

    void stopEventLoop() {
        this.eventLoopRunning = false;
        this.eventHandlerLooper.removeCallbacks(this.eventLoopRunnable);
        if (this.eventTimer != null) {
            try {
                this.eventTimer.cancel();
            } catch (Exception ignored) {}
            this.eventTimer = null;
        }
    }

    void createReconnectTimer(long delayMsec) {
        this.reconnectHandler.removeCallbacks(this.reconnectTimer);
        this.reconnectHandler.postDelayed(this.reconnectTimer, delayMsec);
    }

    public void login() {
        if (this.ttclient == null || this.ttserver == null) {
            return;
        }
        this.isScreenSharingActive = false;
        String nickname = this.ttserver.nickname;
        if (TextUtils.isEmpty(nickname)) {
            nickname = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString(Preferences.PREF_GENERAL_NICKNAME, "");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String clientName = prefs.getString(Preferences.PREF_GENERAL_CLIENTNAME, "");

        String fullClientName;
        if (TextUtils.isEmpty(clientName)) {
            fullClientName = "TeamTalk Next";
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
        int dspEngine = prefs.getInt(Preferences.PREF_EQ_DSP_ENGINE, 0);
        boolean nsEnabled = prefs.getBoolean(Preferences.PREF_EQ_MIC_NS, false);
        boolean aecEnabled = prefs.getBoolean(Preferences.PREF_EQ_MIC_AEC, false);
        boolean agcEnabled = prefs.getBoolean(Preferences.PREF_EQ_MIC_AGC, false);
        boolean vpEnabled = prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_VOICEPROCESSING, false);
        boolean speakerphone = prefs.getBoolean(Preferences.PREF_SOUNDSYSTEM_SPEAKERPHONE, false);
        boolean hwEffects = prefs.getBoolean(Preferences.PREF_EQ_MIC_HW_EFFECTS, false);

        boolean anySoftwareDspNeeded = (dspEngine == 0 && (nsEnabled || aecEnabled || agcEnabled || vpEnabled || prefs.getBoolean(Preferences.PREF_EQ_MIC_PREAMP_ENABLE, false) || (this.mychannel != null && this.mychannel.audiocfg.bEnableAGC)))
                || (dspEngine == 1 && (nsEnabled || aecEnabled || agcEnabled || vpEnabled || (this.mychannel != null && this.mychannel.audiocfg.bEnableAGC)));

        AudioPreprocessor ap;
        if (dspEngine == 2 || !anySoftwareDspNeeded) {
            // Hardware direct / No software preprocessor - enables pure STEREO pass-through without mono downmix
            ap = new AudioPreprocessor(AudioPreprocessorType.NO_AUDIOPREPROCESSOR, false);
        } else if (dspEngine == 1) {
            // SpeexDSP Engine
            ap = new AudioPreprocessor(AudioPreprocessorType.SPEEXDSP_AUDIOPREPROCESSOR, true);
            if (ap.speexdsp != null) {
                ap.speexdsp.bEnableDenoise = nsEnabled || vpEnabled;
                int speexNsDb = prefs.getInt(Preferences.PREF_EQ_MIC_SPEEX_NS_DB, -30);
                ap.speexdsp.nMaxNoiseSuppressDB = speexNsDb;

                ap.speexdsp.bEnableEchoCancellation = aecEnabled || vpEnabled;
                int speexAecDb = prefs.getInt(Preferences.PREF_EQ_MIC_SPEEX_AEC_DB, -40);
                ap.speexdsp.nEchoSuppress = speexAecDb;
                ap.speexdsp.nEchoSuppressActive = speexAecDb / 2;

                if (this.mychannel != null && this.mychannel.audiocfg.bEnableAGC) {
                    ap.speexdsp.bEnableAGC = true;
                    ap.speexdsp.nGainLevel = this.mychannel.audiocfg.nGainLevel;
                } else {
                    ap.speexdsp.bEnableAGC = agcEnabled || vpEnabled;
                    ap.speexdsp.nGainLevel = prefs.getInt(Preferences.PREF_EQ_MIC_SPEEX_AGC_LEVEL, 8000);
                    ap.speexdsp.nMaxGainDB = prefs.getInt(Preferences.PREF_EQ_MIC_SPEEX_AGC_MAX_GAIN, 30);
                    ap.speexdsp.nMaxIncDBSec = 12;
                    ap.speexdsp.nMaxDecDBSec = -40;
                }
            }
        } else {
            // WebRTC Audio Processing (Default & recommended)
            ap = new AudioPreprocessor(AudioPreprocessorType.WEBRTC_AUDIOPREPROCESSOR, true);
            if (ap.webrtc != null) {
                ap.webrtc.noisesuppression.bEnable = nsEnabled || vpEnabled;
                int nsLevel = prefs.getInt(Preferences.PREF_EQ_MIC_NS_LEVEL, 2);
                ap.webrtc.noisesuppression.nLevel = Math.max(0, Math.min(3, nsLevel));

                ap.webrtc.echocanceller.bEnable = aecEnabled || vpEnabled;

                boolean preampEnable = prefs.getBoolean(Preferences.PREF_EQ_MIC_PREAMP_ENABLE, false);
                int preampFactorRaw = prefs.getInt(Preferences.PREF_EQ_MIC_PREAMP_GAIN, 100);
                if (ap.webrtc.preamplifier != null) {
                    ap.webrtc.preamplifier.bEnable = preampEnable;
                    ap.webrtc.preamplifier.fFixedGainFactor = Math.max(1.0f, preampFactorRaw / 100.0f);
                }

                if (ap.webrtc.gaincontroller2 != null) {
                    if (this.mychannel != null && this.mychannel.audiocfg.bEnableAGC) {
                        ap.webrtc.gaincontroller2.bEnable = true;
                        float gainPercent = this.mychannel.audiocfg.nGainLevel / 32000.0f;
                        if (ap.webrtc.gaincontroller2.fixeddigital != null) {
                            ap.webrtc.gaincontroller2.fixeddigital.fGainDB = 49.9f * gainPercent;
                        }
                    } else {
                        ap.webrtc.gaincontroller2.bEnable = agcEnabled || vpEnabled;
                        int agcMode = prefs.getInt(Preferences.PREF_EQ_MIC_AGC_MODE, 0);
                        if (agcMode == 1) {
                            int fixedGain = prefs.getInt(Preferences.PREF_EQ_MIC_AGC_FIXED_GAIN, 15);
                            if (ap.webrtc.gaincontroller2.fixeddigital != null) {
                                ap.webrtc.gaincontroller2.fixeddigital.fGainDB = (float) fixedGain;
                            }
                            if (ap.webrtc.gaincontroller2.adaptivedigital != null) {
                                ap.webrtc.gaincontroller2.adaptivedigital.bEnable = false;
                            }
                        } else {
                            int maxGain = prefs.getInt(Preferences.PREF_EQ_MIC_AGC_MAX_GAIN, 30);
                            int headroom = prefs.getInt(Preferences.PREF_EQ_MIC_AGC_HEADROOM, 5);
                            if (ap.webrtc.gaincontroller2.adaptivedigital != null) {
                                ap.webrtc.gaincontroller2.adaptivedigital.bEnable = agcEnabled || vpEnabled;
                                ap.webrtc.gaincontroller2.adaptivedigital.fHeadRoomDB = (float) headroom;
                                ap.webrtc.gaincontroller2.adaptivedigital.fMaxGainDB = (float) maxGain;
                                ap.webrtc.gaincontroller2.adaptivedigital.fInitialGainDB = Math.min((float) maxGain, 15.0f);
                                ap.webrtc.gaincontroller2.adaptivedigital.fMaxGainChangeDBPerSecond = 6.0f;
                                ap.webrtc.gaincontroller2.adaptivedigital.fMaxOutputNoiseLevelDBFS = -50.0f;
                            }
                        }
                    }
                }
            }
        }

        SoundDeviceEffects sde = new SoundDeviceEffects();
        sde.bEnableDenoise = hwEffects && (nsEnabled || vpEnabled);
        sde.bEnableEchoCancellation = hwEffects && (aecEnabled || vpEnabled);
        sde.bEnableAGC = hwEffects && (agcEnabled || vpEnabled);
        try {
            this.ttclient.setSoundDeviceEffects(sde);
        } catch (Throwable ignored) {
        }

        float[] micEq = new float[MIC_EQ_FREQUENCIES.length];
        for (int i = 0; i < micEq.length; i++) {
            micEq[i] = prefs.getInt(Preferences.PREF_EQ_MIC_BAND_PREFIX + i, 0);
        }
        MicrophoneEqualizer.setBands(micEq);
        try {
            java.lang.reflect.Method eqMethod = this.ttclient.getClass().getMethod("setMicrophoneEqualizer", float[].class);
            eqMethod.invoke(this.ttclient, new Object[]{micEq});
        } catch (Throwable ignored) {
            // Supported after replacing the bundled TeamTalk JNI/JAR with the rebuilt version.
        }
        this.ttclient.setSoundInputPreprocess(ap);

        try {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null && !audioManager.isBluetoothA2dpOn()) {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                if (speakerphone && !audioManager.isWiredHeadsetOn()) {
                    audioManager.setSpeakerphoneOn(true);
                } else {
                    audioManager.setSpeakerphoneOn(false);
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
        acquireServiceLocks();
        if (this.ttserver == null) {
            throw new AssertionError();
        }
        if (Utils.isWebLogin(this.ttserver.username)) {
            requestWebLoginAccessToken();
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
        stopScreenShare();
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
        this.talkingPeerUserIds.remove(Integer.valueOf(user.nUserID));
        this.originalMediaFileVolumes.remove(Integer.valueOf(user.nUserID));
        checkAudioDucking();
        String name = Utils.getDisplayName(getBaseContext(), user);
        MyTextMessage msg = MyTextMessage.createLogMsg(Integer.MIN_VALUE, name + " " + getResources().getString(R.string.text_cmd_userloggedout));
        getChatLogTextMsgs().add(msg);
        syncToUserCache(user);
    }

    @Override
    public void onCmdUserUpdate(User user) {
        User olduser = this.users.get(Integer.valueOf(user.nUserID));
        if (olduser != null) {
            Utils.subscriptionLogChanged(getBaseContext(), olduser, user)
                .ifPresent(text -> getChatLogTextMsgs().add(MyTextMessage.createLogMsg(MyTextMessage.MSGTYPE_LOG_INFO, text)));
        }
        this.users.put(Integer.valueOf(user.nUserID), user);
        updateFloatingWindow();
    }

    public void applyHearMyselfSubscription() {
        if (this.ttclient == null || (this.ttclient.getFlags() & 2) == 0) {
            return;
        }
        int myId = this.ttclient.getMyUserID();
        if (myId <= 0) return;
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean hearMyself = pref.getBoolean(Preferences.PREF_CONNECTION_HEAR_MYSELF, false);
        if (hearMyself) {
            this.ttclient.doSubscribe(myId, 16);
        } else {
            this.ttclient.doUnsubscribe(myId, 16);
        }
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
            applyHearMyselfSubscription();
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
        if (this.isAudioDucked) {
            this.originalMediaFileVolumes.put(user.nUserID, mf_volume);
        }
        int targetVol = (this.isAudioDucked) ? Math.round(mf_volume * getAudioDuckingFactor()) : mf_volume;
        this.ttclient.setUserVolume(user.nUserID, 4, Utils.refVolume(targetVol));
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
        this.talkingPeerUserIds.remove(Integer.valueOf(user.nUserID));
        this.originalMediaFileVolumes.remove(Integer.valueOf(user.nUserID));
        checkAudioDucking();
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
        if (textmessage != null) {
            try {
                if (org.nekit.ttproplus.plugin.PluginManager.getInstance().onTextMessageReceived(textmessage)) {
                    return;
                }
            } catch (Throwable ignored) {}
        }
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

        String srvKey = this.ttserver != null ? (this.ttserver.ipaddr + ":" + this.ttserver.tcpport) : "";
        String chanName = this.mychannel != null ? this.mychannel.szName : "";
        ChatHistoryDbHelper.getInstance(getApplicationContext()).saveIncomingMessage(srvKey, textmessage, newmsg.szNickName, chanName);

        switch (textmessage.nMsgType) {
            case 1:
                getUserTextMsgs(textmessage.nFromUserID).add(newmsg);
                recordSessionPrivateMessage(textmessage.nFromUserID, newmsg.szNickName, newmsg.szMessage, false, System.currentTimeMillis());
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
        Channel oldchannel = this.channels.get(Integer.valueOf(channel.nChannelID));
        if (oldchannel != null && this.mychannel != null && this.mychannel.nChannelID == channel.nChannelID) {
            Utils.transmitUsersLogChanged(getBaseContext(), oldchannel, channel, getUsers())
                .ifPresent(text -> getChatLogTextMsgs().add(MyTextMessage.createLogMsg(MyTextMessage.MSGTYPE_LOG_INFO, text)));
        }
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
        if (serverproperties != null && !TextUtils.isEmpty(serverproperties.szServerVersion)) {
        }
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
        if (this.mychannel != null && user != null && user.nChannelID == this.mychannel.nChannelID) {
            if ((user.uUserState & 1) != 0) {
                onVoiceDetected();
                if (this.ttclient != null && user.nUserID != this.ttclient.getMyUserID()) {
                    this.talkingPeerUserIds.add(Integer.valueOf(user.nUserID));
                }
            } else {
                if (this.ttclient != null && user.nUserID != this.ttclient.getMyUserID()) {
                    this.talkingPeerUserIds.remove(Integer.valueOf(user.nUserID));
                }
            }
            checkAudioDucking();
        } else if (user != null) {
            if (this.talkingPeerUserIds.remove(Integer.valueOf(user.nUserID))) {
                checkAudioDucking();
            }
        }
    }

    @Override
    public void onVoiceActivation(boolean bVoiceActive) {
        adjustMuteOnTx(bVoiceActive);
        updateFloatingWindow();
        this.isMyselfTalking = bVoiceActive;
        checkAudioDucking();
        if (bVoiceActive) {
            onVoiceDetected();
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
        this.currentMediaFileInfo = mediafileinfo;
        User myself = this.users.get(Integer.valueOf(this.ttclient.getMyUserID()));
        switch (mediafileinfo.nStatus) {
            case 1:
            case 3:
            case 4:
                this.isStreamingMedia = false;
                this.currentStreamPath = "";
                this.currentPlayback = null;
                if (myself != null) {
                    this.ttclient.doChangeStatus(myself.nStatusMode & (-2049), myself.szStatusMsg);
                }
                return;
            case 2:
                this.isStreamingMedia = true;
                if (myself != null) {
                    this.ttclient.doChangeStatus(myself.nStatusMode | 2048, myself.szStatusMsg);
                }
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

    private void requestWebLoginAccessToken() {
        connectionExecutor.execute(() -> {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TeamTalkService.this.getBaseContext());
            String username = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_USERNAME, "");
            String token = prefs.getString(Preferences.PREF_GENERAL_BEARWARE_TOKEN, "");
            String accesstoken = "";
            ServerProperties srvprop = new ServerProperties();
            if (TeamTalkService.this.ttclient != null && TeamTalkService.this.ttclient.getServerProperties(srvprop)) {
                accesstoken = srvprop.szAccessToken;
            }

            String xml = Utils.getURL(AppInfo.getBearWareAccessTokenUrl(TeamTalkService.this.getBaseContext(), username, token, accesstoken));
            Log.d("bearware", xml);
            String parsedUser = "";
            try {
                InputSource src = new InputSource(new StringReader(xml));
                DocumentBuilderFactory dbf = Utils.createSecureDocumentBuilderFactory();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document document = db.parse(src);
                XPathFactory factory = XPathFactory.newInstance();
                XPath xPath = factory.newXPath();
                parsedUser = (String) xPath.evaluate("/teamtalk/bearware/username", document, XPathConstants.STRING);
            } catch (Exception e) {
                Log.e("bearware", "Web login parse error: " + e);
            }

            final String finalUser = parsedUser;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finalUser != null && !finalUser.isEmpty() && TeamTalkService.this.ttserver != null) {
                    TeamTalkService.this.ttserver.username = finalUser;
                    TeamTalkService.this.login();
                } else {
                    Toast.makeText(TeamTalkService.this, TeamTalkService.this.getResources().getString(R.string.text_weblogin_authfailure), Toast.LENGTH_SHORT).show();
                }
            });
        });
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

        connectionExecutor.execute(() -> {
            if (this.ttserver == null || this.manualDisconnect || this.ttclient == null) {
                this.isSeamlessReconnecting = false;
                return;
            }
            this.ttclient.disconnect();

            if (!setupEncryption()) {
                Log.w("bearware", "Seamless reconnect encryption setup failed");
                new Handler(Looper.getMainLooper()).post(() -> createReconnectTimer(2000L));
                return;
            }

            if (!this.ttclient.connect(this.ttserver.ipaddr, this.ttserver.tcpport, this.ttserver.udpport, 0, 0, this.ttserver.encrypted)) {
                Log.w("bearware", "Seamless connect() failed, scheduling quick retry");
                new Handler(Looper.getMainLooper()).post(() -> createReconnectTimer(1500L));
            } else {
                Log.i("bearware", "Seamless connect() started over new network interface");
            }
        });
    }
}
