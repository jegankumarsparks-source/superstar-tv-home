package tv.thalaivar.hub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
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
 * JARVIS on the TV: speak (or type), Gemini understands, the TV answers aloud and does it: play a video in
 * JK Tube, open a film, an app, the news, the photos or the screensaver. The conversation keeps its last
 * few turns so follow-ups work ("play the trailer of that one").
 */
public class JarvisActivity extends Activity {
    private static final int VOICE = 51;
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
        said = Ui.text(this, Gemini.hasKey() ? "Hi! Press OK and speak. Try “play Jailer 2 trailer”, “open Netflix” or ask me anything."
                : "Add a Gemini key to secrets.properties to switch me on.", 17, Ui.TEXT, false);
        said.setMaxLines(4);
        said.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        col.addView(said);
        LinearLayout row = Ui.buttonRow(this, col);
        row.addView(Ui.button(this, "🎤  Speak", v -> listen()));
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
        hint = Ui.label(this, "OK speak · BACK close · Gemini", 12, Ui.DIM);
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
        if (getIntent().getBooleanExtra("over_google", false)) coverGoogle();
        String prompt = getIntent().getStringExtra("q");
        if (prompt != null) handle(prompt);
        // from the remote's mic button: listen at once, while the button is still held
        else if (Gemini.hasKey()) main.postDelayed(this::listen, getIntent().getBooleanExtra("hold", false) ? 0 : 400);
    }

    /**
     * Opened over Google's "not available" screen (the remote's mic button): the screen behind the panel goes
     * dark until Google's screen has closed itself in the background, then lights up again.
     */
    private void coverGoogle() {
        android.view.Window w = getWindow();
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams a = w.getAttributes();
        a.dimAmount = 1f;
        w.setAttributes(a);
        main.removeCallbacks(uncover);
        main.postDelayed(uncover, 6500);
    }

    private final Runnable uncover = () -> {
        android.view.WindowManager.LayoutParams a = getWindow().getAttributes();
        a.dimAmount = 0f;
        getWindow().setAttributes(a);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    };

    /** True while the panel is in front: the key service then leaves Google's voice engine alone. */
    static volatile boolean onScreen;

    @Override
    protected void onResume() {
        super.onResume();
        onScreen = true;
    }

    @Override
    protected void onPause() {
        // a little grace, so the engine's window appearing as the panel pauses is not mistaken for the mic button
        main.postDelayed(() -> { if (isFinishing() || !hasWindowFocus()) onScreen = false; }, 1500);
        super.onPause();
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

    private static final int MIC_PERMISSION = 52;
    private android.speech.SpeechRecognizer recognizer;

    /** The language extras both listening paths share. */
    private void putLanguage(Intent i) {
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        if (plainEnglish) {
            // the retry after a missing language pack: the language every engine has
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            return;
        }
        String lang = new Prefs(this).micLanguage();
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN".equals(lang) ? "ta-IN" : "en-IN");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN".equals(lang) ? "ta-IN" : "en-IN");
        if ("both".equals(lang)) i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ta-IN"});
    }

    /**
     * Listens inside the panel with the TV's own speech service: the remote's microphone opens, the face's
     * ears pulse, the words appear as they are heard. Asking for the microphone the first time; the old
     * hand-off to another app's voice screen stays only as a fallback.
     */
    private void listen() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
            return;
        }
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            listenWithVoiceScreen();
            return;
        }
        if (tts != null) tts.stop();
        Voice.stop();
        speechTurn++;
        if (recognizer == null) {
            // Google TV's own voice engine (Katniss, the one the Assistant button uses): online, Indian English and
            // Tamil. The on-device engine is only the fallback, as it may lack the language pack (error 13).
            android.content.ComponentName katniss = new android.content.ComponentName("com.google.android.katniss",
                    "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService");
            boolean hasKatniss;
            try {
                getPackageManager().getServiceInfo(katniss, 0);
                hasKatniss = true;
            } catch (Exception e) {
                hasKatniss = false;
            }
            recognizer = hasKatniss && !useDefaultEngine
                    ? android.speech.SpeechRecognizer.createSpeechRecognizer(this, katniss)
                    : android.speech.SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new android.speech.RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {
                    main.removeCallbacks(stalled);
                    stalls = 0;
                    orb.setState(FaceView.LISTENING);
                    heard.setText("🎤 Listening… hold the mic button while you speak");
                }
                @Override public void onBeginningOfSpeech() {
                    gotSpeech = true;
                    heard.setText("…");
                }
                @Override public void onRmsChanged(float rms) {
                    // a live level bar: if it never moves, no sound is reaching the TV
                    if (!listeningNow || gotPartial) return;
                    int bars = Math.max(0, Math.min(8, (int) ((rms + 2f) / 1.5f)));
                    heard.setText("🎤 " + "▮▮▮▮▮▮▮▮".substring(0, bars) + "▯▯▯▯▯▯▯▯".substring(bars));
                }
                @Override public void onBufferReceived(byte[] b) { }
                @Override public void onEndOfSpeech() { orb.setState(FaceView.THINKING); }
                @Override public void onPartialResults(Bundle p) {
                    ArrayList<String> r = p.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && !r.get(0).isEmpty()) {
                        gotPartial = true;
                        // keep the longest wording heard so far; engines sometimes shorten it before the end
                        if (r.get(0).length() >= lastPartial.length()) lastPartial = r.get(0);
                        heard.setText("“" + lastPartial + "”");
                    }
                }
                @Override public void onResults(Bundle p) {
                    listeningNow = false;
                    retried = false;
                    orb.setState(FaceView.IDLE);
                    quiet(false);
                    ArrayList<String> r = p.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
                    String fin = r != null && !r.isEmpty() ? r.get(0).trim() : "";
                    // of the engine's guesses, the first that is a command the TV knows ("open Netflix" over "often at flicks")
                    if (r != null && QuickCommand.parse(fin) == null) {
                        for (String alt : r) if (alt != null && QuickCommand.parse(alt) != null) { fin = alt.trim(); break; }
                    }
                    // whichever is fuller: the final words, or what was already shown while listening
                    String use = fin.length() >= lastPartial.trim().length() ? fin : lastPartial.trim();
                    if (!use.isEmpty()) handle(use);
                    else micTrouble(android.speech.SpeechRecognizer.ERROR_NO_MATCH);
                }
                @Override public void onError(int error) {
                    // the engine complaining about our own restart (cancel, then start) is not a real failure
                    if ((error == android.speech.SpeechRecognizer.ERROR_CLIENT || error == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                            && android.os.SystemClock.uptimeMillis() - restartedAt < 1000) return;
                    micTrouble(error);
                }
                @Override public void onEvent(int type, Bundle p) { }
            });
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        putLanguage(i);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        // give people time to start talking and to pause mid-sentence
        // a whole sentence: the remote's audio has small gaps between words, so a short silence must not end it
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L);
        orb.setState(FaceView.LISTENING);
        heard.setText("Starting the mic…");
        restartedAt = android.os.SystemClock.uptimeMillis();
        recognizer.cancel();
        gotSpeech = false;
        gotPartial = false;
        lastPartial = "";
        listeningNow = true;
        quiet(true);
        recognizer.startListening(i);
        main.removeCallbacks(stalled);
        main.postDelayed(stalled, 1800);
    }

    /** When listening was last (re)started on purpose. */
    private long restartedAt;

    /** Restarts done for an engine that never got ready; reset once it does. */
    private int stalls;

    /**
     * Google's engine on this TV sometimes never starts while its own "not available" screen is still closing:
     * no ready, no error. After 1.8 s it gets a fresh start (twice at most) instead of leaving J.A.R.V.I.S deaf.
     */
    private final Runnable stalled = () -> {
        if (!this.listeningNow || isFinishing() || stalls >= 2) return;
        stalls++;
        this.listeningNow = false;
        if (this.recognizer != null) this.recognizer.destroy();
        this.recognizer = null;
        listen();
    };

    private android.media.AudioFocusRequest focus;

    /** While listening, other sound on the TV pauses, so the TV's microphone hears the voice and not the film. */
    private void quiet(boolean on) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        if (on && focus == null) {
            focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .build();
            am.requestAudioFocus(focus);
        } else if (!on && focus != null) {
            am.abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    /** Set after the first engine fails to start: the next listen uses the other one, in plain English. */
    private boolean useDefaultEngine, plainEnglish;
    /** True from "speak now" until a result or an error: the remote's mic button must not restart it. */
    private boolean listeningNow;
    /** Sound and words heard in this round, and whether the one automatic retry has been used. */
    private boolean gotSpeech, gotPartial, retried;
    /** The fullest wording seen while listening, used when the engine's final result comes back short or empty. */
    private String lastPartial = "";

    /** What went wrong, in words the person can act on. */
    private void micTrouble(int error) {
        main.removeCallbacks(stalled);
        listeningNow = false;
        quiet(false);
        orb.setState(FaceView.IDLE);
        // words were already on screen when the engine gave up: answer those instead of an error
        if (!lastPartial.trim().isEmpty()) {
            String use = lastPartial.trim();
            lastPartial = "";
            handle(use);
            return;
        }
        // 12/13: language not supported / pack unavailable; 5 client, 8 busy: the engine itself. Try the other
        // engine once, in plain English, before telling the person anything.
        if ((error == 12 || error == 13 || error == android.speech.SpeechRecognizer.ERROR_CLIENT) && !plainEnglish) {
            plainEnglish = true;
            useDefaultEngine = !useDefaultEngine;
            if (recognizer != null) recognizer.destroy();
            recognizer = null;
            main.postDelayed(this::listen, 300);
            return;
        }
        boolean nothingHeard = error == android.speech.SpeechRecognizer.ERROR_NO_MATCH
                || error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        // heard nothing the first time: listen once more straight away instead of giving up
        if (nothingHeard && !retried) {
            retried = true;
            said.setText("I didn't catch that. Listening again — speak now.");
            main.postDelayed(this::listen, 500);
            return;
        }
        retried = false;
        String why;
        switch (error) {
            case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
            case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                why = gotSpeech ? "I heard you but couldn't make out the words. Try again, a little slower."
                        : "No sound reached me. Hold the mic button down the whole time you speak, then let go.";
                break;
            case android.speech.SpeechRecognizer.ERROR_AUDIO:
                why = "The microphone didn't open. Press the mic button again.";
                break;
            case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                why = "I need the microphone permission. Press OK and allow it.";
                break;
            case android.speech.SpeechRecognizer.ERROR_NETWORK:
            case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                why = "Speech needs the internet and the TV seems offline.";
                break;
            case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                why = "The mic is busy. Press OK to try again.";
                break;
            default:
                why = "Mic error " + error + ". Press OK to try again, or type below.";
        }
        heard.setText("");
        said.setText(why);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req != MIC_PERMISSION) return;
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) listen();
        else said.setText("Without the microphone I can't hear you. You can still type below.");
    }

    /** Fallback: hand the listening to the TV's own voice screen. */
    private void listenWithVoiceScreen() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String lang = new Prefs(this).micLanguage();
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN".equals(lang) ? "ta-IN" : "en-IN");
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ta-IN".equals(lang) ? "ta-IN" : "en-IN");
        if ("both".equals(lang)) {
            // English first, Tamil understood too (recognisers that support several languages use the list)
            i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{"ta-IN"});
        }
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask JARVIS");
        orb.setState(FaceView.LISTENING);
        try {
            startActivityForResult(i, VOICE);
        } catch (ActivityNotFoundException e) {
            orb.setState(FaceView.IDLE);
            said.setText("Voice input isn't available on this TV. Type instead.");
            typed.requestFocus();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != VOICE) return;
        orb.setState(FaceView.IDLE);
        if (res == RESULT_OK && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && !r.isEmpty()) handle(r.get(0));
        }
    }

    /** The remote's mic button while JARVIS is already open (the key service relaunches it): listen again. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("over_google", false)) coverGoogle();
        // the mic button pressed again: always a fresh listen, even if an old one looks stuck
        if (intent.getBooleanExtra("hold", false) && intent.getStringExtra("q") == null && Gemini.hasKey()) {
            listeningNow = false;
            stalls = 0;
            listen();
            return;
        }
        String q = intent.getStringExtra("q");
        if (q != null) handle(q);
        else if (Gemini.hasKey() && !listeningNow) listen();
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
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST)
                && !typed.hasFocus()) {
            listen();
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    protected void onDestroy() {
        quiet(false);
        if (recognizer != null) recognizer.destroy();
        Voice.stop();
        main.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
