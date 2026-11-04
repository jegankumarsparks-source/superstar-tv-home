package tv.thalaivar.hub;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * THALAIVAR TV's helper service. It does two things and reads no screen content:
 * <ul>
 *   <li>whenever the TV's own home screen comes up (power-on, the Home button, leaving an app), the THALAIVAR TV
 *       dashboard takes its place (On/Off in the hub's Settings);</li>
 *   <li>it runs the phone remote and carries out its navigation buttons (arrows, OK, Back, Home, sleep).</li>
 * </ul>
 */
public class JarvisKeyService extends AccessibilityService {
    /** The running service, for the phone remote's navigation keys; null while it is off. */
    static volatile JarvisKeyService running;
    /** The name of the app on screen, for J.A.R.V.I.S's "what's on?" (only the app's name, never its content). */
    static volatile String onScreen = "";

    /** The TV's other home screens; whenever one comes up, THALAIVAR TV's dashboard takes its place. */
    private static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";
    private static final String CLEANYOURTV_HOME = "com.gathin.tvhome";
    private long lastHome;

    /** Bound by the system for as long as it is on, so the phone remote stays reachable. */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        PhoneMic.start(this);
        Voice.warm(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        running = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null || cls == null) return;
        noteApp(pkg.toString(), cls.toString());
        if (!new Prefs(this).hubIsHome()) return;
        // Home button, power-on, leaving an app: the stock home appears, and the dashboard replaces it
        if ((GOOGLE_HOME.contentEquals(pkg) && cls.toString().contains("HomeActivity"))
                || (CLEANYOURTV_HOME.contentEquals(pkg) && cls.toString().endsWith("MainActivity"))) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastHome < 1500) return;
            lastHome = now;
            startActivity(new Intent(this, HubActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
    }

    private void noteApp(String pkg, String cls) {
        // dialogs, keyboards and the system's own windows aren't "what's on"
        if (!cls.contains("Activity") || "android".equals(pkg) || pkg.startsWith("com.android.systemui")) return;
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            onScreen = getPackageName().equals(pkg) ? "THALAIVAR TV" : pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            onScreen = pkg;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
