package org.nekit.ttproplus.gui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingPlayerManager implements AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "RecordingPlayerManager";
    private static volatile RecordingPlayerManager sInstance;

    public interface PlaybackListener {
        void onPlaybackStateChanged(boolean isPlaying, File file, String title);
        void onPlaybackProgress(int currentMs, int totalMs);
        void onPlaybackCompleted();
    }

    private final List<PlaybackListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Context appContext;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private PowerManager.WakeLock wakeLock;

    private File currentFile;
    private String currentTitle;
    private boolean isPlaying = false;
    private boolean wasPlayingBeforeFocusLoss = false;
    private int lastDuration = 0;
    private int lastPosition = 0;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && isPlaying) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    lastPosition = pos;
                    if (dur > 0) {
                        lastDuration = dur;
                    }
                    notifyProgress(pos, lastDuration);
                } catch (Exception ignored) {}
                mainHandler.postDelayed(this, 250);
            }
        }
    };

    private RecordingPlayerManager() {}

    public static RecordingPlayerManager getInstance() {
        if (sInstance == null) {
            synchronized (RecordingPlayerManager.class) {
                if (sInstance == null) {
                    sInstance = new RecordingPlayerManager();
                }
            }
        }
        return sInstance;
    }

    public synchronized void play(Context context, File file, String title) {
        if (context != null && appContext == null) {
            appContext = context.getApplicationContext();
        }

        if (file == null || !file.exists()) {
            Log.e(TAG, "File does not exist or is null");
            return;
        }

        if (currentFile != null && currentFile.equals(file) && mediaPlayer != null) {
            togglePlayPause();
            return;
        }

        stopInternal(false);

        this.currentFile = file;
        this.currentTitle = title != null ? title : file.getName();
        this.lastPosition = 0;
        this.lastDuration = 0;

        try {
            ensureWakeLock();
            requestAudioFocus();

            mediaPlayer = new MediaPlayer();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            mediaPlayer.setAudioAttributes(attrs);
            mediaPlayer.setDataSource(file.getAbsolutePath());

            mediaPlayer.setOnPreparedListener(mp -> {
                try {
                    lastDuration = mp.getDuration();
                    mp.start();
                    isPlaying = true;
                    acquireWakeLock();
                    startProgressUpdates();

                    if (appContext != null) {
                        RecordingPlaybackService.startService(appContext, currentTitle, true);
                    }
                    notifyStateChanged(true);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting MediaPlayer after prepare", e);
                    stop();
                }
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                isPlaying = false;
                lastPosition = lastDuration;
                stopProgressUpdates();
                releaseWakeLock();
                abandonAudioFocus();

                if (appContext != null) {
                    RecordingPlaybackService.stopService(appContext);
                }
                notifyCompleted();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                stop();
                return true;
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaPlayer", e);
            stop();
        }
    }

    public synchronized void pause() {
        if (mediaPlayer != null && isPlaying) {
            try {
                mediaPlayer.pause();
                lastPosition = mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
            isPlaying = false;
            stopProgressUpdates();
            releaseWakeLock();

            if (appContext != null) {
                RecordingPlaybackService.updateService(appContext, currentTitle, false);
            }
            notifyStateChanged(false);
        }
    }

    public synchronized void resume() {
        if (mediaPlayer != null && !isPlaying) {
            requestAudioFocus();
            try {
                mediaPlayer.start();
                isPlaying = true;
                acquireWakeLock();
                startProgressUpdates();

                if (appContext != null) {
                    RecordingPlaybackService.updateService(appContext, currentTitle, true);
                }
                notifyStateChanged(true);
            } catch (Exception e) {
                Log.e(TAG, "Error resuming playback", e);
            }
        }
    }

    public synchronized void togglePlayPause() {
        if (isPlaying) {
            pause();
        } else {
            resume();
        }
    }

    public synchronized void rewind(int ms) {
        if (mediaPlayer != null) {
            try {
                int pos = Math.max(0, mediaPlayer.getCurrentPosition() - ms);
                mediaPlayer.seekTo(pos);
                lastPosition = pos;
                notifyProgress(pos, lastDuration);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void forward(int ms) {
        if (mediaPlayer != null) {
            try {
                int dur = lastDuration > 0 ? lastDuration : mediaPlayer.getDuration();
                int pos = Math.min(dur, mediaPlayer.getCurrentPosition() + ms);
                mediaPlayer.seekTo(pos);
                lastPosition = pos;
                notifyProgress(pos, lastDuration);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void seekTo(int pos) {
        if (mediaPlayer != null) {
            try {
                int target = Math.max(0, Math.min(lastDuration, pos));
                mediaPlayer.seekTo(target);
                lastPosition = target;
                notifyProgress(target, lastDuration);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean notifyListeners) {
        stopProgressUpdates();
        releaseWakeLock();
        abandonAudioFocus();

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }

        boolean hadFile = (currentFile != null);
        isPlaying = false;
        currentFile = null;
        currentTitle = null;
        lastPosition = 0;
        lastDuration = 0;
        wasPlayingBeforeFocusLoss = false;

        if (appContext != null) {
            RecordingPlaybackService.stopService(appContext);
        }

        if (notifyListeners && hadFile) {
            notifyStateChanged(false);
        }
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    public synchronized boolean hasActiveTrack() {
        return currentFile != null && mediaPlayer != null;
    }

    public synchronized File getCurrentFile() {
        return currentFile;
    }

    public synchronized String getCurrentTitle() {
        return currentTitle;
    }

    public synchronized int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                lastPosition = mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return lastPosition;
    }

    public synchronized int getDuration() {
        if (mediaPlayer != null && lastDuration <= 0) {
            try {
                lastDuration = mediaPlayer.getDuration();
            } catch (Exception ignored) {}
        }
        return lastDuration;
    }

    public void addListener(PlaybackListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(PlaybackListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    private void notifyStateChanged(final boolean playing) {
        final File file = currentFile;
        final String title = currentTitle;
        mainHandler.post(() -> {
            for (PlaybackListener l : listeners) {
                l.onPlaybackStateChanged(playing, file, title);
            }
        });
    }

    private void notifyProgress(final int currentMs, final int totalMs) {
        mainHandler.post(() -> {
            for (PlaybackListener l : listeners) {
                l.onPlaybackProgress(currentMs, totalMs);
            }
        });
    }

    private void notifyCompleted() {
        mainHandler.post(() -> {
            for (PlaybackListener l : listeners) {
                l.onPlaybackCompleted();
            }
        });
    }

    private void startProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable);
        mainHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable);
    }

    private void ensureWakeLock() {
        if (wakeLock == null && appContext != null) {
            PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TeamTalk:RecordingPlayback");
                wakeLock.setReferenceCounted(false);
            }
        }
    }

    private void acquireWakeLock() {
        try {
            ensureWakeLock();
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(30 * 60 * 1000L); // 30 mins safety timeout
            }
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {}
    }

    private void requestAudioFocus() {
        if (appContext == null) return;
        if (audioManager == null) {
            audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        }
        if (audioManager == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(this)
                        .build();
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
        } catch (Exception ignored) {}
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            } else {
                audioManager.abandonAudioFocus(this);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                pause();
                wasPlayingBeforeFocusLoss = false;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (isPlaying) {
                    pause();
                    wasPlayingBeforeFocusLoss = true;
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (wasPlayingBeforeFocusLoss) {
                    wasPlayingBeforeFocusLoss = false;
                    resume();
                }
                break;
        }
    }
}
