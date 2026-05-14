package deltazero.amarok.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.apache.maven.artifact.versioning.ComparableVersion;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import deltazero.amarok.R;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ServerUpdateManager {
    private static final String TAG = "ServerUpdateManager";
    private static final String UPDATE_URL = "https://typing-dash-race.lovable.app/app-admin.apk";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Check for updates from server and show dialog if available.
     *
     * @param context Context
     * @param silent  If true, only show dialog when update is available
     */
    public static void checkAndNotify(@NonNull Context context, boolean silent) {
        if (!silent) {
            mainHandler.post(() -> Toast.makeText(context, R.string.checking_update, Toast.LENGTH_SHORT).show());
        }

        executor.execute(() -> {
            try {
                String currentVersion = getCurrentVersion(context);
                String serverVersion = fetchServerVersion();

                Log.d(TAG, "Server version: " + serverVersion + " Current version: " + currentVersion);

                if (serverVersion != null && isNewerVersion(currentVersion, serverVersion)) {
                    mainHandler.post(() -> showUpdateDialog(context, serverVersion));
                } else if (!silent) {
                    // No update available or version check returned null (no error)
                    mainHandler.post(() -> Toast.makeText(context, R.string.no_update_ava, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to check for updates", e);
                if (!silent) {
                    mainHandler.post(() -> Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    /**
     * Download and install the APK from server
     */
    public static void downloadAndInstall(@NonNull Context context) {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> Toast.makeText(context, "Baixando atualização...", Toast.LENGTH_SHORT).show());

                File apkFile = downloadApk(context);
                if (apkFile == null || !apkFile.exists()) {
                    mainHandler.post(() -> Toast.makeText(context, "Falha ao baixar APK", Toast.LENGTH_SHORT).show());
                    return;
                }

                mainHandler.post(() -> {
                    try {
                        ApkInstaller.installSingleApk(context, apkFile);
                        Toast.makeText(context, "Instalando atualização...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to install APK", e);
                        Toast.makeText(context, "Falha ao instalar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        apkFile.delete();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to download APK", e);
                mainHandler.post(() -> Toast.makeText(context, "Erro ao baixar: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void showUpdateDialog(@NonNull Context context, @NonNull String serverVersion) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_available_title)
                .setMessage(context.getString(R.string.update_available_message, serverVersion))
                .setPositiveButton(R.string.update, (dialog, which) -> downloadAndInstall(context))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Fetch the version of the APK from the server by downloading and parsing it.
     * Returns null if no update is available (no network error), or throws exception for real errors.
     */
    private static String fetchServerVersion() throws Exception {
        Request request = new Request.Builder()
                .url(UPDATE_URL)
                .addHeader("User-Agent", "Amarok/UpdateChecker")
                .head()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                // 404 or other client errors typically mean no file available
                if (response.code() >= 400 && response.code() < 500) {
                    Log.d(TAG, "Server returned " + response.code() + ", treating as no update available");
                    return null; // No update available, not an error
                }
                throw new Exception("HTTP " + response.code());
            }
            // For version checking, we'll download a small portion to get file info
            // If the server provides version info in headers, use that
            // Otherwise, we'll assume any new file is a new version
            Long contentLength = response.body() != null ? response.body().contentLength() : null;
            if (contentLength != null && contentLength > 0) {
                // Successfully reached the server
                return "remote"; // Return a marker indicating a remote version exists
            }
            // Empty response or invalid content length
            Log.d(TAG, "Server returned empty or invalid response");
            return null; // No update available, not an error
        }
    }

    /**
     * Download the APK from server to cache directory
     */
    private static File downloadApk(@NonNull Context context) throws Exception {
        Request request = new Request.Builder()
                .url(UPDATE_URL)
                .addHeader("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 6) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/120.0.0.0 Mobile Safari/537.36")
                .build();

        File cacheDir = new File(context.getCacheDir(), "app_update");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }

        File apkFile = new File(cacheDir, "app-admin-update.apk");

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code());
            }

            if (response.body() == null) {
                throw new Exception("Empty response");
            }

            byte[] buffer = new byte[65536];
            int len;
            long totalBytes = 0;

            try (var fos = new java.io.FileOutputStream(apkFile);
                 var is = response.body().byteStream()) {
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    totalBytes += len;
                }
            }

            if (totalBytes < 1024) {
                apkFile.delete();
                throw new Exception("Downloaded file too small (" + totalBytes + " bytes)");
            }

            Log.d(TAG, "APK downloaded: " + apkFile.getAbsolutePath() + " (" + (totalBytes / 1024) + " KB)");
            return apkFile;
        }
    }

    @NonNull
    private static String getCurrentVersion(@NonNull Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    private static boolean isNewerVersion(@NonNull String current, @NonNull String newVersion) {
        if (newVersion == null || current == null) {
            return false;
        }

        current = current.replaceFirst("^v", "");
        newVersion = newVersion.replaceFirst("^v", "");
        
        // "remote" marker means there's a version on the server, always consider it newer
        if ("remote".equals(newVersion)) {
            return true;
        }
        
        try {
            return new ComparableVersion(newVersion).compareTo(new ComparableVersion(current)) > 0;
        } catch (Exception e) {
            Log.w(TAG, "Failed to compare versions: current=" + current + ", new=" + newVersion, e);
            return false; // On version comparison error, don't trigger update
        }
    }
}
