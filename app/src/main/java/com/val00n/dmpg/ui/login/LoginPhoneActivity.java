package com.val00n.dmpg.ui.login;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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
    private String userToken;
    private String captchaId;
    private String phoneNumber;
    private String phoneCodeHash;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_phone);

        mpgService = new MPGramService();

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
                resendCode(); // add cooldown
            }
        });
    }

    private void hideEverything() {
        progressBar.setVisibility(View.VISIBLE);
        phoneNumberLayout.setVisibility(View.GONE);
        phoneCaptchaLayout.setVisibility(View.GONE);
        phoneCodeLayout.setVisibility(View.GONE);
    }

    private void resendCode() {
        mpgService.loginResendLoginCode(phoneNumber, userToken, phoneCodeHash, new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse result) {
                Toast.makeText(LoginPhoneActivity.this, "Code sent again", Toast.LENGTH_SHORT);
            }

            @Override
            public void onError(Exception error) {
                Log.d(TAG, "onError: "+ error.getMessage());
            }
        });
    }

    private void submitPhoneNumber(final String phoneNumber) {
        this.phoneNumber = phoneNumber;
        mpgService.loginStartPhoneLogin(phoneNumber, new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(final LoginResponse response) {
                if ("need_captcha".equals(response.getResponse())) {
                    captchaId = response.getCaptchaID();
                    showCaptchaStep();
                    fetchCaptchaImage();
                }
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "onError: " + error.getMessage());
            }
        });
    }

    private void submitCaptcha(final String captchaSolution) {
        mpgService.loginSubmitCaptchaCode(captchaSolution, captchaId, phoneNumber, new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(final LoginResponse response) {
                if ("code_sent".equals(response.getResponse())) {
                    userToken = response.getUserToken();
                    phoneCodeHash = response.getPhoneCodeHash();

                    showLoginCodeStep();
                } else if ("wrong_captcha".equals(response.getResponse())) {
                    showCaptchaStep();
                } else if ("phone_number_invalid".equals(response.getResponse())) {
                    showPhoneNumberStep("phone number invalid");
                }
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "onError: " + error.getMessage());
            }
        });
    }

    private void submitLoginCode(final String loginCode) {
        mpgService.loginSubmitLoginCode(loginCode, userToken, new MPGramService.ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(final LoginResponse response) {
                if ("1".equals(response.getResponse())) {
                    App.getSessionManager().saveSession(userToken);
                    navigateToMain(userToken);
                } else if ("phone_code_invalid".equals(response.getResponse())) {
                    showPhoneNumberStep("phone code invalid");
                }
            }

            @Override
            public void onError(final Exception error) {
                Log.d(TAG, "onError: " + error.getMessage());
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
                Log.d(TAG, "onError: " + error.getMessage());
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
                    }
                } else {
                    Toast.makeText(LoginPhoneActivity.this, "ERR: Empty captcha image", Toast.LENGTH_LONG).show();
                    Log.e(TAG, "ERR: Empty image bytes");
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
}
