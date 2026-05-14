package deltazero.amarok.ui.settings;

import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.network.SupabaseClient;
import deltazero.amarok.utils.WallpaperUtil;

public class WallpaperCategory extends BaseCategory {
    public WallpaperCategory(@NonNull FragmentActivity activity, PreferenceScreen screen) {
        super(activity, screen);
        setTitle(R.string.wallpaper);

        // Set default wallpaper preference
        var setDefaultPref = new Preference(activity);
        setDefaultPref.setTitle(R.string.set_default_wallpaper);
        setDefaultPref.setIcon(R.drawable.calendar_month_24dp_1f1f1f_fill0_wght400_grad0_opsz24);
        setDefaultPref.setSummary(R.string.set_default_wallpaper_description);
        setDefaultPref.setOnPreferenceClickListener(preference -> {
            WallpaperUtil.applyDefaultWallpaper(activity);
            return true;
        });
        addPreference(setDefaultPref);

        // Custom wallpaper URL preference
        var customWallpaperPref = new EditTextPreference(activity);
        customWallpaperPref.setKey("wallpaperUrlInput");
        customWallpaperPref.setTitle(R.string.custom_wallpaper_url);
        customWallpaperPref.setIcon(R.drawable.ic_paw);
        customWallpaperPref.setSummary(R.string.custom_wallpaper_url_description);
        customWallpaperPref.setText(WallpaperUtil.getCurrentWallpaperUrl(activity));
        customWallpaperPref.setOnPreferenceChangeListener((preference, newValue) -> {
            String url = newValue.toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(activity, "URL não pode estar vazia", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                Toast.makeText(activity, "URL deve começar com http:// ou https://", Toast.LENGTH_SHORT).show();
                return false;
            }
            
            // Apply wallpaper
            WallpaperUtil.applyWallpaper(activity, url, true);
            
            // If logged in, save to cloud
            String token = PrefMgr.getSupabaseToken();
            String userId = PrefMgr.getSupabaseUserId();
            if (token != null && userId != null) {
                SupabaseClient.pushWallpaperUrl(token, userId, url, new SupabaseClient.AuthCallback() {
                    @Override
                    public void onSuccess(String t, String rt, String uid) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, "Wallpaper salvo na nuvem", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String error) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, "Erro ao salvar na nuvem: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
            }
            
            return true;
        });
        addPreference(customWallpaperPref);

        // Apply current wallpaper preference
        var applyCurrent = new Preference(activity);
        applyCurrent.setTitle(R.string.apply_current_wallpaper);
        applyCurrent.setIcon(R.drawable.update_black_24dp);
        applyCurrent.setSummary(R.string.apply_current_wallpaper_description);
        applyCurrent.setOnPreferenceClickListener(preference -> {
            String currentUrl = WallpaperUtil.getCurrentWallpaperUrl(activity);
            WallpaperUtil.applyWallpaper(activity, currentUrl, true);
            return true;
        });
        addPreference(applyCurrent);

        // Clear cache preference
        var clearCachePref = new Preference(activity);
        clearCachePref.setTitle(R.string.clear_wallpaper_cache);
        clearCachePref.setIcon(R.drawable.hide_source_black_24dp);
        clearCachePref.setSummary(R.string.clear_wallpaper_cache_description);
        clearCachePref.setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.clear_cache)
                    .setMessage(R.string.clear_wallpaper_cache_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        WallpaperUtil.clearCache(activity);
                        Toast.makeText(activity, "Cache limpo", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        });
        addPreference(clearCachePref);
    }
}
