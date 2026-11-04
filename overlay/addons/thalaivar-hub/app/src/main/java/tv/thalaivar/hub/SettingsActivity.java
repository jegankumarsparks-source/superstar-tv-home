package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Language, animation level (Auto / Low / Medium / High), screensaver interval and the cache. */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private LinearLayout page;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        render();
    }

    private void render() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.INK);
        ScrollView sv = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 56), Ui.dp(this, 36), Ui.dp(this, 56), Ui.dp(this, 40));
        page.addView(Ui.gold(this, Lang.t("settings"), 46));

        LinearLayout lang = group(Lang.both("language"));
        lang.addView(choice("தமிழ்", Lang.tamil, () -> prefs.setLanguage("ta")));
        lang.addView(choice("English", !Lang.tamil, () -> prefs.setLanguage("en")));

        String level = Lang.t(new String[]{"low", "medium", "high"}[Prefs.autoMotion(this)]);
        LinearLayout motion = group(Lang.both("animation") + "   (" + Lang.t("auto") + " → " + level + ")");
        String m = prefs.motionChoice();
        motion.addView(choice(Lang.t("auto"), "auto".equals(m), () -> prefs.setMotion("auto")));
        motion.addView(choice(Lang.t("low"), "low".equals(m), () -> prefs.setMotion("low")));
        motion.addView(choice(Lang.t("medium"), "medium".equals(m), () -> prefs.setMotion("medium")));
        motion.addView(choice(Lang.t("high"), "high".equals(m), () -> prefs.setMotion("high")));

        LinearLayout rot = group(Lang.both("rotation"));
        for (int min : new int[]{1, 2, 5, 10, 30}) {
            rot.addView(choice(min + " " + Lang.t("minutes"), prefs.rotateMinutes() == min, () -> prefs.setRotateMinutes(min)));
        }

        LinearLayout jv = group("J.A.R.V.I.S on the remote's mic / search button (any app)");
        jv.addView(choice("On", prefs.jarvisButton(), () -> prefs.setJarvisButton(true)));
        jv.addView(choice("Off", !prefs.jarvisButton(), () -> prefs.setJarvisButton(false)));
        jv.addView(Ui.button(this, "Accessibility settings", v -> Ui.start(this, new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        TextView svc = Ui.label(this, keyServiceOn() ? "✓ Remote button service is running" : "⚠ Turn on “J.A.R.V.I.S remote button” in TV Settings > Accessibility", 15,
                keyServiceOn() ? 0xFF66BB6A : 0xFFFFB74D);
        page.addView(svc);
        LinearLayout mic = group("Mic language");
        String ml = prefs.micLanguage();
        mic.addView(choice("English + Tamil", "both".equals(ml), () -> prefs.setMicLanguage("both")));
        mic.addView(choice("English (India)", "en-IN".equals(ml), () -> prefs.setMicLanguage("en-IN")));
        mic.addView(choice("தமிழ்", "ta-IN".equals(ml), () -> prefs.setMicLanguage("ta-IN")));
        mic.addView(Ui.button(this, "🎤 Test mic", v -> startActivity(new Intent(this, JarvisActivity.class))));
        LinearLayout sp = group("J.A.R.V.I.S speaks replies");
        sp.addView(choice("On", prefs.speakReplies(), () -> prefs.setSpeakReplies(true)));
        sp.addView(choice("Off", !prefs.speakReplies(), () -> prefs.setSpeakReplies(false)));

        LinearLayout src = group("Screensaver shows");
        String ss = prefs.saverSource();
        src.addView(choice("Everything", "all".equals(ss), () -> prefs.setSaverSource("all")));
        src.addView(choice("Portraits", "portraits".equals(ss), () -> prefs.setSaverSource("portraits")));
        src.addView(choice("Film stills", "stills".equals(ss), () -> prefs.setSaverSource("stills")));
        src.addView(choice("★ Starred only", "starred".equals(ss), () -> prefs.setSaverSource("starred")));
        LinearLayout clk = group("Screensaver clock");
        clk.addView(choice("On", prefs.saverClock(), () -> prefs.setSaverClock(true)));
        clk.addView(choice("Off", !prefs.saverClock(), () -> prefs.setSaverClock(false)));

        LinearLayout play = group("All videos play with");
        String pl = prefs.player();
        play.addView(choice("Auto (SmartTube if installed)", "auto".equals(pl), () -> prefs.setPlayer("auto")));
        play.addView(choice("JK Tube player", "jktube".equals(pl), () -> prefs.setPlayer("jktube")));
        play.addView(choice("Ask each time", "ask".equals(pl), () -> prefs.setPlayer("ask")));
        android.content.Intent probe = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://www.youtube.com/watch?v=00000000000"));
        for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(probe, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || play.getChildCount() >= 5) continue;
            String name = r.loadLabel(getPackageManager()).toString();
            play.addView(choice(name, pkg.equals(pl), () -> prefs.setPlayer(pkg)));
        }

        LinearLayout more = group(Lang.t("screensaver"));
        more.addView(Ui.button(this, Lang.t("tv_saver_settings"), v -> {
            if (!Ui.start(this, new Intent("android.settings.DREAM_SETTINGS"))) Ui.start(this, new Intent(android.provider.Settings.ACTION_SETTINGS));
        }));
        more.addView(Ui.button(this, Lang.t("clear_cache"), v -> {
            Net.clear(this);
            Ui.toast(this, "✓ " + Lang.t("clear_cache"));
        }));

        TextView attr = Ui.text(this, Lang.t("attribution") + "\n" + Lang.t("news_attr")
                + "\nFonts: Cinzel, Bebas Neue (SIL Open Font License). THALAIVAR TV is an unofficial fan app.", 14, 0x99FFFFFF, false);
        attr.setPadding(0, Ui.dp(this, 26), 0, 0);
        page.addView(attr);
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
        if (lang.getChildCount() > 0) lang.getChildAt(0).requestFocus();
    }

    /** Back from the TV's Accessibility page: show the remote-button service's new state. */
    @Override
    protected void onResume() {
        super.onResume();
        if (page != null) render();
    }

    private boolean keyServiceOn() {
        String s = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/") && s.contains("JarvisKeyService");
    }

    private LinearLayout group(String title) {
        TextView t = Ui.label(this, title, 22, Ui.GOLD_HI);
        t.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8));
        page.addView(t);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        page.addView(row);
        return row;
    }

    private TextView choice(String label, boolean on, Runnable set) {
        return Ui.button(this, (on ? "● " : "○ ") + label, v -> {
            set.run();
            render();
        });
    }
}
