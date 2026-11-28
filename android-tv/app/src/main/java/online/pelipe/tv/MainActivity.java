package online.pelipe.tv;

import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.view.TextureView;
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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

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
    // Estado del banner a nivel de proceso: se elige una vez por inicio de la app y no se reinicia al volver al inicio
    private static List<JSONObject> heroItems;
    private static int heroIdx = 0;
    private static List<JSONObject> heroPool = new ArrayList<JSONObject>();
    private static final List<JSONObject> heroBag = new ArrayList<JSONObject>();
    private static java.util.Set<String> teaserIds = new java.util.HashSet<String>();
    private static boolean teaserIdsAsked = false;
    private List<String> genrePick;
    private ImageView heroBg;
    private TextureView heroTex;
    private ExoPlayer heroPlayer;
    private boolean animatedOnce = false;
    private boolean teaserTimed = false;
    private TextView hKind, hTitle, hMeta, hSyn, heroTrailer;
    private LinearLayout heroDots;
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
        return Splash.view(this, msg);
    }

    private void start() {
        Repo.load(this, false, new Repo.Listener() {
            @Override
            public void onData() {
                if (isFinishing()) return;
                whenReady();
            }

            @Override
            public void onError(String msg) {
                if (isFinishing()) return;
                showError(msg);
            }
        });
    }

    private static boolean introDone = false;
    private boolean waiting = false;
    private final long t0 = android.os.SystemClock.uptimeMillis();

    /** La primera vez que se abre la app deja ver la animación de entrada (unos 5 s); después entra directo. */
    private void whenReady() {
        if (built || introDone) {
            build();
            return;
        }
        if (waiting) return;
        waiting = true;
        long wait = Math.max(0, 5200 - (android.os.SystemClock.uptimeMillis() - t0));
        Repo.MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                introDone = true;
                build();
            }
        }, wait);
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
        Updater.check(this);
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
        stopTeaser();
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
        // Banner: 5 lugares que van rotando entre TODO el catálogo (películas y series), sin repetir
        // hasta haber pasado por todos. Si ya se sabe qué títulos tienen video corto, se prefieren esos.
        if (heroItems == null) {
            loadTeaserIds();
            heroPool = new ArrayList<JSONObject>();
            for (JSONObject o : every) if (!Repo.fondo(o).isEmpty()) heroPool.add(o);
            if (heroPool.isEmpty()) heroPool.addAll(every);
            heroItems = new ArrayList<JSONObject>();
            int n = Math.min(5, heroPool.size());
            for (int i = 0; i < n; i++) heroItems.add(nextHero());
            heroIdx = 0;
        }
        content.addView(hero());
        content.addView(Pedir.bar(this));

        // Continuar viendo
        JSONObject prog = Repo.progAll(this);
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
            final JSONObject o = Repo.byId(id);
            JSONObject p = prog.optJSONObject(id);
            if (o == null || p == null || !Repo.visible(o)) continue;
            long d = p.optLong("d", 0), t = p.optLong("t", 0);
            int pct = d > 0 ? (int) (t * 100 / d) : 3;
            final String pid = id;
            cards.add(Ui.card(this, o, Ui.cardW(this), pct, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.open(MainActivity.this, o);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    Repo.dropProg(MainActivity.this, pid);
                    snap = Repo.snap(MainActivity.this);
                    Toast.makeText(MainActivity.this, "Quitado de «Continuar viendo»", Toast.LENGTH_SHORT).show();
                }
            }));
        }
        if (!cards.isEmpty()) content.addView(Ui.row(this, "Continuar viendo 👀", cards));

        // Mi lista
        cards = new ArrayList<View>();
        for (String id : Repo.lista(this)) {
            JSONObject o = Repo.byId(id);
            if (o != null && Repo.visible(o)) cards.add(card(o, -1));
        }
        if (!cards.isEmpty()) content.addView(Ui.row(this, "Mi lista ⭐", cards));

        if (!films.isEmpty()) content.addView(Ui.row(this, "Subidas recientes 🍿", cards(every, 20)));
        if (!series.isEmpty()) content.addView(Ui.row(this, "Cajón de Series 📺", cards(series, 20)));

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
        if (topRated.size() >= 3) content.addView(Ui.row(this, "Las más votadas 👍🏻", cards(topRated, 15)));

        // Siempre tres géneros, distintos en cada apertura de la app
        Map<String, List<JSONObject>> byG = new LinkedHashMap<String, List<JSONObject>>();
        for (JSONObject o : every) {
            for (String g : Repo.genres(o)) {
                if (!byG.containsKey(g)) byG.put(g, new ArrayList<JSONObject>());
                byG.get(g).add(o);
            }
        }
        if (genrePick == null) {
            List<String> ok = new ArrayList<String>();
            for (Map.Entry<String, List<JSONObject>> e : byG.entrySet()) if (e.getValue().size() >= 3) ok.add(e.getKey());
            Collections.shuffle(ok);
            genrePick = new ArrayList<String>(ok.subList(0, Math.min(3, ok.size())));
        }
        for (String g : genrePick) {
            List<JSONObject> l = byG.get(g);
            if (l == null || l.size() < 3) continue;
            List<JSONObject> sh = new ArrayList<JSONObject>(l);
            Collections.shuffle(sh);
            content.addView(Ui.row(this, Repo.genreEmoji(g) + " " + g, cards(sh, 20)));
        }
        if (!animatedOnce) {
            animatedOnce = true;
            for (int i = 1; i < content.getChildCount(); i++) Ui.slideIn(content.getChildAt(i), i - 1);
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

    private View hero() {
        final boolean tv = Ui.tv(this);
        int w = getResources().getDisplayMetrics().widthPixels;
        FrameLayout f = new FrameLayout(this);
        f.setClipChildren(true);
        // Recorte duro: el zoom del banner no puede salirse del marco
        f.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline o) {
                o.setRect(0, 0, view.getWidth(), view.getHeight());
            }
        });
        f.setClipToOutline(true);
        f.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, tv ? 320 : 290)));
        heroBg = new ImageView(this);
        heroBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBg.setBackgroundColor(Ui.BG2);
        f.addView(heroBg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        heroTex = new TextureView(this);
        heroTex.setAlpha(0f);
        f.addView(heroTex, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View left = new View(this);
        left.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xF20B0B10, 0xB30B0B10, 0x000B0B10}));
        f.addView(left, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View bottom = new View(this);
        bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xFF0B0B10, 0x000B0B10}));
        FrameLayout.LayoutParams bpp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 100));
        bpp.gravity = Gravity.BOTTOM;
        f.addView(bottom, bpp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.BOTTOM);
        info.setClipChildren(false);
        info.setClipToPadding(false);
        int m = Ui.margin(this);
        info.setPadding(m, Ui.dp(this, 16), tv ? 0 : m, Ui.dp(this, 30));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(tv ? (int) (w * 0.55f) : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        f.addView(info, ip);

        hKind = Ui.text(this, "", 12, Ui.ACC2, true);
        hKind.setLetterSpacing(0.12f);
        info.addView(hKind);
        hTitle = Ui.text(this, "", tv ? 32 : 26, Ui.TX, true);
        hTitle.setMaxLines(2);
        hTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hTitle.setPadding(0, Ui.dp(this, 4), 0, 0);
        info.addView(hTitle);
        hMeta = Ui.text(this, "", 13, Ui.MUTE, false);
        hMeta.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 8));
        info.addView(hMeta);
        hSyn = Ui.text(this, "", 14, 0xFFD3D3E0, false);
        hSyn.setMaxLines(tv ? 3 : 2);
        hSyn.setLineSpacing(0, 1.15f);
        hSyn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(hSyn);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        btns.setClipToPadding(false);
        btns.setPadding(Ui.dp(this, 3), Ui.dp(this, 14), Ui.dp(this, 3), Ui.dp(this, 3));
        TextView play = Ui.button(this, "▶  Reproducir", true);
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Nav.quick(MainActivity.this, heroItems.get(heroIdx));
            }
        });
        heroTrailer = Ui.button(this, "Ver tráiler", false);
        heroTrailer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Nav.trailer(MainActivity.this, heroItems.get(heroIdx));
            }
        });
        TextView more = Ui.button(this, tv ? "Más información" : "Info", false);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Ui.open(MainActivity.this, heroItems.get(heroIdx));
            }
        });
        btns.addView(play);
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = Ui.dp(this, 10);
        btns.addView(heroTrailer, tp);
        LinearLayout.LayoutParams mp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = Ui.dp(this, 10);
        btns.addView(more, mp);
        info.addView(btns);
        heroPlay = play;

        // Puntitos del carrusel
        heroDots = new LinearLayout(this);
        heroDots.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.gravity = Gravity.BOTTOM | Gravity.END;
        dp.setMargins(0, 0, m, Ui.dp(this, 12));
        for (int i = 0; i < heroItems.size(); i++) {
            View d = new View(this);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            l.leftMargin = Ui.dp(this, 5);
            heroDots.addView(d, l);
        }
        if (heroItems.size() > 1) f.addView(heroDots, dp);
        showHero(false);
        return f;
    }

    private void showHero(boolean fade) {
        if (heroItems == null || heroItems.isEmpty()) return;
        JSONObject o = heroItems.get(heroIdx);
        hKind.setText(Repo.isSerie(o) ? "SERIE" : "PELÍCULA");
        hTitle.setText(Repo.titulo(o));
        hMeta.setText(Repo.meta(o));
        String syn = Repo.sinopsis(o);
        hSyn.setText(syn);
        hSyn.setVisibility(syn.isEmpty() ? View.GONE : View.VISIBLE);
        heroTrailer.setVisibility(Nav.hasTrailer(o) ? View.VISIBLE : View.GONE);
        String img = Repo.fondo(o).isEmpty() ? Repo.poster(o) : Repo.fondo(o);
        int w = getResources().getDisplayMetrics().widthPixels;
        if (!img.isEmpty()) {
            heroBg.setAlpha(fade ? 0.3f : 1f);
            Glide.with(this).load(img).override(Math.max(w, 800), Ui.dp(this, 320)).centerCrop().dontAnimate().into(heroBg);
            if (fade) android.animation.ObjectAnimator.ofFloat(heroBg, "alpha", 0.3f, 1f).setDuration(600).start();
        }
        // Zoom lento (Ken Burns) mientras se ve el banner
        heroBg.animate().cancel();
        heroBg.setScaleX(1f);
        heroBg.setScaleY(1f);
        heroBg.animate().scaleX(1.09f).scaleY(1.09f).setDuration(12000).setStartDelay(0)
                .setInterpolator(new android.view.animation.LinearInterpolator()).start();
        // Teaser corto (si existe) tras un momento
        stopTeaser();
        Repo.MAIN.postDelayed(teaserRun, 1500);
        scheduleRotate(11000); // sin teaser: cambia a los ~11 s; con teaser se ajusta a 3 repeticiones
        for (int i = 0; i < heroDots.getChildCount(); i++) {
            heroDots.getChildAt(i).setBackground(Ui.shape(i == heroIdx ? Ui.ACC2 : 0x66FFFFFF, 0, 0, Ui.dp(this, 4)));
        }
    }

    private final Runnable teaserRun = new Runnable() {
        @Override
        public void run() {
            startTeaser();
        }
    };

    /** URL del teaser: campo "teaser" del título o la convención teasers/<id>.mp4 en R2. */
    private String teaserUrl(JSONObject o) {
        String u = Repo.str(o, "teaser");
        if (u.startsWith("http")) return u;
        // el parámetro cambia cada día: así un "no existe" viejo guardado en la caché de Cloudflare se refresca solo
        return "https://media.pelipe.online/teasers/" + Repo.id(o) + ".mp4?d=" + (System.currentTimeMillis() / 86400000L);
    }

    private void stopTeaser() {
        Repo.MAIN.removeCallbacks(teaserRun);
        if (heroPlayer != null) {
            try {
                heroPlayer.release();
            } catch (Exception ignored) {
            }
            heroPlayer = null;
        }
        if (heroTex != null) {
            heroTex.animate().cancel();
            heroTex.setAlpha(0f);
        }
    }

    private void startTeaser() {
        if (heroItems == null || heroItems.isEmpty() || heroTex == null || heroPlayer != null) return;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null && cm.isActiveNetworkMetered()) return; // con datos móviles no gastamos
        } catch (Exception ignored) {
        }
        String u = teaserUrl(heroItems.get(heroIdx));
        teaserTimed = false;
        ExoPlayer p = new ExoPlayer.Builder(this).build();
        p.setVolume(0f);
        p.setRepeatMode(Player.REPEAT_MODE_OFF); // una sola reproducción
        p.setVideoTextureView(heroTex);
        p.addListener(new Player.Listener() {
            @Override
            public void onVideoSizeChanged(VideoSize vs) {
                cropTeaser(vs);
            }

            @Override
            public void onRenderedFirstFrame() {
                if (heroTex != null) heroTex.animate().alpha(1f).setDuration(700).start();
                if (heroPlayer != null && !teaserTimed) {
                    teaserTimed = true; // el temporizador se programa una sola vez por video
                    long d = heroPlayer.getDuration();
                    // se ve una sola vez y luego pasa al siguiente título
                    scheduleRotate(d > 0 ? Math.max(6000L, d + 300) : 12000L);
                }
            }

            @Override
            public void onPlayerError(PlaybackException e) {
                Repo.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        stopTeaser(); // sin teaser: queda la imagen
                        scheduleRotate(6000);
                    }
                });
            }
        });
        p.setMediaItem(MediaItem.fromUri(u));
        p.prepare();
        p.play();
        heroPlayer = p;
    }

    /** Recorte tipo "cover" para que el video llene el banner sin deformarse. */
    private void cropTeaser(VideoSize vs) {
        if (heroTex == null || vs.width <= 0 || vs.height <= 0) return;
        float w = heroTex.getWidth(), h = heroTex.getHeight();
        if (w <= 0 || h <= 0) return;
        float a = (float) vs.width / vs.height, b = w / h;
        float sx = a > b ? a / b : 1f, sy = a > b ? 1f : b / a;
        Matrix m = new Matrix();
        m.setScale(sx, sy, w / 2f, h / 2f);
        heroTex.setTransform(m);
    }

    /** Siguiente título del banner: bolsa mezclada de todo el catálogo; no repite hasta vaciarla. */
    private JSONObject nextHero() {
        if (heroBag.isEmpty()) {
            List<JSONObject> pref = new ArrayList<JSONObject>();
            if (!teaserIds.isEmpty()) {
                for (JSONObject o : heroPool) if (teaserIds.contains(Repo.id(o))) pref.add(o);
            }
            heroBag.addAll(pref.size() >= 8 ? pref : heroPool);
            Collections.shuffle(heroBag);
            if (heroItems != null && heroBag.size() > heroItems.size()) heroBag.removeAll(heroItems);
        }
        return heroBag.remove(0);
    }

    /** Lista de títulos que tienen teaser (teasers/index.json en R2). Se guarda para el próximo inicio. */
    private void loadTeaserIds() {
        if (teaserIdsAsked) return;
        teaserIdsAsked = true;
        try {
            String saved = Repo.sp(this).getString("teaser_ids", "");
            for (String s : saved.split(",")) if (!s.isEmpty()) teaserIds.add(s);
        } catch (Exception ignored) {
        }
        final android.content.Context app = getApplicationContext();
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String body = Repo.http("GET", "https://media.pelipe.online/teasers/index.json?h=" + (System.currentTimeMillis() / 3600000L), null, 8000);
                    org.json.JSONArray a = new JSONObject(body).getJSONArray("ids");
                    final java.util.HashSet<String> ids = new java.util.HashSet<String>();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < a.length(); i++) {
                        String id = a.optString(i, "");
                        if (id.isEmpty()) continue;
                        ids.add(id);
                        if (sb.length() > 0) sb.append(',');
                        sb.append(id);
                    }
                    Repo.sp(app).edit().putString("teaser_ids", sb.toString()).apply();
                    Repo.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            teaserIds = ids;
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            if (heroItems != null && heroItems.size() > 1) {
                heroIdx = (heroIdx + 1) % heroItems.size();
                // el lugar que sigue se renueva con un título que aún no se ha mostrado
                if (heroPool.size() > heroItems.size()) heroItems.set(heroIdx, nextHero());
                showHero(true);
            }
        }
    };

    private void scheduleRotate(long ms) {
        Repo.MAIN.removeCallbacks(rotate);
        Repo.MAIN.postDelayed(rotate, ms);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.resume(this);
        Sync.pull(this, new Runnable() {
            @Override
            public void run() {
                if (built && !isFinishing() && !Repo.all.isEmpty()) fill();
            }
        });
        // Al volver del reproductor o de una ficha: refrescar "Continuar viendo" / "Mi lista" si cambiaron
        if (built && !snap.equals(Repo.snap(this)) && !Repo.all.isEmpty()) {
            fill();
            if (heroPlay != null) heroPlay.requestFocus();
        }
        // TV: al volver (atrás o desde otra pestaña) el foco debe quedar en "Inicio", no en la pestaña que se había visitado
        if (built && Ui.tv(this)) {
            View f = getCurrentFocus();
            Object tg = f == null ? null : f.getTag();
            if (tg instanceof String && ((String) tg).startsWith("tab:") && !"tab:inicio".equals(tg)) {
                View home = getWindow().getDecorView().findViewWithTag("tab:inicio");
                if (home != null) home.requestFocus();
            }
        }
        Repo.MAIN.removeCallbacks(autoSync);
        Repo.MAIN.postDelayed(autoSync, 10000);
        Repo.MAIN.removeCallbacks(rotate);
        if (built) {
            scheduleRotate(11000);
            Repo.MAIN.removeCallbacks(teaserRun);
            Repo.MAIN.postDelayed(teaserRun, 1500);
        }
    }

    /** Sincronización automática con la cuenta cada 10 s mientras la app está abierta. */
    private final Runnable autoSync = new Runnable() {
        @Override
        public void run() {
            Sync.flush(MainActivity.this);
            Sync.pullNow(MainActivity.this, new Runnable() {
                @Override
                public void run() {
                    if (built && !isFinishing() && !Repo.all.isEmpty()) fill();
                }
            });
            Repo.MAIN.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        Sync.flush(this);
        Repo.MAIN.removeCallbacks(autoSync);
        Repo.MAIN.removeCallbacks(rotate);
        stopTeaser();
        Preview.hide();
        Trailer.dismiss();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTeaser();
    }
}
