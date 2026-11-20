package online.pelipe.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Acceso de pago único por código (colección "accesos" de Firestore). Todo es bloqueante: llamar desde Repo.IO. */
final class Access {
    static final int OK = 0, NO_CODE = 1, INVALID = 2, IN_USE = 3, EXPIRED = 4, NET = 5;
    private static final long GRACE = 7L * 86400000L;

    static String devId(Context c) {
        String id = null;
        try {
            id = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Exception ignored) {
        }
        SharedPreferences p = Repo.sp(c);
        if (id == null || id.length() < 8) {
            id = p.getString("devid", null);
            if (id == null) {
                id = UUID.randomUUID().toString().replace("-", "");
                p.edit().putString("devid", id).apply();
            }
        }
        return id;
    }

    /** Devuelve PELI-XXXX-XXXX o "" si el texto no tiene la forma de un código. */
    static String norm(String t) {
        String c = t == null ? "" : t.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (!Pattern.matches("PELI[A-Z0-9]{8}", c)) return "";
        return "PELI-" + c.substring(4, 8) + "-" + c.substring(8);
    }

    private static final class Doc {
        boolean exists;
        String dev = "";
        long dias, vence;
    }

    private static long num(JSONObject f, String k) {
        JSONObject v = f.optJSONObject(k);
        if (v == null) return 0;
        if (v.has("integerValue")) {
            try {
                return Long.parseLong(v.optString("integerValue", "0"));
            } catch (Exception e) {
                return 0;
            }
        }
        return (long) v.optDouble("doubleValue", 0);
    }

    private static Doc get(String code) throws IOException {
        Doc d = new Doc();
        try {
            String s = Repo.http("GET", Repo.FB_DOCS + "accesos/" + code + "?key=" + Repo.FB_KEY, null, 12000);
            JSONObject f = new JSONObject(s).optJSONObject("fields");
            d.exists = true;
            if (f != null) {
                JSONObject dv = f.optJSONObject("dispositivo");
                d.dev = dv == null ? "" : dv.optString("stringValue", "");
                d.dias = num(f, "dias");
                d.vence = num(f, "vence");
            }
            return d;
        } catch (Repo.HttpErr e) {
            if (e.code == 404) return d;
            throw e;
        } catch (org.json.JSONException e) {
            throw new IOException(e);
        }
    }

    private static void save(Context c, String code, long vence) {
        Repo.sp(c).edit().putString("acc_code", code).putLong("acc_vence", vence)
                .putLong("acc_t", System.currentTimeMillis()).apply();
    }

    private static void clear(Context c) {
        Repo.sp(c).edit().remove("acc_code").remove("acc_vence").remove("acc_t").apply();
    }

    /** Activa un código escrito por la persona. */
    static int activate(Context c, String raw) {
        String code = norm(raw);
        if (code.isEmpty()) return INVALID;
        String dev = devId(c);
        try {
            Doc d = get(code);
            if (!d.exists) return INVALID;
            long vence;
            if (!d.dev.isEmpty()) {
                if (!d.dev.equals(dev)) return IN_USE;
                vence = d.vence;
            } else {
                long t = System.currentTimeMillis();
                vence = d.dias > 0 ? t + d.dias * 86400000L : 0;
                String body = "{\"fields\":{\"dispositivo\":{\"stringValue\":\"" + dev + "\"},"
                        + "\"activadoEn\":{\"integerValue\":\"" + t + "\"},"
                        + "\"vence\":{\"integerValue\":\"" + vence + "\"}}}";
                String url = Repo.FB_DOCS + "accesos/" + code
                        + "?updateMask.fieldPaths=dispositivo&updateMask.fieldPaths=activadoEn&updateMask.fieldPaths=vence"
                        + "&currentDocument.exists=true&key=" + Repo.FB_KEY;
                try {
                    Repo.http("PATCH", url, body, 12000);
                } catch (Repo.HttpErr e) {
                    // Otra persona pudo activarlo justo antes
                    if (e.code == 403 || e.code == 409 || e.code == 400) {
                        Doc d2 = get(code);
                        if (d2.exists && !d2.dev.isEmpty() && !d2.dev.equals(dev)) return IN_USE;
                    }
                    throw e;
                }
            }
            if (vence > 0 && System.currentTimeMillis() > vence) return EXPIRED;
            save(c, code, vence);
            return OK;
        } catch (IOException e) {
            return NET;
        }
    }

    /** Revisa el código guardado al abrir la app. Sin internet deja pasar hasta 7 días desde la última revisión. */
    static int verifyStored(Context c) {
        SharedPreferences p = Repo.sp(c);
        String code = p.getString("acc_code", "");
        if (code.isEmpty()) return NO_CODE;
        long now = System.currentTimeMillis();
        try {
            Doc d = get(code);
            if (!d.exists) {
                clear(c);
                return INVALID;
            }
            if (!d.dev.equals(devId(c))) {
                clear(c);
                return IN_USE;
            }
            if (d.vence > 0 && now > d.vence) return EXPIRED;
            save(c, code, d.vence);
            return OK;
        } catch (IOException e) {
            long vence = p.getLong("acc_vence", 0);
            long t = p.getLong("acc_t", 0);
            if (vence > 0 && now > vence) return EXPIRED;
            return (now - t < GRACE && now >= t) ? OK : NET;
        }
    }
}
