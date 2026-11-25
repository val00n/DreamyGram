package com.val00n.dmpg.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
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
    private RelativeLayout welcomeLayout;
    private RelativeLayout warningLayout;
    private RelativeLayout loginTypesLayout;
    private TextView chosenInstanceText;
    private Button welcomeNextButton;
    private Button warningNextButton;
    private Button QRLoginButton;
    private Button phoneLoginButton;
    private Button instanceSettingsButton;

    private SharedPreferences prefs;
    private MPGramService mpgService;
    private CountDownTimer continueCountdownTimer;
    private Instance instance;
    private String userToken;
    private boolean isMessagesSeen = false;
    private boolean isChecking = false;
    private volatile boolean isActivityDied = false;

    private static final String PREFS_NAME = "startup_prefs";
    private static final String KEY_MESSAGES_SEEN = "is_messages_seen";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup);

        mpgService = App.getMPGramService();
        instance = App.getInstanceManager().getSelectedInstance();

        startupProgressBar = (ProgressBar) findViewById(R.id.startupProgressBar);
        welcomeLayout = (RelativeLayout) findViewById(R.id.startupWelcomeLayout);
        warningLayout = (RelativeLayout) findViewById(R.id.startupWarningLayout);
        loginTypesLayout = (RelativeLayout) findViewById(R.id.startupLoginTypesLayout);
        chosenInstanceText = (TextView) findViewById(R.id.startupChosenInstanceText);
        welcomeNextButton = (Button) findViewById(R.id.startupWelcomeNextButton);
        warningNextButton = (Button) findViewById(R.id.startupWarningNextButton);
        QRLoginButton = (Button) findViewById(R.id.startupQRLoginButton);
        phoneLoginButton = (Button) findViewById(R.id.startupPhoneLoginButton);
        instanceSettingsButton = (Button) findViewById(R.id.startupInstanceSettingsButton);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isMessagesSeen = prefs.getBoolean(KEY_MESSAGES_SEEN, false);

        if (getIntent().hasExtra("SKIP")) {
            isMessagesSeen = true;
        }

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

                prefs.edit().putBoolean(KEY_MESSAGES_SEEN, true).commit();

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

        if (!App.isNetworkAvailable()) {
            Toast.makeText(this, R.string.startup_network_unavailable, Toast.LENGTH_SHORT).show();
        }

        updateChosenInstanceText();
    }

    protected void onResume() {
        super.onResume();
        Instance currentInstance = App.getInstanceManager().getSelectedInstance();
        if (currentInstance == null) return;

        instance = currentInstance;
        mpgService = App.getMPGramService();
        mpgService.refreshInstance();
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

        updateChosenInstanceText();
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

        if (instance == null) return;

        if (!App.isNetworkAvailable()) {
            String savedToken = App.getSessionManager().loadSession(instance.baseURL);

            if (savedToken != null && savedToken.length() > 0) {
                navigateToMain(false);
            } else {
                Toast.makeText(this, R.string.startup_no_network_or_known_session, Toast.LENGTH_LONG).show();
                showAuthOrWelcomeScreen();
            }

            return;
        }

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
                            navigateToMain(false);
                        } else {
                            Log.d(TAG, "checkAuth: User has invalid/no session");

                            App.getSessionManager().clearSession(instance.baseURL);
                            App.getCacheManager().wipeCache(null);

                            showAuthOrWelcomeScreen();
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
                        if (!instance.equals(App.getInstanceManager().getSelectedInstance())) return;

                        isChecking = false;

                        String savedToken = App.getSessionManager().loadSession(instance.baseURL);

                        if (!App.isNetworkAvailable() && savedToken != null && savedToken.length() > 0) {
                            navigateToMain(false);
                            return;
                        }

                        if (App.isSessionDead(error.getMessage(), StartupActivity.this)) {
                            return;
                        }

                        if (savedToken != null && savedToken.length() > 0) {
                            if (App.getCacheManager().hasCachedDialogs()) {
                                Toast.makeText(StartupActivity.this, R.string.startup_server_unavailable_showing_cached, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(StartupActivity.this, R.string.startup_server_unavailable, Toast.LENGTH_LONG).show();
                            }
                            navigateToMain(true);
                            return;
                        }

                        if (error.getMessage() != null) {
                            Toast.makeText(StartupActivity.this, getString(R.string.startup_error_with_message, error.getMessage()), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(StartupActivity.this, getString(R.string.startup_error_class, error.getClass().getName()), Toast.LENGTH_LONG).show();
                        }

                        showAuthOrWelcomeScreen();
                    }
                });
            }
        });
    }

    private void showAuthOrWelcomeScreen() {
        if (warningLayout.getVisibility() == View.VISIBLE || loginTypesLayout.getVisibility() == View.VISIBLE) {
            return;
        }
        hideEverything();
        if (!isMessagesSeen) {
            welcomeLayout.setVisibility(View.VISIBLE);
        } else {
            loginTypesLayout.setVisibility(View.VISIBLE);
        }
    }

    private void continueCooldown() {
        if (continueCountdownTimer != null) continueCountdownTimer.cancel();
        warningNextButton.setEnabled(false);
        continueCountdownTimer = new CountDownTimer(11000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                warningNextButton.setText(getString(R.string.startup_continue_countdown, seconds));
            }

            @Override
            public void onFinish() {
                warningNextButton.setEnabled(true);
                warningNextButton.setText(R.string.startup_continue);
            }
        }.start();
    }

    private void navigateToMain(boolean serverUnavailable) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("SERVER_UNAVAILABLE", serverUnavailable);
        startActivity(intent);
        finish();
    }

    private void hideEverything() {
        startupProgressBar.setVisibility(View.GONE);
        welcomeLayout.setVisibility(View.GONE);
        warningLayout.setVisibility(View.GONE);
        loginTypesLayout.setVisibility(View.GONE);
    }

    private void updateChosenInstanceText() {
        Instance selected = App.getInstanceManager().getSelectedInstance();

        if (selected != null) {
            chosenInstanceText.setText(getString(R.string.startup_chosen_instance, selected.baseURL));
        } else {
            chosenInstanceText.setText(R.string.startup_no_instance_selected);
        }
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
