package tv.thalaivar.hub;

import android.service.dreams.DreamService;

/** The TV's own screensaver: Settings > Screen saver > Thalaivar TV. */
public class ThalaivarDream extends DreamService {
    private SaverView saver;

    boolean baasha() {
        return false;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        new Prefs(this);
        saver = new SaverView(this, baasha());
        setContentView(saver);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        saver.start();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        saver.stop();
    }
}
