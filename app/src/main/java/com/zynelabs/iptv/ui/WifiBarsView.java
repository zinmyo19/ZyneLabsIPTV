package com.zynelabs.iptv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

/**
 * Buffering indicator in the style of a wifi icon: a dot with three arcs
 * radiating upward, lighting up one by one. Amber = app theme primary.
 */
public class WifiBarsView extends View {
    private static final int AMBER = 0xFFFFAA33;      // theme primary
    private static final int AMBER_DIM = 0x2EFFAA33;  // unlit arc

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int lit = 0; // arcs lit: 0..3 (dot is always lit)

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            lit = (lit + 1) % 4;
            if (getVisibility() == VISIBLE) invalidate();
            handler.postDelayed(this, 300);
        }
    };

    public WifiBarsView(Context ctx) { super(ctx); }
    public WifiBarsView(Context ctx, AttributeSet a) { super(ctx, a); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override public void setVisibility(int v) {
        super.setVisibility(v);
        if (v == VISIBLE) start();
        else stop();
    }

    private void start() {
        handler.removeCallbacks(tick);
        lit = 0;
        handler.post(tick);
    }

    private void stop() {
        handler.removeCallbacks(tick);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f;
        float cy = h * 0.86f;
        float unit = Math.min(w / 112f, h / 84f);

        // dot (always lit)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(AMBER);
        c.drawCircle(cx, cy, 5.5f * unit, paint);

        // three arcs radiating upward, lighting one by one
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(8f * unit);
        for (int i = 0; i < 3; i++) {
            float r = (20f + 17f * i) * unit;
            paint.setColor(i < lit ? AMBER : AMBER_DIM);
            RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
            // 225° -> 315°: arc centered on 12 o'clock (upward)
            c.drawArc(oval, 225f, 90f, false, paint);
        }
    }
}
