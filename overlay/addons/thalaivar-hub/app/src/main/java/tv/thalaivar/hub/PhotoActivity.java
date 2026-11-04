package tv.thalaivar.hub;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen photos: his portraits and every lead film's stills from TMDB, one at a time across the
 * whole screen. ◀ ▶ previous / next with a 3D swing · OK slideshow on/off · ▲ star for the screensaver
 * · ▼ show or hide the caption · Back closes.
 */
public class PhotoActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String[]> photos = new ArrayList<>();
    private ImageView a, b;
    private TextView caption, hint;
    private LinearLayout info;
    private Prefs prefs;
    private int index;
    private boolean slideshow;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = new Prefs(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        a = fit();
        b = fit();
        b.setAlpha(0f);
        root.addView(a, Ui.match());
        root.addView(b, Ui.match());
        info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(Ui.dp(this, 48), Ui.dp(this, 60), Ui.dp(this, 48), Ui.dp(this, 32));
        info.setBackground(Ui.gradient(0x00000000, 0xD0000000, GradientDrawable.Orientation.TOP_BOTTOM));
        caption = Ui.gold(this, Lang.t("loading"), 30);
        hint = Ui.label(this, "◀ ▶ browse   OK slideshow   ▲ ★ screensaver   ▼ hide   BACK close", 16, Ui.DIM);
        info.addView(caption);
        info.addView(hint);
        root.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        load();
    }

    private ImageView fit() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setCameraDistance(Ui.dp(this, 3000));
        return v;
    }

    private void load() {
        String only = getIntent().getStringExtra("film");
        Ui.WORK.execute(() -> {
            // built off the main thread in a list of its own, handed over in one step, so the remote never sees it half-filled
            List<String[]> found = new ArrayList<>();
            try {
                if (only == null) {
                    JSONArray p = Tmdb.portraits(this);
                    if (p != null) for (int i = 0; i < p.length(); i++) {
                        found.add(new String[]{p.getJSONObject(i).optString("file_path"), "Superstar Rajinikanth", "h632"});
                    }
                }
                for (Films.Film f : Films.resolve(this, Films.curated())) {
                    if (f.id < 0 || (only != null && !only.equals(f.stem))) continue;
                    JSONArray bd = Tmdb.images(this, f.id).optJSONArray("backdrops");
                    if (bd != null) for (int i = 0; i < Math.min(only == null ? 6 : 40, bd.length()); i++) {
                        found.add(new String[]{bd.getJSONObject(i).optString("file_path"), f.title + "  " + f.year, "original"});
                    }
                }
            } catch (Exception e) {
                main.post(() -> caption.setText(Lang.t("offline")));
            }
            main.post(() -> {
                photos.clear();
                photos.addAll(found);
                show(0, 0);
            });
        });
    }

    /** dir: -1 previous, 1 next, 0 first: the old picture swings away on its vertical axis as the new one swings in. */
    private void show(int i, int dir) {
        if (photos.isEmpty()) return;
        index = (i + photos.size()) % photos.size();
        String[] p = photos.get(index);
        String path = p[0];
        boolean starred = prefs.favWalls().contains(path);
        caption.setText((starred ? "★ " : "") + p[1] + "   " + (index + 1) + " / " + photos.size());
        int w = getResources().getDisplayMetrics().widthPixels;
        Img.load(this, Tmdb.img(path, "original".equals(p[2]) ? "w1280" : p[2]), w, bmp -> {
            if (bmp == null || index != photos.indexOf(p)) return;
            ImageView in = a.getAlpha() > 0.5f ? b : a;
            ImageView out = in == a ? b : a;
            in.setImageBitmap(bmp);
            boolean motion = prefs.motion() > Prefs.LOW;
            long d = motion ? 650 : 200;
            in.setRotationY(motion ? 35f * dir : 0f);
            in.setTranslationX(motion ? w * 0.25f * dir : 0f);
            in.setScaleX(motion ? 0.9f : 1f);
            in.setScaleY(motion ? 0.9f : 1f);
            in.animate().alpha(1f).rotationY(0f).translationX(0f).scaleX(1f).scaleY(1f).setDuration(d).start();
            out.animate().alpha(0f).rotationY(motion ? -35f * dir : 0f).translationX(motion ? -w * 0.25f * dir : 0f)
                    .scaleX(motion ? 0.9f : 1f).scaleY(motion ? 0.9f : 1f).setDuration(d).start();
        });
    }

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            show(index + 1, 1);
            main.postDelayed(this, 6000);
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                show(index + 1, 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                show(index - 1, -1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                slideshow = !slideshow;
                main.removeCallbacks(advance);
                if (slideshow) main.postDelayed(advance, 3000);
                Ui.toast(this, slideshow ? "Slideshow on" : "Slideshow off");
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (!photos.isEmpty()) {
                    boolean on = prefs.toggleFavWall(photos.get(index)[0]);
                    Ui.toast(this, on ? "★ Added to the screensaver" : "☆ Removed from the screensaver");
                    show(index, 0);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                info.animate().alpha(info.getAlpha() > 0.5f ? 0f : 1f).setDuration(250).start();
                return true;
            default:
                return super.onKeyDown(keyCode, e);
        }
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
