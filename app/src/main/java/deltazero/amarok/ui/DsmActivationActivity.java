package deltazero.amarok.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import deltazero.amarok.R;

/**
 * DsmActivationActivity
 * 
 * In the new PJT system, this screen explains how to activate 
 * the God Mode (Device Owner) using a computer, instead of 
 * relying on a legacy server app.
 */
public class DsmActivationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firewall); // Reusing a layout that has a toolbar and container

        // Cleanup the reused layout for this context
        MaterialToolbar toolbar = findViewById(R.id.firewall_toolbar);
        if (toolbar != null) {
            toolbar.setTitle("Ativar Modo Deus");
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        // Show the instruction dialog immediately
        showActivationInstructions();
    }

    private void showActivationInstructions() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Ativação Necessária")
            .setMessage("Para ativar o Modo Deus (MDM), siga estes passos:\n\n" +
                        "1. Conecte o tablet ao computador via USB.\n" +
                        "2. Remova todas as contas do Google do tablet.\n" +
                        "3. Rode o script 'setup_pjt.sh' ou o comando ADB:\n\n" +
                        "adb shell dpm set-device-owner deltazero.amarok.foss/.receivers.AdminReceiver\n\n" +
                        "O sistema PJT assumirá o controle total após o sucesso do comando.")
            .setPositiveButton("Entendido", (dialog, which) -> finish())
            .setCancelable(false)
            .show();
    }
}
