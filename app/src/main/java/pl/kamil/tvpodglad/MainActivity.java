package pl.kamil.tvpodglad;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Prosty ekran na TV: status, adres do wpisania w telefonie, start/stop. */
public class MainActivity extends Activity {
    static final String EXTRA_AUTOSTART = "autostart";
    private static final int REQ_CAPTURE = 1;

    private TextView status;
    private boolean autostart;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        autostart = getIntent().getBooleanExtra(EXTRA_AUTOSTART, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(80, 60, 80, 60);
        root.setBackgroundColor(Color.parseColor("#14212E"));

        TextView title = new TextView(this);
        title.setText("TV Podgląd");
        title.setTextSize(36);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(22);
        status.setTextColor(Color.parseColor("#CFE3F7"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 30, 0, 40);
        root.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button start = new Button(this);
        start.setText("Uruchom");
        start.setOnClickListener(v -> requestCapture());
        buttons.addView(start);

        Button stop = new Button(this);
        stop.setText("Zatrzymaj");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            ui.postDelayed(this::refresh, 500);
        });
        buttons.addView(stop);

        Button hide = new Button(this);
        hide.setText("Ukryj");
        hide.setOnClickListener(v -> moveTaskToBack(true));
        buttons.addView(hide);

        root.addView(buttons);
        setContentView(root);
        start.requestFocus();

        refresh();
        if (!CaptureService.running) requestCapture();
        else if (autostart) finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(EXTRA_AUTOSTART, false) && !CaptureService.running) {
            autostart = true;
            requestCapture();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void requestCapture() {
        if (CaptureService.running) {
            refresh();
            return;
        }
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            Intent svc = new Intent(this, CaptureService.class)
                    .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, data);
            startForegroundService(svc);
            if (autostart) {
                finish();
                return;
            }
            ui.postDelayed(this::refresh, 800);
        } else {
            status.setText("Nie udzielono zgody na przechwytywanie ekranu.\nNaciśnij „Uruchom” i zaakceptuj.");
        }
    }

    private void refresh() {
        if (status == null) return;
        String key = Config.ACCESS_KEY.isEmpty() ? "" : "/?key=" + Config.ACCESS_KEY;
        String url = "http://" + NetUtil.localIp() + ":" + Config.PORT + key;
        if (CaptureService.running) {
            status.setText("● Działa\n\nNa telefonie otwórz:\n" + url
                    + "\n\nZrzut: /snapshot.jpg   Strumień: /stream.mjpg");
        } else {
            status.setText("○ Zatrzymane\n\nAdres po uruchomieniu:\n" + url);
        }
    }
}
