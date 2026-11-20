package pl.kamil.tvpodglad;

import android.graphics.Bitmap;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Trzyma ostatnią klatkę ekranu jako Bitmap i koduje ją do JPEG dopiero wtedy,
 * gdy ktoś o nią poprosi (oszczędza CPU telewizora).
 */
final class FrameStore {
    private static final FrameStore INSTANCE = new FrameStore();

    static FrameStore get() {
        return INSTANCE;
    }

    static final class Snap {
        final byte[] jpeg;
        final long seq;
        final long time;

        Snap(byte[] jpeg, long seq, long time) {
            this.jpeg = jpeg;
            this.seq = seq;
            this.time = time;
        }
    }

    private Bitmap raw;          // bufor o szerokości rowStride/pixelStride
    private int width, height;   // właściwy rozmiar obrazu
    private long seq;            // numer ostatniej klatki
    private long frameTime;      // elapsedRealtime ostatniej klatki
    private byte[] jpeg;         // zakodowana klatka (cache)
    private long jpegSeq = -1;

    /** Kopiuje piksele z ImageReadera. Wywoływane z wątku przechwytywania. */
    synchronized void putPixels(ByteBuffer src, int bufferWidth, int w, int h) {
        if (raw == null || raw.getWidth() != bufferWidth || raw.getHeight() != h) {
            if (raw != null) raw.recycle();
            raw = Bitmap.createBitmap(bufferWidth, h, Bitmap.Config.ARGB_8888);
        }
        src.rewind();
        raw.copyPixelsFromBuffer(src);
        width = w;
        height = h;
        seq++;
        frameTime = SystemClock.elapsedRealtime();
        notifyAll();
    }

    synchronized long seq() {
        return seq;
    }

    synchronized int width() {
        return width;
    }

    synchronized int height() {
        return height;
    }

    synchronized long frameAgeMs() {
        return seq == 0 ? -1 : SystemClock.elapsedRealtime() - frameTime;
    }

    /**
     * Czeka (max timeoutMs) na klatkę nowszą niż afterSeq.
     * Zwraca aktualną klatkę (może być ta sama, jeśli minął czas) albo null, gdy jeszcze nic nie ma.
     */
    synchronized Snap awaitNewer(long afterSeq, long timeoutMs) throws InterruptedException {
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        while (seq <= afterSeq) {
            long left = end - SystemClock.elapsedRealtime();
            if (left <= 0) break;
            wait(left);
        }
        return current();
    }

    /** Bieżąca klatka jako JPEG albo null. */
    synchronized Snap current() {
        if (seq == 0 || raw == null) return null;
        if (jpegSeq != seq) {
            Bitmap out = raw;
            boolean cropped = false;
            if (raw.getWidth() != width) {
                out = Bitmap.createBitmap(raw, 0, 0, width, height);
                cropped = true;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
            out.compress(Bitmap.CompressFormat.JPEG, Config.JPEG_QUALITY, bos);
            if (cropped) out.recycle();
            jpeg = bos.toByteArray();
            jpegSeq = seq;
        }
        return new Snap(jpeg, seq, frameTime);
    }

    synchronized void clear() {
        if (raw != null) raw.recycle();
        raw = null;
        jpeg = null;
        jpegSeq = -1;
        seq = 0;
        notifyAll();
    }
}
