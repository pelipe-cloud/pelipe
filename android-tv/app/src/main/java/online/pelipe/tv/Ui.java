package online.pelipe.tv;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.List;

/** Piezas de interfaz hechas en código: botones, tarjetas, filas y barra superior. Todo se maneja con el control remoto. */
final class Ui {
    static final int BG = 0xFF0B0B10, BG2 = 0xFF14141C, BG3 = 0xFF1D1D28, LINE = 0xFF2A2A38,
            TX = 0xFFF2F2F7, MUTE = 0xFF9A9AB0, ACC = 0xFFFF5A36, ACC2 = 0xFFFFB347, RING = 0xFFFFB347;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static boolean tv(Context c) {
        UiModeManager m = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        boolean t = m != null && m.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        return t || !c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }

    /** Margen lateral: más grande en TV (zona segura de la pantalla). */
    static int margin(Context c) {
        return dp(c, tv(c) ? 48 : 16);
    }

    static int cardW(Context c) {
        return dp(c, tv(c) ? 150 : 112);
    }

    static GradientDrawable shape(int fill, int stroke, int strokeW, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }

    /** Relleno con degradado (menos plano): c1 → c2 en la dirección dada. */
    static GradientDrawable grad(int c1, int c2, GradientDrawable.Orientation o, int stroke, int strokeW, float radiusPx) {
        GradientDrawable g = new GradientDrawable(o, new int[]{c1, c2});
        g.setCornerRadius(radiusPx);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }

    static StateListDrawable states(Drawable focused, Drawable normal) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_pressed}, focused);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** Quita el recuadro blanco que Android dibuja por defecto sobre lo enfocado (se veía como un borde al lado). */
    static void plain(View v) {
        if (Build.VERSION.SDK_INT >= 26) v.setDefaultFocusHighlightEnabled(false);
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    /** Hace que la vista crezca un poco al recibir el foco del control remoto. */
    /** Entrada suave tipo Netflix: sube 22 dp y aparece, escalonado por posición. */
    static void rise(View v, int i) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 22));
        v.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(i, 7) * 90L).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    /** Entrada de una fila del inicio: se desliza desde la derecha y aparece. */
    static void slideIn(View v, int i) {
        v.setAlpha(0f);
        v.setTranslationX(dp(v.getContext(), 40));
        v.animate().alpha(1f).translationX(0f).setStartDelay(Math.min(i, 7) * 80L).setDuration(450)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    static void pop(View v, final float scale, final float lift) {
        plain(v);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean f) {
                // Rebote suave al enfocar (con el control remoto) y al soltar
                x.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f).translationZ(f ? lift : 0f).setDuration(f ? 190 : 130)
                        .setInterpolator(f ? new android.view.animation.OvershootInterpolator(1.8f) : new android.view.animation.DecelerateInterpolator()).start();
            }
        });
        // Al tocar (celular) se hunde un poco y vuelve con rebote
        final float rest = scale;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View x, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    x.animate().scaleX(0.94f).scaleY(0.94f).setDuration(80).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    float s = x.isFocused() ? rest : 1f;
                    x.animate().scaleX(s).scaleY(s).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(2.4f)).start();
                }
                return false;
            }
        });
    }

    /** Animación breve del botón "me gusta": rebote con giro al marcarlo, encogida suave al quitarlo. */
    static void likePop(final View v, boolean liked) {
        final float rest = v.isFocused() ? 1.1f : 1f;
        v.animate().cancel();
        if (liked) {
            v.setRotation(0f);
            v.animate().scaleX(1.5f).scaleY(1.5f).rotation(-22f).setDuration(110)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            v.animate().scaleX(rest).scaleY(rest).rotation(0f).setDuration(320)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(3.2f)).start();
                        }
                    }).start();
        } else {
            v.animate().scaleX(0.82f).scaleY(0.82f).setDuration(90)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            v.animate().scaleX(rest).scaleY(rest).setDuration(220)
                                    .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                        }
                    }).start();
        }
    }

    static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 16, primary ? 0xFFFFFFFF : TX, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12));
        t.setFocusable(true);
        t.setClickable(true);
        float r = dp(c, 24);
        GradientDrawable.Orientation lr = GradientDrawable.Orientation.LEFT_RIGHT, tb = GradientDrawable.Orientation.TOP_BOTTOM;
        Drawable normal = primary ? grad(ACC, 0xFFFF8A3D, lr, 0, 0, r) : grad(0xFF2A2A3A, BG3, tb, LINE, dp(c, 1), r);
        Drawable focus = primary ? grad(0xFFFF7A5C, ACC2, lr, RING, dp(c, 3), r) : grad(0xFF3C3C52, 0xFF2A2A3A, tb, RING, dp(c, 3), r);
        t.setBackground(states(focus, normal));
        t.setElevation(dp(c, 3));
        pop(t, 1.06f, dp(c, 6));
        return t;
    }

    /** Botón redondo con solo un ícono (estilo Netflix): "me gusta". active = ya marcado. */
    static TextView roundButton(Context c, String icon, boolean active) {
        TextView t = text(c, icon, 20, TX, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        int d = dp(c, 48);
        t.setMinWidth(d);
        t.setMinHeight(d);
        t.setPadding(0, 0, 0, 0);
        t.setFocusable(true);
        t.setClickable(true);
        styleRound(t, active);
        pop(t, 1.1f, 0f);
        return t;
    }

    static void styleRound(TextView t, boolean active) {
        Context c = t.getContext();
        float r = dp(c, 24);
        Drawable normal = active ? shape(ACC, 0, 0, r) : shape(BG3, LINE, dp(c, 1), r);
        Drawable focus = shape(active ? 0xFFFF7A5C : 0xFF30303F, RING, dp(c, 3), r);
        t.setBackground(states(focus, normal));
    }

    /** Botón pequeño tipo "chip" (idioma, temporada, género). */
    static TextView chip(Context c, String label, boolean selected) {
        TextView t = text(c, label, 14, selected ? 0xFFFFFFFF : TX, selected);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        t.setFocusable(true);
        t.setClickable(true);
        float r = dp(c, 20);
        GradientDrawable.Orientation lr = GradientDrawable.Orientation.LEFT_RIGHT, tb = GradientDrawable.Orientation.TOP_BOTTOM;
        Drawable normal = selected ? grad(ACC, 0xFFFF8A3D, lr, 0, 0, r) : grad(0xFF272736, BG3, tb, LINE, dp(c, 1), r);
        Drawable focus = selected ? grad(0xFFFF7A5C, ACC2, lr, RING, dp(c, 3), r) : grad(0xFF3C3C52, 0xFF2A2A3A, tb, RING, dp(c, 3), r);
        t.setBackground(states(focus, normal));
        pop(t, 1.05f, dp(c, 4));
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    /* ---------------- barra superior ---------------- */

    static View topBar(final Activity a, final String sel) {
        final boolean tv = tv(a);
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BG);
        bar.setPadding(margin(a), dp(a, 8), margin(a), dp(a, 8));

        // Logo: la "P" con su play (que guiña de vez en cuando) + "ELIPE"
        LinearLayout logoBox = new LinearLayout(a);
        logoBox.setOrientation(LinearLayout.HORIZONTAL);
        logoBox.setGravity(Gravity.CENTER_VERTICAL);
        int lh = dp(a, tv ? 36 : 30);
        LogoP mark = new LogoP(a).autoWink();
        logoBox.addView(mark, new LinearLayout.LayoutParams(Math.round(lh * 236f / 288f), lh));
        TextView logo = text(a, "ELIPE", tv ? 31 : 26, ACC, true);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setPadding(dp(a, 1), 0, 0, 0);
        logoBox.addView(logo);
        bar.addView(logoBox);

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(a, 14), dp(a, 4), dp(a, 4), dp(a, 4));
        hs.addView(tabs);
        String[][] items = {{"inicio", "🏠 Inicio"}, {"peliculas", "🎬 Catálogo Películas"}, {"series", "📺 Catálogo Series"}, {"lista", "⭐ Mi lista"}, {"buscar", "🔍 Buscar"}, {"azar", "🎲"}};
        for (final String[] it : items) {
            final boolean on = it[0].equals(sel);
            TextView t = text(a, it[1], 15, on ? ACC2 : TX, on);
            t.setSingleLine(true);
            t.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 8));
            t.setFocusable(true);
            t.setClickable(true);
            if (it[0].equals("azar")) t.setContentDescription("Título al azar");
            float r = dp(a, 18);
            plain(t);
            t.setBackground(states(grad(0xFF3C3C52, 0xFF2A2A3A, GradientDrawable.Orientation.TOP_BOTTOM, RING, dp(a, 2), r),
                    on ? grad(0x55FF5A36, 0x55FFB347, GradientDrawable.Orientation.LEFT_RIGHT, 0, 0, r) : shape(0, 0, 0, r)));
            pop(t, 1.08f, dp(a, 4));
            LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(a, 2);
            t.setLayoutParams(p);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (it[0].equals("azar")) {
                        // Dado: abre un título al azar
                        java.util.List<JSONObject> all = Repo.list(null);
                        if (!all.isEmpty()) open(a, all.get(new java.util.Random().nextInt(all.size())));
                        return;
                    }
                    if (on) return;
                    Intent i;
                    if (it[0].equals("inicio")) {
                        i = new Intent(a, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    } else {
                        i = new Intent(a, BrowseActivity.class);
                        i.putExtra("mode", it[0]);
                    }
                    a.startActivity(i);
                    if (a instanceof BrowseActivity) {
                        a.finish();
                        a.overridePendingTransition(0, 0);
                    }
                }
            });
            tabs.addView(t);
        }
        bar.addView(hs, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) hs.getLayoutParams()).weight = 1f;
        return bar;
    }

    /* ---------------- tarjeta de póster ---------------- */

    /** pct: progreso 0-100 (barra naranja abajo) o -1 si no aplica. */
    static View card(Activity a, JSONObject o, int w, int pct, View.OnClickListener click) {
        int pad = dp(a, 3);
        int h = Math.round(w * 1.5f);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        FrameLayout frame = new FrameLayout(a);
        frame.setPadding(pad, pad, pad, pad);
        frame.setFocusable(true);
        frame.setClickable(true);
        float r = dp(a, 8);
        frame.setBackground(states(shape(0, RING, dp(a, 3), r), shape(0, 0, 0, r)));
        frame.setOnClickListener(click);
        pop(frame, 1.07f, dp(a, 8));

        final float rad = dp(a, 6);
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(BG3);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline out) {
                out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), rad);
            }
        });
        iv.setClipToOutline(true);
        frame.addView(iv, new FrameLayout.LayoutParams(w, h));
        String url = Repo.poster(o);
        if (url.isEmpty()) url = Repo.fondo(o);
        if (!url.isEmpty()) {
            Glide.with(a).load(url).override(w, h).centerCrop().into(iv);
        } else {
            TextView t = text(a, Repo.titulo(o), 13, TX, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(a, 8), 0, dp(a, 8), 0);
            frame.addView(t, new FrameLayout.LayoutParams(w, h));
        }

        LinearLayout badges = new LinearLayout(a);
        badges.setOrientation(LinearLayout.HORIZONTAL);
        badges.addView(badge(a, "HD", ACC));
        if (Repo.isSerie(o)) {
            View sp = new View(a);
            badges.addView(sp, new LinearLayout.LayoutParams(dp(a, 4), 1));
            badges.addView(badge(a, "SERIE", 0xFF2E5BFF));
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.TOP | Gravity.START;
        bp.setMargins(dp(a, 6), dp(a, 6), 0, 0);
        frame.addView(badges, bp);

        if (pct >= 0) {
            ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(Math.max(3, Math.min(100, pct)));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(ACC));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x99000000));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(w, dp(a, 4));
            pp.gravity = Gravity.BOTTOM;
            frame.addView(pb, pp);
        }
        col.addView(frame, new LinearLayout.LayoutParams(w + 2 * pad, h + 2 * pad));

        TextView title = text(a, Repo.titulo(o), tv(a) ? 14 : 12, TX, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(pad, dp(a, 4), pad, 0);
        col.addView(title, new LinearLayout.LayoutParams(w + 2 * pad, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.setLayoutParams(new LinearLayout.LayoutParams(w + 2 * pad, ViewGroup.LayoutParams.WRAP_CONTENT));
        return col;
    }

    static TextView badge(Context c, String s, int color) {
        TextView t = text(c, s, 10, 0xFFFFFFFF, true);
        t.setPadding(dp(c, 5), dp(c, 1), dp(c, 5), dp(c, 1));
        t.setBackground(shape(color, 0, 0, dp(c, 4)));
        return t;
    }

    /** Fila horizontal con título: "Recién agregadas", etc. */
    static View row(Activity a, String title, List<View> cards) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        TextView h = text(a, title, tv(a) ? 20 : 17, TX, true);
        h.setPadding(margin(a), dp(a, 14), margin(a), dp(a, 4));
        box.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout in = new LinearLayout(a);
        in.setOrientation(LinearLayout.HORIZONTAL);
        in.setClipChildren(false);
        in.setClipToPadding(false);
        in.setPadding(margin(a) - dp(a, 3), dp(a, 8), margin(a), dp(a, 12));
        for (View v : cards) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).rightMargin = dp(a, 8);
            in.addView(v);
        }
        hs.addView(in);
        box.addView(hs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    /** Logo "P" pequeño y semitransparente arriba a la derecha del video, como la marca de un canal de TV. */
    static View watermark(FrameLayout root) {
        Context c = root.getContext();
        int w = dp(c, tv(c) ? 30 : 22);
        LogoP lg = new LogoP(c).autoWink(15000, 120000); // guiña a los 15 s y luego cada 2 minutos
        lg.setAlpha(0.6f);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, Math.round(w * 288f / 236f));
        p.gravity = Gravity.TOP | Gravity.END;
        p.setMargins(0, dp(c, tv(c) ? 24 : 12), dp(c, tv(c) ? 40 : 16), 0);
        root.addView(lg, p);
        return lg;
    }

    static void immersive(Activity a) {
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    static void open(Activity a, JSONObject o) {
        Intent i = new Intent(a, DetailActivity.class);
        i.putExtra("id", Repo.id(o));
        a.startActivity(i);
    }

    static int color(String hex) {
        return Color.parseColor(hex);
    }
}
