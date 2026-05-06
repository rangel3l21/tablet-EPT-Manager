package deltazero.amarok.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SystemAppSafeguard {

    // Apps that should NEVER be hidden because they crash the tablet, and they shouldn't even appear in the list.
    // Note: com.android.settings is NOT here because we will use "Suspend" instead of "Hide" for it.
    private static final Set<String> STRICT_CRITICAL_APPS = new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller"
    ));

    // Apps that are codependent. Hiding one hides all in the same group.
    private static final List<Set<String>> CODEPENDENT_GROUPS = Arrays.asList(
            new HashSet<>(Arrays.asList("com.android.vending", "com.google.android.gms")), // Google Play Store & Services
            new HashSet<>(Arrays.asList("com.google.android.dialer", "com.google.android.contacts")),
            new HashSet<>(Arrays.asList("com.samsung.android.dialer", "com.samsung.android.contacts"))
    );

    // Apps that should be Suspended instead of Hidden to prevent system crashes
    public static final Set<String> SUSPEND_ONLY_APPS = new HashSet<>(Collections.singletonList(
            "com.android.settings" // Settings crashes Samsung/Motorola tablets if fully hidden
    ));

    /**
     * Returns a set of strictly critical apps that should NOT appear in the App Hider UI.
     * This includes static critical apps and the dynamically detected default launcher.
     */
    public static Set<String> getStrictlyCriticalApps(Context context) {
        Set<String> criticalApps = new HashSet<>(STRICT_CRITICAL_APPS);

        // Dynamically add default launcher
        try {
            PackageManager pm = context.getPackageManager();
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (resolveInfo != null && resolveInfo.activityInfo != null) {
                criticalApps.add(resolveInfo.activityInfo.packageName);
            }
        } catch (Exception ignored) {}

        return criticalApps;
    }

    /**
     * Returns all dependencies for a given package to ensure they are toggled together.
     * If the package is not part of any group, it returns a set containing only the package itself.
     */
    public static Set<String> getDependencies(String pkg) {
        for (Set<String> group : CODEPENDENT_GROUPS) {
            if (group.contains(pkg)) {
                return new HashSet<>(group);
            }
        }
        return new HashSet<>(Collections.singletonList(pkg));
    }
}
