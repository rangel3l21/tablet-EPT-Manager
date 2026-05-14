package deltazero.amarok.utils;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Detector de arquitetura de CPU do dispositivo, similar ao CPU-Z.
 * Retorna informações detalhadas sobre a arquitetura para download de APKs corretos.
 */
public class DeviceArchitectureDetector {
    private static final String TAG = "ArchDetector";

    public static class ArchitectureInfo {
        public String primaryAbi;           // ex: "arm64-v8a"
        public String[] allSupportedAbis;   // ex: ["arm64-v8a", "armeabi-v7a"]
        public int abiRank;                 // 0=arm64-v8a (melhor), 1=armeabi-v7a, etc
        public String cpuModel;             // ex: "ARM Cortex-A76"
        public int bits;                    // 32 ou 64
        public boolean is64Bit;
        
        @Override
        public String toString() {
            return String.format(
                "ABI Primário: %s | Todos: %s | Modelo: %s | %d-bit",
                primaryAbi,
                Arrays.toString(allSupportedAbis),
                cpuModel,
                bits
            );
        }
    }

    /**
     * Detecta arquitetura do dispositivo (como CPU-Z faz)
     */
    public static ArchitectureInfo detectArchitecture() {
        ArchitectureInfo info = new ArchitectureInfo();
        
        // 1. Obtém ABIs suportados pelo Sistema
        if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
            info.allSupportedAbis = Build.SUPPORTED_ABIS;
            info.primaryAbi = Build.SUPPORTED_ABIS[0];
        } else {
            // Fallback: padrão arm64
            info.allSupportedAbis = new String[]{"arm64-v8a", "armeabi-v7a"};
            info.primaryAbi = "arm64-v8a";
        }
        
        // 2. Define rank do ABI (32-bit vs 64-bit)
        info.abiRank = getAbiRank(info.primaryAbi);
        info.bits = info.primaryAbi.contains("64") ? 64 : 32;
        info.is64Bit = info.bits == 64;
        
        // 3. Detecta modelo de CPU
        info.cpuModel = detectCpuModel();
        
        Log.d(TAG, "Arquitetura Detectada: " + info.toString());
        return info;
    }

    /**
     * Retorna ranking do ABI (prioridade para download)
     * 0 = Melhor (arm64-v8a), números maiores = menos preferível
     */
    public static int getAbiRank(String abi) {
        if (abi == null) return 999;
        
        switch (abi.toLowerCase(Locale.ROOT)) {
            case "arm64-v8a":
                return 0;  // 64-bit ARM - Preferível (tablets modernos)
            case "x86_64":
                return 1;  // 64-bit Intel
            case "armeabi-v7a":
                return 2;  // 32-bit ARM - Menos comum em tablets
            case "x86":
                return 3;  // 32-bit Intel
            case "armeabi":
                return 4;  // Muito antigo
            default:
                return 999;
        }
    }

    /**
     * Verifica se um ABI é compatível com o dispositivo
     */
    public static boolean isAbiCompatible(String abiToCheck, ArchitectureInfo deviceInfo) {
        if (abiToCheck == null || deviceInfo == null) return false;
        
        String check = abiToCheck.toLowerCase(Locale.ROOT);
        for (String supported : deviceInfo.allSupportedAbis) {
            if (check.equals(supported.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detecta modelo de CPU lendo de /proc/cpuinfo (como CPU-Z faz)
     */
    private static String detectCpuModel() {
        try {
            return readCpuInfo();
        } catch (Exception e) {
            Log.w(TAG, "Falha ao ler CPU model: " + e.getMessage());
            return "Desconhecido";
        }
    }

    private static String readCpuInfo() {
        File cpuinfo = new File("/proc/cpuinfo");
        if (!cpuinfo.exists()) {
            return getModelFromBuild();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(cpuinfo))) {
            String line;
            List<String> processors = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                // Procura por "CPU model name" (Linux x86/x86_64)
                if (line.startsWith("model name")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (!processors.contains(value)) {
                        processors.add(value);
                    }
                }
                // Em ARM, procura por "Processor" ou "CPU implementer"
                if (line.startsWith("Processor")) {
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if (!processors.contains(value)) {
                        processors.add(value);
                    }
                }
                // ARM v8+ pode ter "CPU part"
                if (line.startsWith("CPU part")) {
                    String part = line.substring(line.indexOf(":") + 1).trim();
                    String model = parseArmCpuPart(part);
                    if (!processors.contains(model) && !model.isEmpty()) {
                        processors.add(model);
                    }
                }
            }
            
            if (!processors.isEmpty()) {
                return String.join(" / ", processors);
            }
            
            return getModelFromBuild();
        } catch (Exception e) {
            return getModelFromBuild();
        }
    }

    /**
     * Converte código hexadecimal do CPU part para nome legível
     * Baseado em: https://github.com/amlweems/cpuinfo
     */
    private static String parseArmCpuPart(String part) {
        // Remove "0x" se presente
        if (part.startsWith("0x")) {
            part = part.substring(2);
        }

        // Mapeamento de CPU part para nome comum
        switch (part.toLowerCase(Locale.ROOT)) {
            // ARM Cortex series
            case "800": return "ARM Cortex-A76";
            case "801": return "ARM Cortex-A77";
            case "802": return "ARM Cortex-A76AE";
            case "803": return "ARM Cortex-A77AE";
            case "804": return "ARM Cortex-X1";
            case "805": return "ARM Cortex-A78";
            case "806": return "ARM Cortex-X2";
            case "807": return "ARM Cortex-A78AE";
            case "80a": return "ARM Cortex-A78C";
            case "80b": return "ARM Cortex-X2C";
            case "80c": return "ARM Cortex-A72";
            case "80d": return "ARM Cortex-A73";
            case "80e": return "ARM Cortex-A53";
            case "80f": return "ARM Cortex-A76";
            case "810": return "ARM Cortex-A57";
            case "811": return "ARM Cortex-A72";
            case "812": return "ARM Cortex-A73";
            case "c00": return "ARM Cortex-A73";
            case "c01": return "ARM Cortex-A55";
            case "c02": return "ARM Cortex-A76";
            case "c04": return "ARM Cortex-A76";
            case "c0f": return "ARM Cortex-A76";
            // Apple Firestorm/Icestorm (A14+)
            case "022": return "Apple Firestorm";
            case "023": return "Apple Icestorm";
            // MediaTek Cortex
            case "4e0": return "MediaTek Cortex-A72";
            case "4e1": return "MediaTek Cortex-A73";
            case "4e2": return "MediaTek Cortex-A73";
            default: 
                return "ARM Cortex (0x" + part + ")";
        }
    }

    /**
     * Fallback: obtém modelo de Build Properties
     */
    private static String getModelFromBuild() {
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String hardware = Build.HARDWARE != null ? Build.HARDWARE : "";
        
        if (!hardware.isEmpty()) {
            model += " (" + hardware + ")";
        }
        
        return model;
    }

    /**
     * Retorna string descritiva da arquitetura para logs
     */
    public static String getArchitectureDescription() {
        ArchitectureInfo info = detectArchitecture();
        return String.format(
            "Device Architecture: %s-bit %s | CPUs suportadas: %s | Modelo: %s",
            info.bits,
            info.primaryAbi,
            String.join(", ", info.allSupportedAbis),
            info.cpuModel
        );
    }
}
