package online.pelipe.tv;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ficha de una película o serie: sinopsis, idioma/servidor, temporadas y episodios. */
public class DetailActivity extends AppCompatActivity {
    private JSONObject it;
    private boolean serie;
    private List<Repo.Srv> srvs = new ArrayList<Repo.Srv>();
    private List<Repo.Season> seasons = new ArrayList<Repo.Season>();
    private int curSrv = 0;
    private int curSeason = 0;
    private String lang = "lat";
    private LinearLayout col;
    private TextView playBtn, listBtn;
    private final List<TextView> pbs = new ArrayList<TextView>();
    private final List<String> pbLabels = new ArrayList<String>();
    private int pbSel = 0;
    private LinearLayout langBox, epsBox, seasonBox;
    private View selView, focusEp;
    private LinearLayout castBox;
    private int lastS = -1, lastE = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        it = id == null ? null : Repo.byId(id);
        if (it == null) {
            finish();
            return;
        }
        serie = Repo.isSerie(it);
        lang = Repo.lang(this);
        if (serie) seasons = Repo.seasons(it);
        else {
            srvs = Repo.servers(it);
            Repo.Srv p = Repo.pick(srvs, lang);
            curSrv = Math.max(0, srvs.indexOf(p));
        }
        build();
    }

    private void build() {
        final boolean tv = Ui.tv(this);
        int mg = Ui.margin(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        ImageView bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bg.setAlpha(0.45f);
        root.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String img = Repo.fondo(it).isEmpty() ? Repo.poster(it) : Repo.fondo(it);
        int w = getResources().getDisplayMetrics().widthPixels;
        if (!img.isEmpty()) Glide.with(this).load(img).override(Math.max(w, 800), 720).centerCrop().into(bg);
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x330B0B10, 0xE60B0B10, 0xFF0B0B10}));
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(mg, Ui.dp(this, tv ? 28 : 20), mg, Ui.dp(this, 40));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Cabecera: póster + datos
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(tv ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        int pw = Ui.dp(this, tv ? 170 : 130);
        int ph = pw * 3 / 2;
        FilmFrame film = new FilmFrame(this);
        int fi = film.inset();
        FrameLayout pf = new FrameLayout(this);
        pf.setBackground(film);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(Ui.BG3);
        String pu = Repo.poster(it);
        if (!pu.isEmpty()) Glide.with(this).load(pu).override(pw, ph).centerCrop().into(poster);
        FrameLayout.LayoutParams pip = new FrameLayout.LayoutParams(pw, ph);
        pip.setMargins(fi, fi, fi, fi);
        pf.addView(poster, pip);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(pw + 2 * fi, ph + 2 * fi);
        pp.rightMargin = Ui.dp(this, 20);
        pp.bottomMargin = Ui.dp(this, 12);
        head.addView(pf, pp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView kind = Ui.text(this, serie ? "SERIE" : "PELÍCULA", 12, Ui.ACC2, true);
        kind.setLetterSpacing(0.12f);
        info.addView(kind);
        TextView title = Ui.text(this, Repo.titulo(it), tv ? 32 : 26, Ui.TX, true);
        title.setPadding(0, Ui.dp(this, 4), 0, 0);
        info.addView(title);
        TextView meta = Ui.text(this, Repo.meta(it), 14, Ui.MUTE, false);
        meta.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        info.addView(meta);
        List<String> gs = Repo.genres(it);
        if (!gs.isEmpty()) {
            StringBuilder gl = new StringBuilder();
            for (String x : gs) gl.append(gl.length() > 0 ? "   " : "").append(Repo.genreEmoji(x)).append(" ").append(x);
            TextView g = Ui.text(this, gl.toString(), 14, Ui.ACC2, false);
            g.setPadding(0, 0, 0, Ui.dp(this, 16));
            info.addView(g);
        }
        String syn = Repo.sinopsis(it);
        if (!syn.isEmpty()) {
            TextView s = Ui.text(this, syn, 15, 0xFFD3D3E0, false);
            s.setLineSpacing(0, 1.25f);
            info.addView(s);
        }
        String cr = Repo.str(it, "creditos");
        if (!cr.isEmpty()) {
            TextView c = Ui.text(this, cr, 12, Ui.MUTE, false);
            c.setPadding(0, Ui.dp(this, 14), 0, 0);
            info.addView(c);
        }
        head.addView(info, new LinearLayout.LayoutParams(tv ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, tv ? 1f : 0f));
        col.addView(head);

        // Botones: ▶ [bandera idioma] (uno por versión) + Mi lista + Tráiler, todos en UNA fila
        HorizontalScrollView bhs = new HorizontalScrollView(this);
        bhs.setHorizontalScrollBarEnabled(false);
        bhs.setClipChildren(false);
        bhs.setClipToPadding(false);
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        btns.setClipToPadding(false);
        btns.setPadding(Ui.dp(this, 3), Ui.dp(this, 14), Ui.dp(this, 3), Ui.dp(this, 10));
        bhs.addView(btns, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildPlayButtons(btns);
        View sep = new View(this);
        sep.setBackgroundColor(0xFF4A4A5E);
        LinearLayout.LayoutParams sp = Ui.lp(Ui.dp(this, 2), Ui.dp(this, 30));
        sp.gravity = Gravity.CENTER_VERTICAL;
        sp.leftMargin = Ui.dp(this, 14);
        sp.rightMargin = Ui.dp(this, 4);
        btns.addView(sep, sp);
        listBtn = Ui.button(this, "", false);
        listBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Repo.toggleLista(DetailActivity.this, Repo.id(it));
                updateButtons();
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(this, 10);
        btns.addView(listBtn, lp);
        if (Nav.hasTrailer(it)) {
            TextView tr = Ui.button(this, "Ver tráiler", false);
            tr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Nav.trailer(DetailActivity.this, it);
                }
            });
            LinearLayout.LayoutParams tp2 = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp2.leftMargin = Ui.dp(this, 10);
            btns.addView(tr, tp2);
        }
        col.addView(bhs);

        langBox = new LinearLayout(this);
        langBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(langBox);
        if (serie) {
            seasonBox = new LinearLayout(this);
            seasonBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(seasonBox);
            epsBox = new LinearLayout(this);
            epsBox.setOrientation(LinearLayout.VERTICAL);
            col.addView(epsBox);
            JSONObject p = Repo.prog(this, Repo.id(it));
            if (p != null) {
                for (int i = 0; i < seasons.size(); i++) if (seasons.get(i).n == p.optInt("s", -1)) curSeason = i;
            }
        }
        setContentView(root);
        updateButtons();
        if (serie) {
            buildSeasons();
            buildEps();
        }
        castBox = new LinearLayout(this);
        castBox.setOrientation(LinearLayout.VERTICAL);
        col.addView(castBox);
        loadCast();
        addSimilar();
        playBtn.requestFocus();
        for (int i = 0; i < col.getChildCount(); i++) Ui.rise(col.getChildAt(i), i);
    }


    /** Dirección y reparto principal (TMDB). Si no hay datos o no hay internet, no se muestra nada. */
    private void loadCast() {
        Repo.credits(this, it, new Repo.CreditsCb() {
            @Override
            public void done(JSONObject r) {
                if (r == null || isFinishing()) return;
                fillCast(r);
            }
        });
    }

    private void fillCast(JSONObject r) {
        boolean tv = Ui.tv(this);
        org.json.JSONArray dir = r.optJSONArray("dir"), rep = r.optJSONArray("rep");
        int cols = tv ? 4 : 2;
        int max = 8;
        if (dir != null && dir.length() > 0) {
            boolean creador = "Creación".equals(dir.optJSONObject(0).optString("r"));
            castBox.addView(castHead(creador ? "🎬  CREADA POR" : "🎬  DIRECCIÓN"));
            List<View> l = new ArrayList<View>();
            for (int i = 0; i < dir.length(); i++) {
                JSONObject p = dir.optJSONObject(i);
                if (p != null) l.add(personCard(p.optString("n"), p.optString("f"), creador ? "Creador de la serie" : "Director de la película", true));
            }
            castBox.addView(castRows(l, tv ? 2 : 1));
        }
        if (rep != null && rep.length() > 0) {
            castBox.addView(castHead("👤  REPARTO PRINCIPAL"));
            List<View> l = new ArrayList<View>();
            for (int i = 0; i < rep.length() && l.size() < max; i++) {
                JSONObject p = rep.optJSONObject(i);
                if (p != null) l.add(personCard(p.optString("n"), p.optString("f"), p.optString("r"), false));
            }
            castBox.addView(castRows(l, cols));
        }
    }

    private TextView castHead(String s) {
        TextView t = Ui.text(this, s, 12, Ui.ACC2, true);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        return t;
    }

    private LinearLayout castRows(List<View> cards, int cols) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < cards.size(); i += cols) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < cols; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
                View v = i + k < cards.size() ? cards.get(i + k) : new View(this);
                row.addView(v, lp);
            }
            box.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return box;
    }

    private View personCard(String name, String foto, String role, boolean director) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pd = Ui.dp(this, 8);
        c.setPadding(pd, pd, Ui.dp(this, 12), pd);
        c.setBackground(Ui.shape(director ? 0xFF2A1A1A : Ui.BG2, director ? 0x66FF5A36 : Ui.LINE, 1, Ui.dp(this, 12)));
        int sz = Ui.dp(this, 44);
        FrameLayout av = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable oval = new android.graphics.drawable.GradientDrawable();
        oval.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        oval.setColor(Ui.BG3);
        if (director) oval.setStroke(Ui.dp(this, 2), Ui.ACC);
        av.setBackground(oval);
        TextView ini = Ui.text(this, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), 16, Ui.ACC2, true);
        ini.setGravity(android.view.Gravity.CENTER);
        av.addView(ini, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!foto.isEmpty()) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            av.addView(iv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Glide.with(this).load("https://image.tmdb.org/t/p/w185" + foto).override(sz * 2, sz * 2).circleCrop().into(iv);
        }
        c.addView(av, new LinearLayout.LayoutParams(sz, sz));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(this, name, 13, Ui.TX, true);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tx.addView(n);
        if (!role.isEmpty()) {
            TextView rl = Ui.text(this, role, 11, Ui.MUTE, false);
            rl.setSingleLine(true);
            rl.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tx.addView(rl);
        }
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Ui.dp(this, 10);
        c.addView(tx, tp);
        return c;
    }

    private static final class Scored {
        JSONObject o;
        double s;
    }

    /** "Más como esta 😎": tres títulos que comparten géneros con este (con un poco de azar entre los parecidos). */
    private void addSimilar() {
        List<String> mine = Repo.genres(it);
        if (mine.isEmpty()) return;
        List<Scored> sc = new ArrayList<Scored>();
        for (JSONObject o : Repo.list(null)) {
            if (Repo.id(o).equals(Repo.id(it))) continue;
            int shared = 0;
            for (String g : Repo.genres(o)) if (mine.contains(g)) shared++;
            if (shared == 0) continue;
            Scored x = new Scored();
            x.o = o;
            x.s = shared * 10 + (Repo.isSerie(o) == serie ? 4 : 0) + Math.random() * 6;
            sc.add(x);
        }
        java.util.Collections.sort(sc, new java.util.Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                return Double.compare(b.s, a.s);
            }
        });
        List<View> cards = new ArrayList<View>();
        for (int i = 0; i < sc.size() && cards.size() < 3; i++) {
            final JSONObject o = sc.get(i).o;
            cards.add(Ui.card(this, o, Ui.cardW(this), -1, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.open(DetailActivity.this, o);
                }
            }));
        }
        if (cards.isEmpty()) return;
        int mg = Ui.margin(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(-mg, Ui.dp(this, 12), -mg, 0);
        col.addView(Ui.row(this, "Más como esta 😎", cards), rp);
    }

    private void updateButtons() {
        JSONObject p = Repo.prog(this, Repo.id(it));
        String extra = "";
        if (serie) {
            if (p != null && p.optInt("s", 0) > 0) extra = "  ·  T" + p.optInt("s") + " E" + p.optInt("e");
        } else {
            long t = p == null ? 0 : p.optLong("t", 0);
            if (t > 10) extra = "  ·  " + Repo.clock(t);
        }
        for (int i = 0; i < pbs.size(); i++) {
            pbs.get(i).setText("▶  " + pbLabels.get(i) + (i == pbSel ? extra : ""));
        }
        listBtn.setText(Repo.inLista(this, Repo.id(it)) ? "⭐  En mi lista" : "⭐  Mi lista");
    }

    /** Un botón ▶ por idioma (series) o por versión/servidor (películas), con bandera. */
    private void buildPlayButtons(LinearLayout btns) {
        pbs.clear();
        pbLabels.clear();
        final List<Integer> keys = new ArrayList<Integer>();
        final List<String> langs = new ArrayList<String>();
        if (serie) {
            Set<String> set = new LinkedHashSet<String>();
            for (Repo.Season s : seasons) for (Repo.Ep e : s.eps) for (Repo.Srv v : e.srv) set.add(v.lang);
            langs.addAll(set);
            for (int i = 0; i < langs.size(); i++) {
                String l = langs.get(i);
                pbLabels.add(l.isEmpty() ? "Reproducir" : Repo.flag(l) + Repo.langLabel(l));
                keys.add(i);
                if (l.equals(lang)) pbSel = i;
            }
        } else {
            for (int i = 0; i < srvs.size(); i++) {
                Repo.Srv sv = srvs.get(i);
                String l = srvs.size() < 2 && sv.name.isEmpty() && sv.lang.isEmpty() ? "Reproducir" : sv.label(i) + (sv.direct ? "" : " (web)");
                pbLabels.add(l);
                keys.add(i);
            }
            pbSel = curSrv;
        }
        if (pbLabels.isEmpty()) {
            pbLabels.add("Reproducir");
            keys.add(0);
        }
        if (pbSel >= pbLabels.size()) pbSel = 0;
        for (int i = 0; i < pbLabels.size(); i++) {
            final int k = i;
            TextView b = Ui.button(this, "▶  " + pbLabels.get(i), i == pbSel);
            b.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (serie) {
                        if (!langs.isEmpty()) {
                            lang = langs.get(k);
                            Repo.setLang(DetailActivity.this, lang);
                        }
                        Nav.quick(DetailActivity.this, it);
                    } else if (!srvs.isEmpty()) {
                        curSrv = k;
                        lang = srvs.get(k).lang;
                        Repo.setLang(DetailActivity.this, lang);
                        Nav.play(DetailActivity.this, it, 0, 0, srvs.get(k));
                    }
                    pbSel = k;
                }
            });
            pbs.add(b);
            LinearLayout.LayoutParams l2 = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) l2.leftMargin = Ui.dp(this, 10);
            btns.addView(b, l2);
        }
        playBtn = pbs.get(pbSel);
    }

    /** Selector de versión: servidores de la película, o idiomas disponibles en la serie. */
    private void buildLang() {
        langBox.removeAllViews();
        List<String> labels = new ArrayList<String>();
        final List<Integer> idx = new ArrayList<Integer>();
        if (serie) {
            Set<String> langs = new LinkedHashSet<String>();
            for (Repo.Season s : seasons) for (Repo.Ep e : s.eps) for (Repo.Srv v : e.srv) langs.add(v.lang);
            if (langs.size() < 2) return;
            int k = 0;
            for (String l : langs) {
                labels.add(Repo.flag(l) + Repo.langLabel(l));
                idx.add(k++);
            }
            final List<String> arr = new ArrayList<String>(langs);
            addChips("Idioma", labels, arr.indexOf(lang), new Chooser() {
                @Override
                public void choose(int i) {
                    lang = arr.get(i);
                    Repo.setLang(DetailActivity.this, lang);
                    buildLang();
                    if (selView != null) selView.requestFocus();
                }
            });
        } else {
            if (srvs.size() < 2) return;
            for (int i = 0; i < srvs.size(); i++) {
                Repo.Srv s = srvs.get(i);
                labels.add(s.label(i) + (s.direct ? "" : "  (web)"));
            }
            addChips("Versión", labels, curSrv, new Chooser() {
                @Override
                public void choose(int i) {
                    curSrv = i;
                    lang = srvs.get(i).lang;
                    Repo.setLang(DetailActivity.this, lang);
                    buildLang();
                    if (selView != null) selView.requestFocus();
                }
            });
        }
    }

    private interface Chooser {
        void choose(int i);
    }

    private void addChips(String label, List<String> labels, int selected, final Chooser ch) {
        TextView h = Ui.text(this, label, 13, Ui.MUTE, true);
        h.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        langBox.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 3), Ui.dp(this, 4), Ui.dp(this, 3), Ui.dp(this, 6));
        hs.addView(row);
        for (int i = 0; i < labels.size(); i++) {
            final int k = i;
            TextView c = Ui.chip(this, labels.get(i), i == selected);
            if (i == selected) selView = c;
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ch.choose(k);
                }
            });
            LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = Ui.dp(this, 8);
            row.addView(c, p);
        }
        langBox.addView(hs);
    }

    private void buildSeasons() {
        seasonBox.removeAllViews();
        if (seasons.size() < 2) return;
        TextView h = Ui.text(this, "Temporadas", 13, Ui.MUTE, true);
        h.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        seasonBox.addView(h);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setClipChildren(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 3), Ui.dp(this, 4), Ui.dp(this, 3), Ui.dp(this, 6));
        hs.addView(row);
        for (int i = 0; i < seasons.size(); i++) {
            final int k = i;
            TextView c = Ui.chip(this, "Temporada " + seasons.get(i).n, i == curSeason);
            if (i == curSeason) selView = c;
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    curSeason = k;
                    buildSeasons();
                    buildEps();
                    if (selView != null) selView.requestFocus();
                }
            });
            LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.rightMargin = Ui.dp(this, 8);
            row.addView(c, p);
        }
        seasonBox.addView(hs);
    }

    private void buildEps() {
        epsBox.removeAllViews();
        focusEp = null;
        if (seasons.isEmpty()) return;
        final Repo.Season s = seasons.get(curSeason);
        JSONObject p = Repo.prog(this, Repo.id(it));
        TextView h = Ui.text(this, "Episodios", 13, Ui.MUTE, true);
        h.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        epsBox.addView(h);
        for (final Repo.Ep ep : s.eps) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setFocusable(true);
            r.setClickable(true);
            r.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            float rad = Ui.dp(this, 10);
            r.setBackground(Ui.states(Ui.shape(0xFF30303F, Ui.RING, Ui.dp(this, 3), rad), Ui.shape(Ui.BG2, Ui.LINE, Ui.dp(this, 1), rad)));
            Ui.pop(r, 1.015f, Ui.dp(this, 4));
            TextView n = Ui.text(this, String.valueOf(ep.n), 22, Ui.ACC, true);
            n.setGravity(Gravity.CENTER);
            r.addView(n, new LinearLayout.LayoutParams(Ui.dp(this, 40), ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            String nm = ep.titulo.isEmpty() ? "Episodio " + ep.n : ep.titulo;
            t.addView(Ui.text(this, nm, 16, Ui.TX, true));
            if (!ep.sinopsis.isEmpty()) {
                TextView sy = Ui.text(this, ep.sinopsis, 13, Ui.MUTE, false);
                sy.setMaxLines(2);
                sy.setEllipsize(TextUtils.TruncateAt.END);
                t.addView(sy);
            }
            boolean cur = p != null && p.optInt("s", -1) == s.n && p.optInt("e", -1) == ep.n;
            if (cur && p.optLong("d", 0) > 0) {
                ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(100);
                pb.setProgress((int) (p.optLong("t", 0) * 100 / p.optLong("d", 1)));
                pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACC));
                t.addView(pb, new LinearLayout.LayoutParams(Ui.dp(this, 160), Ui.dp(this, 4)));
            }
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            r.addView(t, tp);
            r.addView(Ui.text(this, "▶", 18, Ui.MUTE, false));
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    lastS = s.n;
                    lastE = ep.n;
                    Nav.play(DetailActivity.this, it, s.n, ep.n, Repo.pick(ep.srv, lang));
                }
            });
            LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = Ui.dp(this, 8);
            epsBox.addView(r, rp);
            if (s.n == lastS && ep.n == lastE) focusEp = r;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Trailer.dismiss();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (it != null && playBtn != null) {
            updateButtons();
            if (serie) {
                buildEps();
                if (focusEp != null) focusEp.requestFocus();
            }
        }
    }
}
