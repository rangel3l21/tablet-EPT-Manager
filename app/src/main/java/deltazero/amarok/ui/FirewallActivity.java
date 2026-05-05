package deltazero.amarok.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.network.FirewallVpnService;

public class FirewallActivity extends AppCompatActivity {

    // Pre-defined domain categories
    private static final List<String> SOCIAL_DOMAINS = Arrays.asList(
            "instagram.com", "facebook.com", "tiktok.com", "twitter.com",
            "x.com", "snapchat.com", "youtube.com", "reddit.com",
            "whatsapp.com", "telegram.org", "discord.com"
    );
    private static final List<String> GAMES_DOMAINS = Arrays.asList(
            "roblox.com", "miniclip.com", "friv.com", "poki.com",
            "crazygames.com", "gameflare.com", "y8.com", "kizi.com",
            "agame.com", "armor games.com", "kongregate.com", "steam.com"
    );

    private MaterialSwitch switchFirewall;
    private RecyclerView rvBlockedUrls;
    private TextView tvEmpty;
    private UrlAdapter adapter;
    private List<String> urlList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firewall);

        MaterialToolbar toolbar = findViewById(R.id.firewall_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        switchFirewall = findViewById(R.id.switch_firewall);
        rvBlockedUrls  = findViewById(R.id.rv_blocked_urls);
        tvEmpty        = findViewById(R.id.tv_empty);

        MaterialButton btnAddUrl     = findViewById(R.id.btn_add_url);
        ChipGroup      chipGroup     = findViewById(R.id.chip_group_categories);
        Chip           chipSocial    = findViewById(R.id.chip_social);
        Chip           chipGames     = findViewById(R.id.chip_games);

        // Load saved blocked URLs
        urlList = new ArrayList<>(PrefMgr.getBlockedUrls());

        adapter = new UrlAdapter(urlList, pos -> {
            String url = urlList.get(pos);
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Remover Site")
                    .setMessage("Remover \"" + url + "\" da lista?")
                    .setPositiveButton("Remover", (d, w) -> {
                        urlList.remove(pos);
                        adapter.notifyItemRemoved(pos);
                        adapter.notifyItemRangeChanged(pos, urlList.size());
                        saveAndApply();
                        updateEmptyState();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        rvBlockedUrls.setLayoutManager(new LinearLayoutManager(this));
        rvBlockedUrls.setAdapter(adapter);
        updateEmptyState();

        // Init chips state
        chipSocial.setChecked(containsAll(urlList, SOCIAL_DOMAINS));
        chipGames.setChecked(containsAll(urlList, GAMES_DOMAINS));

        chipSocial.setOnCheckedChangeListener((btn, checked) ->
                toggleCategory(SOCIAL_DOMAINS, checked, "Redes Sociais"));
        chipGames.setOnCheckedChangeListener((btn, checked) ->
                toggleCategory(GAMES_DOMAINS, checked, "Jogos Online"));

        // Firewall switch
        switchFirewall.setChecked(PrefMgr.getFirewallEnabled());
        switchFirewall.setOnCheckedChangeListener((btn, isChecked) -> {
            PrefMgr.setFirewallEnabled(isChecked);
            Intent svcIntent = new Intent(this, FirewallVpnService.class);
            svcIntent.setAction(isChecked
                    ? FirewallVpnService.ACTION_START
                    : FirewallVpnService.ACTION_STOP);
            startService(svcIntent);
            Toast.makeText(this,
                    isChecked ? "🔒 Firewall ativado" : "🔓 Firewall desativado",
                    Toast.LENGTH_SHORT).show();
        });

        btnAddUrl.setOnClickListener(v -> showAddUrlDialog());
    }

    private void toggleCategory(List<String> domains, boolean add, String label) {
        Set<String> urlSet = new HashSet<>(urlList);
        if (add) {
            for (String d : domains) {
                if (!urlSet.contains(d)) {
                    urlSet.add(d);
                    urlList.add(d);
                }
            }
            Toast.makeText(this, label + " adicionado à lista", Toast.LENGTH_SHORT).show();
        } else {
            urlSet.removeAll(domains);
            urlList.removeAll(domains);
            Toast.makeText(this, label + " removido da lista", Toast.LENGTH_SHORT).show();
        }
        adapter.notifyDataSetChanged();
        saveAndApply();
        updateEmptyState();
    }

    private boolean containsAll(List<String> list, List<String> check) {
        return list.containsAll(check);
    }

    private void updateEmptyState() {
        tvEmpty.setVisibility(urlList.isEmpty() ? View.VISIBLE : View.GONE);
        rvBlockedUrls.setVisibility(urlList.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showAddUrlDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_url, null);
        EditText etUrl = dialogView.findViewById(R.id.et_url);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Bloquear Site")
                .setMessage("Digite o domínio (ex: instagram.com):")
                .setView(dialogView)
                .setPositiveButton("Adicionar", (d, w) -> {
                    String url = etUrl.getText().toString().trim().toLowerCase()
                            .replaceAll("https?://", "").replaceAll("/.*", "");
                    if (!url.isEmpty() && !urlList.contains(url)) {
                        urlList.add(url);
                        adapter.notifyItemInserted(urlList.size() - 1);
                        saveAndApply();
                        updateEmptyState();
                        Toast.makeText(this, "\"" + url + "\" bloqueado!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void saveAndApply() {
        PrefMgr.setBlockedUrls(new HashSet<>(urlList));
        // If firewall is active, restart the VPN service to apply new block list
        if (switchFirewall.isChecked()) {
            Intent svcIntent = new Intent(this, FirewallVpnService.class);
            svcIntent.setAction(FirewallVpnService.ACTION_START);
            startService(svcIntent);
        }
    }

    // ---- Inner RecyclerView Adapter ----
    static class UrlAdapter extends RecyclerView.Adapter<UrlAdapter.VH> {
        interface LongClickListener { void onLongClick(int pos); }
        private final List<String> items;
        private final LongClickListener listener;
        UrlAdapter(List<String> items, LongClickListener l) { this.items = items; this.listener = l; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.text.setText(items.get(position));
            holder.itemView.setOnLongClickListener(v -> {
                listener.onLongClick(holder.getAdapterPosition());
                return true;
            });
        }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView text;
            VH(View v) { super(v); text = v.findViewById(android.R.id.text1); }
        }
    }
}
