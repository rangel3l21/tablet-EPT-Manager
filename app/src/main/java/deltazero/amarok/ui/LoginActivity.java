package deltazero.amarok.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import deltazero.amarok.PrefMgr;
import deltazero.amarok.R;
import deltazero.amarok.SyncManager;
import deltazero.amarok.network.SupabaseClient;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnSignup;
    private ProgressBar progressBar;
    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mainHandler = new Handler(Looper.getMainLooper());

        MaterialToolbar toolbar = findViewById(R.id.login_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnSignup = findViewById(R.id.btn_signup);
        progressBar = findViewById(R.id.login_progress);

        btnLogin.setOnClickListener(v -> performAuth(true));
        btnSignup.setOnClickListener(v -> performAuth(false));
    }

    private void performAuth(boolean isLogin) {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        SupabaseClient.AuthCallback callback = new SupabaseClient.AuthCallback() {
            @Override
            public void onSuccess(String token, String userId) {
                if (token != null) {
                    PrefMgr.setSupabaseToken(token);
                    PrefMgr.setSupabaseUserId(userId);
                    PrefMgr.setSupabaseEmail(email);

                    // Sync settings immediately after login
                    SyncManager.pullSettings(LoginActivity.this, new SyncManager.SyncResultCallback() {
                        @Override
                        public void onComplete(boolean success, String message) {
                            mainHandler.post(() -> {
                                setLoading(false);
                                Toast.makeText(LoginActivity.this, "Login e sincronização concluídos!", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    });
                } else {
                    mainHandler.post(() -> {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this, "Conta criada! Por favor verifique seu email (se aplicável).", Toast.LENGTH_LONG).show();
                    });
                }
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, "Erro: " + error, Toast.LENGTH_LONG).show();
                });
            }
        };

        if (isLogin) {
            SupabaseClient.login(email, password, callback);
        } else {
            SupabaseClient.signup(email, password, callback);
        }
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!isLoading);
        btnSignup.setEnabled(!isLoading);
        etEmail.setEnabled(!isLoading);
        etPassword.setEnabled(!isLoading);
    }
}
