package online.pelipe.tv;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

/** Tráiler en ventana flotante (YouTube incrustado) sobre la pantalla actual. Si no se puede incrustar, usa el método de siempre. */
final class Trailer {
    private static FrameLayout overlay;
    private static WebView web;
    private static OnBackPressedCallback back;

    static void show(final Activity a, final JSONObject it) {
        if (!(a instanceof AppCompatActivity)) {
            Nav.trailerExternal(a, it);
            return;
        }
        dismiss();
        final ViewGroup content = (ViewGroup) a.findViewById(android.R.id.content);
        if (content == null) {
            Nav.trailerExternal(a, it);
            return;
        }
        final boolean tv = Ui.tv(a);
        int sw = a.getResources().getDisplayMetrics().widthPixels;
        int w = tv ? (int) (sw * 0.62f) : sw - Ui.dp(a, 24);
        int h = w * 9 / 16;

        overlay = new FrameLayout(a);
        overlay.setBackgroundColor(0xE6000000);
        overlay.setClickable(true);
        overlay.setFocusable(false);
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        overlay.addView(col, cp);

        FrameLayout box = new FrameLayout(a);
        box.setBackground(Ui.shape(Color.BLACK, Ui.LINE, Ui.dp(a, 1), Ui.dp(a, 14)));
        box.setClipToOutline(true);
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), Ui.dp(view.getContext(), 14));
            }
        });
        web = new WebView(a);
        web.setBackgroundColor(Color.BLACK);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setMediaPlaybackRequiresUserGesture(false);
        final Bridge bridge = new Bridge(a, it);
        web.addJavascriptInterface(bridge, "PelipeTrailer");
        box.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(box, new LinearLayout.LayoutParams(w, h));

        final TextView close = Ui.button(a, "✕  Cerrar", false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        web.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, android.view.KeyEvent ev) {
                if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (ev.getAction() == android.view.KeyEvent.ACTION_UP) close.requestFocus();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        xp.topMargin = Ui.dp(a, 16);
        col.addView(close, xp);

        content.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(220).start();
        box.setScaleX(0.92f);
        box.setScaleY(0.92f);
        box.animate().scaleX(1f).scaleY(1f).setDuration(260).start();

        back = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                dismiss();
            }
        };
        ((AppCompatActivity) a).getOnBackPressedDispatcher().addCallback((AppCompatActivity) a, back);

        final WebView mine = web;
        Repo.trailerId(a, it, new Repo.TrailerCb() {
            @Override
            public void done(String id) {
                if (web != mine || mine == null) return; // ya se cerró
                if (id.isEmpty()) {
                    dismiss();
                    android.widget.Toast.makeText(a, "No hay tráiler disponible", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                bridge.id = id;
                String html = "<!doctype html><html><head><meta name=viewport content='width=device-width,initial-scale=1'>"
                        + "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head>"
                        + "<body><div id=p></div><script src='https://www.youtube.com/iframe_api'></script><script>"
                        + "function onYouTubeIframeAPIReady(){new YT.Player('p',{videoId:'" + id + "',"
                        + "playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1,fs:0,origin:'https://pelipe.online'},"
                        + "events:{onReady:function(e){e.target.playVideo()},onError:function(e){PelipeTrailer.err()}}});}"
                        + "setTimeout(function(){if(!window.YT||!YT.Player){PelipeTrailer.err()}},9000);"
                        + "</script></body></html>";
                mine.loadDataWithBaseURL("https://pelipe.online/", html, "text/html", "utf-8", null);
            }
        });
        web.requestFocus();
    }

    static boolean isOpen() {
        return overlay != null;
    }

    static void dismiss() {
        if (back != null) {
            back.remove();
            back = null;
        }
        if (web != null) {
            try {
                web.stopLoading();
                web.loadUrl("about:blank");
                ViewGroup p = (ViewGroup) web.getParent();
                if (p != null) p.removeView(web);
                web.destroy();
            } catch (Exception ignored) {
            }
            web = null;
        }
        if (overlay != null) {
            ViewGroup p = (ViewGroup) overlay.getParent();
            if (p != null) p.removeView(overlay);
            overlay = null;
        }
    }

    /** Si YouTube no deja incrustar ese tráiler, cierra la ventana y abre la app de YouTube / visor web. */
    private static final class Bridge {
        private final Activity a;
        private final JSONObject it;
        volatile String id = "";

        Bridge(Activity a, JSONObject it) {
            this.a = a;
            this.it = it;
        }

        @JavascriptInterface
        public void err() {
            a.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!isOpen()) return;
                    dismiss();
                    if (id.isEmpty()) Nav.trailerExternal(a, it);
                    else Nav.openYoutube(a, id);
                }
            });
        }
    }
}
