package deltazero.amarok.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.models.WishlistApp;
import deltazero.amarok.network.SupabaseClient;

/**
 * Gerencia a fila de instalação de APKs.
 *
 * Fluxo por app:
 *   PENDING → FETCHING_URLS → DOWNLOADING → INSTALLING → SUCCESS / ERROR
 */
public class AppInstallManager {

    private static final String TAG = "AppInstallManager";

    public interface InstallStateListener {
        void onStateChanged(WishlistApp app);
    }

    private static AppInstallManager instance;
    private final Context context;
    private final ExecutorService executor;
    private InstallStateListener listener;

    private CountDownLatch installLatch;
    private boolean currentInstallSuccess;
    private String currentInstallError;
    private final Set<String> activePackages = new HashSet<>();

    private AppInstallManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public static synchronized AppInstallManager getInstance(Context context) {
        if (instance == null) {
            instance = new AppInstallManager(context);
        }
        return instance;
    }

    public void setListener(InstallStateListener listener) {
        this.listener = listener;
    }

    public synchronized boolean isInstalling(String packageName) {
        return activePackages.contains(packageName);
    }

    /**
     * Inicia o processamento da fila de apps selecionados.
     * Pula apps que já estão em progresso ou com sucesso.
     */
    public void startQueue(List<WishlistApp> apps) {
        if (PrefMgr.isTeacherMode()) {
            new Handler(Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(context,
                            "Modo professor ativo: este aparelho nao instala apps.",
                            android.widget.Toast.LENGTH_LONG).show());
            return;
        }

        executor.submit(() -> {
            // Log da arquitetura detectada no início da fila
            DeviceArchitectureDetector.ArchitectureInfo archInfo = DeviceArchitectureDetector.detectArchitecture();
            Log.i(TAG, "════════════════════════════════════════════════════════════════");
            Log.i(TAG, "📱 INICIANDO FILA DE INSTALAÇÃO");
            Log.i(TAG, "🏗️  Arquitetura do Dispositivo: " + archInfo.toString());
            Log.i(TAG, "📦 Apps a instalar: " + apps.size());
            Log.i(TAG, "════════════════════════════════════════════════════════════════");
            
            for (WishlistApp app : apps) {
                // Pula apps já concluídos ou em andamento
                if (app.status.equals(WishlistApp.STATUS_SUCCESS) || isInstalling(app.packageName)) {
                    continue;
                }

                try {
                    synchronized (this) {
                        activePackages.add(app.packageName);
                    }

                    PrefMgr.clearWishlistError(app.packageName);

                    // 1. Resolver URLs de download
                    updateState(app, WishlistApp.STATUS_FETCHING_URLS, "");
                    Log.d(TAG, "Buscando URLs para: " + app.packageName);

                    AuroraApiManager.AppDetails details;
                    try {
                        details = AuroraApiManager.getAppDetails(app.packageName);
                    } catch (Exception apkPureError) {
                        Log.w(TAG, "APKPure falhou para " + app.packageName + ", tentando APK.DOG: " + apkPureError.getMessage());
                        details = ApkDogManager.getAppDetails(app.packageName);
                    }

                    List<String> urls = new java.util.ArrayList<>();
                    urls.add(details.downloadUrl);

                    // 2. Baixar APK(s)
                    updateState(app, WishlistApp.STATUS_DOWNLOADING, "");
                    Log.d(TAG, "Baixando " + urls.size() + " arquivo(s) para: " + app.packageName);

                    List<File> apks;
                    try {
                        apks = AppDownloader.downloadApks(context, app.packageName, urls);
                    } catch (Exception apkPureDownloadError) {
                        Log.w(TAG, "Download/extração da fonte principal falhou para " + app.packageName
                                + ", tentando APK.DOG: " + apkPureDownloadError.getMessage());
                        AuroraApiManager.AppDetails fallbackDetails = ApkDogManager.getAppDetails(app.packageName);
                        urls.clear();
                        urls.add(fallbackDetails.downloadUrl);
                        apks = AppDownloader.downloadApks(context, app.packageName, urls);
                    }

                    // 3. Instalar via PackageInstaller
                    updateState(app, WishlistApp.STATUS_INSTALLING, "");
                    Log.d(TAG, "Instalando: " + app.packageName + " (" + apks.size() + " APK(s))");

                    currentInstallSuccess = false;
                    currentInstallError = "";
                    installLatch = new CountDownLatch(1);
                    ApkInstaller.installSplitApks(context, app.packageName, apks);

                    // Aguarda o BroadcastReceiver sinalizar o resultado
                    boolean completed = installLatch.await(10, TimeUnit.MINUTES);
                    if (!completed) {
                        throw new Exception("Tempo esgotado aguardando confirmacao da instalacao.");
                    }

                    if (currentInstallSuccess) {
                        // Limpa arquivos temporários após instalação bem-sucedida
                        for (File apk : apks) apk.delete();
                        updateState(app, WishlistApp.STATUS_SUCCESS, "");
                        Log.d(TAG, "Instalação concluída: " + app.packageName);
                    } else {
                        throw new Exception(currentInstallError);
                    }

                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "Erro desconhecido";
                    Log.e(TAG, "Erro ao instalar " + app.packageName + ": " + msg);
                    PrefMgr.setWishlistError(app.packageName, msg);
                    updateState(app, WishlistApp.STATUS_ERROR, msg);
                } finally {
                    synchronized (this) {
                        activePackages.remove(app.packageName);
                    }
                }
            }
            
            Log.i(TAG, "════════════════════════════════════════════════════════════════");
            Log.i(TAG, "✅ Fila de instalação concluída");
            Log.i(TAG, "════════════════════════════════════════════════════════════════");
        });
    }

    /** Chamado pelo InstallReceiver quando o PackageInstaller termina */
    public void onInstallComplete(String packageName, boolean success, String message) {
        currentInstallSuccess = success;
        currentInstallError = message;
        if (installLatch != null) {
            installLatch.countDown();
        }
    }

    private void updateState(WishlistApp app, String status, String error) {
        app.status = status;
        app.errorMessage = error;
        if (WishlistApp.STATUS_ERROR.equals(status) && error != null && !error.isEmpty()) {
            PrefMgr.setWishlistError(app.packageName, error);
        } else if (!WishlistApp.STATUS_ERROR.equals(status)) {
            PrefMgr.clearWishlistError(app.packageName);
        }

        // Se instalado com sucesso, marca como instalado localmente neste dispositivo
        if (WishlistApp.STATUS_SUCCESS.equals(status)) {
            java.util.Set<String> installedApps = PrefMgr.getDeviceInstalledApps();
            installedApps.add(app.packageName);
            PrefMgr.setDeviceInstalledApps(installedApps);
        }

        // Atualiza o status no banco de dados (Supabase)
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();
        if (token != null && userId != null) {
            SupabaseClient.updateWishlistStatus(token, userId, app.packageName, status,
                    new SupabaseClient.SimpleCallback() {
                        @Override public void onSuccess() {}
                        @Override public void onError(String e) {
                            Log.w(TAG, "Falha ao atualizar status no Supabase: " + e);
                        }
                    });
        }

        // Notifica a UI na main thread
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onStateChanged(app));
        }
    }
}
