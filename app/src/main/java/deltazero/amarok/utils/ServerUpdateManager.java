package deltazero.amarok.utils;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import deltazero.amarok.BuildConfig;
import deltazero.amarok.R;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

public class ServerUpdateManager {
    private static final String TAG = "ServerUpdateManager";
    private static final String BASE_URL = "https://raw.githubusercontent.com/rangel3l21/tablet-EPT-Manager/main/releases/";
    private static final String LATEST_JSON_URL = BASE_URL + "latest.json";
    private static final String SHA256_TXT_URL = BASE_URL + "sha256.txt";
    private static final int HASH_BUFFER_SIZE = 8192;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private static UpdateState state = UpdateState.idle();
    private static UpdateStateListener stateListener;

    public interface UpdateStateListener {
        void onStateChanged(@NonNull UpdateState state);
    }

    public static final class UpdateState {
        public enum Type {
            IDLE, CHECKING, DOWNLOADING, VERIFYING, READY, ERROR
        }

        public final Type type;
        public final int progress;
        public final String message;

        private UpdateState(@NonNull Type type, int progress, String message) {
            this.type = type;
            this.progress = progress;
            this.message = message;
        }

        public static UpdateState idle() {
            return new UpdateState(Type.IDLE, 0, null);
        }

        public static UpdateState checking() {
            return new UpdateState(Type.CHECKING, 0, null);
        }

        public static UpdateState downloading(int progress) {
            return new UpdateState(Type.DOWNLOADING, progress, null);
        }

        public static UpdateState verifying() {
            return new UpdateState(Type.VERIFYING, 100, null);
        }

        public static UpdateState ready() {
            return new UpdateState(Type.READY, 100, null);
        }

        public static UpdateState error(@NonNull String message) {
            return new UpdateState(Type.ERROR, 0, message);
        }
    }

    private record ReleaseInfo(String version, long versionCode, String appName, String releaseFile, String changelog) {
    }

    public static void setStateListener(UpdateStateListener listener) {
        stateListener = listener;
        if (listener != null) {
            listener.onStateChanged(state);
        }
    }

    @NonNull
    public static UpdateState getState() {
        return state;
    }

    /**
     * Check for updates and install the selected APK after SHA-256 validation.
     *
     * @param activity Activity used to show dialogs and launch the package installer.
     * @param silent   If true, only shows UI when an update is available or installation needs user action.
     */
    public static void checkAndNotify(@NonNull Activity activity, boolean silent) {
        checkAndInstall(activity, silent);
    }

    public static void checkAndInstall(@NonNull Activity activity) {
        checkAndInstall(activity, false);
    }

    public static void checkAndInstall(@NonNull Activity activity, boolean silent) {
        if (!silent) {
            mainHandler.post(() -> Toast.makeText(activity, R.string.checking_update, Toast.LENGTH_SHORT).show());
        }

        setState(UpdateState.checking());
        executor.execute(() -> {
            try {
                long installedVersionCode = getInstalledVersionCode(activity);
                ReleaseInfo latest = fetchLatestRelease();

                Log.d(TAG, "Latest versionCode: " + latest.versionCode + " installed versionCode: " + installedVersionCode);

                if (latest.versionCode <= installedVersionCode) {
                    setState(UpdateState.idle());
                    if (!silent) {
                        mainHandler.post(() -> Toast.makeText(activity, "Voc\u00ea j\u00e1 est\u00e1 na \u00faltima vers\u00e3o", Toast.LENGTH_SHORT).show());
                    }
                    return;
                }

                mainHandler.post(() -> showUpdateDialog(activity, latest));
            } catch (Exception e) {
                Log.e(TAG, "Failed to check for updates", e);
                setState(UpdateState.error(e.getMessage() == null ? "Falha ao verificar se h\u00e1 atualiza\u00e7\u00f5es" : e.getMessage()));
                if (!silent) {
                    mainHandler.post(() -> Toast.makeText(activity, R.string.update_check_failed, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private static void showUpdateDialog(@NonNull Activity activity, @NonNull ReleaseInfo release) {
        StringBuilder message = new StringBuilder();
        message.append(release.appName).append(" ").append(release.version).append("\n\n");
        if (release.changelog != null && !release.changelog.trim().isEmpty()) {
            message.append(release.changelog);
        }

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update, (dialog, which) -> downloadVerifyAndInstall(activity, release))
                .setNegativeButton(R.string.cancel, (dialog, which) -> setState(UpdateState.idle()))
                .setOnCancelListener(dialog -> setState(UpdateState.idle()))
                .show();
    }

    private static void downloadVerifyAndInstall(@NonNull Activity activity, @NonNull ReleaseInfo release) {
        AlertDialog progressDialog = createProgressDialog(activity);

        progressDialog.show();
        ProgressBar progressBar = progressDialog.findViewById(android.R.id.progress);
        TextView progressText = progressDialog.findViewById(android.R.id.text1);

        setStateListener(updateState -> mainHandler.post(() -> {
            if (progressBar == null || progressText == null) {
                return;
            }

            if (updateState.type == UpdateState.Type.DOWNLOADING) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(updateState.progress);
                progressText.setText("Baixando atualiza\u00e7\u00e3o... " + updateState.progress + "%");
            } else if (updateState.type == UpdateState.Type.VERIFYING) {
                progressBar.setIndeterminate(true);
                progressText.setText("Verificando APK...");
            } else if (updateState.type == UpdateState.Type.ERROR) {
                progressDialog.dismiss();
                setStateListener(null);
            } else if (updateState.type == UpdateState.Type.READY) {
                progressDialog.dismiss();
                setStateListener(null);
            }
        }));

        executor.execute(() -> {
            File apkFile = null;
            try {
                String expectedHash = fetchExpectedHash(release.releaseFile);
                apkFile = downloadApk(activity, release);

                setState(UpdateState.verifying());
                String actualHash = calculateSha256(apkFile);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    //noinspection ResultOfMethodCallIgnored
                    apkFile.delete();
                    throw new SecurityException("APK corrompido");
                }

                setState(UpdateState.ready());
                File finalApkFile = apkFile;
                mainHandler.post(() -> installApk(activity, finalApkFile));
            } catch (Exception e) {
                Log.e(TAG, "Failed to update APK", e);
                if (apkFile != null) {
                    //noinspection ResultOfMethodCallIgnored
                    apkFile.delete();
                }
                String message = e instanceof SecurityException ? "APK corrompido" : "Erro ao atualizar: " + e.getMessage();
                setState(UpdateState.error(message));
                mainHandler.post(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
            }
        });
    }

    @NonNull
    private static AlertDialog createProgressDialog(@NonNull Activity activity) {
        int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding / 2);

        TextView message = new TextView(activity);
        message.setId(android.R.id.text1);
        message.setText("Baixando atualiza\u00e7\u00e3o... 0%");

        ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setId(android.R.id.progress);
        progress.setMax(100);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = padding / 2;

        container.addView(message);
        container.addView(progress, progressParams);

        return new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update)
                .setView(container)
                .setCancelable(false)
                .create();
    }

    private static void installApk(@NonNull Activity activity, @NonNull File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "Autorize a instala\u00e7\u00e3o de apps desconhecidos e toque em Atualizar novamente.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return;
        }

        Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    @NonNull
    private static ReleaseInfo fetchLatestRelease() throws Exception {
        try (Response response = httpClient.newCall(noCacheRequest(LATEST_JSON_URL)).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("HTTP " + response.code());
            }

            JSONObject json = new JSONObject(response.body().string());
            return new ReleaseInfo(
                    json.getString("version"),
                    json.getLong("versionCode"),
                    json.optString("appName", "Amarok"),
                    json.getString("releaseFile"),
                    json.optString("changelog", "")
            );
        }
    }

    @NonNull
    private static String fetchExpectedHash(@NonNull String releaseFile) throws Exception {
        try (Response response = httpClient.newCall(noCacheRequest(SHA256_TXT_URL)).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new Exception("HTTP " + response.code());
            }

            String[] lines = response.body().string().split("\\R");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length == 2 && releaseFile.equals(parts[1].trim())) {
                    return parts[0].trim();
                }
            }
        }

        throw new Exception("Hash SHA-256 nao encontrado para " + releaseFile);
    }

    @NonNull
    private static File downloadApk(@NonNull Activity activity, @NonNull ReleaseInfo release) throws Exception {
        File externalFilesDir = activity.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            throw new Exception("Armazenamento externo indispon\u00edvel");
        }
        File apkFile = new File(externalFilesDir, "update.apk");
        String apkUrl = BASE_URL + release.releaseFile;

        try (Response response = httpClient.newCall(noCacheRequest(apkUrl)).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new Exception("HTTP " + response.code());
            }

            long contentLength = body.contentLength();
            long bytesRead = 0L;
            Buffer buffer = new Buffer();
            BufferedSource source = body.source();

            try (FileOutputStream output = new FileOutputStream(apkFile, false)) {
                long read;
                while ((read = source.read(buffer, 64 * 1024)) != -1) {
                    output.write(buffer.readByteArray());
                    bytesRead += read;
                    if (contentLength > 0) {
                        int progress = (int) Math.min(100, (bytesRead * 100) / contentLength);
                        setState(UpdateState.downloading(progress));
                    }
                }
            }
        }

        return apkFile;
    }

    @NonNull
    private static String calculateSha256(@NonNull File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[HASH_BUFFER_SIZE];

        try (FileInputStream input = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.US, "%02x", b));
        }
        return hex.toString();
    }

    @NonNull
    private static Request noCacheRequest(@NonNull String url) {
        return new Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("User-Agent", "Amarok/OTA-Updater")
                .get()
                .build();
    }

    private static long getInstalledVersionCode(@NonNull Activity activity) throws PackageManager.NameNotFoundException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0)
                    .getLongVersionCode();
        }

        //noinspection deprecation
        return activity.getPackageManager()
                .getPackageInfo(activity.getPackageName(), 0)
                .versionCode;
    }

    private static void setState(@NonNull UpdateState newState) {
        state = newState;
        UpdateStateListener listener = stateListener;
        if (listener != null) {
            listener.onStateChanged(newState);
        }
    }
}
