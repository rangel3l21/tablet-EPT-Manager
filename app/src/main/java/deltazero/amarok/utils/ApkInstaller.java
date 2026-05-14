package deltazero.amarok.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import deltazero.amarok.receivers.InstallReceiver;

public class ApkInstaller {
    
    /**
     * Install a single APK file (used for app updates)
     */
    public static void installSingleApk(Context context, File apkFile) throws Exception {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);

        try {
            try (InputStream in = new FileInputStream(apkFile);
                 OutputStream out = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
                byte[] buffer = new byte[65536];
                int c;
                while ((c = in.read(buffer)) != -1) {
                    out.write(buffer, 0, c);
                }
                session.fsync(out);
            }

            Intent intent = new Intent(context, InstallReceiver.class);
            intent.setAction(InstallReceiver.ACTION_INSTALL_COMPLETE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            session.commit(pendingIntent.getIntentSender());
        } finally {
            session.close();
        }
    }
    
    public static void installSplitApks(Context context, String packageName, List<File> apks) throws Exception {
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);

        try {
            for (File apkFile : apks) {
                try (InputStream in = new FileInputStream(apkFile);
                     OutputStream out = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
                    byte[] buffer = new byte[65536];
                    int c;
                    while ((c = in.read(buffer)) != -1) {
                        out.write(buffer, 0, c);
                    }
                    session.fsync(out);
                }
            }

            Intent intent = new Intent(context, InstallReceiver.class);
            intent.setAction(InstallReceiver.ACTION_INSTALL_COMPLETE);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

            session.commit(pendingIntent.getIntentSender());
        } finally {
            session.close();
        }
    }
}
