package com.val00n.dmpg.services;

import android.util.Log;
import com.val00n.dmpg.App;
import com.val00n.dmpg.model.DialogItem;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.LoginResponse;
import com.val00n.dmpg.network.RestClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;
import static android.content.ContentValues.TAG;

public class MPGramService {

    private final RestClient restClient;
    private final InstanceManager instanceManager;
    private Instance instance;
    private int getDialogLimit = 20; // TODO Pagination

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

    public void checkAuth(String userToken, final ApiCallback<LoginResponse> callback) {
        checkAuth(instance, userToken, callback);
    }

    public void checkAuth(final Instance pickedInstance, String userToken, final ApiCallback<LoginResponse> callback) {
        if (userToken == null || userToken.length() == 0) {
            if (callback != null) callback.onError(new IOException("No saved token"));
            return;
        }

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "checkAuth");
        queryParams.put("v", pickedInstance.apiVersion);

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-MPGRAM-USER", userToken);

        final long start = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(pickedInstance.baseURL, pickedInstance.apiEndpoint, queryParams, headers);
                    long ping = System.currentTimeMillis() - start;

                    Log.d(TAG, "checkAuth Raw Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
                    response.setPing(ping);

                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    long ping = System.currentTimeMillis() - start;
                    String msg = e.getMessage();

                    if (msg != null && (msg.contains("401") || msg.contains("authentication challenge"))) {
                        // code 401 means that session is expired
                        // ugly as hell, needs refactor
                        LoginResponse response = new LoginResponse();
                        response.setResponse("0");
                        response.setPing(ping);
                        if (callback != null) callback.onSuccess(response);
                    } else {
                        if (callback != null) callback.onError(e);
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginStartPhoneLogin(String phoneNumber, final ApiCallback<LoginResponse> callback) {
        phoneNumber = phoneNumber.startsWith("+") ? phoneNumber : "+" + phoneNumber;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "phoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phoneNumber);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.getString(instance.baseURL, instance.apiEndpoint, queryParams);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginStartQRLogin(final ApiCallback<LoginResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "initLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("qr", "1");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.getString(instance.baseURL, instance.apiEndpoint, queryParams);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginQrPoll(String userToken, final ApiCallback<LoginResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "qrLogin");
        queryParams.put("v", instance.apiVersion);

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-MPGRAM-USER", userToken);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "poll Raw Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);

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

    public void loginSubmitCaptchaCode(String captchaSolution, String captchaId, String phoneNumber, final ApiCallback<LoginResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "phoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phoneNumber);
        queryParams.put("captcha_id", captchaId);
        queryParams.put("captcha_key", captchaSolution);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.getString(instance.baseURL, instance.apiEndpoint, queryParams);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginGetQRData(String captchaSolution, String captchaId, final ApiCallback<LoginResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "initLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("qr", "1");
        queryParams.put("captcha_id", captchaId);
        queryParams.put("captcha_key", captchaSolution);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.getString(instance.baseURL, instance.apiEndpoint, queryParams);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginSubmitLoginCode(String code, String userToken, final ApiCallback<LoginResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "completePhoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("code", code);

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-MPGRAM-USER", userToken);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
                    if (callback != null) callback.onSuccess(response);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }

    public void loginResendLoginCode(String phone, String hash, String userToken, final ApiCallback<LoginResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "resendCode");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phone);
        queryParams.put("hash", hash);

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-MPGRAM-USER", userToken);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    LoginResponse response = LoginResponse.fromJSON(parsedJson);
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

    public void getDialogs(String userToken, final ApiCallback<ArrayList<DialogItem>> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getDialogs");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("limit", Integer.toString(getDialogLimit));
        queryParams.put("media", "1");

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-MPGRAM-USER", userToken);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    ArrayList<DialogItem> response = DialogsParser.parse(parsedJson);
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
        queryParams.put("p", "rp48");

        final Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-MPGRAM-USER", userToken);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.postBytes(instance.baseURL, instance.avatarEndpoint, queryParams, headers);
                    Log.d(TAG, "getAvatar: got bytes");

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        }).start();
    }
}
