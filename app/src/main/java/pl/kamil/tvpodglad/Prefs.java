package pl.kamil.tvpodglad;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

/** Ustawienia okienka TV (zmieniane z ekranu aplikacji na TV). */
final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("pip", Context.MODE_PRIVATE);
    }

    static final float[] SIZES = {0.25f, 0.33f, 0.45f};
    static final String[] SIZE_NAMES = {"Mały", "Średni", "Duży"};

    /** Przycisk pilota przełączający okienko. Domyślnie niebieski. */
    static int key(Context c) {
        return sp(c).getInt("key", KeyEvent.KEYCODE_PROG_BLUE);
    }

    static void setKey(Context c, int key) {
        sp(c).edit().putInt("key", key).apply();
    }

    /** Id wejścia TV (TvInputInfo.getId()); null = wybór automatyczny. */
    static String input(Context c) {
        return sp(c).getString("input", null);
    }

    static void setInput(Context c, String id) {
        sp(c).edit().putString("input", id).remove("channel").apply();
    }

    /** Id kanału z TvContract; -1 = pierwszy z listy. */
    static long channel(Context c) {
        return sp(c).getLong("channel", -1);
    }

    static void setChannel(Context c, long id) {
        sp(c).edit().putLong("channel", id).apply();
    }

    static int size(Context c) {
        return Math.max(0, Math.min(SIZES.length - 1, sp(c).getInt("size", 1)));
    }

    static void setSize(Context c, int s) {
        sp(c).edit().putInt("size", s).apply();
    }

    static boolean sound(Context c) {
        return sp(c).getBoolean("sound", false);
    }

    static void setSound(Context c, boolean on) {
        sp(c).edit().putBoolean("sound", on).apply();
    }

    /** CH+/CH- zmienia kanał w okienku, gdy jest widoczne. */
    static boolean chKeys(Context c) {
        return sp(c).getBoolean("ch_keys", true);
    }

    static void setChKeys(Context c, boolean on) {
        sp(c).edit().putBoolean("ch_keys", on).apply();
    }

    static String keyName(int code) {
        String s = KeyEvent.keyCodeToString(code);
        return s.startsWith("KEYCODE_") ? s.substring(8) : s;
    }
}
