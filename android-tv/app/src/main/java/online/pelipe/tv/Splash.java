package online.pelipe.tv;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Pantalla de entrada animada: las letras de PELIPE aparecen una a una, se enciende la línea y corre una barra de carga. */
final class Splash {
    static View view(final Context c, String msg) {
        FrameLayout root = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientRadius(Ui.dp(c, 460));
        bg.setGradientCenter(0.5f, 0.42f);
        bg.setColors(new int[]{0xFF35160F, 0xFF14090C, 0xFF0B0B10});
        root.setBackground(bg);

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        root.addView(col, cp);

        final List<Animator> loops = new ArrayList<Animator>();

        // Letras
        final LinearLayout word = new LinearLayout(c);
        word.setOrientation(LinearLayout.HORIZONTAL);
        word.setGravity(Gravity.CENTER);
        final String w = "PELIPE";
        final TextView[] ls = new TextView[w.length()];
        for (int i = 0; i < w.length(); i++) {
            TextView t = Ui.text(c, String.valueOf(w.charAt(i)), 54, Ui.ACC, true);
            t.setShadowLayer(Ui.dp(c, 14), 0, 0, 0x99FF5A36);
            t.setLetterSpacing(0.06f);
            t.setAlpha(0f);
            t.setTranslationY(Ui.dp(c, 34));
            t.setScaleX(0.5f);
            t.setScaleY(0.5f);
            ls[i] = t;
            word.addView(t);
        }
        col.addView(word);

        // Línea que se enciende
        final View line = new View(c);
        line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x00FFB347, 0xFFFFB347, 0x00FFB347}));
        line.setScaleX(0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 260), Ui.dp(c, 3));
        lp.topMargin = Ui.dp(c, 4);
        col.addView(line, lp);

        final TextView on = Ui.text(c, "PELÍCULAS Y SERIES LIBRES", 12, Ui.MUTE, true);
        on.setLetterSpacing(0.3f);
        on.setAlpha(0f);
        on.setPadding(0, Ui.dp(c, 12), 0, 0);
        col.addView(on);

        // Barra de carga que va y viene
        FrameLayout track = new FrameLayout(c);
        track.setBackground(Ui.shape(0x22FFFFFF, 0, 0, Ui.dp(c, 2)));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(Ui.dp(c, 200), Ui.dp(c, 3));
        tp.topMargin = Ui.dp(c, 36);
        col.addView(track, tp);
        final View bar = new View(c);
        bar.setBackground(Ui.shape(Ui.ACC2, 0, 0, Ui.dp(c, 2)));
        track.addView(bar, new FrameLayout.LayoutParams(Ui.dp(c, 70), Ui.dp(c, 3)));
        ObjectAnimator sweep = ObjectAnimator.ofFloat(bar, "translationX", 0f, Ui.dp(c, 130));
        sweep.setDuration(900);
        sweep.setRepeatCount(ObjectAnimator.INFINITE);
        sweep.setRepeatMode(ObjectAnimator.REVERSE);
        sweep.setInterpolator(new AccelerateDecelerateInterpolator());
        loops.add(sweep);

        TextView m = Ui.text(c, msg, 13, Ui.MUTE, false);
        m.setPadding(0, Ui.dp(c, 14), 0, 0);
        col.addView(m);

        // Brillo suave del logo
        ObjectAnimator glow = ObjectAnimator.ofFloat(word, "alpha", 1f, 0.8f);
        glow.setDuration(1100);
        glow.setStartDelay(1400);
        glow.setRepeatCount(ObjectAnimator.INFINITE);
        glow.setRepeatMode(ObjectAnimator.REVERSE);
        glow.setInterpolator(new LinearInterpolator());
        loops.add(glow);

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                for (int i = 0; i < ls.length; i++) {
                    ls[i].animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                            .setStartDelay(200 + i * 110L).setDuration(560)
                            .setInterpolator(new OvershootInterpolator(1.7f)).start();
                }
                line.animate().scaleX(1f).setStartDelay(950).setDuration(600).start();
                on.animate().alpha(1f).setStartDelay(1100).setDuration(600).start();
                for (Animator a : loops) a.start();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                for (Animator a : loops) a.cancel();
                for (TextView t : ls) t.animate().cancel();
            }
        });
        return root;
    }
}
