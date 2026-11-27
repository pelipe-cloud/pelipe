package online.pelipe.tv;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * "¿No encuentras tu película?": igual que en la web. Se busca el título en TMDB, se elige la correcta y la solicitud
 * llega por correo (FormSubmit) a pelipepubs@gmail.com. Máximo 5 solicitudes por día.
 */
final class Pedir {
    private static final String MAIL = "pelipepubs@gmail.com";
    private static final int MAX_DIA = 5;

    /** Botón grande para el inicio (debajo del banner). */
    static View bar(final Activity a) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.margin(a), Ui.dp(a, 14), Ui.margin(a), Ui.dp(a, 6));
        row.setClipChildren(false);
        row.setClipToPadding(false);
        TextView b = Ui.button(a, "🎬  ¿No encuentras tu Película/Serie?  ¡Solicítala!", false);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(a);
            }
        });
        row.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static final class Res {
        String id = "", nombre = "", anio = "", tipo = ""; // tipo: movie | tv | libre
        JSONObject enCat;
    }

    static void show(final Activity a) {
        final Handler h = new Handler(Looper.getMainLooper());
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        final boolean tv = Ui.tv(a);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(Ui.BG2, 0x55FFB347, Ui.dp(a, 1), Ui.dp(a, 18)));
        box.setPadding(Ui.dp(a, 20), Ui.dp(a, 18), Ui.dp(a, 20), Ui.dp(a, 16));

        TextView title = Ui.text(a, "Solicita tu Película/Serie", 20, 0xFFFFFFFF, true);
        box.addView(title);
        TextView sub = Ui.text(a, "Escribe el título y elige la correcta de la lista.", 13, Ui.MUTE, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(a, 4);
        sp.bottomMargin = Ui.dp(a, 10);
        box.addView(sub, sp);

        final EditText q = field(a, "Ej: Matrix, Coco, Breaking Bad", InputType.TYPE_CLASS_TEXT);
        box.addView(q);

        final LinearLayout sugs = new LinearLayout(a);
        sugs.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.addView(sugs);
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, tv ? 220 : 190));
        svp.topMargin = Ui.dp(a, 8);
        box.addView(sv, svp);

        TextView ml = Ui.text(a, "Tu correo (opcional, para avisarte cuando esté)", 12, Ui.MUTE, false);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = Ui.dp(a, 10);
        box.addView(ml, mlp);
        final EditText mail = field(a, "tucorreo@ejemplo.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        box.addView(mail);

        final TextView status = Ui.text(a, "Máximo " + MAX_DIA + " solicitudes por día. Revisamos todas.", 12, Ui.MUTE, false);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.topMargin = Ui.dp(a, 10);
        box.addView(status, stp);

        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        btns.setClipChildren(false);
        final TextView send = Ui.button(a, "Enviar solicitud", true);
        send.setAlpha(0.4f);
        TextView close = Ui.button(a, "Cerrar", false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        btns.addView(close);
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbp.leftMargin = Ui.dp(a, 10);
        btns.addView(send, sbp);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(a, 12);
        box.addView(btns, bp);

        final List<Res> ult = new ArrayList<Res>();
        final Res[] sel = new Res[1];
        final int[] seq = {0};
        final Runnable[] pinta = new Runnable[1];

        pinta[0] = new Runnable() {
            @Override
            public void run() {
                sugs.removeAllViews();
                if (ult.isEmpty()) {
                    String t = q.getText().toString().trim();
                    if (t.length() >= 2) sugs.addView(Ui.text(a, "Sin resultados. Prueba con otro título.", 13, Ui.MUTE, false));
                    return;
                }
                for (final Res r : ult) {
                    boolean on = sel[0] != null && sel[0].id.equals(r.id);
                    String line = r.nombre + (r.anio.isEmpty() ? "" : "  (" + r.anio + ")") + "  ·  " + ("tv".equals(r.tipo) ? "Serie" : "Película");
                    if (r.enCat != null) line += "   ✔ Ya está en Pelipe · Ver";
                    TextView c = Ui.chip(a, (on ? "✓  " : "") + line, on);
                    c.setSingleLine(true);
                    c.setEllipsize(TextUtils.TruncateAt.END);
                    c.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    c.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (r.enCat != null) {
                                d.dismiss();
                                Ui.open(a, r.enCat);
                                return;
                            }
                            sel[0] = r;
                            send.setAlpha(1f);
                            pinta[0].run();
                        }
                    });
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    cp.bottomMargin = Ui.dp(a, 6);
                    sugs.addView(c, cp);
                }
            }
        };

        final Runnable[] buscar = new Runnable[1];
        buscar[0] = new Runnable() {
            @Override
            public void run() {
                final String v = q.getText().toString().trim();
                final int mi = ++seq[0];
                if (v.length() < 2) return;
                if (Repo.tmdbKey.isEmpty()) return; // sin TMDB se envía el texto tal cual
                Repo.IO.execute(new Runnable() {
                    @Override
                    public void run() {
                        final List<Res> rs = new ArrayList<Res>();
                        String err = null;
                        try {
                            String u = "https://api.themoviedb.org/3/search/multi?api_key=" + URLEncoder.encode(Repo.tmdbKey, "UTF-8")
                                    + "&language=" + URLEncoder.encode(Repo.tmdbLang, "UTF-8") + "&include_adult=false&query=" + URLEncoder.encode(v, "UTF-8");
                            JSONArray arr = new JSONObject(Repo.http("GET", u, null, 8000)).optJSONArray("results");
                            for (int i = 0; arr != null && i < arr.length() && rs.size() < 8; i++) {
                                JSONObject o = arr.optJSONObject(i);
                                if (o == null) continue;
                                String mt = o.optString("media_type", "");
                                if (!"movie".equals(mt) && !"tv".equals(mt)) continue;
                                Res r = new Res();
                                r.id = String.valueOf(o.optLong("id"));
                                r.tipo = mt;
                                r.nombre = o.optString("title", o.optString("name", ""));
                                String f = o.optString("release_date", o.optString("first_air_date", ""));
                                r.anio = f.length() >= 4 ? f.substring(0, 4) : "";
                                r.enCat = enCatalogo(o, r);
                                rs.add(r);
                            }
                        } catch (Exception e) {
                            err = "x";
                        }
                        final String fe = err;
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mi != seq[0] || !d.isShowing()) return;
                                ult.clear();
                                sel[0] = null;
                                send.setAlpha(0.4f);
                                if (fe != null) {
                                    status.setText("No se pudo buscar ahora. Escribe el título completo y envía igual.");
                                    Res r = new Res();
                                    r.id = "libre";
                                    r.tipo = "libre";
                                    r.nombre = v;
                                    sel[0] = r;
                                    send.setAlpha(1f);
                                } else {
                                    ult.addAll(rs);
                                }
                                pinta[0].run();
                            }
                        });
                    }
                });
            }
        };

        q.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                h.removeCallbacks(buscar[0]);
                sel[0] = null;
                send.setAlpha(0.4f);
                ult.clear();
                sugs.removeAllViews();
                String v = e.toString().trim();
                if (Repo.tmdbKey.isEmpty() && v.length() >= 2) {
                    Res r = new Res();
                    r.id = "libre";
                    r.tipo = "libre";
                    r.nombre = v;
                    sel[0] = r;
                    send.setAlpha(1f);
                    return;
                }
                if (v.length() >= 2) h.postDelayed(buscar[0], 350);
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Res r = sel[0];
                if (r == null) {
                    status.setText("Elige una película de la lista.");
                    return;
                }
                final org.json.JSONObject u = usados(a);
                if (u.optInt("n", 0) >= MAX_DIA) {
                    status.setText("Ya enviaste " + MAX_DIA + " solicitudes hoy. Vuelve mañana 🙂");
                    return;
                }
                final String clave = Repo.norm(r.nombre) + r.anio;
                if (u.optString("t", "").contains("|" + clave + "|")) {
                    status.setText("Ya pediste esta película hoy. ¡La revisaremos pronto!");
                    return;
                }
                final String correo = mail.getText().toString().trim();
                final boolean correoOk = correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
                send.setAlpha(0.4f);
                send.setEnabled(false);
                status.setText("Enviando…");
                Repo.IO.execute(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok = enviar(r, correoOk ? correo : "");
                        final boolean fok = ok;
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                send.setEnabled(true);
                                if (!d.isShowing()) return;
                                if (fok) {
                                    try {
                                        u.put("n", u.optInt("n", 0) + 1);
                                        u.put("t", u.optString("t", "") + "|" + clave + "|");
                                        Repo.sp(a).edit().putString("pedidos", u.toString()).apply();
                                    } catch (Exception ignored) {
                                    }
                                    sugs.removeAllViews();
                                    ult.clear();
                                    sel[0] = null;
                                    q.setText("");
                                    status.setText("✓ ¡Solicitud enviada! Recibimos «" + r.nombre + (r.anio.isEmpty() ? "" : " (" + r.anio + ")") + "». La revisaremos pronto.");
                                    status.setTextColor(0xFF49D17D);
                                    send.setAlpha(0.4f);
                                } else {
                                    send.setAlpha(1f);
                                    status.setText("No se pudo enviar la solicitud. Inténtalo de nuevo en un rato. (Si eres el administrador: confirma el correo de activación de FormSubmit.)");
                                }
                            }
                        });
                    }
                });
            }
        });

        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(box);
        d.setCancelable(true);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            int sw = a.getResources().getDisplayMetrics().widthPixels;
            int width = tv ? Math.min(sw - Ui.dp(a, 80), Ui.dp(a, 680)) : sw - Ui.dp(a, 24);
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
        q.requestFocus();
    }

    private static EditText field(Activity a, String hint, int type) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setHintTextColor(Ui.MUTE);
        e.setTextColor(Ui.TX);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setInputType(type);
        e.setTypeface(Typeface.DEFAULT);
        e.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
        e.setBackground(Ui.states(Ui.shape(Ui.BG, Ui.RING, Ui.dp(a, 2), Ui.dp(a, 10)), Ui.shape(Ui.BG, Ui.LINE, Ui.dp(a, 1), Ui.dp(a, 10))));
        return e;
    }

    /** ¿Ya está en el catálogo? Por id de TMDB o por título + año (igual que en la web). */
    private static JSONObject enCatalogo(JSONObject tm, Res r) {
        String t = Repo.norm(tm.optString("title", tm.optString("name", "")));
        String o = Repo.norm(tm.optString("original_title", tm.optString("original_name", "")));
        for (JSONObject p : Repo.all) {
            if (!Repo.visible(p)) continue;
            if (!Repo.str(p, "tmdb").isEmpty() && Repo.str(p, "tmdb").equals(r.id)) return p;
            String pt = Repo.norm(Repo.titulo(p));
            if (pt.equals(t) || pt.equals(o)) {
                String ya = Repo.str(p, "anio");
                if (r.anio.isEmpty() || ya.isEmpty()) return p;
                try {
                    if (Math.abs(Integer.parseInt(ya) - Integer.parseInt(r.anio)) <= 1) return p;
                } catch (NumberFormatException e) {
                    return p;
                }
            }
        }
        return null;
    }

    private static JSONObject usados(Activity a) {
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        try {
            JSONObject o = new JSONObject(Repo.sp(a).getString("pedidos", "{}"));
            if (hoy.equals(o.optString("d", ""))) return o;
        } catch (Exception ignored) {
        }
        JSONObject n = new JSONObject();
        try {
            n.put("d", hoy);
            n.put("n", 0);
            n.put("t", "");
        } catch (Exception ignored) {
        }
        return n;
    }

    private static boolean enviar(Res r, String correo) {
        try {
            JSONObject b = new JSONObject();
            String tipo = "libre".equals(r.tipo) ? "Película o serie" : ("tv".equals(r.tipo) ? "Serie" : "Película");
            b.put("_subject", "Pelipe · Solicitud (app): " + r.nombre + (r.anio.isEmpty() ? "" : " (" + r.anio + ")"));
            b.put("_template", "table");
            b.put("_captcha", "false");
            b.put("_honey", "");
            b.put("Título", r.nombre);
            b.put("Año", r.anio.isEmpty() ? "—" : r.anio);
            b.put("Tipo", tipo);
            b.put("Enlace TMDB", "libre".equals(r.tipo) ? "(escrito a mano)" : "https://www.themoviedb.org/" + ("tv".equals(r.tipo) ? "tv" : "movie") + "/" + r.id);
            b.put("Correo para avisar", correo.isEmpty() ? "(no dejó correo)" : correo);
            b.put("Origen", "App Android");
            b.put("Fecha", new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date()));
            if (!correo.isEmpty()) b.put("email", correo);
            HttpURLConnection c = (HttpURLConnection) new URL("https://formsubmit.co/ajax/" + MAIL).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(12000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", "PelipeApp/2");
            c.setRequestProperty("Origin", "https://pelipe.online");
            c.setRequestProperty("Referer", "https://pelipe.online/");
            OutputStream os = c.getOutputStream();
            os.write(b.toString().getBytes("UTF-8"));
            os.close();
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String s = in == null ? "" : Repo.readAll(in);
            c.disconnect();
            if (code >= 400) return false;
            return !s.contains("\"success\":\"false\"") && !s.contains("\"success\": \"false\"") && !s.contains("\"success\":false");
        } catch (Exception e) {
            return false;
        }
    }
}
