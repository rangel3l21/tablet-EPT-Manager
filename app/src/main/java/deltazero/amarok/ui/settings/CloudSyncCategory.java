package deltazero.amarok.ui.settings;

import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.SyncManager;
import deltazero.amarok.ui.LoginActivity;

public class CloudSyncCategory extends BaseCategory {

    private Preference loginPref;
    private Preference syncPref;

    public CloudSyncCategory(@NonNull FragmentActivity activity, PreferenceScreen screen) {
        super(activity, screen);
        category.setTitle("Supabase Sync");

        loginPref = new Preference(activity);
        loginPref.setIcon(R.drawable.ic_paw); // Using an existing drawable
        loginPref.setOnPreferenceClickListener(preference -> {
            if (PrefMgr.getSupabaseToken() == null) {
                activity.startActivity(new Intent(activity, LoginActivity.class));
            } else {
                // Logout
                PrefMgr.setSupabaseToken(null);
                PrefMgr.setSupabaseUserId(null);
                PrefMgr.setSupabaseEmail(null);
                Toast.makeText(activity, "Logout efetuado com sucesso.", Toast.LENGTH_SHORT).show();
                updateUI();
            }
            return true;
        });
        addPreference(loginPref);

        syncPref = new Preference(activity);
        syncPref.setIcon(R.drawable.ic_paw);
        syncPref.setTitle("Sincronizar Agora");
        syncPref.setSummary("Envia e baixa as configurações mais recentes da nuvem");
        syncPref.setOnPreferenceClickListener(preference -> {
            if (PrefMgr.getSupabaseToken() == null) {
                Toast.makeText(activity, "Por favor, faça login primeiro.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "Sincronizando...", Toast.LENGTH_SHORT).show();
                SyncManager.pushSettings(new SyncManager.SyncResultCallback() {
                    @Override
                    public void onComplete(boolean success, String message) {
                        if (success) {
                            SyncManager.pullSettings(activity, new SyncManager.SyncResultCallback() {
                                @Override
                                public void onComplete(boolean success, String message) {
                                    activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
                                }
                            });
                        } else {
                            activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
                        }
                    }
                });
            }
            return true;
        });
        addPreference(syncPref);

        updateUI();
    }

    private void updateUI() {
        String email = PrefMgr.getSupabaseEmail();
        if (email != null) {
            loginPref.setTitle("Logout");
            loginPref.setSummary("Logado como: " + email);
            syncPref.setEnabled(true);
        } else {
            loginPref.setTitle("Login / Criar Conta");
            loginPref.setSummary("Sincronize a lista de apps ocultos entre seus dispositivos");
            syncPref.setEnabled(false);
        }
    }

    @Override
    public void notifyUpdate() {
        super.notifyUpdate();
        updateUI();
    }
}
