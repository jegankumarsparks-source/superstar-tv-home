package tv.thalaivar.hub;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's search / voice button open J.A.R.V.I.S from anywhere on the TV, in any app. It sees
 * key presses only (no screen content), acts on these few buttons, and passes every other key through.
 * The hub's Settings has the On/Off switch; with it Off, the button does what it did before.
 */
public class JarvisKeyService extends AccessibilityService {
    static final int[] KEYS = {KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST};

    static boolean isJarvisKey(int code) {
        for (int k : KEYS) if (k == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isJarvisKey(event.getKeyCode()) || !new Prefs(this).jarvisButton()) return false;
        // The remote sends voice only while its mic button is held, so listening starts on the press, not the
        // release: whatever is said while holding the button is heard.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            startActivity(new Intent(this, JarvisActivity.class)
                    .putExtra("hold", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        return true;
    }

    /** The running service, for the phone remote's navigation keys; null while it is off. */
    static volatile JarvisKeyService running;

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

    private static final String GOOGLE_VOICE = "com.google.android.katniss";
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private long lastHandOver;

    /**
     * Bluetooth voice remotes (RC860G and most Google TV remotes) don't send a key for the mic button: the
     * remote service opens Google's voice screen directly. When that screen comes up and the switch is On,
     * it is closed and J.A.R.V.I.S opens and listens instead, through the same remote microphone.
     */
    /** The TV's other home screens; whenever one comes up, THALAIVAR TV's dashboard takes its place. */
    private static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";
    private static final String CLEANYOURTV_HOME = "com.gathin.tvhome";
    private long lastHome;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        CharSequence cls = event.getClassName();
        if (pkg == null) return;
        // Home button, power-on, leaving an app: the stock home appears, and the dashboard replaces it
        if (cls != null && new Prefs(this).hubIsHome()
                && ((GOOGLE_HOME.contentEquals(pkg) && cls.toString().contains("HomeActivity"))
                || (CLEANYOURTV_HOME.contentEquals(pkg) && cls.toString().endsWith("MainActivity")))) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastHome < 1500) return;
            lastHome = now;
            startActivity(new Intent(this, HubActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            return;
        }
        if (!GOOGLE_VOICE.contentEquals(pkg) || !new Prefs(this).jarvisButton()) return;
        // Google's assistant screen (an Activity) only ever comes from the mic button: hand over at once, even
        // while J.A.R.V.I.S is open. Anything else from that package while J.A.R.V.I.S is on screen is its own
        // listening (the same voice engine), which must be left alone.
        boolean assistantScreen = cls != null && cls.toString().endsWith("Activity");
        if (!assistantScreen && JarvisActivity.onScreen) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHandOver < 1200) return;
        lastHandOver = now;
        // J.A.R.V.I.S comes straight to the front over Google's screen, which closes itself in the background a
        // few seconds later. No Back press: Back could land on J.A.R.V.I.S or the film underneath instead.
        startActivity(new Intent(this, JarvisActivity.class)
                .putExtra("hold", true)
                .putExtra("over_google", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override
    public void onInterrupt() {
    }
}
