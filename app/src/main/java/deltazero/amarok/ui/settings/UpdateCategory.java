package deltazero.amarok.ui.settings;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.utils.ServerUpdateManager;
import rikka.material.preference.MaterialSwitchPreference;

public class UpdateCategory extends BaseCategory {
    public UpdateCategory(@NonNull FragmentActivity activity, @NonNull PreferenceScreen screen) {
        super(activity, screen);
        setTitle(R.string.update);

        String appVersionName = null;
        try {
            appVersionName = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), PackageManager.GET_ACTIVITIES).versionName;
        } catch (PackageManager.NameNotFoundException ignore) {
        }

        // Check for update preference
        var checkUpdatePref = new Preference(activity);
        checkUpdatePref.setTitle(R.string.check_update);
        checkUpdatePref.setIcon(R.drawable.update_black_24dp);
        checkUpdatePref.setSummary(activity.getString(R.string.check_update_description, appVersionName));
        checkUpdatePref.setOnPreferenceClickListener(preference -> {
            ServerUpdateManager.checkAndNotify(activity, false);
            return true;
        });
        addPreference(checkUpdatePref);

        // Auto update switch
        var autoUpdatePref = new MaterialSwitchPreference(activity);
        autoUpdatePref.setKey(PrefMgr.IS_ENABLE_AUTO_UPDATE);
        autoUpdatePref.setIcon(R.drawable.autorenew_black_24dp);
        autoUpdatePref.setTitle(R.string.check_update_on_start);
        autoUpdatePref.setSummary(R.string.check_update_on_start_description);
        autoUpdatePref.setDefaultValue(true);
        addPreference(autoUpdatePref);
    }
}