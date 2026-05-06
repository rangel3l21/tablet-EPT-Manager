package deltazero.amarok.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AppDownloader {
    private static final OkHttpClient client = new OkHttpClient();

    public static List<File> downloadApks(Context context, String packageName, List<String> urls) throws Exception {
        List<File> downloadedFiles = new ArrayList<>();
        
        File dir = new File(context.getCacheDir(), "downloads_" + packageName);
        if (!dir.exists()) {
            dir.mkdirs();
        } else {
            // Limpa downloads anteriores deste pacote
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
        }

        int index = 0;
        for (String url : urls) {
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Download falhou: HTTP " + response.code() + " para URL " + url);
                }

                File outFile = new File(dir, "split_" + index + ".apk");
                try (InputStream is = response.body().byteStream();
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
                downloadedFiles.add(outFile);
                index++;
            }
        }

        return downloadedFiles;
    }
}
