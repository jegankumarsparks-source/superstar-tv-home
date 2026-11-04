package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;

/**
 * The assistant's name as its own lettering: wide-spaced capitals that ride a slow wave one after another,
 * with a gold-to-cyan shimmer sweeping through them, and a dot between letters that glows in turn.
 */
class NameView extends View {
    private static final String NAME = "JARVIS";
    private final Paint letters = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private LinearGradient shimmer;
    private float t;
    private long last;
    private final boolean calm;

    NameView(Context c) {
        super(c);
        letters.setTypeface(Ui.bebas(c));
        letters.setTextAlign(Paint.Align.CENTER);
        calm = new Prefs(c).motion() == Prefs.LOW;
    }

    @Override
    protected void onMeasure(int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension(resolveSize((int) (260 * d), w), (int) (46 * d));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        letters.setTextSize(h * 0.82f);
        shimmer = new LinearGradient(0, 0, w * 0.6f, 0, new int[]{0xFFFFD35C, 0xFFFFF3C4, 0xFF4FE3FF, 0xFFFFD35C},
                null, Shader.TileMode.MIRROR);
        letters.setShader(shimmer);
        letters.setShadowLayer(h * 0.18f, 0, 0, 0x88FFC14D);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        t += dt;
        float w = getWidth(), h = getHeight();
        m.setTranslate((t * w * 0.35f) % (w * 1.2f), 0);
        shimmer.setLocalMatrix(m);
        float step = w / NAME.length();
        for (int i = 0; i < NAME.length(); i++) {
            float x = step * (i + 0.5f);
            float wave = calm ? 0 : (float) Math.sin(t * 2.4 - i * 0.7) * h * 0.08f;
            c.drawText(String.valueOf(NAME.charAt(i)), x, h * 0.8f + wave, letters);
            if (i < NAME.length() - 1) {
                float glow = 0.35f + 0.65f * Math.max(0f, (float) Math.sin(t * 2.4 - i * 0.7 - 0.6));
                dot.setColor(((int) (glow * 255) << 24) | 0x4FE3FF);
                c.drawCircle(x + step / 2f, h * 0.62f + wave, h * 0.05f, dot);
            }
        }
        if (isShown() && !calm) postInvalidateOnAnimation();
    }
}
