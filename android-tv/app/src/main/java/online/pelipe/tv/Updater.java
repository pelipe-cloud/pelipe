package online.pelipe.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Busca versiones nuevas: lee https://media.pelipe.online/version.json
 *   {"versionCode":16,"versionName":"2.12","url":"https://media.pelipe.online/pelipeapp.apk","notas":"..."}
 * Si el número es mayor al instalado, ofrece descargar e instalar (Android pide confirmar la instalación).
 * Actualización OBLIGATORIA: con "minima":N en version.json, las versiones con versionCode menor a N no pueden seguir:
 * ven una pantalla sin "Más tarde" hasta actualizar. Opcional "mensaje" para explicar el motivo.
 * Si version.json no se puede leer (sin internet), la app deja entrar.
 */
final class Updater {
    private static final String VERSION_URL = "https://media.pelipe.online/version.json";
    private static boolean checked = false;
    private static JSONObject pending;
    private static boolean forced = false;
    private static AlertDialog dlg;

    static void check(final Activity a) {
        if (checked) return;
        checked = true;
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject o = new JSONObject(Repo.http("GET", VERSION_URL + "?t=" + System.currentTimeMillis(), null, 8000));
                    PackageInfo pi = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                    boolean oblig = o.optInt("minima", 0) > pi.versionCode;
                    if ((o.optInt("versionCode", 0) > pi.versionCode || oblig) && o.optString("url", "").startsWith("https://")) {
                        pending = o;
                        forced = oblig;
                        a.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!a.isFinishing()) ask(a);
                            }
                        });
                    }
                } catch (Exception ignored) {
                    // sin internet o sin version.json: no pasa nada
                }
            }
        });
    }

    /** Al volver de los ajustes de "instalar apps desconocidas", retoma la actualización. */
    static void resume(Activity a) {
        if (pending != null && dlg == null && (forced || canInstall(a))) ask(a);
    }

    private static boolean canInstall(Activity a) {
        return Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls();
    }

    private static void ask(final Activity a) {
        if (pending == null || dlg != null) return;
        final JSONObject o = pending;
        String msg;
        String notas = o.optString("notas", "");
        if (forced) {
            String m = o.optString("mensaje", "");
            msg = m.isEmpty() ? "Esta versión de Pelipe ya no es compatible. Actualiza a la " + o.optString("versionName", "nueva versión") + " para seguir viendo." : m;
        } else {
            msg = "Hay una versión nueva de Pelipe (" + o.optString("versionName", "") + ").";
        }
        if (!notas.isEmpty()) msg += "\n\n" + notas;
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(forced ? "Actualiza para continuar" : "Actualización disponible")
                .setMessage(msg)
                .setCancelable(!forced)
                .setPositiveButton("Actualizar", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        dlg = null;
                        start(a, o.optString("url"));
                    }
                });
        if (forced) {
            b.setNegativeButton("Salir", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    dlg = null;
                    a.finishAffinity();
                }
            });
        } else {
            b.setNegativeButton("Más tarde", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    dlg = null;
                    pending = null;
                }
            });
            b.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(android.content.DialogInterface d) {
                    dlg = null;
                    pending = null;
                }
            });
        }
        dlg = b.show();
    }

    private static void start(final Activity a, final String url) {
        if (!canInstall(a)) {
            try {
                Toast.makeText(a, "Activa el permiso para instalar desde Pelipe y vuelve a la app", Toast.LENGTH_LONG).show();
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                Toast.makeText(a, "No se pudo abrir el permiso de instalación", Toast.LENGTH_LONG).show();
                if (forced) ask(a); else pending = null;
            }
            return;
        }
        final ProgressBar pb = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        final AlertDialog d = new AlertDialog.Builder(a).setTitle("Descargando actualización…").setView(pb).setCancelable(false).create();
        d.show();
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                File out = null;
                try {
                    File dir = new File(a.getCacheDir(), "update");
                    if (!dir.exists()) dir.mkdirs();
                    out = new File(dir, "pelipe-update.apk");
                    HttpURLConnection c = (HttpURLConnection) new URL(url + (url.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis()).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(20000);
                    long total = c.getContentLength();
                    InputStream in = c.getInputStream();
                    FileOutputStream fo = new FileOutputStream(out);
                    byte[] buf = new byte[32768];
                    long got = 0;
                    int n, last = -1;
                    while ((n = in.read(buf)) > 0) {
                        fo.write(buf, 0, n);
                        got += n;
                        if (total > 0) {
                            final int pct = (int) (got * 100 / total);
                            if (pct != last) {
                                last = pct;
                                a.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        pb.setProgress(pct);
                                    }
                                });
                            }
                        }
                    }
                    fo.close();
                    in.close();
                    if (got < 500000) throw new Exception("archivo incompleto");
                    final File apk = out;
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            d.dismiss();
                            if (!forced) pending = null; // obligatoria: si cancela la instalación, vuelve a pedirla
                            try {
                                Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fp", apk);
                                Intent i = new Intent(Intent.ACTION_VIEW);
                                i.setDataAndType(uri, "application/vnd.android.package-archive");
                                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                                a.startActivity(i);
                            } catch (Exception e) {
                                Toast.makeText(a, "No se pudo iniciar la instalación", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (Exception e) {
                    a.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            d.dismiss();
                            Toast.makeText(a, "No se pudo descargar la actualización. Inténtalo más tarde.", Toast.LENGTH_LONG).show();
                            if (forced && !a.isFinishing()) ask(a);
                        }
                    });
                }
            }
        });
    }
}
