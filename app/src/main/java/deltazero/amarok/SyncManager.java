package deltazero.amarok;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import deltazero.amarok.network.SupabaseClient;

public class SyncManager {
    private static final String TAG = "SyncManager";

    public interface SyncResultCallback {
        void onComplete(boolean success, String message);
    }

    public static void pullSettings(Context context, SyncResultCallback callback) {
        String token = PrefMgr.getSupabaseToken();
        if (token == null) {
            callback.onComplete(false, "Usuário não autenticado");
            return;
        }

        SupabaseClient.fetchSettings(token, new SupabaseClient.SyncCallback() {
            @Override
            public void onSuccess(List<String> hiddenApps, List<String> blockedUrls) {
                Set<String> appSet = new HashSet<>(hiddenApps);
                PrefMgr.setHideApps(appSet);
                
                Set<String> urlSet = new HashSet<>(blockedUrls);
                PrefMgr.setBlockedUrls(urlSet);
                
                // Se o hider estiver ativo, reaplicamos a ocultação para os novos apps
                if (PrefMgr.getIsHidden()) {
                    Hider.hide(context);
                }
                
                callback.onComplete(true, "Sincronização concluída com sucesso.");
            }

            @Override
            public void onError(String error) {
                callback.onComplete(false, "Erro ao baixar configurações: " + error);
            }
        });
    }

    public static void pushSettings(SyncResultCallback callback) {
        String token = PrefMgr.getSupabaseToken();
        String userId = PrefMgr.getSupabaseUserId();
        if (token == null || userId == null) {
            callback.onComplete(false, "Usuário não autenticado");
            return;
        }

        Set<String> hideApps = PrefMgr.getHideApps();
        List<String> appList = new ArrayList<>(hideApps);
        
        Set<String> blockedUrls = PrefMgr.getBlockedUrls();
        List<String> urlList = new ArrayList<>(blockedUrls);

        SupabaseClient.pushSettings(token, userId, appList, urlList, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String t, String uid) {
                callback.onComplete(true, "Configurações salvas na nuvem.");
            }

            @Override
            public void onError(String error) {
                callback.onComplete(false, "Erro ao salvar configurações: " + error);
            }
        });
    }
}
