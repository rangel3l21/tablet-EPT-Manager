package deltazero.amarok.services;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.ui.AppLockActivity;
import deltazero.amarok.utils.SystemAppSafeguard;

public class AppLockAccessibilityService extends AccessibilityService {

    private static final Set<String> unlockedPackages = new HashSet<>();
    private ScreenReceiver screenReceiver;

    public static void unlockPackage(String packageName) {
        unlockedPackages.add(packageName);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        screenReceiver = new ScreenReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence packageNameSequence = event.getPackageName();
        if (packageNameSequence == null) return;
        String packageName = packageNameSequence.toString();

        // Clear unlocked packages if the user switches to a different app
        // We ignore SystemUI (notifications/recent apps), Android OS dialogs, Amarok itself, and keyboards.
        if (!unlockedPackages.contains(packageName) &&
                !packageName.startsWith("deltazero.amarok") &&
                !packageName.equals("com.android.systemui") &&
                !packageName.equals("android") &&
                !packageName.toLowerCase().contains("inputmethod") &&
                !packageName.toLowerCase().contains("keyboard") &&
                !packageName.toLowerCase().contains("honeyboard") &&
                !packageName.toLowerCase().contains("gboard") &&
                !packageName.toLowerCase().contains("swiftkey")) {
            unlockedPackages.clear();
        }

        // If the Amarok app is visible to the user (not hidden), no need to lock apps
        if (Hider.getState() != Hider.State.HIDDEN) return;

        // Check if the launched app is marked as LOCKED
        if (SystemAppSafeguard.LOCKED_APPS.contains(packageName)) {
            // Check if the user marked it to be hidden in Amarok's configuration
            Set<String> hideApps = PrefMgr.getHideApps();
            if (hideApps.contains(packageName)) {
                // Check if it's already temporarily unlocked
                if (!unlockedPackages.contains(packageName)) {
                    // Lock the app
                    Intent lockIntent = new Intent(this, AppLockActivity.class);
                    lockIntent.putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, packageName);
                    lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(lockIntent);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try {
            if (screenReceiver != null) {
                unregisterReceiver(screenReceiver);
                screenReceiver = null;
            }
        } catch (Exception ignored) {}
        return super.onUnbind(intent);
    }

    private class ScreenReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                // Clear the unlocked apps when screen turns off
                unlockedPackages.clear();
            }
        }
    }
}
