package pl.kamil.tvpodglad;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.nio.ByteBuffer;

/** Usługa: przechwytuje ekran (MediaProjection) i uruchamia serwer HTTP. */
public class CaptureService extends Service {
    static final String TAG = "TvPodglad";
    static final String EXTRA_RESULT_CODE = "result_code";
    static final String EXTRA_DATA = "data";
    private static final String CHANNEL = "capture";
    private static final int NOTIF_ID = 1;

    static volatile boolean running = false;

    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler handler;
    private HttpServer server;

    private int capW, capH;
    private long lastGrab;
    private ByteBuffer padBuffer;
    private final Runnable grab = this::grabFrame;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());

        if (projection != null) return START_NOT_STICKY; // już działa
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        if (data == null || mpm == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) {
            Log.e(TAG, "Brak zgody na przechwytywanie ekranu");
            stopSelf();
            return START_NOT_STICKY;
        }

        thread = new HandlerThread("capture");
        thread.start();
        handler = new Handler(thread.getLooper());

        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                Log.i(TAG, "MediaProjection zatrzymane przez system");
                stopSelf();
            }
        };
        projection.registerCallback(projectionCallback, handler);

        DisplayMetrics m = new DisplayMetrics();
        WindowManager wm = getSystemService(WindowManager.class);
        wm.getDefaultDisplay().getRealMetrics(m);
        int screenW = Math.max(m.widthPixels, m.heightPixels);
        int screenH = Math.min(m.widthPixels, m.heightPixels);
        capW = Math.min(Config.MAX_WIDTH, screenW) & ~1;
        capH = Math.round(screenH * (capW / (float) screenW)) & ~1;
        Log.i(TAG, "Ekran " + screenW + "x" + screenH + " -> przechwytywanie " + capW + "x" + capH);

        reader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> onImageAvailable(), handler);
        virtualDisplay = projection.createVirtualDisplay("tv-podglad", capW, capH, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);

        server = new HttpServer(Config.PORT, FrameStore.get());
        server.start();
        running = true;
        return START_NOT_STICKY;
    }

    /** Ogranicza liczbę klatek do Config.FPS, ale zawsze dociąga ostatnią zmianę ekranu. */
    private void onImageAvailable() {
        handler.removeCallbacks(grab);
        long wait = lastGrab + 1000L / Config.FPS - SystemClock.elapsedRealtime();
        if (wait <= 0) grab.run();
        else handler.postDelayed(grab, wait);
    }

    private void grabFrame() {
        if (reader == null) return;
        Image img = null;
        try {
            img = reader.acquireLatestImage();
            if (img == null) return;
            Image.Plane plane = img.getPlanes()[0];
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int bufferWidth = rowStride / pixelStride;
            ByteBuffer src = plane.getBuffer();
            int need = bufferWidth * 4 * capH;
            if (src.remaining() < need) {
                // niektóre układy nie dodają paddingu w ostatnim wierszu - uzupełniamy bufor
                if (padBuffer == null || padBuffer.capacity() != need) {
                    padBuffer = ByteBuffer.allocateDirect(need);
                }
                padBuffer.clear();
                padBuffer.put(src);
                while (padBuffer.hasRemaining()) padBuffer.put((byte) 0);
                padBuffer.rewind();
                src = padBuffer;
            }
            FrameStore.get().putPixels(src, bufferWidth, capW, capH);
            lastGrab = SystemClock.elapsedRealtime();
        } catch (Exception e) {
            Log.w(TAG, "Błąd przechwycenia klatki", e);
        } finally {
            if (img != null) img.close();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        if (server != null) server.shutdown();
        if (virtualDisplay != null) virtualDisplay.release();
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
            reader = null;
        }
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
            projection = null;
        }
        if (thread != null) thread.quitSafely();
        FrameStore.get().clear();
        super.onDestroy();
    }

    private Notification buildNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Podgląd ekranu",
                NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("TV Podgląd działa")
                .setContentText("http://" + NetUtil.localIp() + ":" + Config.PORT)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }
}
