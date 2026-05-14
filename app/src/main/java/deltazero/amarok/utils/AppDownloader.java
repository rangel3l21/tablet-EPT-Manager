package deltazero.amarok.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AppDownloader {

    private static final String TAG = "AppDownloader";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    public static List<File> downloadApks(Context context, String packageName, List<String> urls) throws Exception {
        List<File> downloadedFiles = new ArrayList<>();
        File dir = new File(context.getCacheDir(), "apk_downloads_" + packageName);
        if (dir.exists()) {
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
        } else {
            dir.mkdirs();
        }

        int index = 0;
        for (String url : urls) {
            Log.d(TAG, "Baixando APK [" + index + "]: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent",
                            "Mozilla/5.0 (Linux; Android 13; Pixel 6) "
                                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                    + "Chrome/120.0.0.0 Mobile Safari/537.36")
                    .addHeader("Accept", "application/vnd.android.package-archive,*/*")
                    .addHeader("Referer", "https://apkpure.com/")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Download falhou: HTTP " + response.code() + "\nURL: " + url);
                }

                String contentType = response.header("Content-Type", "");
                String normalizedContentType = contentType == null ? "" : contentType.toLowerCase();
                if (contentType != null
                        && !contentType.isEmpty()
                        && !normalizedContentType.contains("apk")
                        && !normalizedContentType.contains("octet-stream")
                        && !normalizedContentType.contains("zip")
                        && !normalizedContentType.contains("java-archive")) {
                    Log.w(TAG, "Content-Type inesperado: " + contentType + " - tentando mesmo assim");
                }

                File outFile = new File(dir, getSafeFileName(url, index));
                long totalBytes = 0;
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        totalBytes += len;
                    }
                }

                if (totalBytes < 1024) {
                    outFile.delete();
                    throw new Exception("Arquivo baixado muito pequeno (" + totalBytes + " bytes).");
                }

                if (isInstallableApk(context, outFile)) {
                    Log.d(TAG, "APK baixado: " + outFile.getName() + " (" + (totalBytes / 1024) + " KB)");
                    downloadedFiles.add(outFile);
                } else {
                    List<File> extracted = extractNestedApks(context, outFile, dir);
                    if (extracted.isEmpty()) {
                        outFile.delete();
                        throw new Exception("O pacote baixado nao contem APK compativel com este dispositivo. ABI principal: "
                                + getPrimaryAbiConfig() + ".");
                    }
                    Log.d(TAG, "Pacote extraido: " + extracted.size() + " APK(s)");
                    downloadedFiles.addAll(extracted);
                    outFile.delete();
                }
                index++;
            }
        }

        return downloadedFiles;
    }

    private static String getSafeFileName(String url, int index) {
        String defaultName = (index == 0) ? "base.apk" : "split_" + index + ".apk";
        try {
            String path = url.split("\\?")[0];
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : "";
            name = URLDecoder.decode(name, StandardCharsets.UTF_8.name());
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!name.isEmpty()) {
                return index + "_" + name;
            }
        } catch (Exception ignored) {
        }
        return defaultName;
    }

    private static boolean isInstallableApk(Context context, File file) {
        PackageManager pm = context.getPackageManager();
        return pm.getPackageArchiveInfo(file.getAbsolutePath(), 0) != null;
    }

    private static List<File> extractNestedApks(Context context, File archive, File dir) throws Exception {
        List<File> apks = new ArrayList<>();
        
        // Detecta a arquitetura do dispositivo
        DeviceArchitectureDetector.ArchitectureInfo archInfo = DeviceArchitectureDetector.detectArchitecture();
        Log.d(TAG, "🏗️ Extraindo APKs - Arquitetura Detectada: " + archInfo.toString());
        
        int index = 0;
        boolean foundAbiSplit = false;
        boolean selectedAbiSplit = false;
        List<String> selectedAbis = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(archive)) {
            int bestDensity = chooseBestDensity(context, zipFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            List<ZipEntry> entriesToProcess = new ArrayList<>();
            
            // Primeiro passe: encontra todos os split ABIs disponíveis
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || entryName == null || !entryName.toLowerCase().endsWith(".apk")) {
                    continue;
                }
                String abiSplit = getAbiSplit(entryName);
                if (abiSplit != null) {
                    foundAbiSplit = true;
                }
                entriesToProcess.add(entry);
            }

            // Segundo passe: extrai APKs com lógica melhorada de ABI
            for (ZipEntry entry : entriesToProcess) {
                String entryName = entry.getName();

                String abiSplit = getAbiSplit(entryName);
                if (abiSplit != null) {
                    // Tenta encontrar um ABI compatível em ordem de preferência
                    boolean isCompatible = isSplitAbiCompatible(abiSplit, archInfo, selectedAbis);
                    
                    if (!isCompatible) {
                        Log.d(TAG, "⏭️  Ignorando split ABI incompatível: " + entryName 
                            + " (ABI: " + abiSplit + " não está em " + archInfo.primaryAbi + ")");
                        continue;
                    }
                    
                    selectedAbiSplit = true;
                    if (!selectedAbis.contains(abiSplit)) {
                        selectedAbis.add(abiSplit);
                    }
                    Log.d(TAG, "✅ Selecionando split ABI: " + entryName);
                }

                Integer densitySplit = getDensitySplit(entryName);
                if (densitySplit != null && bestDensity > 0 && densitySplit != bestDensity) {
                    Log.d(TAG, "⏭️  Ignorando split de densidade diferente: " + entryName);
                    continue;
                }

                File outFile = new File(dir, "extracted_" + index + "_" + new File(entryName).getName());
                long totalBytes = 0;
                try (InputStream is = zipFile.getInputStream(entry);
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        totalBytes += len;
                    }
                }

                if (totalBytes < 1024) {
                    outFile.delete();
                } else {
                    Log.d(TAG, "📦 APK extraído: " + entryName + " (" + (totalBytes / 1024) + " KB)");
                    apks.add(outFile);
                    index++;
                }
            }
        }

        if (foundAbiSplit && !selectedAbiSplit) {
            throw new Exception("❌ O pacote possui splits ABI, mas nenhum é compatível com a arquitetura deste dispositivo:\n"
                    + "   Primário: " + archInfo.primaryAbi + "\n"
                    + "   Todos Suportados: " + String.join(", ", archInfo.allSupportedAbis));
        }

        if (!selectedAbis.isEmpty()) {
            Log.d(TAG, "✅ ABIs selecionados para instalação: " + String.join(", ", selectedAbis));
        }

        Collections.sort(apks, Comparator.comparingInt(file -> {
            String name = file.getName().toLowerCase();
            if (name.endsWith("base.apk") || name.contains("extracted_0_") || !name.contains("config.")) {
                return 0;
            }
            return 1;
        }));
        return apks;
    }

    /**
     * Verifica se um split ABI é compatível com o dispositivo.
     * Tenta ser inteligente: permite seleção de múltiplos ABIs se o primário não existir.
     */
    private static boolean isSplitAbiCompatible(String splitAbi, DeviceArchitectureDetector.ArchitectureInfo archInfo, 
                                                 List<String> alreadySelected) {
        if (splitAbi == null || archInfo == null) return false;

        String normalizedSplit = splitAbi.toLowerCase().replace('_', '-');
        String normalizedPrimary = archInfo.primaryAbi.toLowerCase().replace('_', '-');

        // Se é o ABI primário, sempre compatível
        if (normalizedSplit.equals(normalizedPrimary)) {
            return true;
        }

        // Verifica se está na lista de todos os ABIs suportados
        for (String supported : archInfo.allSupportedAbis) {
            if (normalizedSplit.equals(supported.toLowerCase().replace('_', '-'))) {
                // Se já temos um ABI mais preferível, pula este
                if (!alreadySelected.isEmpty()) {
                    int currentRank = DeviceArchitectureDetector.getAbiRank(splitAbi);
                    int selectedRank = DeviceArchitectureDetector.getAbiRank(alreadySelected.get(0));
                    if (currentRank > selectedRank) {
                        return false;  // Já temos um melhor
                    }
                }
                return true;
            }
        }

        return false;
    }

    private static String getAbiSplit(String entryName) {
        String normalizedName = entryName.toLowerCase()
                .replace('-', '_')
                .replace(File.separatorChar, '/');
        if (!normalizedName.endsWith(".apk") || !normalizedName.contains("config.")) {
            return null;
        }

        String[] abiConfigs = {"armeabi_v7a", "arm64_v8a", "x86", "x86_64"};
        for (String abiConfig : abiConfigs) {
            if (normalizedName.contains("config." + abiConfig + ".apk")) {
                return abiConfig;
            }
        }
        return null;
    }

    private static Integer getDensitySplit(String entryName) {
        String normalizedName = entryName.toLowerCase().replace(File.separatorChar, '/');
        PatternDensity[] densities = {
                new PatternDensity("ldpi", DisplayMetrics.DENSITY_LOW),
                new PatternDensity("mdpi", DisplayMetrics.DENSITY_MEDIUM),
                new PatternDensity("hdpi", DisplayMetrics.DENSITY_HIGH),
                new PatternDensity("xhdpi", DisplayMetrics.DENSITY_XHIGH),
                new PatternDensity("xxhdpi", DisplayMetrics.DENSITY_XXHIGH),
                new PatternDensity("xxxhdpi", DisplayMetrics.DENSITY_XXXHIGH)
        };
        for (PatternDensity density : densities) {
            if (normalizedName.contains("config." + density.name + ".apk")) {
                return density.value;
            }
        }
        return null;
    }

    private static int chooseBestDensity(Context context, ZipFile zipFile) {
        int deviceDensity = context.getResources().getDisplayMetrics().densityDpi;
        int bestDensity = -1;
        int bestDistance = Integer.MAX_VALUE;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            Integer density = getDensitySplit(entries.nextElement().getName());
            if (density == null) continue;
            int distance = Math.abs(density - deviceDensity);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestDensity = density;
            }
        }
        return bestDensity;
    }

    private static String getPrimaryAbiConfig() {
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            return Build.SUPPORTED_ABIS[0].toLowerCase().replace('-', '_');
        }
        return "";
    }

    private static final class PatternDensity {
        final String name;
        final int value;

        PatternDensity(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
