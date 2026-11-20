package pl.kamil.tvpodglad;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Wejścia TV (tuner, HDMI...) i kanały z systemowej bazy TvProvider. */
final class TvInputs {
    private TvInputs() {}

    static final class Channel {
        final long id;
        final String number;
        final String name;
        final String type;
        final String inputId;

        Channel(long id, String number, String name, String type, String inputId) {
            this.id = id;
            this.inputId = inputId;
            this.number = number == null ? "" : number;
            this.name = name == null ? "" : name;
            this.type = type == null ? "" : type;
        }

        String label() {
            return (number.isEmpty() ? "" : number + "  ") + name;
        }
    }

    static List<TvInputInfo> inputs(Context c) {
        TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (m == null) return Collections.emptyList();
        try {
            return m.getTvInputList();
        } catch (Exception e) {
            Log.w(Config.TAG, "getTvInputList", e);
            return Collections.emptyList();
        }
    }

    static TvInputInfo find(Context c, String id) {
        if (id == null) return null;
        for (TvInputInfo i : inputs(c)) if (i.getId().equals(id)) return i;
        return null;
    }

    static String label(Context c, TvInputInfo i) {
        CharSequence l = null;
        try {
            l = i.loadLabel(c);
        } catch (Exception ignored) {
        }
        return (l == null ? i.getId() : l.toString()) + " [" + typeName(i.getType()) + "]";
    }

    static String typeName(int t) {
        switch (t) {
            case TvInputInfo.TYPE_TUNER: return "tuner";
            case TvInputInfo.TYPE_HDMI: return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE: return "AV";
            case TvInputInfo.TYPE_COMPONENT: return "YPbPr";
            case TvInputInfo.TYPE_SCART: return "SCART";
            case TvInputInfo.TYPE_VGA: return "VGA";
            case TvInputInfo.TYPE_DVI: return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "DP";
            case TvInputInfo.TYPE_SVIDEO: return "S-Video";
            default: return "inne(" + t + ")";
        }
    }

    /**
     * Wybór automatyczny: sprzętowy tuner cyfrowy (DTV) producenta TV.
     * Pomija "tunery" aplikacji (Filmy Google Play, SkyShowtime - podglądy wideo).
     */
    static String defaultTunerId(Context c) {
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (TvInputInfo i : inputs(c)) {
            if (i.getType() != TvInputInfo.TYPE_TUNER) continue;
            String s = (i.getId() + " " + label(c, i)).toLowerCase(Locale.ROOT);
            int score = 0;
            if (s.contains("preview") || s.contains("com.google.android.videos")) score -= 100;
            if (s.contains("mediatek") || s.contains("droidtv") || s.contains("philips") || s.contains("tpvision")) score += 20;
            if (s.contains("tunerinputservice")) score += 10;
            if (s.contains("dtv") || s.contains("dvb") || s.contains("terr") || s.contains("anten") || s.contains("naziem")) score += 30;
            if (s.contains("atv") || s.contains("analog")) score -= 20;
            if (s.contains("sat") || s.contains("cable") || s.contains("kabl")) score -= 5;
            if (score > bestScore) {
                bestScore = score;
                best = i.getId();
            }
        }
        return best;
    }

    static String effectiveInput(Context c) {
        String id = Prefs.input(c);
        if (id != null && find(c, id) != null) return id;
        return defaultTunerId(c);
    }

    /** Kanały danego wejścia. Wymaga uprawnienia READ_EPG_DATA - bez niego zwraca pustą listę. */
    static List<Channel> channels(Context c, String inputId) {
        List<Channel> out = new ArrayList<>();
        if (inputId == null) return out;
        out.addAll(query(c, TvContract.buildChannelsUriForInput(inputId)));
        if (out.isEmpty()) {
            // Część TV zapisuje kanały pod innym id wejścia - szukamy w całej bazie
            // kanałów z tej samej usługi (np. com.mediatek.tvinput/.dtv.TunerInputService/...).
            String svc = inputId.contains("/") ? inputId.substring(0, inputId.lastIndexOf('/')) : inputId;
            String pkg = inputId.contains("/") ? inputId.substring(0, inputId.indexOf('/')) : inputId;
            List<Channel> all = allChannels(c);
            for (Channel ch : all) if (ch.inputId != null && ch.inputId.startsWith(svc)) out.add(ch);
            if (out.isEmpty()) {
                for (Channel ch : all) if (ch.inputId != null && ch.inputId.startsWith(pkg)) out.add(ch);
            }
        }
        Collections.sort(out, (a, b) -> compareNumbers(a.number, b.number));
        return out;
    }

    /** Wszystkie kanały w systemowej bazie TvProvider (niezależnie od wejścia). */
    static List<Channel> allChannels(Context c) {
        return query(c, TvContract.Channels.CONTENT_URI);
    }

    private static List<Channel> query(Context c, android.net.Uri uri) {
        List<Channel> out = new ArrayList<>();
        String[] proj = {TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_TYPE,
                TvContract.Channels.COLUMN_INPUT_ID};
        try (Cursor cur = c.getContentResolver().query(uri, proj, null, null, null)) {
            if (cur != null) {
                while (cur.moveToNext()) {
                    out.add(new Channel(cur.getLong(0), cur.getString(1), cur.getString(2), cur.getString(3),
                            cur.getString(4)));
                }
            }
        } catch (Exception e) {
            Log.w(Config.TAG, "Brak dostępu do listy kanałów (" + uri + "): " + e);
        }
        return out;
    }

    /** JSON: wszystkie kanały w bazie (/tv/channels). */
    static String channelsJson(Context c) {
        List<Channel> all = allChannels(c);
        boolean perm = c.checkSelfPermission("com.android.providers.tv.permission.READ_EPG_DATA")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        StringBuilder sb = new StringBuilder("{\"epgPermission\":").append(perm)
                .append(",\"count\":").append(all.size()).append(",\"channels\":[");
        for (int i = 0; i < Math.min(300, all.size()); i++) {
            Channel ch = all.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(ch.id).append(",\"number\":").append(q(ch.number))
                    .append(",\"name\":").append(q(ch.name)).append(",\"type\":").append(q(ch.type))
                    .append(",\"input\":").append(q(ch.inputId)).append('}');
        }
        return sb.append("]}").toString();
    }

    /** Porównuje numery kanałów typu "1", "2", "10", "1-1". */
    private static int compareNumbers(String a, String b) {
        String[] pa = a.split("[^0-9]+");
        String[] pb = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            long x = i < pa.length ? parse(pa[i]) : -1;
            long y = i < pb.length ? parse(pb[i]) : -1;
            if (x != y) return Long.compare(x, y);
        }
        return a.compareTo(b);
    }

    private static long parse(String s) {
        try {
            return s.isEmpty() ? Long.MAX_VALUE : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    /** JSON z wejściami i kanałami - diagnostyka (/tv/inputs). */
    static String json(Context c) {
        boolean perm = c.checkSelfPermission("com.android.providers.tv.permission.READ_EPG_DATA")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        StringBuilder sb = new StringBuilder("{\"selected\":").append(q(effectiveInput(c)))
                .append(",\"epgPermission\":").append(perm).append(",\"inputs\":[");
        boolean first = true;
        for (TvInputInfo i : inputs(c)) {
            if (!first) sb.append(',');
            first = false;
            List<Channel> ch = channels(c, i.getId());
            sb.append("{\"id\":").append(q(i.getId()))
                    .append(",\"label\":").append(q(label(c, i)))
                    .append(",\"type\":").append(q(typeName(i.getType())))
                    .append(",\"passthrough\":").append(i.isPassthroughInput())
                    .append(",\"channels\":").append(ch.size())
                    .append(",\"sample\":[");
            for (int k = 0; k < Math.min(10, ch.size()); k++) {
                if (k > 0) sb.append(',');
                sb.append(q(ch.get(k).label() + " (id " + ch.get(k).id + ", " + ch.get(k).type + ")"));
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
