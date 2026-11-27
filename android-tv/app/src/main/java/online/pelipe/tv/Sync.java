package online.pelipe.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Cuenta de Pelipe en la app: se vincula con un código de 6 dígitos (que se escribe en pelipe.online/vincular con la sesión iniciada)
 * y desde ahí "Mi lista" y "Continuar viendo" se sincronizan con el documento users/{uid} que usa la web (Firestore por REST).
 * La app entra a Firebase como visitante anónimo (ver Likes.token); la web la autoriza creando users/{uid}/dispositivos/{uidDeLaApp}.
 */
final class Sync {
    private Sync() {
    }

    interface PairCb {
        void code(String code);

        void linked(String who);

        void fail(String msg);
    }

    private static volatile int pairGen = 0;
    private static volatile String lastCode = "";
    private static boolean scheduled = false;
    private static Context appCtx;
    private static long lastPull = 0;

    private static SharedPreferences sp(Context c) {
        return Repo.sp(c);
    }

    static String owner(Context c) {
        return sp(c).getString("cuenta_owner", "");
    }

    static String who(Context c) {
        return sp(c).getString("cuenta_who", "");
    }

    static boolean linked(Context c) {
        return !owner(c).isEmpty();
    }

    static boolean ok() {
        return !Repo.fbKey.isEmpty() && !Repo.fbProject.isEmpty();
    }

    private static String base() {
        return "https://firestore.googleapis.com/v1/projects/" + Repo.fbProject + "/databases/(default)/documents";
    }

    /* ---------------- valores de Firestore (REST) <-> JSON ---------------- */

    private static JSONObject S(String s) throws Exception {
        return new JSONObject().put("stringValue", s == null ? "" : s);
    }

    private static JSONObject I(long n) throws Exception {
        return new JSONObject().put("integerValue", String.valueOf(n));
    }

    static JSONObject toFs(Object o) throws Exception {
        if (o == null || o == JSONObject.NULL) return new JSONObject().put("nullValue", JSONObject.NULL);
        if (o instanceof JSONObject) {
            JSONObject src = (JSONObject) o, f = new JSONObject();
            Iterator<String> it = src.keys();
            while (it.hasNext()) {
                String k = it.next();
                f.put(k, toFs(src.get(k)));
            }
            return new JSONObject().put("mapValue", new JSONObject().put("fields", f));
        }
        if (o instanceof JSONArray) {
            JSONArray src = (JSONArray) o, vs = new JSONArray();
            for (int i = 0; i < src.length(); i++) vs.put(toFs(src.get(i)));
            return new JSONObject().put("arrayValue", new JSONObject().put("values", vs));
        }
        if (o instanceof Boolean) return new JSONObject().put("booleanValue", ((Boolean) o).booleanValue());
        if (o instanceof Integer || o instanceof Long) return I(((Number) o).longValue());
        if (o instanceof Number) return new JSONObject().put("doubleValue", ((Number) o).doubleValue());
        return S(String.valueOf(o));
    }

    static Object fromFs(JSONObject v) throws Exception {
        if (v.has("stringValue")) return v.getString("stringValue");
        if (v.has("integerValue")) return Long.valueOf(Long.parseLong(v.getString("integerValue")));
        if (v.has("doubleValue")) return Double.valueOf(v.getDouble("doubleValue"));
        if (v.has("booleanValue")) return Boolean.valueOf(v.getBoolean("booleanValue"));
        if (v.has("mapValue")) {
            JSONObject f = v.getJSONObject("mapValue").optJSONObject("fields");
            JSONObject o = new JSONObject();
            if (f != null) {
                Iterator<String> it = f.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    o.put(k, fromFs(f.getJSONObject(k)));
                }
            }
            return o;
        }
        if (v.has("arrayValue")) {
            JSONArray vs = v.getJSONObject("arrayValue").optJSONArray("values");
            JSONArray a = new JSONArray();
            if (vs != null) for (int i = 0; i < vs.length(); i++) a.put(fromFs(vs.getJSONObject(i)));
            return a;
        }
        return JSONObject.NULL;
    }

    private static JSONArray arr(JSONObject fields, String k) {
        try {
            JSONObject v = fields.optJSONObject(k);
            if (v == null) return new JSONArray();
            Object o = fromFs(v);
            return o instanceof JSONArray ? (JSONArray) o : new JSONArray();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** Ficha corta de un título, igual a la de la web (mini). */
    private static JSONObject mini(JSONObject it) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", Repo.id(it));
        o.put("titulo", Repo.str(it, "titulo"));
        o.put("poster", Repo.str(it, "poster"));
        o.put("anio", Repo.str(it, "anio"));
        String q = Repo.str(it, "calidad");
        o.put("calidad", q.isEmpty() ? "HD" : q);
        JSONArray cs = it.optJSONArray("calidades");
        o.put("calidades", cs == null ? new JSONArray() : cs);
        o.put("generos", Repo.str(it, "generos"));
        return o;
    }

    /* ---------------- vincular ---------------- */

    /** Genera un código de 6 dígitos y espera (hasta 15 min) a que la web lo reclame con la sesión iniciada. */
    static void startPair(final Context ctx, final PairCb cb) {
        final Context app = ctx.getApplicationContext();
        final int gen = ++pairGen;
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!ok()) throw new Exception("Sin conexión con la nube.");
                    String t = Likes.token(app);
                    String uid = sp(app).getString("fb_uid", "");
                    String code = null;
                    Random rnd = new Random();
                    for (int i = 0; i < 6 && code == null; i++) {
                        String c = String.format(Locale.US, "%06d", rnd.nextInt(1000000));
                        JSONObject f = new JSONObject();
                        f.put("dev", S(uid));
                        f.put("nombre", S(Build.MODEL == null ? "Android" : Build.MODEL));
                        f.put("tipo", S(Ui.tv(app) ? "TV" : "Celular"));
                        f.put("ts", I(System.currentTimeMillis()));
                        try {
                            Repo.http("PATCH", base() + "/vinculos/" + c + "?currentDocument.exists=false", new JSONObject().put("fields", f).toString(), 10000, t);
                            code = c;
                        } catch (Repo.HttpErr e) {
                            if (e.code != 400 && e.code != 409 && e.code != 412) throw e; // código repetido: se prueba otro
                        }
                    }
                    if (code == null) throw new Exception("No se pudo crear el código.");
                    if (gen != pairGen) return;
                    lastCode = code;
                    final String fc = code;
                    Repo.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.code(fc);
                        }
                    });
                    long end = System.currentTimeMillis() + 15 * 60 * 1000L;
                    while (gen == pairGen && System.currentTimeMillis() < end) {
                        Thread.sleep(3000);
                        if (gen != pairGen) return;
                        try {
                            String tk = Likes.token(app);
                            String r = Repo.http("GET", base() + "/vinculos/" + code, null, 10000, tk);
                            JSONObject fields = new JSONObject(r).optJSONObject("fields");
                            if (fields != null && fields.has("owner")) {
                                final String own = fields.getJSONObject("owner").optString("stringValue", "");
                                JSONObject nm = fields.optJSONObject("ownerNombre");
                                final String who = nm == null ? "" : nm.optString("stringValue", "");
                                if (own.isEmpty()) continue;
                                sp(app).edit().putString("cuenta_owner", own).putString("cuenta_who", who)
                                        .putBoolean("cuenta_merge", true).putBoolean("cuenta_dirty", true).apply();
                                try {
                                    Repo.http("DELETE", base() + "/vinculos/" + code, null, 8000, tk);
                                } catch (Exception ignored) {
                                }
                                lastCode = "";
                                Repo.MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        cb.linked(who);
                                    }
                                });
                                pullNow(app, null);
                                return;
                            }
                        } catch (Exception ignored) {
                            // sin red un momento: se sigue intentando
                        }
                    }
                    if (gen == pairGen) {
                        Repo.MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.fail("El código venció. Genera uno nuevo.");
                            }
                        });
                    }
                } catch (final Exception e) {
                    if (gen != pairGen) return;
                    Repo.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.fail("No se pudo generar el código. Revisa tu conexión.");
                        }
                    });
                }
            }
        });
    }

    /** Deja de esperar y borra el código pendiente. */
    static void cancelPair(final Context ctx) {
        pairGen++;
        final String code = lastCode;
        lastCode = "";
        if (code.isEmpty() || !ok()) return;
        final Context app = ctx.getApplicationContext();
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Repo.http("DELETE", base() + "/vinculos/" + code, null, 8000, Likes.token(app));
                } catch (Exception ignored) {
                }
            }
        });
    }

    static void unlink(final Context ctx, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        final String own = owner(app);
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!own.isEmpty() && ok()) {
                        String uid = sp(app).getString("fb_uid", "");
                        Repo.http("DELETE", base() + "/users/" + own + "/dispositivos/" + uid, null, 10000, Likes.token(app));
                    }
                } catch (Exception ignored) {
                }
                sp(app).edit().remove("cuenta_owner").remove("cuenta_who").remove("cuenta_merge").remove("cuenta_dirty").apply();
                Repo.MAIN.post(done);
            }
        });
    }

    /* ---------------- sincronizar ---------------- */

    /** Trae lo de la nube (máx. 1 vez por minuto). `changed` se llama en el hilo principal si algo cambió. */
    static void pull(Context ctx, Runnable changed) {
        if (System.currentTimeMillis() - lastPull < 60000) return;
        pullNow(ctx, changed);
    }

    static void pullNow(final Context ctx, final Runnable changed) {
        final Context app = ctx.getApplicationContext();
        if (!linked(app) || !ok()) return;
        lastPull = System.currentTimeMillis();
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String t = Likes.token(app);
                    String r;
                    try {
                        r = Repo.http("GET", base() + "/users/" + owner(app), null, 12000, t);
                    } catch (Repo.HttpErr e) {
                        if (e.code == 404) {
                            pushNow(app);
                            return;
                        }
                        throw e;
                    }
                    JSONObject fields = new JSONObject(r).optJSONObject("fields");
                    if (fields == null) fields = new JSONObject();
                    boolean merge = sp(app).getBoolean("cuenta_merge", false);
                    boolean ch = false;

                    // Mi lista = favoritos de la web. En la primera vinculación se unen las dos; después manda la nube.
                    List<String> ids = new ArrayList<String>();
                    JSONArray fav = arr(fields, "favoritos");
                    for (int i = 0; i < fav.length(); i++) {
                        JSONObject m = fav.optJSONObject(i);
                        if (m != null && !m.optString("id", "").isEmpty() && !ids.contains(m.optString("id"))) ids.add(m.optString("id"));
                    }
                    if (merge) {
                        for (String id : Repo.lista(app)) if (!ids.contains(id)) ids.add(id);
                    }
                    String nuevo = new JSONArray(ids).toString();
                    if (!nuevo.equals(sp(app).getString("lista", "[]"))) {
                        sp(app).edit().putString("lista", nuevo).apply();
                        ch = true;
                    }

                    // Continuar viendo: gana lo más reciente de cada título
                    JSONObject local = Repo.progAll(app);
                    JSONArray pr = arr(fields, "progreso");
                    boolean pch = false;
                    for (int i = 0; i < pr.length(); i++) {
                        JSONObject m = pr.optJSONObject(i);
                        if (m == null) continue;
                        String id = m.optString("id", "");
                        long ts = m.optLong("ts", 0);
                        if (id.isEmpty() || ts <= 0) continue;
                        JSONObject lo = local.optJSONObject(id);
                        if (lo == null || lo.optLong("ts", 0) < ts) {
                            JSONObject o = new JSONObject();
                            JSONObject ep = m.optJSONObject("ep");
                            o.put("t", m.optLong("t", 0));
                            o.put("d", m.optLong("d", 0));
                            o.put("s", ep == null ? 0 : ep.optInt("t", 0));
                            o.put("e", ep == null ? 0 : ep.optInt("e", 0));
                            o.put("ts", ts);
                            local.put(id, o);
                            pch = true;
                        }
                    }
                    if (pch) {
                        sp(app).edit().putString("prog", local.toString()).apply();
                        ch = true;
                    }
                    if (merge) {
                        sp(app).edit().putBoolean("cuenta_merge", false).apply();
                        pushNow(app); // sube lo que solo tenía la app
                    }
                    if (ch && changed != null) Repo.MAIN.post(changed);
                } catch (Exception ignored) {
                }
            }
        });
    }

    /** Avisa que hay cambios locales: se suben en unos segundos (agrupados, para no gastar escrituras). */
    static void touch(Context ctx, long delayMs) {
        final Context app = ctx.getApplicationContext();
        if (!linked(app) || !ok()) return;
        sp(app).edit().putBoolean("cuenta_dirty", true).apply();
        appCtx = app;
        if (scheduled) return;
        scheduled = true;
        Repo.MAIN.postDelayed(pushRun, delayMs);
    }

    /** Sube ya (al salir del reproductor o de la app). */
    static void flush(Context ctx) {
        final Context app = ctx.getApplicationContext();
        if (!linked(app) || !ok() || !sp(app).getBoolean("cuenta_dirty", false)) return;
        appCtx = app;
        Repo.MAIN.removeCallbacks(pushRun);
        scheduled = false;
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    pushNow(app);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private static final Runnable pushRun = new Runnable() {
        @Override
        public void run() {
            scheduled = false;
            final Context app = appCtx;
            if (app == null) return;
            Repo.IO.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        pushNow(app);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    };

    private static void pushNow(Context app) throws Exception {
        if (!linked(app) || !ok()) return;
        String t = Likes.token(app);
        String url = base() + "/users/" + owner(app);
        JSONArray rFav = new JSONArray(), rProg = new JSONArray();
        try {
            JSONObject f = new JSONObject(Repo.http("GET", url, null, 12000, t)).optJSONObject("fields");
            if (f != null) {
                rFav = arr(f, "favoritos");
                rProg = arr(f, "progreso");
            }
        } catch (Repo.HttpErr e) {
            if (e.code != 404) throw e;
        }
        // favoritos = Mi lista de la app (se conserva la ficha de la web si ya estaba)
        JSONArray favOut = new JSONArray();
        Set<String> seen = new LinkedHashSet<String>();
        for (String id : Repo.lista(app)) {
            if (!seen.add(id)) continue;
            JSONObject found = null;
            for (int i = 0; i < rFav.length(); i++) {
                JSONObject m = rFav.optJSONObject(i);
                if (m != null && id.equals(m.optString("id"))) found = m;
            }
            if (found == null) {
                JSONObject it = Repo.byId(id);
                if (it == null) continue;
                found = mini(it);
            }
            favOut.put(found);
        }
        // progreso: gana lo más reciente de cada título
        Map<String, JSONObject> m = new LinkedHashMap<String, JSONObject>();
        for (int i = 0; i < rProg.length(); i++) {
            JSONObject x = rProg.optJSONObject(i);
            if (x != null && !x.optString("id", "").isEmpty()) m.put(x.optString("id"), x);
        }
        JSONObject local = Repo.progAll(app);
        Iterator<String> it = local.keys();
        while (it.hasNext()) {
            String id = it.next();
            JSONObject lo = local.optJSONObject(id);
            if (lo == null) continue;
            JSONObject cur = m.get(id);
            if (cur != null && cur.optLong("ts", 0) >= lo.optLong("ts", 0)) continue;
            JSONObject item = Repo.byId(id);
            if (item == null) continue;
            JSONObject e = mini(item);
            e.put("t", lo.optLong("t", 0));
            e.put("d", lo.optLong("d", 0));
            e.put("ts", lo.optLong("ts", 0));
            int s = lo.optInt("s", 0), ep = lo.optInt("e", 0);
            if (s > 0) {
                JSONObject eo = new JSONObject();
                eo.put("t", s);
                eo.put("e", ep < 1 ? 1 : ep);
                e.put("ep", eo);
            }
            m.put(id, e);
        }
        List<JSONObject> all = new ArrayList<JSONObject>(m.values());
        Collections.sort(all, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                return Long.compare(b.optLong("ts", 0), a.optLong("ts", 0));
            }
        });
        JSONArray progOut = new JSONArray();
        for (int i = 0; i < all.size() && i < 20; i++) progOut.put(all.get(i));

        JSONObject fields = new JSONObject();
        fields.put("favoritos", toFs(favOut));
        fields.put("progreso", toFs(progOut));
        Repo.http("PATCH", url + "?updateMask.fieldPaths=favoritos&updateMask.fieldPaths=progreso", new JSONObject().put("fields", fields).toString(), 15000, t);
        sp(app).edit().putBoolean("cuenta_dirty", false).apply();
    }
}
