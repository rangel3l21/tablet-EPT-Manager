package deltazero.amarok.ui;

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import deltazero.amarok.R;
import deltazero.amarok.utils.CategoryManager;

public class CategoryDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_CATEGORY_ID = "category_id";
    
    private String categoryId;
    private CategoryManager.FirewallCategory currentCategory;
    private Map<String, CategoryManager.FirewallCategory> allCategories;

    private MaterialToolbar toolbar;
    private MaterialSwitch switchCategory;
    private RecyclerView rvDomains;
    private TextView tvEmpty;
    private DomainAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_details);

        categoryId = getIntent().getStringExtra(EXTRA_CATEGORY_ID);
        if (categoryId == null) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.category_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        switchCategory = findViewById(R.id.switch_category_details);
        rvDomains = findViewById(R.id.rv_domains);
        tvEmpty = findViewById(R.id.tv_empty);
        ExtendedFloatingActionButton fabAddDomain = findViewById(R.id.fab_add_domain);

        rvDomains.setLayoutManager(new LinearLayoutManager(this));

        fabAddDomain.setOnClickListener(v -> showAddDomainDialog());

        loadData();
    }

    private void loadData() {
        allCategories = CategoryManager.getCategories();
        currentCategory = allCategories.get(categoryId);

        if (currentCategory == null) {
            finish();
            return;
        }

        toolbar.setTitle(currentCategory.icon + " " + currentCategory.name);

        switchCategory.setOnCheckedChangeListener(null);
        switchCategory.setChecked(currentCategory.isEnabled);
        switchCategory.setOnCheckedChangeListener((btn, isChecked) -> {
            currentCategory.isEnabled = isChecked;
            CategoryManager.saveCategories(allCategories.values());
        });

        // Sort domains alphabetically
        Collections.sort(currentCategory.domains);

        if (adapter == null) {
            adapter = new DomainAdapter(currentCategory.domains);
            rvDomains.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        if (currentCategory.domains.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvDomains.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvDomains.setVisibility(View.VISIBLE);
        }
    }

    private void showAddDomainDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_url, null);
        EditText etUrl = dialogView.findViewById(R.id.et_url);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Adicionar Domínio")
                .setView(dialogView)
                .setPositiveButton("Adicionar", (d, w) -> {
                    String url = etUrl.getText().toString().trim().toLowerCase()
                            .replaceAll("https?://", "").replaceAll("/.*", "");

                    if (!url.isEmpty() && !currentCategory.domains.contains(url)) {
                        currentCategory.domains.add(url);
                        currentCategory.isEnabled = true; // Auto-enable
                        CategoryManager.saveCategories(allCategories.values());
                        loadData(); // Re-sort and update
                        Toast.makeText(this, "\"" + url + "\" adicionado!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showEditDomainDialog(String domain, int position) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_domain, null);
        TextView tvDomain = dialogView.findViewById(R.id.tv_domain_name);
        android.widget.Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);

        tvDomain.setText(domain);

        List<CategoryManager.FirewallCategory> categoriesList = new java.util.ArrayList<>(allCategories.values());
        Collections.sort(categoriesList, (c1, c2) -> {
            List<String> order = java.util.Arrays.asList("social", "games", "porn", "outros");
            int idx1 = order.indexOf(c1.id);
            int idx2 = order.indexOf(c2.id);
            if (idx1 == -1) idx1 = 99;
            if (idx2 == -1) idx2 = 99;
            return Integer.compare(idx1, idx2);
        });

        List<String> catNames = new java.util.ArrayList<>();
        int currentSelection = 0;
        for (int i = 0; i < categoriesList.size(); i++) {
            CategoryManager.FirewallCategory cat = categoriesList.get(i);
            catNames.add(cat.icon + " " + cat.name);
            if (cat.id.equals(currentCategory.id)) {
                currentSelection = i;
            }
        }

        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, catNames);
        spinnerCategory.setAdapter(spinnerAdapter);
        spinnerCategory.setSelection(currentSelection);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Gerenciar Domínio")
                .setView(dialogView)
                .setPositiveButton("Salvar", (d, w) -> {
                    int selectedPos = spinnerCategory.getSelectedItemPosition();
                    if (selectedPos < 0 || selectedPos >= categoriesList.size()) return;
                    
                    CategoryManager.FirewallCategory selectedCat = categoriesList.get(selectedPos);
                    
                    if (!selectedCat.id.equals(currentCategory.id)) {
                        currentCategory.domains.remove(domain);
                        if (!selectedCat.domains.contains(domain)) {
                            selectedCat.domains.add(domain);
                            selectedCat.isEnabled = true;
                        }
                        CategoryManager.saveCategories(allCategories.values());
                        loadData();
                        Toast.makeText(this, "Movido para " + selectedCat.name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Excluir", (d, w) -> {
                    currentCategory.domains.remove(domain);
                    CategoryManager.saveCategories(allCategories.values());
                    loadData();
                    Toast.makeText(this, "Domínio excluído!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    class DomainAdapter extends RecyclerView.Adapter<DomainAdapter.VH> {
        private final List<String> domains;

        DomainAdapter(List<String> domains) {
            this.domains = domains;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            String domain = domains.get(position);
            holder.text.setText(domain);
            
            holder.itemView.setOnClickListener(v -> {
                showEditDomainDialog(domain, position);
            });
            
            holder.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(CategoryDetailsActivity.this)
                        .setTitle("Remover Domínio")
                        .setMessage("Deseja remover \"" + domain + "\" desta categoria?")
                        .setPositiveButton("Remover", (d, w) -> {
                            domains.remove(position);
                            CategoryManager.saveCategories(allCategories.values());
                            notifyItemRemoved(position);
                            notifyItemRangeChanged(position, domains.size());
                            updateEmptyState();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return true;
            });
        }

        @Override public int getItemCount() { return domains.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView text;
            VH(View v) { super(v); text = v.findViewById(android.R.id.text1); }
        }
    }
}
