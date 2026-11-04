package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every app on the TV, found at run time and sorted into Entertainment, Music, Live TV, Games and Tools.
 * Nothing is hidden, disabled or removed. OK opens an app; hold OK pins it to the hub's own row.
 */
public class AppsActivity extends Activity {
    private static final String[] OTT = {"netflix", "amazonvideo", "hotstar", "disney", "sunnxt", "graymatrix", "sonyliv",
            "aha", "jiocinema", "mxplayer", "zee5", "voot", "erosnow", "hoichoi", "mubi", "apple", "youtube.tv", "primevideo", "airtel"};
    private static final String[] MUSIC = {"music", "spotify", "gaana", "jiosaavn", "wynk", "saavn", "audible", "radio", "podcast"};
    private static final String[] LIVE = {"livetv", "tv.tuner", "android.tv", "dtv", "tvinput", "channels", "jiotv", "tataplay", "dishtv"};

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.gradient(0xFF0B0A1E, 0xFF0A0604, android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM));
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 40));
        TextView h = Ui.gold(this, Lang.t("apps"), 46);
        h.setPadding(Ui.dp(this, 48), 0, 0, 0);
        page.addView(h);
        TextView hint = Ui.label(this, "OK: open  ·  hold OK: " + Lang.t("pin") + " / " + Lang.t("unpin"), 16, Ui.DIM);
        hint.setPadding(Ui.dp(this, 50), 0, 0, 0);
        page.addView(hint);

        Map<String, List<ResolveInfo>> groups = new LinkedHashMap<>();
        for (String k : new String[]{"entertainment", "music", "live_tv", "games", "tools"}) groups.put(k, new ArrayList<>());
        PackageManager pm = getPackageManager();
        Map<String, ResolveInfo> apps = new TreeMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || apps.containsKey(pkg)) continue;
                apps.put(pkg, r);
            }
        }
        for (ResolveInfo r : apps.values()) groups.get(group(r.activityInfo.applicationInfo)).add(r);
        boolean focused = false;
        for (Map.Entry<String, List<ResolveInfo>> g : groups.entrySet()) {
            if (g.getValue().isEmpty()) continue;
            g.getValue().sort((x, y) -> x.loadLabel(pm).toString().compareToIgnoreCase(y.loadLabel(pm).toString()));
            LinearLayout strip = Ui.row(this, page, Lang.both(g.getKey()) + "  (" + g.getValue().size() + ")");
            for (ResolveInfo r : g.getValue()) {
                FrameLayout card = appCard(r, pm);
                Ui.add(strip, card);
                if (!focused) {
                    card.requestFocus();
                    focused = true;
                }
            }
            if (g.getKey().equals("tools")) {
                FrameLayout s = Ui.tile(this, Lang.t("settings"), "Android TV", 0xFF222222, 0xFF555555, Ui.dp(this, 240), Ui.dp(this, 135));
                s.setOnClickListener(v -> Ui.start(this, new Intent(Settings.ACTION_SETTINGS)));
                Ui.add(strip, s);
            }
        }
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
    }

    static String group(ApplicationInfo ai) {
        String p = ai.packageName.toLowerCase(Locale.ROOT);
        if (ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return "games";
        for (String m : MUSIC) if (p.contains(m)) return "music";
        if (ai.category == ApplicationInfo.CATEGORY_AUDIO) return "music";
        for (String o : OTT) if (p.contains(o)) return "entertainment";
        if (ai.category == ApplicationInfo.CATEGORY_VIDEO) return "entertainment";
        for (String l : LIVE) if (p.contains(l)) return "live_tv";
        return "tools";
    }

    private FrameLayout appCard(ResolveInfo r, PackageManager pm) {
        String pkg = r.activityInfo.packageName;
        FrameLayout card = Ui.card(this, Ui.dp(this, 240), Ui.dp(this, 135), false);
        Drawable banner = r.activityInfo.loadBanner(pm);
        if (banner == null) banner = r.activityInfo.applicationInfo.loadBanner(pm);
        ImageView iv = new ImageView(this);
        if (banner != null) {
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.underRim(card, iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackground(Ui.round(Ui.gradientColour(pkg), Ui.dp(this, 10), 0, 0));
            iv.setImageDrawable(r.loadIcon(pm));
            Ui.underRim(card, iv, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            ((FrameLayout.LayoutParams) iv.getLayoutParams()).topMargin = Ui.dp(this, 18);
            TextView name = Ui.text(this, r.loadLabel(pm).toString(), 16, Ui.TEXT, false);
            name.setGravity(Gravity.CENTER);
            name.setSingleLine(true);
            Ui.underRim(card, name, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            ((FrameLayout.LayoutParams) name.getLayoutParams()).bottomMargin = Ui.dp(this, 12);
        }
        if (prefs.pinned().contains(pkg)) {
            TextView star = Ui.label(this, "★", 22, Ui.GOLD_HI);
            Ui.underRim(card, star, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP));
        }
        card.setOnClickListener(v -> Ui.launchApp(this, pkg));
        card.setOnLongClickListener(v -> {
            boolean on = prefs.togglePin(pkg);
            Ui.toast(this, (on ? "★ " + Lang.t("pin") : "☆ " + Lang.t("unpin")) + ": " + r.loadLabel(pm));
            return true;
        });
        return card;
    }
}
