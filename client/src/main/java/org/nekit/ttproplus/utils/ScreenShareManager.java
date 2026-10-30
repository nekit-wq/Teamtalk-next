package org.nekit.ttproplus.utils;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import dk.bearware.DesktopWindow;
import dk.bearware.TeamTalkBase;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class ScreenShareManager {
    private static final String TAG = "ScreenShareManager";
    private final Context context;
    private final ClientProvider clientProvider;
    private final Handler mainHandler;
    private Handler handler;
    private HandlerThread handlerThread;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private MediaProjection mediaProjection;
    private OnStoppedBySystemListener stoppedBySystemListener;
    private final PowerManager powerManager;
    private PowerManager.WakeLock screenShareWakeLock;
    private long lastFrameTimestampMs = 0;
    private static final long MIN_FRAME_INTERVAL_MS = 66; // Limit to ~15 FPS to prevent CPU/bandwidth saturation

    private int width = 1280;
    private int height = 720;
    private boolean isSharing = false;
    private volatile boolean stoppingByApp = false;
    private byte[] cachedFrameBuffer = null;

    private synchronized byte[] getOrCreateFrameBuffer(int size) {
        if (this.cachedFrameBuffer == null || this.cachedFrameBuffer.length != size) {
            this.cachedFrameBuffer = new byte[size];
        }
        return this.cachedFrameBuffer;
    }

    public interface ClientProvider {
        List<TeamTalkBase> getClients();
    }

    public interface OnStoppedBySystemListener {
        void onScreenShareStoppedBySystem();
    }

    public ScreenShareManager(Context context, final TeamTalkBase teamTalkBase) {
        this(context, new ClientProvider() {
            @Override
            public List<TeamTalkBase> getClients() {
                return teamTalkBase != null ? Collections.singletonList(teamTalkBase) : Collections.<TeamTalkBase>emptyList();
            }
        });
    }

    public ScreenShareManager(Context context, ClientProvider clientProvider) {
        this.context = context;
        this.clientProvider = clientProvider;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.powerManager = context != null ? (PowerManager) context.getSystemService(Context.POWER_SERVICE) : null;
    }

    public void setOnStoppedBySystemListener(OnStoppedBySystemListener listener) {
        this.stoppedBySystemListener = listener;
    }

    public boolean isSharing() {
        return this.isSharing;
    }

    public synchronized void startShare(MediaProjection projection) {
        if (this.isSharing) {
            return;
        }
        this.mediaProjection = projection;
        this.isSharing = true;
        this.stoppingByApp = false;
        this.lastFrameTimestampMs = 0;

        if (this.powerManager != null) {
            try {
                this.screenShareWakeLock = this.powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ttproplus:ScreenShareLock");
                this.screenShareWakeLock.setReferenceCounted(false);
                this.screenShareWakeLock.acquire();
            } catch (Exception e) {
                Log.w(TAG, "Failed to acquire screenShareWakeLock", e);
            }
        }

        HandlerThread ht = new HandlerThread("ScreenShareThread");
        this.handlerThread = ht;
        ht.start();
        this.handler = new Handler(this.handlerThread.getLooper());

        this.imageReader = ImageReader.newInstance(this.width, this.height, 1, 2); // PixelFormat.RGBA_8888 = 1
        this.mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                if (ScreenShareManager.this.stoppingByApp) {
                    return;
                }
                ScreenShareManager.this.stopShareInternal();
                final OnStoppedBySystemListener listener = ScreenShareManager.this.stoppedBySystemListener;
                if (listener != null) {
                    ScreenShareManager.this.mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onScreenShareStoppedBySystem();
                        }
                    });
                }
            }
        }, this.handler);

        this.virtualDisplay = this.mediaProjection.createVirtualDisplay(
                "ScreenShare",
                this.width,
                this.height,
                1,
                16, // DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                this.imageReader.getSurface(),
                null,
                this.handler
        );

        this.imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image == null) {
                        return;
                    }
                    if (ScreenShareManager.this.powerManager != null && !ScreenShareManager.this.powerManager.isInteractive()) {
                        // Screen is turned off / phone is locked.
                        // Skip processing to avoid 100% CPU lockup and severe audio thread starvation.
                        return;
                    }
                    long now = SystemClock.uptimeMillis();
                    if (now - ScreenShareManager.this.lastFrameTimestampMs < MIN_FRAME_INTERVAL_MS) {
                        // Drop excessive frames (e.g. on 90Hz/120Hz displays) to maintain stable audio & network streaming
                        return;
                    }
                    ScreenShareManager.this.lastFrameTimestampMs = now;
                    processImage(image);
                } catch (Exception e) {
                    Log.e(TAG, "Error acquiring image", e);
                } finally {
                    if (image != null) {
                        try {
                            image.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }, this.handler);
    }

    private void processImage(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int rowStride = planes[0].getRowStride();
        int frameSize = this.width * this.height * 4;
        byte[] frameBuffer = getOrCreateFrameBuffer(frameSize);
        int offset = 0;

        for (int row = 0; row < this.height; row++) {
            buffer.position(row * rowStride);
            buffer.get(frameBuffer, offset, this.width * 4);
            offset += this.width * 4;
        }

        DesktopWindow desktopWindow = new DesktopWindow();
        desktopWindow.nWidth = this.width;
        desktopWindow.nHeight = this.height;
        desktopWindow.bmpFormat = 4; // BMP_RGBA_32BIT
        desktopWindow.nBytesPerLine = this.width * 4;
        desktopWindow.frameBuffer = frameBuffer;

        List<TeamTalkBase> clients = this.clientProvider != null ? this.clientProvider.getClients() : null;
        if (clients != null) {
            for (TeamTalkBase client : clients) {
                if (client != null && (client.getFlags() & 32768) != 0 && client.getMyChannelID() > 0) {
                    try {
                        client.sendDesktopWindow(desktopWindow, 0);
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending desktop window", e);
                    }
                }
            }
        }
    }

    public synchronized void stopShare() {
        if (this.isSharing) {
            this.stoppingByApp = true;
            stopShareInternal();
        }
    }

    public synchronized void stopShareInternal() {
        if (this.isSharing) {
            this.isSharing = false;
            List<TeamTalkBase> clients = this.clientProvider != null ? this.clientProvider.getClients() : null;
            if (clients != null) {
                for (TeamTalkBase client : clients) {
                    if (client != null) {
                        try {
                            client.closeDesktopWindow();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (this.virtualDisplay != null) {
                this.virtualDisplay.release();
                this.virtualDisplay = null;
            }
            if (this.imageReader != null) {
                this.imageReader.close();
                this.imageReader = null;
            }
            if (this.mediaProjection != null) {
                this.mediaProjection.stop();
                this.mediaProjection = null;
            }
            if (this.handlerThread != null) {
                this.handlerThread.quitSafely();
                this.handlerThread = null;
            }
            if (this.screenShareWakeLock != null && this.screenShareWakeLock.isHeld()) {
                try {
                    this.screenShareWakeLock.release();
                } catch (Exception ignored) {}
                this.screenShareWakeLock = null;
            }
            this.cachedFrameBuffer = null;
        }
    }
}
