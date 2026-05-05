package deltazero.amarok.apphider;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

// Legacy DSM import removed to use native DevicePolicyManager


import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import deltazero.amarok.receivers.AdminReceiver;
import deltazero.amarok.R;
import deltazero.amarok.ui.DsmActivationActivity;

public class DsmAppHider extends BaseAppHider {
    
    private static final Set<String> CRITICAL_APPS = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.google.android.googlequicksearchbox", // Google Widget cause system freeze if hidden
            "com.android.settings",
            "com.android.vending", // Google Play Store
            "com.google.android.gms" // Google Play Services
    ));
    public static ActivationCallbackListener activationCallbackListener;
    private final DevicePolicyManager dpm;
    private final ComponentName admin;

    public DsmAppHider(Context context) {
        super(context);
        dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        admin = new ComponentName(context, AdminReceiver.class);
    }

    @Override
    public void hide(Set<String> pkgNames, boolean disableOnly) {
        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.w("DsmAppHider", "Device Owner not active. Failed to hide apps.");
            Toast.makeText(context, R.string.hide_app_failed_admin_inactive, Toast.LENGTH_LONG).show();
            return;
        }
        android.content.pm.PackageManager pm = context.getPackageManager();
        
        // Find default launcher
        String defaultLauncher = null;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo resolveInfo = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                defaultLauncher = resolveInfo.activityInfo.packageName;
            }
        } catch (Exception e) {}

        java.util.List<String> skippedApps = new java.util.ArrayList<>();
        for (String p : pkgNames) {
            // SAFEGUARD: Do not hide the app itself, default launcher, or critical system apps
            if (p.equals(context.getPackageName()) || p.equals(defaultLauncher) || CRITICAL_APPS.contains(p)) {
                Log.w("DsmAppHider", "Safeguard prevented hiding critical app: " + p);
                skippedApps.add(p);
                continue;
            }

            try {
                pm.getPackageInfo(p, 0);
                dpm.setApplicationHidden(admin, p, true);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                Log.w("DsmAppHider", "App not installed, skipping hide: " + p);
            } catch (Exception e) {
                Log.e("DsmAppHider", "Error hiding app: " + p, e);
            }
        }

        if (!skippedApps.isEmpty()) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                String msg = "Os seguintes aplicativos são CRÍTICOS para o Android e foram mantidos ativos por segurança (ocultá-los congelaria o tablet):\n\n" 
                        + String.join("\n", skippedApps);
                
                if (context instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) context;
                    if (!activity.isFinishing()) {
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                                .setTitle("🛡️ Proteção do Sistema Ativada")
                                .setMessage(msg)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        return;
                    }
                }
                // Fallback caso não seja uma Activity (ex: rodando em background pelo Widget)
                Toast.makeText(context, "Proteção: " + skippedApps.size() + " app(s) crítico(s) ignorado(s).", Toast.LENGTH_LONG).show();
            });
        }
    }

    @Override
    public void unhide(Set<String> pkgNames) {
        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.w("DsmAppHider", "Device Owner not active. Failed to unhide apps.");
            Toast.makeText(context, R.string.hide_app_failed_admin_inactive, Toast.LENGTH_LONG).show();
            return;
        }
        android.content.pm.PackageManager pm = context.getPackageManager();
        for (String p : pkgNames) {
            try {
                // If it was hidden by setApplicationHidden, it will still be found by getPackageInfo
                // However, we should use MATCH_UNINSTALLED_PACKAGES flag to find hidden apps
                pm.getPackageInfo(p, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES);
                dpm.setApplicationHidden(admin, p, false);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                Log.w("DsmAppHider", "App not found, skipping unhide: " + p);
            } catch (Exception e) {
                Log.e("DsmAppHider", "Error unhiding app: " + p, e);
            }
        }
    }

    @Override
    public void tryToActivate(ActivationCallbackListener activationCallbackListener) {
        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.e("DsmAppHider", "Device Owner is not set. Run: adb shell dpm set-device-owner " + context.getPackageName() + "/.receivers.AdminReceiver");
            // R.string.hide_app_failed_admin_inactive is more accurate than 'invalid_dsm_provider'
            activationCallbackListener.onActivateCallback(this.getClass(), false, R.string.hide_app_failed_admin_inactive);
            return;
        }

        activationCallbackListener.onActivateCallback(this.getClass(), true, 0);
    }

    @Override
    public String getName() {
        return "DSM";
    }
}
