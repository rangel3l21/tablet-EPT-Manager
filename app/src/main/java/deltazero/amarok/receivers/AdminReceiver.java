package deltazero.amarok.receivers;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;

public class AdminReceiver extends DeviceAdminReceiver {
    private static final String POLICY_PREFS = "amarok_policy";
    private static final String KEY_CAMERA_DISABLED = "camera_disabled";
    private static final String EXTRA_KEEP_CAMERA_ENABLED = "keep_camera_enabled";
    private static final String EXTRA_DISABLE_CAMERA = "disable_camera";

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        applyCameraPolicy(context, intent);
    }

    @Override
    public void onProfileProvisioningComplete(@NonNull Context context, @NonNull Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
        applyCameraPolicy(context, intent);
    }

    private void applyCameraPolicy(@NonNull Context context, @NonNull Intent intent) {
        boolean disabled = getPersistedCameraPolicy(context);
        Bundle extras = intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);

        if (extras != null) {
            boolean keepCameraEnabled = extras.getBoolean(EXTRA_KEEP_CAMERA_ENABLED, false);
            boolean disableCamera = extras.getBoolean(EXTRA_DISABLE_CAMERA, false);
            disabled = disableCamera && !keepCameraEnabled;
            persistCameraPolicy(context, disabled);
        }

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            dpm.setCameraDisabled(admin, disabled);
        }
    }

    private boolean getPersistedCameraPolicy(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(POLICY_PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_CAMERA_DISABLED, false);
    }

    private void persistCameraPolicy(@NonNull Context context, boolean disabled) {
        SharedPreferences prefs = context.getSharedPreferences(POLICY_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_CAMERA_DISABLED, disabled).apply();
    }
}
