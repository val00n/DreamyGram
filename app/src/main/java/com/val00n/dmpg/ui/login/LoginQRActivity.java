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
import com.val00n.dmpg.model.AuthResponse;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.main.MainActivity;
import static android.content.ContentValues.TAG;

public class LoginQRActivity extends Activity {

    private ProgressBar progressBar;
    private LinearLayout captchaLayout;
    private LinearLayout qrLayout;
    private ImageView captchaImageView;
    private ImageView qrImageView;
    private EditText captchaSolutionEditText;
    private Button captchaSubmitButton;

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

        mpgService = App.getMPGramService();
        sessionManager = App.getSessionManager();

        qrPollingHandler = new Handler();

        qrPollTask = new Runnable() {
            @Override
            public void run() {
                mpgService.loginQrPoll(userToken, new MPGramService.ApiCallback<AuthResponse>() {
                    @Override
                    public void onSuccess(AuthResponse result) {
                        if ("1".equals(result.response)) {
                            sessionManager.saveSession(userToken);
                            navigateToMain(userToken);
                        } else if ("password".equals(result.response)) {
                            navigateTo2FA(userToken);
                        } else if (result.text != null && result.text.length() > 0) {
                            if (result.text.equals(qrLink)) {
                                qrPollingHandler.postDelayed(qrPollTask, 3000);
                            } else {
                                qrLink = result.text;
                                getQrImage();
                                qrPollingHandler.postDelayed(qrPollTask, 3000);
                            }
                        } else {
                            error(new Exception("Poll has died unexpectedly :("));
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
        mpgService.loginStartQRLogin(new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(final AuthResponse response) {
                if ("need_captcha".equals(response.response)) {
                    captchaId = response.captchaID;

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
                    error(new Exception("unexpected response: " + response.response));
                }
            }

            @Override
            public void onError(final Exception error) {
                error(error);
            }
        });
    }

    private void submitCaptcha(final String captchaSolution) {
        mpgService.loginGetQRData(captchaSolution, captchaId, new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                if ("qr".equals(result.response)) {
                    qrLink = result.text;
                    userToken = result.userToken;

                    getQrImage();
                    qrPollingHandler.postDelayed(qrPollTask, 5000);
                } else if ("wrong_captcha".equals(result.response) || "captcha_expired".equals(result.response)) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            captchaLayout.setVisibility(View.VISIBLE);
                            fetchCaptcha();
                        }
                    });
                } else {
                    error(new Exception("unexpected response: " + result.response));
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
        Log.d(TAG, "Error: " + error.getMessage());
        error.printStackTrace();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (error.getMessage() != null) {
                    Toast.makeText(LoginQRActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(LoginQRActivity.this, "Error: " + error.getClass().getName(), Toast.LENGTH_LONG).show();
                }
                finish();
            }
        });
    }
}
