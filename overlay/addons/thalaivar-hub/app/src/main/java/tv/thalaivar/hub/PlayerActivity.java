package tv.thalaivar.hub;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * JK Tube's player: YouTube's official embedded player (IFrame API) full screen, driven by the remote.
 * OK play/pause · Left/Right 10 s back/forward · Up/Down show or hide the title · Back closes.
 * YouTube decides what the embedded player shows, ads included.
 */
public class PlayerActivity extends Activity {
    private WebView web;
    private TextView info;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id = getIntent().getStringExtra("id");
        String title = getIntent().getStringExtra("title");
        // the id comes from YouTube / TMDB data and goes into the player page: only a real 11-character video id
        if (id == null || !id.matches("^[A-Za-z0-9_-]{11}$")) {
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(false);
        root.addView(web, Ui.match());
        info = Ui.label(this, "JK TUBE  ·  " + (title == null ? "" : title) + "\nOK play/pause   ◀ ▶ 10 s   BACK close", 18, Ui.TEXT);
        info.setBackground(Ui.gradient(0xE0000000, 0x00000000, android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM));
        info.setPadding(Ui.dp(this, 40), Ui.dp(this, 24), Ui.dp(this, 40), Ui.dp(this, 40));
        root.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        setContentView(root);
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>"
                + "<body><div id='p'></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:" + org.json.JSONObject.quote(id) + ","
                + "playerVars:{autoplay:1,controls:1,rel:0,playsinline:1,modestbranding:1,fs:0},"
                + "events:{onReady:function(e){e.target.playVideo();}}});}"
                + "function toggle(){if(!player)return;player.getPlayerState()==1?player.pauseVideo():player.playVideo();}"
                + "function seek(d){if(player)player.seekTo(Math.max(0,player.getCurrentTime()+d),true);}"
                + "</script></body></html>";
        web.loadDataWithBaseURL("https://thalaivar.tv/", html, "text/html", "utf-8", null);
        info.postDelayed(() -> info.animate().alpha(0f).setDuration(600).start(), 4000);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                web.evaluateJavascript("toggle()", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                web.evaluateJavascript("seek(-10)", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                web.evaluateJavascript("seek(10)", null);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                info.animate().alpha(info.getAlpha() > 0.5f ? 0f : 1f).setDuration(250).start();
                return true;
            default:
                return super.onKeyDown(keyCode, e);
        }
    }

    /** Home or another app in front: stop the video instead of letting its sound run on. */
    @Override
    protected void onPause() {
        web.evaluateJavascript("player&&player.pauseVideo&&player.pauseVideo()", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
