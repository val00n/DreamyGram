package com.val00n.dmpg.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.AuthResponse;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.main.MainActivity;
import static android.content.ContentValues.TAG;

public class Login2FAActivity extends Activity {

    public Button submitButton;
    public EditText passwordEditText;

    private MPGramService mpgService;
    private String userToken;
    private SessionManager sessionManager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_2fa);

        mpgService = App.getMPGramService();
        sessionManager = App.getSessionManager();

        submitButton = (Button) findViewById(R.id.login2FASubmitButton);
        passwordEditText = (EditText) findViewById(R.id.login2FAPasswordEditText);

        userToken = getIntent().getStringExtra("USER_TOKEN");

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submit2FA(passwordEditText.getText().toString());
            }
        });
    }

    private void submit2FA(String password) {
        mpgService.loginComplete2FA(password, userToken, new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if (result.response.equals("1")) {
                    sessionManager.saveSession(userToken);
                    navigateToMain();
                } else if (result.response.equals("password_hash_invalid")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(Login2FAActivity.this, "Wrong password", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    onError(new Exception("Unexpected response: " + result.response));
                }
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "onError: " + error.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Login2FAActivity.this, "onError: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void navigateToMain() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(Login2FAActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
