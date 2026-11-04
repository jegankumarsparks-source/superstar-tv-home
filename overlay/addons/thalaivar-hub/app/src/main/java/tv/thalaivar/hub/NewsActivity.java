package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * The news engine's own page: every headline with its publisher, age and a confirmed / reported / rumour
 * badge, topic filters, a refresh, and the time of the copy shown when the TV is offline.
 */
public class NewsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list, filters;
    private TextView status;
    private News.Feed feed;
    private String topic;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        topic = getIntent().getStringExtra("topic");
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.INK);
        ScrollView sv = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 56), Ui.dp(this, 36), Ui.dp(this, 56), Ui.dp(this, 40));
        page.addView(Ui.gold(this, Lang.t("news"), 48));
        status = Ui.label(this, Lang.t("loading"), 17, Ui.DIM);
        page.addView(status);
        filters = Ui.buttonRow(this, page);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        TextView note = Ui.text(this, Lang.t("auto_label") + "  ·  " + Lang.t("news_attr"), 13, 0x88FFFFFF, false);
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        page.addView(note);
        sv.addView(page);
        root.addView(sv, Ui.match());
        setContentView(root);
        load(false);
    }

    private void load(boolean force) {
        status.setText(Lang.t("loading"));
        Executors.newSingleThreadExecutor().execute(() -> {
            News.Feed f = News.load(this, force);
            main.post(() -> {
                feed = f;
                render();
            });
        });
    }

    private void render() {
        if (feed.items.isEmpty()) {
            status.setText(Lang.t("offline"));
        } else {
            status.setText((feed.fresh ? Lang.t("updated") : "⚠ " + Lang.t("offline") + " — " + Lang.t("saved_news")) + " " + when(feed.savedAt));
        }
        filters.removeAllViews();
        filters.addView(Ui.button(this, "⟳  " + Lang.t("refresh"), v -> load(true)));
        filters.addView(filter(null, Lang.t("all")));
        for (String[] t : News.TOPICS) filters.addView(filter(t[0], t[0]));
        list.removeAllViews();
        View first = null;
        for (News.Item i : feed.items) {
            if (topic != null && !topic.equals(i.topic)) continue;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setFocusable(true);
            row.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            row.setBackground(Ui.round(0x22FFFFFF, Ui.dp(this, 10), 0, 0));
            row.setOnFocusChangeListener((v, has) -> v.setBackground(has
                    ? Ui.round(0x44D4AF37, Ui.dp(this, 10), Ui.dp(this, 2), Ui.GOLD_HI)
                    : Ui.round(0x22FFFFFF, Ui.dp(this, 10), 0, 0)));
            row.addView(badge(this, i));
            row.addView(Ui.text(this, i.title, 20, Ui.TEXT, false));
            row.addView(Ui.label(this, (i.source == null ? "" : i.source) + "  ·  " + News.ago(i.time) + "  ·  " + i.topic, 15, Ui.GOLD));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Ui.dp(this, 10);
            list.addView(row, lp);
            if (first == null) first = row;
        }
        if (first != null) first.requestFocus();
        else if (filters.getChildCount() > 0) filters.getChildAt(0).requestFocus();
    }

    private View filter(String t, String label) {
        TextView b = Ui.button(this, (t == null ? topic == null : t.equals(topic)) ? "● " + label : label, v -> {
            topic = t;
            render();
        });
        return b;
    }

    static TextView badge(Context c, News.Item i) {
        String[] keys = {"confirmed", "reported", "rumour"};
        int[] colours = {0xFF43A047, 0xFFFFB300, 0xFFE53935};
        TextView t = Ui.label(c, Lang.t(keys[i.kind]), 13, 0xFF000000);
        t.setBackground(Ui.round(colours[i.kind], Ui.dp(c, 4), 0, 0));
        t.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.bottomMargin = Ui.dp(c, 4);
        t.setLayoutParams(lp);
        return t;
    }

    static String when(long t) {
        if (t <= 0) return "—";
        SimpleDateFormat f = new SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH);
        f.setTimeZone(HubActivity.CHENNAI);
        return f.format(new Date(t));
    }
}
