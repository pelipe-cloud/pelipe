package online.pelipe.tv;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Plan B para enlaces que no son un video directo (reproductores incrustados de otros sitios).
 * Solo abre la página del reproductor y bloquea ventanas emergentes y redirecciones a otros sitios.
 */
public class WebPlayerActivity extends AppCompatActivity {
    private WebView web;
    private FrameLayout root;
    private View custom;
    private WebChromeClient.CustomViewCallback customCb;
    private String host = "";
    private WebChromeClient chrome;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Ui.immersive(this);
        String url = getIntent().getStringExtra("url");
        if (url == null || !url.startsWith("https://")) {
            finish();
            return;
        }
        url = embedUrl(url);
        host = Uri.parse(url).getHost() == null ? "" : Uri.parse(url).getHost();

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                String h = r.getUrl().getHost();
                return h == null || !sameSite(h);
            }
        });
        chrome = new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dialog, boolean gesture, Message m) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (custom != null) {
                    cb.onCustomViewHidden();
                    return;
                }
                custom = view;
                customCb = cb;
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                Ui.immersive(WebPlayerActivity.this);
            }

            @Override
            public void onHideCustomView() {
                if (custom == null) return;
                root.removeView(custom);
                custom = null;
                web.setVisibility(View.VISIBLE);
                if (customCb != null) customCb.onCustomViewHidden();
                customCb = null;
            }
        };
        web.setWebChromeClient(chrome);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        java.util.HashMap<String, String> hd = new java.util.HashMap<String, String>();
        hd.put("Referer", "https://pelipe.online/");
        web.loadUrl(url, hd);
        web.requestFocus();
    }

    private boolean sameSite(String h) {
        if (h.equals(host)) return true;
        String[] a = host.split("\\.");
        String[] c = h.split("\\.");
        if (a.length < 2 || c.length < 2) return false;
        return a[a.length - 2].equals(c[c.length - 2]) && a[a.length - 1].equals(c[c.length - 1]);
    }

    /** Enlaces de YouTube normales → versión para incrustar. */
    private String embedUrl(String u) {
        Uri x = Uri.parse(u);
        String h = x.getHost() == null ? "" : x.getHost();
        if (h.endsWith("youtube.com") && "/watch".equals(x.getPath()) && x.getQueryParameter("v") != null) {
            return "https://www.youtube.com/embed/" + x.getQueryParameter("v") + "?autoplay=1";
        }
        if (h.equals("youtu.be") && x.getPath() != null && x.getPath().length() > 1) {
            return "https://www.youtube.com/embed" + x.getPath() + "?autoplay=1";
        }
        return u;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && custom != null) {
            chrome.onHideCustomView();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.immersive(this);
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
