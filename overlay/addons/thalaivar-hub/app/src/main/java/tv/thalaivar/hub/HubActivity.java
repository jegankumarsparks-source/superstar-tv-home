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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;

/** THALAIVAR TV's home: the splash, the hero, and every row of the fan world. */
public class HubActivity extends Activity {
    static final TimeZone CHENNAI = TimeZone.getTimeZone("Asia/Kolkata");
    private final Handler main = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private ImageView heroA, heroB;
    private TextView clock, date, greet, featTitle, featSub;
    private LinearLayout rows;
    private List<Films.Film> films = new ArrayList<>();
    private int heroIndex;
    private boolean splashShown;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        PhoneMic.start(this);
        build();
        if (b == null) splash();
        loadFilms();
        loadNews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // back from a film or a video: continue watching, favourites and recently viewed are up to date
        if (cinemaAnchor != null) refreshPersonal();
        tick.run();
        main.postDelayed(rotate, 12000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        main.removeCallbacks(tick);
        main.removeCallbacks(rotate);
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.INK);
        heroA = Ui.image(this);
        heroB = Ui.image(this);
        root.addView(heroA, Ui.match());
        root.addView(heroB, Ui.match());
        heroA.setImageBitmap(null);
        try {
            heroA.setImageDrawable(android.graphics.drawable.Drawable.createFromStream(getAssets().open("art/01-emperor-gold.jpg"), null));
        } catch (Exception ignored) {
            // the ink background stays
        }
        View scrimL = new View(this);
        scrimL.setBackground(Ui.gradient(0xF00A0604, 0x000A0604, GradientDrawable.Orientation.LEFT_RIGHT));
        root.addView(scrimL, new FrameLayout.LayoutParams(Ui.dp(this, 760), -1));
        View scrimB = new View(this);
        scrimB.setBackground(Ui.gradient(0x000A0604, 0xFF0A0604, GradientDrawable.Orientation.TOP_BOTTOM));
        root.addView(scrimB, new FrameLayout.LayoutParams(-1, -1));
        scrimB.setAlpha(0.85f);
        root.addView(new ParticleView(this, Ui.GOLD_HI), Ui.match());

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setSmoothScrollingEnabled(true);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(0, 0, 0, Ui.dp(this, 60));
        sv.addView(rows);
        root.addView(sv, Ui.match());

        // header: brand, tagline, Chennai clock and date, then the dashboard's navigation
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(top);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(Ui.gold(this, "THALAIVAR TV", 34));
        brand.addView(Ui.label(this, Lang.t("brand_tag"), 15, Ui.DIM));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        clock = Ui.gold(this, "", 40);
        date = Ui.label(this, "", 16, Ui.TEXT);
        time.addView(clock);
        time.addView(date);
        top.addView(time);
        nav = Ui.buttonRow(this, header);
        addNav("HOME", this::goHome);
        addNav("THALAIVAR CINEMA", () -> jumpTo(cinemaAnchor));
        addNav("MY MOVIES", () -> jumpTo(myAnchor != null ? myAnchor : cinemaAnchor));
        addNav("APPS", () -> startActivity(new Intent(this, AppsActivity.class)));
        addNav("YOUTUBE", () -> startActivity(new Intent(this, TubeActivity.class)));
        addNav("ENTERTAINMENT", () -> jumpTo(entAnchor));
        addNav("JARVIS", () -> startActivity(new Intent(this, JarvisActivity.class)));
        addNav("SETTINGS", () -> startActivity(new Intent(this, SettingsActivity.class)));
        addNav("🔍", () -> startActivity(new Intent(this, SearchActivity.class)));
        // the brand and the Chennai clock stay on screen while the rows scroll under them
        header.setPadding(Ui.dp(this, 48), Ui.dp(this, 18), Ui.dp(this, 48), Ui.dp(this, 14));
        header.setBackground(Ui.gradient(0xF50A0604, 0x000A0604, GradientDrawable.Orientation.TOP_BOTTOM));
        root.addView(header, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        this.header = header;
        this.sv = sv;
        // browsing the rows: the header slides up out of the way; it comes back at the top or when the menu is focused
        int hideAfter = Ui.dp(this, 60);
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> showHeader(y <= hideAfter));
        rows.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.dp(this, 150)));
        // personal rows (continue watching, favourites, recently viewed) go here, refreshed on every return
        personal = new LinearLayout(this);
        personal.setOrientation(LinearLayout.VERTICAL);

        // welcome and the featured film
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.dp(this, 48), Ui.dp(this, 36), Ui.dp(this, 48), 0);
        greet = Ui.text(this, "", 26, Ui.TEXT, true);
        hero.addView(greet);
        TextView feat = Ui.label(this, Lang.t("featured"), 16, Ui.GOLD);
        feat.setPadding(0, Ui.dp(this, 22), 0, 0);
        hero.addView(feat);
        featTitle = Ui.gold(this, Lang.t("loading"), 54);
        hero.addView(featTitle);
        featSub = Ui.text(this, "", 16, Ui.DIM, false);
        featSub.setMaxLines(2);
        featSub.setEllipsize(TextUtils.TruncateAt.END);
        featSub.setMaxWidth(Ui.dp(this, 620));
        hero.addView(featSub);
        rows.addView(hero);
        rows.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.dp(this, 40)));
        rows.addView(personal);

        // J.A.R.V.I.S, small and out of the way in the corner: OK on it opens the compact panel
        FaceView orb = new FaceView(this);
        orb.setFocusable(true);
        orb.setOnClickListener(v -> startActivity(new Intent(this, JarvisActivity.class)));
        orb.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.25f : 1f).scaleY(has ? 1.25f : 1f).setDuration(160).start());
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84), Gravity.BOTTOM | Gravity.END);
        olp.setMargins(0, 0, Ui.dp(this, 22), Ui.dp(this, 18));
        root.addView(orb, olp);
        setContentView(root);
    }

    private LinearLayout nav, personal, header;
    private ScrollView sv;
    private View cinemaAnchor, myAnchor, entAnchor;
    private List<Films.Film> everything = new ArrayList<>();
    private View firstCard;

    private void addNav(String label, Runnable action) {
        TextView b = Ui.button(this, label, v -> action.run());
        // compact pills: all nine fit across the screen, none scrolled off the edge
        b.setTextSize(14);
        b.setPadding(Ui.dp(this, 15), Ui.dp(this, 6), Ui.dp(this, 15), Ui.dp(this, 6));
        ((LinearLayout.LayoutParams) b.getLayoutParams()).rightMargin = Ui.dp(this, 9);
        b.setOnFocusChangeListener(chainNav(b.getOnFocusChangeListener()));
        nav.addView(b);
    }

    /** The menu focused: the header comes back into view. */
    private View.OnFocusChangeListener chainNav(View.OnFocusChangeListener base) {
        return (v, has) -> {
            base.onFocusChange(v, has);
            if (has) showHeader(true);
        };
    }

    private void showHeader(boolean show) {
        if (header == null || show == (header.getTag() != Boolean.TRUE)) return;
        header.setTag(!show);
        long d = prefs.motion() == Prefs.LOW ? 1 : 260;
        header.animate().translationY(show ? 0 : -header.getHeight()).alpha(show ? 1f : 0f).setDuration(d).start();
    }

    private void goHome() {
        sv.smoothScrollTo(0, 0);
        showHeader(true);
        if (firstCard != null) firstCard.requestFocus();
    }

    /** Scrolls a section's heading under the header and focuses its first card. */
    private void jumpTo(View anchor) {
        if (anchor == null || !(anchor.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) anchor.getParent();
        int top = anchor.getTop() + (parent == personal ? personal.getTop() : 0);
        sv.smoothScrollTo(0, Math.max(0, top - Ui.dp(this, 20)));
        int i = parent.indexOfChild(anchor);
        View strip = i >= 0 && i + 1 < parent.getChildCount() ? parent.getChildAt(i + 1) : null;
        if (strip instanceof android.widget.HorizontalScrollView) {
            View s = ((android.widget.HorizontalScrollView) strip).getChildAt(0);
            if (s instanceof LinearLayout && ((LinearLayout) s).getChildCount() > 0) ((LinearLayout) s).getChildAt(0).requestFocus();
        }
    }

    /** A row added to the page, and its heading kept as a place the menu can jump to. */
    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout strip = Ui.row(this, parent, title);
        return strip;
    }

    /** This is the TV's home screen: Back never leaves it, it returns to the top. */
    @Override
    public void onBackPressed() {
        goHome();
    }

    private void splash() {
        if (splashShown) return;
        splashShown = true;
        FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
        FrameLayout s = new FrameLayout(this);
        s.setBackgroundColor(0xFF000000);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_splash);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        s.addView(logo, Ui.match());
        View shine = new View(this);
        shine.setBackground(Ui.gradient(0x00FFFFFF, 0x55FFF1C0, GradientDrawable.Orientation.LEFT_RIGHT));
        s.addView(shine, new FrameLayout.LayoutParams(Ui.dp(this, 220), -1));
        root.addView(s, Ui.match());
        int level = prefs.motion();
        long hold = level == Prefs.LOW ? 600 : level == Prefs.HIGH ? 2200 : 1600;
        logo.setScaleX(1.18f);
        logo.setScaleY(1.18f);
        logo.setAlpha(0f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(hold * 2 / 3).start();
        shine.setTranslationX(-Ui.dp(this, 300));
        if (level > Prefs.LOW) {
            shine.animate().translationX(getResources().getDisplayMetrics().widthPixels).setStartDelay(hold / 3)
                    .setDuration(hold / 2).start();
        }
        s.animate().alpha(0f).setStartDelay(hold).setDuration(level == Prefs.LOW ? 150 : 450)
                .withEndAction(() -> root.removeView(s)).start();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            SimpleDateFormat t = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            t.setTimeZone(CHENNAI);
            SimpleDateFormat d = new SimpleDateFormat("EEEE, d MMMM yyyy", Lang.tamil ? new Locale("ta", "IN") : Locale.ENGLISH);
            d.setTimeZone(CHENNAI);
            clock.setText(t.format(now).toUpperCase(Locale.ENGLISH));
            date.setText(d.format(now) + "  ·  " + Lang.t("chennai"));
            Calendar cal = Calendar.getInstance(CHENNAI);
            int hr = cal.get(Calendar.HOUR_OF_DAY);
            String g = Lang.t(hr < 12 ? "morning" : hr < 16 ? "afternoon" : hr < 20 ? "evening" : "night");
            if (!g.contentEquals(greet.getText())) {
                greet.setText(g);
                greet.setAlpha(0f);
                greet.setTranslationY(Ui.dp(HubActivity.this, 12));
                greet.animate().alpha(1f).translationY(0).setDuration(prefs.motion() == Prefs.LOW ? 1 : 900).start();
            }
            main.postDelayed(this, 30000 - (System.currentTimeMillis() % 30000));
        }
    };

    /** The hero backdrop crossfades through the films every 12 seconds, never the same one twice in a row. */
    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            List<Films.Film> withArt = new ArrayList<>();
            for (Films.Film f : films) if (f.backdrop != null && !"null".equals(f.backdrop)) withArt.add(f);
            if (!withArt.isEmpty()) {
                heroIndex = (heroIndex + 1 + (int) (Math.random() * Math.max(1, withArt.size() - 1))) % withArt.size();
                showHero(withArt.get(heroIndex));
            }
            main.postDelayed(this, 12000);
        }
    };

    private void showHero(Films.Film f) {
        featTitle.setText(f.name() + (f.year > 0 ? "  " + f.year : ""));
        featSub.setText(f.overview);
        int w = getResources().getDisplayMetrics().widthPixels;
        Img.load(this, Tmdb.img(f.backdrop, "w1280"), w, bmp -> {
            if (bmp == null) return;
            ImageView in = heroB.getAlpha() > 0.5f ? heroA : heroB;
            ImageView out = in == heroA ? heroB : heroA;
            in.setImageBitmap(bmp);
            long d = prefs.motion() == Prefs.LOW ? 1 : 1400;
            in.setScaleX(prefs.motion() == Prefs.HIGH ? 1.06f : 1f);
            in.setScaleY(in.getScaleX());
            in.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(prefs.motion() == Prefs.HIGH ? 12000 : d).start();
            out.animate().alpha(0f).setDuration(d).start();
        });
    }

    private void loadFilms() {
        if (!Tmdb.hasToken()) {
            featTitle.setText(Lang.t("no_token"));
            buildRows(Films.curated(), new ArrayList<>());
            return;
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Films.Film> curated = Films.curated();
            List<Films.Film> all = new ArrayList<>();
            try {
                Films.resolve(this, curated);
                all = Films.all(this);
            } catch (Exception e) {
                main.post(() -> featSub.setText(Lang.t("offline")));
            }
            List<Films.Film> everything = all;
            main.post(() -> {
                films = curated;
                buildRows(curated, everything);
                if (!curated.isEmpty() && curated.get(0).backdrop != null) showHero(curated.get(0));
            });
        });
    }

    private News.Feed news;

    private void loadNews() {
        Executors.newSingleThreadExecutor().execute(() -> {
            News.Feed f = News.load(this, false);
            main.post(() -> {
                news = f;
                fillNewsRow();
            });
        });
    }

    private LinearLayout newsStrip;

    private void buildRows(List<Films.Film> curated, List<Films.Film> all) {
        while (rows.getChildCount() > 4) rows.removeViewAt(4);
        int pw = Ui.dp(this, 150);
        everything = all;

        int at = rows.getChildCount();
        LinearLayout movies = section(rows, "THALAIVAR CINEMA  ·  " + Lang.both("movies"));
        cinemaAnchor = rows.getChildAt(at);
        View first = null;
        for (Films.Film f : curated) {
            FrameLayout card = Ui.poster(this, f, pw);
            card.setOnClickListener(v -> openMovie(f));
            card.setOnFocusChangeListener(chain(card.getOnFocusChangeListener(), f));
            Ui.add(movies, card);
            if (first == null) first = card;
        }

        LinearLayout special = Ui.row(this, rows, Lang.both("special"));
        int tw = Ui.dp(this, 300), th = Ui.dp(this, 150);
        addTile(special, Lang.t("baasha_title"), Lang.t("baasha_sub"), 0xFF000000, 0xFF5A4206, tw, th,
                () -> startActivity(new Intent(this, SpecialActivity.class).putExtra("zone", "baasha")));
        addTile(special, Lang.t("jailer2"), Lang.t("jailer2_sub"), 0xFF0E2A30, 0xFFB4501A, tw, th,
                () -> startActivity(new Intent(this, SpecialActivity.class).putExtra("zone", "jailer2")));
        addTile(special, "J.A.R.V.I.S", "Speak · Ask · Play", 0xFF0A1430, 0xFF8A6A12, tw, th,
                () -> startActivity(new Intent(this, JarvisActivity.class)));
        addTile(special, "JK Tube", "Watch · Search · Speak", 0xFF3A0610, 0xFFB71C1C, tw, th,
                () -> startActivity(new Intent(this, TubeActivity.class)));
        addTile(special, "Photos", "Full screen gallery", 0xFF1A1208, 0xFF8A6A12, tw, th,
                () -> startActivity(new Intent(this, PhotoActivity.class)));
        addTile(special, Lang.t("moments"), Lang.t("dialogues"), Ui.RED, Ui.PURPLE, tw, th,
                () -> startActivity(new Intent(this, MomentsActivity.class)));
        addTile(special, Lang.t("wallpapers"), Lang.t("screensaver"), Ui.PURPLE, Ui.BLUE, tw, th,
                () -> startActivity(new Intent(this, WallpaperActivity.class)));
        addTile(special, Lang.t("screensaver"), Lang.t("start_saver"), 0xFF1A1208, Ui.GOLD, tw, th,
                () -> startActivity(new Intent(this, SaverActivity.class)));

        // ENTERTAINMENT: JK Tube first, then every streaming and music app actually installed on this TV
        at = rows.getChildCount();
        LinearLayout ent = section(rows, "ENTERTAINMENT");
        entAnchor = rows.getChildAt(at);
        addTile(ent, "JK Tube", "Watch · Search · Speak", 0xFF3A0610, 0xFFB71C1C, Ui.dp(this, 240), Ui.dp(this, 135),
                () -> startActivity(new Intent(this, TubeActivity.class)));
        android.content.pm.PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (android.content.pm.ResolveInfo r : pm.queryIntentActivities(q, 0)) {
            android.content.pm.ApplicationInfo ai = r.activityInfo.applicationInfo;
            String g = AppsActivity.group(ai);
            if (ai.packageName.equals(getPackageName()) || ai.packageName.equals(Ui.YT_TV)
                    || !("entertainment".equals(g) || "music".equals(g))) continue;
            Ui.add(ent, appTile(r, pm));
        }

        newsStrip = Ui.row(this, rows, Lang.both("news"));
        fillNewsRow();

        List<String> pinned = prefs.pinned();
        LinearLayout apps = Ui.row(this, rows, Lang.both(pinned.isEmpty() ? "apps" : "my_dock"));
        for (String pkg : pinned) {
            CharSequence name = pkg;
            try {
                name = getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0));
            } catch (Exception ignored) {
                continue;
            }
            addTile(apps, name.toString(), "", 0xFF1A1208, 0xFF3A2A06, Ui.dp(this, 220), Ui.dp(this, 110), () -> Ui.launchApp(this, pkg));
        }
        addTile(apps, Lang.t("apps"), Lang.t("entertainment") + " · " + Lang.t("music") + " · " + Lang.t("games"),
                0xFF0B2A4A, Ui.BLUE, Ui.dp(this, 300), Ui.dp(this, 110), () -> startActivity(new Intent(this, AppsActivity.class)));
        addTile(apps, Lang.t("settings"), Lang.t("language") + " · " + Lang.t("animation"), 0xFF222222, 0xFF444444,
                Ui.dp(this, 260), Ui.dp(this, 110), () -> startActivity(new Intent(this, SettingsActivity.class)));

        if (!all.isEmpty()) {
            LinearLayout full = Ui.row(this, rows, Lang.both("filmography") + "  (" + all.size() + ")");
            int shown = 0;
            for (Films.Film f : all) {
                if (shown++ >= 60) break;
                FrameLayout card = Ui.poster(this, f, Ui.dp(this, 120));
                card.setOnClickListener(v -> openMovie(f));
                Ui.add(full, card);
            }
        }
        TextView attr = Ui.text(this, Lang.t("attribution") + "  ·  " + Lang.t("news_attr"), 12, 0x88FFFFFF, false);
        attr.setPadding(Ui.dp(this, 48), Ui.dp(this, 20), Ui.dp(this, 48), 0);
        rows.addView(attr);
        firstCard = first;
        refreshPersonal();
        if (first != null) first.requestFocus();
    }

    /** An installed app as a tile with its own banner (or icon and name). */
    private FrameLayout appTile(android.content.pm.ResolveInfo r, android.content.pm.PackageManager pm) {
        String pkg = r.activityInfo.packageName;
        FrameLayout card = Ui.card(this, Ui.dp(this, 240), Ui.dp(this, 135), false);
        android.graphics.drawable.Drawable banner = r.activityInfo.loadBanner(pm);
        if (banner == null) banner = r.activityInfo.applicationInfo.loadBanner(pm);
        ImageView iv = new ImageView(this);
        if (banner != null) {
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Ui.underRim(card, iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackground(Ui.round(Ui.gradientColour(pkg), Ui.dp(this, 10), 0, 0));
            iv.setImageDrawable(r.loadIcon(pm));
            Ui.underRim(card, iv, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER));
        }
        card.setOnClickListener(v -> Ui.launchApp(this, pkg));
        return card;
    }

    /**
     * MY MOVIES and CONTINUE WATCHING, rebuilt each time the dashboard comes back: videos stopped part-way
     * (they resume where they were), favourite films and recently opened films.
     */
    private void refreshPersonal() {
        if (personal == null) return;
        personal.removeAllViews();
        myAnchor = null;
        List<String[]> watching = prefs.continueWatching();
        if (!watching.isEmpty()) {
            LinearLayout cw = section(personal, "CONTINUE WATCHING");
            for (String[] w : watching) {
                FrameLayout card = Ui.card(this, Ui.dp(this, 260), Ui.dp(this, 146), false);
                ImageView th = Ui.image(this);
                Ui.underRim(card, th, new FrameLayout.LayoutParams(-1, -1));
                Img.into(th, "https://i.ytimg.com/vi/" + w[0] + "/hqdefault.jpg", Ui.dp(this, 260));
                TextView cap = Ui.text(this, "▶  " + (w[1].isEmpty() ? "Resume" : w[1]), 14, Ui.TEXT, false);
                cap.setMaxLines(2);
                cap.setEllipsize(TextUtils.TruncateAt.END);
                cap.setBackground(Ui.gradient(0x00000000, 0xE6000000, GradientDrawable.Orientation.TOP_BOTTOM));
                cap.setPadding(Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 8));
                Ui.underRim(card, cap, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                card.setOnClickListener(v -> TubeActivity.play(this, w[0], w[1]));
                Ui.add(cw, card);
            }
        }
        java.util.Map<Long, Films.Film> byId = new java.util.HashMap<>();
        for (Films.Film f : everything) byId.put(f.id, f);
        for (Films.Film f : films) if (f.id > 0) byId.put(f.id, f);
        List<Films.Film> favs = new ArrayList<>();
        for (String s : prefs.set("fav_movies")) {
            try {
                Films.Film f = byId.get(Long.parseLong(s));
                if (f != null) favs.add(f);
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        List<Films.Film> recent = new ArrayList<>();
        for (Long id : prefs.recentMovies()) {
            Films.Film f = byId.get(id);
            if (f != null) recent.add(f);
        }
        if (!favs.isEmpty() || !recent.isEmpty()) {
            int at = personal.getChildCount();
            if (!favs.isEmpty()) {
                LinearLayout fr = section(personal, "MY MOVIES  ·  ★ " + Lang.t("favourites"));
                for (Films.Film f : favs) addPoster(fr, f);
            }
            if (!recent.isEmpty()) {
                LinearLayout rr = section(personal, (favs.isEmpty() ? "MY MOVIES  ·  " : "") + "RECENTLY VIEWED");
                for (Films.Film f : recent) addPoster(rr, f);
            }
            myAnchor = personal.getChildAt(at);
        }
    }

    private void addPoster(LinearLayout strip, Films.Film f) {
        FrameLayout card = Ui.poster(this, f, Ui.dp(this, 130));
        card.setOnClickListener(v -> openMovie(f));
        Ui.add(strip, card);
    }

    private View.OnFocusChangeListener chain(View.OnFocusChangeListener base, Films.Film f) {
        return (v, has) -> {
            base.onFocusChange(v, has);
            if (has && f.backdrop != null) {
                main.removeCallbacks(rotate);
                showHero(f);
                main.postDelayed(rotate, 15000);
            }
        };
    }

    private void fillNewsRow() {
        if (newsStrip == null || news == null) return;
        newsStrip.removeAllViews();
        int n = 0;
        for (News.Item i : news.items) {
            if (n++ >= 10) break;
            FrameLayout card = Ui.card(this, Ui.dp(this, 340), Ui.dp(this, 150), false);
            card.setBackground(Ui.round(0xE01A1208, Ui.dp(this, 12), Ui.dp(this, 1), 0x44D4AF37));
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
            card.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class)));
            Ui.add(newsStrip, card);
        }
        addTile(newsStrip, Lang.t("news"), (news.fresh ? Lang.t("updated") : Lang.t("saved_news")) + " " + NewsActivity.when(news.savedAt),
                Ui.RED, 0xFF3A0A10, Ui.dp(this, 260), Ui.dp(this, 150), () -> startActivity(new Intent(this, NewsActivity.class)));
    }

    private void addTile(LinearLayout strip, String title, String sub, int from, int to, int w, int h, Runnable action) {
        FrameLayout t = Ui.tile(this, title, sub, from, to, w, h);
        t.setOnClickListener(v -> action.run());
        Ui.add(strip, t);
    }

    private void openMovie(Films.Film f) {
        if (f.id < 0) {
            Ui.youtubeSearch(this, f.title + " " + f.year + " Rajinikanth official trailer");
            return;
        }
        startActivity(new Intent(this, MovieActivity.class).putExtra("id", f.id).putExtra("tamil", f.tamil).putExtra("stem", f.stem));
    }

    /** The remote's search / voice button anywhere on the hub calls J.A.R.V.I.S. */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent e) {
        if (keyCode == android.view.KeyEvent.KEYCODE_SEARCH || keyCode == android.view.KeyEvent.KEYCODE_VOICE_ASSIST) {
            startActivity(new Intent(this, JarvisActivity.class));
            return true;
        }
        // Up with nothing above the focused card (the menu is hidden while scrolled): back to the top menu
        if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP && nav != null && nav.getChildCount() > 0 && !nav.hasFocus()) {
            sv.smoothScrollTo(0, 0);
            showHeader(true);
            nav.getChildAt(0).requestFocus();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    /** Home pressed while the dashboard is up: back to the top, nothing rebuilt. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goHome();
    }
}
