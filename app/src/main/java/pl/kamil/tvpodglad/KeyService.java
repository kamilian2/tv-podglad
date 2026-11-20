package pl.kamil.tvpodglad;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Usługa ułatwień dostępu: przechwytuje wybrany przycisk pilota w każdym trybie
 * (HDMI, aplikacje, kanały) i przełącza okienko z TV naziemnej.
 */
public class KeyService extends AccessibilityService {
    private static volatile KeyService instance;
    /** Gdy true - przyciski przechodzą dalej (trwa nauka przycisku w ustawieniach). */
    static volatile boolean learning;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private PipWindow pip;

    static boolean isEnabled() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        pip = new PipWindow(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        instance = this;
        Log.i(CaptureService.TAG, "KeyService połączony, przycisk = " + Prefs.keyName(Prefs.key(this)));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (pip != null) pip.hide();
        super.onDestroy();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (learning || pip == null) return false;
        int code = e.getKeyCode();

        if (code == Prefs.key(this)) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) pip.toggle();
            return true; // zjadamy przycisk, żeby nie trafił do HDMI/aplikacji
        }

        if (pip.isShown() && Prefs.chKeys(this)
                && (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                pip.channelStep(code == KeyEvent.KEYCODE_CHANNEL_UP ? 1 : -1);
            }
            return true;
        }
        return false;
    }

    /** Wywołanie z innego wątku (np. serwer HTTP). Zwraca status albo null, gdy usługa wyłączona. */
    static String command(String cmd) {
        KeyService s = instance;
        if (s == null) return null;
        final String[] result = new String[1];
        CountDownLatch done = new CountDownLatch(1);
        Runnable r = () -> {
            try {
                switch (cmd) {
                    case "show": s.pip.show(); break;
                    case "hide": s.pip.hide(); break;
                    case "toggle": s.pip.toggle(); break;
                    case "next": s.pip.channelStep(1); break;
                    case "prev": s.pip.channelStep(-1); break;
                    default: break;
                }
                result[0] = "{\"shown\":" + s.pip.isShown() + ",\"status\":" + TvInputs.q(s.pip.status())
                        + ",\"key\":" + TvInputs.q(Prefs.keyName(Prefs.key(s))) + "}";
            } finally {
                done.countDown();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
            return result[0];
        }
        s.ui.post(r);
        try {
            done.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }
}
