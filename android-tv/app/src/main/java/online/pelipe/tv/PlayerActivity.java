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
                    if (stage == 0 && dur > 4000) {
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
    private final Runnable hideHint = new Runnable() {
        @Override
        public void run() {
            hintTv.setVisibility(View.GONE);
        }
    };

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
        pv.setControllerVisibilityListener(new PlayerView.ControllerVisibilityListener() {
            @Override
            public void onVisibilityChanged(int visibility) {
                titleTv.setVisibility(visibility == View.VISIBLE ? View.VISIBLE : View.GONE);
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
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true);
        DefaultLoadControl lc = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 60000, 1500, 3000)
                .build();
        player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setLoadControl(lc)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(ds))
                .build();
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(), false); // sin manejo de foco de audio: otras apps/sonidos del sistema ya no lo pausan
        player.setHandleAudioBecomingNoisy(true);
        pv.setPlayer(player);
        player.addListener(new Player.Listener() {
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

    private void onEnded() {
        if (ended) return;
        ended = true;
        if (it != null && Repo.isSerie(it)) {
            // Siguiente episodio
            List<Repo.Season> ss = Repo.seasons(it);
            Repo.Ep next = null;
            int ns = 0;
            for (int i = 0; i < ss.size() && next == null; i++) {
                Repo.Season s = ss.get(i);
                if (s.n < season) continue;
                for (Repo.Ep e : s.eps) {
                    if ((s.n == season && e.n > ep) || s.n > season) {
                        next = e;
                        ns = s.n;
                        break;
                    }
                }
            }
            if (next != null) {
                Repo.putProg(this, id, 0, 0, ns, next.n);
                Repo.Srv sv = Repo.pick(next.srv, lang);
                if (sv != null && sv.direct) {
                    season = ns;
                    ep = next.n;
                    lang = sv.lang;
                    Repo.dropProg(this, id);
                    Repo.putProg(this, id, 0, 0, ns, next.n);
                    load(sv.url);
                    return;
                }
                if (sv != null) {
                    Nav.play(this, it, ns, next.n, sv);
                    finish();
                    return;
                }
            }
        }
        if (id != null) Repo.dropProg(this, id);
        finish();
    }

    private void save() {
        if (player == null || id == null || ended) return;
        long d = player.getDuration() / 1000, t = player.getCurrentPosition() / 1000;
        if (d <= 0) return;
        if (t > d * 0.95) {
            if (it != null && Repo.isSerie(it)) return; // el siguiente episodio se guarda al terminar
            Repo.dropProg(this, id);
        } else if (t > 5) {
            Repo.putProg(this, id, t, d, season, ep);
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(saver);
        h.removeCallbacks(watchdog);
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
