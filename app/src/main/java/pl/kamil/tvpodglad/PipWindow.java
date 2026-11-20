package pl.kamil.tvpodglad;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/**
 * Okienko w prawym dolnym rogu z obrazem z tunera (TvView / TV Input Framework).
 * Rysowane jako nakładka usługi ułatwień dostępu - nie zabiera fokusu, pilot dalej steruje HDMI.
 */
final class PipWindow {
    private static final String TAG = CaptureService.TAG;

    private final Context ctx;
    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private TvView tv;
    private TextView label;
    private boolean shown;
    private String inputId;
    private List<TvInputs.Channel> channels = Collections.emptyList();
    private int chIndex = -1;
    private volatile String status = "ukryte";

    private final Runnable hideLabel = () -> {
        if (label != null) label.setVisibility(TextView.GONE);
    };

    PipWindow(Context ctx, int windowType) {
        this.ctx = ctx;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.windowType = windowType;
    }

    private final int windowType;

    boolean isShown() {
        return shown;
    }

    String status() {
        return status;
    }

    void toggle() {
        if (shown) hide();
        else show();
    }

    void show() {
        if (shown) return;
        inputId = TvInputs.effectiveInput(ctx);
        if (inputId == null) {
            status = "brak wejścia tunera w systemie";
            Log.w(TAG, status);
            return;
        }

        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int screenW = Math.max(dm.widthPixels, dm.heightPixels);
        int w = Math.round(screenW * Prefs.SIZES[Prefs.size(ctx)]);
        int h = w * 9 / 16;
        int margin = screenW / 40;

        root = new FrameLayout(ctx);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.BLACK);
        frame.setStroke(3, Color.parseColor("#2E86DE"));
        root.setBackground(frame);
        root.setPadding(3, 3, 3, 3);

        tv = new TvView(ctx);
        root.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        label = new TextView(ctx);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setBackgroundColor(Color.parseColor("#B0000000"));
        label.setPadding(16, 8, 16, 8);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        root.addView(label, llp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = margin;
        lp.y = margin;

        try {
            wm.addView(root, lp);
        } catch (Exception e) {
            status = "nie można dodać okna: " + e.getMessage();
            Log.e(TAG, status, e);
            root = null;
            tv = null;
            return;
        }
        shown = true;

        tv.setCallback(callback);
        tv.setStreamVolume(Prefs.sound(ctx) ? 1f : 0f);

        channels = TvInputs.channels(ctx, inputId);
        chIndex = -1;
        long wanted = Prefs.channel(ctx);
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).id == wanted) chIndex = i;
        }
        if (chIndex < 0 && !channels.isEmpty()) chIndex = 0;
        tuneCurrent();
    }

    private void tuneCurrent() {
        if (tv == null) return;
        Uri uri;
        String name;
        if (chIndex >= 0 && chIndex < channels.size()) {
            TvInputs.Channel ch = channels.get(chIndex);
            uri = TvContract.buildChannelUri(ch.id);
            name = ch.label();
        } else {
            // brak listy kanałów (np. brak uprawnienia) - próbujemy wejścia "jako całości"
            uri = TvContract.buildChannelUriForPassthroughInput(inputId);
            name = "brak listy kanałów";
        }
        status = "strojenie: " + name;
        showLabel(name);
        try {
            tv.tune(inputId, uri);
        } catch (Exception e) {
            status = "błąd strojenia: " + e;
            showLabel("Błąd: " + e.getMessage());
            Log.e(TAG, "tune", e);
        }
    }

    void channelStep(int delta) {
        if (!shown || channels.isEmpty()) return;
        int n = channels.size();
        chIndex = ((chIndex < 0 ? 0 : chIndex) + delta % n + n) % n;
        Prefs.setChannel(ctx, channels.get(chIndex).id);
        tuneCurrent();
    }

    void hide() {
        if (!shown) return;
        ui.removeCallbacks(hideLabel);
        try {
            if (tv != null) tv.reset();
        } catch (Exception ignored) {
        }
        try {
            wm.removeView(root);
        } catch (Exception ignored) {
        }
        tv = null;
        root = null;
        label = null;
        shown = false;
        status = "ukryte";
    }

    private void showLabel(String text) {
        if (label == null) return;
        label.setText(text);
        label.setVisibility(TextView.VISIBLE);
        ui.removeCallbacks(hideLabel);
        ui.postDelayed(hideLabel, 3000);
    }

    private final TvView.TvInputCallback callback = new TvView.TvInputCallback() {
        @Override
        public void onConnectionFailed(String id) {
            status = "nie można połączyć z wejściem " + id;
            showPersistent("Tuner niedostępny");
        }

        @Override
        public void onDisconnected(String id) {
            status = "rozłączono (tuner zajęty przez system?)";
            showPersistent("Rozłączono");
        }

        @Override
        public void onVideoAvailable(String id) {
            status = "obraz OK";
            Log.i(TAG, "PiP: obraz dostępny");
        }

        @Override
        public void onVideoUnavailable(String id, int reason) {
            String r;
            switch (reason) {
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING: r = "strojenie…"; break;
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL: r = "słaby sygnał"; break;
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING: r = "buforowanie…"; break;
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY: r = "tylko dźwięk (radio)"; break;
                default: r = "brak obrazu (kod " + reason + ")";
            }
            status = "brak obrazu: " + r;
            Log.i(TAG, "PiP: " + status);
            if (reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING
                    && reason != TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) {
                showPersistent(r);
            }
        }

        @Override
        public void onChannelRetuned(String id, Uri channelUri) {
            Log.i(TAG, "PiP: przestrojono na " + channelUri);
        }
    };

    private void showPersistent(String text) {
        ui.post(() -> {
            if (label == null) return;
            label.setText(text);
            label.setVisibility(TextView.VISIBLE);
            ui.removeCallbacks(hideLabel);
        });
    }
}
