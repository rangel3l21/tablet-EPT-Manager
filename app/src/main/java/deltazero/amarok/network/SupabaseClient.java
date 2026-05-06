package deltazero.amarok.network;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
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

    public interface AuthCallback {
        void onSuccess(String token, String userId);
        void onError(String error);
    }

    public interface SyncCallback {
        void onSuccess(List<String> hiddenApps, List<String> blockedUrls);
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
                            String userId = resJson.getJSONObject("user").getString("id");
                            callback.onSuccess(token, userId);
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
                                String userId = resJson.getJSONObject("user").getString("id");
                                callback.onSuccess(token, userId);
                            } else {
                                // Fallback se não tiver token na resposta
                                callback.onSuccess(null, resJson.getString("id"));
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
                        callback.onSuccess(token, userId);
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
}
