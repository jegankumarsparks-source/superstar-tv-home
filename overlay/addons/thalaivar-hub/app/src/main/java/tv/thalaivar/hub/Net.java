package tv.thalaivar.hub;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Plain HTTPS with retries and a disk cache. Some Indian networks reset about half of the connections to
 * TMDB, so every request is tried a few times before it counts as failed, and a stale copy is kept to fall
 * back on.
 */
final class Net {
    static final class Result {
        final String body;
        final long savedAt;
        final boolean fresh;

        Result(String body, long savedAt, boolean fresh) {
            this.body = body;
            this.savedAt = savedAt;
            this.fresh = fresh;
        }
    }

    static boolean online(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null || cm.getActiveNetwork() == null) return false;
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    static byte[] get(String url, Map<String, String> headers) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(20000);
                con.setRequestProperty("User-Agent", "ThalaivarTV/1.0 (Android TV)");
                if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) con.setRequestProperty(h.getKey(), h.getValue());
                int code = con.getResponseCode();
                if (code == 401 || code == 404) throw new IOException("HTTP " + code);
                if (code != 200) throw new IOException("HTTP " + code);
                try (InputStream in = con.getInputStream()) {
                    return readAll(in);
                }
            } catch (IOException e) {
                last = e;
                if (e.getMessage() != null && (e.getMessage().equals("HTTP 401") || e.getMessage().equals("HTTP 404"))) throw e;
                try {
                    Thread.sleep(700L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            } finally {
                if (con != null) con.disconnect();
            }
        }
        throw last != null ? last : new IOException("request failed");
    }

    /** POST a JSON body, retried like get(); for the assistant, which is never cached. */
    static String postJson(String url, Map<String, String> headers, String json) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(12000);
                con.setReadTimeout(30000);
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) con.setRequestProperty(h.getKey(), h.getValue());
                con.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
                int code = con.getResponseCode();
                InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
                String body = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
                if (code >= 400 && code < 500) throw new IOException("HTTP " + code + ": " + body);
                if (code != 200) throw new IOException("HTTP " + code);
                return body;
            } catch (IOException e) {
                last = e;
                if (e.getMessage() != null && e.getMessage().startsWith("HTTP 4")) throw e;
                try {
                    Thread.sleep(800L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            } finally {
                if (con != null) con.disconnect();
            }
        }
        throw last != null ? last : new IOException("request failed");
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    /** The cached body if younger than maxAgeMs, otherwise a fresh fetch, otherwise the stale copy. */
    static Result cached(Context c, String key, long maxAgeMs, String url, Map<String, String> headers) throws IOException {
        File f = new File(new File(c.getCacheDir(), "api"), hash(key));
        long now = System.currentTimeMillis();
        if (f.exists() && now - f.lastModified() < maxAgeMs) {
            return new Result(read(f), f.lastModified(), true);
        }
        try {
            byte[] body = get(url, headers);
            write(f, body);
            return new Result(new String(body, StandardCharsets.UTF_8), now, true);
        } catch (IOException e) {
            if (f.exists()) return new Result(read(f), f.lastModified(), false);
            throw e;
        }
    }

    static String read(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        }
    }

    static void write(File f, byte[] data) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) throw new IOException("no cache dir");
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        }
        if (!tmp.renameTo(f)) throw new IOException("cache write failed");
    }

    static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    static void clear(Context c) {
        deleteTree(c.getCacheDir());
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) {
            if (k.isDirectory()) deleteTree(k);
            //noinspection ResultOfMethodCallIgnored
            k.delete();
        }
    }

    private Net() {
    }
}
