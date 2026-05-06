package deltazero.amarok.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.models.WishlistApp;
import deltazero.amarok.network.SupabaseClient;
import deltazero.amarok.utils.AppInstallManager;

public class WishlistActivity extends AppCompatActivity {

    private RecyclerView rvWishlist;
    private WishlistAdapter adapter;
    private LinearProgressIndicator piLoading;
    private MaterialButton btnProcessQueue;
    private List<WishlistApp> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wishlist);

        MaterialToolbar toolbar = findViewById(R.id.wishlist_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvWishlist = findViewById(R.id.rv_wishlist);
        piLoading = findViewById(R.id.pi_loading);
        btnProcessQueue = findViewById(R.id.btn_process_queue);

        rvWishlist.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WishlistAdapter(apps);
        rvWishlist.setAdapter(adapter);

        btnProcessQueue.setOnClickListener(v -> startProcessing());

        AppInstallManager.getInstance(this).setListener(app -> adapter.updateAppStatus(app));

        loadWishlist();
    }

    private void loadWishlist() {
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();
        
        if (token == null || userId == null) {
            Toast.makeText(this, "Você precisa estar logado para acessar os aplicativos.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        piLoading.setVisibility(View.VISIBLE);
        SupabaseClient.fetchWishlist(token, userId, new SupabaseClient.WishlistCallback() {
            @Override
            public void onSuccess(List<WishlistApp> fetchedApps) {
                runOnUiThread(() -> {
                    piLoading.setVisibility(View.GONE);
                    apps = fetchedApps;
                    adapter.updateData(apps);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    android.util.Log.e("WishlistActivity", "API ERROR: " + error);
                    piLoading.setVisibility(View.GONE);
                    if (error != null && error.toLowerCase().contains("jwt expired")) {
                        Toast.makeText(WishlistActivity.this, "Sua sessão expirou. Por favor, vá nas configurações e faça login novamente.", Toast.LENGTH_LONG).show();
                        PrefMgr.setSupabaseToken(null);
                        finish();
                    } else {
                        Toast.makeText(WishlistActivity.this, "Erro: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void startProcessing() {
        if (apps.isEmpty()) {
            Toast.makeText(this, "A lista está vazia.", Toast.LENGTH_SHORT).show();
            return;
        }
        btnProcessQueue.setEnabled(false);
        AppInstallManager.getInstance(this).startQueue(apps);
    }
}
