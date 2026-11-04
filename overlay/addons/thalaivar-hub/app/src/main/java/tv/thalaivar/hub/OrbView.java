package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The assistant's face: a gold core in turning rings. Idle it breathes slowly; listening, the rings speed up
 * and brighten; thinking, an arc sweeps round; speaking, the core pulses.
 */
class OrbView extends View {
    static final int IDLE = 0, LISTENING = 1, THINKING = 2, SPEAKING = 3;
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int state = IDLE;
    private float t;
    private long last;

    OrbView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setState(int s) {
        state = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float speed = state == LISTENING ? 2.6f : state == THINKING ? 1.8f : 0.6f;
        t += dt * speed;
        float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) * 0.42f;
        float pulse = state == SPEAKING ? 1f + 0.08f * (float) Math.sin(t * 9) : 1f + 0.04f * (float) Math.sin(t * 2);
        float cr = r * pulse;
        core.setShader(new RadialGradient(cx, cy, cr * 1.9f, new int[]{0xFFFFF4C2, 0xFFFFDE78, 0xAAD4AF37, 0x00D4AF37},
                new float[]{0f, 0.35f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, cr * 1.9f, core);
        float d = getResources().getDisplayMetrics().density;
        int[] alphas = {state == IDLE ? 90 : 200, state == IDLE ? 60 : 160, state == IDLE ? 40 : 120};
        for (int i = 0; i < 3; i++) {
            float rr = r * (1.55f + i * 0.32f);
            box.set(cx - rr, cy - rr, cx + rr, cy + rr);
            ring.setStrokeWidth(d * (3 - i));
            ring.setColor((alphas[i] << 24) | 0xFFDE78);
            float start = (float) Math.toDegrees(t * (i % 2 == 0 ? 1 : -1.4f)) + i * 40;
            c.drawArc(box, start, 220 - i * 40, false, ring);
            c.drawArc(box, start + 250, 60, false, ring);
        }
        if (state == THINKING) {
            float rr = r * 1.25f;
            box.set(cx - rr, cy - rr, cx + rr, cy + rr);
            ring.setStrokeWidth(d * 4);
            ring.setColor(0xFFFF6B6B);
            c.drawArc(box, (float) Math.toDegrees(t * 3), 70, false, ring);
        }
        if (isShown()) postInvalidateOnAnimation();
    }
}
