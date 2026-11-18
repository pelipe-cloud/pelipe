package online.pelipe.tv;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Pantalla del código de acceso (solo si ACCESO_APP está en true en config.js). */
public class GateActivity extends AppCompatActivity {
    private EditText input;
    private TextView msg, go;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int motivo = getIntent().getIntExtra("motivo", Access.NO_CODE);
        int mg = Ui.margin(this);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(mg, Ui.dp(this, 24), mg, Ui.dp(this, 24));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(Ui.text(this, "PELIPE", 40, Ui.ACC, true));
        TextView sub = Ui.text(this, "Acceso a la app", 20, Ui.TX, true);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 4));
        col.addView(sub);
        String precio = Repo.precio.isEmpty() ? "" : "Pago único de " + Repo.precio + ". ";
        TextView d = Ui.text(this, precio + "Escribe el código que recibiste al pagar.", 15, Ui.MUTE, false);
        d.setGravity(Gravity.CENTER);
        d.setPadding(0, 0, 0, Ui.dp(this, 14));
        col.addView(d);

        int fw = Ui.dp(this, 340);
        input = new EditText(this);
        input.setHint("PELI-XXXX-XXXX");
        input.setHintTextColor(Ui.MUTE);
        input.setTextColor(Ui.TX);
        input.setTextSize(20);
        input.setGravity(Gravity.CENTER);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(20)});
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        float r = Ui.dp(this, 28);
        input.setBackground(Ui.states(Ui.shape(Ui.BG3, Ui.ACC2, Ui.dp(this, 3), r), Ui.shape(Ui.BG3, Ui.LINE, Ui.dp(this, 1), r)));
        col.addView(input, new LinearLayout.LayoutParams(fw, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent ev) {
                activar();
                return true;
            }
        });

        msg = Ui.text(this, mensaje(motivo), 14, 0xFFFF8A80, false);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        col.addView(msg, new LinearLayout.LayoutParams(fw, ViewGroup.LayoutParams.WRAP_CONTENT));

        go = Ui.button(this, "Activar", true);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activar();
            }
        });
        col.addView(go, new LinearLayout.LayoutParams(fw, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!Repo.pagoUrl.isEmpty()) {
            TextView buy = Ui.button(this, "Comprar acceso" + (Repo.precio.isEmpty() ? "" : " · " + Repo.precio), false);
            buy.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    abrir(Repo.pagoUrl);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(fw, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Ui.dp(this, 10);
            col.addView(buy, p);
            if (Ui.tv(this)) {
                TextView u = Ui.text(this, "Si no se abre, paga desde tu celular en:\n" + Repo.pagoUrl, 12, Ui.MUTE, false);
                u.setGravity(Gravity.CENTER);
                u.setPadding(0, Ui.dp(this, 6), 0, 0);
                col.addView(u, new LinearLayout.LayoutParams(fw, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        if (!Repo.whatsapp.isEmpty()) {
            TextView wa = Ui.button(this, "Pedir mi código por WhatsApp", false);
            wa.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    abrir("https://wa.me/" + Repo.whatsapp + "?text=" + Uri.encode("Hola, ya pagué el acceso a la app de Pelipe y quiero mi código."));
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(fw, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = Ui.dp(this, 10);
            col.addView(wa, p);
        }
        setContentView(sv);
        input.requestFocus();
    }

    private String mensaje(int motivo) {
        switch (motivo) {
            case Access.INVALID:
                return "Ese código ya no es válido. Escribe uno nuevo.";
            case Access.IN_USE:
                return "Este código está activo en otro dispositivo.";
            case Access.EXPIRED:
                return "Tu acceso venció. Escribe un código nuevo.";
            case Access.NET:
                return "Necesitamos internet para verificar tu acceso.";
            default:
                return "";
        }
    }

    private void abrir(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            msg.setText("No se pudo abrir el enlace en este dispositivo.");
        }
    }

    private void activar() {
        if (busy) return;
        final String raw = input.getText().toString();
        if (Access.norm(raw).isEmpty()) {
            msg.setText("Escribe el código completo (PELI-XXXX-XXXX).");
            return;
        }
        busy = true;
        go.setText("Verificando…");
        msg.setText("");
        Repo.IO.execute(new Runnable() {
            @Override
            public void run() {
                final int r = Access.activate(getApplicationContext(), raw);
                Repo.MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (isFinishing()) return;
                        go.setText("Activar");
                        if (r == Access.OK) {
                            Intent i = new Intent(GateActivity.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(i);
                            finish();
                        } else if (r == Access.NET) {
                            msg.setText("No se pudo verificar. Revisa tu internet e inténtalo de nuevo.");
                        } else {
                            msg.setText(mensaje(r));
                        }
                    }
                });
            }
        });
    }
}
