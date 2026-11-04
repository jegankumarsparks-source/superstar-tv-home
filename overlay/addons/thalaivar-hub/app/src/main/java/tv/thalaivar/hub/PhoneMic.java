package tv.thalaivar.hub;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * The phone remote, paired by scanning a QR code on the TV. The QR holds a 128-bit secret key in the
 * address, so nothing is typed and nothing can be guessed. From the phone: talk to J.A.R.V.I.S with the
 * keyboard's microphone, every remote button (power, arrows, OK, Back, Home, volume, media keys), every app
 * on the TV and the dashboard's own screens. Home network only; the server answers only at the TV's own
 * address and only its own page.
 */
final class PhoneMic {
    static final int PORT = 8787;
    private static ServerSocket server;

    /** The pairing key, made once and kept; "new pairing" in Settings replaces it. */
    static String key(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("thalaivar", Context.MODE_PRIVATE);
        String k = p.getString("remote_key", "");
        if (k.length() < 32) {
            byte[] b = new byte[16];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format(Locale.ROOT, "%02x", x));
            k = sb.toString();
            p.edit().putString("remote_key", k).apply();
        }
        return k;
    }

    static void newKey(Context c) {
        c.getSharedPreferences("thalaivar", Context.MODE_PRIVATE).edit().remove("remote_key").apply();
        key(c);
    }

    /** "http://192.168.1.50:8787", or null without a network. */
    static String address(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) return null;
        LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
        if (lp == null) return null;
        for (LinkAddress a : lp.getLinkAddresses()) {
            InetAddress ip = a.getAddress();
            if (ip instanceof Inet4Address && !ip.isLoopbackAddress()) return "http://" + ip.getHostAddress() + ":" + PORT;
        }
        return null;
    }

    /** What the QR code holds: the page address with the pairing key. */
    static String pairingUrl(Context c) {
        String a = address(c);
        return a == null ? null : a + "/j/" + key(c);
    }

    /** Started by the helper service and by the dashboard, so the phone can connect whenever the TV is on. */
    static synchronized void start(Context context) {
        if (server != null && !server.isClosed()) return;
        Context c = context.getApplicationContext();
        try {
            server = new ServerSocket(PORT);
        } catch (Exception e) {
            return;
        }
        Thread t = new Thread(() -> {
            while (server != null && !server.isClosed()) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(5000);
                    serve(c, s);
                } catch (Exception ignored) {
                    // one bad request never stops the server
                }
            }
        }, "jarvis-remote");
        t.setDaemon(true);
        t.start();
    }

    private static boolean keyMatches(Context c, String given) {
        return given != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), key(c).getBytes(StandardCharsets.UTF_8));
    }

    private static void serve(Context c, Socket s) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        String line = in.readLine();
        if (line == null || line.length() > 2048) return;
        String host = "", marker = "";
        for (int i = 0; i < 40; i++) {
            String h = in.readLine();
            if (h == null || h.isEmpty()) break;
            int colon = h.indexOf(':');
            if (colon <= 0) continue;
            String name = h.substring(0, colon).trim().toLowerCase(Locale.ROOT), value = h.substring(colon + 1).trim();
            if ("host".equals(name)) host = value;
            if ("x-jarvis".equals(name)) marker = value;
        }
        String me = address(c);
        if (me == null || !("http://" + host).equals(me)) {
            // reached through another name (DNS rebinding) or an unexpected address
            reply(s, "421 Misdirected Request", "text/plain", "scan the QR code on the TV");
            return;
        }
        String[] parts = line.split(" ");
        if (parts.length < 2 || !"GET".equals(parts[0])) {
            reply(s, "405 Method Not Allowed", "text/plain", "GET only");
            return;
        }
        Uri u = Uri.parse("http://tv" + parts[1]);
        java.util.List<String> seg = u.getPathSegments();
        // every path starts /j/<key>: anything else is just "scan the QR code"
        if (seg.size() < 2 || !"j".equals(seg.get(0)) || !keyMatches(c, seg.get(1))) {
            reply(s, "404 Not Found", "text/plain", "Scan the QR code on the TV's J.A.R.V.I.S panel.");
            return;
        }
        if (seg.size() == 2) {
            reply(s, "200 OK", "text/html; charset=utf-8", RemotePage.html(c));
            return;
        }
        // "Add to Home screen": the remote opens like an app, at its own paired address
        // app icons for the Apps grid: plain images (an <img> cannot send the command header), nothing secret
        if ("icon".equals(seg.get(2))) {
            byte[] png = TvControl.icon(c, u.getQueryParameter("p"));
            if (png == null) reply(s, "404 Not Found", "text/plain", "no icon");
            else reply(s, "200 OK", "image/png", png);
            return;
        }
        if ("manifest.json".equals(seg.get(2))) {
            reply(s, "200 OK", "application/manifest+json", "{\"name\":\"THALAIVAR TV Remote\",\"short_name\":\"Thalaivar\","
                    + "\"start_url\":\"/j/" + seg.get(1) + "\",\"scope\":\"/j/" + seg.get(1) + "\",\"display\":\"standalone\","
                    + "\"background_color\":\"#06080f\",\"theme_color\":\"#06080f\"}");
            return;
        }
        // commands come only from the page's own script (other web pages cannot add this header)
        if (!"1".equals(marker)) {
            reply(s, "403 Forbidden", "application/json", "{\"ok\":false}");
            return;
        }
        String cmd = seg.get(2);
        boolean ok;
        switch (cmd) {
            case "ping":
                // the phone remote is open: have J.A.R.V.I.S's voice connection ready before the first question
                LiveJarvis.warm(c);
                // the phone's "Connected" light, volume, power, now playing and J.A.R.V.I.S's last words
                reply(s, "200 OK", "application/json", TvControl.status(c));
                return;
            case "apps":
                reply(s, "200 OK", "application/json", TvControl.apps(c));
                return;
            case "go":
                ok = TvControl.go(c, u.getQueryParameter("s"), u.getQueryParameter("q"));
                break;
            case "vol":
                ok = TvControl.setVolume(c, u.getQueryParameter("v"));
                break;
            case "ask": {
                String q = u.getQueryParameter("q");
                ok = q != null && !q.trim().isEmpty() && q.length() <= 300;
                if (ok) c.startActivity(new Intent(c, JarvisActivity.class).putExtra("q", q.trim())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                break;
            }
            case "key":
                ok = TvControl.key(c, u.getQueryParameter("k"));
                break;
            case "app":
                ok = TvControl.app(c, u.getQueryParameter("p"));
                break;
            default:
                ok = false;
        }
        reply(s, ok ? "200 OK" : "400 Bad Request", "application/json", "{\"ok\":" + ok + "}");
    }

    private static void reply(Socket s, String status, String type, String body) throws Exception {
        reply(s, status, type, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void reply(Socket s, String status, String type, byte[] b) throws Exception {
        OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Frame-Options: DENY\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.write(b);
        out.flush();
    }

    private PhoneMic() {
    }
}
