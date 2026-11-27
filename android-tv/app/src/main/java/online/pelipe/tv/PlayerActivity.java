package online.pelipe.tv;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Format;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;
import android.text.TextUtils;
import org.json.JSONObject;

import java.util.List;

/** Reproductor nativo (ExoPlayer) para videos .mp4 / .webm / HLS. Se maneja con flechas, OK y Atrás. */
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView pv;
    private TextView titleTv, hintTv, errTv, infoTv;
    private LinearLayout errBox;
    private final Handler h = new Handler(Looper.getMainLooper());
    private JSONObject it;
    private String id, lang;
    private int season, ep;
    private boolean ended = false;
    // Intro y créditos (marcas del panel web) + siguiente episodio con cuenta regresiva
    private double introA = -1, introB = -1, finD = -1;
    private TextView introBtn, nextCount;
    private LinearLayout nextBox;
    private android.widget.ProgressBar nextBar;
    private android.widget.ImageView nextImg;
    private TextView nextTitleTv;
    private boolean nextCancelled = false;
    private Repo.Ep nextEp;
    private int nextSeason;

    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            save();
            h.postDelayed(this, 5000);
        }
    };
    // Vigilante: replica lo que haces a mano. Si debería estar reproduciendo y la imagen no avanza,
    // primero hace pausa y play solo (4 s); si sigue, vuelve a pedir los datos (10 s) y luego reabre la conexión (18 s).
    private long lastPos = -1, stuckSince = 0, loadedAt = 0;
    private int stage = 0;
    private final Runnable resume = new Runnable() {
        @Override
        public void run() {
            if (player != null) player.play();
        }
    };
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (player != null && errBox.getVisibility() != View.VISIBLE) {
                int st = player.getPlaybackState();
                long pos = player.getCurrentPosition();
                boolean want = player.getPlayWhenReady() && st != Player.STATE_ENDED && st != Player.STATE_IDLE;
                long now = System.currentTimeMillis();
                // Al abrir un video se le da tiempo (20 s) antes de intervenir
                boolean grace = pos <= 0 && now - loadedAt < 20000;
                if (want && pos == lastPos && !grace) {
                    if (stuckSince == 0) stuckSince = now;
                    long dur = now - stuckSince;
                    if (stage == 0 && dur > 3000) {
                        stage = 1;
                        player.pause();
                        h.postDelayed(resume, 500);
                    } else if (stage == 1 && dur > 10000) {
                        stage = 2;
                        player.seekTo(pos);
                        player.play();
                    } else if (stage == 2 && dur > 18000) {
                        stage = 3;
                        player.prepare();
                        player.seekTo(pos);
                        player.play();
                    } else if (stage == 3 && dur > 30000) {
                        stage = 0;
                        stuckSince = now;
                    }
                } else {
                    stuckSince = 0;
                    stage = 0;
                }
                lastPos = pos;
            }
            h.postDelayed(this, 1000);
        }
    };
    // Mientras carga, muestra la velocidad medida de la conexión y el peso del video (sirve para saber si es la red o el archivo)
    private final Runnable infoTick = new Runnable() {
        @Override
        public void run() {
            if (player != null) {
                boolean buf = player.getPlaybackState() == Player.STATE_BUFFERING;
                boolean show = (buf || pv.isControllerFullyVisible()) && errBox.getVisibility() != View.VISIBLE;
                if (show) {
                    long bps = DefaultBandwidthMeter.getSingletonInstance(PlayerActivity.this).getBitrateEstimate();
                    Format vf = player.getVideoFormat();
                    long ahead = Math.max(0, (player.getBufferedPosition() - player.getCurrentPosition()) / 1000);
                    StringBuilder t = new StringBuilder(buf ? "Cargando…   " : "");
                    t.append("Colchón ").append(ahead).append(" s");
                    if (bps > 0) t.append("   ·   Conexión ≈ ").append(String.format(java.util.Locale.US, "%.1f", bps / 1e6)).append(" Mbps");
                    if (vf != null && vf.bitrate > 0) t.append("   ·   Video ≈ ").append(String.format(java.util.Locale.US, "%.1f", vf.bitrate / 1e6)).append(" Mbps");
                    if (vf != null && vf.height > 0) t.append("   ·   ").append(vf.width).append("×").append(vf.height);
                    if (vf != null && vf.codecs != null) t.append(" ").append(vf.codecs);
                    Format af = player.getAudioFormat();
                    if (af != null) t.append("\nAudio ").append(af.sampleMimeType == null ? "?" : af.sampleMimeType.replace("audio/", "")).append(" ").append(af.channelCount).append("ch");
                    Runtime rt = Runtime.getRuntime();
                    t.append("   ·   Memoria ").append((rt.totalMemory() - rt.freeMemory()) >> 20).append("/").append(rt.maxMemory() >> 20).append(" MB");
                    t.append("   ·   Estado ").append(new String[]{"", "inactivo", "cargando", "listo", "fin"}[Math.max(0, Math.min(4, player.getPlaybackState()))]);
                    androidx.media3.exoplayer.DecoderCounters dc = player.getVideoDecoderCounters();
                    if (dc != null) {
                        dc.ensureUpdated();
                        t.append("   ·   Frames perdidos ").append(dc.droppedBufferCount);
                    }
                    infoTv.setText(t.toString());
                    infoTv.setVisibility(View.VISIBLE);
                } else {
                    infoTv.setVisibility(View.GONE);
                }
            }
            h.postDelayed(this, 1000);
        }
    };
    /** Oculta los controles a los pocos segundos si el video está reproduciéndose (media3 a veces los deja fijos tras pausar/reanudar). */
    private final Runnable autoHide = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && pv != null && pv.isControllerFullyVisible()) pv.hideController();
        }
    };

    private void armAutoHide() {
        h.removeCallbacks(autoHide);
        h.postDelayed(autoHide, 4500);
    }

    private final Runnable hideHint = new Runnable() {
        @Override
        public void run() {
            hintTv.setVisibility(View.GONE);
        }
    };

    /** Cada medio segundo: botón "Saltar intro" y caja de "Siguiente episodio" según el minuto del video. */
    private final Runnable markTick = new Runnable() {
        @Override
        public void run() {
            try {
                tickMarks();
            } catch (Exception ignored) {
            }
            h.postDelayed(this, 500);
        }
    };

    private void tickMarks() {
        if (player == null || errBox.getVisibility() == View.VISIBLE || ended) return;
        long dm = player.getDuration(), pm = player.getCurrentPosition();
        if (dm <= 0) return;
        double d = dm / 1000.0, t = pm / 1000.0;
        boolean inIntro = introA >= 0 && t >= introA - 0.2 && t < introB - 1;
        introBtn.setVisibility(inIntro ? View.VISIBLE : View.GONE);
        if (nextEp != null && d >= 90) {
            boolean marked = finD > 30 && finD < d - 3;
            double fin = marked ? Math.min(d, finD + 15) : d;
            double av = marked ? fin - finD : 40;
            double r = fin - t;
            if (r > av + 1) {
                nextCancelled = false;
                hideNext();
            } else if (!nextCancelled) {
                if (r <= 0.3) {
                    if (marked) goNextNow(); // sin marcas, al terminar avanza solo (onEnded)
                } else {
                    showNext(av, r);
                }
            }
        } else {
            hideNext();
        }
    }

    private void showNext(double av, double r) {
        if (nextBox.getVisibility() != View.VISIBLE) {
            nextTitleTv.setText("T" + nextSeason + " · E" + nextEp.n + (nextEp.titulo.isEmpty() ? "" : " — " + nextEp.titulo));
            String img = it == null ? "" : (Repo.fondo(it).isEmpty() ? Repo.poster(it) : Repo.fondo(it));
            if (!img.isEmpty()) Glide.with(this).load(img).override(Ui.dp(this, 160), Ui.dp(this, 90)).centerCrop().dontAnimate().into(nextImg);
            nextBox.setVisibility(View.VISIBLE);
        }
        nextCount.setText("Empieza en " + (int) Math.max(1, Math.ceil(r)) + " s");
        nextBar.setProgress((int) Math.min(1000, Math.max(0, (1 - r / av) * 1000)));
    }

    private void hideNext() {
        if (nextBox != null && nextBox.getVisibility() != View.GONE) nextBox.setVisibility(View.GONE);
    }

    private void goNextNow() {
        hideNext();
        ended = true;
        if (!playNext()) ended = false;
    }

    private void skipIntro() {
        if (player == null || introB < 0) return;
        player.seekTo((long) (introB * 1000));
        player.play();
        introBtn.setVisibility(View.GONE);
    }

    private boolean overlayVisible(View v) {
        return v != null && v.getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.immersive(this);
        Intent in = getIntent();
        id = in.getStringExtra("id");
        season = in.getIntExtra("s", 0);
        ep = in.getIntExtra("e", 0);
        lang = in.getStringExtra("lang");
        if (lang == null) lang = "";
        String url = in.getStringExtra("url");
        it = id == null ? null : Repo.byId(id);
        if (url == null) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        pv = new PlayerView(this);
        pv.setUseController(true);
        pv.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        pv.setControllerShowTimeoutMs(4000);
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        titleTv = Ui.text(this, "", 18, 0xFFFFFFFF, true);
        titleTv.setShadowLayer(8, 0, 0, 0xFF000000);
        titleTv.setPadding(Ui.dp(this, 32), Ui.dp(this, 20), Ui.dp(this, 32), 0);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.TOP | Gravity.START;
        root.addView(titleTv, tp);
        Ui.watermark(root);
        pv.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                titleTv.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE);
                if (visibility == View.VISIBLE) armAutoHide();
                else h.removeCallbacks(autoHide);
            }
        });

        hintTv = Ui.text(this, "", 30, 0xFFFFFFFF, true);
        hintTv.setShadowLayer(10, 0, 0, 0xFF000000);
        hintTv.setVisibility(View.GONE);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.CENTER;
        root.addView(hintTv, hp);

        infoTv = Ui.text(this, "", 14, 0xFFFFFFFF, false);
        infoTv.setShadowLayer(8, 0, 0, 0xFF000000);
        infoTv.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 28));
        infoTv.setVisibility(View.GONE);
        FrameLayout.LayoutParams ifp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ifp.gravity = Gravity.BOTTOM | Gravity.START;
        root.addView(infoTv, ifp);

        // "Saltar intro ›" (abajo a la derecha, dentro de la franja marcada)
        introBtn = Ui.button(this, "Saltar intro  ›", false);
        introBtn.setVisibility(View.GONE);
        introBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                skipIntro();
            }
        });
        FrameLayout.LayoutParams ibp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ibp.gravity = Gravity.BOTTOM | Gravity.END;
        ibp.rightMargin = Ui.dp(this, 36);
        ibp.bottomMargin = Ui.dp(this, 110);
        root.addView(introBtn, ibp);

        // Caja de "Siguiente episodio" con cuenta regresiva
        nextBox = new LinearLayout(this);
        nextBox.setOrientation(LinearLayout.VERTICAL);
        nextBox.setBackground(Ui.shape(0xF014141C, 0x55FFFFFF, Ui.dp(this, 1), Ui.dp(this, 14)));
        nextBox.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        nextBox.setVisibility(View.GONE);
        LinearLayout nrow = new LinearLayout(this);
        nrow.setOrientation(LinearLayout.HORIZONTAL);
        nrow.setGravity(Gravity.CENTER_VERTICAL);
        nextImg = new android.widget.ImageView(this);
        nextImg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        nextImg.setBackgroundColor(Ui.BG3);
        nrow.addView(nextImg, new LinearLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 72)));
        LinearLayout ntx = new LinearLayout(this);
        ntx.setOrientation(LinearLayout.VERTICAL);
        ntx.setPadding(Ui.dp(this, 14), 0, 0, 0);
        TextView nsm = Ui.text(this, "SIGUIENTE EPISODIO", 11, Ui.MUTE, true);
        nextTitleTv = Ui.text(this, "", 15, 0xFFFFFFFF, true);
        nextTitleTv.setMaxLines(2);
        nextTitleTv.setEllipsize(TextUtils.TruncateAt.END);
        nextCount = Ui.text(this, "", 13, Ui.ACC2, true);
        ntx.addView(nsm);
        ntx.addView(nextTitleTv);
        ntx.addView(nextCount);
        nrow.addView(ntx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nextBox.addView(nrow);
        nextBar = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        nextBar.setMax(1000);
        nextBar.setProgressDrawable(new android.graphics.drawable.ClipDrawable(Ui.shape(Ui.ACC, 0, 0, Ui.dp(this, 2)), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL));
        nextBar.setBackground(Ui.shape(0x33FFFFFF, 0, 0, Ui.dp(this, 2)));
        LinearLayout.LayoutParams nbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 4));
        nbp.topMargin = Ui.dp(this, 12);
        nextBox.addView(nextBar, nbp);
        LinearLayout nbtn = new LinearLayout(this);
        nbtn.setOrientation(LinearLayout.HORIZONTAL);
        TextView nyes = Ui.button(this, "▶  Reproducir ahora", true);
        nyes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goNextNow();
            }
        });
        TextView nno = Ui.button(this, "Cancelar", false);
        nno.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextCancelled = true;
                hideNext();
            }
        });
        nbtn.addView(nyes, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams nnp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nnp.leftMargin = Ui.dp(this, 8);
        nbtn.addView(nno, nnp);
        LinearLayout.LayoutParams nbtp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nbtp.topMargin = Ui.dp(this, 12);
        nextBox.addView(nbtn, nbtp);
        if (Ui.tv(this)) {
            TextView nh = Ui.text(this, "OK: reproducir ahora   ·   ↓: cancelar", 11, Ui.MUTE, false);
            nh.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nhp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nhp.topMargin = Ui.dp(this, 8);
            nextBox.addView(nh, nhp);
        }
        FrameLayout.LayoutParams nxp = new FrameLayout.LayoutParams(Ui.dp(this, 400), ViewGroup.LayoutParams.WRAP_CONTENT);
        nxp.gravity = Gravity.BOTTOM | Gravity.END;
        nxp.rightMargin = Ui.dp(this, 36);
        nxp.bottomMargin = Ui.dp(this, 100);
        root.addView(nextBox, nxp);

        errBox = new LinearLayout(this);
        errBox.setOrientation(LinearLayout.VERTICAL);
        errBox.setGravity(Gravity.CENTER);
        errBox.setBackgroundColor(0xE6000000);
        errBox.setVisibility(View.GONE);
        errTv = Ui.text(this, "", 17, 0xFFFFFFFF, false);
        errTv.setGravity(Gravity.CENTER);
        errTv.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), Ui.dp(this, 16));
        errBox.addView(errTv);
        TextView retry = Ui.button(this, "Reintentar", true);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errBox.setVisibility(View.GONE);
                player.prepare();
                player.play();
            }
        });
        errBox.addView(retry);
        TextView back = Ui.button(this, "Volver", false);
        LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(this, 10);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        errBox.addView(back, bp);
        root.addView(errBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Ajustes de las primeras versiones (los que mejor se portaron en tu TV)
        DefaultHttpDataSource.Factory ds = new DefaultHttpDataSource.Factory()
                .setUserAgent("PelipeApp/2")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(8000) // una conexión muerta (tras una pausa larga) se detecta rápido
                .setAllowCrossProtocolRedirects(true);
        // Colchón acotado también por memoria (48 MB): en la TV, con películas pesadas, un colchón de 60 s llenaba la memoria de la app
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 45000, 1500, 3000)
                .setTargetBufferBytes(48 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();
        player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setLoadControl(lc)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(ds).setLoadErrorHandlingPolicy(new androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(6)))
                .build();
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), false); // sin manejo de foco de audio: otras apps/sonidos del sistema ya no lo pausan
        player.setHandleAudioBecomingNoisy(true);
        pv.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                if (playing) armAutoHide();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) onEnded();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                errTv.setText("No se pudo reproducir este video.\nRevisa tu conexión e inténtalo de nuevo.");
                errBox.setVisibility(View.VISIBLE);
                errBox.getChildAt(1).requestFocus();
            }
        });
        load(url);
    }

    private void load(String url) {
        ended = false;
        loadedAt = System.currentTimeMillis();
        errBox.setVisibility(View.GONE);
        StringBuilder t = new StringBuilder();
        if (it != null) t.append(Repo.titulo(it));
        if (it != null && Repo.isSerie(it) && season > 0) {
            Repo.Ep e = Nav.findEp(Repo.seasons(it), season, ep);
            t.append("   ·   T").append(season).append(" E").append(ep);
            if (e != null && !e.titulo.isEmpty()) t.append("  ").append(e.titulo);
        }
        titleTv.setText(t.toString());
        // Marcas de intro/créditos de este episodio y cuál sigue
        double[] mk = Repo.marks(it, season, ep);
        introA = mk[0];
        introB = mk[1];
        finD = mk[2];
        nextCancelled = false;
        introBtn.setVisibility(View.GONE);
        hideNext();
        findNext();
        player.setMediaItem(MediaItem.fromUri(url));
        // Reanudar donde se quedó (mismo título y mismo episodio)
        long start = 0;
        JSONObject p = id == null ? null : Repo.prog(this, id);
        if (p != null && p.optInt("s", 0) == season && p.optInt("e", 0) == ep && p.optLong("t", 0) > 10) {
            start = p.optLong("t") * 1000;
        }
        if (start > 0) player.seekTo(start);
        player.prepare();
        player.play();
        pv.showController();
    }

    /** Busca el episodio que sigue al actual (queda en nextEp / nextSeason; null si no hay). */
    private void findNext() {
        nextEp = null;
        nextSeason = 0;
        if (it == null || !Repo.isSerie(it) || season <= 0) return;
        List<Repo.Season> ss = Repo.seasons(it);
        for (int i = 0; i < ss.size() && nextEp == null; i++) {
            Repo.Season s = ss.get(i);
            if (s.n < season) continue;
            for (Repo.Ep e : s.eps) {
                if ((s.n == season && e.n > ep) || s.n > season) {
                    nextEp = e;
                    nextSeason = s.n;
                    break;
                }
            }
        }
    }

    /** Pasa al siguiente episodio. Devuelve false si no hay o no tiene video disponible. */
    /** Marca el episodio actual como visto completo (se muestra "Visto" en su botón). */
    private void markWatched() {
        if (player == null || id == null || it == null || !Repo.isSerie(it) || season <= 0) return;
        long d = player.getDuration() / 1000;
        if (d > 0) Repo.putEpProg(this, id, season, ep, d, d);
    }

    private boolean playNext() {
        markWatched();
        findNext();
        if (nextEp == null) return false;
        Repo.Ep next = nextEp;
        int ns = nextSeason;
        Repo.putProg(this, id, 0, 0, ns, next.n);
        Repo.Srv sv = Repo.pick(next.srv, lang);
        if (sv != null && sv.direct) {
            season = ns;
            ep = next.n;
            lang = sv.lang;
            Repo.dropProg(this, id);
            Repo.putProg(this, id, 0, 0, ns, next.n);
            load(sv.url);
            return true;
        }
        if (sv != null) {
            Nav.play(this, it, ns, next.n, sv);
            finish();
            return true;
        }
        return false;
    }

    private void onEnded() {
        if (ended) return;
        ended = true;
        hideNext();
        markWatched();
        if (nextCancelled && nextEp != null && id != null) { // canceló la cuenta: no avanza solo; "Continuar viendo" queda en el siguiente
            Repo.putProg(this, id, 0, 0, nextSeason, nextEp.n);
            finish();
            return;
        }
        if (it != null && Repo.isSerie(it) && playNext()) return;
        if (id != null) Repo.dropProg(this, id);
        finish();
    }

    private void save() {
        if (player == null || id == null || ended) return;
        long d = player.getDuration() / 1000, t = player.getCurrentPosition() / 1000;
        if (d <= 0) return;
        if (t > d * 0.95) {
            if (it != null && Repo.isSerie(it)) {
                Repo.putEpProg(this, id, season, ep, d, d); // visto completo
                return; // el siguiente episodio se guarda al terminar
            }
            Repo.dropProg(this, id);
        } else if (t > 5) {
            Repo.putProg(this, id, t, d, season, ep);
            if (it != null && Repo.isSerie(it)) Repo.putEpProg(this, id, season, ep, t, d);
        }
    }

    private void hint(String s) {
        hintTv.setText(s);
        hintTv.setVisibility(View.VISIBLE);
        h.removeCallbacks(hideHint);
        h.postDelayed(hideHint, 900);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && errBox.getVisibility() != View.VISIBLE) {
            int k = e.getKeyCode();
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean ctl = pv != null && pv.isControllerFullyVisible();
            if (overlayVisible(nextBox) && !ctl) {
                if (ok) {
                    goNextNow();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                    nextCancelled = true;
                    hideNext();
                    return true;
                }
            }
            if (overlayVisible(introBtn) && !ctl && ok) {
                skipIntro();
                return true;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && pv != null && pv.isControllerFullyVisible()) armAutoHide();
        if (player != null && e.getAction() == KeyEvent.ACTION_DOWN && !pv.isControllerFullyVisible() && errBox.getVisibility() != View.VISIBLE) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                    hint("⏪ 10 s");
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    player.seekTo(player.getCurrentPosition() + 10000);
                    hint("10 s ⏩");
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (player.isPlaying()) player.pause();
                    else player.play();
                    pv.showController();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    pv.showController();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        h.removeCallbacks(saver);
        h.postDelayed(saver, 5000);
        h.removeCallbacks(watchdog);
        h.postDelayed(watchdog, 4000);
        h.removeCallbacks(markTick);
        h.postDelayed(markTick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(saver);
        h.removeCallbacks(watchdog);
        h.removeCallbacks(markTick);
        h.removeCallbacks(infoTick);
        save();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
