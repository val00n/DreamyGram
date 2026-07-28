package com.val00n.dmpg.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.LoginResponse;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.main.MainActivity;

import static android.content.ContentValues.TAG;

public class LoginQRActivity extends Activity {

    // UI
    private ProgressBar progressBar;
    private LinearLayout captchaLayout;
    private LinearLayout qrLayout;
    private ImageView captchaImageView;
    private ImageView qrImageView;
    private EditText captchaSolutionEditText;
    private Button captchaSubmitButton;
    // Others
    private MPGramService mpgService;
    private SessionManager sessionManager;
    private Handler qrPollingHandler;
    private Runnable qrPollTask;
    private String captchaId;
    private String qrLink;
    private String userToken;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_qr);

        mpgService = new MPGramService();
        qrPollingHandler = new Handler();
        sessionManager = App.getSessionManager();

        qrPollTask = new Runnable() {
            @Override
            public void run() {
                mpgService.loginQrPoll(userToken, new MPGramService.ApiCallback<LoginResponse>() {
                    @Override
                    public void onSuccess(LoginResponse result) {
                        if (result.getResponse().equals("1")) {
                            sessionManager.saveSession(userToken);
                            navigateToMain(userToken);
                        } else if (result.getResponse().equals("password")) {
                            navigateTo2FA(userToken);
                        } else if (result.getText() != null && result.getText().length() > 0) {
                            if (result.getText().equals(qrLink)) {
                                qrPollingHandler.postDelayed(qrPollTask, 3000);
                            } else {
                                qrLink = result.getText();
                                getQrImage();
                                qrPollingHandler.postDelayed(qrPollTask, 3000);
                            }
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        error(error);
                    }
                });
            }
        };

        progressBar = (ProgressBar) findViewById(R.id.loginQRProgressBar);
        captchaLayout = (LinearLayout) findViewById(R.id.loginQRCaptchaLayout);
        qrLayout = (LinearLayout) findViewById(R.id.loginQRLayout);
        captchaImageView = (ImageView) findViewById(R.id.loginQRCaptchaImageView);
        qrImageView = (ImageView) findViewById(R.id.loginQRImageView);
        captchaSolutionEditText = (EditText) findViewById(R.id.loginQRCaptchaEditText);
        captchaSubmitButton = (Button) findViewById(R.id.loginQRCaptchaSubmitButton);

        captchaSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitCaptcha(captchaSolutionEditText.getText().toString());

                progressBar.setVisibility(View.VISIBLE);
                captchaLayout.setVisibility(View.GONE);
            }
        });

        fetchCaptcha();
    }

    protected void onDestroy() {
        super.onDestroy();
        qrPollingHandler.removeCallbacks(qrPollTask);
    }

    private void fetchCaptcha() {
        mpgService.loginStartQRLogin(new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(final LoginResponse response) {
                if ("need_captcha".equals(response.getResponse())) {
                    captchaId = response.getCaptchaID();

                    mpgService.loginGetCaptchaImage(captchaId, new MPGramService.ApiCallback<byte[]>() {
                        @Override
                        public void onSuccess(byte[] result) {
                            showCaptcha(result);
                        }

                        @Override
                        public void onError(Exception error) {
                            error(error);
                        }
                    });
                } else {
                    error(new Exception("unexpected response: " + response.getResponse()));
                }
            }

            @Override
            public void onError(final Exception error) {
                error(error);
            }
        });
    }

    private void submitCaptcha(final String captchaSolution) {
        mpgService.loginGetQRData(captchaSolution, captchaId, new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                if ("qr".equals(result.getResponse())) {
                    qrLink = result.getText();
                    userToken = result.getUserToken();

                    getQrImage();
                    qrPollingHandler.postDelayed(qrPollTask, 5000);
                } else {
                    error(new Exception("unexpected response: " + result.getResponse()));
                }
            }

            @Override
            public void onError(Exception error) {
                error(error);
            }
        });
    }

    private void getQrImage() {
        mpgService.loginGetQRImage(qrLink, new MPGramService.ApiCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] result) {
                if (result != null) {
                    showQRCode(result);
                } else {
                    Log.d(TAG, "onSuccess: ERR: qr img bytes is null");
                    Toast.makeText(LoginQRActivity.this, "onError: null image", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onError(Exception error) {
                error(error);
            }
        });
    }

    private void showCaptcha(final byte[] imageBytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.INVISIBLE);
                captchaLayout.setVisibility(View.VISIBLE);

                if (imageBytes != null && imageBytes.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        captchaImageView.setImageBitmap(bitmap);
                    } else {
                        error(new Exception("ERR: Failed to decode bitmap"));
                    }
                } else {
                    error(new Exception("ERR: Empty captcha image"));
                }
            }
        });
    }

    private void showQRCode(final byte[] imageBytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.INVISIBLE);
                qrLayout.setVisibility(View.VISIBLE);

                if (imageBytes != null && imageBytes.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        qrImageView.setImageBitmap(bitmap);
                    } else {
                        error(new Exception("ERR: Failed to decode bitmap"));
                    }
                } else {
                    error(new Exception("ERR: empty image"));
                }
            }
        });
    }

    private void navigateToMain(final String userToken) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(LoginQRActivity.this, MainActivity.class);
                intent.putExtra("USER_TOKEN", userToken);
                startActivity(intent);
                finish();
            }
        });
    }

    private void navigateTo2FA(final String userToken) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(LoginQRActivity.this, Login2FAActivity.class);
                intent.putExtra("USER_TOKEN", userToken);
                startActivity(intent);
                finish();
            }
        });
    }

    private void error(final Exception error) {
        Log.d(TAG, "onError: " + error.getMessage());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(LoginQRActivity.this, "onError: " + error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
