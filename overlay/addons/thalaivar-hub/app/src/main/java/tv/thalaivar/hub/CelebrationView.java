package tv.thalaivar.hub;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.util.Random;

/**
 * The fan celebration: slow stage-light rays turning behind the title, and bursts of gold and red confetti
 * that rise and fall, like a first-day-first-show crowd. Original drawing, no footage or music.
 */
class CelebrationView extends View {
    private final Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float[] bx = new float[140], by = new float[140], vx = new float[140], vy = new float[140], spin = new float[140];
    private final int[] col = new int[140];
    private final Random rnd = new Random(11);
    private final int level;
    private float angle;
    private long last, nextBurst;
    private int count;

    CelebrationView(Context c) {
        super(c);
        level = new Prefs(c).motion();
        ray.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private void burst(int w, int h) {
        int n = level == Prefs.HIGH ? 70 : 34;
        float cx = w * (0.2f + rnd.nextFloat() * 0.6f);
        int[] colours = {Ui.GOLD, Ui.GOLD_HI, 0xFFE53935, 0xFFFFFFFF, 0xFF7E57C2};
        for (int k = 0; k < n; k++) {
            int i = count++ % bx.length;
            bx[i] = cx;
            by[i] = h * 0.95f;
            double a = -Math.PI / 2 + (rnd.nextDouble() - 0.5) * 1.3;
            float sp = h * (0.7f + rnd.nextFloat() * 0.6f);
            vx[i] = (float) Math.cos(a) * sp;
            vy[i] = (float) Math.sin(a) * sp;
            spin[i] = rnd.nextFloat() * 360f;
            col[i] = colours[rnd.nextInt(colours.length)];
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        angle += dt * 6f;
        // stage-light rays from below the title
        float cx = w / 2f, cy = h * 0.62f;
        for (int i = 0; i < 14; i++) {
            double a = Math.toRadians(angle + i * (360.0 / 14));
            double s = Math.toRadians(4.5);
            float len = Math.max(w, h);
            path.reset();
            path.moveTo(cx, cy);
            path.lineTo(cx + (float) Math.cos(a - s) * len, cy + (float) Math.sin(a - s) * len);
            path.lineTo(cx + (float) Math.cos(a + s) * len, cy + (float) Math.sin(a + s) * len);
            path.close();
            ray.setColor(i % 2 == 0 ? 0x16D4AF37 : 0x0EFFDE78);
            c.drawPath(path, ray);
        }
        if (level == Prefs.LOW) return;
        if (now > nextBurst) {
            burst(w, h);
            nextBurst = now + (long) ((level == Prefs.HIGH ? 1.6 : 2.8) * 1e9);
        }
        float g = h * 1.1f;
        int live = Math.min(count, bx.length);
        for (int i = 0; i < live; i++) {
            if (by[i] > h * 1.05f) continue;
            vy[i] += g * dt;
            vx[i] *= 0.99f;
            bx[i] += vx[i] * dt;
            by[i] += vy[i] * dt;
            spin[i] += dt * 300f;
            bit.setColor(col[i]);
            c.save();
            c.rotate(spin[i], bx[i], by[i]);
            float s = getResources().getDisplayMetrics().density * 4;
            c.drawRect(bx[i] - s, by[i] - s * 0.5f, bx[i] + s, by[i] + s * 0.5f, bit);
            c.restore();
        }
        if (isShown()) postInvalidateOnAnimation();
    }
}
