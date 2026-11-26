package online.pelipe.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * "Me gusta" de la app: usa los mismos votos de la web (peliculas/{id}/votos/{uid} en Firestore),
 * con sesión anónima de Firebase por REST. Se guarda también en el teléfono para que el botón responda al instante.
 */
final class Likes {
    interface Cb {
        void done(boolean liked);
    }

    private static SharedPreferences sp(Context c) {
        return Repo.sp(c);
    }

    static boolean cached(Context c, String id) {
        return sp(c).getBoolean("like_" + id, false);
    }

    private static String token(Context c) throws Exception {
        SharedPreferences p = sp(c);
        String idt = p.getString("fb_id", "");
        long exp = p.getLong("fb_exp", 0);
        if (!idt.isEmpty() && exp > System.currentTimeMillis() + 60000) return idt;
        String key = java.net.URLEncoder.encode(Repo.fbKey, "UTF-8");
        String rt = p.getString("fb_refresh", "");
        JSONObject r;
        if (!rt.isEmpty()) {
            try {
                String form = "grant_type=refresh_token&refresh_token=" + java.net.URLEncoder.encode(rt, "UTF-8");
                r = new JSONObject(postForm("https://securetoken.googleapis.com/v1/token?key=" + key, form));
                String nid = r.getString("id_token");
                p.edit().putString("fb_id", nid).putString("fb_refresh", r.optString("refresh_token", rt))
                        .putString("fb_uid", r.optString("user_id", p.getString("fb_uid", "")))
                        .putLong("fb_exp", System.currentTimeMillis() + Long.parseLong(r.optString("expires_in", "3600")) * 1000L).apply();
                return nid;
            } catch (Exception ignored) {
                // si el token de renovación ya no sirve, se crea una sesión anónima nueva
            }
        }
        r = new JSONObject(Repo.http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=" + key, "{\"returnSecureToken\":true}", 10000));
        String nid = r.getString("idToken");
        p.edit().putString("fb_id", nid).putString("fb_refresh", r.getString("refreshToken")).putString("fb_uid", r.getString("localId"))
                .putLong("fb_exp", System.currentTimeMillis() + Long.parseLong(r.optString("expiresIn", "3600")) * 1000L).apply();
        return nid;
    }

    private static String postForm(String url, String form) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Referer", Repo.SITE + "/");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setDoOutput(true);
        java.io.OutputStream os = c.getOutputStream();
        os.write(form.getBytes("UTF-8"));
        os.close();
        int code = c.getResponseCode();
        java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String s = in == null ? "" : Repo.readAll(in);
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code);
        return s;
    }

    private static String docUrl(Context c, String id) {
        String uid = sp(c).getString("fb_uid", "");
        return "https://firestore.googleapis.com/v1/projects/" + Repo.fbProject + "/databases/(default)/documents/peliculas/"
                + java.net.URLEncoder.encode(id).replace("+", "%20") + "/votos/" + uid;
    }

    /** Pregunta al servidor si ya hay un voto "me gusta" de esta persona (sin crear sesión si no la hay). */
    static void refresh(final Context ctx, final String id, final Cb cb) {
        final Context app = ctx.getApplicationContext();
        if (Repo.fbKey.isEmpty() || Repo.fbProject.isEmpty() || sp(app).getString("fb_uid", "").isEmpty()) return;
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String t = token(app);
                    String r = Repo.http("GET", docUrl(app, id), null, 9000, t);
                    String tipo = new JSONObject(r).getJSONObject("fields").getJSONObject("tipo").optString("stringValue", "");
                    final boolean liked = "like".equals(tipo);
                    sp(app).edit().putBoolean("like_" + id, liked).apply();
                    Repo.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.done(liked);
                        }
                    });
                } catch (Repo.HttpErr e) {
                    if (e.code == 404) {
                        sp(app).edit().putBoolean("like_" + id, false).apply();
                        Repo.MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                cb.done(false);
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    /** Alterna el "me gusta". El botón cambia al instante; si falla la red se devuelve al estado anterior. */
    static void toggle(final Context ctx, final String id, final Cb cb) {
        final Context app = ctx.getApplicationContext();
        final boolean next = !cached(app, id);
        sp(app).edit().putBoolean("like_" + id, next).apply();
        cb.done(next);
        if (Repo.fbKey.isEmpty() || Repo.fbProject.isEmpty()) return;
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String t = token(app);
                    if (next) {
                        String body = "{\"fields\":{\"tipo\":{\"stringValue\":\"like\"},\"t\":{\"integerValue\":\"" + System.currentTimeMillis() + "\"}}}";
                        Repo.http("PATCH", docUrl(app, id), body, 10000, t);
                    } else {
                        Repo.http("DELETE", docUrl(app, id), null, 10000, t);
                    }
                } catch (Exception e) {
                    sp(app).edit().putBoolean("like_" + id, !next).apply();
                    Repo.MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            cb.done(!next);
                            android.widget.Toast.makeText(app, "No se pudo guardar tu voto. Revisa tu conexión.", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
}
