package online.pelipe.tv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Círculo de carga de puntos naranjas con cola que se desvanece y brillo suave. */
final class DotSpinner extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle = 0f;
    private ValueAnimator anim;

    DotSpinner(Context c) {
        super(c);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 360f);
        anim.setDuration(1300);
        anim.setInterpolator(new LinearInterpolator());
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                angle = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float ring = Math.min(cx, cy) * 0.78f;
        float maxR = Math.min(cx, cy) * 0.17f;
        int n = 18;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;                       // 0 = cabeza, 1 = cola
            double a = Math.toRadians(angle - i * 14f - 90f);
            float x = cx + (float) Math.cos(a) * ring;
            float y = cy + (float) Math.sin(a) * ring;
            float r = maxR * (1f - t * 0.82f);
            int alpha = (int) (255 * (1f - t * 0.9f));
            paint.setColor(0xFFFF5A36);
            paint.setAlpha((int) (alpha * 0.22f));
            cv.drawCircle(x, y, r * 2.1f, paint);         // brillo
            paint.setColor(0xFFFF7A4D);
            paint.setAlpha(alpha);
            cv.drawCircle(x, y, r, paint);                // punto
            paint.setColor(0xFFFFC9A8);
            paint.setAlpha((int) (alpha * 0.8f));
            cv.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.38f, paint); // destello
        }
    }
}
