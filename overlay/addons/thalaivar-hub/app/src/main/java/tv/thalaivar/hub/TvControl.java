package tv.thalaivar.hub;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * What the phone remote can do on the TV. Navigation (arrows, OK, Back, Home) goes through the J.A.R.V.I.S
 * accessibility service's global actions (D-pad actions need Android 13); volume and play/pause through the
 * audio service; apps by their launcher entry.
 */
final class TvControl {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean key(Context c, String k) {
        if (k == null) return false;
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
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
            case "playpause":
                long now = SystemClock.uptimeMillis();
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0));
                return true;
            default:
                break;
        }
        AccessibilityService svc = JarvisKeyService.running;
        if (svc == null) return false;
        int action;
        switch (k) {
            case "back": action = AccessibilityService.GLOBAL_ACTION_BACK; break;
            case "home": action = AccessibilityService.GLOBAL_ACTION_HOME; break;
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

    private TvControl() {
    }
}
