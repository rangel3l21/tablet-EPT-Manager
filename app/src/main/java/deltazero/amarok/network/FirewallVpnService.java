package deltazero.amarok.network;

import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.receivers.AdminReceiver;

/**
 * FirewallVpnService — uses ONLY Device Owner Private DNS API.
 * No VPN tunnel is needed. The Device Owner API forces ALL apps
 * (including Chrome with DoH) to use the specified DoT server,
 * which is enforced at the system level and cannot be bypassed by apps.
 *
 * Cloudflare Family (family.cloudflare-dns.com / 1.1.1.3) blocks:
 *   - Pornography
 *   - Malware / Phishing
 *   - Safe Search enforcement on Google/Bing/YouTube
 */
public class FirewallVpnService extends Service {

    private static final String TAG = "FirewallVpnService";
    public static final String ACTION_START = "deltazero.amarok.network.START_FIREWALL";
    public static final String ACTION_STOP  = "deltazero.amarok.network.STOP_FIREWALL";

    // Popular DoT hostname for Cloudflare Family (blocks adult content)
    private static final String DNS_FAMILY = "family.cloudflare-dns.com";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_START.equals(intent.getAction())) {
            applyFirewallDns(DNS_FAMILY);
            Log.i(TAG, "Firewall ON → Private DNS = " + DNS_FAMILY);
        } else if (ACTION_STOP.equals(intent.getAction())) {
            applyFirewallDns(null);
            Log.i(TAG, "Firewall OFF → Private DNS = automatic");
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    /**
     * Sets or clears the system Private DNS using the Device Owner API.
     *
     * @param hostname DoT hostname to use, or null to revert to automatic.
     */
    private void applyFirewallDns(String hostname) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "Private DNS via Device Owner requires Android 9+");
            return;
        }
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);

            if (!dpm.isDeviceOwnerApp(getPackageName())) {
                Log.w(TAG, "PJT is NOT Device Owner. Run: adb shell dpm set-device-owner "
                        + getPackageName() + "/.receivers.AdminReceiver");
                return;
            }

            if (hostname != null) {
                dpm.setGlobalSetting(admin, "private_dns_mode", "hostname");
                dpm.setGlobalSetting(admin, "private_dns_specifier", hostname);
            } else {
                dpm.setGlobalSetting(admin, "private_dns_mode", "opportunistic");
                dpm.setGlobalSetting(admin, "private_dns_specifier", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "applyFirewallDns error", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
