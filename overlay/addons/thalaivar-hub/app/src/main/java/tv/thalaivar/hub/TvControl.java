package tv.thalaivar.hub;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the phone remote can do on the TV. Navigation (arrows, OK, Back, Home, recent apps, the TV's
 * side panel, sleep) goes through the THALAIVAR TV helper's global actions (D-pad actions need Android 13);
 * volume and media keys through the audio service, or straight to JK Tube's player when it is in front;
 * apps by their launcher entry; the dashboard's own screens by name.
 */
final class TvControl {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean key(Context c, String k) {
        if (k == null) return false;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        // media buttons go to JK Tube's player when it is showing (it has no media session of its own)
        PlayerActivity player = PlayerActivity.showing;
        if (player != null && player.remote(k)) return true;
        switch (k) {
            case "volup":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return true;
            case "voldown":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return true;
            case "mute":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                return true;
            case "playpause": return media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            case "play": return media(am, KeyEvent.KEYCODE_MEDIA_PLAY);
            case "pause": return media(am, KeyEvent.KEYCODE_MEDIA_PAUSE);
            case "stop": return media(am, KeyEvent.KEYCODE_MEDIA_STOP);
            case "next": return media(am, KeyEvent.KEYCODE_MEDIA_NEXT);
            case "prev": return media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            case "rewind": case "rewind30": return media(am, KeyEvent.KEYCODE_MEDIA_REWIND);
            case "ff": case "ff30": return media(am, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
            case "wake": return wake(c);
            case "settings": return open(c, new Intent(android.provider.Settings.ACTION_SETTINGS));
            case "saver": return open(c, new Intent(c, SaverActivity.class));
            default: break;
        }
        AccessibilityService svc = JarvisKeyService.running;
        if (svc == null) return false;
        int action;
        switch (k) {
            case "back": action = AccessibilityService.GLOBAL_ACTION_BACK; break;
            case "home": action = AccessibilityService.GLOBAL_ACTION_HOME; break;
            case "recents": action = AccessibilityService.GLOBAL_ACTION_RECENTS; break;
            case "panel": action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS; break;
            case "sleep": action = Build.VERSION.SDK_INT >= 28 ? AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN : -1; break;
            case "up": action = Build.VERSION.SDK_INT >= 33 ? AccessibilityService.GLOBAL_ACTION_DPAD_UP : -1; break;
            case "down": action = Build.VERSION.SDK_INT >= 33 ? AccessibilityService.GLOBAL_ACTION_DPAD_DOWN : -1; break;
            case "left": action = Build.VERSION.SDK_INT >= 33 ? AccessibilityService.GLOBAL_ACTION_DPAD_LEFT : -1; break;
            case "right": action = Build.VERSION.SDK_INT >= 33 ? AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT : -1; break;
            case "ok": action = Build.VERSION.SDK_INT >= 33 ? AccessibilityService.GLOBAL_ACTION_DPAD_CENTER : -1; break;
            default: action = -1;
        }
        if (action < 0) return false;
        int a = action;
        MAIN.post(() -> svc.performGlobalAction(a));
        return true;
    }

    private static boolean media(AudioManager am, int code) {
        long now = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0));
        return true;
    }

    /** Screen back on while the TV is only resting (the system keeps it reachable for a while after sleep). */
    @SuppressWarnings("deprecation")
    private static boolean wake(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return false;
        if (!pm.isInteractive()) {
            PowerManager.WakeLock w = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                    | PowerManager.ACQUIRE_CAUSES_WAKEUP, "thalaivar:wake");
            w.acquire(3000);
        }
        // and the dashboard, as on every power-on
        MAIN.postDelayed(() -> open(c, new Intent(c, HubActivity.class)), 600);
        return true;
    }

    static boolean screenOn(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    /** Volume 0..max on the TV's media stream. */
    static boolean setVolume(Context c, String v) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            int want = Integer.parseInt(v);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, want)), AudioManager.FLAG_SHOW_UI);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The phone's status line: volume, mute, screen, what JK Tube is playing, J.A.R.V.I.S's last words. */
    static String status(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            return new JSONObject().put("ok", true)
                    .put("vol", am.getStreamVolume(AudioManager.STREAM_MUSIC))
                    .put("max", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                    .put("muted", am.isStreamMute(AudioManager.STREAM_MUSIC))
                    .put("on", screenOn(c))
                    .put("helper", JarvisKeyService.running != null)
                    .put("now", PlayerActivity.showing != null ? PlayerActivity.nowTitle : "")
                    .put("said", JarvisActivity.lastSaid)
                    .put("saidAt", JarvisActivity.lastSaidAt)
                    .toString();
        } catch (Exception e) {
            return "{\"ok\":true}";
        }
    }

    /** The dashboard's own screens, by name. */
    static boolean go(Context c, String s, String q) {
        if (s == null) return false;
        Intent i;
        switch (s) {
            case "home": case "cinema": case "my": case "ent":
                i = new Intent(c, HubActivity.class).putExtra("section", s);
                break;
            case "apps": i = new Intent(c, AppsActivity.class); break;
            case "tube":
                i = new Intent(c, TubeActivity.class);
                if (q != null && !q.trim().isEmpty() && q.length() <= 200) i.putExtra("q", q.trim());
                break;
            case "films":
                i = new Intent(c, SearchActivity.class);
                if (q != null && !q.trim().isEmpty() && q.length() <= 100) i.putExtra("q", q.trim());
                break;
            case "news": i = new Intent(c, NewsActivity.class); break;
            case "photos": i = new Intent(c, PhotoActivity.class); break;
            case "moments": i = new Intent(c, MomentsActivity.class); break;
            case "wallpapers": i = new Intent(c, WallpaperActivity.class); break;
            case "saver": i = new Intent(c, SaverActivity.class); break;
            case "jarvis": i = new Intent(c, JarvisActivity.class); break;
            case "hubsettings": i = new Intent(c, SettingsActivity.class); break;
            default: return false;
        }
        return open(c, i);
    }

    private static boolean open(Context c, Intent i) {
        try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens an installed app by package, as its own tile would. */
    static boolean app(Context c, String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{3,120}")) return false;
        if ("jktube".equals(pkg)) {
            c.startActivity(new Intent(c, TubeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
        Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return true;
    }

    /** Every app on the TV's launcher, A to Z, as [{"p":package,"n":name}]. */
    static String apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, String> seen = new HashMap<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                if (!seen.containsKey(p)) seen.put(p, r.loadLabel(pm).toString());
            }
        }
        List<Map.Entry<String, String>> l = new ArrayList<>(seen.entrySet());
        l.sort((a, b) -> a.getValue().toLowerCase(Locale.ROOT).compareTo(b.getValue().toLowerCase(Locale.ROOT)));
        JSONArray a = new JSONArray();
        try {
            for (Map.Entry<String, String> e : l) a.put(new JSONObject().put("p", e.getKey()).put("n", e.getValue()));
        } catch (Exception ignored) {
            // a partial list is still useful
        }
        return a.toString();
    }

    private static final Map<String, byte[]> ICONS = new HashMap<>();

    /** An app's icon as a 96 px PNG, kept after the first time; null for an unknown package. */
    static synchronized byte[] icon(Context c, String pkg) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_.]{3,120}")) return null;
        byte[] png = ICONS.get(pkg);
        if (png != null) return png;
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 96, 96);
            d.draw(new Canvas(b));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            png = out.toByteArray();
            ICONS.put(pkg, png);
            return png;
        } catch (Exception e) {
            return null;
        }
    }

    private TvControl() {
    }
}
