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

    /** The player in front, for the phone remote's media buttons; null when none is showing. */
    static volatile PlayerActivity showing;
    /** What is playing, for the phone remote's "now playing". */
    static volatile String nowTitle = "";

    /** A media button from the phone remote; false when it isn't one the player knows. */
    boolean remote(String k) {
        String js;
        switch (k) {
            case "playpause": js = "toggle()"; break;
            case "play": js = "player&&player.playVideo()"; break;
            case "pause": js = "player&&player.pauseVideo()"; break;
            case "rewind": js = "seek(-10)"; break;
            case "ff": js = "seek(10)"; break;
            case "rewind30": js = "seek(-30)"; break;
            case "ff30": js = "seek(30)"; break;
            case "next": js = "next()"; break;
            case "prev": js = "prev()"; break;
            case "stop": runOnUiThread(this::finish); return true;
            default: return false;
        }
        runOnUiThread(() -> web.evaluateJavascript(js, null));
        return true;
    }

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
        // nothing on top of the video: the title shows only when Up or Down is pressed
        info.setAlpha(0f);
        setContentView(root);
        immersive();
        // a video whose owner blocks playing it inside apps (YouTube errors 100 / 101 / 150) is skipped: the next
        // match in the queue plays instead, still here, still full screen. The page tells us only when none is left.
        web.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void noneLeft() {
                runOnUiThread(() -> {
                    Ui.toast(PlayerActivity.this, "None of these videos can play inside JK Tube");
                    finish();
                });
            }
        }, "JkTube");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}#p{position:fixed;inset:0;width:100%;height:100%}</style></head>"
                + "<body><div id='p'></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                + "var queue=" + queueJson() + ",qi=0;"
                + "var player;function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',videoId:" + org.json.JSONObject.quote(id) + ","
                + "playerVars:{autoplay:1,controls:1,rel:0,playsinline:1,modestbranding:1,fs:0,start:" + new Prefs(this).savedPosition(id) + "},"
                + "events:{onReady:function(e){e.target.playVideo();},"
                // a queue of songs keeps going: when one ends, the next starts
                + "onStateChange:function(e){if(e.data===0&&qi+1<queue.length){qi++;player.loadVideoById(queue[qi]);}},"
                + "onError:function(e){qi++;if(qi<queue.length){player.loadVideoById(queue[qi]);}else if(window.JkTube){JkTube.noneLeft();}}}});}"
                + "function toggle(){if(!player)return;player.getPlayerState()==1?player.pauseVideo():player.playVideo();}"
                + "function seek(d){if(player)player.seekTo(Math.max(0,player.getCurrentTime()+d),true);}"
                + "function next(){if(player&&qi+1<queue.length){qi++;player.loadVideoById(queue[qi]);}else seek(30);}"
                + "function prev(){if(!player)return;if(player.getCurrentTime()<5&&qi>0){qi--;player.loadVideoById(queue[qi]);}else player.seekTo(0,true);}"
                + "</script></body></html>";
        web.loadDataWithBaseURL("https://thalaivar.tv/", html, "text/html", "utf-8", null);
    }

    /** The queue as a JS array literal: only valid 11-character video ids, each safely quoted. */
    private String queueJson() {
        java.util.ArrayList<String> q = getIntent().getStringArrayListExtra("queue");
        org.json.JSONArray a = new org.json.JSONArray();
        a.put(getIntent().getStringExtra("id"));
        if (q != null) for (String s : q) {
            if (s != null && s.matches("^[A-Za-z0-9_-]{11}$") && !s.equals(getIntent().getStringExtra("id"))) a.put(s);
        }
        return a.toString();
    }

    /** Full screen with no system bars, coming back after any popup. */
    private void immersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();
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
        if (showing == this) showing = null;
        // remember where it stopped, for Continue watching on the home screen
        String title = getIntent().getStringExtra("title");
        Prefs prefs = new Prefs(this);
        web.evaluateJavascript("(function(){if(!player||!player.getCurrentTime)return '';var d=player.getVideoData?player.getVideoData():{};"
                + "return (player.getCurrentTime()|0)+','+(player.getDuration()|0)+','+(d.video_id||'');})()", v -> {
            if (v == null) return;
            String[] p = v.replace("\"", "").split(",");
            if (p.length < 3 || !p[2].matches("^[A-Za-z0-9_-]{11}$")) return;
            try {
                prefs.saveProgress(p[2], title, Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            } catch (NumberFormatException ignored) {
                // nothing to keep
            }
        });
        web.evaluateJavascript("player&&player.pauseVideo&&player.pauseVideo()", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showing = this;
        String t = getIntent().getStringExtra("title");
        nowTitle = t == null ? "" : t;
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
