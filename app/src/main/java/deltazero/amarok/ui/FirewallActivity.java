package deltazero.amarok.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.network.FirewallVpnService;
import deltazero.amarok.utils.CategoryManager;

public class FirewallActivity extends AppCompatActivity {

    private MaterialSwitch switchFirewall;
    private RecyclerView rvCategories;
    private CategoryAdapter adapter;
    private List<CategoryManager.FirewallCategory> categoriesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firewall);

        MaterialToolbar toolbar = findViewById(R.id.firewall_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        switchFirewall = findViewById(R.id.switch_firewall);
        rvCategories  = findViewById(R.id.rv_categories);
        ExtendedFloatingActionButton fabAddUrl = findViewById(R.id.fab_add_url);

        rvCategories.setLayoutManager(new LinearLayoutManager(this));

        // Firewall switch
        switchFirewall.setChecked(PrefMgr.getFirewallEnabled());
        switchFirewall.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                Intent vpnIntent = android.net.VpnService.prepare(this);
                if (vpnIntent != null) {
                    startActivityForResult(vpnIntent, 0x0F);
                } else {
                    startVpnService();
                }
            } else {
                stopVpnService();
            }
        });

        fabAddUrl.setOnClickListener(v -> showAddUrlDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCategories();
    }

    private void loadCategories() {
        Map<String, CategoryManager.FirewallCategory> map = CategoryManager.getCategories();
        categoriesList = new ArrayList<>(map.values());
        // Sort specifically: Social, Games, Porn, Others
        Collections.sort(categoriesList, (c1, c2) -> {
            List<String> order = java.util.Arrays.asList("social", "games", "porn", "outros");
            int idx1 = order.indexOf(c1.id);
            int idx2 = order.indexOf(c2.id);
            if (idx1 == -1) idx1 = 99;
            if (idx2 == -1) idx2 = 99;
            return Integer.compare(idx1, idx2);
        });

        if (adapter == null) {
            adapter = new CategoryAdapter(categoriesList);
            rvCategories.setAdapter(adapter);
        } else {
            adapter.updateData(categoriesList);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0x0F) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                switchFirewall.setChecked(false);
                Toast.makeText(this, "Permissão de VPN negada", Toast.LENGTH_SHORT).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startVpnService() {
        PrefMgr.setFirewallEnabled(true);
        Intent svcIntent = new Intent(this, FirewallVpnService.class);
        svcIntent.setAction(FirewallVpnService.ACTION_START);
        startService(svcIntent);
        Toast.makeText(this, "🔒 Firewall ativado", Toast.LENGTH_SHORT).show();
    }

    private void stopVpnService() {
        PrefMgr.setFirewallEnabled(false);
        Intent svcIntent = new Intent(this, FirewallVpnService.class);
        svcIntent.setAction(FirewallVpnService.ACTION_STOP);
        startService(svcIntent);
        Toast.makeText(this, "🔓 Firewall desativado", Toast.LENGTH_SHORT).show();
    }

    private void showAddUrlDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_url_category, null);
        EditText etUrl = dialogView.findViewById(R.id.et_url);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);

        List<String> catNames = new ArrayList<>();
        for (CategoryManager.FirewallCategory cat : categoriesList) {
            catNames.add(cat.icon + " " + cat.name);
        }
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catNames);
        spinnerCategory.setAdapter(spinnerAdapter);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Bloquear Site")
                .setView(dialogView)
                .setPositiveButton("Adicionar", (d, w) -> {
                    String url = etUrl.getText().toString().trim().toLowerCase()
                            .replaceAll("https?://", "").replaceAll("/.*", "");
                    
                    int selectedPos = spinnerCategory.getSelectedItemPosition();
                    if (selectedPos < 0 || selectedPos >= categoriesList.size()) return;
                    
                    CategoryManager.FirewallCategory selectedCat = categoriesList.get(selectedPos);

                    if (!url.isEmpty() && !selectedCat.domains.contains(url)) {
                        selectedCat.domains.add(url);
                        // Make sure the category is enabled so the newly added site is blocked
                        selectedCat.isEnabled = true;
                        CategoryManager.saveCategories(categoriesList);
                        loadCategories();
                        Toast.makeText(this, "\"" + url + "\" adicionado à " + selectedCat.name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.VH> {
        private List<CategoryManager.FirewallCategory> items;

        CategoryAdapter(List<CategoryManager.FirewallCategory> items) { 
            this.items = items; 
        }

        void updateData(List<CategoryManager.FirewallCategory> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_firewall_category, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            CategoryManager.FirewallCategory cat = items.get(position);
            holder.tvIcon.setText(cat.icon);
            holder.tvName.setText(cat.name);
            holder.tvCount.setText(cat.domains.size() + " sites cadastrados");
            
            holder.switchCat.setOnCheckedChangeListener(null);
            holder.switchCat.setChecked(cat.isEnabled);
            
            holder.switchCat.setOnCheckedChangeListener((buttonView, isChecked) -> {
                cat.isEnabled = isChecked;
                CategoryManager.saveCategories(items);
            });

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(FirewallActivity.this, CategoryDetailsActivity.class);
                intent.putExtra(CategoryDetailsActivity.EXTRA_CATEGORY_ID, cat.id);
                startActivity(intent);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvName, tvCount;
            MaterialSwitch switchCat;
            VH(View v) { 
                super(v); 
                tvIcon = v.findViewById(R.id.tv_cat_icon); 
                tvName = v.findViewById(R.id.tv_cat_name); 
                tvCount = v.findViewById(R.id.tv_cat_count); 
                switchCat = v.findViewById(R.id.switch_cat); 
            }
        }
    }
}
