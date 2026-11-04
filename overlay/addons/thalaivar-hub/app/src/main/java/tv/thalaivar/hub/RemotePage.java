package tv.thalaivar.hub;

import android.content.Context;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The phone remote's page, assets/remote.html (one file, no outside resources): power, the D-pad or a
 * touchpad, volume with a level bar, media keys, J.A.R.V.I.S chat, every app on the TV with its icon, and the
 * dashboard's screens. Its address carries the pairing key, so the page finds its own commands under the same
 * path.
 */
final class RemotePage {
    private static String html;

    static synchronized String html(Context c) {
        if (html == null) {
            try (InputStream in = c.getAssets().open("remote.html")) {
                html = new String(Net.readAll(in), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return "<!doctype html><meta charset=utf-8><p>The remote page is missing from this build.</p>";
            }
        }
        return html;
    }

    private RemotePage() {
    }
}
