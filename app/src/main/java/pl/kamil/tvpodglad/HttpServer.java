package pl.kamil.tvpodglad;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Minimalny serwer HTTP bez zewnętrznych bibliotek.
 *   /              strona z podglądem (na telefon)
 *   /snapshot.jpg  pojedynczy zrzut ekranu
 *   /stream.mjpg   strumień MJPEG (przeglądarka, Home Assistant, VLC)
 *   /status        JSON ze stanem
 *   /pip/toggle|show|hide|next|prev|status  okienko TV naziemnej
 *   /tv/inputs     diagnostyka: wejścia TV i kanały
 */
final class HttpServer extends Thread {
    private static final String TAG = CaptureService.TAG;
    private static final int MAX_STREAMS = 4;

    private final int port;
    private final FrameStore store;
    private final android.content.Context ctx;
    private volatile boolean stopped;
    private volatile ServerSocket serverSocket;
    private int activeStreams;

    HttpServer(android.content.Context ctx, int port, FrameStore store) {
        super("http-server");
        this.ctx = ctx.getApplicationContext();
        this.port = port;
        this.store = store;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            serverSocket = ss;
            Log.i(TAG, "Serwer HTTP na porcie " + port);
            while (!stopped) {
                Socket s = ss.accept();
                Thread t = new Thread(() -> handle(s), "http-client");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (!stopped) Log.e(TAG, "Serwer HTTP padł", e);
        }
    }

    void shutdown() {
        stopped = true;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    private void handle(Socket s) {
        try (Socket socket = s) {
            socket.setSoTimeout(10000);
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) return;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // nagłówki pomijamy
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String target = parts[1];
            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }

            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 64 * 1024);

            if (!Config.ACCESS_KEY.isEmpty() && !Config.ACCESS_KEY.equals(param(query, "key"))) {
                sendText(out, 403, "text/plain", "Brak dostępu - dopisz ?key=...");
                return;
            }

            switch (path) {
                case "/":
                case "/index.html":
                    sendText(out, 200, "text/html; charset=utf-8", WebPage.html(query));
                    break;
                case "/snapshot.jpg":
                case "/screenshot.jpg":
                case "/shot.jpg":
                    sendSnapshot(out, query.contains("download"));
                    break;
                case "/stream.mjpg":
                case "/video":
                    socket.setSoTimeout(0);
                    sendStream(out);
                    break;
                case "/pip/toggle":
                case "/pip/show":
                case "/pip/hide":
                case "/pip/next":
                case "/pip/prev":
                case "/pip/status": {
                    String r = KeyService.command(path.substring(5));
                    if (r == null) {
                        sendText(out, 503, "application/json",
                                "{\"error\":\"usluga ulatwien dostepu TV Podglad jest wylaczona\"}");
                    } else {
                        sendText(out, 200, "application/json", r);
                    }
                    break;
                }
                case "/tv/inputs":
                    sendText(out, 200, "application/json; charset=utf-8", TvInputs.json(ctx));
                    break;
                case "/status":
                    sendText(out, 200, "application/json", statusJson());
                    break;
                default:
                    sendText(out, 404, "text/plain", "404");
            }
        } catch (Exception e) {
            // klient się rozłączył itp. - normalne
        }
    }

    private void sendSnapshot(OutputStream out, boolean download) throws IOException, InterruptedException {
        FrameStore.Snap f = store.current();
        if (f == null) f = store.awaitNewer(0, 2000);
        if (f == null) {
            sendText(out, 503, "text/plain", "Brak obrazu - czy przechwytywanie jest uruchomione?");
            return;
        }
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: image/jpeg\r\n")
                .append("Content-Length: ").append(f.jpeg.length).append("\r\n")
                .append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
                .append("Access-Control-Allow-Origin: *\r\n");
        if (download) {
            h.append("Content-Disposition: attachment; filename=\"tv-")
                    .append(System.currentTimeMillis()).append(".jpg\"\r\n");
        }
        h.append("Connection: close\r\n\r\n");
        out.write(h.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(f.jpeg);
        out.flush();
    }

    private void sendStream(OutputStream out) throws IOException, InterruptedException {
        synchronized (this) {
            if (activeStreams >= MAX_STREAMS) {
                sendText(out, 503, "text/plain", "Za dużo jednoczesnych podglądów");
                return;
            }
            activeStreams++;
        }
        try {
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: multipart/x-mixed-replace; boundary=frame\r\n"
                    + "Cache-Control: no-cache, no-store, must-revalidate\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            long lastSeq = -1;
            while (!stopped && CaptureService.running) {
                // czekamy na nową klatkę; co 3 s wysyłamy tę samą (podtrzymanie połączenia)
                FrameStore.Snap f = store.awaitNewer(lastSeq, 3000);
                if (f == null) continue;
                lastSeq = f.seq;
                out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: "
                        + f.jpeg.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(f.jpeg);
                out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
        } finally {
            synchronized (this) {
                activeStreams--;
            }
        }
    }

    private String statusJson() {
        return "{\"running\":" + CaptureService.running
                + ",\"width\":" + store.width()
                + ",\"height\":" + store.height()
                + ",\"frame\":" + store.seq()
                + ",\"frameAgeMs\":" + store.frameAgeMs()
                + ",\"fps\":" + Config.FPS + "}";
    }

    private static void sendText(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : "Service Unavailable";
        String h = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    static String param(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            String k = eq >= 0 ? kv.substring(0, eq) : kv;
            if (k.equals(name)) {
                try {
                    return eq >= 0 ? URLDecoder.decode(kv.substring(eq + 1), "UTF-8") : "";
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Czyta linię zakończoną \r\n (ASCII). Max 8 KB. */
    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("Za długa linia");
        }
        if (c == -1 && sb.length() == 0) return null;
        return sb.toString();
    }
}
