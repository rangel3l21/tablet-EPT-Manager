package deltazero.amarok.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.models.WishlistApp;
import deltazero.amarok.network.SupabaseClient;

public class AppInstallManager {

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

    public void startQueue(List<WishlistApp> apps) {
        executor.submit(() -> {
            for (WishlistApp app : apps) {
                if (app.status.equals(WishlistApp.STATUS_SUCCESS) || app.status.equals(WishlistApp.STATUS_INSTALLING) || app.status.equals(WishlistApp.STATUS_DOWNLOADING)) {
                    continue; // Pula se já estiver em sucesso ou em andamento
                }

                try {
                    updateState(app, WishlistApp.STATUS_FETCHING_URLS, "");

                    String gsfId = AuroraApiManager.getAnonymousGsfId();
                    AuroraApiManager.AppDetails details = AuroraApiManager.getAppDetails(app.packageName);
                    List<String> urls = AuroraApiManager.getDownloadUrls(app.packageName, details.versionCode);

                    updateState(app, WishlistApp.STATUS_DOWNLOADING, "");
                    List<File> apks = AppDownloader.downloadApks(context, app.packageName, urls);

                    updateState(app, WishlistApp.STATUS_INSTALLING, "");
                    
                    installLatch = new CountDownLatch(1);
                    ApkInstaller.installSplitApks(context, app.packageName, apks);
                    
                    // Bloqueia a worker thread até o BroadcastReceiver disparar o countDown
                    installLatch.await();
                    
                    if (currentInstallSuccess) {
                        updateState(app, WishlistApp.STATUS_SUCCESS, "");
                    } else {
                        throw new Exception(currentInstallError);
                    }
                    
                } catch (Exception e) {
                    updateState(app, WishlistApp.STATUS_ERROR, e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
            }
        });
    }

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
        
        // Atualizar DB (dispara na thread do OkHttp)
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();
        if (token != null && userId != null) {
            SupabaseClient.updateWishlistStatus(token, userId, app.packageName, status, new SupabaseClient.SimpleCallback() {
                @Override public void onSuccess() {}
                @Override public void onError(String e) {}
            });
        }
        
        // Atualizar UI na Main Thread
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() -> listener.onStateChanged(app));
        }
    }
}
