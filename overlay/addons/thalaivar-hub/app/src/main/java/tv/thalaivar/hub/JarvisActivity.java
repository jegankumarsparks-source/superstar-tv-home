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
        LiveJarvis.warm(this);
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
        heard.setText("“" + text.trim() + "”");
        JarvisBrain.ask(this, text.trim(), new JarvisBrain.Screen() {
            @Override public void thinking() {
                if (isDestroyed()) return;
                said.setText("…");
                orb.setState(FaceView.THINKING);
            }
            @Override public void words(String soFar) {
                if (isDestroyed()) return;
                said.setText(soFar);
                orb.setState(FaceView.SPEAKING);
            }
            @Override public void done(String all) {
                if (isDestroyed()) return;
                if (!all.isEmpty()) said.setText(all);
                orb.setState(FaceView.HAPPY);
                main.postDelayed(() -> orb.setState(FaceView.IDLE), 1400);
            }
        });
    }

    @Override
    protected void onDestroy() {
        Voice.stop();
        LiveJarvis.stopAudio();
        main.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
