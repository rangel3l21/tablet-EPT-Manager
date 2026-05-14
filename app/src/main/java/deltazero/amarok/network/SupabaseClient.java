package deltazero.amarok.network;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import deltazero.amarok.utils.SupabaseConfig;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseClient {

    private static final String TAG = "SupabaseClient";
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long REFRESH_SKEW_SECONDS = 5 * 60;
    public static final String COMMAND_TYPE_POWER_OFF = "POWER_OFF";

    public interface AuthCallback {
        void onSuccess(String token, String refreshToken, String userId);
        void onError(String error);
    }

    public static void ensureFreshSession(String token, String refreshToken, String userId, AuthCallback callback) {
        if (token == null || userId == null) {
            callback.onError("Usuario nao autenticado");
            return;
        }

        if (!isTokenExpiringSoon(token)) {
            callback.onSuccess(token, refreshToken, userId);
            return;
        }

        if (refreshToken == null || refreshToken.isEmpty()) {
            callback.onError("Sessao expirada. Faca login novamente.");
            return;
        }

        refreshSession(refreshToken, callback);
    }

    public interface SyncCallback {
        void onSuccess(List<String> hiddenApps, List<String> blockedUrls);
        void onError(String error);
    }

    public static class RemotePowerCommand {
        public final String commandId;
        public final String commandType;
        public final String expiresAt;

        public RemotePowerCommand(String commandId, String commandType, String expiresAt) {
            this.commandId = commandId;
            this.commandType = commandType;
            this.expiresAt = expiresAt;
        }
    }

    public interface RemotePowerCommandCallback {
        void onSuccess(RemotePowerCommand command);
        void onNoCommand();
        void onError(String error);
    }

    public static void login(String email, String password, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String resBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject resJson = new JSONObject(resBody);
                            String token = resJson.getString("access_token");
                            String refreshToken = resJson.optString("refresh_token", null);
                            String userId = resJson.getJSONObject("user").getString("id");
                            callback.onSuccess(token, refreshToken, userId);
                        } catch (Exception e) {
                            callback.onError("Parse error: " + e.getMessage());
                        }
                    } else {
                        callback.onError(parseError(resBody));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void signup(String email, String password, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/signup")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String resBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject resJson = new JSONObject(resBody);
                            // Se email confirmation is disabled, token might be present
                            if (resJson.has("access_token")) {
                                String token = resJson.getString("access_token");
                                String refreshToken = resJson.optString("refresh_token", null);
                                String userId = resJson.getJSONObject("user").getString("id");
                                callback.onSuccess(token, refreshToken, userId);
                            } else {
                                // Fallback se não tiver token na resposta
                                callback.onSuccess(null, null, resJson.getString("id"));
                            }
                        } catch (Exception e) {
                            callback.onError("Parse error: " + e.getMessage());
                        }
                    } else {
                        callback.onError(parseError(resBody));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void pushSettings(String token, String userId, List<String> hiddenApps, List<String> blockedUrls, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            
            JSONArray jsonArray = new JSONArray();
            for (String app : hiddenApps) {
                jsonArray.put(app);
            }
            json.put("hidden_apps", jsonArray);

            JSONArray urlArray = new JSONArray();
            for (String url : blockedUrls) {
                urlArray.put(url);
            }
            json.put("blocked_urls", urlArray);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/user_settings")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates") // Upsert
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(token, null, userId);
                    } else {
                        callback.onError("Sync failed: " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void fetchSettings(String token, SyncCallback callback) {
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/user_settings?select=hidden_apps,blocked_urls")
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resBody = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray resArray = new JSONArray(resBody);
                        if (resArray.length() > 0) {
                            JSONObject userSettings = resArray.getJSONObject(0);
                            
                            List<String> hiddenApps = new ArrayList<>();
                            if (userSettings.has("hidden_apps") && !userSettings.isNull("hidden_apps")) {
                                JSONArray hiddenAppsJson = userSettings.getJSONArray("hidden_apps");
                                for (int i = 0; i < hiddenAppsJson.length(); i++) {
                                    hiddenApps.add(hiddenAppsJson.getString(i));
                                }
                            }
                            
                            List<String> blockedUrls = new ArrayList<>();
                            if (userSettings.has("blocked_urls") && !userSettings.isNull("blocked_urls")) {
                                JSONArray blockedUrlsJson = userSettings.getJSONArray("blocked_urls");
                                for (int i = 0; i < blockedUrlsJson.length(); i++) {
                                    blockedUrls.add(blockedUrlsJson.getString(i));
                                }
                            }
                            
                            callback.onSuccess(hiddenApps, blockedUrls);
                        } else {
                            callback.onSuccess(new ArrayList<>(), new ArrayList<>());
                        }
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                } else {
                    callback.onError(parseError(resBody));
                }
            }
        });
    }

    private static String parseError(String resBody) {
        try {
            JSONObject json = new JSONObject(resBody);
            if (json.has("error_description")) {
                return json.getString("error_description");
            }
            if (json.has("msg")) {
                return json.getString("msg");
            }
            if (json.has("message")) {
                return json.getString("message");
            }
            return resBody;
        } catch (Exception e) {
            return resBody;
        }
    }

    public static void createRemotePowerCommand(
            String token,
            String userId,
            String commandId,
            String createdAt,
            String expiresAt,
            SimpleCallback callback
    ) {
        try {
            JSONObject json = new JSONObject();
            json.put("command_id", commandId);
            json.put("user_id", userId);
            json.put("command_type", COMMAND_TYPE_POWER_OFF);
            json.put("created_at", createdAt);
            json.put("expires_at", expiresAt);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/remote_power_commands")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        String resBody = response.body().string();
                        callback.onError(parseError(resBody));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void fetchLatestRemotePowerCommand(String token, String userId, String nowIso, RemotePowerCommandCallback callback) {
        try {
            String encodedNow = URLEncoder.encode(nowIso, StandardCharsets.UTF_8.name());
            String url = SupabaseConfig.SUPABASE_URL
                    + "/rest/v1/remote_power_commands"
                    + "?select=command_id,command_type,expires_at"
                    + "&user_id=eq." + userId
                    + "&command_type=eq." + COMMAND_TYPE_POWER_OFF
                    + "&expires_at=gte." + encodedNow
                    + "&order=created_at.desc"
                    + "&limit=1";

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String resBody = response.body().string();
                    if (!response.isSuccessful()) {
                        callback.onError(parseError(resBody));
                        return;
                    }

                    try {
                        JSONArray resArray = new JSONArray(resBody);
                        if (resArray.length() == 0) {
                            callback.onNoCommand();
                            return;
                        }

                        JSONObject obj = resArray.getJSONObject(0);
                        callback.onSuccess(new RemotePowerCommand(
                                obj.getString("command_id"),
                                obj.getString("command_type"),
                                obj.getString("expires_at")
                        ));
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    private static boolean isTokenExpiringSoon(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return true;

            byte[] decoded = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            JSONObject payload = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            long exp = payload.optLong("exp", 0);
            if (exp <= 0) return true;

            long now = System.currentTimeMillis() / 1000L;
            return exp <= now + REFRESH_SKEW_SECONDS;
        } catch (Exception e) {
            Log.w(TAG, "Falha ao ler expiracao do JWT, renovando por seguranca.", e);
            return true;
        }
    }

    /**
     * Renova a sessão usando o refresh_token armazenado.
     * Chame isso quando receber um erro de JWT expirado.
     */
    public static void refreshSession(String refreshToken, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("refresh_token", refreshToken);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String resBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject resJson = new JSONObject(resBody);
                            String newToken = resJson.getString("access_token");
                            String newRefreshToken = resJson.optString("refresh_token", refreshToken);
                            String userId = resJson.getJSONObject("user").getString("id");
                            callback.onSuccess(newToken, newRefreshToken, userId);
                        } catch (Exception e) {
                            callback.onError("Parse error: " + e.getMessage());
                        }
                    } else {
                        callback.onError(parseError(resBody));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public interface WishlistCallback {
        void onSuccess(List<deltazero.amarok.models.WishlistApp> apps);
        void onError(String error);
    }
    
    public interface SimpleCallback {
        void onSuccess();
        void onError(String error);
    }

    public static void fetchWishlist(String token, String userId, WishlistCallback callback) {
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/wishlist?user_id=eq." + userId)
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resBody = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray resArray = new JSONArray(resBody);
                        List<deltazero.amarok.models.WishlistApp> apps = new ArrayList<>();
                        for (int i = 0; i < resArray.length(); i++) {
                            JSONObject obj = resArray.getJSONObject(i);
                            String packageName = obj.getString("package_name");
                            deltazero.amarok.models.WishlistApp app = new deltazero.amarok.models.WishlistApp(packageName);
                            if (obj.has("status") && !obj.isNull("status")) {
                                app.status = obj.getString("status");
                            }
                            apps.add(app);
                        }
                        callback.onSuccess(apps);
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                } else {
                    callback.onError(parseError(resBody));
                }
            }
        });
    }

    public static void updateWishlistStatus(String token, String userId, String packageName, String status, SimpleCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("status", status);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/wishlist?user_id=eq." + userId + "&package_name=eq." + packageName)
                    .patch(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        callback.onError("Update failed: " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void addToWishlist(String token, String userId, String packageName, SimpleCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("package_name", packageName);
            json.put("status", deltazero.amarok.models.WishlistApp.STATUS_PENDING);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/wishlist")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=ignore-duplicates")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        String resBody = response.body().string();
                        callback.onError(parseError(resBody));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public static void deleteFromWishlist(String token, String userId, String packageName, SimpleCallback callback) {
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/wishlist?user_id=eq." + userId + "&package_name=eq." + packageName)
                .delete()
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Delete failed: " + response.code());
                }
            }
        });
    }

    // Wallpaper
    public interface WallpaperCallback {
        void onSuccess(String wallpaperUrl);
        void onError(String error);
    }

    public static void fetchWallpaperUrl(String token, WallpaperCallback callback) {
        Request request = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/user_settings?select=wallpaper_url")
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resBody = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray resArray = new JSONArray(resBody);
                        if (resArray.length() > 0) {
                            JSONObject userSettings = resArray.getJSONObject(0);
                            if (userSettings.has("wallpaper_url") && !userSettings.isNull("wallpaper_url")) {
                                String url = userSettings.getString("wallpaper_url");
                                callback.onSuccess(url);
                            } else {
                                callback.onSuccess(null);
                            }
                        } else {
                            callback.onSuccess(null);
                        }
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                } else {
                    callback.onError(parseError(resBody));
                }
            }
        });
    }

    public static void pushWallpaperUrl(String token, String userId, String wallpaperUrl, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("wallpaper_url", wallpaperUrl);

            RequestBody body = RequestBody.create(json.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/rest/v1/user_settings")
                    .post(body)
                    .addHeader("apikey", SupabaseConfig.SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        callback.onSuccess(token, null, userId);
                    } else {
                        callback.onError("Push failed: " + response.code());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}
