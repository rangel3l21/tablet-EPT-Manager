package deltazero.amarok.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.models.WishlistApp;
import deltazero.amarok.network.SupabaseClient;
import deltazero.amarok.services.AppLockAccessibilityService;
import deltazero.amarok.utils.AppInstallManager;
import deltazero.amarok.utils.AuroraApiManager;
import deltazero.amarok.utils.DeviceStateManager;

public class WishlistActivity extends AppCompatActivity {

    private interface SessionReadyCallback {
        void onReady(String token, String userId);
    }

    private RecyclerView rvWishlist;
    private WishlistAdapter adapter;
    private LinearProgressIndicator piLoading;
    private MaterialButton btnProcessQueue;
    private MaterialButton btnAddApp;
    private View tvEmpty;
    private List<WishlistApp> apps = new ArrayList<>();
    private List<WishlistApp> pendingInstallSelection;
    private boolean waitingForUnknownSourcesPermission;
    private DeviceStateManager deviceStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wishlist);

        deviceStateManager = new DeviceStateManager(this);

        MaterialToolbar toolbar = findViewById(R.id.wishlist_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvWishlist = findViewById(R.id.rv_wishlist);
        piLoading = findViewById(R.id.pi_loading);
        btnProcessQueue = findViewById(R.id.btn_process_queue);
        btnAddApp = findViewById(R.id.btn_add_app);
        tvEmpty = findViewById(R.id.tv_empty);

        rvWishlist.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WishlistAdapter(apps);
        rvWishlist.setAdapter(adapter);

        adapter.setOnDeleteClickListener((app, position) -> showDeleteConfirmDialog(app, position));

        AppInstallManager.getInstance(this).setListener(app -> {
            adapter.updateAppStatus(app);
            updateEmptyState();
        });

        btnAddApp.setOnClickListener(v -> showAddAppDialog());
        btnProcessQueue.setOnClickListener(v -> startDownload());
        updateTeacherModeUi();

        loadWishlist();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTeacherModeUi();
        resumeInstallAfterUnknownSourcesPermission();
    }

    private void loadWishlist() {
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();

        if (token == null || userId == null) {
            Toast.makeText(this, "Voce precisa estar logado para acessar a lista.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        piLoading.setVisibility(View.VISIBLE);

        withFreshSession((freshToken, freshUserId) -> fetchWishlist(freshToken, freshUserId));
    }

    private void fetchWishlist(String token, String userId) {
        SupabaseClient.fetchWishlist(token, userId, new SupabaseClient.WishlistCallback() {
            @Override
            public void onSuccess(List<WishlistApp> fetchedApps) {
                runOnUiThread(() -> {
                    piLoading.setVisibility(View.GONE);
                    
                    // Sincroniza cache local de apps instalados neste dispositivo
                    java.util.Set<String> packageNames = new java.util.HashSet<>();
                    for (WishlistApp app : fetchedApps) {
                        packageNames.add(app.packageName);
                    }
                    deviceStateManager.syncInstalledAppsCache(packageNames);
                    
                    resetStaleInstallStates(fetchedApps);
                    applyDeviceSpecificStatus(fetchedApps);
                    apps = fetchedApps;
                    adapter.updateData(apps);
                    updateEmptyState();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    android.util.Log.e("WishlistActivity", "API ERROR: " + error);
                    piLoading.setVisibility(View.GONE);
                    Toast.makeText(WishlistActivity.this,
                            "Erro ao carregar lista: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Aplica status específico do dispositivo atual.
     * Se um app foi marcado como SUCCESS em outro tablet mas não está instalado aqui, marca como PENDING.
     */
    private void applyDeviceSpecificStatus(List<WishlistApp> fetchedApps) {
        for (WishlistApp app : fetchedApps) {
            app.status = deviceStateManager.getDeviceSpecificStatus(app.status, app.packageName);
        }
    }

    private void withFreshSession(SessionReadyCallback callback) {
        String token = PrefMgr.getSupabaseToken();
        String refreshToken = PrefMgr.getSupabaseRefreshToken();
        String userId = PrefMgr.getSupabaseUserId();
        if (token == null || userId == null) {
            clearSessionAndExit();
            return;
        }

        SupabaseClient.ensureFreshSession(token, refreshToken, userId, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String newToken, String newRefreshToken, String newUserId) {
                PrefMgr.setSupabaseToken(newToken);
                PrefMgr.setSupabaseRefreshToken(newRefreshToken);
                PrefMgr.setSupabaseUserId(newUserId);
                callback.onReady(newToken, newUserId);
            }

            @Override
            public void onError(String refreshError) {
                runOnUiThread(() -> clearSessionAndExit());
            }
        });
    }

    private void clearSessionAndExit() {
        piLoading.setVisibility(View.GONE);
        PrefMgr.setSupabaseToken(null);
        PrefMgr.setSupabaseRefreshToken(null);
        Toast.makeText(WishlistActivity.this,
                "Sessao expirada. Faca login novamente nas configuracoes.",
                Toast.LENGTH_LONG).show();
        finish();
    }

    private void showAddAppDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, 0);

        AutoCompleteTextView etPackageName = new AutoCompleteTextView(this);
        etPackageName.setHint("Nome do app ou pacote");
        etPackageName.setSingleLine(true);
        etPackageName.setThreshold(0);
        etPackageName.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etPackageName.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                KnownDownloadApp.getDisplayValues()));
        etPackageName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) etPackageName.showDropDown();
        });
        layout.addView(etPackageName, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle("Adicionar App a Lista")
                .setMessage("Digite ou selecione o nome do app/pacote permitido:")
                .setView(layout)
                .setPositiveButton("Adicionar", (dialog, which) -> {
                    String appQuery = etPackageName.getText().toString().trim();
                    if (appQuery.isEmpty()) {
                        Toast.makeText(this, "Informe o nome do app ou pacote.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    addAppToList(appQuery);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addAppToList(String appQuery) {
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();
        if (token == null || userId == null) {
            Toast.makeText(this, "Voce precisa estar logado.", Toast.LENGTH_SHORT).show();
            return;
        }

        String packageName = KnownDownloadApp.resolvePackage(appQuery);
        if (packageName == null) {
            Toast.makeText(this,
                    "Selecione um app da lista ou digite um pacote permitido.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        piLoading.setVisibility(View.VISIBLE);
        withFreshSession((freshToken, freshUserId) -> addResolvedAppToList(freshToken, freshUserId, packageName));
    }

    private void addResolvedAppToList(String token, String userId, String packageName) {
        SupabaseClient.addToWishlist(token, userId, packageName, new SupabaseClient.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    piLoading.setVisibility(View.GONE);
                    WishlistApp newApp = new WishlistApp(packageName);
                    adapter.addItem(newApp);
                    updateEmptyState();
                    Toast.makeText(WishlistActivity.this,
                            "App adicionado a lista!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    piLoading.setVisibility(View.GONE);
                    Toast.makeText(WishlistActivity.this,
                            "Erro ao adicionar: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showDeleteConfirmDialog(WishlistApp app, int position) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remover App")
                .setMessage("Remover \"" + app.packageName + "\" da lista?")
                .setPositiveButton("Remover", (dialog, which) -> deleteApp(app, position))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteApp(WishlistApp app, int position) {
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();
        if (token == null || userId == null) return;

        withFreshSession((freshToken, freshUserId) ->
                SupabaseClient.deleteFromWishlist(freshToken, freshUserId, app.packageName, new SupabaseClient.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    adapter.removeItem(position);
                    if (apps != null) apps.remove(app);
                    updateEmptyState();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(WishlistActivity.this,
                                "Erro ao remover: " + error, Toast.LENGTH_SHORT).show());
            }
        }));
    }

    private void startDownload() {
        if (PrefMgr.isTeacherMode()) {
            Toast.makeText(this,
                    "Modo professor ativo: este aparelho nao instala apps.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        List<WishlistApp> selected = adapter.getSelectedApps();

        if (selected.isEmpty()) {
            Toast.makeText(this, "Selecione pelo menos um app para baixar.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!getPackageManager().canRequestPackageInstalls()) {
            pendingInstallSelection = new ArrayList<>(selected);
            waitingForUnknownSourcesPermission = true;
            setSettingsTemporarilyUnlocked(true);

            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this,
                    "Permita instalar apps por esta fonte. O download continua ao voltar.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        processSelectedApps(selected);
    }

    private void processSelectedApps(List<WishlistApp> selected) {
        btnProcessQueue.setEnabled(false);
        btnAddApp.setEnabled(false);

        AppInstallManager.getInstance(this).startQueue(selected);

        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    btnProcessQueue.setEnabled(true);
                    btnAddApp.setEnabled(true);
                }, 3000);
    }

    private void resumeInstallAfterUnknownSourcesPermission() {
        if (!waitingForUnknownSourcesPermission) return;

        if (!getPackageManager().canRequestPackageInstalls()) {
            setSettingsTemporarilyUnlocked(false);
            waitingForUnknownSourcesPermission = false;
            pendingInstallSelection = null;
            Toast.makeText(this,
                    "Permissao de instalacao nao ativada.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        List<WishlistApp> selected = pendingInstallSelection;
        setSettingsTemporarilyUnlocked(false);
        waitingForUnknownSourcesPermission = false;
        pendingInstallSelection = null;

        if (selected == null || selected.isEmpty()) return;

        Toast.makeText(this,
                "Permissao ativada. Continuando instalacao.",
                Toast.LENGTH_SHORT).show();
        processSelectedApps(selected);
    }

    private void setSettingsTemporarilyUnlocked(boolean unlocked) {
        if (unlocked) {
            AppLockAccessibilityService.unlockPackage("com.android.settings");
        } else {
            AppLockAccessibilityService.lockPackage("com.android.settings");
        }
    }

    private void updateEmptyState() {
        boolean isEmpty = apps == null || apps.isEmpty();
        tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvWishlist.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateTeacherModeUi() {
        if (btnProcessQueue == null) return;

        boolean teacherMode = PrefMgr.isTeacherMode();
        btnProcessQueue.setEnabled(!teacherMode);
        btnProcessQueue.setText(teacherMode ? "Modo Professor Ativo" : "Baixar Agora");
    }

    private void resetStaleInstallStates(List<WishlistApp> fetchedApps) {
        AppInstallManager manager = AppInstallManager.getInstance(this);
        for (WishlistApp app : fetchedApps) {
            if (WishlistApp.STATUS_ERROR.equals(app.status)) {
                app.errorMessage = PrefMgr.getWishlistError(app.packageName);
            }
            if (isTransientStatus(app.status) && !manager.isInstalling(app.packageName)) {
                app.status = WishlistApp.STATUS_PENDING;
                app.errorMessage = "";
                updateRemoteStatus(app.packageName, WishlistApp.STATUS_PENDING);
            }
        }
    }

    private boolean isTransientStatus(String status) {
        return WishlistApp.STATUS_FETCHING_URLS.equals(status)
                || WishlistApp.STATUS_DOWNLOADING.equals(status)
                || WishlistApp.STATUS_INSTALLING.equals(status);
    }

    private void updateRemoteStatus(String packageName, String status) {
        withFreshSession((token, userId) -> SupabaseClient.updateWishlistStatus(token, userId, packageName, status,
                new SupabaseClient.SimpleCallback() {
                    @Override public void onSuccess() {}
                    @Override public void onError(String error) {
                        android.util.Log.w("WishlistActivity",
                                "Falha ao resetar status da lista: " + error);
                    }
                }));
    }

    private static final class KnownDownloadApp {
        private static final List<KnownDownloadApp> APPS = List.of(
                app("Social", "WhatsApp", "com.whatsapp"),
                app("Social", "Instagram", "com.instagram.android"),
                app("Social", "Facebook", "com.facebook.katana"),
                app("Social", "Messenger", "com.facebook.orca"),
                app("Social", "TikTok", "com.zhiliaoapp.musically"),
                app("Social", "X (Twitter)", "com.twitter.android"),
                app("Social", "Telegram", "org.telegram.messenger"),
                app("Educacao", "Google Classroom", "com.google.android.apps.classroom"),
                app("Educacao", "Khan Academy", "org.khanacademy.android"),
                app("Educacao", "Duolingo", "com.duolingo"),
                app("Educacao", "Photomath", "com.microblink.photomath"),
                app("Educacao", "Kahoot!", "no.mobitroll.kahoot.android"),
                app("Educacao", "Quizlet", "com.quizlet.quizletandroid"),
                app("Robotica", "Scratch", "org.scratch"),
                app("Robotica", "ScratchJr", "org.scratchjr.android"),
                app("Robotica", "LEGO Education SPIKE", "com.lego.education.spike"),
                app("Robotica", "LEGO MINDSTORMS EV3 Home", "com.lego.mindstorms.ev3programmer"),
                app("Robotica", "Arduino Pro IDE (Remote)", "cc.arduino.cloud.iot"),
                app("Robotica", "Tinkercad (Autodesk)", "com.autodesk.tinkercad"),
                app("Robotica", "Expo Go", "host.exp.exponent"),
                app("Produtividade", "Google Planilhas (Sheets)", "com.google.android.apps.docs.editors.sheets"),
                app("Produtividade", "Google Documentos (Docs)", "com.google.android.apps.docs.editors.docs"),
                app("Produtividade", "Google Apresentacoes (Slides)", "com.google.android.apps.docs.editors.slides"),
                app("Produtividade", "Microsoft Excel", "com.microsoft.office.excel"),
                app("Produtividade", "Microsoft Word", "com.microsoft.office.word"),
                app("Produtividade", "Microsoft PowerPoint", "com.microsoft.office.powerpoint"),
                app("Produtividade", "Microsoft 365 (App Unificado)", "com.microsoft.office.officehubrow"),
                app("Utilitarios", "YouTube", "com.google.android.youtube"),
                app("Utilitarios", "Gmail", "com.google.android.gm"),
                app("Utilitarios", "Google Chrome", "com.android.chrome"),
                app("Utilitarios", "Google Maps", "com.google.android.apps.maps"),
                app("Utilitarios", "Google Drive", "com.google.android.apps.docs"),
                app("Utilitarios", "Google Photos", "com.google.android.apps.photos"),
                app("Utilitarios", "Play Store", "com.android.vending"),
                app("Design", "Canva", "com.canva.editor"),
                app("Entretenimento", "Netflix", "com.netflix.mediaclient"),
                app("Entretenimento", "Spotify", "com.spotify.music"),
                app("Financas", "Nubank", "com.nu.production"),
                app("Transporte", "Uber", "com.ubercab"),
                app("Compras", "Mercado Livre", "com.mercadolibre")
        );

        private static final Map<String, String> PACKAGE_BY_INPUT = buildInputMap();
        private final String genre;
        private final String name;
        private final String packageName;

        private KnownDownloadApp(String genre, String name, String packageName) {
            this.genre = genre;
            this.name = name;
            this.packageName = packageName;
        }

        private static KnownDownloadApp app(String genre, String name, String packageName) {
            return new KnownDownloadApp(genre, name, packageName);
        }

        private static List<String> getDisplayValues() {
            List<String> values = new ArrayList<>();
            for (KnownDownloadApp app : APPS) {
                values.add(app.name + " - " + app.packageName);
            }
            return values;
        }

        private static String resolvePackage(String input) {
            return PACKAGE_BY_INPUT.get(normalize(input));
        }

        private static Map<String, String> buildInputMap() {
            Map<String, String> map = new LinkedHashMap<>();
            for (KnownDownloadApp app : APPS) {
                map.put(normalize(app.packageName), app.packageName);
                map.put(normalize(app.name), app.packageName);
                map.put(normalize(app.name + " - " + app.packageName), app.packageName);
                map.put(normalize(app.genre + " - " + app.name), app.packageName);
            }
            return map;
        }

        private static String normalize(String value) {
            return value == null ? "" : java.text.Normalizer
                    .normalize(value.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
        }
    }
}
