package deltazero.amarok;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.topjohnwu.superuser.Shell;

import java.time.Instant;
import java.util.UUID;

import deltazero.amarok.network.SupabaseClient;
import deltazero.amarok.receivers.AdminReceiver;

public final class RemotePowerManager {

    private static final long COMMAND_TTL_SECONDS = 5 * 60;

    private RemotePowerManager() {
    }

    public interface CommandCallback {
        void onSuccess();
        void onError(String error);
    }

    public static void sendPowerOffCommand(CommandCallback callback) {
        withFreshSession(new SessionCallback() {
            @Override
            public void onReady(String token, String userId) {
                Instant now = Instant.now();
                SupabaseClient.createRemotePowerCommand(
                        token,
                        userId,
                        UUID.randomUUID().toString(),
                        now.toString(),
                        now.plusSeconds(COMMAND_TTL_SECONDS).toString(),
                        new SupabaseClient.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                callback.onSuccess();
                            }

                            @Override
                            public void onError(String error) {
                                callback.onError(error);
                            }
                        }
                );
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public static void checkForPowerCommand(Context context) {
        if (PrefMgr.isTeacherMode()) {
            return;
        }

        withFreshSession(new SessionCallback() {
            @Override
            public void onReady(String token, String userId) {
                SupabaseClient.fetchLatestRemotePowerCommand(
                        token,
                        userId,
                        Instant.now().toString(),
                        new SupabaseClient.RemotePowerCommandCallback() {
                            @Override
                            public void onSuccess(SupabaseClient.RemotePowerCommand command) {
                                if (!SupabaseClient.COMMAND_TYPE_POWER_OFF.equals(command.commandType)) {
                                    return;
                                }

                                String lastCommandId = PrefMgr.getLastRemotePowerCommandId();
                                if (command.commandId.equals(lastCommandId)) {
                                    return;
                                }

                                PrefMgr.setLastRemotePowerCommandId(command.commandId);
                                powerOffOrReboot(context.getApplicationContext());
                            }

                            @Override
                            public void onNoCommand() {
                            }

                            @Override
                            public void onError(String error) {
                            }
                        }
                );
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    public static void powerOffOrReboot(Context context) {
        if (PrefMgr.isTeacherMode()) {
            return;
        }

        Shell.getShell(shell -> {
            if (shell.isRoot()) {
                Shell.cmd("reboot -p").submit();
                return;
            }

            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && dpm.isDeviceOwnerApp(context.getPackageName())) {
                ComponentName admin = new ComponentName(context, AdminReceiver.class);
                dpm.reboot(admin);
                return;
            }

            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, "Este tablet precisa de root ou modo dono do dispositivo para desligamento remoto.", Toast.LENGTH_LONG).show()
            );
        });
    }

    private interface SessionCallback {
        void onReady(String token, String userId);
        void onError(String error);
    }

    private static void withFreshSession(SessionCallback callback) {
        String token = PrefMgr.getSupabaseToken();
        String refreshToken = PrefMgr.getSupabaseRefreshToken();
        String userId = PrefMgr.getSupabaseUserId();

        SupabaseClient.ensureFreshSession(token, refreshToken, userId, new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String freshToken, String freshRefreshToken, String freshUserId) {
                PrefMgr.setSupabaseToken(freshToken);
                PrefMgr.setSupabaseRefreshToken(freshRefreshToken);
                PrefMgr.setSupabaseUserId(freshUserId);
                callback.onReady(freshToken, freshUserId);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
