package com.val00n.dmpg.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.login.LoginPhoneActivity;
import com.val00n.dmpg.ui.login.LoginQRActivity;
import com.val00n.dmpg.ui.main.MainActivity;
import com.val00n.dmpg.ui.settings.instances.InstanceSettingsActivity;

import static android.content.ContentValues.TAG;

public class StartupActivity extends Activity {

    private ProgressBar startupProgressBar;
    private LinearLayout welcomeLayout;
    private LinearLayout warningLayout;
    private LinearLayout loginTypesLayout;
    private Button welcomeNextButton;
    private Button warningNextButton;
    private Button QRLoginButton;
    private Button phoneLoginButton;
    private Button instanceSettingsButton;

    private MPGramService mpgService;
    private CountDownTimer continueCountdownTimer;
    private Instance instance;
    private String userToken;
    private boolean isMessagesSeen = false;
    private boolean isChecking = false;
    private volatile boolean isActivityDied = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup);

        mpgService = App.getMPGramService();
        instance = App.getInstanceManager().getSelectedInstance();

        startupProgressBar = (ProgressBar) findViewById(R.id.startupProgressBar);
        welcomeLayout = (LinearLayout) findViewById(R.id.startupWelcomeLayout);
        warningLayout = (LinearLayout) findViewById(R.id.startupWarningLayout);
        loginTypesLayout = (LinearLayout) findViewById(R.id.startupLoginTypesLayout);
        welcomeNextButton = (Button) findViewById(R.id.startupWelcomeNextButton);
        warningNextButton = (Button) findViewById(R.id.startupWarningNextButton);
        QRLoginButton = (Button) findViewById(R.id.startupQRLoginButton);
        phoneLoginButton = (Button) findViewById(R.id.startupPhoneLoginButton);
        instanceSettingsButton = (Button) findViewById(R.id.startupInstanceSettingsButton);

        welcomeNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideEverything();
                warningLayout.setVisibility(View.VISIBLE);
                continueCooldown();
            }
        });

        warningNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isMessagesSeen = true;
                hideEverything();
                loginTypesLayout.setVisibility(View.VISIBLE);
            }
        });

        QRLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToLoginQR();
            }
        });

        phoneLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToLoginPhone();
            }
        });

        instanceSettingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToInstanceSettings();
            }
        });

        if (getIntent().hasExtra("SKIP")) {
            isMessagesSeen = true;
        }

        if (!App.isNetworkAvailable()) {
            Toast.makeText(this, "Network unavailable", Toast.LENGTH_SHORT).show();
        }
    }

    protected void onResume() {
        super.onResume();
        Instance currentInstance = App.getInstanceManager().getSelectedInstance();
        if (currentInstance == null) return;

        instance = currentInstance;
        mpgService = App.getMPGramService();
        userToken = App.getSessionManager().loadSession(instance.baseURL);

        if (App.sessionDead) {
            App.sessionDead = false;
        }

        if (userToken == null || userToken.length() == 0) {
            userToken = "instance_check";
            checkSessionStatus();

            startupProgressBar.setVisibility(View.GONE);
            if (!isMessagesSeen)
                welcomeLayout.setVisibility(View.VISIBLE);
            else
                loginTypesLayout.setVisibility(View.VISIBLE);
        } else {
            checkSessionStatus();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        isActivityDied = true;
        if (continueCountdownTimer != null) {
            continueCountdownTimer.cancel();
            continueCountdownTimer = null;
        }
    }

    private void checkSessionStatus() {
        if (isChecking || isActivityDied) return;
        isChecking = true;
        mpgService.checkAuth(userToken, new MPGramService.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(final Boolean response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityDied) return;
                        isChecking = false;

                        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) {
                            return;
                        }

                        if (response) {
                            Log.d(TAG, "checkAuth: User has valid session");
                            navigateToMain();
                        } else {
                            Log.d(TAG, "checkAuth: User has invalid/no session");
                            App.getSessionManager().clearSession(instance.baseURL);
                            hideEverything();
                            if (!isMessagesSeen)
                                welcomeLayout.setVisibility(View.VISIBLE);
                            else
                                loginTypesLayout.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }

            @Override
            public void onError(final Exception error) {
                error.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "AUTH onError: " + error.getMessage());
                        if (isActivityDied) return;

                        if (error.getMessage() != null) {
                            Toast.makeText(StartupActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(StartupActivity.this, "Error: " + error.getClass().getName(), Toast.LENGTH_LONG).show();
                        }

                        isChecking = false;
                        hideEverything();

                        if (!isMessagesSeen)
                            welcomeLayout.setVisibility(View.VISIBLE);
                        else
                            loginTypesLayout.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void continueCooldown() {
        if (continueCountdownTimer != null) continueCountdownTimer.cancel();
        warningNextButton.setEnabled(false);
        continueCountdownTimer = new CountDownTimer(11000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                warningNextButton.setText("Continue (" + seconds + " s)");
            }

            @Override
            public void onFinish() {
                warningNextButton.setEnabled(true);
                warningNextButton.setText("Continue");
            }
        }.start();
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void hideEverything() {
        startupProgressBar.setVisibility(View.GONE);
        welcomeLayout.setVisibility(View.GONE);
        warningLayout.setVisibility(View.GONE);
        loginTypesLayout.setVisibility(View.GONE);
    }

    private void navigateToLoginQR() {
        Intent intent = new Intent(this, LoginQRActivity.class);
        startActivity(intent);
    }

    private void navigateToLoginPhone() {
        Intent intent = new Intent(this, LoginPhoneActivity.class);
        startActivity(intent);
    }

    private void navigateToInstanceSettings() {
        Intent intent = new Intent(this, InstanceSettingsActivity.class);
        startActivity(intent);
    }
}
