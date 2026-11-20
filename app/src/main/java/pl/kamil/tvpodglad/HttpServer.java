package pl.kamil.tvpodglad;

import android.content.Context;
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
 * Mały serwer HTTP do sterowania okienkiem (np. z Home Assistanta):
 *   /pip/toggle|show|hide|next|prev|status
 *   /tv/inputs   diagnostyka: wejścia TV i kanały
 *   /            prosta strona z przyciskami
 */
final class HttpServer extends Thread {
    private final Context ctx;
    private final int port;
    private volatile boolean stopped;
    private volatile ServerSocket serverSocket;

    HttpServer(Context ctx, int port) {
        super("http-server");
        this.ctx = ctx.getApplicationContext();
        this.port = port;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));
            serverSocket = ss;
            Log.i(Config.TAG, "Serwer HTTP na porcie " + port);
            while (!stopped) {
                Socket s = ss.accept();
                Thread t = new Thread(() -> handle(s), "http-client");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (!stopped) Log.e(Config.TAG, "Serwer HTTP padł", e);
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
            InputStream in = socket.getInputStream();
            String requestLine = readLine(in);
            if (requestLine == null) return;
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // nagłówki pomijamy
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String path = parts[1];
            String query = "";
            int q = path.indexOf('?');
            if (q >= 0) {
                query = path.substring(q + 1);
                path = path.substring(0, q);
            }
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            if (!Config.ACCESS_KEY.isEmpty() && !Config.ACCESS_KEY.equals(param(query, "key"))) {
                send(out, 403, "text/plain", "Brak dostępu - dopisz ?key=...");
                return;
            }

            if (path.startsWith("/pip/")) {
                String r = KeyService.command(path.substring(5));
                if (r == null) send(out, 503, "application/json", "{\"error\":\"usluga przycisku wylaczona\"}");
                else send(out, 200, "application/json; charset=utf-8", r);
            } else if (path.equals("/tv/inputs")) {
                send(out, 200, "application/json; charset=utf-8", TvInputs.json(ctx));
            } else if (path.equals("/")) {
                send(out, 200, "text/html; charset=utf-8", page(query));
            } else {
                send(out, 404, "text/plain", "404");
            }
        } catch (Exception ignored) {
            // klient się rozłączył itp.
        }
    }

    private static String page(String query) {
        String key = param(query, "key");
        String k = (key == null || key.isEmpty()) ? "" : "?key=" + key.replaceAll("[^A-Za-z0-9_-]", "");
        return "<!DOCTYPE html><html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>TV – okienko</title>"
                + "<style>body{margin:0;padding:16px;background:#0b1520;color:#e6eef6;font-family:system-ui,sans-serif}"
                + "button{display:block;width:100%;margin:8px 0;padding:16px;border:0;border-radius:8px;"
                + "background:#2e86de;color:#fff;font-size:17px}#s{font-size:13px;color:#8fa6bb;white-space:pre-wrap}</style>"
                + "</head><body><h2>Okienko TV naziemnej</h2>"
                + "<button onclick=\"c('toggle')\">Pokaż / ukryj</button>"
                + "<button onclick=\"c('next')\">Kanał +</button>"
                + "<button onclick=\"c('prev')\">Kanał −</button>"
                + "<div id=\"s\"></div><script>var K='" + k + "';"
                + "function c(x){fetch('/pip/'+x+K).then(function(r){return r.text()})"
                + ".then(function(t){document.getElementById('s').textContent=t})}c('status');</script></body></html>";
    }

    private static void send(OutputStream out, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String reason = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : "Service Unavailable";
        String h = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
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
