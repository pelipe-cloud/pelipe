package online.pelipe.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Datos de la app: catálogo (el mismo catalogo.json del sitio), configuración, progreso y lista. */
final class Repo {
    static final String SITE = "https://pelipe.online";
    static final String FB_KEY = "AIzaSyDkqPEXOkNBAI4xV5M8jdgQoPHP7IAxo6c";
    static final String FB_DOCS = "https://firestore.googleapis.com/v1/projects/pelipe-m/databases/(default)/documents/";

    static final ExecutorService IO = Executors.newFixedThreadPool(3);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    static volatile List<JSONObject> all = new ArrayList<JSONObject>();
    static long loadedAt = 0;
    static String lastHash = "";

    // Configuración remota (se lee de config.js del sitio)
    static String tmdbKey = "", tmdbLang = "es-MX";
    static boolean accesoApp = false;
    static String pagoUrl = "";
    static String whatsapp = "";
    static String precio = "";

    interface Listener {
        void onData();
        void onError(String msg);
    }

    /* ---------------- red ---------------- */

    static final class HttpErr extends IOException {
        final int code;
        HttpErr(int code, String body) {
            super("HTTP " + code + " " + body);
            this.code = code;
        }
    }

    static String http(String method, String url, String body, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", "PelipeApp/2");
        c.setRequestProperty("Referer", SITE + "/");
        try {
            c.setRequestMethod(method);
        } catch (ProtocolException e) {
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", method);
        }
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String s = in == null ? "" : readAll(in);
        c.disconnect();
        if (code >= 400) throw new HttpErr(code, s);
        return s;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    static String readFile(File f) throws IOException {
        return readAll(new FileInputStream(f));
    }

    static void writeFile(File f, String s) {
        try {
            FileOutputStream o = new FileOutputStream(f);
            o.write(s.getBytes("UTF-8"));
            o.close();
        } catch (Exception ignored) {
        }
    }

    /* ---------------- catálogo ---------------- */

    private static String hash(String s) {
        return s.length() + ":" + s.hashCode();
    }

    private static List<JSONObject> parse(String s) throws Exception {
        JSONObject root = new JSONObject(s);
        JSONArray arr = root.optJSONArray("peliculas");
        List<JSONObject> l = new ArrayList<JSONObject>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !str(o, "id").isEmpty() && !str(o, "titulo").isEmpty()) l.add(o);
            }
        }
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(b.optLong("creado", 0), a.optLong("creado", 0));
            }
        });
        return l;
    }

    /** Entrega el catálogo guardado enseguida y lo refresca en segundo plano (solo avisa si cambió). */
    static void load(Context ctx, final boolean force, final Listener l) {
        final Context app = ctx.getApplicationContext();
        if (!force && !all.isEmpty() && System.currentTimeMillis() - loadedAt < 300000) {
            l.onData();
            return;
        }
        final boolean haveMem = !all.isEmpty();
        if (haveMem) l.onData();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                File f = new File(app.getFilesDir(), "catalogo.json");
                boolean delivered = haveMem;
                if (!delivered && f.exists()) {
                    try {
                        String s = readFile(f);
                        List<JSONObject> c = parse(s);
                        if (!c.isEmpty()) {
                            all = c;
                            lastHash = hash(s);
                            delivered = true;
                            MAIN.post(new Runnable() {
                                @Override
                                public void run() {
                                    l.onData();
                                }
                            });
                        }
                    } catch (Exception ignored) {
                    }
                }
                try {
                    String s = http("GET", SITE + "/catalogo.json?t=" + (System.currentTimeMillis() / 60000), null, 15000);
                    String h = hash(s);
                    List<JSONObject> c = parse(s);
                    loadedAt = System.currentTimeMillis();
                    if (!h.equals(lastHash) || !delivered) {
                        all = c;
                        lastHash = h;
                        writeFile(f, s);
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                l.onData();
                            }
                        });
                    }
                } catch (Exception e) {
                    if (!delivered) {
                        MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                l.onError("No se pudo cargar el catálogo. Revisa tu conexión a internet.");
                            }
                        });
                    }
                }
            }
        });
    }

    /* ---------------- configuración (config.js del sitio) ---------------- */

    static void loadConfig(Context ctx, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        IO.execute(new Runnable() {
            @Override
            public void run() {
                SharedPreferences p = sp(app);
                String js = null;
                try {
                    js = http("GET", SITE + "/config.js?t=" + (System.currentTimeMillis() / 60000), null, 8000);
                    p.edit().putString("cfgjs", js).apply();
                } catch (Exception e) {
                    js = p.getString("cfgjs", null);
                }
                if (js != null) applyConfig(js);
                MAIN.post(done);
            }
        });
    }

    private static String cfgStr(String js, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + "\\s*:\\s*\"([^\"]*)\"").matcher(js);
        return m.find() ? m.group(1) : "";
    }

    private static void applyConfig(String js) {
        StringBuilder sb = new StringBuilder();
        for (String line : js.split("\n")) {
            if (!line.trim().startsWith("//")) sb.append(line).append('\n');
        }
        String clean = sb.toString();
        Matcher m = Pattern.compile("(?m)^\\s*ACCESO_APP\\s*:\\s*(true|false)").matcher(clean);
        accesoApp = m.find() && "true".equals(m.group(1));
        pagoUrl = cfgStr(clean, "PAGO_URL");
        whatsapp = cfgStr(clean, "WHATSAPP");
        precio = cfgStr(clean, "PRECIO_APP");
        tmdbKey = cfgStr(clean, "TMDB_API_KEY");
        String lg = cfgStr(clean, "LANG");
        if (!lg.isEmpty()) tmdbLang = lg;
        if (pagoUrl.contains("...")) pagoUrl = "";
        if (whatsapp.contains("XXXX")) whatsapp = "";
    }


    /* ---------------- dirección y reparto (TMDB, con la misma clave del sitio) ---------------- */

    interface CreditsCb {
        void done(JSONObject r);
    }

    /** Devuelve {"dir":[{n,f,r}], "rep":[{n,f,r}]} o null. Se guarda en el teléfono para no repetir la consulta. */
    static void credits(final Context ctx, final JSONObject o, final CreditsCb cb) {
        final Context app = ctx.getApplicationContext();
        final String id = id(o);
        IO.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject res = null;
                try {
                    SharedPreferences p = sp(app);
                    String c = p.getString("cr_" + id, null);
                    if (c != null) {
                        res = new JSONObject(c);
                    } else if (!tmdbKey.isEmpty()) {
                        res = fetchCredits(o);
                        if (res != null) p.edit().putString("cr_" + id, res.toString()).apply();
                    }
                } catch (Exception e) {
                    res = null;
                }
                final JSONObject out = res;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.done(out);
                    }
                });
            }
        });
    }

    private static JSONObject fetchCredits(JSONObject o) throws Exception {
        boolean tv = isSerie(o);
        String tipo = tv ? "tv" : "movie";
        String base = "https://api.themoviedb.org/3";
        String q = "api_key=" + java.net.URLEncoder.encode(tmdbKey, "UTF-8") + "&language=" + java.net.URLEncoder.encode(tmdbLang, "UTF-8");
        String y = str(o, "anio");
        if (!y.matches("\\d{4}")) y = "";
        String sq = base + "/search/" + tipo + "?" + q + "&include_adult=false&query=" + java.net.URLEncoder.encode(titulo(o), "UTF-8");
        JSONArray rs = new JSONObject(http("GET", y.isEmpty() ? sq : sq + (tv ? "&first_air_date_year=" : "&year=") + y, null, 9000)).optJSONArray("results");
        if ((rs == null || rs.length() == 0) && !y.isEmpty()) rs = new JSONObject(http("GET", sq, null, 9000)).optJSONArray("results");
        if (rs == null || rs.length() == 0) return null;
        int tid = rs.getJSONObject(0).optInt("id", 0);
        if (tid == 0) return null;
        JSONObject cr = new JSONObject(http("GET", base + "/" + tipo + "/" + tid + "/credits?" + q, null, 9000));
        JSONArray dir = new JSONArray();
        if (tv) {
            JSONArray cb = new JSONObject(http("GET", base + "/tv/" + tid + "?" + q, null, 9000)).optJSONArray("created_by");
            for (int i = 0; cb != null && i < cb.length() && dir.length() < 3; i++) dir.put(person(cb.getJSONObject(i), "Creación"));
        } else {
            JSONArray crew = cr.optJSONArray("crew");
            for (int i = 0; crew != null && i < crew.length() && dir.length() < 3; i++) {
                JSONObject c = crew.getJSONObject(i);
                if ("Director".equals(c.optString("job"))) dir.put(person(c, "Dirección"));
            }
        }
        JSONArray rep = new JSONArray();
        JSONArray cast = cr.optJSONArray("cast");
        for (int i = 0; cast != null && i < cast.length() && rep.length() < 12; i++) {
            JSONObject c = cast.getJSONObject(i);
            rep.put(person(c, c.optString("character", "")));
        }
        if (dir.length() == 0 && rep.length() == 0) return null;
        JSONObject out = new JSONObject();
        out.put("dir", dir);
        out.put("rep", rep);
        return out;
    }

    private static JSONObject person(JSONObject c, String role) throws Exception {
        JSONObject p = new JSONObject();
        p.put("n", c.optString("name", ""));
        p.put("f", c.isNull("profile_path") ? "" : c.optString("profile_path", ""));
        p.put("r", role);
        return p;
    }

    /* ---------------- accesores del catálogo ---------------- */

    static String str(JSONObject o, String k) {
        if (o == null || o.isNull(k)) return "";
        String v = o.optString(k, "");
        return v == null ? "" : v.trim();
    }

    static String id(JSONObject o) { return str(o, "id"); }
    static String titulo(JSONObject o) { return str(o, "titulo"); }
    static String poster(JSONObject o) { return str(o, "poster"); }
    static String fondo(JSONObject o) { return str(o, "fondo"); }
    static String sinopsis(JSONObject o) { return str(o, "sinopsis"); }
    static boolean isSerie(JSONObject o) { return "serie".equals(str(o, "tipo")); }

    static boolean visible(JSONObject o) {
        long t = o.optLong("publicarEn", 0);
        return t <= 0 || t <= System.currentTimeMillis();
    }

    /** serie: null = todo, true = solo series, false = solo películas. */
    static List<JSONObject> list(Boolean serie) {
        List<JSONObject> r = new ArrayList<JSONObject>();
        for (JSONObject o : all) {
            if (!visible(o)) continue;
            if (serie != null && isSerie(o) != serie.booleanValue()) continue;
            if (isSerie(o) && seasons(o).isEmpty()) continue;
            r.add(o);
        }
        return r;
    }

    static JSONObject byId(String id) {
        for (JSONObject o : all) if (id.equals(id(o))) return o;
        return null;
    }

    static double rating(JSONObject o) {
        double d = o.optDouble("calificacion", 0);
        return Double.isNaN(d) ? 0 : d;
    }

    static List<String> genres(JSONObject o) {
        List<String> r = new ArrayList<String>();
        String g = str(o, "generos");
        if (g.isEmpty()) return r;
        for (String x : g.split(",")) {
            x = x.trim();
            if (!x.isEmpty()) r.add(x);
        }
        return r;
    }

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    static String meta(JSONObject o) {
        StringBuilder sb = new StringBuilder();
        String a = str(o, "anio");
        if (!a.isEmpty()) sb.append(a);
        if (isSerie(o)) {
            int n = seasons(o).size();
            if (n > 0) sb.append(sb.length() > 0 ? "  ·  " : "").append(n).append(n == 1 ? " temporada" : " temporadas");
        } else {
            String d = str(o, "duracion");
            if (!d.isEmpty()) sb.append(sb.length() > 0 ? "  ·  " : "").append(d).append(" min");
        }
        double r = rating(o);
        if (r > 0) sb.append(sb.length() > 0 ? "  ·  " : "").append("★ ").append(String.format(Locale.US, "%.1f", r));
        sb.append(sb.length() > 0 ? "  ·  " : "").append("HD");
        return sb.toString();
    }

    /* ---------------- servidores, temporadas y episodios ---------------- */

    static final class Srv {
        String lang = "", name = "", url = "";
        boolean direct;

        String label(int idx) {
            String f = flag(lang);
            if (!name.isEmpty()) return f + name;
            if (!lang.isEmpty()) return f + langLabel(lang);
            return "Servidor " + (idx + 1);
        }
    }

    static String langLabel(String l) {
        if ("lat".equals(l)) return "Latino";
        if ("en".equals(l)) return "English";
        if ("ensub".equals(l)) return "English · Sub. español";
        return "Original";
    }

    /** Bandera del idioma (emoji) para los botones de servidor e idioma. */
    static String flag(String l) {
        if ("lat".equals(l)) return "\uD83C\uDDF2\uD83C\uDDFD ";
        if ("en".equals(l) || "ensub".equals(l)) return "\uD83C\uDDFA\uD83C\uDDF8 ";
        return "";
    }

    /** Emoji de cada género, como en la web. */
    static String genreEmoji(String g) {
        String n = norm(g);
        if (n.contains("accion")) return "💥";
        if (n.contains("aventura")) return "🧭";
        if (n.contains("animacion")) return "🎨";
        if (n.contains("comedia")) return "😂";
        if (n.contains("crimen")) return "🕵️";
        if (n.contains("documental")) return "🎞️";
        if (n.contains("drama")) return "🎭";
        if (n.contains("familia")) return "👨‍👩‍👧";
        if (n.contains("fantasia")) return "🧙";
        if (n.contains("historia")) return "📜";
        if (n.contains("terror") || n.contains("horror")) return "😱";
        if (n.contains("musica") || n.contains("musical")) return "🎵";
        if (n.contains("misterio")) return "🔍";
        if (n.contains("romance")) return "💕";
        if (n.contains("ciencia") || n.contains("ficcion")) return "🚀";
        if (n.contains("suspense") || n.contains("thriller")) return "😰";
        if (n.contains("belica") || n.contains("guerra")) return "⚔️";
        if (n.contains("western") || n.contains("oeste")) return "🤠";
        return "🎬";
    }

    /** Id de YouTube del tráiler, o "" si no es de YouTube. */
    static String youtubeId(String u) {
        if (u == null) return "";
        java.util.regex.Matcher m = Pattern.compile("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})").matcher(u);
        return m.find() ? m.group(1) : "";
    }

    static boolean isDirect(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        int h = u.indexOf('#');
        if (h >= 0) u = u.substring(0, h);
        return u.endsWith(".mp4") || u.endsWith(".m4v") || u.endsWith(".webm") || u.endsWith(".mkv")
                || u.endsWith(".mov") || u.endsWith(".m3u8") || u.startsWith("https://media.pelipe.online/");
    }

    static List<Srv> servers(JSONObject h) {
        List<Srv> r = new ArrayList<Srv>();
        JSONArray a = h.optJSONArray("servidores");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x == null) continue;
                String u = str(x, "u");
                if (!u.startsWith("http")) continue;
                Srv s = new Srv();
                String lg = str(x, "i");
                s.lang = ("lat".equals(lg) || "en".equals(lg) || "ensub".equals(lg)) ? lg : "";
                s.name = str(x, "n");
                s.url = u;
                s.direct = isDirect(u);
                r.add(s);
            }
        }
        if (r.isEmpty()) {
            String u = str(h, "url");
            if (u.startsWith("http")) {
                Srv s = new Srv();
                s.url = u;
                s.direct = isDirect(u);
                r.add(s);
            }
        }
        return r;
    }

    /** Elige el servidor: idioma preferido en video propio, luego cualquier video propio, luego el idioma, luego el primero. */
    static Srv pick(List<Srv> l, String lang) {
        for (Srv s : l) if (s.direct && s.lang.equals(lang)) return s;
        for (Srv s : l) if (s.direct) return s;
        for (Srv s : l) if (s.lang.equals(lang)) return s;
        return l.isEmpty() ? null : l.get(0);
    }

    static final class Ep {
        int n;
        String titulo = "", sinopsis = "";
        List<Srv> srv = new ArrayList<Srv>();
    }

    static final class Season {
        int n;
        List<Ep> eps = new ArrayList<Ep>();
    }

    static List<Season> seasons(JSONObject serie) {
        List<Season> r = new ArrayList<Season>();
        JSONArray ts = serie.optJSONArray("temporadas");
        if (ts == null) return r;
        for (int i = 0; i < ts.length(); i++) {
            JSONObject t = ts.optJSONObject(i);
            if (t == null) continue;
            Season s = new Season();
            s.n = t.optInt("n", i + 1);
            JSONArray es = t.optJSONArray("episodios");
            if (es != null) {
                for (int j = 0; j < es.length(); j++) {
                    JSONObject e = es.optJSONObject(j);
                    if (e == null) continue;
                    Ep ep = new Ep();
                    ep.n = e.optInt("n", j + 1);
                    ep.titulo = str(e, "titulo");
                    ep.sinopsis = str(e, "sinopsis");
                    ep.srv = servers(e);
                    if (!ep.srv.isEmpty()) s.eps.add(ep);
                }
            }
            if (s.eps.isEmpty()) continue;
            Collections.sort(s.eps, new Comparator<Ep>() {
                @Override
                public int compare(Ep a, Ep b) {
                    return a.n - b.n;
                }
            });
            r.add(s);
        }
        Collections.sort(r, new Comparator<Season>() {
            @Override
            public int compare(Season a, Season b) {
                return a.n - b.n;
            }
        });
        return r;
    }

    /* ---------------- preferencias locales: idioma, lista, progreso ---------------- */

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("pelipe", Context.MODE_PRIVATE);
    }

    static String lang(Context c) { return sp(c).getString("lang", "lat"); }
    static void setLang(Context c, String l) { sp(c).edit().putString("lang", l).apply(); }

    static List<String> lista(Context c) {
        List<String> r = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(sp(c).getString("lista", "[]"));
            for (int i = 0; i < a.length(); i++) r.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return r;
    }

    static boolean inLista(Context c, String id) { return lista(c).contains(id); }

    static boolean toggleLista(Context c, String id) {
        List<String> l = lista(c);
        boolean now;
        if (l.contains(id)) {
            l.remove(id);
            now = false;
        } else {
            l.add(0, id);
            now = true;
        }
        sp(c).edit().putString("lista", new JSONArray(l).toString()).apply();
        return now;
    }

    static JSONObject progAll(Context c) {
        try {
            return new JSONObject(sp(c).getString("prog", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Progreso de un título: {t: segundos vistos, d: duración, s: temporada, e: episodio, ts: fecha} o null. */
    static JSONObject prog(Context c, String id) {
        return progAll(c).optJSONObject(id);
    }

    static void putProg(Context c, String id, long t, long d, int s, int e) {
        try {
            JSONObject all = progAll(c);
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("d", d);
            o.put("s", s);
            o.put("e", e);
            o.put("ts", System.currentTimeMillis());
            all.put(id, o);
            if (all.length() > 30) {
                String oldest = null;
                long ot = Long.MAX_VALUE;
                java.util.Iterator<String> it = all.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    long ts = all.optJSONObject(k) == null ? 0 : all.optJSONObject(k).optLong("ts", 0);
                    if (ts < ot) {
                        ot = ts;
                        oldest = k;
                    }
                }
                if (oldest != null) all.remove(oldest);
            }
            sp(c).edit().putString("prog", all.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static void dropProg(Context c, String id) {
        JSONObject all = progAll(c);
        all.remove(id);
        sp(c).edit().putString("prog", all.toString()).apply();
    }

    static String snap(Context c) {
        return sp(c).getString("prog", "") + "|" + sp(c).getString("lista", "");
    }

    static String clock(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, s) : String.format(Locale.US, "%d:%02d", m, s);
    }
}
