package tv.thalaivar.hub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.app.SearchManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Shared look: colours, type, gold text, focusable cards with the 3D tilt, rows and YouTube hand-off. */
final class Ui {
    static final int GOLD = 0xFFD4AF37, GOLD_HI = 0xFFFFDE78, RED = 0xFF96101A, PURPLE = 0xFF4A148C,
            BLUE = 0xFF1E88FF, INK = 0xFF0A0604, CARD = 0xFF1A1208, TEXT = 0xFFF5ECD7, DIM = 0xB3F5ECD7;
    static final String YT_TV = "com.google.android.youtube.tv";

    private static Typeface cinzel, bebas, montserrat;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static Typeface cinzel(Context c) {
        if (cinzel == null) cinzel = c.getResources().getFont(R.font.cinzel);
        return cinzel;
    }

    static Typeface bebas(Context c) {
        if (bebas == null) bebas = c.getResources().getFont(R.font.bebas);
        return bebas;
    }

    static Typeface montserrat(Context c) {
        if (montserrat == null) montserrat = c.getResources().getFont(R.font.montserrat);
        return montserrat;
    }

    /** Headings in Cinzel, body text in Montserrat; Tamil (if chosen) falls back to the system's Tamil font. */
    static TextView text(Context c, String s, float sp, int colour, boolean display) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        boolean tamil = s != null && s.codePoints().anyMatch(cp -> cp >= 0x0B80 && cp <= 0x0BFF);
        if (display && !tamil) t.setTypeface(cinzel(c), Typeface.BOLD);
        else if (display) t.setTypeface(Typeface.DEFAULT_BOLD);
        else if (!tamil) t.setTypeface(montserrat(c));
        t.setIncludeFontPadding(true);
        return t;
    }

    static TextView label(Context c, String s, float sp, int colour) {
        TextView t = text(c, s, sp, colour, false);
        boolean tamil = s != null && s.codePoints().anyMatch(cp -> cp >= 0x0B80 && cp <= 0x0BFF);
        if (!tamil) {
            t.setTypeface(bebas(c));
            t.setLetterSpacing(0.08f);
        }
        return t;
    }

    /** Metallic gold: a vertical gradient through the glyphs, set once the view knows its height. */
    static TextView gold(Context c, String s, float sp) {
        TextView t = text(c, s, sp, GOLD, true);
        t.setShadowLayer(dp(c, 14), 0, 0, 0x88D4AF37);
        t.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> {
            int h = b - top;
            if (h > 0) t.getPaint().setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{GOLD_HI, GOLD, 0xFF8A6A12, GOLD_HI}, new float[]{0f, 0.45f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        });
        return t;
    }

    static GradientDrawable round(int colour, float radius, int stroke, int strokeColour) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(radius);
        if (stroke > 0) g.setStroke(stroke, strokeColour);
        return g;
    }

    static GradientDrawable gradient(int a, int b, GradientDrawable.Orientation o) {
        GradientDrawable g = new GradientDrawable(o, new int[]{a, b});
        g.setCornerRadius(0);
        return g;
    }

    /**
     * A focusable card. At rest a poster card stands turned a little on its vertical axis, like a
     * standee; focused, it swings to face the viewer, lifts and gains a gold rim.
     */
    static FrameLayout card(Context c, int w, int h, boolean tilt) {
        FrameLayout f = new FrameLayout(c);
        f.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        f.setFocusable(true);
        f.setFocusableInTouchMode(false);
        f.setClipToOutline(true);
        f.setBackground(round(CARD, dp(c, 10), 0, 0));
        int level = new Prefs(c).motion();
        float restTilt = tilt && level > Prefs.LOW ? -14f : 0f;
        f.setCameraDistance(dp(c, 2400));
        f.setRotationY(restTilt);
        View rim = new View(c);
        rim.setBackground(round(Color.TRANSPARENT, dp(c, 10), dp(c, 3), GOLD_HI));
        rim.setAlpha(0f);
        f.setOnFocusChangeListener((v, has) -> {
            float s = has ? (level == Prefs.HIGH ? 1.14f : 1.09f) : 1f;
            long d = level == Prefs.LOW ? 60 : (level == Prefs.HIGH ? 260 : 180);
            v.animate().scaleX(s).scaleY(s).rotationY(has ? 0f : restTilt).translationZ(has ? dp(c, 16) : 0)
                    .setDuration(d).start();
            rim.animate().alpha(has ? 1f : 0f).setDuration(d).start();
            if (has) {
                View p = (View) v.getParent();
                if (p != null && p.getParent() instanceof HorizontalScrollView) {
                    HorizontalScrollView hs = (HorizontalScrollView) p.getParent();
                    int target = v.getLeft() - (hs.getWidth() - v.getWidth()) / 2;
                    if (level == Prefs.LOW) hs.scrollTo(target, 0); else hs.smoothScrollTo(target, 0);
                }
            }
        });
        f.addView(rim, new FrameLayout.LayoutParams(-1, -1));
        f.setTag(R.id.rim, rim);
        return f;
    }

    /** Puts a child under the rim so the gold edge always draws on top. */
    static void underRim(FrameLayout card, View child, FrameLayout.LayoutParams lp) {
        View rim = (View) card.getTag(R.id.rim);
        card.addView(child, rim == null ? card.getChildCount() : card.indexOfChild(rim), lp);
    }

    static ImageView image(Context c) {
        ImageView i = new ImageView(c);
        i.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return i;
    }

    /** A poster card: TMDB poster, a gradient foot with the film's name and year. */
    static FrameLayout poster(Context c, Films.Film f, int w) {
        int h = w * 3 / 2;
        FrameLayout card = card(c, w, h, true);
        card.setBackground(round(gradientColour(f.title), dp(c, 10), 0, 0));
        ImageView img = image(c);
        underRim(card, img, new FrameLayout.LayoutParams(-1, -1));
        Img.into(img, Tmdb.img(f.poster, "w342"), w);
        View foot = new View(c);
        foot.setBackground(gradient(0x00000000, 0xE6000000, GradientDrawable.Orientation.TOP_BOTTOM));
        underRim(card, foot, new FrameLayout.LayoutParams(-1, h / 3, Gravity.BOTTOM));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(c, 10), 0, dp(c, 10), dp(c, 8));
        TextView name = text(c, f.name(), 15, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        t.addView(name);
        if (f.year > 0) t.addView(label(c, String.valueOf(f.year), 14, GOLD));
        underRim(card, t, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return card;
    }

    /** A wide tile with its own gradient, a gold heading and a line under it. */
    static FrameLayout tile(Context c, String title, String sub, int from, int to, int w, int h) {
        FrameLayout card = card(c, w, h, false);
        card.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to}) {{
            setCornerRadius(dp(c, 12));
        }});
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        // cream with a gold glow reads on every tile colour, the gold ones included
        TextView head = text(c, title, 20, TEXT, true);
        head.setShadowLayer(dp(c, 10), 0, 0, 0xCC000000);
        head.setGravity(Gravity.CENTER);
        t.addView(head);
        if (sub != null && !sub.isEmpty()) {
            TextView s = label(c, sub, 14, TEXT);
            s.setGravity(Gravity.CENTER);
            t.addView(s);
        }
        underRim(card, t, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    static int gradientColour(String seed) {
        int[] palette = {0xFF3A0A10, 0xFF2A1250, 0xFF0B2A4A, 0xFF3A2A06, 0xFF102A2A};
        return palette[Math.abs(seed == null ? 0 : seed.hashCode()) % palette.length];
    }

    /** A titled row: heading, then a sideways strip of cards that scrolls to keep focus centred. */
    static LinearLayout row(Context c, LinearLayout parent, String heading) {
        TextView h = label(c, heading, 24, GOLD_HI);
        h.setPadding(dp(c, 48), dp(c, 18), 0, dp(c, 6));
        parent.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(c, 48), dp(c, 14), dp(c, 48), dp(c, 18));
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        hs.addView(strip);
        parent.addView(hs);
        return strip;
    }

    static void add(LinearLayout strip, View card) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        lp.rightMargin = dp(strip.getContext(), 18);
        strip.addView(card, lp);
    }

    /** A pill button for detail pages. */
    static TextView button(Context c, String s, View.OnClickListener click) {
        TextView b = label(c, s, 20, TEXT);
        b.setSingleLine(true);
        b.setPadding(dp(c, 22), dp(c, 8), dp(c, 22), dp(c, 8));
        b.setFocusable(true);
        b.setBackground(round(0x33FFFFFF, dp(c, 24), dp(c, 1), 0x55D4AF37));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? round(GOLD, dp(c, 24), 0, 0) : round(0x33FFFFFF, dp(c, 24), dp(c, 1), 0x55D4AF37));
            ((TextView) v).setTextColor(has ? INK : TEXT);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(120).start();
        });
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(c, 14);
        b.setLayoutParams(lp);
        return b;
    }

    /** A row of buttons that scrolls sideways instead of squeezing its buttons when they don't fit. */
    static LinearLayout buttonRow(Context c, LinearLayout parent) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(c, 6), dp(c, 24), dp(c, 6));
        row.setClipChildren(false);
        hs.addView(row);
        parent.addView(hs);
        return row;
    }

    /** An official video, in the YouTube app on the TV. */
    static void youtube(Activity a, String key) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key));
        i.setPackage(YT_TV);
        if (!start(a, i)) start(a, new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key)));
    }

    /** A search in the YouTube app, for songs and moments: YouTube decides what is there, nothing is copied here. */
    static void youtubeSearch(Activity a, String query) {
        Intent s = new Intent(Intent.ACTION_SEARCH);
        s.setPackage(YT_TV);
        s.putExtra(SearchManager.QUERY, query);
        s.putExtra("query", query);
        if (start(a, s)) return;
        Intent v = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
        v.setPackage(YT_TV);
        if (start(a, v)) return;
        toast(a, "YouTube " + Lang.t("not_installed"));
    }

    static boolean start(Activity a, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    static void launchApp(Activity a, String pkg) {
        Intent i = a.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null || !start(a, i)) toast(a, pkg + " " + Lang.t("not_installed"));
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    /** OK and Enter and the centre key all count as a press on our own cards. */
    static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    static ViewGroup.LayoutParams match() {
        return new ViewGroup.LayoutParams(-1, -1);
    }

    private Ui() {
    }
}
