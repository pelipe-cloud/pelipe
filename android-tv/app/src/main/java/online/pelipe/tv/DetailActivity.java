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
    private LinearLayout langBox, epsBox, seasonBox;
    private View selView, focusEp;
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

        // Botones
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        btns.setClipToPadding(false);
        btns.setPadding(Ui.dp(this, 3), Ui.dp(this, 14), Ui.dp(this, 3), Ui.dp(this, 10));
        playBtn = Ui.button(this, "▶  Reproducir", true);
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (serie) Nav.quick(DetailActivity.this, it);
                else if (!srvs.isEmpty()) Nav.play(DetailActivity.this, it, 0, 0, srvs.get(curSrv));
            }
        });
        btns.addView(playBtn);
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
        col.addView(btns);

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
        buildLang();
        if (serie) {
            buildSeasons();
            buildEps();
        }
        playBtn.requestFocus();
    }

    private void updateButtons() {
        JSONObject p = Repo.prog(this, Repo.id(it));
        String label;
        if (serie) {
            if (p != null && p.optInt("s", 0) > 0) label = "▶  Continuar T" + p.optInt("s") + " E" + p.optInt("e");
            else label = "▶  Reproducir T" + (seasons.isEmpty() ? 1 : seasons.get(0).n) + " E" + (seasons.isEmpty() ? 1 : seasons.get(0).eps.get(0).n);
        } else {
            long t = p == null ? 0 : p.optLong("t", 0);
            label = t > 10 ? "▶  Continuar (" + Repo.clock(t) + ")" : "▶  Reproducir";
        }
        playBtn.setText(label);
        listBtn.setText(Repo.inLista(this, Repo.id(it)) ? "✓  En mi lista" : "+  Mi lista");
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
