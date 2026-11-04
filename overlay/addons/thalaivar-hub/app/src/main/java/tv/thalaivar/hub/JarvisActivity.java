package tv.thalaivar.hub;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JARVIS on the TV: ask from the phone remote (or type), Gemini understands, the TV answers aloud and does it: play a video in
 * JK Tube, open a film, an app, the news, the photos or the screensaver. The conversation keeps its last
 * few turns so follow-ups work ("play the trailer of that one").
 */
public class JarvisActivity extends Activity {
    /** J.A.R.V.I.S's latest words and when, for the phone remote's chat. */
    static volatile String lastSaid = "";
    static volatile long lastSaidAt;

    private static void remember(String words) {
        lastSaid = words == null ? "" : words;
        lastSaidAt = System.currentTimeMillis();
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String[]> history = new ArrayList<>();
    private FaceView orb;
    private TextView heard, said, hint;
    private EditText typed;
    private TextToSpeech tts;
    private boolean ttsReady;
    private final List<ResolveInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // a compact glass panel floating at the bottom right; whatever was playing stays visible behind it
        FrameLayout root = new FrameLayout(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 22), Ui.dp(this, 14));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xEE141B2E, 0xEE0B0F1A, 0xEE1A1206});
        glass.setCornerRadius(Ui.dp(this, 30));
        glass.setStroke(Ui.dp(this, 1), 0x88FFD35C);
        panel.setBackground(glass);
        panel.setElevation(Ui.dp(this, 24));
        orb = new FaceView(this);
        panel.addView(orb, new LinearLayout.LayoutParams(Ui.dp(this, 170), Ui.dp(this, 170)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 6), 0, 0, 0);
        col.addView(new NameView(this));
        heard = Ui.text(this, "", 14, 0xFF8FD8FF, false);
        heard.setMaxLines(2);
        col.addView(heard);
        said = Ui.text(this, Gemini.hasKey() ? "Hi Thalaiva! Talk to me from your phone (scan the QR), or type here. Try “play Jailer 2 trailer”."
                : "Add a Gemini key to secrets.properties to switch me on.", 17, Ui.TEXT, false);
        said.setMaxLines(4);
        said.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        col.addView(said);
        LinearLayout row = Ui.buttonRow(this, col);
        typed = new EditText(this);
        typed.setHint("type…");
        typed.setHintTextColor(0x88FFFFFF);
        typed.setTextColor(Ui.TEXT);
        typed.setTextSize(15);
        typed.setSingleLine(true);
        typed.setTypeface(Ui.montserrat(this));
        typed.setImeOptions(EditorInfo.IME_ACTION_SEND);
        typed.setMinWidth(Ui.dp(this, 170));
        typed.setPadding(Ui.dp(this, 16), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        typed.setBackground(Ui.round(0x33FFFFFF, Ui.dp(this, 22), Ui.dp(this, 1), 0x55FFDE78));
        typed.setOnEditorActionListener((v, a, e) -> {
            String q = typed.getText().toString();
            typed.setText("");
            handle(q);
            return true;
        });
        row.addView(typed);
        hint = Ui.label(this, "📱 phone = voice · BACK close · Gemini", 12, Ui.DIM);
        col.addView(hint);
        // the phone remote: a QR code that pairs it (mic, D-pad, volume, apps), always on the panel
        PhoneMic.start(this);
        String pairing = PhoneMic.pairingUrl(this);
        android.graphics.Bitmap qr = pairing == null ? null : qrCode(pairing, Ui.dp(this, 132));
        if (qr != null) {
            LinearLayout qrBox = new LinearLayout(this);
            qrBox.setOrientation(LinearLayout.VERTICAL);
            qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
            qrBox.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 6));
            qrBox.setBackground(Ui.round(0xFFFFFFFF, Ui.dp(this, 16), 0, 0));
            android.widget.ImageView qv = new android.widget.ImageView(this);
            qv.setImageBitmap(qr);
            qrBox.addView(qv, new LinearLayout.LayoutParams(Ui.dp(this, 132), Ui.dp(this, 132)));
            TextView cap = Ui.label(this, "SCAN · PHONE REMOTE", 11, 0xFF1A1206);
            qrBox.addView(cap);
            LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(-2, -2);
            qlp.leftMargin = Ui.dp(this, 14);
            panel.addView(qrBox, qlp);
        } else {
            TextView phone = Ui.label(this, "📱 Phone remote: connect the TV to Wi-Fi", 13, 0xFF8FD8FF);
            col.addView(phone);
        }
        panel.addView(col, new LinearLayout.LayoutParams(Ui.dp(this, 360), -2));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        plp.setMargins(0, 0, Ui.dp(this, 36), Ui.dp(this, 30));
        root.addView(panel, plp);
        setContentView(root);
        // the panel springs up from below
        if (new Prefs(this).motion() > Prefs.LOW) {
            panel.setTranslationY(Ui.dp(this, 120));
            panel.setScaleX(0.85f);
            panel.setScaleY(0.85f);
            panel.setAlpha(0f);
            panel.animate().translationY(0).scaleX(1f).scaleY(1f).alpha(1f).setDuration(420)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
        }

        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        apps.addAll(getPackageManager().queryIntentActivities(q, 0));
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(new Locale("en", "IN"));
                Voice.pickBestVoice(tts, new Locale("en", "IN"));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { main.post(() -> orb.setState(FaceView.SPEAKING)); }
                    @Override public void onDone(String id) {
                        main.post(() -> {
                            orb.setState(FaceView.HAPPY);
                            main.postDelayed(() -> orb.setState(FaceView.IDLE), 1400);
                        });
                    }
                    @Override public void onError(String id) { main.post(() -> orb.setState(FaceView.IDLE)); }
                });
            }
        });
        Voice.warm(this);
        String prompt = getIntent().getStringExtra("q");
        if (prompt != null) handle(prompt);
        else typed.requestFocus();
    }

    /** The pairing address as a QR code (ZXing), black on white with a quiet border. */
    static android.graphics.Bitmap qrCode(String text, int size) {
        try {
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints = new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 1);
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
            com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter()
                    .encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            return null;
        }
    }

    /** A new request from the phone remote while the panel is open. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String q = intent.getStringExtra("q");
        if (q != null) handle(q);
    }

    private void handle(String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (!Gemini.hasKey()) {
            said.setText("Add a Gemini key to secrets.properties to switch me on.");
            return;
        }
        heard.setText("“" + text.trim() + "”");
        // plain commands happen at once, on the TV; only questions wait for Gemini
        QuickCommand quick = QuickCommand.parse(text);
        if (quick != null) {
            said.setText(quick.say);
            remember(quick.say);
            // the action happens at once; the human voice confirms it (instant after the first time, from the cache)
            if (new Prefs(this).speakReplies()) speak(quick.voice, false);
            else main.postDelayed(() -> orb.setState(FaceView.HAPPY), 600);
            Gemini.Reply r = new Gemini.Reply();
            r.action = quick.action;
            r.query = quick.query;
            r.say = quick.say;
            main.postDelayed(() -> act(r), 150);
            return;
        }
        said.setText("…");
        orb.setState(FaceView.THINKING);
        history.add(new String[]{"user", text.trim()});
        while (history.size() > 8) history.remove(0);
        List<String> names = new ArrayList<>();
        for (ResolveInfo r : apps) names.add(r.loadLabel(getPackageManager()).toString());
        List<String[]> turn = new ArrayList<>(history);
        long asked = android.os.SystemClock.uptimeMillis();
        Ui.WORK.execute(() -> {
            Gemini.Reply r;
            boolean ok = true;
            // his films' facts come straight from the TV's own film data: no AI wait
            String film = FilmAnswer.answer(this, text);
            if (film != null) {
                r = new Gemini.Reply();
                r.say = film;
            } else {
                try {
                    r = Gemini.ask(this, turn, names);
                } catch (Exception e) {
                    ok = false;
                    r = new Gemini.Reply();
                    r.say = Net.online(this) ? "Sorry, I couldn't reach Gemini just now. Try again." : "The TV is offline right now.";
                }
            }
            Gemini.Reply reply = r;
            boolean success = ok;
            // a slow answer is spoken at once with the TV's voice instead of waiting again for the natural one
            boolean waitedLong = android.os.SystemClock.uptimeMillis() - asked > 5000;
            main.post(() -> respond(reply, success, waitedLong));
        });
    }

    private void respond(Gemini.Reply r, boolean ok) {
        respond(r, ok, false);
    }

    private void respond(Gemini.Reply r, boolean ok, boolean fastVoice) {
        if (isFinishing() || isDestroyed()) return;
        // only real answers go back to Gemini as context; a failed turn is dropped so it doesn't poison the next one
        if (ok && !r.say.isEmpty()) history.add(new String[]{"model", r.say});
        else if (!history.isEmpty() && "user".equals(history.get(history.size() - 1)[0])) history.remove(history.size() - 1);
        said.setText(r.say);
        remember(r.say);
        orb.setState(FaceView.IDLE);
        if (!r.say.isEmpty() && new Prefs(this).speakReplies()) speak(r.say, fastVoice);
        // act after the reply has started, so the answer is heard before the screen changes
        main.postDelayed(() -> act(r), "none".equals(r.action) ? 0 : 1800);
    }

    /** Incremented per reply, so a slow voice for an old reply never plays over a newer one. */
    private int speechTurn;

    /**
     * The reply in Gemini's natural human voice; if that hasn't arrived within 7 s (slow, or out of quota),
     * the TV's best installed voice reads it instead.
     */
    private void speak(String text, boolean fastVoice) {
        int turn = ++speechTurn;
        boolean[] done = {false};
        Runnable fallback = () -> {
            if (done[0] || turn != speechTurn || isFinishing()) return;
            done[0] = true;
            if (!ttsReady) return;
            boolean tamil = text.codePoints().anyMatch(cp -> cp >= 0x0B80 && cp <= 0x0BFF);
            tts.setLanguage(tamil ? new Locale("ta", "IN") : new Locale("en", "IN"));
            Voice.pickBestVoice(tts, tamil ? new Locale("ta", "IN") : new Locale("en", "IN"));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis");
        };
        // always the human voice; the TV's own voice only if Google's voice service doesn't answer within 10 s
        main.postDelayed(fallback, 10000);
        Ui.WORK.execute(() -> {
            byte[] pcm = Voice.natural(this, text);
            main.post(() -> {
                if (done[0] || turn != speechTurn || isFinishing() || isDestroyed()) return;
                if (pcm == null) {
                    main.removeCallbacks(fallback);
                    fallback.run();
                    return;
                }
                done[0] = true;
                main.removeCallbacks(fallback);
                long ms = Voice.play(pcm);
                orb.setState(FaceView.SPEAKING);
                main.postDelayed(() -> {
                    if (turn != speechTurn) return;
                    orb.setState(FaceView.HAPPY);
                    main.postDelayed(() -> { if (turn == speechTurn) orb.setState(FaceView.IDLE); }, 1400);
                }, ms + 150);
            });
        });
    }

    private void act(Gemini.Reply r) {
        if (isFinishing() || isDestroyed()) return;
        String q = r.query == null ? "" : r.query.trim();
        if (q.isEmpty() && ("play".equals(r.action) || "film".equals(r.action) || "open_app".equals(r.action))) return;
        switch (r.action) {
            case "play":
                playFirst(q);
                break;
            case "tube":
                startActivity(new Intent(this, TubeActivity.class));
                break;
            case "film":
                openFilm(q);
                break;
            case "open_app":
                openApp(q);
                break;
            case "news":
                startActivity(new Intent(this, NewsActivity.class));
                break;
            case "photos":
                startActivity(new Intent(this, PhotoActivity.class));
                break;
            case "screensaver":
                startActivity(new Intent(this, SaverActivity.class));
                break;
            default:
                break;
        }
    }

    /** With a YouTube key the top result plays straight away; without one JK Tube hands the search to YouTube. */
    private void playFirst(String q) {
        if (q.isEmpty()) return;
        if (!YtApi.hasKey()) {
            // no YouTube key: a film's official trailer still plays straight away in JK Tube
            Ui.WORK.execute(() -> {
                YtApi.Video v = YtApi.filmTrailer(this, q);
                main.post(() -> {
                    if (v != null) TubeActivity.play(this, v.id, v.title);
                    else startActivity(new Intent(this, TubeActivity.class).putExtra("q", q));
                });
            });
            return;
        }
        Ui.WORK.execute(() -> {
            try {
                // songs: a queue of 10 from the Music category that plays one after another; anything else: the top
                // 5 matches, the first plays at once and the others are backups if one won't play here
                boolean songs = q.toLowerCase(Locale.ROOT).matches(".*\\b(songs?|paattu|paadal|music|hits|bgm|album)\\b.*");
                List<YtApi.Video> l = songs ? YtApi.searchMusic(this, q, 10) : YtApi.search(this, q, 5);
                if (songs && l.isEmpty()) l = YtApi.search(this, q, 5);
                if (!l.isEmpty()) {
                    java.util.ArrayList<String> queue = TubeActivity.ids(l);
                    String title = l.get(0).title;
                    main.post(() -> TubeActivity.playQueue(this, queue, title));
                    return;
                }
            } catch (Exception ignored) {
                // fall through to JK Tube's search
            }
            main.post(() -> startActivity(new Intent(this, TubeActivity.class).putExtra("q", q)));
        });
    }

    private void openFilm(String title) {
        Ui.WORK.execute(() -> {
            try {
                String want = title.toLowerCase(Locale.ROOT);
                for (Films.Film f : Films.resolve(this, Films.curated())) {
                    if (f.id > 0 && (f.title.toLowerCase(Locale.ROOT).contains(want) || want.contains(f.title.toLowerCase(Locale.ROOT)))) {
                        main.post(() -> startActivity(new Intent(this, MovieActivity.class).putExtra("id", f.id).putExtra("tamil", f.tamil)));
                        return;
                    }
                }
                for (Films.Film f : Films.all(this)) {
                    if (f.title.toLowerCase(Locale.ROOT).contains(want)) {
                        main.post(() -> startActivity(new Intent(this, MovieActivity.class).putExtra("id", f.id)));
                        return;
                    }
                }
            } catch (Exception ignored) {
                // nothing found
            }
            main.post(() -> said.setText("I couldn't find " + title + "."));
        });
    }

    private void openApp(String name) {
        String want = name.toLowerCase(Locale.ROOT).trim();
        // exact name first, then a name that contains what was said, then a name said inside a longer phrase
        for (int pass = 0; pass < 3; pass++) {
            for (ResolveInfo r : apps) {
                String label = r.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT).trim();
                boolean hit = pass == 0 ? label.equals(want)
                        : pass == 1 ? label.contains(want)
                        : label.length() >= 3 && want.contains(label);
                if (hit) {
                    Ui.launchApp(this, r.activityInfo.packageName);
                    return;
                }
            }
        }
        said.setText(name + " isn't installed on this TV.");
    }

    @Override
    protected void onDestroy() {
        Voice.stop();
        main.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
