package tv.thalaivar.hub;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** One film: backdrop, 3D poster, the facts from TMDB, official videos, stills, and where it streams in India. */
public class MovieActivity extends Activity {
    /** Streaming services TMDB names in India, and their Android TV apps. */
    static final Map<String, String[]> APPS = new LinkedHashMap<>();

    static {
        APPS.put("Netflix", new String[]{"com.netflix.ninja"});
        APPS.put("Amazon Prime Video", new String[]{"com.amazon.amazonvideo.livingroom"});
        APPS.put("JioHotstar", new String[]{"in.startv.hotstar"});
        APPS.put("Sun Nxt", new String[]{"com.suntv.sunnxt"});
        APPS.put("Zee5", new String[]{"com.graymatrix.did"});
        APPS.put("Sony Liv", new String[]{"com.sonyliv"});
        APPS.put("aha", new String[]{"ahaflix.tv"});
        APPS.put("Apple TV", new String[]{"com.apple.atve.androidtv.appletv"});
        APPS.put("YouTube", new String[]{Ui.YT_TV});
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private long id;
    private LinearLayout col, buttons, rows;
    private ImageView backdrop, poster;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        id = getIntent().getLongExtra("id", -1);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.INK);
        backdrop = Ui.image(this);
        root.addView(backdrop, Ui.match());
        View scrim = new View(this);
        scrim.setBackground(Ui.gradient(0xF50A0604, 0x660A0604, GradientDrawable.Orientation.LEFT_RIGHT));
        root.addView(scrim, Ui.match());
        root.addView(new ParticleView(this, Ui.GOLD_HI), Ui.match());
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(Ui.dp(this, 56), Ui.dp(this, 48), Ui.dp(this, 56), 0);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        top.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        poster = Ui.image(this);
        poster.setCameraDistance(Ui.dp(this, 2400));
        poster.setRotationY(prefs.motion() > Prefs.LOW ? -16f : 0f);
        poster.setElevation(Ui.dp(this, 20));
        poster.setClipToOutline(true);
        poster.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 12), 0, 0));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(this, 250), Ui.dp(this, 375));
        plp.leftMargin = Ui.dp(this, 40);
        top.addView(poster, plp);
        page.addView(top);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        page.addView(rows);
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
        col.addView(Ui.gold(this, Lang.t("loading"), 48));
        load();
    }

    private void load() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject m = Tmdb.movie(this, id);
                JSONObject imgs = null;
                try {
                    imgs = Tmdb.images(this, id);
                } catch (Exception ignored) {
                    // stills are optional
                }
                JSONObject images = imgs;
                main.post(() -> show(m, images));
            } catch (Exception e) {
                main.post(() -> {
                    col.removeAllViews();
                    col.addView(Ui.gold(this, Lang.t("offline"), 36));
                });
            }
        });
    }

    private void show(JSONObject m, JSONObject images) {
        col.removeAllViews();
        prefs.addRecentMovie(id);
        String title = m.optString("title");
        String tamil = getIntent().getStringExtra("tamil");
        TextView t = Ui.gold(this, Lang.tamil && tamil != null ? tamil : title, 56);
        col.addView(t);
        if (Lang.tamil && tamil != null) col.addView(Ui.label(this, title.toUpperCase(), 22, Ui.DIM));
        StringBuilder meta = new StringBuilder(m.optString("release_date", "").length() >= 4 ? m.optString("release_date").substring(0, 4) : "");
        int rt = m.optInt("runtime", 0);
        if (rt > 0) meta.append("  ·  ").append(rt / 60).append("h ").append(rt % 60).append("m");
        JSONArray g = m.optJSONArray("genres");
        if (g != null) for (int i = 0; i < Math.min(3, g.length()); i++) meta.append(i == 0 ? "  ·  " : " / ").append(g.optJSONObject(i).optString("name"));
        if (m.optDouble("vote_average", 0) > 0) meta.append(String.format("  ·  ★ %.1f", m.optDouble("vote_average")));
        col.addView(Ui.label(this, meta.toString(), 20, Ui.GOLD));
        fact(Lang.t("released"), m.optString("release_date"));
        fact(Lang.t("director"), Tmdb.director(m));
        fact(Lang.t("music_by"), Tmdb.music(m));
        fact(Lang.t("cast"), Tmdb.cast(m, 8));
        TextView ov = Ui.text(this, m.optString("overview"), 17, Ui.TEXT, false);
        ov.setMaxLines(4);
        ov.setEllipsize(TextUtils.TruncateAt.END);
        ov.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 16));
        col.addView(ov);

        buttons = Ui.buttonRow(this, col);
        String trailer = bestVideo(m, "Trailer");
        View firstButton = null;
        if (trailer != null) {
            firstButton = Ui.button(this, "▶  " + Lang.t("trailer"), v -> Ui.youtube(this, trailer));
            buttons.addView(firstButton);
        }
        View songs = Ui.button(this, "♪  " + Lang.t("watch_songs"), v -> Ui.youtubeSearch(this, title + " " + yearOf(m) + " songs official jukebox"));
        buttons.addView(songs);
        if (firstButton == null) firstButton = songs;
        addWatch(m);
        TextView fav = Ui.button(this, favLabel(), null);
        fav.setOnClickListener(v -> {
            prefs.toggleFavMovie(id);
            fav.setText(favLabel());
        });
        buttons.addView(fav);
        String bd = m.optString("backdrop_path", null);
        if (bd != null && !"null".equals(bd)) {
            TextView wall = Ui.button(this, "✦  " + Lang.t("set_wall"), null);
            wall.setOnClickListener(v -> Ui.toast(this, (prefs.toggleFavWall(bd) ? "★ " : "☆ ") + Lang.t("set_wall")));
            buttons.addView(wall);
        }

        int w = getResources().getDisplayMetrics().widthPixels;
        Img.into(backdrop, Tmdb.img(bd, "w1280"), w);
        Img.into(poster, Tmdb.img(m.optString("poster_path", null), "w780"), Ui.dp(this, 250));
        if (prefs.motion() > Prefs.LOW) {
            poster.setTranslationX(Ui.dp(this, 80));
            poster.animate().translationX(0).rotationY(-8f).setDuration(900).start();
        }

        // official videos: every trailer, teaser and song clip TMDB lists for the film, on YouTube
        JSONObject vids = m.optJSONObject("videos");
        JSONArray vr = vids == null ? null : vids.optJSONArray("results");
        if (vr != null && vr.length() > 0) {
            LinearLayout strip = Ui.row(this, rows, Lang.both("videos"));
            for (int i = 0; i < vr.length(); i++) {
                JSONObject v = vr.optJSONObject(i);
                if (v == null || !"YouTube".equals(v.optString("site"))) continue;
                String key = v.optString("key");
                FrameLayout card = Ui.card(this, Ui.dp(this, 320), Ui.dp(this, 180), false);
                ImageView th = Ui.image(this);
                Ui.underRim(card, th, new FrameLayout.LayoutParams(-1, -1));
                Img.into(th, "https://i.ytimg.com/vi/" + key + "/hqdefault.jpg", Ui.dp(this, 320));
                View shade = new View(this);
                shade.setBackground(Ui.gradient(0x00000000, 0xE0000000, GradientDrawable.Orientation.TOP_BOTTOM));
                Ui.underRim(card, shade, new FrameLayout.LayoutParams(-1, -1));
                TextView cap = Ui.text(this, "▶  " + v.optString("name"), 15, Ui.TEXT, false);
                cap.setMaxLines(2);
                cap.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 10));
                Ui.underRim(card, cap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                card.setOnClickListener(x -> Ui.youtube(this, key));
                Ui.add(strip, card);
            }
        }
        JSONArray bds = images == null ? null : images.optJSONArray("backdrops");
        if (bds != null && bds.length() > 0) {
            LinearLayout strip = Ui.row(this, rows, Lang.both("stills") + "  —  " + Lang.t("favourite_hint"));
            for (int i = 0; i < Math.min(16, bds.length()); i++) {
                String path = bds.optJSONObject(i).optString("file_path");
                FrameLayout card = WallpaperActivity.wallCard(this, prefs, path);
                Ui.add(strip, card);
            }
        }
        if (firstButton != null) firstButton.requestFocus();
    }

    private String favLabel() {
        return prefs.isFavMovie(id) ? "★  " + Lang.t("unfavourite") : "☆  " + Lang.t("favourite");
    }

    private void fact(String k, String v) {
        if (v == null || v.isEmpty()) return;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        TextView key = Ui.label(this, k, 18, Ui.GOLD);
        key.setMinWidth(Ui.dp(this, 120));
        l.addView(key);
        TextView val = Ui.text(this, v, 17, Ui.TEXT, false);
        val.setMaxLines(2);
        val.setEllipsize(TextUtils.TruncateAt.END);
        l.addView(val, new LinearLayout.LayoutParams(0, -2, 1));
        l.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(l);
    }

    /** "Watch on" for every service TMDB lists in India whose app is on this TV; the rest are named. */
    private void addWatch(JSONObject m) {
        JSONObject wp = m.optJSONObject("watch/providers");
        JSONObject res = wp == null ? null : wp.optJSONObject("results");
        JSONObject in = res == null ? null : res.optJSONObject(BuildConfig.REGION);
        JSONArray flat = in == null ? null : in.optJSONArray("flatrate");
        if (flat == null || flat.length() == 0) {
            // on its own line under the buttons, so it never squeezes them
            TextView none = Ui.label(this, Lang.t("not_streaming"), 15, Ui.DIM);
            none.setPadding(0, Ui.dp(this, 10), 0, 0);
            col.addView(none);
            return;
        }
        for (int i = 0; i < flat.length(); i++) {
            String name = flat.optJSONObject(i).optString("provider_name");
            String pkg = null;
            for (Map.Entry<String, String[]> e : APPS.entrySet()) {
                if (name.toLowerCase().startsWith(e.getKey().toLowerCase())) {
                    for (String p : e.getValue()) if (installed(p)) pkg = p;
                }
            }
            if (pkg == null) continue;
            String launch = pkg;
            buttons.addView(Ui.button(this, "▶  " + Lang.t("watch_on") + " " + name, v -> Ui.launchApp(this, launch)));
        }
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String bestVideo(JSONObject m, String type) {
        JSONObject vids = m.optJSONObject("videos");
        JSONArray r = vids == null ? null : vids.optJSONArray("results");
        if (r == null) return null;
        String any = null;
        for (int i = 0; i < r.length(); i++) {
            JSONObject v = r.optJSONObject(i);
            if (v == null || !"YouTube".equals(v.optString("site"))) continue;
            if (type.equals(v.optString("type")) && v.optBoolean("official", true)) return v.optString("key");
            if (any == null) any = v.optString("key");
        }
        return any;
    }

    static String yearOf(JSONObject m) {
        String d = m.optString("release_date", "");
        return d.length() >= 4 ? d.substring(0, 4) : "";
    }
}
