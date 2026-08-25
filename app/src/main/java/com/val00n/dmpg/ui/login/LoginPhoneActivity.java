package com.val00n.dmpg.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import com.val00n.dmpg.ui.main.MainActivity;

import static android.content.ContentValues.TAG;

public class LoginPhoneActivity extends Activity {

    // UI
    private ProgressBar progressBar;
    private LinearLayout phoneNumberLayout;
    private LinearLayout phoneCaptchaLayout;
    private LinearLayout phoneCodeLayout;
    private ImageView phoneCaptchaImageView;
    private EditText phoneNumberEditText;
    private EditText phoneCaptchaEditText;
    private EditText phoneCodeEditText;
    private Button phoneNumberSubmitButton;
    private Button phoneCaptchaSubmitButton;
    private Button phoneCodeSubmitButton;
    private Button phoneResendSubmitButton;
    // Others
    private MPGramService mpgService;
    private CountDownTimer resendCountdownTimer;
    private String userToken;
    private String captchaId;
    private String phoneNumber;
    private String phoneCodeHash;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_phone);

        mpgService = App.getMPGramService();

        progressBar = (ProgressBar) findViewById(R.id.loginPhoneProgressBar);
        phoneNumberLayout = (LinearLayout) findViewById(R.id.loginPhoneNumberLayout);
        phoneCaptchaLayout = (LinearLayout) findViewById(R.id.loginPhoneCaptchaLayout);
        phoneCodeLayout = (LinearLayout) findViewById(R.id.loginPhoneCodeLayout);
        phoneCaptchaImageView = (ImageView) findViewById(R.id.loginPhoneCaptchaImageView);
        phoneNumberEditText = (EditText) findViewById(R.id.loginPhoneNumberEditText);
        phoneCaptchaEditText = (EditText) findViewById(R.id.loginPhoneCaptchaEditText);
        phoneCodeEditText = (EditText) findViewById(R.id.loginPhoneCodeEditText);
        phoneNumberSubmitButton = (Button) findViewById(R.id.loginPhoneNumberSubmitButton);
        phoneCaptchaSubmitButton = (Button) findViewById(R.id.loginPhoneCaptchaSubmitButton);
        phoneCodeSubmitButton = (Button) findViewById(R.id.loginPhoneCodeSubmitButton);
        phoneResendSubmitButton = (Button) findViewById(R.id.loginPhoneResendCodeButton);

        phoneNumberSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideEverything();
                submitPhoneNumber(phoneNumberEditText.getText().toString());
            }
        });

        phoneCaptchaSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideEverything();
                submitCaptcha(phoneCaptchaEditText.getText().toString());
                resendCooldown();
            }
        });

        phoneCodeSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideEverything();
                submitLoginCode(phoneCodeEditText.getText().toString());
            }
        });

        phoneResendSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resendCode();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendCountdownTimer != null) {
            resendCountdownTimer.cancel();
        }
    }

    private void hideEverything() {
        progressBar.setVisibility(View.VISIBLE);
        phoneNumberLayout.setVisibility(View.GONE);
        phoneCaptchaLayout.setVisibility(View.GONE);
        phoneCodeLayout.setVisibility(View.GONE);
    }

    private void resendCooldown() {
        phoneResendSubmitButton.setEnabled(false);
        resendCountdownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000;
                phoneResendSubmitButton.setText("Resend (" + seconds + "s)");
            }

            @Override
            public void onFinish() {
                phoneResendSubmitButton.setEnabled(true);
                phoneResendSubmitButton.setText("Resend Code");
            }
        }.start();
    }

    private void resendCode() {
        resendCooldown();

        mpgService.loginResendLoginCode(phoneNumber, phoneCodeHash, userToken, new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(AuthResponse result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(LoginPhoneActivity.this, "Code sent again", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                error(error);
            }
        });
    }

    private void submitPhoneNumber(final String phoneNumber) {
        this.phoneNumber = phoneNumber;
        mpgService.loginStartPhoneLogin(phoneNumber, new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(final AuthResponse response) {
                if ("need_captcha".equals(response.response)) {
                    captchaId = response.captchaID;
                    showCaptchaStep();
                    fetchCaptchaImage();
                } else {
                    showPhoneNumberStep("unexpected response: " + response.response);
                }
            }

            @Override
            public void onError(final Exception error) {
                error(error);
            }
        });
    }

    private void submitCaptcha(final String captchaSolution) {
        mpgService.loginSubmitCaptchaCode(captchaSolution, captchaId, phoneNumber, new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(final AuthResponse response) {
                if ("code_sent".equals(response.response)) {
                    userToken = response.userToken;
                    phoneCodeHash = response.phoneCodeHash;

                    showLoginCodeStep();
                } else if ("wrong_captcha".equals(response.response)) {
                    showCaptchaStep();
                } else if ("phone_number_invalid".equals(response.response)) {
                    showPhoneNumberStep("phone number invalid");
                } else {
                    showPhoneNumberStep("unexpected response: " + response.response);
                }
            }

            @Override
            public void onError(final Exception error) {
                error(error);
            }
        });
    }

    private void submitLoginCode(final String loginCode) {
        mpgService.loginSubmitLoginCode(loginCode, userToken, new MPGramService.ApiCallback<AuthResponse>() {
            @Override
            public void onSuccess(final AuthResponse response) {
                if ("1".equals(response.response)) {
                    App.getSessionManager().saveSession(userToken);
                    navigateToMain(userToken);
                } else if ("password".equals(response.response)) {
                    navigateTo2FA(userToken);
                } else if ("phone_code_invalid".equals(response.response)) {
                    showPhoneNumberStep("phone code invalid");
                } else {
                    showPhoneNumberStep("unexpected response: " + response.response);
                }
            }

            @Override
            public void onError(final Exception error) {
                error(error);
            }
        });
    }

    private void fetchCaptchaImage() {
        mpgService.loginGetCaptchaImage(captchaId, new MPGramService.ApiCallback<byte[]>() {
            @Override
            public void onSuccess(final byte[] imageBytes) {
                showCaptchaImage(imageBytes);
            }

            @Override
            public void onError(final Exception error) {
                error(error);
            }
        });
    }

    private void showPhoneNumberStep(final String error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (error != null && error.length() > 0) {
                    Toast.makeText(LoginPhoneActivity.this, error, Toast.LENGTH_LONG).show();
                }

                progressBar.setVisibility(View.INVISIBLE);
                phoneNumberLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showCaptchaStep() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.INVISIBLE);
                phoneCaptchaLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showCaptchaImage(final byte[] imageBytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (imageBytes != null && imageBytes.length > 0) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        phoneCaptchaImageView.setImageBitmap(bitmap);
                    } else {
                        Toast.makeText(LoginPhoneActivity.this, "ERR: Failed to decode bitmap", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "ERR: Failed to decode bitmap");
                        finish();
                    }
                } else {
                    Toast.makeText(LoginPhoneActivity.this, "ERR: Empty captcha image", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "ERR: Empty captcha image bytes");
                    finish();
                }
            }
        });
    }

    private void showLoginCodeStep() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.INVISIBLE);
                phoneCodeLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void navigateToMain(final String userToken) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(LoginPhoneActivity.this, MainActivity.class);
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
                Intent intent = new Intent(LoginPhoneActivity.this, Login2FAActivity.class);
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
                Toast.makeText(LoginPhoneActivity.this, "onError: " + error.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
