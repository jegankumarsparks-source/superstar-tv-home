package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * The wallpaper library: each film's stills from TMDB, one row per film. OK stars a picture for the
 * screensaver; the starred ones rotate first, with no picture shown twice in a row.
 */
public class WallpaperActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.gradient(0xFF120A24, 0xFF0A0604, android.graphics.drawable.GradientDrawable.Orientation.TL_BR));
        ScrollView sv = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 40));
        TextView h = Ui.gold(this, Lang.t("wallpapers"), 46);
        h.setPadding(Ui.dp(this, 48), 0, 0, 0);
        page.addView(h);
        TextView hint = Ui.label(this, Lang.t("favourite_hint").split("•")[0] + "·  " + Lang.t("screensaver") + ": "
                + prefs.favWalls().size() + " ★", 16, Ui.DIM);
        hint.setPadding(Ui.dp(this, 50), 0, 0, 0);
        page.addView(hint);
        LinearLayout actions = Ui.row(this, page, Lang.t("screensaver"));
        FrameLayout start = Ui.tile(this, Lang.t("start_saver"), "THALAIVAR", 0xFF1A1208, Ui.GOLD, Ui.dp(this, 300), Ui.dp(this, 120));
        start.setOnClickListener(v -> startActivity(new Intent(this, SaverActivity.class)));
        Ui.add(actions, start);
        FrameLayout baasha = Ui.tile(this, Lang.t("baasha_saver"), "BAASHA", 0xFF000000, 0xFF5A4206, Ui.dp(this, 300), Ui.dp(this, 120));
        baasha.setOnClickListener(v -> startActivity(new Intent(this, SaverActivity.class).putExtra("baasha", true)));
        Ui.add(actions, baasha);
        FrameLayout tv = Ui.tile(this, Lang.t("tv_saver_settings"), "Android TV", 0xFF222222, 0xFF555555, Ui.dp(this, 300), Ui.dp(this, 120));
        tv.setOnClickListener(v -> {
            if (!Ui.start(this, new Intent("android.settings.DREAM_SETTINGS"))) Ui.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
        });
        Ui.add(actions, tv);
        start.requestFocus();
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
        load();
    }

    private void load() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Films.Film> films = Films.resolve(this, Films.curated());
                for (Films.Film f : films) {
                    if (f.id < 0) continue;
                    JSONArray bds = Tmdb.images(this, f.id).optJSONArray("backdrops");
                    if (bds == null || bds.length() == 0) continue;
                    main.post(() -> {
                        LinearLayout strip = Ui.row(this, page, f.name() + "  " + f.year);
                        for (int i = 0; i < Math.min(10, bds.length()); i++) {
                            Ui.add(strip, wallCard(this, prefs, bds.optJSONObject(i).optString("file_path")));
                        }
                    });
                }
            } catch (Exception e) {
                main.post(() -> page.addView(Ui.label(this, Lang.t("offline"), 20, Ui.DIM)));
            }
        });
    }

    /** A still: OK toggles its star for the screensaver. */
    static FrameLayout wallCard(Context c, Prefs prefs, String path) {
        FrameLayout card = Ui.card(c, Ui.dp(c, 300), Ui.dp(c, 169), false);
        ImageView iv = Ui.image(c);
        Ui.underRim(card, iv, new FrameLayout.LayoutParams(-1, -1));
        Img.into(iv, Tmdb.img(path, "w780"), Ui.dp(c, 300));
        TextView star = Ui.label(c, prefs.favWalls().contains(path) ? "★" : "", 30, Ui.GOLD_HI);
        star.setShadowLayer(8, 0, 0, 0xFF000000);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
        lp.setMargins(0, Ui.dp(c, 4), Ui.dp(c, 10), 0);
        Ui.underRim(card, star, lp);
        card.setOnClickListener(v -> star.setText(prefs.toggleFavWall(path) ? "★" : ""));
        return card;
    }
}
