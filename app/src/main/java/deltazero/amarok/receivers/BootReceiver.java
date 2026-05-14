package deltazero.amarok.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.RemotePowerService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        if (!PrefMgr.initialized) {
            PrefMgr.init(context.getApplicationContext());
        }

        if (PrefMgr.getSupabaseToken() != null) {
            RemotePowerService.startService(context.getApplicationContext());
        }
    }
}
