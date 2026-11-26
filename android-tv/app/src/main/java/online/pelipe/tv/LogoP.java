package online.pelipe.tv;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** La "P" del logo de Pelipe (con su triángulo de play), dibujada en vector. */
final class LogoP extends View {
    private final Path p = new Path();
    private final Path tri = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float eye = 1f;          // 1 = play abierto, ~0.08 = guiño
    private float glowK = 0f;
    private boolean auto = false;
    private long winkFirst = 2500, winkEvery = 6500;
    private ValueAnimator winkAnim;
    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!auto || !isAttachedToWindow()) return;
            wink();
            postDelayed(this, winkEvery);
        }
    };

    /** Guiño automático periódico (para el logo de la barra superior). */
    LogoP autoWink() {
        auto = true;
        return this;
    }

    /** Guiño automático con tiempos propios (marca de agua de los videos: espaciado). */
    LogoP autoWink(long firstMs, long everyMs) {
        auto = true;
        winkFirst = firstMs;
        winkEvery = everyMs;
        return this;
    }

    /** El triángulo del play "cierra el ojo" y vuelve a abrirse con un pequeño brillo. */
    void wink() {
        if (winkAnim != null) winkAnim.cancel();
        winkAnim = ValueAnimator.ofFloat(0f, 1f);
        winkAnim.setDuration(620);
        winkAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                // 0..0.35 cierra, 0.35..0.5 queda cerrado, 0.5..1 abre con rebote
                if (t < 0.35f) eye = 1f - 0.92f * (t / 0.35f);
                else if (t < 0.5f) eye = 0.08f;
                else {
                    float k = (t - 0.5f) / 0.5f;
                    eye = 0.08f + 0.92f * k + 0.18f * (float) Math.sin(Math.PI * k);
                }
                glowK = t < 0.5f ? 0f : (float) Math.sin(Math.PI * (t - 0.5f) / 0.5f);
                invalidate();
            }
        });
        winkAnim.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (auto) postDelayed(loop, winkFirst);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(loop);
        if (winkAnim != null) winkAnim.cancel();
        super.onDetachedFromWindow();
    }

    LogoP(Context c) {
        super(c);
        p.setFillType(Path.FillType.EVEN_ODD);
        p.moveTo(168, 112);
        p.lineTo(292, 112);
        p.arcTo(new RectF(180, 112, 404, 336), -90, 180);
        p.lineTo(240, 336);
        p.lineTo(240, 400);
        p.lineTo(168, 400);
        p.close();
        p.moveTo(240, 176);
        p.lineTo(240, 272);
        p.lineTo(292, 272);
        p.arcTo(new RectF(244, 176, 340, 272), 90, -180);
        p.close();
        tri.moveTo(258, 197);
        tri.lineTo(258, 251);
        tri.lineTo(302, 224);
        tri.close();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float s = getWidth() / 236f;
        cv.save();
        cv.scale(s, s);
        cv.translate(-168, -112);
        // sombra suave para que se lea sobre cualquier imagen
        cv.save();
        cv.translate(0, 6);
        fill.setColor(0x99000000);
        cv.drawPath(p, fill);
        cv.restore();
        fill.setColor(0xFFFF5A36);
        cv.drawPath(p, fill);
        cv.save();
        cv.scale(1f + 0.12f * (eye - 1f), eye, 272f, 224f);
        if (glowK > 0f) {
            fill.setColor(0xFFFFB347);
            fill.setAlpha((int) (90 * glowK));
            cv.drawCircle(272f, 224f, 52f, fill);
        }
        fill.setColor(glowK > 0.2f ? 0xFFFFD27A : 0xFFFFB347);
        cv.drawPath(tri, fill);
        cv.restore();
        cv.restore();
    }
}
