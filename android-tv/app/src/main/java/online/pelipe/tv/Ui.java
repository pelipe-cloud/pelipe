package online.pelipe.tv;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
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
            TX = 0xFFF2F2F7, MUTE = 0xFF9A9AB0, ACC = 0xFFFF5A36, ACC2 = 0xFFFFB347, RING = 0xFFFFFFFF;

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

    static StateListDrawable states(Drawable focused, Drawable normal) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, focused);
        s.addState(new int[]{android.R.attr.state_pressed}, focused);
        s.addState(new int[]{}, normal);
        return s;
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
    static void pop(View v, final float scale, final float lift) {
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View x, boolean f) {
                x.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f).translationZ(f ? lift : 0f).setDuration(120).start();
            }
        });
    }

    static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 16, primary ? 0xFFFFFFFF : TX, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 22), dp(c, 12), dp(c, 22), dp(c, 12));
        t.setFocusable(true);
        t.setClickable(true);
        float r = dp(c, 24);
        Drawable normal = primary ? shape(ACC, 0, 0, r) : shape(BG3, LINE, dp(c, 1), r);
        Drawable focus = primary ? shape(ACC, RING, dp(c, 3), r) : shape(0xFF30303F, RING, dp(c, 3), r);
        t.setBackground(states(focus, normal));
        pop(t, 1.05f, dp(c, 6));
        return t;
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
        Drawable normal = selected ? shape(ACC, 0, 0, r) : shape(BG3, LINE, dp(c, 1), r);
        Drawable focus = shape(selected ? ACC : 0xFF30303F, RING, dp(c, 3), r);
        t.setBackground(states(focus, normal));
        pop(t, 1.06f, dp(c, 4));
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

        TextView logo = text(a, "PELIPE", tv ? 24 : 20, ACC, true);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(logo);
        if (tv) {
            TextView on = text(a, ".ONLINE", 12, MUTE, true);
            on.setPadding(dp(a, 2), dp(a, 8), 0, 0);
            bar.addView(on);
        }

        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        LinearLayout tabs = new LinearLayout(a);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        tabs.setPadding(dp(a, 14), dp(a, 4), dp(a, 4), dp(a, 4));
        hs.addView(tabs);
        String[][] items = {{"inicio", "Inicio"}, {"peliculas", "Películas"}, {"series", "Series"}, {"lista", "Mi lista"}, {"buscar", "Buscar"}};
        for (final String[] it : items) {
            final boolean on = it[0].equals(sel);
            TextView t = text(a, it[1], 15, on ? ACC2 : TX, on);
            t.setSingleLine(true);
            t.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 8));
            t.setFocusable(true);
            t.setClickable(true);
            float r = dp(a, 18);
            t.setBackground(states(shape(0xFF30303F, RING, dp(a, 2), r), shape(0, 0, 0, r)));
            LinearLayout.LayoutParams p = lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(a, 2);
            t.setLayoutParams(p);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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
