package org.nekit.ttproplus.gui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.nekit.ttproplus.R;

public class RecordingPlaybackService extends Service {

    public static final String ACTION_START = "org.nekit.ttproplus.action.RECORDING_PLAYBACK_START";
    public static final String ACTION_UPDATE = "org.nekit.ttproplus.action.RECORDING_PLAYBACK_UPDATE";
    public static final String ACTION_TOGGLE = "org.nekit.ttproplus.action.RECORDING_PLAYBACK_TOGGLE";
    public static final String ACTION_REWIND = "org.nekit.ttproplus.action.RECORDING_PLAYBACK_REWIND";
    public static final String ACTION_FORWARD = "org.nekit.ttproplus.action.RECORDING_PLAYBACK_FORWARD";
    public static final String ACTION_STOP = "org.nekit.ttproplus.action.RECORDING_PLAYBACK_STOP";

    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_IS_PLAYING = "extra_is_playing";

    private static final String CHANNEL_ID = "recordings_playback";
    private static final int NOTIFICATION_ID = 2002;

    private MediaSessionCompat mediaSession;

    public static void startService(Context context, String title, boolean isPlaying) {
        if (context == null) return;
        Intent intent = new Intent(context, RecordingPlaybackService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }

    public static void updateService(Context context, String title, boolean isPlaying) {
        if (context == null) return;
        Intent intent = new Intent(context, RecordingPlaybackService.class);
        intent.setAction(ACTION_UPDATE);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_IS_PLAYING, isPlaying);
        try {
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    public static void stopService(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, RecordingPlaybackService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSession = new MediaSessionCompat(this, "RecordingPlaybackService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                RecordingPlayerManager.getInstance().resume();
            }

            @Override
            public void onPause() {
                RecordingPlayerManager.getInstance().pause();
            }

            @Override
            public void onStop() {
                RecordingPlayerManager.getInstance().stop();
            }

            @Override
            public void onFastForward() {
                RecordingPlayerManager.getInstance().forward(5000);
            }

            @Override
            public void onRewind() {
                RecordingPlayerManager.getInstance().rewind(5000);
            }

            @Override
            public void onSeekTo(long pos) {
                RecordingPlayerManager.getInstance().seekTo((int) pos);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            RecordingPlayerManager.getInstance().togglePlayPause();
            return START_STICKY;
        } else if (ACTION_REWIND.equals(action)) {
            RecordingPlayerManager.getInstance().rewind(5000);
            return START_STICKY;
        } else if (ACTION_FORWARD.equals(action)) {
            RecordingPlayerManager.getInstance().forward(5000);
            return START_STICKY;
        } else if (ACTION_STOP.equals(action)) {
            if (mediaSession != null) {
                mediaSession.setActive(false);
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        } else if (ACTION_START.equals(action) || ACTION_UPDATE.equals(action)) {
            String title = intent.getStringExtra(EXTRA_TITLE);
            boolean isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true);
            showNotification(title, isPlaying);
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    private void showNotification(String title, boolean isPlaying) {
        createNotificationChannel();

        int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            pFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        Intent openIntent = new Intent(this, RecordingsActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 10, openIntent, pFlags);

        Intent rewindIntent = new Intent(this, RecordingPlaybackService.class).setAction(ACTION_REWIND);
        PendingIntent piRewind = PendingIntent.getService(this, 11, rewindIntent, pFlags);

        Intent toggleIntent = new Intent(this, RecordingPlaybackService.class).setAction(ACTION_TOGGLE);
        PendingIntent piToggle = PendingIntent.getService(this, 12, toggleIntent, pFlags);

        Intent forwardIntent = new Intent(this, RecordingPlaybackService.class).setAction(ACTION_FORWARD);
        PendingIntent piForward = PendingIntent.getService(this, 13, forwardIntent, pFlags);

        Intent stopIntent = new Intent(this, RecordingPlaybackService.class).setAction(ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(this, 14, stopIntent, pFlags);

        if (mediaSession != null) {
            int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            long actions = PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_FAST_FORWARD
                    | PlaybackStateCompat.ACTION_REWIND
                    | PlaybackStateCompat.ACTION_SEEK_TO;

            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, RecordingPlayerManager.getInstance().getCurrentPosition(), 1.0f)
                    .build());

            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title != null ? title : "")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getString(R.string.title_activity_recordings))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, RecordingPlayerManager.getInstance().getDuration())
                    .build());
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.teamtalk_green)
                .setContentTitle(title != null ? title : getString(R.string.title_activity_recordings))
                .setContentText(getString(isPlaying ? R.string.recordings_playback_playing : R.string.recordings_playback_paused))
                .setContentIntent(piOpen)
                .addAction(R.drawable.ic_fast_rewind, getString(R.string.recordings_playback_rewind), piRewind)
                .addAction(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play, getString(isPlaying ? R.string.recordings_action_pause : R.string.recordings_action_play), piToggle)
                .addAction(R.drawable.ic_fast_forward, getString(R.string.recordings_playback_forward), piForward)
                .addAction(R.drawable.ic_delete, getString(R.string.recordings_playback_stop), piStop)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession != null ? mediaSession.getSessionToken() : null)
                        .setShowActionsInCompactView(0, 1, 2))
                .setOngoing(isPlaying)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        Notification notification = builder.build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ignored) {}
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.recordings_playback_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.recordings_playback_channel_desc));
                channel.setShowBadge(false);
                channel.setSound(null, null);
                channel.enableLights(false);
                channel.enableVibration(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
