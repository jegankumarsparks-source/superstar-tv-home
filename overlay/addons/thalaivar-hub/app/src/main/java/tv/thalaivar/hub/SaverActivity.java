package tv.thalaivar.hub;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

/** The screensaver on demand, from the hub. Any button returns. */
public class SaverActivity extends Activity {
    private SaverView saver;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        saver = new SaverView(this, getIntent().getBooleanExtra("baasha", false));
        setContentView(saver);
        saver.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saver.stop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(keyCode, event);
        }
        finish();
        return true;
    }
}
