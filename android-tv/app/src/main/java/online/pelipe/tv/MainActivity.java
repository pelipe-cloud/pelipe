package online.pelipe.tv;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inicio: portada destacada y filas tipo Netflix. Antes revisa el acceso de pago si está activado. */
public class MainActivity extends AppCompatActivity {
    private LinearLayout content;
    private View heroPlay;
    private boolean built = false;
    private String snap = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(splash("Cargando…"));
        Repo.loadConfig(this, new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                if (Repo.accesoApp) {
                    Repo.IO.execute(new Runnable() {
                        @Override
                        public void run() {
                            final int r = Access.verifyStored(getApplicationContext());
                            Repo.MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (isFinishing()) return;
                                    if (r == Access.OK) start();
                                    else {
                                        Intent i = new Intent(MainActivity.this, GateActivity.class);
                                        i.putExtra("motivo", r);
                                        startActivity(i);
                                        finish();
                                    }
                                }
                            });
                        }
                    });
                } else {
                    start();
                }
            }
        });
    }

    private View splash(String msg) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Ui.BG);
        TextView logo = Ui.text(this, "PELIPE", 40, Ui.ACC, true);
        l.addView(logo);
        TextView t = Ui.text(this, msg, 15, Ui.MUTE, false);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        l.addView(t);
        l.addView(new ProgressBar(this));
        return l;
    }

    private void start() {
        Repo.load(this, false, new Repo.Listener() {
            @Override
            public void onData() {
                if (isFinishing()) return;
                build();
            }

            @Override
            public void onError(String msg) {
                if (isFinishing()) return;
                showError(msg);
            }
        });
    }

    private void showError(String msg) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Ui.BG);
        l.addView(Ui.text(this, "PELIPE", 40, Ui.ACC, true));
        TextView t = Ui.text(this, msg, 16, Ui.TX, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 24), Ui.dp(this, 12), Ui.dp(this, 24), Ui.dp(this, 16));
        l.addView(t);
        TextView btn = Ui.button(this, "Reintentar", true);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setContentView(splash("Cargando…"));
                start();
            }
        });
        l.addView(btn);
        setContentView(l);
        btn.requestFocus();
    }

    private void build() {
        if (!built) {
            LinearLayout shell = new LinearLayout(this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setBackgroundColor(Ui.BG);
            shell.addView(Ui.topBar(this, "inicio"));
            ScrollView sv = new ScrollView(this);
            sv.setVerticalScrollBarEnabled(false);
            sv.setClipChildren(false);
            sv.setClipToPadding(false);
            content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setClipChildren(false);
            content.setClipToPadding(false);
            sv.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            shell.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            setContentView(shell);
            built = true;
        }
        fill();
        if (heroPlay != null) heroPlay.requestFocus();
    }

    private void fill() {
        snap = Repo.snap(this);
        content.removeAllViews();
        heroPlay = null;
        List<JSONObject> films = Repo.list(false);
        List<JSONObject> series = Repo.list(true);
        List<JSONObject> every = Repo.list(null);
        if (every.isEmpty()) {
            TextView t = Ui.text(this, "Aún no hay títulos publicados.", 16, Ui.MUTE, false);
            t.setPadding(Ui.margin(this), Ui.dp(this, 30), Ui.margin(this), 0);
            content.addView(t);
            return;
        }
        // Portada destacada: lo último publicado que tenga imagen de fondo
        JSONObject feat = every.get(0);
        for (JSONObject o : every) {
            if (!Repo.fondo(o).isEmpty()) {
                feat = o;
                break;
            }
        }
        content.addView(hero(feat));

        // Continuar viendo
        JSONObject prog = Repo.progAll(this);
        final List<JSONObject> cont = new ArrayList<JSONObject>();
        Iterator<String> it = prog.keys();
        List<String> ids = new ArrayList<String>();
        while (it.hasNext()) ids.add(it.next());
        final JSONObject pg = prog;
        Collections.sort(ids, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Long.compare(pg.optJSONObject(b).optLong("ts", 0), pg.optJSONObject(a).optLong("ts", 0));
            }
        });
        List<View> cards = new ArrayList<View>();
        for (String id : ids) {
            JSONObject o = Repo.byId(id);
            JSONObject p = prog.optJSONObject(id);
            if (o == null || p == null || !Repo.visible(o)) continue;
            long d = p.optLong("d", 0), t = p.optLong("t", 0);
            int pct = d > 0 ? (int) (t * 100 / d) : 3;
            cards.add(card(o, pct));
        }
        if (!cards.isEmpty()) content.addView(Ui.row(this, "Continuar viendo", cards));

        // Mi lista
        cards = new ArrayList<View>();
        for (String id : Repo.lista(this)) {
            JSONObject o = Repo.byId(id);
            if (o != null && Repo.visible(o)) cards.add(card(o, -1));
        }
        if (!cards.isEmpty()) content.addView(Ui.row(this, "Mi lista", cards));

        if (!films.isEmpty()) content.addView(Ui.row(this, "Películas recientes", cards(films, 20)));
        if (!series.isEmpty()) content.addView(Ui.row(this, "Series", cards(series, 20)));

        // Mejor calificadas
        List<JSONObject> best = new ArrayList<JSONObject>(every);
        Collections.sort(best, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Double.compare(Repo.rating(b), Repo.rating(a));
            }
        });
        List<JSONObject> topRated = new ArrayList<JSONObject>();
        for (JSONObject o : best) if (Repo.rating(o) > 0 && topRated.size() < 15) topRated.add(o);
        if (topRated.size() >= 3) content.addView(Ui.row(this, "Mejor calificadas", cards(topRated, 15)));

        // Por género (los que tengan al menos 3 títulos)
        Map<String, List<JSONObject>> byG = new LinkedHashMap<String, List<JSONObject>>();
        for (JSONObject o : every) {
            List<String> gs = Repo.genres(o);
            if (gs.isEmpty()) continue;
            String g = gs.get(0);
            if (!byG.containsKey(g)) byG.put(g, new ArrayList<JSONObject>());
            byG.get(g).add(o);
        }
        List<Map.Entry<String, List<JSONObject>>> es = new ArrayList<Map.Entry<String, List<JSONObject>>>(byG.entrySet());
        Collections.sort(es, new Comparator<Map.Entry<String, List<JSONObject>>>() {
            @Override
            public int compare(Map.Entry<String, List<JSONObject>> a, Map.Entry<String, List<JSONObject>> b) {
                return b.getValue().size() - a.getValue().size();
            }
        });
        int shown = 0;
        for (Map.Entry<String, List<JSONObject>> e : es) {
            if (e.getValue().size() < 3 || shown >= 6) continue;
            content.addView(Ui.row(this, e.getKey(), cards(e.getValue(), 20)));
            shown++;
        }
        View pad = new View(this);
        content.addView(pad, new LinearLayout.LayoutParams(1, Ui.dp(this, 40)));
    }

    private List<View> cards(List<JSONObject> l, int max) {
        List<View> r = new ArrayList<View>();
        for (JSONObject o : l) {
            if (r.size() >= max) break;
            r.add(card(o, -1));
        }
        return r;
    }

    private View card(final JSONObject o, int pct) {
        return Ui.card(this, o, Ui.cardW(this), pct, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.open(MainActivity.this, o);
            }
        });
    }

    private View hero(final JSONObject o) {
        final boolean tv = Ui.tv(this);
        int w = getResources().getDisplayMetrics().widthPixels;
        FrameLayout f = new FrameLayout(this);
        f.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, tv ? 300 : 250)));
        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setBackgroundColor(Ui.BG2);
        f.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String img = Repo.fondo(o).isEmpty() ? Repo.poster(o) : Repo.fondo(o);
        if (!img.isEmpty()) Glide.with(this).load(img).override(Math.max(w, 800), Ui.dp(this, 300)).centerCrop().into(bg);

        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF20B0B10, 0xB30B0B10, 0x000B0B10}));
        f.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xFF0B0B10, 0x000B0B10}));
        FrameLayout.LayoutParams bpp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 90));
        bpp.gravity = Gravity.BOTTOM;
        f.addView(bottom, bpp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.BOTTOM);
        int m = Ui.margin(this);
        info.setPadding(m, Ui.dp(this, 16), tv ? 0 : m, Ui.dp(this, 22));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(tv ? (int) (w * 0.55f) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        f.addView(info, ip);

        TextView kind = Ui.text(this, Repo.isSerie(o) ? "SERIE" : "PELÍCULA", 12, Ui.ACC2, true);
        info.addView(kind);
        TextView title = Ui.text(this, Repo.titulo(o), tv ? 32 : 26, Ui.TX, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        TextView meta = Ui.text(this, Repo.meta(o), 13, Ui.MUTE, false);
        meta.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        info.addView(meta);
        String syn = Repo.sinopsis(o);
        if (!syn.isEmpty()) {
            TextView s = Ui.text(this, syn, 14, 0xFFD3D3E0, false);
            s.setMaxLines(tv ? 3 : 2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            info.addView(s);
        }
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);
        TextView play = Ui.button(this, "▶  Reproducir", true);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Nav.quick(MainActivity.this, o);
            }
        });
        TextView more = Ui.button(this, "Más información", false);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.open(MainActivity.this, o);
            }
        });
        btns.addView(play);
        LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = Ui.dp(this, 10);
        btns.addView(more, mp);
        info.addView(btns);
        heroPlay = play;
        return f;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al volver del reproductor o de una ficha: refrescar "Continuar viendo" / "Mi lista" si cambiaron
        if (built && !snap.equals(Repo.snap(this)) && !Repo.all.isEmpty()) {
            fill();
            if (heroPlay != null) heroPlay.requestFocus();
        }
    }
}
