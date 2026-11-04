package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Intent;
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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

/** The two celebration zones: BAASHA – THE EVERGREEN EMPEROR, and the JAILER 2 hub. */
public class SpecialActivity extends Activity {
    /** Original fan-wall lines written for this app (no film dialogue). */
    private static final String[][] FAN_WALL = {
            {"முதல் நாள் முதல் காட்சி – திரையரங்கமே திருவிழா!", "First day, first show – the theatre becomes a festival!"},
            {"சென்னை முதல் உலகம் வரை – ஒரே பெயர், தலைவர்!", "From Chennai to the world – one name, Thalaivar!"},
            {"பால் அபிஷேகம், பட்டாசு, ஆட்டம் – ரசிகர்களின் கொண்டாட்டம்!", "Milk, crackers and dancing – the fans' celebration!"},
            {"ஸ்டைலுக்கு ஒரு பெயர் இருந்தால் அது சூப்பர்ஸ்டார்!", "If style has a name, it is Superstar!"},
            {"தலைமுறைகள் மாறினாலும் தலைவர் மாறவில்லை!", "Generations change – Thalaivar stays!"},
            {"எங்கள் இதயத்தில் என்றும் சூப்பர்ஸ்டார்!", "Forever the Superstar in our hearts!"},
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean baasha;
    private LinearLayout col, rows, buttons;
    private ImageView backdrop;
    private TextView wall;
    private int wallIndex;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        baasha = "baasha".equals(getIntent().getStringExtra("zone"));
        Prefs prefs = new Prefs(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(baasha ? 0xFF000000 : 0xFF050E10);
        backdrop = Ui.image(this);
        backdrop.setAlpha(0.55f);
        root.addView(backdrop, Ui.match());
        try (InputStream in = getAssets().open(baasha ? "art/02-baasha-night.jpg" : "art/09-jailer2-steel.jpg")) {
            backdrop.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(in, null));
        } catch (Exception ignored) {
            // plain background
        }
        View scrim = new View(this);
        scrim.setBackground(Ui.gradient(0xEE000000, 0x55000000, GradientDrawable.Orientation.LEFT_RIGHT));
        root.addView(scrim, Ui.match());
        root.addView(new CelebrationView(this), Ui.match());
        root.addView(new ParticleView(this, baasha ? Ui.GOLD_HI : 0xFFFFA040), Ui.match());

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 56), Ui.dp(this, 44), Ui.dp(this, 56), 0);
        page.addView(col);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        page.addView(rows);
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);

        TextView head = Ui.gold(this, baasha ? "BAASHA" : "JAILER 2", 96);
        col.addView(head);
        col.addView(Ui.label(this, baasha ? Lang.both("baasha_sub") : Lang.both("jailer2_sub"), 26, Ui.TEXT));
        if (prefs.motion() > Prefs.LOW) {
            head.setScaleX(1.25f);
            head.setScaleY(1.25f);
            head.setAlpha(0f);
            head.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(1100).start();
        }
        wall = Ui.text(this, "", 22, Ui.GOLD_HI, true);
        wall.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 10));
        col.addView(wall);
        buttons = Ui.buttonRow(this, col);
        if (baasha) {
            buttons.addView(Ui.button(this, "✦  " + Lang.t("baasha_saver"),
                    v -> startActivity(new Intent(this, SaverActivity.class).putExtra("baasha", true))));
        } else {
            boxOffice();
        }
        nextWallLine.run();
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(nextWallLine);
    }

    /** The fan wall: one celebration line at a time, fading through. */
    private final Runnable nextWallLine = new Runnable() {
        @Override
        public void run() {
            String[] l = FAN_WALL[wallIndex++ % FAN_WALL.length];
            wall.setText("“" + (Lang.tamil ? l[0] : l[1]) + "”");
            wall.setAlpha(0f);
            wall.animate().alpha(1f).setDuration(700).start();
            main.postDelayed(this, 6000);
        }
    };

    private void boxOffice() {
        try (InputStream in = getAssets().open("jailer2.json")) {
            JSONObject j = new JSONObject(new String(Net.readAll(in), StandardCharsets.UTF_8));
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            box.setBackground(Ui.round(0xCC101A1C, Ui.dp(this, 12), Ui.dp(this, 1), 0x88FFA040));
            box.addView(Ui.label(this, Lang.t("release_info") + ":  " + j.optString("release_date"), 18, Ui.GOLD));
            JSONArray bo = j.optJSONArray("box_office");
            if (bo != null) for (int i = 0; i < bo.length(); i++) {
                JSONObject o = bo.getJSONObject(i);
                box.addView(Ui.text(this, Lang.t("box_office") + " · " + o.optString("scope") + ":  " + o.optString("figure"), 24, Ui.TEXT, true));
                TextView badge = Ui.label(this, Lang.t("reported") + "  ·  " + Lang.t("trade_estimate"), 15, 0xFFFFB74D);
                box.addView(badge);
                box.addView(Ui.label(this, Lang.t("source") + ": " + o.optString("source") + "  ·  " + Lang.t("updated") + ": "
                        + o.optString("reported_on"), 15, Ui.DIM));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.bottomMargin = Ui.dp(this, 14);
            col.addView(box, col.indexOfChild((View) buttons.getParent()), lp);
        } catch (Exception ignored) {
            // the hub still works without the box-office card
        }
    }

    private void load() {
        Executors.newSingleThreadExecutor().execute(() -> {
            JSONObject movie = null;
            News.Feed feed = baasha ? null : News.load(this, false);
            try {
                List<Films.Film> l = Films.curated();
                Films.Film want = null;
                for (Films.Film f : l) if (f.stem.equals(baasha ? "baasha" : "jailer-2")) want = f;
                if (want != null) {
                    Films.resolve(this, java.util.Collections.singletonList(want));
                    if (want.id > 0) movie = Tmdb.movie(this, want.id);
                }
            } catch (Exception ignored) {
                // offline: the zone keeps its art, wall and box office
            }
            JSONObject m = movie;
            main.post(() -> show(m, feed));
        });
    }

    private void show(JSONObject m, News.Feed feed) {
        View first = buttons.getChildCount() > 0 ? buttons.getChildAt(0) : null;
        if (m != null) {
            long id = m.optLong("id");
            String trailer = MovieActivity.bestVideo(m, "Trailer");
            if (trailer != null) {
                View t = Ui.button(this, "▶  " + Lang.t("trailer"), v -> Ui.youtube(this, trailer));
                buttons.addView(t, 0);
                first = t;
            }
            String title = m.optString("title");
            buttons.addView(Ui.button(this, "♪  " + Lang.t("watch_songs"),
                    v -> Ui.youtubeSearch(this, title + " " + MovieActivity.yearOf(m) + " songs official")));
            buttons.addView(Ui.button(this, "ⓘ  " + Lang.t("details"),
                    v -> startActivity(new Intent(this, MovieActivity.class).putExtra("id", id)
                            .putExtra("tamil", baasha ? "பாட்ஷா" : "ஜெயிலர் 2"))));
            String bd = m.optString("backdrop_path", null);
            if (bd != null && !"null".equals(bd)) {
                Img.into(backdrop, Tmdb.img(bd, "w1280"), getResources().getDisplayMetrics().widthPixels);
                backdrop.animate().alpha(0.55f).setDuration(1).start();
            }
            TextView facts = Ui.text(this, Lang.t("director") + ": " + Tmdb.director(m) + "\n" + Lang.t("music_by") + ": "
                    + Tmdb.music(m) + "\n" + Lang.t("cast") + ": " + Tmdb.cast(m, 8), 17, Ui.TEXT, false);
            facts.setPadding(0, Ui.dp(this, 16), 0, 0);
            facts.setMaxWidth(Ui.dp(this, 900));
            col.addView(facts);
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
                    TextView cap = Ui.text(this, "▶  " + v.optString("name"), 15, Ui.TEXT, false);
                    cap.setMaxLines(2);
                    cap.setBackground(Ui.gradient(0x00000000, 0xE0000000, GradientDrawable.Orientation.TOP_BOTTOM));
                    cap.setPadding(Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 10));
                    Ui.underRim(card, cap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                    card.setOnClickListener(x -> Ui.youtube(this, key));
                    Ui.add(strip, card);
                }
            }
        }
        if (feed != null && !feed.items.isEmpty()) {
            LinearLayout strip = Ui.row(this, rows, Lang.both("news") + " — Jailer 2");
            int n = 0;
            for (News.Item i : feed.items) {
                if (!i.title.toLowerCase().contains("jailer") || n++ >= 10) continue;
                FrameLayout card = Ui.card(this, Ui.dp(this, 360), Ui.dp(this, 150), false);
                card.setBackground(Ui.round(0xE0101A1C, Ui.dp(this, 12), Ui.dp(this, 1), 0x44FFA040));
                LinearLayout t = new LinearLayout(this);
                t.setOrientation(LinearLayout.VERTICAL);
                t.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
                t.addView(NewsActivity.badge(this, i));
                TextView h = Ui.text(this, i.title, 16, Ui.TEXT, false);
                h.setMaxLines(3);
                h.setEllipsize(TextUtils.TruncateAt.END);
                t.addView(h);
                t.addView(Ui.label(this, (i.source == null ? "" : i.source) + "  ·  " + News.ago(i.time), 13, Ui.GOLD));
                Ui.underRim(card, t, new FrameLayout.LayoutParams(-1, -1));
                card.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class).putExtra("topic", "Jailer 2")));
                Ui.add(strip, card);
            }
        }
        if (first != null) first.requestFocus();
    }
}
