package deltazero.amarok.apphider;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

// Legacy DSM import removed to use native DevicePolicyManager


import java.util.Set;

import deltazero.amarok.receivers.AdminReceiver;
import deltazero.amarok.R;
import deltazero.amarok.ui.DsmActivationActivity;

public class DsmAppHider extends BaseAppHider {
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
        for (String p : pkgNames) {
            dpm.setApplicationHidden(admin, p, true);
        }
    }

    @Override
    public void unhide(Set<String> pkgNames) {
        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.w("DsmAppHider", "Device Owner not active. Failed to unhide apps.");
            Toast.makeText(context, R.string.hide_app_failed_admin_inactive, Toast.LENGTH_LONG).show();
            return;
        }
        for (String p : pkgNames) {
            dpm.setApplicationHidden(admin, p, false);
        }
    }

    @Override
    public void tryToActivate(ActivationCallbackListener activationCallbackListener) {
        if (!dpm.isDeviceOwnerApp(context.getPackageName())) {
            Log.e("DsmAppHider", "Device Owner is not set. Run: adb shell dpm set-device-owner " + context.getPackageName() + "/.receivers.AdminReceiver");
            // Instead of 'invalid_dsm_provider', we tell the user to use the computer
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
