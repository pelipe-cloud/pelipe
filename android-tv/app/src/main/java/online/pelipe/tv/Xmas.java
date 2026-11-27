package online.pelipe.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.Calendar;
import java.util.Random;

/** Navidad: luces y nieve sobre el logo de la barra superior. Activo del 20 de noviembre al 6 de enero. */
final class Xmas {
    private Xmas() {
    }

    static boolean on() {
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        return (m == 11 && d >= 20) || m == 12 || (m == 1 && d <= 6);
    }

    /** Envuelve el logo con el cable de luces y la nieve (solo dentro del área del logo). */
    static View wrap(Context a, LinearLayout logoBox) {
        FrameLayout fl = new FrameLayout(a);
        fl.setClipChildren(false);
        logoBox.setPadding(0, Ui.dp(a, 13), 0, 0);
        fl.addView(logoBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fl.addView(new Overlay(a), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return fl;
    }

    /** Nieve sobre toda la pantalla (pantalla de carga). */
    static View snowAll(Context c) {
        return new Overlay(c, 2);
    }

    static final class Overlay extends View {
        private static final int[] COL = {0xFFFF3B3B, 0xFF3BD15F, 0xFFFFD23B, 0xFF3B9BFF, 0xFFFF8A3D};
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path wire = new Path();
        private final float[] fx, fy, fr, fv, fa;
        private final boolean soloNieve;
        private final Random rnd = new Random();
        private boolean init = false;
        private final float dp;

        Overlay(Context c) {
            this(c, 0);
        }

        Overlay(Context c, int modo) {
            super(c);
            soloNieve = modo == 2;
            int n = soloNieve ? 44 : 18;
            fx = new float[n];
            fy = new float[n];
            fr = new float[n];
            fv = new float[n];
            fa = new float[n];
            dp = c.getResources().getDisplayMetrics().density;
            setFocusable(false);
            setClickable(false);
        }

        private void spawn(int i, boolean top) {
            fx[i] = rnd.nextFloat() * getWidth();
            fy[i] = top ? -3 * dp : rnd.nextFloat() * getHeight();
            fr[i] = (0.8f + rnd.nextFloat() * 1.1f) * dp;
            fv[i] = (0.25f + rnd.nextFloat() * 0.5f) * dp;
            fa[i] = rnd.nextFloat() * 6f;
        }

        /** Mide 0 si el padre se ajusta al contenido: así el overlay NO agranda al logo (se ajusta a su tamaño). */
        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(resolveSize(0, wSpec), resolveSize(0, hSpec));
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (!init) {
                for (int i = 0; i < fx.length; i++) spawn(i, false);
                init = true;
            }
            if (!soloNieve) luces(cv, w);
            // nieve
            nieve(cv, h);
            if (isAttachedToWindow()) postInvalidateDelayed(45);
        }

        private void luces(Canvas cv, int w) {
            // cable
            float y0 = 3 * dp, sag = 8 * dp;
            wire.reset();
            wire.moveTo(0, y0);
            wire.quadTo(w / 2f, y0 + sag * 2f, w, y0);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * dp);
            p.setColor(0xFF1F4A2C);
            cv.drawPath(wire, p);
            p.setStyle(Paint.Style.FILL);
            // bombillos que parpadean
            int n = Math.max(5, Math.round(w / (14 * dp)));
            double t = System.currentTimeMillis() / 1000.0;
            for (int i = 0; i <= n; i++) {
                float k = i / (float) n;
                float x = k * w;
                float y = (1 - k) * (1 - k) * y0 + 2 * (1 - k) * k * (y0 + sag * 2f) + k * k * y0;
                float al = (float) (0.65 + 0.35 * Math.sin(t * 3.4 + i * 1.7));
                int c = COL[i % 5];
                p.setColor(c);
                p.setAlpha((int) (60 * al));
                cv.drawCircle(x, y + 4.5f * dp, 4.6f * dp, p);
                p.setColor(c);
                p.setAlpha((int) (255 * al));
                cv.drawOval(x - 1.8f * dp, y + 2f * dp, x + 1.8f * dp, y + 6.6f * dp, p);
            }
        }

        private void nieve(Canvas cv, int h) {
            p.setColor(0xFFFFFFFF);
            p.setAlpha(225);
            for (int i = 0; i < fx.length; i++) {
                fy[i] += fv[i];
                fa[i] += 0.03f;
                fx[i] += (float) Math.sin(fa[i]) * 0.3f * dp;
                if (fy[i] > h) spawn(i, true);
                cv.drawCircle(fx[i], fy[i], fr[i], p);
            }
        }
    }
}
