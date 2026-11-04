package tv.thalaivar.hub;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * THALAIVAR ICONIC MOMENTS: eleven categories, each a YouTube search aimed at official channels. Nothing is
 * copied into the app; the YouTube app shows what its owners publish.
 */
public class MomentsActivity extends Activity {
    private static final Object[][] CATS = {
            {"m_entry", "Rajinikanth mass entry scene official", 0xFF3A0A10, 0xFFB71C1C},
            {"m_style", "Rajinikanth style scenes official", 0xFF3A2A06, 0xFFD4AF37},
            {"m_punch", "Rajinikanth punch dialogue scene official", 0xFF1A1208, 0xFFFF8F00},
            {"m_comedy", "Rajinikanth comedy scenes official", 0xFF0B3A2A, 0xFF43A047},
            {"m_emotion", "Rajinikanth emotional scene official", 0xFF0B2A4A, 0xFF5C6BC0},
            {"m_action", "Rajinikanth action scene official", 0xFF2A0A0A, 0xFFE53935},
            {"m_interview", "Rajinikanth interview", 0xFF1A1A1A, 0xFF757575},
            {"m_public", "Rajinikanth public appearance", 0xFF102A2A, 0xFF26A69A},
            {"m_audio", "Rajinikanth audio launch speech", 0xFF2A1250, 0xFF8E24AA},
            {"m_fans", "Rajinikanth fans celebration theatre", 0xFF3A2A06, 0xFFFFB300},
            {"m_history", "Rajinikanth movie success celebration", 0xFF3A0A10, 0xFFD4AF37},
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.gradient(0xFF14040A, 0xFF0A0604, android.graphics.drawable.GradientDrawable.Orientation.TL_BR));
        root.addView(new ParticleView(this, Ui.GOLD_HI), Ui.match());
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, Ui.dp(this, 36), 0, Ui.dp(this, 40));
        android.widget.TextView h = Ui.gold(this, Lang.t("moments").toUpperCase(), 48);
        h.setPadding(Ui.dp(this, 48), 0, 0, 0);
        page.addView(h);
        android.widget.TextView s = Ui.label(this, "THALAIVAR ICONIC MOMENTS  ·  " + Lang.t("opens_youtube"), 18, Ui.DIM);
        s.setPadding(Ui.dp(this, 50), 0, 0, 0);
        page.addView(s);
        LinearLayout r1 = Ui.row(this, page, "");
        LinearLayout r2 = Ui.row(this, page, "");
        int w = Ui.dp(this, 300), hh = Ui.dp(this, 170);
        for (int i = 0; i < CATS.length; i++) {
            Object[] c = CATS[i];
            String key = (String) c[0];
            String q = (String) c[1];
            FrameLayout t = Ui.tile(this, Lang.t(key), Lang.tamil ? "" : "", (int) c[2], (int) c[3], w, hh);
            t.setOnClickListener(v -> Ui.youtubeSearch(this, q));
            Ui.add(i < 6 ? r1 : r2, t);
        }
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
        if (r1.getChildCount() > 0) r1.getChildAt(0).requestFocus();
    }
}
