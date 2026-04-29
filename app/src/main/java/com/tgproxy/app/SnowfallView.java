package com.tgproxy.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class SnowfallView extends View {

    private static final int   COUNT = 38;
    private static final int   FPS   = 30;

    private final Paint  paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd   = new Random();
    private final Handler ticker = new Handler(Looper.getMainLooper());

    private float[] x, y, speed, radius, alpha;
    private int     w = 0, h = 0;
    private boolean attached = false;

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (!attached || w == 0) return;
            for (int i = 0; i < COUNT; i++) {
                y[i] += speed[i];
                x[i] += (rnd.nextFloat() - 0.5f) * 0.6f;
                if (y[i] > h + radius[i]) {
                    y[i]      = -radius[i];
                    x[i]      = rnd.nextFloat() * w;
                    alpha[i]  = 0.3f + rnd.nextFloat() * 0.65f;
                    speed[i]  = 0.8f + rnd.nextFloat() * 2.4f;
                    radius[i] = 2f  + rnd.nextFloat() * 5f;
                }
            }
            invalidate();
            ticker.postDelayed(this, 1000 / FPS);
        }
    };

    public SnowfallView(Context ctx) { super(ctx); init(); }
    public SnowfallView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        x = new float[COUNT]; y = new float[COUNT];
        speed = new float[COUNT]; radius = new float[COUNT];
        alpha = new float[COUNT];
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw; h = nh;
        if (w == 0 || h == 0) return;
        for (int i = 0; i < COUNT; i++) {
            x[i]      = rnd.nextFloat() * w;
            y[i]      = rnd.nextFloat() * h;
            speed[i]  = 0.8f + rnd.nextFloat() * 2.4f;
            radius[i] = 2f  + rnd.nextFloat() * 5f;
            alpha[i]  = 0.3f + rnd.nextFloat() * 0.65f;
        }
    }

    @Override protected void onDraw(Canvas c) {
        for (int i = 0; i < COUNT; i++) {
            paint.setARGB((int)(alpha[i] * 255), 200, 230, 255);
            c.drawCircle(x[i], y[i], radius[i], paint);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        ticker.post(step);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        ticker.removeCallbacks(step);
    }
}
