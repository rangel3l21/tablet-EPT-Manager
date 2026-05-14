package deltazero.amarok.utils;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import deltazero.amarok.PrefMgr;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WallpaperUtil {
    private static final String TAG = "WallpaperUtil";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final String DEFAULT_WALLPAPER_URL = "https://i.ibb.co/wh8h9bry/imgbg.jpg";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Get the current wallpaper URL (default or from settings)
     */
    public static String getCurrentWallpaperUrl(@NonNull Context context) {
        String url = PrefMgr.getWallpaperUrl();
        return url != null ? url : DEFAULT_WALLPAPER_URL;
    }

    /**
     * Apply wallpaper from URL (download if needed)
     */
    public static void applyWallpaper(@NonNull Context context, @NonNull String url, boolean showToast) {
        executor.execute(() -> {
            try {
                mainHandler.post(() -> {
                    if (showToast) {
                        Toast.makeText(context, "Baixando wallpaper...", Toast.LENGTH_SHORT).show();
                    }
                });

                // Check if it's cached
                File cachedFile = getCachedWallpaperFile(context, url);
                Bitmap bitmap = null;

                if (cachedFile.exists()) {
                    Log.d(TAG, "Usando wallpaper em cache: " + cachedFile.getAbsolutePath());
                    bitmap = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());
                } else {
                    // Download and cache
                    Log.d(TAG, "Baixando wallpaper de: " + url);
                    bitmap = downloadBitmap(url);
                    if (bitmap != null) {
                        cacheWallpaper(context, bitmap, url);
                    }
                }

                if (bitmap != null) {
                    setWallpaper(context, bitmap, url, showToast);
                } else {
                    mainHandler.post(() -> Toast.makeText(context, "Falha ao baixar wallpaper", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao aplicar wallpaper", e);
                mainHandler.post(() -> Toast.makeText(context, "Erro: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * Download wallpaper from URL
     */
    private static Bitmap downloadBitmap(@NonNull String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Pixel 6) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/120.0.0.0 Mobile Safari/537.36")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("HTTP " + response.code());
            }

            if (response.body() == null) {
                throw new Exception("Empty response");
            }

            return BitmapFactory.decodeStream(response.body().byteStream());
        }
    }

    /**
     * Set wallpaper to system
     */
    private static void setWallpaper(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String url, boolean showToast) {
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(context);
            wm.setBitmap(bitmap);

            // Save URL to preferences
            PrefMgr.setWallpaperUrl(url);

            mainHandler.post(() -> {
                if (showToast) {
                    Toast.makeText(context, "Wallpaper aplicado com sucesso!", Toast.LENGTH_SHORT).show();
                }
            });

            Log.d(TAG, "Wallpaper aplicado: " + url);
        } catch (Exception e) {
            Log.e(TAG, "Erro ao aplicar wallpaper ao sistema", e);
            mainHandler.post(() -> Toast.makeText(context, "Erro ao aplicar wallpaper: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Cache wallpaper locally
     */
    private static void cacheWallpaper(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String url) {
        try {
            File cachedFile = getCachedWallpaperFile(context, url);
            cachedFile.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            }
            Log.d(TAG, "Wallpaper em cache: " + cachedFile.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "Falha ao cachear wallpaper", e);
        }
    }

    /**
     * Get cached wallpaper file path
     */
    private static File getCachedWallpaperFile(@NonNull Context context, @NonNull String url) {
        String fileName = String.valueOf(url.hashCode()) + ".jpg";
        return new File(context.getCacheDir(), "wallpapers/" + fileName);
    }

    /**
     * Clear wallpaper cache
     */
    public static void clearCache(@NonNull Context context) {
        executor.execute(() -> {
            try {
                File wallpaperDir = new File(context.getCacheDir(), "wallpapers");
                if (wallpaperDir.exists()) {
                    File[] files = wallpaperDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            file.delete();
                        }
                    }
                    wallpaperDir.delete();
                }
                Log.d(TAG, "Cache de wallpaper limpo");
            } catch (Exception e) {
                Log.w(TAG, "Erro ao limpar cache de wallpaper", e);
            }
        });
    }

    /**
     * Apply default wallpaper
     */
    public static void applyDefaultWallpaper(@NonNull Context context) {
        applyWallpaper(context, DEFAULT_WALLPAPER_URL, true);
    }

    /**
     * Check and apply wallpaper on app start (from saved URL or default)
     */
    public static void checkAndApplyOnStart(@NonNull Context context) {
        String savedUrl = PrefMgr.getWallpaperUrl();
        String urlToUse = savedUrl != null ? savedUrl : DEFAULT_WALLPAPER_URL;
        applyWallpaper(context, urlToUse, false);
    }
}
