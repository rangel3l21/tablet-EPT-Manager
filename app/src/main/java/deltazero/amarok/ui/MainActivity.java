package deltazero.amarok.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hjq.permissions.XXPermissions;

import deltazero.amarok.AmarokActivity;
import deltazero.amarok.Hider;
import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.RemotePowerManager;
import deltazero.amarok.RemotePowerService;
import deltazero.amarok.apphider.NoneAppHider;
import deltazero.amarok.filehider.NoneFileHider;
import deltazero.amarok.network.FirewallVpnService;
import deltazero.amarok.ui.settings.SettingsActivity;
import deltazero.amarok.ui.settings.SwitchAppHiderActivity;
import deltazero.amarok.ui.settings.SwitchFileHiderActivity;
import deltazero.amarok.utils.HashUtil;
import deltazero.amarok.utils.PermissionUtil;
import deltazero.amarok.utils.ServerUpdateManager;
import nl.dionsegijn.konfetti.xml.KonfettiView;
import android.provider.Settings;
import android.content.ComponentName;
import android.text.TextUtils;
import deltazero.amarok.services.AppLockAccessibilityService;

public class MainActivity extends AmarokActivity {

    public final static String TAG = "Main";
    private ImageView ivStatusImg;
    private TextView tvStatusInfo, tvStatus, tvMoto;
    private MaterialButton btChangeStatus, btSetHideFiles, btSetHideApps;
    private MaterialSwitch swTeacherMode;
    private CircularProgressIndicator piProcessStatus;
    private KonfettiView konfettiView;
    private boolean updatingTeacherSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Setup activity
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Binding views
        ivStatusImg = findViewById(R.id.main_iv_status);
        tvStatus = findViewById(R.id.main_tv_status);
        tvStatusInfo = findViewById(R.id.main_tv_statusinfo);
        tvMoto = findViewById(R.id.main_tv_moto);
        btChangeStatus = findViewById(R.id.main_bt_change_status);
        btSetHideApps = findViewById(R.id.main_bt_set_hide_apps);
        btSetHideFiles = findViewById(R.id.main_bt_set_hide_files);
        swTeacherMode = findViewById(R.id.main_sw_teacher_mode);
        piProcessStatus = findViewById(R.id.main_pi_process_status);
        konfettiView = findViewById(R.id.main_konfetti_view);

        swTeacherMode.setChecked(PrefMgr.isTeacherMode());
        swTeacherMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (updatingTeacherSwitch) return;

            updatingTeacherSwitch = true;
            swTeacherMode.setChecked(!isChecked);
            updatingTeacherSwitch = false;

            showTeacherModePasswordDialog(isChecked);
        });

        // Init UI
        refreshUi(Hider.getState());

        // Setup observer
        Hider.state.observe(this, this::refreshUi);

        // Show welcome dialog
        if (PrefMgr.getShowWelcome()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.welcome_title)
                    .setMessage(R.string.welcome_msg)
                    .setPositiveButton(R.string.ok, (dialog, which)
                            -> PermissionUtil.requestStoragePermission(this))
                    .setNegativeButton(R.string.view_github_repo, (dialog, which) -> {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/deltazefiro/Amarok-Hider")));
                        PermissionUtil.requestStoragePermission(this);
                    })
                    .setOnCancelListener(dialog -> PermissionUtil.requestStoragePermission(this))
                    .show();
            PrefMgr.setShowWelcome(false);
        } else {
            PermissionUtil.requestStoragePermission(this);
        }

        // Check Hiders availability
        PrefMgr.getAppHider(this).tryToActivate((appHiderClass, succeed, msg) -> {
            if (succeed) return;
            Hider.showNoHiderDialog(this, msg);
        });

        checkAccessibilityService();

        PrefMgr.getFileHider(this).tryToActive((fileHiderClass, succeed, msg) -> {
            if (succeed) return;
            PrefMgr.setFileHiderMode(NoneFileHider.class);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.filehider_not_ava_title)
                    .setMessage(msg)
                    .setPositiveButton(R.string.switch_file_hider, (dialog, which)
                            -> startActivity(new Intent(this, SwitchFileHiderActivity.class)))
                    .setNegativeButton(getString(R.string.ok), null)
                    .show();
        });

        // Check for updates
        if (PrefMgr.getEnableAutoUpdate()) {
            ServerUpdateManager.checkAndNotify(this, true);
        }
    }

    public void changeStatus(View view) {
        if (PrefMgr.isTeacherMode()) {
            Toast.makeText(this, "Modo professor ativo: este aparelho nao sera bloqueado.", Toast.LENGTH_LONG).show();
            return;
        }

        if (Hider.getState() == Hider.State.HIDDEN) Hider.unhide(this);
        else Hider.hide(this);
    }

    private void setTeacherMode(boolean enabled) {
        PrefMgr.setTeacherMode(enabled);

        updatingTeacherSwitch = true;
        swTeacherMode.setChecked(enabled);
        updatingTeacherSwitch = false;

        if (enabled) {
            PrefMgr.setFirewallEnabled(false);
            Intent svcIntent = new Intent(this, FirewallVpnService.class);
            svcIntent.setAction(FirewallVpnService.ACTION_STOP);
            startService(svcIntent);

            if (Hider.getState() == Hider.State.HIDDEN) {
                Hider.unhide(this);
            }

            Toast.makeText(this,
                    "Modo professor ativo neste aparelho. Ele so gerencia os tablets.",
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this,
                    "Modo professor desativado. Este aparelho volta a receber as acoes.",
                    Toast.LENGTH_LONG).show();
        }

        refreshUi(Hider.getState());
    }

    private void showTeacherModePasswordDialog(boolean enabled) {
        String savedPassword = PrefMgr.getAmarokPassword();
        if (savedPassword == null) {
            Toast.makeText(this, "Faca login novamente para definir a senha admin.", Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_security, null);
        TextInputLayout tilPassword = dialogView.findViewById(R.id.security_dialog_til_password_input);
        TextInputEditText etPassword = dialogView.findViewById(R.id.security_dialog_et_password_input);
        MaterialButton btCancel = dialogView.findViewById(R.id.security_dialog_bt_cancel);
        MaterialButton btUnlock = dialogView.findViewById(R.id.security_dialog_bt_unlock);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        btCancel.setOnClickListener(v -> dialog.dismiss());
        btUnlock.setOnClickListener(v -> {
            String typedPassword = etPassword.getText() == null ? "" : etPassword.getText().toString().trim();
            String typedHash = HashUtil.calculateHash(typedPassword);
            if (!typedHash.equals(savedPassword)) {
                tilPassword.setError("Senha incorreta");
                return;
            }

            tilPassword.setError(null);
            dialog.dismiss();
            setTeacherMode(enabled);
        });

        dialog.setOnShowListener(d -> etPassword.requestFocus());
        dialog.show();
    }

    public void setHideApps(View view) {

        if (PrefMgr.getSupabaseToken() == null) {
            Toast.makeText(this, "Você precisa estar logado para configurar apps.", Toast.LENGTH_LONG).show();
            return;
        }

        if (Hider.getState() == Hider.State.HIDDEN) {
            Toast.makeText(this, R.string.setting_not_ava_when_hidden, Toast.LENGTH_SHORT).show();
            return;
        }

        if (PrefMgr.getAppHider(this) instanceof NoneAppHider) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.apphider_not_activated_title)
                    .setMessage(R.string.apphider_not_activated_msg)
                    .setPositiveButton(R.string.switch_app_hider, (dialog, which)
                            -> startActivity(new Intent(this, SwitchAppHiderActivity.class)))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
            return;
        }

        startActivity(new Intent(this, SetHideAppActivity.class));

    }

    public void showMoreSettings(View view) {

        startActivity(new Intent(this, SettingsActivity.class));

    }

    public void openFirewall(View view) {
        startActivity(new Intent(this, FirewallActivity.class));
    }

    public void openWishlist(View view) {
        startActivity(new Intent(this, WishlistActivity.class));
    }

    public void powerOffConnectedDevices(View view) {
        if (PrefMgr.getSupabaseToken() == null) {
            Toast.makeText(this, "Voce precisa estar logado para enviar comandos.", Toast.LENGTH_LONG).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Desligar tablets conectados")
                .setMessage("Os tablets logados nesta conta vao tentar desligar nos proximos 5 minutos. Depois disso o comando expira.")
                .setPositiveButton("Enviar comando", (dialog, which) -> {
                    RemotePowerManager.sendPowerOffCommand(new RemotePowerManager.CommandCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                RemotePowerService.startService(MainActivity.this);
                                Toast.makeText(MainActivity.this, "Comando enviado por 5 minutos.", Toast.LENGTH_LONG).show();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Erro: " + error, Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public void setHideFile(View view) {

        if (!XXPermissions.isGranted(this, com.hjq.permissions.Permission.MANAGE_EXTERNAL_STORAGE)) {
            Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show();
            return;
        }

        if (Hider.getState() == Hider.State.HIDDEN) {
            Toast.makeText(this, R.string.setting_not_ava_when_hidden, Toast.LENGTH_SHORT).show();
            return;
        }

        startActivity(new Intent(this, SetHideFilesActivity.class));
    }

    public void refreshUi(Hider.State state) {
        tvMoto.setText(R.string.moto);
        switch (state) {
            case HIDDEN -> {
                // Not Processing
                piProcessStatus.hide();
                btChangeStatus.setEnabled(true);
                // Hidden
                ivStatusImg.setImageResource(R.drawable.img_status_hidden);
                ivStatusImg.setImageTintList(getColorStateList(com.google.android.material.R.color.material_on_background_emphasis_high_type));
                btChangeStatus.setText(R.string.unhide);
                btChangeStatus.setIconResource(R.drawable.ic_wolf);
                btSetHideFiles.setEnabled(false);
                btSetHideApps.setEnabled(false);
                tvStatus.setText(getText(R.string.hidden_status));
                tvStatusInfo.setText(getText(R.string.hidden_moto));
            }
            case VISIBLE -> {
                // Not Processing
                piProcessStatus.hide();
                btChangeStatus.setEnabled(true);
                // Visible
                ivStatusImg.setImageResource(R.drawable.img_status_visible);
                ivStatusImg.setImageTintList(null);
                btChangeStatus.setText(R.string.hide);
                btChangeStatus.setIconResource(R.drawable.ic_paw);
                btSetHideFiles.setEnabled(true);
                btSetHideApps.setEnabled(true);
                tvStatus.setText(getText(R.string.visible_status));
                tvStatusInfo.setText(getText(R.string.visible_moto));
            }
            case PROCESSING -> {
                // Processing
                piProcessStatus.show();
                btChangeStatus.setEnabled(false);
            }
        }

        if (PrefMgr.isTeacherMode()) {
            btChangeStatus.setEnabled(false);
            tvStatus.setText("Modo Professor");
            tvStatusInfo.setText("Este aparelho gerencia os tablets, mas nao recebe bloqueios locais.");
        }
    }

    @Override
    protected void onResume() {
        refreshUi(Hider.getState());
        super.onResume();
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expectedComponentName = new ComponentName(this, AppLockAccessibilityService.class);
        String enabledServicesSetting = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            ComponentName enabledService = ComponentName.unflattenFromString(colonSplitter.next());
            if (enabledService != null && enabledService.equals(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }

    private void checkAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Acessibilidade Necessária")
                    .setMessage("Para usar o bloqueio de senha na Play Store, ative o Amarok nas configurações de Acessibilidade.")
                    .setPositiveButton("Ativar Agora", (dialog, which) -> {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }
}




