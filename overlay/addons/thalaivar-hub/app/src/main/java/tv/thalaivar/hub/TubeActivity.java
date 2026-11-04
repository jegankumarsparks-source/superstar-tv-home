package tv.thalaivar.hub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * JK Tube: a TV-first video browser in the THALAIVAR style. Search bar on top, rows of
 * videos underneath, 3D focus, and playback in the official YouTube player (or the app the owner picks).
 */
public class TubeActivity extends Activity {
    /** Rows the home shows with a YouTube key: label and search. */
    private static final String[][] ROWS = {
            {"Superstar Rajinikanth", "Rajinikanth"},
            {"Jailer 2", "Jailer 2 official"},
            {"Tamil hit songs", "Tamil hit songs official video"},
            {"Anirudh hits", "Anirudh Ravichander songs official"},
            {"Tamil comedy", "Tamil comedy scenes official"},
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout rows;
    private EditText search;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF14060A, 0xFF0A0604, 0xFF050A18}));
        root.addView(new ParticleView(this, 0xFFFF6B6B), Ui.match());
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 48));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 48), 0, Ui.dp(this, 48), 0);
        TextView logo = Ui.gold(this, "JK TUBE", 40);
        top.addView(logo);
        TextView star = Ui.label(this, "  ★", 30, 0xFFFF5252);
        top.addView(star);
        search = new EditText(this);
        search.setHint("Search videos, songs, trailers…");
        search.setHintTextColor(0x88FFFFFF);
        search.setTextColor(Ui.TEXT);
        search.setSingleLine(true);
        search.setTypeface(Ui.montserrat(this));
        search.setTextSize(20);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        search.setBackground(Ui.round(0x33FFFFFF, Ui.dp(this, 28), Ui.dp(this, 1), 0x55FFDE78));
        search.setOnFocusChangeListener((v, has) -> v.setBackground(has
                ? Ui.round(0x55FFFFFF, Ui.dp(this, 28), Ui.dp(this, 2), Ui.GOLD_HI)
                : Ui.round(0x33FFFFFF, Ui.dp(this, 28), Ui.dp(this, 1), 0x55FFDE78)));
        search.setOnEditorActionListener((v, action, e) -> {
            run(search.getText().toString());
            return true;
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, -2, 1);
        slp.leftMargin = Ui.dp(this, 32);
        slp.rightMargin = Ui.dp(this, 16);
        top.addView(search, slp);
        top.addView(Ui.button(this, "✦  JARVIS", v -> startActivity(new Intent(this, JarvisActivity.class))));
        page.addView(top);
        status = Ui.label(this, "", 16, Ui.DIM);
        status.setPadding(Ui.dp(this, 50), Ui.dp(this, 10), 0, 0);
        page.addView(status);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        page.addView(rows);
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);

        String q = getIntent().getStringExtra("q");
        if (q != null) run(q); else home();
    }

    private void home() {
        status.setText(Lang.t("loading"));
        Ui.WORK.execute(() -> {
            List<Object[]> built = new ArrayList<>();
            built.add(new Object[]{"Thalaivar trailers & clips", YtApi.thalaivarVideos(this)});
            if (YtApi.hasKey()) {
                try {
                    built.add(new Object[]{"Trending in India", YtApi.trending(this, 20)});
                    for (String[] r : ROWS) built.add(new Object[]{r[0], YtApi.search(this, r[1], 15)});
                } catch (Exception e) {
                    main.post(() -> status.setText(Lang.t("offline")));
                }
            }
            main.post(() -> {
                rows.removeAllViews();
                View first = null;
                for (Object[] r : built) {
                    @SuppressWarnings("unchecked") List<YtApi.Video> vids = (List<YtApi.Video>) r[1];
                    View f = row((String) r[0], vids);
                    if (first == null) first = f;
                }
                status.setText(YtApi.hasKey() ? "" : "Search opens in the YouTube app · add a free YouTube key for in-app search (see the guide)");
                if (first != null) first.requestFocus(); else search.requestFocus();
            });
        });
    }

    private void run(String q) {
        if (q == null || q.trim().isEmpty()) return;
        q = q.trim();
        search.setText(q);
        if (!YtApi.hasKey()) {
            // stays in JK Tube: matching a song or video by name needs the free YouTube Data API key
            status.setText("To find “" + q + "”, JK Tube needs a free YouTube Data API key (youtube.key in secrets.properties). "
                    + "Thalaivar trailers below play now.");
            return;
        }
        status.setText("Searching “" + q + "”…");
        String query = q;
        Ui.WORK.execute(() -> {
            List<YtApi.Video> l = new ArrayList<>();
            boolean failed = false;
            try {
                l = YtApi.search(this, query, 30);
            } catch (Exception e) {
                failed = true;
            }
            List<YtApi.Video> res = l;
            boolean offline = failed;
            main.post(() -> {
                if (offline) {
                    status.setText(Lang.t("offline"));
                    return;
                }
                rows.removeAllViews();
                status.setText(res.size() + " results for “" + query + "”");
                View first = null;
                for (int i = 0; i < res.size(); i += 10) {
                    View f = row(i == 0 ? "Results" : "More", res.subList(i, Math.min(res.size(), i + 10)));
                    if (first == null) first = f;
                }
                if (first != null) first.requestFocus();
            });
        });
    }

    private View row(String title, List<YtApi.Video> vids) {
        if (vids == null || vids.isEmpty()) return null;
        LinearLayout strip = Ui.row(this, rows, title);
        View first = null;
        for (YtApi.Video v : vids) {
            FrameLayout card = Ui.card(this, Ui.dp(this, 300), Ui.dp(this, 169), true);
            ImageView th = Ui.image(this);
            Ui.underRim(card, th, new FrameLayout.LayoutParams(-1, -1));
            Img.into(th, v.thumb(), Ui.dp(this, 300));
            View shade = new View(this);
            shade.setBackground(Ui.gradient(0x00000000, 0xE6000000, GradientDrawable.Orientation.TOP_BOTTOM));
            Ui.underRim(card, shade, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 10));
            TextView name = Ui.text(this, v.title, 14, Ui.TEXT, false);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(name);
            if (v.channel != null) t.addView(Ui.label(this, v.channel, 13, Ui.GOLD));
            Ui.underRim(card, t, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            // the chosen video first, then the rest of the row as backups
            java.util.ArrayList<String> queue = ids(vids.subList(vids.indexOf(v), vids.size()));
            card.setOnClickListener(x -> playQueue(this, queue, v.title));
            Ui.add(strip, card);
            if (first == null) first = card;
        }
        return first;
    }

    /** Plays in JK Tube's player, or in the video app the owner picked in Settings. */
    /** Every video plays in JK Tube's own full-screen player. */
    static void play(android.content.Context a, String id, String title) {
        java.util.ArrayList<String> one = new java.util.ArrayList<>();
        one.add(id);
        playQueue(a, one, title);
    }

    /** The best match first, the rest kept as backups: a video that won't play in JK Tube skips to the next. */
    static void playQueue(android.content.Context a, java.util.ArrayList<String> ids, String title) {
        a.startActivity(new Intent(a, PlayerActivity.class).putStringArrayListExtra("queue", ids)
                .putExtra("id", ids.get(0)).putExtra("title", title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static java.util.ArrayList<String> ids(List<YtApi.Video> vids) {
        java.util.ArrayList<String> l = new java.util.ArrayList<>();
        for (YtApi.Video v : vids) l.add(v.id);
        return l;
    }
}
