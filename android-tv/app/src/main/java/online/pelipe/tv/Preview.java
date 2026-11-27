package online.pelipe.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import org.json.JSONObject;

/**
 * Vista previa flotante en TV: si el control remoto se queda un momento sobre un cartel, aparece al lado
 * un panel con título, año/duración/nota, géneros y sinopsis. Solo informa (no toma el foco):
 * OK abre la ficha como siempre. Al mover el foco desaparece.
 */
final class Preview {
    private Preview() {
    }

    private static final long ESPERA = 1000;
    private static View panel;
    private static ViewGroup host;
    private static Runnable pendiente;

    static void focus(final Activity a, final View card, final JSONObject o, boolean has) {
        cancelar();
        hide();
        if (!has) return;
        pendiente = new Runnable() {
            @Override
            public void run() {
                pendiente = null;
                if (card.isFocused() && card.isAttachedToWindow() && !a.isFinishing()) show(a, card, o);
            }
        };
        Repo.MAIN.postDelayed(pendiente, ESPERA);
    }

    private static void cancelar() {
        if (pendiente != null) {
            Repo.MAIN.removeCallbacks(pendiente);
            pendiente = null;
        }
    }

    static void hide() {
        cancelar();
        if (panel != null && host != null) {
            try {
                host.removeView(panel);
            } catch (Exception ignored) {
            }
        }
        panel = null;
        host = null;
    }

    private static void show(Activity a, View card, JSONObject o) {
        ViewGroup content = (ViewGroup) a.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) return;
        int pw = Math.min(Ui.dp(a, 420), content.getWidth() - Ui.dp(a, 40));

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1E1E2C, 0xFF14141C});
        bg.setCornerRadius(Ui.dp(a, 16));
        bg.setStroke(Ui.dp(a, 1), 0x33FFFFFF);
        box.setBackground(bg);
        box.setElevation(Ui.dp(a, 24));
        box.setFocusable(false);
        box.setClickable(false);
        box.setClipChildren(true);

        LinearLayout tx = new LinearLayout(a);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(Ui.dp(a, 18), Ui.dp(a, 16), Ui.dp(a, 18), Ui.dp(a, 14));
        TextView t = Ui.text(a, Repo.titulo(o), 20, 0xFFFFFFFF, true);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t);
        TextView m = Ui.text(a, Repo.meta(o), 13, Ui.ACC2, true);
        m.setPadding(0, Ui.dp(a, 4), 0, Ui.dp(a, 8));
        tx.addView(m);
        String syn = Repo.sinopsis(o);
        if (!syn.isEmpty()) {
            TextView s = Ui.text(a, syn, 14, 0xFFD6D6E2, false);
            s.setMaxLines(5);
            s.setEllipsize(TextUtils.TruncateAt.END);
            s.setLineSpacing(0, 1.15f);
            tx.addView(s);
        }
        List<String> g = Repo.genres(o);
        if (!g.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < g.size() && i < 3; i++) sb.append(i > 0 ? "  ·  " : "").append(g.get(i));
            TextView gt = Ui.text(a, sb.toString(), 12, Ui.MUTE, false);
            gt.setPadding(0, Ui.dp(a, 8), 0, 0);
            tx.addView(gt);
        }
        TextView hint = Ui.text(a, "OK para abrir la ficha", 12, Ui.MUTE, false);
        hint.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        hint.setPadding(0, Ui.dp(a, 10), 0, 0);
        tx.addView(hint);
        box.addView(tx, new LinearLayout.LayoutParams(pw, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Medir para ubicarlo: a la derecha del cartel; si no cabe, a la izquierda
        box.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int ph = Math.max(box.getMeasuredHeight(), Ui.dp(a, 120));
        int[] cl = new int[2], hl = new int[2];
        card.getLocationOnScreen(cl);
        content.getLocationOnScreen(hl);
        int cw = Math.round(card.getWidth() * card.getScaleX());
        int x = cl[0] - hl[0] + cw + Ui.dp(a, 14);
        if (x + pw > content.getWidth() - Ui.dp(a, 12)) x = cl[0] - hl[0] - pw - Ui.dp(a, 14);
        x = Math.max(Ui.dp(a, 12), x);
        int y = cl[1] - hl[1];
        y = Math.max(Ui.dp(a, 60), Math.min(y, content.getHeight() - ph - Ui.dp(a, 12)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(pw, ph);
        lp.leftMargin = x;
        lp.topMargin = y;
        lp.gravity = Gravity.TOP | Gravity.START;
        box.setAlpha(0f);
        content.addView(box, lp);
        box.animate().alpha(1f).setDuration(180).start();
        panel = box;
        host = content;
    }
}
