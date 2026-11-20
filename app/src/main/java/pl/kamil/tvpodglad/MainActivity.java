package pl.kamil.tvpodglad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Ustawienia okienka TV naziemnej (PiP) i przycisku pilota. */
public class MainActivity extends Activity {
    private static final int REQ_EPG = 2;
    private static final String EPG_PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";

    private TextView info;
    private Button btnAcc, btnKey, btnInput, btnChannel, btnSize, btnSound, btnCh;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(80, 40, 80, 40);

        root.addView(text("TV Podgląd – okienko TV naziemnej", 30, Color.WHITE));

        info = text("", 17, Color.parseColor("#CFE3F7"));
        info.setPadding(0, 16, 0, 24);
        root.addView(info);

        root.addView(section("Przycisk"));
        LinearLayout r1 = row();
        btnAcc = button(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        btnKey = button(v -> learnKey());
        r1.addView(btnAcc);
        r1.addView(btnKey);
        root.addView(r1);

        root.addView(section("Okienko (PiP)"));
        LinearLayout r2 = row();
        btnInput = button(v -> chooseInput());
        btnChannel = button(v -> chooseChannel());
        r2.addView(btnInput);
        r2.addView(btnChannel);
        root.addView(r2);

        LinearLayout r3 = row();
        btnSize = button(v -> {
            Prefs.setSize(this, (Prefs.size(this) + 1) % Prefs.SIZES.length);
            refresh();
        });
        btnSound = button(v -> {
            Prefs.setSound(this, !Prefs.sound(this));
            refresh();
        });
        btnCh = button(v -> {
            Prefs.setChKeys(this, !Prefs.chKeys(this));
            refresh();
        });
        r3.addView(btnSize);
        r3.addView(btnSound);
        r3.addView(btnCh);
        root.addView(r3);

        LinearLayout r4 = row();
        Button test = button(v -> {
            if (KeyService.command("toggle") == null) {
                message("Najpierw włącz usługę „TV Podgląd” w ułatwieniach dostępu (przycisk wyżej).");
            }
            ui.postDelayed(this::refresh, 800);
        });
        test.setText("Test okienka (pokaż / ukryj)");
        r4.addView(test);
        Button hide = button(v -> moveTaskToBack(true));
        hide.setText("Zamknij");
        r4.addView(hide);
        root.addView(r4);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#14212E"));
        scroll.addView(root);
        setContentView(scroll);
        btnKey.requestFocus();

        if (checkSelfPermission(EPG_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{EPG_PERMISSION}, REQ_EPG);
        }
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        refresh();
    }

    // ---------- widoki ----------

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 20, Color.parseColor("#2E86DE"));
        t.setPadding(0, 20, 0, 6);
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    private Button button(View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void message(String msg) {
        new AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK", null).show();
    }

    // ---------- ustawienia ----------

    private void learnKey() {
        KeyService.learning = true;
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Ustaw przycisk okienka")
                .setMessage("Naciśnij teraz na pilocie przycisk, który ma pokazywać i chować okienko TV.\n\n"
                        + "Najlepiej kolorowy (czerwony/zielony/żółty/niebieski) albo inny rzadko używany.\n"
                        + "Strzałki, OK, Wstecz i Home są zablokowane.")
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
            message("System nie zgłasza żadnych wejść TV (TV Input Framework) – okienko nie zadziała na tym telewizorze.");
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
            message(perm ? "To wejście nie udostępnia listy kanałów innym aplikacjom."
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

    private void refresh() {
        if (info == null) return;
        boolean acc = KeyService.isEnabled();
        String keyName = Prefs.keyName(Prefs.key(this));
        btnAcc.setText(acc ? "Usługa przycisku: WŁ" : "Usługa przycisku: WYŁ – włącz");
        btnKey.setText("Przycisk: " + keyName + "  (zmień)");

        String input = TvInputs.effectiveInput(this);
        TvInputInfo ii = TvInputs.find(this, input);
        btnInput.setText("Źródło: " + (ii == null ? "brak tunera" : TvInputs.label(this, ii))
                + (Prefs.input(this) == null ? " (auto)" : ""));

        String chName = "pierwszy z listy";
        long chId = Prefs.channel(this);
        List<TvInputs.Channel> ch = TvInputs.channels(this, input);
        for (TvInputs.Channel c : ch) if (c.id == chId) chName = c.label();
        btnChannel.setText("Kanał: " + (ch.isEmpty() ? "lista niedostępna" : chName));

        btnSize.setText("Rozmiar: " + Prefs.SIZE_NAMES[Prefs.size(this)]);
        btnSound.setText("Dźwięk: " + (Prefs.sound(this) ? "WŁ" : "WYŁ"));
        btnCh.setText("CH+/CH− w okienku: " + (Prefs.chKeys(this) ? "WŁ" : "WYŁ"));

        if (!acc) {
            info.setText("Aby przycisk działał w każdym trybie (HDMI, aplikacje), włącz usługę ułatwień dostępu "
                    + "„TV Podgląd – przycisk okienka TV”.");
        } else {
            String st = KeyService.command("status");
            info.setText("Naciśnij " + keyName + " w dowolnym trybie, aby pokazać/ukryć okienko w prawym dolnym rogu."
                    + (Config.PORT > 0 ? "\nSterowanie z HA: http://" + NetUtil.localIp() + ":" + Config.PORT + "/pip/toggle" : "")
                    + (st != null ? "\nStan: " + st : ""));
        }
    }
}
