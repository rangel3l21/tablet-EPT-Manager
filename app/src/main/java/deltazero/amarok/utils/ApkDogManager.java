package deltazero.amarok.utils;

import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ApkDogManager {

    private static final String TAG = "ApkDogManager";

    private static final Pattern DOWNLOAD_LINK_PATTERN = Pattern.compile(
            "<a[^>]+href=[\"']([^\"']*download\\?[^\"']+)[\"'][^>]*>(.*?)</a>(.{0,300})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern GO_LINK_PATTERN = Pattern.compile(
            "href=[\"'][^\"']*/go/\\?[^\"']*\\bb=([^\"'&]+)[^\"']*[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private static final Map<String, String> SLUG_BY_PACKAGE = buildSlugMap();

    public static AuroraApiManager.AppDetails getAppDetails(String packageName) throws Exception {
        DeviceArchitectureDetector.ArchitectureInfo archInfo = DeviceArchitectureDetector.detectArchitecture();
        Log.d(TAG, "🏗️ Buscando APK.DOG - Arquitetura Detectada: " + archInfo.toString());
        
        String slug = SLUG_BY_PACKAGE.get(packageName);
        if (slug == null) {
            throw new Exception("APK.DOG ainda nao esta mapeado para o pacote " + packageName + ".");
        }

        String appUrl = "https://" + slug + ".apk.dog/";
        String appHtml = fetchText(appUrl);
        String downloadPageUrl = chooseDownloadPage(appUrl, appHtml);
        String downloadHtml = fetchText(downloadPageUrl);
        String directUrl = extractDirectDownloadUrl(downloadHtml);

        AuroraApiManager.AppDetails details = new AuroraApiManager.AppDetails();
        details.packageName = packageName;
        details.versionCode = 0;
        details.downloadUrl = directUrl;
        details.isXapk = directUrl.toLowerCase(Locale.ROOT).contains(".zip")
                || directUrl.toLowerCase(Locale.ROOT).contains(".apks");

        Log.d(TAG, "✅ APK.DOG encontrado para " + packageName + ": " + directUrl);
        return details;
    }

    private static String chooseDownloadPage(String appUrl, String html) throws Exception {
        DeviceArchitectureDetector.ArchitectureInfo archInfo = DeviceArchitectureDetector.detectArchitecture();
        
        Matcher matcher = DOWNLOAD_LINK_PATTERN.matcher(html);
        Candidate best = null;
        List<Candidate> allCandidates = new ArrayList<>();
        
        while (matcher.find()) {
            String href = matcher.group(1).replace("&amp;", "&");
            String block = stripHtml((matcher.group(2) + " " + matcher.group(3)).toLowerCase(Locale.ROOT));
            Candidate candidate = new Candidate(resolveUrl(appUrl, href), scoreCompatibility(block), block);
            allCandidates.add(candidate);
            
            if (candidate.score > 0 && (best == null || candidate.score > best.score)) {
                best = candidate;
            }
        }

        if (best == null) {
            Log.w(TAG, "❌ Variantes encontradas mas nenhuma compatível: " + allCandidates.size());
            for (Candidate c : allCandidates) {
                Log.w(TAG, "   Score=" + c.score + ": " + c.description);
            }
            throw new Exception("APK.DOG nao encontrou variante compativel com "
                    + archInfo.primaryAbi + " para este dispositivo.\n"
                    + "Arquitetura detectada: " + archInfo.toString());
        }

        Log.d(TAG, "✅ Variante APK.DOG selecionada (score=" + best.score + "): " + best.description);
        return best.url;
    }

    private static int scoreCompatibility(String text) {
        DeviceArchitectureDetector.ArchitectureInfo archInfo = DeviceArchitectureDetector.detectArchitecture();
        String primaryAbi = archInfo.primaryAbi.toLowerCase(Locale.ROOT);
        
        Log.d(TAG, "🏗️ Scoring compatibilidade APK.DOG para ABI: " + primaryAbi);

        // Preferência 1: Exato com ABI primário
        if (text.contains(primaryAbi)) return 110;
        
        // Mapeamentos de nome comum para ABI técnico
        switch (primaryAbi) {
            case "arm64-v8a":
                if (text.contains("arm64")) return 105;
                if (text.contains("arm8")) return 100;
                if (text.contains("64-bit arm")) return 100;
                break;
            case "armeabi-v7a":
                if (text.contains("arm7")) return 105;
                if (text.contains("armv7")) return 100;
                if (text.contains("32-bit arm")) return 100;
                break;
            case "x86_64":
                if (text.contains("x86-64")) return 105;
                if (text.contains("x86_64")) return 100;
                if (text.contains("x64")) return 100;
                if (text.contains("64-bit x86")) return 100;
                break;
            case "x86":
                if (text.contains("x86")) return 100;
                if (text.contains("32-bit x86")) return 100;
                break;
        }

        // Preferência 2: Compatível com outro ABI suportado
        for (String supportedAbi : archInfo.allSupportedAbis) {
            String supported = supportedAbi.toLowerCase(Locale.ROOT);
            if (!supported.equals(primaryAbi) && text.contains(supported)) {
                return 75;  // Compatível mas não ideal
            }
        }

        // Preferência 3: Universal/Genérico
        if (text.contains("universal") || text.contains("all devices") || text.contains("multi-arch")) {
            return 40;  // Último recurso - pode não funcionar bem
        }

        return 0;  // Não compatível
    }

    private static String extractDirectDownloadUrl(String html) throws Exception {
        Matcher matcher = GO_LINK_PATTERN.matcher(html);
        if (!matcher.find()) {
            throw new Exception("APK.DOG nao retornou o link final de download.");
        }

        String encoded = matcher.group(1);
        byte[] decoded = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
        return new String(decoded, StandardCharsets.UTF_8).replace("&amp;", "&");
    }

    private static String fetchText(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("APK.DOG falhou: HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private static String resolveUrl(String baseUrl, String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("/")) return "https://apk.dog" + href;
        return baseUrl + href;
    }

    private static String stripHtml(String value) {
        return value.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String getPrimaryAbi() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0].toLowerCase(Locale.ROOT);
        }
        return "";
    }

    private static Map<String, String> buildSlugMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("no.mobitroll.kahoot.android", "kahoot");
        map.put("com.spotify.music", "spotify-music");
        return map;
    }

    private static final class Candidate {
        final String url;
        final int score;
        final String description;

        Candidate(String url, int score, String description) {
            this.url = url;
            this.score = score;
            this.description = description;
        }
    }
}
