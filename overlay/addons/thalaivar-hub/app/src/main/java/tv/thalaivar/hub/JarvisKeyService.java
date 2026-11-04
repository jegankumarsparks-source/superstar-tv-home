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
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !GOOGLE_VOICE.contentEquals(pkg) || !new Prefs(this).jarvisButton()) return;
        // J.A.R.V.I.S's own listening uses Google's voice engine too: never close anything while it is on screen
        if (JarvisActivity.onScreen) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastHandOver < 2500) return;
        lastHandOver = now;
        performGlobalAction(GLOBAL_ACTION_BACK);
        main.postDelayed(() -> startActivity(new Intent(this, JarvisActivity.class)
                .putExtra("hold", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)), 120);
    }

    @Override
    public void onInterrupt() {
    }
}
