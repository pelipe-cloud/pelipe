package online.pelipe.tv;

import android.app.Activity;
import android.content.Intent;

import org.json.JSONObject;

import java.util.List;

/** Abrir el reproductor correcto según el tipo de enlace. */
final class Nav {
    static void play(Activity a, JSONObject it, int s, int e, Repo.Srv sv) {
        if (sv == null) return;
        Intent i = new Intent(a, sv.direct ? PlayerActivity.class : WebPlayerActivity.class);
        i.putExtra("id", Repo.id(it));
        i.putExtra("s", s);
        i.putExtra("e", e);
        i.putExtra("url", sv.url);
        i.putExtra("lang", sv.lang);
        a.startActivity(i);
    }

    /** Reproducir ya: películas desde donde se quedó; series desde el último episodio visto o el T1 E1. */
    static void quick(Activity a, JSONObject it) {
        String lang = Repo.lang(a);
        if (!Repo.isSerie(it)) {
            Repo.Srv sv = Repo.pick(Repo.servers(it), lang);
            play(a, it, 0, 0, sv);
            return;
        }
        List<Repo.Season> ss = Repo.seasons(it);
        if (ss.isEmpty()) return;
        int s = ss.get(0).n, e = ss.get(0).eps.get(0).n;
        JSONObject p = Repo.prog(a, Repo.id(it));
        if (p != null && p.optInt("s", 0) > 0) {
            s = p.optInt("s");
            e = p.optInt("e");
        }
        Repo.Ep ep = findEp(ss, s, e);
        if (ep == null) {
            s = ss.get(0).n;
            ep = ss.get(0).eps.get(0);
            e = ep.n;
        }
        play(a, it, s, e, Repo.pick(ep.srv, lang));
    }

    static boolean hasTrailer(JSONObject it) {
        return Repo.str(it, "trailer").startsWith("http");
    }

    /** Tráiler en ventana flotante dentro de la app (si no se puede, cae al método externo). */
    static void trailer(Activity a, JSONObject it) {
        Trailer.show(a, it);
    }

    /** Tráiler externo: primero la app de YouTube (en TV maneja bien el control); si no hay, el visor web. */
    static void trailerExternal(Activity a, JSONObject it) {
        String u = Repo.str(it, "trailer");
        if (!u.startsWith("http")) return;
        String yt = Repo.youtubeId(u);
        if (!yt.isEmpty()) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:" + yt)));
                return;
            } catch (Exception ignored) {
            }
        }
        Intent i = new Intent(a, WebPlayerActivity.class);
        i.putExtra("url", yt.isEmpty() ? u : "https://www.youtube.com/embed/" + yt + "?autoplay=1");
        a.startActivity(i);
    }

    static Repo.Ep findEp(List<Repo.Season> ss, int s, int e) {
        for (Repo.Season x : ss) {
            if (x.n != s) continue;
            for (Repo.Ep ep : x.eps) if (ep.n == e) return ep;
        }
        return null;
    }
}
