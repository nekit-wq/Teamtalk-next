package org.nekit.ttproplus.backend;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MicrophoneInfo;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import dk.bearware.AudioBlock;
import dk.bearware.OpusConstants;
import dk.bearware.TeamTalkBase;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.nekit.ttproplus.data.MicrophoneInputHelper;
import org.nekit.ttproplus.data.Preferences;
import org.nekit.ttproplus.data.ScreenShareAudioHelper;

public final class ExperimentalAudioCapture {
    private static final String TAG = "bearware";
    private final Context context;
    private final AudioManager audioManager;
    private final PowerManager.WakeLock captureWakeLock;
    private final Set<TeamTalkBase> ttclients = new CopyOnWriteArraySet<>();
    private final Object lock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TeamTalkService service;
    private AudioRecord audioRecord;
    private CaptureSession captureSession;
    private Thread captureThread;
    private Config activeConfig;

    private volatile boolean running = false;
    private volatile boolean active = false;
    private volatile int activeChannels = 0;
    private volatile int activeSampleRate = 0;
    private volatile long lastCaptureActivityUptimeMs = 0;
    private long lastRestartUptimeMs = 0;
    private volatile int screenShareAudioMode = ScreenShareAudioHelper.MODE_BOTH;
    private volatile boolean micEnhancementEnabled = false;
    private volatile VoiceActivationListener voiceActivationListener;

    private final Runnable restartRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (running && activeConfig != null) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastRestartUptimeMs >= 500) {
                        lastRestartUptimeMs = now;
                        Log.i(TAG, "Restarting experimental audio capture...");
                        Config config = activeConfig;
                        CaptureSession oldSession = stopLocked(false);
                        if (oldSession != null) {
                            oldSession.stopAndRelease();
                        }
                        CaptureSession newSession = openSessionForConfig(config);
                        if (newSession != null) {
                            captureSession = newSession;
                            audioRecord = newSession.audioRecord;
                            running = true;
                            active = true;
                            activeChannels = newSession.channels;
                            activeSampleRate = newSession.sampleRate;
                            lastCaptureActivityUptimeMs = SystemClock.uptimeMillis();
                            if (captureWakeLock != null && !captureWakeLock.isHeld()) {
                                captureWakeLock.acquire();
                            }
                            VoiceChanger.resetState();
                            MicEnhancement.resetState();
                            MicrophoneEqualizer.resetState();
                            Thread th = new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    runCaptureLoop(captureSession);
                                }
                            }, "TT-ExperimentalCapture");
                            captureThread = th;
                            th.start();
                        }
                    }
                }
            }
        }
    };

    public interface VoiceActivationListener {
        void onVoiceActivation(TeamTalkBase client, boolean active);
    }

    public static final class Config {
        public final int sampleRate;
        public final int frameDurationMs;
        public final int captureMode;
        public final String preferredInputDeviceId;
        public final int streamId;
        public final float gainMultiplier;
        public boolean voxEnabled;
        public int voxLevel;
        public int voxStopDelayMs;

        public Config(int sampleRate, int frameDurationMs, int captureMode, String preferredInputDeviceId,
                      int streamId, float gainMultiplier, boolean voxEnabled, int voxLevel, int voxStopDelayMs) {
            this.sampleRate = sampleRate;
            this.frameDurationMs = frameDurationMs;
            this.captureMode = captureMode;
            this.preferredInputDeviceId = preferredInputDeviceId;
            this.streamId = streamId;
            this.gainMultiplier = gainMultiplier;
            this.voxEnabled = voxEnabled;
            this.voxLevel = voxLevel;
            this.voxStopDelayMs = Math.max(100, voxStopDelayMs);
        }
    }

    public static final class PlatformPreprocessors {
        final AutomaticGainControl agc;
        final NoiseSuppressor ns;
        final AcousticEchoCanceler aec;

        public PlatformPreprocessors(AutomaticGainControl agc, NoiseSuppressor ns, AcousticEchoCanceler aec) {
            this.agc = agc;
            this.ns = ns;
            this.aec = aec;
        }

        private void setEffectEnabled(AudioEffect effect, boolean enabled) {
            if (effect != null) {
                try {
                    effect.setEnabled(enabled);
                } catch (Throwable ignored) {
                }
            }
        }

        public void enable() {
            setEffectEnabled(agc, true);
            setEffectEnabled(ns, true);
            setEffectEnabled(aec, true);
        }

        public void disable() {
            setEffectEnabled(agc, false);
            setEffectEnabled(ns, false);
            setEffectEnabled(aec, false);
        }

        public void release() {
            if (agc != null) try { agc.release(); } catch (Throwable ignored) {}
            if (ns != null) try { ns.release(); } catch (Throwable ignored) {}
            if (aec != null) try { aec.release(); } catch (Throwable ignored) {}
        }
    }

    public static final class CaptureSession {
        final AudioRecord audioRecord;
        final AudioRecord internalAudioRecord;
        final int sampleRate;
        final int channels;
        final int frameSamples;
        final int streamId;
        final float gainMultiplier;
        final int audioSource;
        final PlatformPreprocessors preprocessors;
        AudioManager.AudioRecordingCallback recordingCallback;
        boolean stereoVerified;
        volatile boolean voxEnabled;
        volatile int voxLevel;
        volatile int voxStopDelayMs = 500;
        volatile boolean voiceTransmitting;
        volatile boolean silencedByPolicy = false;

        public CaptureSession(AudioRecord audioRecord, AudioRecord internalAudioRecord, int sampleRate,
                              int channels, int frameSamples, int streamId, float gainMultiplier,
                              int audioSource, PlatformPreprocessors preprocessors) {
            this.audioRecord = audioRecord;
            this.internalAudioRecord = internalAudioRecord;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.frameSamples = frameSamples;
            this.streamId = streamId;
            this.gainMultiplier = gainMultiplier;
            this.audioSource = audioSource;
            this.preprocessors = preprocessors;
        }

        public void applyPreprocessorsConfig(boolean enable) {
            if (preprocessors != null) {
                if (enable && this.channels == 1) {
                    preprocessors.enable();
                } else {
                    preprocessors.disable();
                }
            }
        }

        public boolean isVoiceTransmitting() {
            return !this.voxEnabled || this.voiceTransmitting;
        }

        public void stopAndRelease() {
            if (recordingCallback != null && Build.VERSION.SDK_INT >= 29 && audioRecord != null) {
                try {
                    audioRecord.unregisterAudioRecordingCallback(recordingCallback);
                } catch (Throwable ignored) {
                }
                recordingCallback = null;
            }
            if (preprocessors != null) {
                preprocessors.release();
            }
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                } catch (Throwable ignored) {
                }
                try {
                    audioRecord.release();
                } catch (Throwable ignored) {
                }
            }
            if (internalAudioRecord != null) {
                try {
                    internalAudioRecord.stop();
                } catch (Throwable ignored) {
                }
                try {
                    internalAudioRecord.release();
                } catch (Throwable ignored) {
                }
            }
        }

        public void release() {
            stopAndRelease();
        }
    }

    public ExperimentalAudioCapture(Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.captureWakeLock = pm != null ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeamTalk:ExperimentalAudioCapture") : null;
        if (this.captureWakeLock != null) {
            this.captureWakeLock.setReferenceCounted(false);
        }
    }

    public void setService(TeamTalkService service) {
        this.service = service;
    }

    public boolean isRunning() {
        return this.running;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean isActive(TeamTalkBase client) {
        return this.active && this.ttclients.contains(client);
    }

    public boolean isVoiceTransmitting() {
        CaptureSession session = this.captureSession;
        return this.active && session != null && session.isVoiceTransmitting();
    }

    public boolean isVoiceTransmitting(TeamTalkBase client) {
        return isVoiceTransmitting() && this.ttclients.contains(client);
    }

    public boolean isMicEnhancementEnabled() {
        return this.micEnhancementEnabled;
    }

    public int getActiveChannels() {
        return this.activeChannels;
    }

    public int getActiveSampleRate() {
        return this.activeSampleRate;
    }

    public void setMicEnhancementEnabled(boolean enabled) {
        this.micEnhancementEnabled = enabled;
        MicEnhancement.setEnabled(enabled);
    }

    public void setScreenShareAudioMode(int mode) {
        this.screenShareAudioMode = mode;
    }

    public void setVoiceActivationListener(VoiceActivationListener listener) {
        this.voiceActivationListener = listener;
    }

    public void setVoiceActivationEnabled(boolean enabled) {
        synchronized (this.lock) {
            if (this.captureSession != null) {
                this.captureSession.voxEnabled = enabled;
            }
        }
    }

    public void setVoiceActivationLevel(int level) {
        synchronized (this.lock) {
            if (this.captureSession != null) {
                this.captureSession.voxLevel = level;
            }
        }
    }

    public void setVoiceActivationStopDelay(int delayMs) {
        synchronized (this.lock) {
            if (this.captureSession != null) {
                this.captureSession.voxStopDelayMs = Math.max(100, delayMs);
            }
        }
    }

    public void scheduleRestart() {
        if (this.service == null || !this.service.isInPhoneCall()) {
            this.mainHandler.removeCallbacks(this.restartRunnable);
            this.mainHandler.postDelayed(this.restartRunnable, 150L);
        }
    }

    private void syncMicEnhancementFromPreferences() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
            boolean micEnhance = prefs.getBoolean("mic_enhancements_checkbox", false);
            setMicEnhancementEnabled(micEnhance);
        } catch (Throwable ignored) {
        }
    }

    public boolean start(TeamTalkBase client, Config config) {
        synchronized (this.lock) {
            if (client != null) {
                this.ttclients.add(client);
            }
            this.activeConfig = config;
            if (this.running) {
                if (this.captureSession != null) {
                    this.captureSession.voxEnabled = config.voxEnabled;
                    this.captureSession.voxLevel = config.voxLevel;
                    this.captureSession.voxStopDelayMs = config.voxStopDelayMs;
                    if (config.voxEnabled || this.captureSession.audioSource == 7) {
                        this.captureSession.applyPreprocessorsConfig(true);
                    }
                }
                return true;
            }
            syncMicEnhancementFromPreferences();
            CaptureSession session = openSessionForConfig(config);
            if (session == null) {
                if (client != null) {
                    this.ttclients.remove(client);
                }
                this.activeConfig = null;
                return false;
            }
            session.voxEnabled = config.voxEnabled;
            session.voxLevel = config.voxLevel;
            session.voxStopDelayMs = config.voxStopDelayMs;
            this.captureSession = session;
            this.audioRecord = session.audioRecord;
            this.running = true;
            this.active = true;
            this.activeChannels = session.channels;
            this.activeSampleRate = session.sampleRate;
            this.lastCaptureActivityUptimeMs = SystemClock.uptimeMillis();
            if (this.captureWakeLock != null && !this.captureWakeLock.isHeld()) {
                this.captureWakeLock.acquire();
            }
            VoiceChanger.resetState();
            MicEnhancement.resetState();
            MicrophoneEqualizer.resetState();
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    runCaptureLoop(captureSession);
                }
            }, "TT-ExperimentalCapture");
            this.captureThread = th;
            th.start();
            return true;
        }
    }

    public void stop(TeamTalkBase client, boolean releaseClient) {
        CaptureSession sessionToRelease = null;
        Thread threadToJoin = null;
        synchronized (this.lock) {
            if (client != null) {
                this.ttclients.remove(client);
            }
            if (this.ttclients.isEmpty() || releaseClient) {
                sessionToRelease = stopLocked(releaseClient);
                threadToJoin = this.captureThread;
                this.captureThread = null;
                this.activeConfig = null;
            }
        }
        if (sessionToRelease != null) {
            sessionToRelease.stopAndRelease();
        }
        if (threadToJoin != null && threadToJoin != Thread.currentThread()) {
            try {
                threadToJoin.join(300L);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void stop(boolean releaseClient) {
        stop(null, releaseClient);
    }

    public void shutdown() {
        stop(true);
    }

    private CaptureSession stopLocked(boolean flushBlank) {
        this.running = false;
        this.mainHandler.removeCallbacks(this.restartRunnable);
        if (this.captureWakeLock != null && this.captureWakeLock.isHeld()) {
            try {
                this.captureWakeLock.release();
            } catch (Throwable ignored) {
            }
        }
        CaptureSession session = this.captureSession;
        this.captureSession = null;
        this.audioRecord = null;
        if (flushBlank) {
            flushEmptyAudioBlock();
        }
        this.active = false;
        this.activeChannels = 0;
        this.activeSampleRate = 0;
        this.lastCaptureActivityUptimeMs = 0L;
        return session;
    }

    private void flushEmptyAudioBlock() {
        AudioBlock block = new AudioBlock();
        for (TeamTalkBase client : this.ttclients) {
            if (client != null) {
                try {
                    client.insertAudioBlock(block);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void insertBlockToClients(AudioBlock audioBlock) {
        for (TeamTalkBase client : this.ttclients) {
            int retries = 0;
            while (this.running && client != null && !client.insertAudioBlock(audioBlock)) {
                retries++;
                if (retries >= 3) {
                    break;
                }
                SystemClock.sleep(2L);
            }
        }
    }

    private void notifyVoiceActivation(boolean transmitting) {
        VoiceActivationListener listener = this.voiceActivationListener;
        if (listener != null) {
            for (TeamTalkBase client : this.ttclients) {
                listener.onVoiceActivation(client, transmitting);
            }
        }
    }

    public static int computeEnergyLevel(byte[] buf, int length) {
        int numSamples = length / 2;
        if (numSamples <= 0) return 0;
        long sumSquares = 0;
        for (int i = 0; i + 1 < length; i += 2) {
            short sample = (short) ((buf[i] & 255) | (buf[i + 1] << 8));
            sumSquares += (long) sample * sample;
        }
        double rms = Math.sqrt((double) sumSquares / numSamples);
        if (rms <= 64.0d) return 0;
        return (int) Math.round(Math.max(0.0d, Math.min(100.0d, (((Math.log10(rms / 32767.0d) * 20.0d) + 50.0d) / 50.0d) * 100.0d)));
    }

    private void applyGain(byte[] buf, float gain) {
        if (buf == null || buf.length < 2 || Math.abs(gain - 1.0f) < 0.01f) {
            return;
        }
        for (int i = 0; i + 1 < buf.length; i += 2) {
            short sample = (short) ((buf[i] & 255) | (buf[i + 1] << 8));
            short result = VoiceChanger.clampToShort(sample * gain);
            buf[i] = (byte) (result & 255);
            buf[i + 1] = (byte) ((result >> 8) & 255);
        }
    }

    private int getEffectiveScreenShareAudioMode(CaptureSession session) {
        if (this.service == null || !this.service.isScreenSharingActive() || session == null || session.internalAudioRecord == null) {
            return ScreenShareAudioHelper.MODE_BOTH;
        }
        return this.screenShareAudioMode;
    }

    private void mixInternalAudio(CaptureSession session, byte[] micBuf, byte[] internalBuf, int length, float internalGain, int mode) {
        if (session.internalAudioRecord == null || !this.running) {
            return;
        }
        int readTotal = 0;
        while (this.running && readTotal < length) {
            int read = session.internalAudioRecord.read(internalBuf, readTotal, length - readTotal);
            if (read <= 0) break;
            readTotal += read;
        }
        if (mode == ScreenShareAudioHelper.MODE_MIC_ONLY) {
            return;
        }
        boolean screenOnly = (mode == ScreenShareAudioHelper.MODE_SCREEN_ONLY);
        if (readTotal <= 0) {
            if (screenOnly) {
                Arrays.fill(micBuf, 0, length, (byte) 0);
            }
            return;
        }

        for (int i = 0; i + 1 < readTotal; i += 2) {
            short micSample = !screenOnly ? (short) ((micBuf[i] & 255) | ((micBuf[i + 1] & 255) << 8)) : (short) 0;
            short internalSample = (short) ((internalBuf[i] & 255) | ((internalBuf[i + 1] & 255) << 8));
            float combined = (internalSample * internalGain) + (!screenOnly ? micSample : 0.0f);
            short result = VoiceChanger.clampToShort(combined);
            micBuf[i] = (byte) (result & 255);
            micBuf[i + 1] = (byte) ((result >> 8) & 255);
        }
        if (screenOnly && readTotal < length) {
            Arrays.fill(micBuf, readTotal, length, (byte) 0);
        }
    }

    private void runCaptureLoop(CaptureSession session) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set audio thread priority", t);
        }

        int frameBytes = session.frameSamples * session.channels * 2;
        byte[] micBuffer = new byte[frameBytes];
        byte[] internalBuffer = new byte[frameBytes];
        AudioBlock block = new AudioBlock();

        float internalGain = 0.3f;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.context);
            int gainInt = prefs.getInt("internal_audio_gain_seekbar", 30);
            float norm = gainInt / 100.0f;
            internalGain = norm * norm;
        } catch (Exception ignored) {
        }

        byte[][] ringBuffer = (byte[][]) Array.newInstance(Byte.TYPE, 2, frameBytes);
        int ringWriteIdx = 0;
        int ringCount = 0;
        int sampleIndex = 0;
        boolean transmitting = false;
        long lastVoiceActivityTime = SystemClock.uptimeMillis();
        long silenceStartTime = 0;

        while (this.running) {
            if (session.silencedByPolicy) {
                if (transmitting) {
                    transmitting = false;
                    session.voiceTransmitting = false;
                    flushEmptyAudioBlock();
                    notifyVoiceActivation(false);
                }
                SystemClock.sleep(50L);
                continue;
            }

            int bytesRead = 0;
            int micReadAttempts = 0;
            while (this.running && bytesRead < frameBytes) {
                int read = session.audioRecord.read(micBuffer, bytesRead, frameBytes - bytesRead);
                if (read > 0) {
                    bytesRead += read;
                    this.lastCaptureActivityUptimeMs = SystemClock.uptimeMillis();
                    micReadAttempts = 0;
                } else if (read < 0) {
                    if (session.silencedByPolicy) {
                        micReadAttempts = 0;
                        SystemClock.sleep(50L);
                        continue;
                    }
                    micReadAttempts++;
                    Log.w(TAG, "Experimental capture mic read error: " + read + ", attempt " + micReadAttempts);
                    if (micReadAttempts >= 5) {
                        Log.e(TAG, "Persistent read error. Scheduling restart.");
                        scheduleRestart();
                        if (transmitting) {
                            session.voiceTransmitting = false;
                            flushEmptyAudioBlock();
                            notifyVoiceActivation(false);
                        }
                        return;
                    }
                    SystemClock.sleep(25L);
                } else {
                    SystemClock.sleep(20L);
                }
            }

            if (!this.running) break;

            long now = SystemClock.uptimeMillis();
            int screenMode = getEffectiveScreenShareAudioMode(session);

            if (session.voxEnabled && screenMode != ScreenShareAudioHelper.MODE_SCREEN_ONLY) {
                int energy = computeEnergyLevel(micBuffer, frameBytes);
                if (energy >= session.voxLevel) {
                    if (!transmitting) {
                        transmitting = true;
                        session.voiceTransmitting = true;
                        notifyVoiceActivation(true);

                        if (ringCount > 0) {
                            int startIdx = ringCount >= 2 ? ringWriteIdx : 0;
                            for (int r = 0; r < ringCount; r++) {
                                byte[] preBuf = ringBuffer[(startIdx + r) % 2];
                                if (this.micEnhancementEnabled) {
                                    MicEnhancement.process(preBuf, 0, frameBytes, session.sampleRate, session.channels);
                                }
                                MicrophoneEqualizer.process(preBuf, 0, frameBytes, session.sampleRate, session.channels);
                                VoiceChanger.process(preBuf, 0, frameBytes, session.sampleRate, session.channels);
                                applyGain(preBuf, session.gainMultiplier);

                                block.nStreamID = session.streamId;
                                block.uStreamTypes = 1;
                                block.nSampleRate = session.sampleRate;
                                block.nChannels = session.channels;
                                block.nSamples = session.frameSamples;
                                block.uSampleIndex = sampleIndex;
                                block.lpRawAudio = preBuf;
                                insertBlockToClients(block);
                                sampleIndex += session.frameSamples;
                            }
                            ringCount = 0;
                        }
                        lastVoiceActivityTime = now;
                        silenceStartTime = now;
                    } else {
                        silenceStartTime = now;
                    }
                } else {
                    if (transmitting) {
                        if (now - silenceStartTime > session.voxStopDelayMs) {
                            transmitting = false;
                            session.voiceTransmitting = false;
                            flushEmptyAudioBlock();
                            notifyVoiceActivation(false);
                            lastVoiceActivityTime = now;
                        }
                    }
                }
            } else {
                if (!transmitting) {
                    transmitting = true;
                    session.voiceTransmitting = true;
                }
            }

            if (!transmitting) {
                System.arraycopy(micBuffer, 0, ringBuffer[ringWriteIdx], 0, frameBytes);
                ringWriteIdx = (ringWriteIdx + 1) % 2;
                if (ringCount < 2) ringCount++;
            } else {
                if (this.micEnhancementEnabled && screenMode != ScreenShareAudioHelper.MODE_SCREEN_ONLY) {
                    MicEnhancement.process(micBuffer, 0, frameBytes, session.sampleRate, session.channels);
                }
                if (screenMode != ScreenShareAudioHelper.MODE_SCREEN_ONLY) {
                    MicrophoneEqualizer.process(micBuffer, 0, frameBytes, session.sampleRate, session.channels);
                }
                mixInternalAudio(session, micBuffer, internalBuffer, frameBytes, internalGain, screenMode);
                if (screenMode != ScreenShareAudioHelper.MODE_SCREEN_ONLY) {
                    VoiceChanger.process(micBuffer, 0, frameBytes, session.sampleRate, session.channels);
                }
                applyGain(micBuffer, session.gainMultiplier);

                block.nStreamID = session.streamId;
                block.uStreamTypes = 1;
                block.nSampleRate = session.sampleRate;
                block.nChannels = session.channels;
                block.nSamples = session.frameSamples;
                block.uSampleIndex = sampleIndex;
                block.lpRawAudio = micBuffer;
                insertBlockToClients(block);
                sampleIndex += session.frameSamples;
            }
        }
    }

    private CaptureSession openSessionForConfig(Config config) {
        AudioDeviceInfo preferredDevice = findPreferredInputDevice(config.preferredInputDeviceId);

        boolean isCallMic = MicrophoneInputHelper.EXPERIMENTAL_INPUT_DEVICE_CALL.equals(config.preferredInputDeviceId);

        // Call microphone (AudioSource.VOICE_COMMUNICATION) is telephony voice processing and strictly mono.
        // Also skip stereo pass if explicitly forced to MONO.
        if (isCallMic || config.captureMode == MicrophoneInputHelper.CAPTURE_MODE_MONO) {
            return openSessionInternal(config, preferredDevice, false);
        }

        // For DEFAULT (0) and STEREO (2): ALWAYS try Stereo first!
        CaptureSession stereoSession = openSessionInternal(config, preferredDevice, true);
        if (stereoSession != null) {
            Log.i(TAG, "Stereo microphone capture session initialized successfully (2 channels)");
            return stereoSession;
        }

        // If stereo failed on this device, smoothly fall back to mono
        Log.i(TAG, "Stereo microphone capture not supported by device, falling back to mono (1 channel)");
        return openSessionInternal(config, preferredDevice, false);
    }

    private CaptureSession openSessionInternal(Config config, AudioDeviceInfo preferredDevice, boolean stereo) {
        LinkedHashSet<Integer> sampleRates = new LinkedHashSet<>();
        if (stereo) {
            sampleRates.add(OpusConstants.DEFAULT_OPUS_SAMPLERATE);
            sampleRates.add(44100);
            if (config.sampleRate > 0) sampleRates.add(config.sampleRate);
            sampleRates.add(32000);
            sampleRates.add(16000);
            sampleRates.add(8000);
        } else {
            if (config.sampleRate > 0) sampleRates.add(config.sampleRate);
            sampleRates.add(OpusConstants.DEFAULT_OPUS_SAMPLERATE);
            sampleRates.add(16000);
            sampleRates.add(44100);
            sampleRates.add(32000);
            sampleRates.add(8000);
        }

        boolean isCallMic = MicrophoneInputHelper.EXPERIMENTAL_INPUT_DEVICE_CALL.equals(config.preferredInputDeviceId);
        int[] sources;
        int channelMask = stereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        if (isCallMic) {
            // Explicit call microphone requested by user - VOICE_COMMUNICATION is strictly mono
            sources = stereo ? new int[]{1, 6, 0, 9, 5} : new int[]{7, 1, 6, 0};
        } else if (stereo) {
            // Prioritize stereo-capable recording sources
            sources = new int[]{5, 9, 6, 1, 0}; // CAMCORDER, UNPROCESSED, VOICE_RECOGNITION, MIC, DEFAULT
        } else {
            // Standard mono sources: avoid VOICE_COMMUNICATION to prevent silencing other apps (e.g., Telegram voice messages)
            sources = new int[]{1, 6, 0, 9, 5}; // MIC, VOICE_RECOGNITION, DEFAULT, UNPROCESSED, CAMCORDER
        }

        for (int sr : sampleRates) {
            if (sr <= 0) continue;
            for (int source : sources) {
                CaptureSession session = buildCaptureSession(config, preferredDevice, sr, source, channelMask);
                if (session != null) {
                    if (startCaptureSession(session)) {
                        return session;
                    } else {
                        session.release();
                    }
                }
            }
        }
        return null;
    }

    private AudioDeviceInfo findPreferredInputDevice(String deviceId) {
        if (this.audioManager != null && !TextUtils.isEmpty(deviceId)
                && !MicrophoneInputHelper.EXPERIMENTAL_INPUT_DEVICE_DEFAULT.equals(deviceId)
                && !MicrophoneInputHelper.EXPERIMENTAL_INPUT_DEVICE_CALL.equals(deviceId)) {
            AudioDeviceInfo[] devices = this.audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            if (devices != null) {
                for (AudioDeviceInfo dev : devices) {
                    if (dev != null && String.valueOf(dev.getId()).equals(deviceId)) {
                        return dev;
                    }
                }
            }
        }
        return null;
    }

    private CaptureSession buildCaptureSession(Config config, AudioDeviceInfo preferredDevice, int sampleRate, int audioSource, int channelMask) {
        int channels = (channelMask == AudioFormat.CHANNEL_IN_STEREO) ? 2 : 1;
        int frameSamples = (sampleRate * Math.max(20, config.frameDurationMs)) / 1000;
        int frameBytes = frameSamples * channels * 2;
        int minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf < frameBytes * 2) {
            minBuf = frameBytes * 2;
        }

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();

        AudioRecord micRecord = null;
        try {
            micRecord = new AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(minBuf)
                    .build();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to create mic AudioRecord: " + t.getMessage());
            return null;
        }

        if (micRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            micRecord.release();
            return null;
        }

        if (preferredDevice != null && Build.VERSION.SDK_INT >= 23) {
            micRecord.setPreferredDevice(preferredDevice);
        }

        AudioRecord internalRecord = null;
        MediaProjection projection = this.service != null ? this.service.getMediaProjection() : null;
        if (projection != null && Build.VERSION.SDK_INT >= 29) {
            try {
                AudioPlaybackCaptureConfiguration playbackConfig = new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(1)  // USAGE_MEDIA
                        .addMatchingUsage(14) // USAGE_GAME
                        .addMatchingUsage(0)  // USAGE_UNKNOWN
                        .excludeUid(Process.myUid())
                        .build();

                AudioRecord internal = new AudioRecord.Builder()
                        .setAudioFormat(audioFormat)
                        .setBufferSizeInBytes(minBuf)
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .build();

                if (internal.getState() == AudioRecord.STATE_INITIALIZED) {
                    internalRecord = internal;
                } else {
                    internal.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize internal audio capture", e);
            }
        }

        PlatformPreprocessors preprocessors = attachPlatformPreprocessors(micRecord.getAudioSessionId());
        return new CaptureSession(micRecord, internalRecord, sampleRate, channels, frameSamples,
                config.streamId, config.gainMultiplier, audioSource, preprocessors);
    }

    private PlatformPreprocessors attachPlatformPreprocessors(int sessionId) {
        AcousticEchoCanceler aec = null;
        AutomaticGainControl agc = null;
        NoiseSuppressor ns = null;
        try {
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(sessionId);
            if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(sessionId);
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(sessionId);
        } catch (Throwable t) {
            Log.w(TAG, "Error attaching preprocessors", t);
        }
        return new PlatformPreprocessors(agc, ns, aec);
    }

    private boolean startCaptureSession(final CaptureSession session) {
        try {
            session.audioRecord.startRecording();
            session.applyPreprocessorsConfig(session.voxEnabled || session.audioSource == 7);
            if (session.internalAudioRecord != null) {
                try {
                    session.internalAudioRecord.startRecording();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start internal audio record", e);
                }
            }
            if (session.audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 29) {
                AudioManager.AudioRecordingCallback callback = new AudioManager.AudioRecordingCallback() {
                    private boolean wasSilenced = false;
                    @Override
                    public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
                        if (configs != null && running && session == captureSession) {
                            for (AudioRecordingConfiguration cfg : configs) {
                                if (cfg != null && cfg.getClientAudioSessionId() == session.audioRecord.getAudioSessionId()) {
                                    if (cfg.isClientSilenced()) {
                                        session.silencedByPolicy = true;
                                        if (!wasSilenced) {
                                            Log.w(TAG, "AudioRecord client silenced by system policy");
                                        }
                                        wasSilenced = true;
                                    } else {
                                        session.silencedByPolicy = false;
                                        if (wasSilenced) {
                                            wasSilenced = false;
                                            Log.i(TAG, "AudioRecord unsilenced. Scheduling restart.");
                                            scheduleRestart();
                                        }
                                    }
                                }
                            }
                        }
                    }
                };
                session.audioRecord.registerAudioRecordingCallback(new java.util.concurrent.Executor() {
                    @Override
                    public void execute(Runnable command) {
                        mainHandler.post(command);
                    }
                }, callback);
                session.recordingCallback = callback;
            }
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "startCaptureSession error", t);
            return false;
        }
    }
}
