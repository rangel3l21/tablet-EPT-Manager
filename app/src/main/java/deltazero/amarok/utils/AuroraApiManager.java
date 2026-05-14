package deltazero.amarok.utils;

import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Obtém URLs de download de APKs via API do APKPure.
 *
 * A API retorna Protocol Buffers (não JSON), mas as URLs de download
 * ficam embutidas no binário como strings UTF-8, permitindo extração via regex.
 * Esta é a mesma estratégia usada pelo apkeep (EFForg/apkeep, licença MIT).
 *
 * Endpoint: https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name={pkg}
 * Headers: x-cv, x-sv, x-abis, x-gp (simulam cliente Android do APKPure)
 */
public class AuroraApiManager {

    private static final String TAG = "AuroraApiManager";

    // Endpoint da API APKPure (protobuf, mesma usada pelo app APKPure oficial)
    private static final String APKPURE_API =
            "https://api.pureapk.com/m/v3/cms/app_version?hl=en-US&package_name=";

    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile(
            "^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$"
    );

    // Regex extraída do apkeep (EFForg) para capturar (XAPK|APK, download_url) do protobuf binário
    // Captura: grupo 1 = tipo (XAPKJ = XAPK bundle, APKJ = APK simples)
    //          grupo 2 = URL de download do CDN do APKPure
    private static final Pattern DOWNLOAD_URL_PATTERN = Pattern.compile(
            "(X?APKJ)..(https?://[a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}" +
            "\\b[-a-zA-Z0-9()@:%_+.~#?&/=]*)",
            Pattern.DOTALL
    );

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    public static class AppDetails {
        public String packageName;
        public int versionCode;
        public String downloadUrl;
        public boolean isXapk;
    }

    public static String getAnonymousGsfId() throws Exception {
        return "apkpure_no_auth_needed";
    }

    public static AppDetails getAppDetails(String packageName) throws Exception {
        packageName = normalizePackageName(packageName);
        
        DeviceArchitectureDetector.ArchitectureInfo archInfo = DeviceArchitectureDetector.detectArchitecture();
        Log.d(TAG, "🏗️ Arquitetura do Dispositivo: " + archInfo.toString());
        Log.d(TAG, "Consultando APKPure para: " + packageName);

        Request request = new Request.Builder()
                .url(APKPURE_API + URLEncoder.encode(packageName, StandardCharsets.UTF_8.name()))
                // Headers que o app APKPure Android usa para identificar o cliente
                // (descobertos pelo apkeep da EFF — projeto open source MIT)
                .addHeader("x-cv", "3172501")
                .addHeader("x-sv", "29")
                .addHeader("x-abis", getSupportedAbisHeader())
                .addHeader("x-gp", "1")
                .addHeader("User-Agent", "APKPure/3.17.26 (Linux; U; Android 10; Pixel 4 Build/QQ3A.200605.001)")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            Log.d(TAG, "APKPure API: HTTP " + code);

            if (!response.isSuccessful()) {
                throw new Exception(
                        "APKPure não encontrou o app '" + packageName + "' (HTTP " + code + ").\n" +
                        "Verifique se o nome do pacote está correto.");
            }

            // Lê a resposta binária (protobuf) como texto Latin-1 para preservar bytes
            byte[] rawBytes = response.body().bytes();
            // Converte para String Latin-1 — garante que nenhum byte é perdido
            // As URLs ficam embutidas como UTF-8 dentro do protobuf
            String rawText = new String(rawBytes, StandardCharsets.ISO_8859_1);

            return extractDownloadInfo(packageName, rawText);
        } catch (IOException e) {
            throw new Exception("Falha de rede ao consultar APKPure: " + e.getMessage());
        }
    }

    private static String getSupportedAbisHeader() {
        if (Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0) {
            return "arm64-v8a,armeabi-v7a,armeabi,x86,x86_64";
        }
        return String.join(",", Build.SUPPORTED_ABIS);
    }

    public static String normalizePackageName(String input) throws Exception {
        if (input == null) {
            throw new Exception("Informe o pacote do app ou uma URL do APKPure.");
        }

        String value = input.trim();
        if (value.isEmpty()) {
            throw new Exception("Informe o pacote do app ou uma URL do APKPure.");
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            value = extractPackageFromApkpureUrl(value);
        }

        if (!PACKAGE_NAME_PATTERN.matcher(value).matches()) {
            throw new Exception("Use o nome do pacote do app (ex: com.spotify.music) ou a URL do app no APKPure.");
        }

        return value;
    }

    private static String extractPackageFromApkpureUrl(String url) throws Exception {
        String clean = URLDecoder.decode(url, StandardCharsets.UTF_8.name());
        if (!clean.contains("apkpure.com/")) {
            throw new Exception("URL invalida. Use uma URL do APKPure.");
        }

        String path = clean.replaceFirst("^https?://[^/]+/", "");
        String[] parts = path.split("[/?#]")[0].split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i].trim();
            if (PACKAGE_NAME_PATTERN.matcher(part).matches()) {
                return part;
            }
        }

        throw new Exception("Nao consegui encontrar o pacote na URL do APKPure.");
    }

    private static AppDetails extractDownloadInfo(String packageName, String rawText) throws Exception {
        Matcher m = DOWNLOAD_URL_PATTERN.matcher(rawText);

        if (!m.find()) {
            throw new Exception(
                    "Não foi possível encontrar URL de download para '" + packageName + "' no APKPure.\n" +
                    "O app pode não estar disponível ou o nome do pacote está incorreto.");
        }

        String fileType = m.group(1);  // "XAPKJ" ou "APKJ"
        String downloadUrl = m.group(2);

        AppDetails details = new AppDetails();
        details.packageName = packageName;
        details.versionCode = 0;
        details.downloadUrl = downloadUrl;
        details.isXapk = "XAPKJ".equals(fileType);

        Log.d(TAG, "Download encontrado: " + fileType + " → " + downloadUrl);

        if (details.isXapk) {
            Log.w(TAG, packageName + " é um XAPK (bundle). " +
                    "O arquivo baixado precisará ser extraído antes da instalação.");
        }

        return details;
    }

    /**
     * Retorna a URL de download do APK/XAPK para o package name dado.
     */
    public static List<String> getDownloadUrls(String packageName, int versionCode) throws Exception {
        AppDetails details = getAppDetails(packageName);

        List<String> urls = new ArrayList<>();
        urls.add(details.downloadUrl);
        return urls;
    }
}
