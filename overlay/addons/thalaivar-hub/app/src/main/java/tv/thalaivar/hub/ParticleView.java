package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * Gold dust drifting up through the frame. The number of sparks follows the animation level, and on LOW
 * it draws one still frame and never animates, so it costs nothing while the remote is in use.
 */
class ParticleView extends View {
    private final float[] x, y, r, v, phase;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean animate;
    private final int colour;
    private long last;

    ParticleView(Context c, int colour) {
        super(c);
        this.colour = colour;
        int level = new Prefs(c).motion();
        int n = level == Prefs.HIGH ? 90 : level == Prefs.MEDIUM ? 45 : 20;
        animate = level > Prefs.LOW;
        x = new float[n];
        y = new float[n];
        r = new float[n];
        v = new float[n];
        phase = new float[n];
        Random rnd = new Random(7);
        for (int i = 0; i < n; i++) {
            x[i] = rnd.nextFloat();
            y[i] = rnd.nextFloat();
            r[i] = 1.2f + rnd.nextFloat() * 3.2f;
            v[i] = 0.01f + rnd.nextFloat() * 0.035f;
            phase[i] = rnd.nextFloat() * 6.28f;
        }
        paint.setColor(colour);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        float scale = getResources().getDisplayMetrics().density;
        for (int i = 0; i < x.length; i++) {
            if (animate) {
                y[i] -= v[i] * dt;
                phase[i] += dt * 1.4f;
                if (y[i] < -0.02f) {
                    y[i] = 1.02f;
                    x[i] = (x[i] + 0.37f) % 1f;
                }
            }
            float px = (x[i] + (float) Math.sin(phase[i]) * 0.006f) * w;
            float py = y[i] * h;
            float a = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(phase[i] * 1.7f));
            float rad = r[i] * scale;
            glow.setShader(new RadialGradient(px, py, rad * 4, (colour & 0x00FFFFFF) | ((int) (a * 70) << 24),
                    colour & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(px, py, rad * 4, glow);
            paint.setAlpha((int) (a * 255));
            c.drawCircle(px, py, rad, paint);
        }
        if (animate && isShown()) postInvalidateOnAnimation();
    }
}
