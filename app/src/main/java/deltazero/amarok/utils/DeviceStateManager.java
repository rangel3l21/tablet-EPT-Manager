package deltazero.amarok.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;

import deltazero.amarok.PrefMgr;

/**
 * Gerencia estado local de instalação de apps por dispositivo específico.
 * Cada tablet mantém seu próprio status, não compartilhado via Supabase.
 */
public class DeviceStateManager {
    private static final String TAG = "DeviceStateManager";
    private static final String DEVICE_INSTALLED_APPS = "deviceInstalledApps";

    private final Context context;
    private final PackageManager packageManager;

    public DeviceStateManager(Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = context.getPackageManager();
    }

    /**
     * Verifica se um app está realmente instalado neste dispositivo
     */
    public boolean isAppInstalledOnDevice(String packageName) {
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Sincroniza o cache local de apps instalados com o sistema.
     * Chamado periodicamente para manter lista atualizada.
     */
    public void syncInstalledAppsCache(Set<String> wishlistPackages) {
        Set<String> actuallyInstalled = new HashSet<>();
        
        for (String packageName : wishlistPackages) {
            if (isAppInstalledOnDevice(packageName)) {
                actuallyInstalled.add(packageName);
            }
        }
        
        PrefMgr.setDeviceInstalledApps(actuallyInstalled);
        Log.d(TAG, "Sincronizado: " + actuallyInstalled.size() + " apps instalados neste dispositivo");
    }

    /**
     * Obtém lista local de apps instalados neste dispositivo
     */
    public Set<String> getLocalInstalledApps() {
        return PrefMgr.getDeviceInstalledApps();
    }

    /**
     * Retorna o status apropriado para um app neste dispositivo específico
     * @param remoteStatus Status armazenado no Supabase (pode ser de outro tablet)
     * @param packageName Nome do pacote
     * @return Status local apropriado para este dispositivo
     */
    public String getDeviceSpecificStatus(String remoteStatus, String packageName) {
        // Se o status remoto é SUCCESS, mas o app não está instalado localmente,
        // retorna PENDING (foi instalado em outro tablet)
        if ("SUCCESS".equals(remoteStatus)) {
            if (!isAppInstalledOnDevice(packageName)) {
                return "PENDING";
            }
        }
        
        // Caso contrário, mantém o status remoto
        return remoteStatus;
    }
}
