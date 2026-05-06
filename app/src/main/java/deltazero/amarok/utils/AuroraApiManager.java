package deltazero.amarok.utils;

import java.util.ArrayList;
import java.util.List;

public class AuroraApiManager {

    public static class AppDetails {
        public String packageName;
        public int versionCode;
        public String downloadUrlBase;
    }

    public static String getAnonymousGsfId() throws Exception {
        // TODO: MOCK - Na integração real com a Aurora Store, isso faria o auth via protobuf
        Thread.sleep(1000); // Simulate network latency
        return "mock_gsf_id_123456789";
    }

    public static AppDetails getAppDetails(String packageName) throws Exception {
        // TODO: MOCK - Na integração real, faria a requisição para obter versionCode e URLs
        Thread.sleep(1000); // Simulate network
        AppDetails details = new AppDetails();
        details.packageName = packageName;
        details.versionCode = 10001;
        details.downloadUrlBase = "https://mock-play-store.com/download/" + packageName;
        return details;
    }

    public static List<String> getDownloadUrls(String packageName, int versionCode) throws Exception {
        // TODO: MOCK - Retorna a lista de URLs (base.apk e split_config.xx.apk)
        Thread.sleep(500); // Simulate network
        List<String> urls = new ArrayList<>();
        
        // Simulação retornando arquivos de texto do repositório para testarmos a engine do OkHttp e o PackageInstaller
        // Obviamente, como não são APKs reais, a instalação final lançará um INSTALL_PARSE_FAILED_NOT_APK, o que testará perfeitamente o tratamento de erros em vermelho na UI.
        urls.add("https://raw.githubusercontent.com/deltazefiro/Amarok-Hider/master/README.md"); // Simulando base.apk
        urls.add("https://raw.githubusercontent.com/deltazefiro/Amarok-Hider/master/LICENSE");   // Simulando split.apk
        
        return urls;
    }
}
