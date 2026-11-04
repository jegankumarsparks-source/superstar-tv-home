package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * J.A.R.V.I.S's face, an original character: a glossy golden sphere with big eyes, sparkles and blush.
 * It bobs and turns a little (the features slide across the sphere, so it reads as 3D), blinks, looks
 * around, and shows what it is doing: ears of light pulse while LISTENING, eyes drift up with orbiting
 * dots while THINKING, the mouth moves while SPEAKING, and it smiles when IDLE.
 */
class FaceView extends View {
    static final int IDLE = 0, LISTENING = 1, THINKING = 2, SPEAKING = 3, HAPPY = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final Random rnd = new Random(3);
    private int state = IDLE;
    private float t, blink = 1f, lookX, lookY, wantX, wantY;
    private long last, nextBlink, nextLook;
    private final boolean calm;

    FaceView(Context c) {
        super(c);
        calm = new Prefs(c).motion() == Prefs.LOW;
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setState(int s) {
        state = s;
        if (s == THINKING) {
            wantX = 0.35f;
            wantY = -0.55f;
        } else if (s == LISTENING || s == SPEAKING) {
            wantX = 0;
            wantY = 0;
        }
        invalidate();
    }

    private void tick(long now) {
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        t += dt;
        if (now > nextBlink) {
            blink = 0f;
            nextBlink = now + (long) ((2.4 + rnd.nextFloat() * 3.2) * 1e9);
        }
        blink = Math.min(1f, blink + dt * 7f);
        if (state == IDLE && now > nextLook) {
            wantX = (rnd.nextFloat() - 0.5f) * 0.9f;
            wantY = (rnd.nextFloat() - 0.5f) * 0.5f;
            nextLook = now + (long) ((1.5 + rnd.nextFloat() * 2.5) * 1e9);
        }
        float k = Math.min(1f, dt * 4f);
        lookX += (wantX - lookX) * k;
        lookY += (wantY - lookY) * k;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        tick(now);
        float w = getWidth(), h = getHeight();
        float R = Math.min(w, h) * 0.34f;
        float bob = calm ? 0 : (float) Math.sin(t * 1.6) * R * 0.04f;
        float cx = w / 2f, cy = h / 2f + bob;
        float d = getResources().getDisplayMetrics().density;

        // aura: its colour says what it is doing
        int aura = state == LISTENING ? 0x6633E1FF : state == THINKING ? 0x66B388FF : state == SPEAKING ? 0x66FFD54F : 0x40FFC870;
        float pulse = state == LISTENING ? 1.12f + 0.06f * (float) Math.sin(t * 8) : 1.08f + 0.02f * (float) Math.sin(t * 2);
        // kept inside the view, so the glow never shows a clipped square edge
        float auraR = Math.min(R * 1.5f * pulse, Math.min(w, h) / 2f - bob);
        p.setShader(new RadialGradient(cx, cy, auraR, new int[]{aura, aura & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, auraR, p);

        // listening: ears of light either side
        if (state == LISTENING) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 3; i++) {
                float ph = (t * 1.6f + i / 3f) % 1f;
                float rr = R * (1.08f + ph * 0.45f);
                p.setStrokeWidth(d * 3);
                p.setColor(((int) ((1 - ph) * 200) << 24) | 0x33E1FF);
                r.set(cx - rr, cy - rr, cx + rr, cy + rr);
                c.drawArc(r, 150, 60, false, p);
                c.drawArc(r, -30, 60, false, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        // the sphere: warm gold with a lit top-left and a shaded rim
        float lx = cx - R * 0.35f + lookX * R * 0.1f, ly = cy - R * 0.4f + lookY * R * 0.1f;
        p.setShader(new RadialGradient(lx, ly, R * 1.6f, new int[]{0xFFFFF3C4, 0xFFFFD35C, 0xFFE09A1E, 0xFF8A4E08},
                new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, p);
        // rim light and gloss
        p.setShader(new RadialGradient(cx, cy, R, new int[]{0x00000000, 0x00000000, 0x55FFFFFF}, new float[]{0f, 0.86f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, R, p);
        p.setShader(new LinearGradient(0, cy - R, 0, cy - R * 0.2f, 0xAAFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        r.set(cx - R * 0.62f, cy - R * 0.9f, cx + R * 0.2f, cy - R * 0.35f);
        c.save();
        c.rotate(-18, r.centerX(), r.centerY());
        c.drawOval(r, p);
        c.restore();
        p.setShader(null);

        // the features slide with the look direction, so the sphere seems to turn
        float fx = cx + lookX * R * 0.28f, fy = cy + lookY * R * 0.22f;
        float squash = 1f - Math.abs(lookX) * 0.12f;

        // cheeks
        p.setColor(0x55FF6B81);
        r.set(fx - R * 0.72f, fy + R * 0.12f, fx - R * 0.36f, fy + R * 0.34f);
        c.drawOval(r, p);
        r.set(fx + R * 0.36f, fy + R * 0.12f, fx + R * 0.72f, fy + R * 0.34f);
        c.drawOval(r, p);

        // eyes: big rounded, with two sparkles each; blinking squeezes them; happy turns them into arcs
        float ew = R * 0.21f * squash, eh = R * 0.3f * Math.max(0.08f, blink);
        float ex = R * 0.34f, ey = fy - R * 0.1f;
        for (int s = -1; s <= 1; s += 2) {
            float ecx = fx + s * ex;
            if (state == HAPPY) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(R * 0.07f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(0xFF2A1A3A);
                r.set(ecx - ew, ey - ew * 0.6f, ecx + ew, ey + ew * 1.2f);
                c.drawArc(r, 200, 140, false, p);
                p.setStyle(Paint.Style.FILL);
                continue;
            }
            p.setColor(0xFF2A1A3A);
            r.set(ecx - ew, ey - eh, ecx + ew, ey + eh);
            c.drawOval(r, p);
            if (blink > 0.4f) {
                float px = ecx + lookX * ew * 0.25f, py = ey + lookY * eh * 0.25f;
                p.setColor(0xFF4FC3F7);
                r.set(px - ew * 0.55f, py - eh * 0.2f, px + ew * 0.55f, py + eh * 0.75f);
                c.drawOval(r, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(px - ew * 0.3f, py - eh * 0.4f, ew * 0.32f, p);
                c.drawCircle(px + ew * 0.32f, py + eh * 0.25f, ew * 0.14f, p);
            }
        }

        // mouth
        float mx = fx, my = fy + R * 0.38f;
        if (state == SPEAKING) {
            float open = 0.35f + 0.65f * Math.abs((float) (Math.sin(t * 13) * Math.sin(t * 5.3 + 1)));
            p.setColor(0xFF5A1E2E);
            r.set(mx - R * 0.17f, my - R * 0.06f, mx + R * 0.17f, my + R * 0.06f + R * 0.2f * open);
            c.drawRoundRect(r, R * 0.15f, R * 0.15f, p);
            p.setColor(0xFFFF8FA3);
            r.set(mx - R * 0.1f, my + R * 0.06f * open, mx + R * 0.1f, my + R * 0.06f + R * 0.18f * open);
            c.drawOval(r, p);
        } else if (state == LISTENING) {
            p.setColor(0xFF5A1E2E);
            c.drawCircle(mx, my + R * 0.03f, R * 0.07f, p);
        } else if (state == THINKING) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(R * 0.05f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF5A1E2E);
            path.reset();
            path.moveTo(mx - R * 0.14f, my + R * 0.04f);
            path.quadTo(mx, my - R * 0.03f, mx + R * 0.12f, my + R * 0.05f);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) {
                double a = t * 3 + i * 2.09;
                float ox = cx + (float) Math.cos(a) * R * 1.25f, oy = cy - R * 0.2f + (float) Math.sin(a) * R * 0.35f;
                p.setColor(0xCCB388FF);
                c.drawCircle(ox, oy, R * 0.07f, p);
            }
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(R * 0.055f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF5A1E2E);
            r.set(mx - R * 0.2f, my - R * 0.16f, mx + R * 0.2f, my + R * 0.12f);
            c.drawArc(r, 20, 140, false, p);
            p.setStyle(Paint.Style.FILL);
        }

        // speaking: sound rings from the mouth side
        if (state == SPEAKING && !calm) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d * 2.5f);
            for (int i = 0; i < 3; i++) {
                float ph = (t * 1.3f + i / 3f) % 1f;
                p.setColor(((int) ((1 - ph) * 180) << 24) | 0xFFD54F);
                float rr = R * (1.1f + ph * 0.4f);
                r.set(cx - rr, cy - rr, cx + rr, cy + rr);
                c.drawArc(r, 20, 50, false, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        if (isShown() && !calm) postInvalidateOnAnimation();
        else if (isShown()) postInvalidateDelayed(80);
    }
}
