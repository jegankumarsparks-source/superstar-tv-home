package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Film search: his whole filmography (English and Tamil names), filtered as you type; OK opens the film. */
public class SearchActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Films.Film> films = new ArrayList<>();
    private LinearLayout results;
    private TextView status;
    private EditText box;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF120A02, 0xFF0A0604, 0xFF050A18}));
        root.addView(new ParticleView(this, Ui.GOLD_HI), Ui.match());
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, Ui.dp(this, 32), 0, Ui.dp(this, 40));
        TextView h = Ui.gold(this, "SEARCH THALAIVAR CINEMA", 34);
        h.setPadding(Ui.dp(this, 48), 0, 0, Ui.dp(this, 10));
        page.addView(h);
        box = new EditText(this);
        box.setHint("Film name — Baasha, Padayappa, எந்திரன்…");
        box.setHintTextColor(0x88FFFFFF);
        box.setTextColor(Ui.TEXT);
        box.setTextSize(22);
        box.setSingleLine(true);
        box.setTypeface(Ui.montserrat(this));
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 12));
        box.setBackground(Ui.round(0x33FFFFFF, Ui.dp(this, 28), Ui.dp(this, 2), Ui.GOLD_HI));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.setMargins(Ui.dp(this, 48), 0, Ui.dp(this, 48), 0);
        page.addView(box, blp);
        status = Ui.label(this, Lang.t("loading"), 16, Ui.DIM);
        status.setPadding(Ui.dp(this, 50), Ui.dp(this, 10), 0, 0);
        page.addView(status);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        page.addView(results);
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
        box.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable e) {
                main.removeCallbacks(filter);
                main.postDelayed(filter, 250);
            }
        });
        box.requestFocus();
        Ui.WORK.execute(() -> {
            List<Films.Film> l = new ArrayList<>();
            try {
                l.addAll(Films.resolve(this, Films.curated()));
                java.util.Set<Long> seen = new java.util.HashSet<>();
                for (Films.Film f : l) seen.add(f.id);
                for (Films.Film f : Films.all(this)) if (seen.add(f.id)) l.add(f);
            } catch (Exception e) {
                main.post(() -> status.setText(Lang.t("offline")));
            }
            main.post(() -> {
                films.clear();
                for (Films.Film f : l) if (f.id > 0) films.add(f);
                filter.run();
            });
        });
    }

    private final Runnable filter = new Runnable() {
        @Override
        public void run() {
            String q = box.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<Films.Film> hits = new ArrayList<>();
            for (Films.Film f : films) {
                if (q.isEmpty() || f.title.toLowerCase(Locale.ROOT).contains(q) || f.tamil.contains(q)
                        || String.valueOf(f.year).equals(q)) hits.add(f);
            }
            results.removeAllViews();
            status.setText(q.isEmpty() ? films.size() + " films" : hits.size() + (hits.size() == 1 ? " film" : " films") + " for “" + q + "”");
            for (int i = 0; i < hits.size(); i += 8) {
                LinearLayout strip = Ui.row(SearchActivity.this, results, i == 0 ? "" : " ");
                for (Films.Film f : hits.subList(i, Math.min(hits.size(), i + 8))) {
                    FrameLayout card = Ui.poster(SearchActivity.this, f, Ui.dp(SearchActivity.this, 130));
                    card.setOnClickListener(v -> startActivity(new Intent(SearchActivity.this, MovieActivity.class)
                            .putExtra("id", f.id).putExtra("tamil", f.tamil)));
                    Ui.add(strip, card);
                }
            }
        }
    };
}
