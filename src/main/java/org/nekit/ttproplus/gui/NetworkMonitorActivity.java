package org.nekit.ttproplus.gui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dk.bearware.Channel;
import dk.bearware.ClientStatistics;
import dk.bearware.IntPtr;
import dk.bearware.TeamTalkBase;
import dk.bearware.User;
import dk.bearware.UserStatistics;
import org.nekit.ttproplus.R;
import org.nekit.ttproplus.backend.TeamTalkConnection;
import org.nekit.ttproplus.backend.TeamTalkConnectionListener;
import org.nekit.ttproplus.backend.TeamTalkService;
import org.nekit.ttproplus.data.ServerEntry;

public class NetworkMonitorActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private TeamTalkConnection mConnection;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;
    private boolean autoRefresh = true;

    private TextView txtQualityBadge;
    private TextView txtServer;
    private TextView txtInterface;
    private TextView txtEncryption;
    private TextView txtUptime;

    private TextView txtUdpPing;
    private TextView txtTcpPing;
    private TextView txtJitter;
    private TextView txtSilence;

    private TextView txtVoiceTxStatus;
    private TextView txtVoiceLoss;
    private TextView txtActiveTalkers;
    private TextView txtMediaLoss;
    private TextView txtVideoLoss;

    private TextView txtSpeedTotal;
    private TextView txtVolumeTotal;
    private TextView txtStreamVoice;
    private TextView txtStreamMedia;
    private TextView txtStreamVideo;
    private TextView txtStreamDesktop;

    private CheckBox chkAutoRefresh;
    private Button btnReset;
    private Button btnCopyReport;

    private ClientStatistics prevStats;
    private long prevStatsTime = 0;
    private long sessionStartTime = 0;

    private final List<Integer> pingSamples = new ArrayList<>();
    private static final int MAX_PING_SAMPLES = 30;

    private long initialRxBytes = -1;
    private long initialTxBytes = -1;
    private long lastDoPingTime = 0;

    TeamTalkService getService() {
        return mConnection != null ? mConnection.getService() : null;
    }

    TeamTalkBase getClient() {
        TeamTalkService service = getService();
        return service != null ? service.getTTInstance() : null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_monitor);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_activity_network_monitor);
        }

        initViews();
        sessionStartTime = SystemClock.elapsedRealtime();

        mConnection = new TeamTalkConnection(this);
        Intent intent = new Intent(this, TeamTalkService.class);
        if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Toast.makeText(this, R.string.err_service_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void initViews() {
        txtQualityBadge = findViewById(R.id.net_quality_badge);
        txtServer = findViewById(R.id.net_server_text);
        txtInterface = findViewById(R.id.net_interface_text);
        txtEncryption = findViewById(R.id.net_encryption_text);
        txtUptime = findViewById(R.id.net_uptime_text);

        txtUdpPing = findViewById(R.id.net_udp_ping);
        txtTcpPing = findViewById(R.id.net_tcp_ping);
        txtJitter = findViewById(R.id.net_jitter);
        txtSilence = findViewById(R.id.net_silence);

        txtVoiceTxStatus = findViewById(R.id.net_voice_tx_status);
        txtVoiceLoss = findViewById(R.id.net_voice_loss);
        txtActiveTalkers = findViewById(R.id.net_active_talkers);
        txtMediaLoss = findViewById(R.id.net_media_loss);
        txtVideoLoss = findViewById(R.id.net_video_loss);

        txtSpeedTotal = findViewById(R.id.net_speed_total);
        txtVolumeTotal = findViewById(R.id.net_volume_total);
        txtStreamVoice = findViewById(R.id.net_stream_voice);
        txtStreamMedia = findViewById(R.id.net_stream_media);
        txtStreamVideo = findViewById(R.id.net_stream_video);
        txtStreamDesktop = findViewById(R.id.net_stream_desktop);

        chkAutoRefresh = findViewById(R.id.net_chk_auto_refresh);
        btnReset = findViewById(R.id.net_btn_reset);
        btnCopyReport = findViewById(R.id.net_btn_copy_report);

        chkAutoRefresh.setOnCheckedChangeListener((buttonView, isChecked) -> {
            autoRefresh = isChecked;
            if (isChecked) {
                startMonitoring();
            } else {
                stopMonitoring();
            }
        });

        btnReset.setOnClickListener(v -> resetStatistics());
        btnCopyReport.setOnClickListener(v -> copyDiagnosticReport());
    }

    private void startMonitoring() {
        stopMonitoring();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                refreshMetrics();
                if (autoRefresh) {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.post(updateRunnable);
    }

    private void stopMonitoring() {
        if (updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (autoRefresh) {
            startMonitoring();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMonitoring();
    }

    @Override
    protected void onDestroy() {
        stopMonitoring();
        if (mConnection.isBound()) {
            unbindService(mConnection);
        }
        super.onDestroy();
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        if (getClient() != null) {
            getClient().doPing();
        }
        refreshMetrics();
        if (autoRefresh) {
            startMonitoring();
        }
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        stopMonitoring();
    }

    private void resetStatistics() {
        pingSamples.clear();
        prevStats = null;
        prevStatsTime = 0;
        initialRxBytes = -1;
        initialTxBytes = -1;
        sessionStartTime = SystemClock.elapsedRealtime();
        if (getClient() != null) {
            getClient().doPing();
        }
        refreshMetrics();
        Toast.makeText(this, R.string.net_stats_reset_toast, Toast.LENGTH_SHORT).show();
    }

    private void refreshMetrics() {
        TeamTalkService service = getService();
        TeamTalkBase client = getClient();

        if (client == null || service == null) {
            setDisconnectedState();
            return;
        }

        int flags = client.getFlags();
        boolean isAuthorized = (flags & 32768) == 32768; // CLIENT_AUTHORIZED
        boolean isConnected = isAuthorized || (flags & 16384) == 16384 || (flags & 24576) != 0;

        ServerEntry server = service.getServerEntry();
        String serverHost = server != null ? server.ipaddr + ":" + server.tcpport : getString(R.string.stat_offline);
        int udpPort = server != null ? server.udpport : 0;
        txtServer.setText(getString(R.string.net_server_format, serverHost, udpPort));

        txtInterface.setText(getString(R.string.net_interface_format, getNetworkTypeName()));

        boolean isEncrypted = server != null && server.encrypted;
        txtEncryption.setText(getString(R.string.net_encryption_format,
                getString(isEncrypted ? R.string.net_encryption_enabled : R.string.net_encryption_disabled)));

        long sessionSeconds = (SystemClock.elapsedRealtime() - sessionStartTime) / 1000;
        txtUptime.setText(getString(R.string.net_uptime_format, formatDuration(sessionSeconds)));

        if (!isConnected) {
            setDisconnectedState();
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastDoPingTime > 2500) {
            lastDoPingTime = now;
            client.doPing();
        }

        ClientStatistics stats = new ClientStatistics();
        if (!client.getClientStatistics(stats)) {
            return;
        }

        long timeDeltaMs = prevStatsTime > 0 ? (now - prevStatsTime) : 1000;
        if (timeDeltaMs <= 0) timeDeltaMs = 1000;

        if (initialRxBytes < 0) {
            initialRxBytes = stats.nUdpBytesRecv;
            initialTxBytes = stats.nUdpBytesSent;
        }

        // Ping and Jitter
        int currentUdpPing = stats.nUdpPingTimeMs;
        int currentTcpPing = stats.nTcpPingTimeMs;
        int effectivePing = currentUdpPing >= 0 ? currentUdpPing : currentTcpPing;

        if (effectivePing >= 0) {
            pingSamples.add(effectivePing);
            if (pingSamples.size() > MAX_PING_SAMPLES) {
                pingSamples.remove(0);
            }
        }

        int minPing = effectivePing >= 0 ? effectivePing : 0;
        int maxPing = effectivePing >= 0 ? effectivePing : 0;
        int sumPing = 0;
        if (!pingSamples.isEmpty()) {
            minPing = pingSamples.get(0);
            maxPing = pingSamples.get(0);
            for (int p : pingSamples) {
                if (p < minPing) minPing = p;
                if (p > maxPing) maxPing = p;
                sumPing += p;
            }
        }
        int avgPing = !pingSamples.isEmpty() ? (sumPing / pingSamples.size()) : (effectivePing >= 0 ? effectivePing : 0);
        double jitter = calculateJitter(pingSamples);

        if (currentUdpPing >= 0) {
            txtUdpPing.setText(getString(R.string.net_udp_ping_format, currentUdpPing, minPing, avgPing, maxPing));
        } else if (currentTcpPing >= 0) {
            txtUdpPing.setText(getString(R.string.net_udp_ping_format, currentTcpPing, minPing, avgPing, maxPing));
        } else {
            txtUdpPing.setText(R.string.net_quality_calculating);
        }

        txtTcpPing.setText(getString(R.string.net_tcp_ping_format, currentTcpPing >= 0 ? currentTcpPing : 0));
        txtJitter.setText(getString(R.string.net_jitter_format, String.format(Locale.ROOT, "%.1f", jitter)));
        txtSilence.setText(getString(R.string.net_silence_format, stats.nUdpServerSilenceSec, stats.nTcpServerSilenceSec));

        // Voice & Media Streams
        PacketLossStats lossStats = calculatePacketLoss(client, service);

        // Outgoing TX Voice
        boolean isTxActive = service.isVoiceTransmissionEnabled() || (client.getFlags() & 256) != 0;
        long rxVoiceDelta = prevStats != null ? Math.max(0, stats.nVoiceBytesRecv - prevStats.nVoiceBytesRecv) : 0;
        long txVoiceDelta = prevStats != null ? Math.max(0, stats.nVoiceBytesSent - prevStats.nVoiceBytesSent) : 0;
        double txVoiceSpeed = (txVoiceDelta * 1000.0) / (timeDeltaMs * 1024.0);
        if (isTxActive) {
            txtVoiceTxStatus.setText(getString(R.string.net_tx_voice_active, String.format(Locale.ROOT, "%.1f", txVoiceSpeed)));
        } else {
            txtVoiceTxStatus.setText(R.string.net_tx_voice_idle);
        }

        // Incoming RX Voice Loss
        if (lossStats.voicePacketsTotal > 0) {
            txtVoiceLoss.setText(getString(R.string.net_loss_voice_active_format,
                    String.format(Locale.ROOT, "%.2f", lossStats.voiceLossPercent),
                    lossStats.voicePacketsLost, lossStats.voicePacketsTotal));
        } else {
            txtVoiceLoss.setText(R.string.net_loss_voice_idle_format);
        }

        // Active Talkers
        if (!lossStats.activeTalkers.isEmpty()) {
            StringBuilder talkersSb = new StringBuilder();
            for (int i = 0; i < lossStats.activeTalkers.size(); i++) {
                if (i > 0) talkersSb.append(", ");
                talkersSb.append(lossStats.activeTalkers.get(i));
            }
            txtActiveTalkers.setText(getString(R.string.net_active_talkers_format, talkersSb.toString()));
        } else {
            txtActiveTalkers.setText(R.string.net_active_talkers_none);
        }

        txtMediaLoss.setText(getString(R.string.net_loss_media_format,
                String.format(Locale.ROOT, "%.2f", lossStats.mediaLossPercent),
                lossStats.mediaPacketsLost, lossStats.mediaPacketsTotal));
        txtVideoLoss.setText(getString(R.string.net_loss_video_format,
                String.format(Locale.ROOT, "%.2f", lossStats.videoLossPercent),
                lossStats.videoFramesLost, lossStats.videoFramesTotal, lossStats.videoFramesDropped));

        // Bandwidth
        long rxBytesDelta = prevStats != null ? Math.max(0, stats.nUdpBytesRecv - prevStats.nUdpBytesRecv) : 0;
        long txBytesDelta = prevStats != null ? Math.max(0, stats.nUdpBytesSent - prevStats.nUdpBytesSent) : 0;
        double rxSpeedKB = (rxBytesDelta * 1000.0) / (timeDeltaMs * 1024.0);
        double txSpeedKB = (txBytesDelta * 1000.0) / (timeDeltaMs * 1024.0);
        long rxMediaDelta = prevStats != null ? Math.max(0, stats.nMediaFileAudioBytesRecv - prevStats.nMediaFileAudioBytesRecv) : 0;
        long txMediaDelta = prevStats != null ? Math.max(0, stats.nMediaFileAudioBytesSent - prevStats.nMediaFileAudioBytesSent) : 0;
        long rxVideoDelta = prevStats != null ? Math.max(0, stats.nVideoCaptureBytesRecv - prevStats.nVideoCaptureBytesRecv) : 0;
        long txVideoDelta = prevStats != null ? Math.max(0, stats.nVideoCaptureBytesSent - prevStats.nVideoCaptureBytesSent) : 0;
        long rxDesktopDelta = prevStats != null ? Math.max(0, stats.nDesktopBytesRecv - prevStats.nDesktopBytesRecv) : 0;
        long txDesktopDelta = prevStats != null ? Math.max(0, stats.nDesktopBytesSent - prevStats.nDesktopBytesSent) : 0;

        txtSpeedTotal.setText(getString(R.string.net_speed_total_format,
                String.format(Locale.ROOT, "%.1f", rxSpeedKB),
                String.format(Locale.ROOT, "%.1f", txSpeedKB)));
        txtVolumeTotal.setText(getString(R.string.net_volume_total_format,
                formatBytes(stats.nUdpBytesRecv),
                formatBytes(stats.nUdpBytesSent)));

        txtStreamVoice.setText(getString(R.string.net_stream_voice_format,
                String.format(Locale.ROOT, "%.1f", (rxVoiceDelta * 1000.0) / (timeDeltaMs * 1024.0)),
                String.format(Locale.ROOT, "%.1f", (txVoiceDelta * 1000.0) / (timeDeltaMs * 1024.0))));
        txtStreamMedia.setText(getString(R.string.net_stream_media_format,
                String.format(Locale.ROOT, "%.1f", (rxMediaDelta * 1000.0) / (timeDeltaMs * 1024.0)),
                String.format(Locale.ROOT, "%.1f", (txMediaDelta * 1000.0) / (timeDeltaMs * 1024.0))));
        txtStreamVideo.setText(getString(R.string.net_stream_video_format,
                String.format(Locale.ROOT, "%.1f", (rxVideoDelta * 1000.0) / (timeDeltaMs * 1024.0)),
                String.format(Locale.ROOT, "%.1f", (txVideoDelta * 1000.0) / (timeDeltaMs * 1024.0))));
        txtStreamDesktop.setText(getString(R.string.net_stream_desktop_format,
                String.format(Locale.ROOT, "%.1f", (rxDesktopDelta * 1000.0) / (timeDeltaMs * 1024.0)),
                String.format(Locale.ROOT, "%.1f", (txDesktopDelta * 1000.0) / (timeDeltaMs * 1024.0))));

        // Overall Quality Score (0 to 100)
        int score = computeQualityScore(effectivePing, jitter, lossStats.voiceLossPercent, stats.nTcpServerSilenceSec, isConnected);
        updateQualityBadge(score);

        prevStats = stats;
        prevStatsTime = now;
    }

    private int computeQualityScore(int ping, double jitter, double lossPercent, int tcpSilenceSec, boolean isConnected) {
        if (!isConnected) {
            return 0;
        }
        if (ping < 0) {
            return 95;
        }

        double score = 100.0;

        // Ping latency impact
        if (ping <= 50) {
            // Excellent latency
        } else if (ping <= 100) {
            score -= (ping - 50) * 0.15; // 50ms..100ms: -0..-7.5
        } else if (ping <= 200) {
            score -= 7.5 + (ping - 100) * 0.2; // 100ms..200ms: -7.5..-27.5
        } else if (ping <= 350) {
            score -= 27.5 + (ping - 200) * 0.25; // 200ms..350ms: -27.5..-65
        } else {
            score -= 65.0 + Math.min(30.0, (ping - 350) * 0.1);
        }

        // Jitter impact
        if (jitter > 30.0) {
            score -= 20.0;
        } else if (jitter > 15.0) {
            score -= 10.0;
        } else if (jitter > 5.0) {
            score -= (jitter - 5.0) * 0.5;
        }

        // Packet loss impact
        if (lossPercent > 10.0) {
            score -= 40.0;
        } else if (lossPercent > 5.0) {
            score -= 25.0;
        } else if (lossPercent > 1.0) {
            score -= lossPercent * 3.0;
        } else if (lossPercent > 0.0) {
            score -= lossPercent * 2.0;
        }

        // TCP Silence impact (connection heartbeat stall)
        if (tcpSilenceSec > 20) {
            score -= 35.0;
        } else if (tcpSilenceSec > 10) {
            score -= (tcpSilenceSec - 10) * 2.0;
        }

        int result = (int) Math.round(score);
        return Math.max(5, Math.min(100, result));
    }

    private void updateQualityBadge(int score) {
        String label;
        int color;

        if (score >= 90) {
            label = getString(R.string.net_quality_excellent);
            color = Color.parseColor("#2E7D32"); // Green
        } else if (score >= 75) {
            label = getString(R.string.net_quality_good);
            color = Color.parseColor("#388E3C"); // Light Green
        } else if (score >= 50) {
            label = getString(R.string.net_quality_fair);
            color = Color.parseColor("#F57C00"); // Orange
        } else if (score >= 25) {
            label = getString(R.string.net_quality_poor);
            color = Color.parseColor("#E64A19"); // Deep Orange
        } else {
            label = getString(R.string.net_quality_critical);
            color = Color.parseColor("#C62828"); // Red
        }

        txtQualityBadge.setText(getString(R.string.net_quality_badge_format, score, label));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(12f);
        drawable.setColor(color);
        txtQualityBadge.setBackground(drawable);
    }

    private void setDisconnectedState() {
        txtQualityBadge.setText(R.string.stat_offline);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(12f);
        drawable.setColor(Color.parseColor("#C62828"));
        txtQualityBadge.setBackground(drawable);

        txtUdpPing.setText(getString(R.string.net_udp_ping_format, 0, 0, 0, 0));
        txtTcpPing.setText(getString(R.string.net_tcp_ping_format, 0));
        txtJitter.setText(getString(R.string.net_jitter_format, "0.0"));
        txtSilence.setText(getString(R.string.net_silence_format, 0, 0));

        txtVoiceTxStatus.setText(R.string.net_tx_voice_idle);
        txtVoiceLoss.setText(R.string.net_loss_voice_idle_format);
        txtActiveTalkers.setText(R.string.net_active_talkers_none);
        txtMediaLoss.setText(getString(R.string.net_loss_media_format, "0.00", 0, 0));
        txtVideoLoss.setText(getString(R.string.net_loss_video_format, "0.00", 0, 0, 0));

        txtSpeedTotal.setText(getString(R.string.net_speed_total_format, "0.0", "0.0"));
        txtVolumeTotal.setText(getString(R.string.net_volume_total_format, "0 B", "0 B"));
    }

    private double calculateJitter(List<Integer> samples) {
        if (samples.size() < 2) return 0.0;
        double sumDelta = 0;
        for (int i = 1; i < samples.size(); i++) {
            sumDelta += Math.abs(samples.get(i) - samples.get(i - 1));
        }
        return sumDelta / (samples.size() - 1);
    }

    private PacketLossStats calculatePacketLoss(TeamTalkBase client, TeamTalkService service) {
        PacketLossStats loss = new PacketLossStats();
        if (client == null || service == null) return loss;

        Map<Integer, User> users = service.getUsers();
        if (users != null) {
            for (User u : users.values()) {
                if (u != null && u.nUserID != client.getMyUserID()) {
                    if ((u.uUserState & 1) != 0) { // USERSTATE_VOICE
                        String name = u.szNickname != null && !u.szNickname.trim().isEmpty() ? u.szNickname.trim() : ("ID " + u.nUserID);
                        loss.activeTalkers.add(name);
                    }
                    UserStatistics ustats = new UserStatistics();
                    if (client.getUserStatistics(u.nUserID, ustats)) {
                        loss.voicePacketsLost += ustats.nVoicePacketsLost;
                        loss.voicePacketsRecv += ustats.nVoicePacketsRecv;
                        loss.mediaPacketsLost += ustats.nMediaFileAudioPacketsLost;
                        loss.mediaPacketsRecv += ustats.nMediaFileAudioPacketsRecv;
                        loss.videoFramesLost += ustats.nVideoCaptureFramesLost;
                        loss.videoFramesRecv += ustats.nVideoCaptureFramesRecv;
                        loss.videoFramesDropped += ustats.nVideoCaptureFramesDropped;
                    }
                }
            }
        }

        loss.voicePacketsTotal = loss.voicePacketsLost + loss.voicePacketsRecv;
        loss.mediaPacketsTotal = loss.mediaPacketsLost + loss.mediaPacketsRecv;
        loss.videoFramesTotal = loss.videoFramesLost + loss.videoFramesRecv;

        if (loss.voicePacketsTotal > 0) {
            loss.voiceLossPercent = (loss.voicePacketsLost * 100.0) / loss.voicePacketsTotal;
        }
        if (loss.mediaPacketsTotal > 0) {
            loss.mediaLossPercent = (loss.mediaPacketsLost * 100.0) / loss.mediaPacketsTotal;
        }
        if (loss.videoFramesTotal > 0) {
            loss.videoLossPercent = (loss.videoFramesLost * 100.0) / loss.videoFramesTotal;
        }

        return loss;
    }

    private String getNetworkTypeName() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return "Wi-Fi";
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        return getString(R.string.net_type_cellular);
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        return "Ethernet";
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return "VPN";
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.net_type_unknown);
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, secs);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        else return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void copyDiagnosticReport() {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        sb.append("Отчет о качестве соединения TeamTalk\n");
        sb.append("Дата отчета: ").append(sdf.format(new Date())).append("\n");
        sb.append("--------------------------------------------------\n");
        sb.append(txtServer.getText()).append("\n");
        sb.append(txtInterface.getText()).append("\n");
        sb.append(txtEncryption.getText()).append("\n");
        sb.append(txtUptime.getText()).append("\n");
        sb.append("Качество соединения: ").append(txtQualityBadge.getText()).append("\n\n");

        sb.append("Параметры задержки:\n");
        sb.append("  ").append(txtUdpPing.getText()).append("\n");
        sb.append("  ").append(txtTcpPing.getText()).append("\n");
        sb.append("  ").append(txtJitter.getText()).append("\n");
        sb.append("  ").append(txtSilence.getText()).append("\n\n");

        sb.append("Голосовые и медиапотоки:\n");
        sb.append("  ").append(txtVoiceTxStatus.getText()).append("\n");
        sb.append("  ").append(txtVoiceLoss.getText()).append("\n");
        sb.append("  ").append(txtActiveTalkers.getText()).append("\n");
        sb.append("  ").append(txtMediaLoss.getText()).append("\n");
        sb.append("  ").append(txtVideoLoss.getText()).append("\n\n");

        sb.append("Скорость и трафик:\n");
        sb.append("  ").append(txtSpeedTotal.getText()).append("\n");
        sb.append("  ").append(txtVolumeTotal.getText()).append("\n");
        sb.append("  ").append(txtStreamVoice.getText()).append("\n");
        sb.append("  ").append(txtStreamMedia.getText()).append("\n");
        sb.append("  ").append(txtStreamVideo.getText()).append("\n");
        sb.append("  ").append(txtStreamDesktop.getText()).append("\n");

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("network_report", sb.toString()));
            Toast.makeText(this, R.string.net_report_copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class PacketLossStats {
        long voicePacketsLost = 0;
        long voicePacketsRecv = 0;
        long voicePacketsTotal = 0;
        double voiceLossPercent = 0.0;

        long mediaPacketsLost = 0;
        long mediaPacketsRecv = 0;
        long mediaPacketsTotal = 0;
        double mediaLossPercent = 0.0;

        long videoFramesLost = 0;
        long videoFramesRecv = 0;
        long videoFramesTotal = 0;
        long videoFramesDropped = 0;
        double videoLossPercent = 0.0;

        List<String> activeTalkers = new ArrayList<>();
    }
}
