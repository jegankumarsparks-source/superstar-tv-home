package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads TMDB artwork off the main thread: a small memory cache, a disk cache, and decoding at the size the
 * card needs (RGB_565 on low-memory TVs), so scrolling a row of posters stays smooth.
 */
final class Img {
    interface Done {
        void loaded(Bitmap b);
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static LruCache<String, Bitmap> mem;

    private static synchronized LruCache<String, Bitmap> mem() {
        if (mem == null) {
            int kb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 6);
            mem = new LruCache<String, Bitmap>(kb) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
        }
        return mem;
    }

    static void into(ImageView v, String url, int targetW) {
        if (url == null) return;
        v.setTag(url);
        load(v.getContext(), url, targetW, b -> {
            if (b != null && url.equals(v.getTag())) {
                v.setImageBitmap(b);
                v.setAlpha(0f);
                v.animate().alpha(1f).setDuration(220).start();
            }
        });
    }

    static void load(Context c, String url, int targetW, Done done) {
        if (url == null) {
            done.loaded(null);
            return;
        }
        String key = url + "@" + targetW;
        Bitmap hit = mem().get(key);
        if (hit != null) {
            done.loaded(hit);
            return;
        }
        Context app = c.getApplicationContext();
        boolean low = new Prefs(app).motion() == Prefs.LOW;
        POOL.execute(() -> {
            Bitmap b = null;
            try {
                File f = new File(new File(app.getCacheDir(), "img"), Net.hash(url));
                if (!f.exists()) Net.write(f, Net.get(url, null));
                b = decode(f.getPath(), targetW, low);
                if (b != null) mem().put(key, b);
            } catch (Exception ignored) {
                // a missing picture leaves the card's colour and title in place
            }
            Bitmap result = b;
            MAIN.post(() -> done.loaded(result));
        });
    }

    /** Blocking load for screensavers that run their own timing thread. */
    static Bitmap now(Context c, String url, int targetW) {
        try {
            File f = new File(new File(c.getCacheDir(), "img"), Net.hash(url));
            if (!f.exists()) Net.write(f, Net.get(url, null));
            return decode(f.getPath(), targetW, new Prefs(c).motion() == Prefs.LOW);
        } catch (Exception e) {
            return null;
        }
    }

    static Bitmap decode(String path, int targetW, boolean low) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int s = 1;
        while (o.outWidth / (s * 2) >= targetW) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        o.inPreferredConfig = low ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path, o);
    }

    private Img() {
    }
}
