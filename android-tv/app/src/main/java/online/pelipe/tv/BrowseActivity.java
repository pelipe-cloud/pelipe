package online.pelipe.tv;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** Rejilla de títulos: Películas, Series, Mi lista y Buscar. */
public class BrowseActivity extends AppCompatActivity {
    private String mode = "peliculas";
    private final List<JSONObject> shown = new ArrayList<JSONObject>();
    private RecyclerView grid;
    private TextView empty;
    private EditText q;
    private String genre = "";
    private String query = "";
    private LinearLayout chips;
    private String snap = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String m = getIntent().getStringExtra("mode");
        if (m != null) mode = m;

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Ui.BG);
        shell.addView(Ui.topBar(this, mode));

        int mg = Ui.margin(this);
        if (mode.equals("buscar")) {
            q = new EditText(this);
            q.setHint("Buscar películas y series…");
            q.setHintTextColor(Ui.MUTE);
            q.setTextColor(Ui.TX);
            q.setTextSize(17);
            q.setSingleLine(true);
            q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            q.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
            Ui.plain(q);
        float r = Ui.dp(this, 26);
            q.setBackground(Ui.states(Ui.shape(Ui.BG3, Ui.ACC2, Ui.dp(this, 3), r), Ui.shape(Ui.BG3, Ui.LINE, Ui.dp(this, 1), r)));
            LinearLayout.LayoutParams qp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            qp.setMargins(mg, Ui.dp(this, 6), mg, Ui.dp(this, 6));
            shell.addView(q, qp);
            q.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b2, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b2, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    query = Repo.norm(s.toString()).trim();
                    refresh();
                }
            });
        } else if (mode.equals("peliculas") || mode.equals("series")) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipToPadding(false);
            chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setPadding(mg, Ui.dp(this, 6), mg, Ui.dp(this, 6));
            hs.addView(chips);
            shell.addView(hs);
        }

        FrameLayout body = new FrameLayout(this);
        grid = new RecyclerView(this);
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(mg - Ui.dp(this, 3), Ui.dp(this, 8), mg - Ui.dp(this, 3), Ui.dp(this, 24));
        int cell = Ui.cardW(this) + Ui.dp(this, 14);
        int w = getResources().getDisplayMetrics().widthPixels;
        int span = Math.max(2, (w - 2 * mg) / cell);
        grid.setLayoutManager(new GridLayoutManager(this, span));
        grid.setAdapter(new Adapter());
        body.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        empty = Ui.text(this, "", 16, Ui.MUTE, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(mg, mg, mg, mg);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.gravity = Gravity.CENTER;
        body.addView(empty, ep);
        shell.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(shell);

        Repo.load(this, false, new Repo.Listener() {
            @Override
            public void onData() {
                if (isFinishing()) return;
                buildChips();
                refresh();
            }

            @Override
            public void onError(String msg) {
                empty.setText(msg);
            }
        });
    }

    private void buildChips() {
        if (chips == null) return;
        chips.removeAllViews();
        TreeSet<String> gs = new TreeSet<String>();
        for (JSONObject o : Repo.list(mode.equals("series"))) gs.addAll(Repo.genres(o));
        addChip("🎬 Todos", "");
        for (String g : gs) addChip(Repo.genreEmoji(g) + " " + g, g);
    }

    private void addChip(String label, final String g) {
        TextView c = Ui.chip(this, label, g.equals(genre));
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = Ui.dp(this, 8);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                genre = g;
                buildChips();
                refresh();
            }
        });
        chips.addView(c, p);
    }

    private void refresh() {
        snap = Repo.snap(this);
        shown.clear();
        if (mode.equals("peliculas") || mode.equals("series")) {
            for (JSONObject o : Repo.list(mode.equals("series"))) {
                if (genre.isEmpty() || Repo.genres(o).contains(genre)) shown.add(o);
            }
            empty.setText(shown.isEmpty() ? "No hay títulos en esta categoría." : "");
        } else if (mode.equals("lista")) {
            for (String id : Repo.lista(this)) {
                JSONObject o = Repo.byId(id);
                if (o != null && Repo.visible(o)) shown.add(o);
            }
            empty.setText(shown.isEmpty() ? "Tu lista está vacía.\nAbre una película o serie y elige \"Mi lista\"." : "");
        } else {
            if (query.length() >= 1) {
                for (JSONObject o : Repo.list(null)) {
                    if (Repo.norm(Repo.titulo(o)).contains(query)) shown.add(o);
                }
                empty.setText(shown.isEmpty() ? "No encontramos \"" + q.getText().toString().trim() + "\"." : "");
            } else {
                empty.setText("Escribe el nombre de una película o serie.");
            }
        }
        if (mode.equals("buscar")) {
            Collections.sort(shown, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    return Repo.titulo(a).compareToIgnoreCase(Repo.titulo(b));
                }
            });
        }
        grid.getAdapter().notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mode.equals("lista") && !snap.equals(Repo.snap(this)) && !Repo.all.isEmpty()) refresh();
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            FrameLayout f = new FrameLayout(BrowseActivity.this);
            f.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            f.setClipChildren(false);
            return new RecyclerView.ViewHolder(f) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            final JSONObject o = shown.get(pos);
            FrameLayout f = (FrameLayout) h.itemView;
            f.removeAllViews();
            View c = Ui.card(BrowseActivity.this, o, Ui.cardW(BrowseActivity.this), -1, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Ui.open(BrowseActivity.this, o);
                }
            });
            f.addView(c, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}
