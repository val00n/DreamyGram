package com.val00n.dmpg.services;

import android.os.Build;
import android.util.Log;
import com.val00n.dmpg.App;
import com.val00n.dmpg.BuildConfig;
import com.val00n.dmpg.model.AuthResult;
import com.val00n.dmpg.model.DialogResponse;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.AuthResponse;
import com.val00n.dmpg.network.HttpClient;
import com.val00n.dmpg.network.RestClient;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import static android.content.ContentValues.TAG;

public class MPGramService {

    private final RestClient restClient;
    private final InstanceManager instanceManager;
    private Instance instance;
    private int getDialogLimit = 25; // TODO Pagination

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public MPGramService() {
        this.restClient = new RestClient();
        instanceManager = App.getInstanceManager();
        instance = instanceManager.getSelectedInstance();
    }

    public void refreshInstance() {
        instance = instanceManager.getSelectedInstance();
    }

    private Map<String, String> buildHeaders(String userToken, boolean includeInstancePassword, boolean includeClientInfo) {
        Map<String, String> headers = new HashMap<>();
        if (userToken != null && userToken.length() > 0) {
            headers.put("X-MPGRAM-USER", userToken);
        }
        headers.put("X-MPGRAM-UNICODE", "1");

        if (includeInstancePassword && instance.instancePassword != null
                && instance.instancePassword.length() > 0) {
            headers.put("X-MPGRAM-INSTANCE-PASSWORD", instance.instancePassword);
        }

        if (includeClientInfo) {
            headers.put("X-MPGRAM-APP-VERSION", "DreamyGram " + BuildConfig.VERSION_NAME);
            headers.put("X-MPGRAM-DEVICE", Build.MODEL);
            headers.put("X-MPGRAM-SYSTEM", "Android " + Build.VERSION.RELEASE);
        }

        return headers;
    }

    public void checkAuth(String userToken, final ApiCallback<AuthResult> callback) {
        checkAuth(instance, userToken, callback);
    }

    public void checkAuth(final Instance pickedInstance, String userToken, final ApiCallback<AuthResult> callback) {
        if (userToken == null || userToken.length() == 0) {
            if (callback != null) callback.onError(new IOException("No saved token"));
            return;
        }

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getNotifySettings"); // i hate it. but i think this is only way to do something with telegram itself
        queryParams.put("v", pickedInstance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, false, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                long ping = 0;
                try {
                    ping = HttpClient.ping(pickedInstance.baseURL + '/' + pickedInstance.apiEndpoint);

                    String stringResponse = restClient.postString(pickedInstance.baseURL, pickedInstance.apiEndpoint, queryParams, headers);
                    boolean isLogged = stringResponse.contains("users") || stringResponse.contains("chats");

                    Log.d(TAG, "checkAuth Raw Response (" + pickedInstance.baseURL + "/" + pickedInstance.apiEndpoint + "): " + stringResponse);

                    AuthResult result = new AuthResult(isLogged, ping);

                    if (callback != null) callback.onSuccess(result);
                } catch (IOException e) {
                    String msg = e.getMessage();

                    if (msg != null && (msg.contains("401") || msg.contains("authentication challenge"))) {
                        // code 401 means that session is expired
                        // ugly as hell, needs refactor
                        AuthResult result = new AuthResult(false, ping);

                        if (callback != null) callback.onSuccess(result);
                    } else {
                        if (callback != null) callback.onError(e);
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginStartPhoneLogin(String phoneNumber, final ApiCallback<AuthResponse> callback) {
        phoneNumber = phoneNumber.startsWith("+") ? phoneNumber : "+" + phoneNumber;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "phoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phoneNumber);

        final Map<String, String> headers = buildHeaders(null, true, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginStartQRLogin(final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "initLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("qr", "1");

        final Map<String, String> headers = buildHeaders(null, true, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginQrPoll(String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "qrLogin");
        queryParams.put("v", instance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, true, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "poll Raw Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginGetCaptchaImage(String captchaId, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getCaptchaImg");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("captcha_id", captchaId);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.getBytes(instance.baseURL, instance.apiEndpoint, queryParams);

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginSubmitCaptchaCode(String captchaSolution, String captchaId, String phoneNumber, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "phoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phoneNumber);
        queryParams.put("captcha_id", captchaId);
        queryParams.put("captcha_key", captchaSolution);

        final Map<String, String> headers = buildHeaders(null, true, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginGetQRData(String captchaSolution, String captchaId, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "initLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("qr", "1");
        queryParams.put("captcha_id", captchaId);
        queryParams.put("captcha_key", captchaSolution);

        final Map<String, String> headers = buildHeaders(null, true, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginSubmitLoginCode(String code, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "completePhoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("code", code);

        final Map<String, String> headers = buildHeaders(userToken, true, true);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginResendLoginCode(String phone, String hash, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "resendCode");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phone);
        queryParams.put("hash", hash);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginGetQRImage(String encodedLink, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("t", encodedLink);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.getBytes(instance.baseURL, instance.qrcodeEndpoint, queryParams);

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginComplete2FA(String password, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "complete2faLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("password", password);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void getDialogs(String userToken, final ApiCallback<DialogResponse> callback) {
        getDialogs(null, null, 0, userToken, callback);
    }

    public void getDialogs(String offset_peer, String offset_id, long offset_date, String userToken, final ApiCallback<DialogResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getDialogs");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("limit", Integer.toString(getDialogLimit));

        if (offset_peer != null && offset_id != null && offset_date > 0) {
            queryParams.put("offset_peer", offset_peer);
            queryParams.put("offset_id", offset_id);
            queryParams.put("offset_date", Long.toString(offset_date));
        }

        queryParams.put("media", "1");

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    DialogResponse response = new DialogResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void getAvatar(String peerId, String userToken, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("c", peerId);
        queryParams.put("p", "rp48"); // i should unhardcode it

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.postBytes(instance.baseURL, instance.avatarEndpoint, queryParams, headers);

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void logout(String userToken, final ApiCallback<AuthResponse> callback) {
        logout(instance, userToken, callback);
    }

    public void logout(final Instance pickedInstance, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "logout");
        queryParams.put("v", pickedInstance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(pickedInstance.baseURL, pickedInstance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }
}