package deltazero.amarok;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import deltazero.amarok.network.SupabaseClient;

public class SyncManager {

    public interface SyncResultCallback {
        void onComplete(boolean success, String message);
    }

    public static void pullSettings(Context context, SyncResultCallback callback) {
        String token = PrefMgr.getSupabaseToken();
        String refreshToken = PrefMgr.getSupabaseRefreshToken();
        String userId = PrefMgr.getSupabaseUserId();

        if (token == null || userId == null) {
            callback.onComplete(false, "Usuario nao autenticado");
            return;
        }

        SupabaseClient.ensureFreshSession(token, refreshToken, userId, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String freshToken, String freshRefreshToken, String freshUserId) {
                saveSession(freshToken, freshRefreshToken, freshUserId);
                fetchSettingsWithToken(context, freshToken, callback);
            }

            @Override
            public void onError(String error) {
                callback.onComplete(false, error);
            }
        });
    }

    private static void fetchSettingsWithToken(Context context, String token, SyncResultCallback callback) {
        SupabaseClient.fetchSettings(token, new SupabaseClient.SyncCallback() {
            @Override
            public void onSuccess(List<String> hiddenApps, List<String> blockedUrls) {
                Set<String> appSet = new HashSet<>(hiddenApps);
                PrefMgr.setHideApps(appSet);

                Set<String> urlSet = new HashSet<>(blockedUrls);
                PrefMgr.setBlockedUrls(urlSet);

                if (PrefMgr.getIsHidden()) {
                    Hider.hide(context);
                }

                // After fetching main settings, also fetch wallpaper
                fetchWallpaperWithToken(context, token, callback);
            }

            @Override
            public void onError(String error) {
                callback.onComplete(false, "Erro ao baixar configuracoes: " + error);
            }
        });
    }

    private static void fetchWallpaperWithToken(Context context, String token, SyncResultCallback callback) {
        SupabaseClient.fetchWallpaperUrl(token, new SupabaseClient.WallpaperCallback() {
            @Override
            public void onSuccess(String wallpaperUrl) {
                if (wallpaperUrl != null && !wallpaperUrl.isEmpty()) {
                    PrefMgr.setWallpaperUrl(wallpaperUrl);
                    deltazero.amarok.utils.WallpaperUtil.applyWallpaper(context, wallpaperUrl, false);
                }
                callback.onComplete(true, "Sincronizacao concluida com sucesso.");
            }

            @Override
            public void onError(String error) {
                // Don't fail sync if wallpaper fails
                callback.onComplete(true, "Sincronizacao concluida com sucesso (wallpaper falhou).");
            }
        });
    }

    public static void pushSettings(SyncResultCallback callback) {
        String token = PrefMgr.getSupabaseToken();
        String refreshToken = PrefMgr.getSupabaseRefreshToken();
        String userId = PrefMgr.getSupabaseUserId();

        if (token == null || userId == null) {
            callback.onComplete(false, "Usuario nao autenticado");
            return;
        }

        SupabaseClient.ensureFreshSession(token, refreshToken, userId, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String freshToken, String freshRefreshToken, String freshUserId) {
                saveSession(freshToken, freshRefreshToken, freshUserId);
                pushSettingsWithToken(freshToken, freshUserId, callback);
            }

            @Override
            public void onError(String error) {
                callback.onComplete(false, error);
            }
        });
    }

    private static void pushSettingsWithToken(String token, String userId, SyncResultCallback callback) {
        Set<String> hideApps = PrefMgr.getHideApps();
        List<String> appList = new ArrayList<>(hideApps);

        Set<String> blockedUrls = PrefMgr.getBlockedUrls();
        List<String> urlList = new ArrayList<>(blockedUrls);

        SupabaseClient.pushSettings(token, userId, appList, urlList, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String t, String rt, String uid) {
                callback.onComplete(true, "Configuracoes salvas na nuvem.");
            }

            @Override
            public void onError(String error) {
                callback.onComplete(false, "Erro ao salvar configuracoes: " + error);
            }
        });
    }

    private static void saveSession(String token, String refreshToken, String userId) {
        PrefMgr.setSupabaseToken(token);
        PrefMgr.setSupabaseRefreshToken(refreshToken);
        PrefMgr.setSupabaseUserId(userId);
    }
}
