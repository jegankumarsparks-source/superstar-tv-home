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
