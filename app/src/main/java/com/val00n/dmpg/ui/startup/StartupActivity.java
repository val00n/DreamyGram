package com.val00n.dmpg.ui.startup;

import android.app.Activity;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.LoginResponse;
import com.val00n.dmpg.services.InstanceManager;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.ui.login.LoginPhoneActivity;
import com.val00n.dmpg.ui.login.LoginQRActivity;
import com.val00n.dmpg.ui.main.MainActivity;
import com.val00n.dmpg.ui.settings.instances.InstanceSettingsActivity;

import static android.content.ContentValues.TAG;

public class StartupActivity extends Activity {

    // UI
    private ProgressBar startupProgressBar;
    private LinearLayout welcomeLayout;
    private LinearLayout warningLayout;
    private LinearLayout loginTypesLayout;
    private Button welcomeNextButton;
    private Button warningNextButton;
    private Button QRLoginButton;
    private Button phoneLoginButton;
    private Button instanceSettingsButton;
    //Others
    private MPGramService mpgService;
    private Instance instance;
    private String userToken;
    private boolean isMessagesSeen = false;
    private boolean isChecking = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup);

        mpgService = new MPGramService();
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

        init();
    }

    protected void onResume() {
        super.onResume();
        Instance currentInstance = App.getInstanceManager().getSelectedInstance();
        if (currentInstance == null) return;

        instance = currentInstance;
        mpgService = new MPGramService();
        userToken = App.getSessionManager().loadSession(instance.baseURL);
        hideEverything();
        startupProgressBar.setVisibility(View.VISIBLE);

        if (userToken == null || userToken.length() == 0) {
            startupProgressBar.setVisibility(View.GONE);
            if (!isMessagesSeen)
                welcomeLayout.setVisibility(View.VISIBLE);
            else
                loginTypesLayout.setVisibility(View.VISIBLE);
        } else {
            checkSessionStatus();
        }
    }

    private void init() {
        if (!App.isNetworkAvailable()) {
            Toast.makeText(this, "Network unavailable", Toast.LENGTH_SHORT).show();
        }

        userToken = App.getSessionManager().loadSession(instance.baseURL);
        if (userToken == null || userToken.length() == 0) {
            hideEverything();
            welcomeLayout.setVisibility(View.VISIBLE);
        }
    }

    private void checkSessionStatus() {
        if (isChecking) return;
        isChecking = true;
        mpgService.checkAuth(userToken, new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(final LoginResponse response) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isChecking = false;
                        if ("1".equals(response.getResponse())) {
                            Log.d(TAG, "checkAuth: User has valid session");
                            navigateToMain();
                        } else {
                            Log.d(TAG, "checkAuth: User has invalid/no session");
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isChecking = false;
                        Log.d(TAG, "AUTH onError: " + error.getMessage());
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
