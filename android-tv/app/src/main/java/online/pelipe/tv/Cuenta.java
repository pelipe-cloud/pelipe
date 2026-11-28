package online.pelipe.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

/** Ventana "Cuenta": vincular la app con tu cuenta de Pelipe (código de 6 dígitos + QR en TV) y sincronizar. */
final class Cuenta {
    private Cuenta() {
    }

    private static final String WEB = "https://pelipe.online/";

    static void show(final Activity a) {
        final Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        final boolean tv = Ui.tv(a);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.shape(Ui.BG2, 0x55FFB347, Ui.dp(a, 1), Ui.dp(a, 18)));
        box.setPadding(Ui.dp(a, 22), Ui.dp(a, 18), Ui.dp(a, 22), Ui.dp(a, 16));
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        final boolean linked = Sync.linked(a);
        if (linked) {
            final TextView av = Ui.text(a, Sync.avatar(a), 44, 0xFFFFFFFF, false);
            av.setGravity(Gravity.CENTER);
            av.setFocusable(true);
            av.setClickable(true);
            av.setContentDescription("Cambiar avatar");
            av.setPadding(Ui.dp(a, 18), Ui.dp(a, 6), Ui.dp(a, 18), Ui.dp(a, 6));
            av.setBackground(Ui.states(Ui.shape(0x33FF7A18, Ui.RING, Ui.dp(a, 3), Ui.dp(a, 22)), Ui.shape(0, 0, 0, Ui.dp(a, 22))));
            Ui.pop(av, 1.1f, Ui.dp(a, 2));
            av.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    av.setText(Sync.nextAvatar(a));
                    tabCuenta(a);
                }
            });
            box.addView(av);
            TextView avh = Ui.text(a, tv ? "OK sobre el animalito para cambiarlo" : "Toca el animalito para cambiarlo", 11, Ui.MUTE, false);
            avh.setGravity(Gravity.CENTER);
            box.addView(avh);
        }
        TextView title = Ui.text(a, linked ? "Cuenta vinculada ✓" : "Vincular con tu cuenta", 20, 0xFFFFFFFF, true);
        box.addView(title);

        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = Ui.dp(a, 8);

        final TextView status = Ui.text(a, "", 13, Ui.MUTE, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(tv ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        btns.setGravity(Gravity.CENTER);
        btns.setClipChildren(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (tv) {
            bp.leftMargin = Ui.dp(a, 6);
            bp.rightMargin = Ui.dp(a, 6);
        } else {
            bp.topMargin = Ui.dp(a, 8);
        }

        TextView close = Ui.button(a, "Cerrar", false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        if (linked) {
            String who = Sync.who(a);
            TextView t = Ui.text(a, "Tu lista y «Continuar viendo» se sincronizan con tu cuenta" + (who.isEmpty() ? "." : ":\n" + who), 14, 0xFFD6D6E2, false);
            t.setGravity(Gravity.CENTER);
            box.addView(t, wp);
            box.addView(status, wp);
            TextView sync = Ui.button(a, "Sincronizar ahora", true);
            sync.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    status.setText("Sincronizando…");
                    Sync.flush(a);
                    Sync.pullNow(a, null);
                    Repo.MAIN.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            status.setText("✓ Listo. Si no ves cambios, vuelve al inicio.");
                        }
                    }, 1800);
                }
            });
            TextView un = Ui.button(a, "Desvincular", false);
            un.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Sync.unlink(a, new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(a, "Dispositivo desvinculado.", Toast.LENGTH_SHORT).show();
                            tabCuenta(a);
                            d.dismiss();
                        }
                    });
                }
            });
            btns.addView(sync, bp);
            btns.addView(un, bp);
            btns.addView(close, bp);
        } else {
            TextView t = Ui.text(a, "1. En tu celular o PC entra a pelipe.online/vincular\n2. Inicia sesión (Google o correo)\n3. Escribe este código:", 14, 0xFFD6D6E2, false);
            t.setGravity(Gravity.CENTER);
            box.addView(t, wp);

            final TextView code = Ui.text(a, "······", 40, Ui.ACC2, true);
            code.setLetterSpacing(0.25f);
            code.setGravity(Gravity.CENTER);
            final int qs = Ui.dp(a, tv ? 120 : 170);
            final ImageView qr = new ImageView(a);
            qr.setVisibility(View.GONE);
            final TextView qrHint = Ui.text(a, "o escanea el QR", 12, Ui.MUTE, false);
            qrHint.setVisibility(View.GONE);
            qrHint.setGravity(Gravity.CENTER);
            if (tv) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(code, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout col = new LinearLayout(a);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                col.addView(qr, new LinearLayout.LayoutParams(qs, qs));
                col.addView(qrHint);
                LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                colp.leftMargin = Ui.dp(a, 24);
                row.addView(col, colp);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.topMargin = Ui.dp(a, 8);
                box.addView(row, rp);
            } else {
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cp.topMargin = Ui.dp(a, 8);
                box.addView(code, cp);
                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(qs, qs);
                qp.topMargin = Ui.dp(a, 8);
                box.addView(qr, qp);
                box.addView(qrHint);
            }

            TextView emailBtn = null;
            status.setText("Generando código…");
            box.addView(status, wp);

            {
                TextView or = Ui.text(a, tv ? "¿Prefieres tu correo? Escríbelo y te enviamos un enlace para abrir en tu celular o PC:" : "¿Prefieres recibirlo en tu correo? Te enviamos un enlace: lo abres y listo.", 13, 0xFFD6D6E2, false);
                or.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                op.topMargin = Ui.dp(a, 14);
                box.addView(or, op);
                final android.widget.EditText em = new android.widget.EditText(a);
                em.setHint("tu@correo.com");
                em.setHintTextColor(Ui.MUTE);
                em.setTextColor(Ui.TX);
                em.setTextSize(16);
                em.setSingleLine(true);
                em.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                em.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
                em.setBackground(Ui.states(Ui.shape(Ui.BG, Ui.RING, Ui.dp(a, 2), Ui.dp(a, 10)), Ui.shape(Ui.BG, Ui.LINE, Ui.dp(a, 1), Ui.dp(a, 10))));
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                ep.topMargin = Ui.dp(a, 8);
                box.addView(em, ep);
                final TextView send = Ui.button(a, "✉ Enviarme enlace al correo", false);
                final long[] last = {0};
                send.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String mail = em.getText().toString().trim();
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
                            status.setText("Escribe un correo válido.");
                            return;
                        }
                        if (System.currentTimeMillis() - last[0] < 30000) {
                            status.setText("Espera unos segundos antes de pedirlo otra vez.");
                            return;
                        }
                        last[0] = System.currentTimeMillis();
                        status.setText("Enviando…");
                        Sync.sendLink(mail, Sync.currentCode(), new Runnable() {
                            @Override
                            public void run() {
                                status.setText("✓ Listo. Abre el correo en tu celular o PC (revisa spam) y toca el enlace. Esta ventana se cierra sola al vincular.");
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                status.setText("No se pudo enviar el correo. Usa el código de arriba.");
                            }
                        });
                    }
                });
                if (tv) {
                    emailBtn = send;
                } else {
                    LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    sp2.topMargin = Ui.dp(a, 8);
                    box.addView(send, sp2);
                }
            }

            final TextView open = Ui.button(a, "Abrir pelipe.online/vincular", true);
            open.setVisibility(View.GONE);
            if (emailBtn != null) btns.addView(emailBtn, bp);
            btns.addView(open, bp);
            btns.addView(close, bp);

            d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(android.content.DialogInterface di) {
                    Sync.cancelPair(a);
                }
            });
            Sync.startPair(a, new Sync.PairCb() {
                @Override
                public void code(final String c) {
                    if (!d.isShowing()) return;
                    code.setText(c);
                    status.setText("El código vence en 15 minutos. Esta ventana se actualiza sola al vincular.");
                    final String link = WEB + "#/vincular?c=" + c;
                    if (tv) {
                        Bitmap b = qr(link, qs);
                        if (b != null) {
                            qr.setImageBitmap(b);
                            qr.setVisibility(View.VISIBLE);
                            qrHint.setVisibility(View.VISIBLE);
                        }
                    } else {
                        open.setVisibility(View.VISIBLE);
                        open.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                                } catch (Exception e) {
                                    Toast.makeText(a, "No se pudo abrir el navegador.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                }

                @Override
                public void linked(String who) {
                    if (d.isShowing()) d.dismiss();
                    Toast.makeText(a, "✓ Cuenta vinculada", Toast.LENGTH_LONG).show();
                    tabCuenta(a);
                }

                @Override
                public void fail(String msg) {
                    if (d.isShowing()) status.setText(msg);
                }
            });
        }

        LinearLayout.LayoutParams bbp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bbp.topMargin = Ui.dp(a, 14);
        box.addView(btns, bbp);

        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(w.getAttributes());
            lp.width = Math.min(Ui.dp(a, tv ? 560 : 420), a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 32));
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.dimAmount = 0.7f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
    }

    /** Pone en la pestaña "Cuenta" el animalito (o 👤 si no hay sesión vinculada). */
    static void tabCuenta(Activity a) {
        View v = a.getWindow().getDecorView().findViewWithTag("tab:cuenta");
        if (v instanceof TextView) ((TextView) v).setText((Sync.linked(a) ? Sync.avatar(a) : "👤") + " Cuenta");
    }

    private static Bitmap qr(String text, int size) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix bm = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            int w = bm.getWidth(), h = bm.getHeight();
            int scale = Math.max(1, size / w);
            int[] px = new int[w * scale * h * scale];
            int W = w * scale;
            for (int y = 0; y < h * scale; y++)
                for (int x = 0; x < W; x++) px[y * W + x] = bm.get(x / scale, y / scale) ? 0xFF000000 : 0xFFFFFFFF;
            Bitmap b = Bitmap.createBitmap(W, h * scale, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, W, 0, 0, W, h * scale);
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
