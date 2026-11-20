package pl.kamil.tvpodglad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Ekran na TV: podgląd ekranu (start/stop) + ustawienia okienka TV naziemnej. */
public class MainActivity extends Activity {
    static final String EXTRA_AUTOSTART = "autostart";
    private static final int REQ_CAPTURE = 1;
    private static final int REQ_EPG = 2;
    private static final String EPG_PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";

    private TextView status;
    private TextView pipInfo;
    private Button btnKey, btnInput, btnChannel, btnSize, btnSound, btnCh, btnAcc;
    private boolean autostart;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        autostart = getIntent().getBooleanExtra(EXTRA_AUTOSTART, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(80, 40, 80, 40);

        TextView title = text("TV Podgląd", 32, Color.WHITE);
        root.addView(title);

        status = text("", 18, Color.parseColor("#CFE3F7"));
        status.setPadding(0, 16, 0, 16);
        root.addView(status);

        LinearLayout row1 = row();
        Button start = button("Uruchom podgląd", v -> requestCapture());
        row1.addView(start);
        row1.addView(button("Zatrzymaj podgląd", v -> {
            stopService(new Intent(this, CaptureService.class));
            ui.postDelayed(this::refresh, 500);
        }));
        row1.addView(button("Ukryj", v -> moveTaskToBack(true)));
        root.addView(row1);

        TextView pipTitle = text("Okienko TV naziemnej", 24, Color.WHITE);
        pipTitle.setPadding(0, 36, 0, 8);
        root.addView(pipTitle);

        pipInfo = text("", 16, Color.parseColor("#CFE3F7"));
        root.addView(pipInfo);

        LinearLayout row2 = row();
        btnAcc = button("", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        row2.addView(btnAcc);
        btnKey = button("", v -> learnKey());
        row2.addView(btnKey);
        row2.addView(button("Test okienka", v -> {
            String r = KeyService.command("toggle");
            if (r == null) info("Najpierw włącz usługę ułatwień dostępu „TV Podgląd”.");
            ui.postDelayed(this::refresh, 800);
        }));
        root.addView(row2);

        LinearLayout row3 = row();
        btnInput = button("", v -> chooseInput());
        row3.addView(btnInput);
        btnChannel = button("", v -> chooseChannel());
        row3.addView(btnChannel);
        root.addView(row3);

        LinearLayout row4 = row();
        btnSize = button("", v -> {
            Prefs.setSize(this, (Prefs.size(this) + 1) % Prefs.SIZES.length);
            refresh();
        });
        row4.addView(btnSize);
        btnSound = button("", v -> {
            Prefs.setSound(this, !Prefs.sound(this));
            refresh();
        });
        row4.addView(btnSound);
        btnCh = button("", v -> {
            Prefs.setChKeys(this, !Prefs.chKeys(this));
            refresh();
        });
        row4.addView(btnCh);
        root.addView(row4);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#14212E"));
        scroll.addView(root);
        setContentView(scroll);
        start.requestFocus();

        if (checkSelfPermission(EPG_PERMISSION) != PackageManager.PERMISSION_GRANTED && !autostart) {
            requestPermissions(new String[]{EPG_PERMISSION}, REQ_EPG);
        }

        refresh();
        if (!CaptureService.running) requestCapture();
        else if (autostart) finish();
    }

    // ---------- pomocnicze widoki ----------

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    private Button button(String s, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void info(String msg) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show();
    }

    // ---------- okienko TV: ustawienia ----------

    private void learnKey() {
        KeyService.learning = true;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Ustaw przycisk okienka")
                .setMessage("Naciśnij teraz na pilocie przycisk, który ma pokazywać i chować okienko TV.\n\n"
                        + "Najlepiej kolorowy (czerwony/zielony/żółty/niebieski) albo inny rzadko używany.\n"
                        + "Strzałki, OK i Wstecz są zablokowane.")
                .setNegativeButton("Anuluj", null)
                .create();
        d.setOnKeyListener((dialog, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_HOME:
                    return false;
                default:
                    Prefs.setKey(this, keyCode);
                    dialog.dismiss();
                    refresh();
                    return true;
            }
        });
        d.setOnDismissListener(x -> KeyService.learning = false);
        d.show();
    }

    private void chooseInput() {
        List<TvInputInfo> inputs = TvInputs.inputs(this);
        if (inputs.isEmpty()) {
            info("System nie zgłasza żadnych wejść TV (TV Input Framework). Okienko nie zadziała na tym telewizorze.");
            return;
        }
        String[] labels = new String[inputs.size() + 1];
        labels[0] = "Automatycznie (tuner naziemny)";
        for (int i = 0; i < inputs.size(); i++) labels[i + 1] = TvInputs.label(this, inputs.get(i));
        new AlertDialog.Builder(this).setTitle("Źródło okienka")
                .setItems(labels, (d, which) -> {
                    Prefs.setInput(this, which == 0 ? null : inputs.get(which - 1).getId());
                    refresh();
                }).show();
    }

    private void chooseChannel() {
        String input = TvInputs.effectiveInput(this);
        List<TvInputs.Channel> ch = TvInputs.channels(this, input);
        if (ch.isEmpty()) {
            boolean perm = checkSelfPermission(EPG_PERMISSION) == PackageManager.PERMISSION_GRANTED;
            info(perm ? "To wejście nie udostępnia listy kanałów innym aplikacjom."
                    : "Brak uprawnienia do listy kanałów. Zezwól w oknie systemowym albo przez adb:\n"
                    + "adb shell pm grant pl.kamil.tvpodglad " + EPG_PERMISSION);
            if (!perm) requestPermissions(new String[]{EPG_PERMISSION}, REQ_EPG);
            return;
        }
        String[] labels = new String[ch.size()];
        for (int i = 0; i < ch.size(); i++) labels[i] = ch.get(i).label();
        new AlertDialog.Builder(this).setTitle("Kanał w okienku")
                .setItems(labels, (d, which) -> {
                    Prefs.setChannel(this, ch.get(which).id);
                    refresh();
                }).show();
    }

    // ---------- przechwytywanie ekranu ----------

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
            status.setText("Nie udzielono zgody na przechwytywanie ekranu.\nNaciśnij „Uruchom podgląd” i zaakceptuj.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        refresh();
    }

    private void refresh() {
        if (status == null) return;
        String key = Config.ACCESS_KEY.isEmpty() ? "" : "/?key=" + Config.ACCESS_KEY;
        String url = "http://" + NetUtil.localIp() + ":" + Config.PORT + key;
        status.setText(CaptureService.running
                ? "● Podgląd działa:  " + url
                : "○ Podgląd zatrzymany  (" + url + ")");

        boolean acc = KeyService.isEnabled();
        btnAcc.setText(acc ? "Usługa przycisku: WŁ" : "Usługa przycisku: WYŁ – włącz");
        btnKey.setText("Przycisk: " + Prefs.keyName(Prefs.key(this)));

        String input = TvInputs.effectiveInput(this);
        TvInputInfo info = TvInputs.find(this, input);
        btnInput.setText("Źródło: " + (info == null ? "brak tunera" : TvInputs.label(this, info))
                + (Prefs.input(this) == null ? " (auto)" : ""));

        String chName = "pierwszy z listy";
        long chId = Prefs.channel(this);
        List<TvInputs.Channel> ch = TvInputs.channels(this, input);
        for (TvInputs.Channel c : ch) if (c.id == chId) chName = c.label();
        btnChannel.setText("Kanał: " + (ch.isEmpty() ? "lista niedostępna" : chName));

        btnSize.setText("Rozmiar: " + Prefs.SIZE_NAMES[Prefs.size(this)]);
        btnSound.setText("Dźwięk z okienka: " + (Prefs.sound(this) ? "WŁ" : "WYŁ"));
        btnCh.setText("CH+/CH− w okienku: " + (Prefs.chKeys(this) ? "WŁ" : "WYŁ"));

        String pip = KeyService.command("status");
        pipInfo.setText(acc
                ? "Naciśnij " + Prefs.keyName(Prefs.key(this)) + " w dowolnym trybie (np. HDMI), aby pokazać/ukryć okienko."
                + (pip != null ? "\nStan: " + pip : "")
                : "Okienko wymaga włączenia usługi ułatwień dostępu „TV Podgląd” (przycisk poniżej albo adb).");
    }
}
