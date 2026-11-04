package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The screensaver picture: starred stills first, then every film's backdrop, then the bundled original art
 * when offline. Crossfades on the owner's interval with a slow zoom, never the same picture twice in a row,
 * under a Chennai clock and date. The Baasha variant keeps to Baasha's stills in black and gold.
 */
class SaverView extends FrameLayout {
    private final boolean baasha;
    private final Prefs prefs;
    private final ImageView a, b;
    private final TextView clock, date, caption;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread worker = new HandlerThread("saver");
    private Handler bg;
    private final List<String[]> pictures = new ArrayList<>();
    private String last = "";
    private int next;

    SaverView(Context c, boolean baasha) {
        super(c);
        this.baasha = baasha;
        prefs = new Prefs(c);
        setBackgroundColor(0xFF000000);
        a = Ui.image(c);
        b = Ui.image(c);
        b.setAlpha(0f);
        addView(a, Ui.match());
        addView(b, Ui.match());
        View shade = new View(c);
        shade.setBackground(Ui.gradient(0x00000000, 0xDD000000, GradientDrawable.Orientation.TOP_BOTTOM));
        addView(shade, Ui.match());
        if (baasha) {
            View gold = new View(c);
            gold.setBackground(Ui.gradient(0x55000000, 0x553A2A06, GradientDrawable.Orientation.TL_BR));
            addView(gold, Ui.match());
            addView(new CelebrationView(c), Ui.match());
        }
        addView(new ParticleView(c, Ui.GOLD_HI), Ui.match());
        LinearLayout info = new LinearLayout(c);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(c, 64), 0, Ui.dp(c, 64), Ui.dp(c, 48));
        if (baasha) info.addView(Ui.gold(c, "BAASHA", 64));
        clock = Ui.gold(c, "", 84);
        date = Ui.label(c, "", 22, Ui.TEXT);
        caption = Ui.label(c, "", 18, Ui.GOLD);
        if (prefs.saverClock()) {
            info.addView(clock);
            info.addView(date);
        }
        info.addView(caption);
        addView(info, new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.START));
        TextView brand = Ui.label(c, "THALAIVAR TV", 20, 0xAAD4AF37);
        LayoutParams blp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        blp.setMargins(0, Ui.dp(c, 36), Ui.dp(c, 48), 0);
        addView(brand, blp);
    }

    void start() {
        worker.start();
        bg = new Handler(worker.getLooper());
        tick.run();
        bg.post(this::collect);
    }

    void stop() {
        main.removeCallbacksAndMessages(null);
        if (bg != null) bg.removeCallbacksAndMessages(null);
        worker.quitSafely();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            SimpleDateFormat t = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            t.setTimeZone(HubActivity.CHENNAI);
            SimpleDateFormat d = new SimpleDateFormat("EEEE, d MMMM", Lang.tamil ? new Locale("ta", "IN") : Locale.ENGLISH);
            d.setTimeZone(HubActivity.CHENNAI);
            clock.setText(t.format(now).toUpperCase(Locale.ENGLISH));
            date.setText(d.format(now) + "  ·  " + Lang.t("chennai"));
            main.postDelayed(this, 20000);
        }
    };

    /** Builds the playlist: {url or asset:, caption}. */
    private void collect() {
        List<String[]> starred = new ArrayList<>(), films = new ArrayList<>(), portraits = new ArrayList<>();
        String source = baasha ? "stills" : prefs.saverSource();
        if (!baasha) for (String p : prefs.favWalls()) starred.add(new String[]{Tmdb.img(p, "w1280"), "★"});
        try {
            if (source.equals("all") || source.equals("portraits")) {
                JSONArray pr = Tmdb.portraits(getContext());
                if (pr != null) for (int i = 0; i < pr.length(); i++) {
                    portraits.add(new String[]{Tmdb.img(pr.optJSONObject(i).optString("file_path"), "h632"), "Superstar Rajinikanth"});
                }
            }
            for (Films.Film f : Films.resolve(getContext(), Films.curated())) {
                if (!(source.equals("all") || source.equals("stills"))) break;
                if (f.id < 0 || (baasha && !"baasha".equals(f.stem))) continue;
                JSONArray bds = Tmdb.images(getContext(), f.id).optJSONArray("backdrops");
                int max = baasha ? 30 : 4;
                if (bds != null) for (int i = 0; i < Math.min(max, bds.length()); i++) {
                    films.add(new String[]{Tmdb.img(bds.optJSONObject(i).optString("file_path"), "w1280"), f.name() + "  " + f.year});
                }
            }
        } catch (Exception ignored) {
            // offline: the bundled art below
        }
        Collections.shuffle(starred);
        List<String[]> rest = new ArrayList<>(films);
        rest.addAll(portraits);
        Collections.shuffle(rest);
        pictures.addAll(starred);
        if (!"starred".equals(source) || starred.isEmpty()) pictures.addAll(rest);
        if (pictures.isEmpty()) {
            String[] art = baasha ? new String[]{"02-baasha-night"} : new String[]{"01-emperor-gold", "02-baasha-night", "08-midnight-star", "09-jailer2-steel"};
            for (String s : art) pictures.add(new String[]{"asset:art/" + s + ".jpg", "THALAIVAR TV"});
        }
        advance();
    }

    private void advance() {
        if (pictures.isEmpty()) return;
        String[] pick = pictures.get(next++ % pictures.size());
        if (pick[0].equals(last) && pictures.size() > 1) pick = pictures.get(next++ % pictures.size());
        last = pick[0];
        int w = getResources().getDisplayMetrics().widthPixels;
        Bitmap bmp = null;
        if (pick[0].startsWith("asset:")) {
            try (InputStream in = getContext().getAssets().open(pick[0].substring(6))) {
                bmp = BitmapFactory.decodeStream(in);
            } catch (Exception ignored) {
                // skip
            }
        } else {
            bmp = Img.now(getContext(), pick[0], w);
        }
        Bitmap shown = bmp;
        String cap = pick[1];
        main.post(() -> show(shown, cap));
        long every = baasha ? 20000 : Math.max(20000, prefs.rotateMinutes() * 60000L);
        bg.postDelayed(this::advance, every);
    }

    private void show(Bitmap bmp, String cap) {
        if (bmp == null) return;
        ImageView in = a.getAlpha() > 0.5f ? b : a;
        ImageView out = in == a ? b : a;
        in.setImageBitmap(bmp);
        int level = prefs.motion();
        in.setScaleX(1f);
        in.setScaleY(1f);
        in.animate().alpha(1f).setDuration(level == Prefs.LOW ? 300 : 1800).start();
        if (level > Prefs.LOW) in.animate().scaleX(1.08f).scaleY(1.08f).setDuration(20000).start();
        out.animate().alpha(0f).setDuration(level == Prefs.LOW ? 300 : 1800).start();
        caption.setText(cap);
    }
}
