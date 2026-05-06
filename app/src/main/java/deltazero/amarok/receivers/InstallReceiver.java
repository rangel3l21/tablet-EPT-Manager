package deltazero.amarok.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import deltazero.amarok.utils.AppInstallManager;

public class InstallReceiver extends BroadcastReceiver {
    public static final String ACTION_INSTALL_COMPLETE = "deltazero.amarok.ACTION_INSTALL_COMPLETE";
    private static final String TAG = "InstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_INSTALL_COMPLETE.equals(intent.getAction())) {
            int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
            
            Log.d(TAG, "Install result for " + packageName + ": status=" + status + " msg=" + message);
            
            if (status == PackageInstaller.STATUS_SUCCESS) {
                AppInstallManager.getInstance(context).onInstallComplete(packageName, true, message);
            } else {
                AppInstallManager.getInstance(context).onInstallComplete(packageName, false, message);
            }
        }
    }
}
