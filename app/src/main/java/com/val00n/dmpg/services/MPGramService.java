package com.val00n.dmpg.services;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.val00n.dmpg.App;
import com.val00n.dmpg.BuildConfig;
import com.val00n.dmpg.model.DialogResponse;
import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.model.HistoryResponse;
import com.val00n.dmpg.model.Instance;
import com.val00n.dmpg.model.AuthResponse;
import com.val00n.dmpg.model.Update;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.network.HttpClient;
import com.val00n.dmpg.network.RestClient;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static android.content.ContentValues.TAG;

public class MPGramService {

    private final ExecutorService executor;
    private final RestClient restClient;
    private final InstanceManager instanceManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Instance instance;

    private final ExecutorService updatesExecutor = Executors.newSingleThreadExecutor();
    private final Object updatesLock = new Object();
    private volatile boolean updatesInFlight = false;

    private static final int GET_HISTORY_LIMIT = 30; // TODO change this in settings
    private static final int CHECK_AUTH_TIMEOUT = 2;

    public static final int AVATAR_SIZE = 48; // TODO change this in settings
    public static final String FILE_PREVIEW_SIZE = "256"; // TODO change this in settings

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(Exception error);
    }

    public interface DownloadCallback {
        void onProgress(long downloaded, long total);
        void onSuccess(File file);
        void onError(Exception error);
        void onCancelled();
    }

    public MPGramService(int threads) {
        this.restClient = new RestClient();
        executor = Executors.newFixedThreadPool(threads);
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

    public void checkAuth(String userToken, final ApiCallback<Boolean> callback) {
        checkAuth(instance, userToken, callback);
    }

    public void checkAuth(final Instance pickedInstance, String userToken, final ApiCallback<Boolean> callback) {
        if (userToken == null || userToken.length() == 0) {
            if (callback != null) callback.onError(new IOException("No saved token"));
            return;
        }

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getDialogs"); // i hate it. but i think this is only way to do something with telegram itself
        queryParams.put("v", pickedInstance.apiVersion);
        queryParams.put("limit", Integer.toString(2));

        final Map<String, String> headers = buildHeaders(userToken, false, true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(pickedInstance.baseURL, pickedInstance.apiEndpoint, CHECK_AUTH_TIMEOUT, queryParams, headers);
                    boolean result = stringResponse.contains("\"dialogs\":");

                    Log.d(TAG, "checkAuth (getDialogs) Raw Response (" + pickedInstance.baseURL + "/" + pickedInstance.apiEndpoint + "): " + stringResponse);
                    if (callback != null) callback.onSuccess(result);
                } catch (IOException e) {
                    String msg = e.getMessage();

                    if (msg != null) {
                        msg = msg.toLowerCase();

                        if ((msg.contains("401") || msg.contains("authentication challenge") || msg.contains("unsupported API version"))) {
                            // code 401 means that session is expired
                            // ugly as hell, needs refactor

                            if (callback != null) callback.onSuccess(false);
                        } else {
                            if (callback != null) callback.onError(e);
                        }
                    } else {
                        if (callback != null) callback.onError(e);
                    }

                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void pingInstance(final Instance pickedInstance, final ApiCallback<Integer> callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int result = HttpClient.ping(pickedInstance.baseURL);

                    Log.d(TAG, "pinged: " + result);
                    if (callback != null) callback.onSuccess(result);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getSelf(String userToken, final ApiCallback<User> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "me");
        queryParams.put("v", instance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    User response = new User(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginStartPhoneLogin(String phoneNumber, final ApiCallback<AuthResponse> callback) {
        phoneNumber = phoneNumber.startsWith("+") ? phoneNumber : "+" + phoneNumber;

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "phoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phoneNumber);

        final Map<String, String> headers = buildHeaders(null, true, true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginStartQRLogin(final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "initLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("qr", "1");

        final Map<String, String> headers = buildHeaders(null, true, true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginQrPoll(String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "qrLogin");
        queryParams.put("v", instance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, true, true);

        executor.execute(new Runnable() {
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
        });
    }

    public void loginGetCaptchaImage(String captchaId, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getCaptchaImg");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("captcha_id", captchaId);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.getBytes(instance.baseURL, instance.apiEndpoint, queryParams);

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (IOException e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginSubmitCaptchaCode(String captchaSolution, String captchaId, String phoneNumber, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "phoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phoneNumber);
        queryParams.put("captcha_id", captchaId);
        queryParams.put("captcha_key", captchaSolution);

        final Map<String, String> headers = buildHeaders(null, true, true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginGetQRData(String captchaSolution, String captchaId, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "initLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("qr", "1");
        queryParams.put("captcha_id", captchaId);
        queryParams.put("captcha_key", captchaSolution);

        final Map<String, String> headers = buildHeaders(null, true, true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginSubmitLoginCode(String code, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "completePhoneLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("code", code);

        final Map<String, String> headers = buildHeaders(userToken, true, true);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginResendLoginCode(String phone, String hash, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "resendCode");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("phone", phone);
        queryParams.put("hash", hash);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginGetQRImage(String encodedLink, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("t", encodedLink);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.getBytes(instance.baseURL, instance.qrcodeEndpoint, queryParams);

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void loginComplete2FA(String password, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "complete2faLogin");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("password", password);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getDialogs(int limit, String offset_peer, String offset_id, long offset_date, String userToken, final ApiCallback<DialogResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getDialogs");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("limit", Integer.toString(limit));

        if (offset_peer != null && offset_id != null && offset_date > 0) {
            queryParams.put("offset_peer", offset_peer);
            queryParams.put("offset_id", offset_id);
            queryParams.put("offset_date", Long.toString(offset_date));
        }

        queryParams.put("media", "1");

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    DialogResponse response = new DialogResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getHistory(String peer, String offset_id, long offset_date, String userToken, final ApiCallback<HistoryResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getHistory");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("limit", Integer.toString(GET_HISTORY_LIMIT));
        queryParams.put("peer", peer);

        if (offset_id != null) {
            queryParams.put("offset_id", offset_id);
        }

        if (offset_date > 0) {
            queryParams.put("offset_date", Long.toString(offset_date));
        }

        queryParams.put("media", "1");
        queryParams.put("read", "1"); // FIXME: always read every loaded ones

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    HistoryResponse response = new HistoryResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void sendMessage(String userToken, String peer, String text, String reply, String fwdFrom, String fwdId, String top, final ApiCallback<String> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "sendMessage");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("peer", peer);

        if (text != null) {
            queryParams.put("text", text);
        }

        if (reply != null) {
            queryParams.put("reply", reply);
        }

        if (fwdFrom != null) {
            queryParams.put("fwd_from", fwdFrom);
        }

        if (fwdId != null) {
            queryParams.put("id", fwdId);
        }

        if (top != null) {
            queryParams.put("top", top);
        }

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    String response = parsedJson.getNullableString("res");

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void deleteMessage(String userToken, String peer, String id, boolean revoke, final ApiCallback<String> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "deleteMessage");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("peer", peer);
        queryParams.put("id", id);

        if (revoke) {
            queryParams.put("revoke", "1");
        }

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    String response = parsedJson.getNullableString("res");

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void editMessage(String userToken, String peer, String id, String text, final ApiCallback<String> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "editMessage");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("peer", peer);
        queryParams.put("id", id);
        queryParams.put("text", text);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    String response = parsedJson.getNullableString("res");

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getUpdates(String[] filter, String limit, String offset, String messageId, boolean read, final int timeoutSeconds, String userToken, final ApiCallback<ArrayList<Update>> callback) {
        getUpdates(null, filter, limit, offset, messageId, read, timeoutSeconds, userToken, callback);
    }

    public void getUpdates(String peer, String[] filter, String limit, String offset, String messageId, boolean read, final int timeoutSeconds, String userToken, final ApiCallback<ArrayList<Update>> callback) {
        synchronized (updatesLock) {
            if (updatesInFlight) {
                if (callback != null) callback.onError(
                        new IllegalStateException("Updates polling already in progress"));
                return;
            }
            updatesInFlight = true;
        }

        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "updates");
        queryParams.put("v", instance.apiVersion);
        if (peer != null) queryParams.put("peer", peer);
        queryParams.put("limit", limit);
        queryParams.put("offset", offset);
        if (messageId != null) queryParams.put("message", messageId);
        queryParams.put("timeout", Integer.toString(timeoutSeconds));

        queryParams.put("media", "1");
        if (read) {
            queryParams.put("read", "1");
        }

        if (filter != null && filter.length > 0) {
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < filter.length; i++) {
                if (i != 0) s.append(",");
                s.append(filter[i]);
            }

            queryParams.put("types", s.toString());
        }

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        updatesExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, timeoutSeconds, queryParams, headers);
                    stringResponse = stringResponse.trim();

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    if (parsedJson.has("cancel")) {
                        if (callback != null) callback.onSuccess(new ArrayList<Update>());
                        return;
                    }

                    if (parsedJson.has("error")) {
                        if (callback != null) callback.onError(new Exception(parsedJson.getObject("error").getString("message", "(no message)")));
                        Log.d(TAG, "updates: ERROR:  " + parsedJson.getObject("error").getString("message", "(no message)"));
                        Log.d(TAG, parsedJson.getObject("error").getString("stack_trace", "(no stack trace)"));
                    } else {
                        JSONArray res = parsedJson.getArray("res");
                        ArrayList<Update> response= new ArrayList<Update>();

                        for (int i = 0; i < res.size(); i++) {
                            response.add(new Update(res.getObject(i)));
                        }

                        if (callback != null) callback.onSuccess(response);
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                } finally {
                    synchronized (updatesLock) { updatesInFlight = false; }
                }
            }
        });
    }

    public void getLastUpdate(String userToken, final int timeoutSeconds, final ApiCallback<Update> callback) {
        getLastUpdate(null, userToken, timeoutSeconds, callback);
    }

    public void getLastUpdate(String peer, String userToken, final int timeoutSeconds, final ApiCallback<Update> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getLastUpdate");
        queryParams.put("v", instance.apiVersion);
        if (peer != null) queryParams.put("peer", peer);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, timeoutSeconds, queryParams, headers);
                    stringResponse = stringResponse.trim();

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);

                    if (parsedJson.has("error")) {
                        if (callback != null) callback.onError(new Exception(parsedJson.getObject("error").getString("message", "(no message)")));
                        Log.d(TAG, "updates: ERROR:  " + parsedJson.getObject("error").getString("message", "(no message)"));
                        Log.d(TAG, parsedJson.getObject("error").getString("stack_trace", "(no stack trace)"));
                    } else {
                        JSONObject o = parsedJson.getNullableObject("res");
                        if (o != null) {
                            Update response = new Update(o);
                            if (callback != null) callback.onSuccess(response);
                        } else {
                            if (callback != null) callback.onSuccess(null);
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void cancelUpdates(String userToken, final ApiCallback<String> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "cancelUpdates");
        queryParams.put("v", instance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    String response = parsedJson.getNullableString("res");

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getUserPeer(String userToken, String id, final ApiCallback<User> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "getPeer");
        queryParams.put("v", instance.apiVersion);
        queryParams.put("id", id);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(instance.baseURL, instance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    User response = new User(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getAvatar(String peerId, String userToken, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("c", peerId);
        queryParams.put("p", "rp" + AVATAR_SIZE);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] imageBytes = restClient.postBytes(instance.baseURL, instance.avatarEndpoint, queryParams, headers);

                    if (callback != null) callback.onSuccess(imageBytes);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    public void getFile(String peerId, String messageId, FileParams fileParams, String userToken, final ApiCallback<byte[]> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("c", peerId);
        queryParams.put("m", messageId);

        if (fileParams != null) {
            fileParams.applyTo(queryParams);
        }

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] fileBytes = restClient.postBytes(instance.baseURL, instance.fileEndpoint, queryParams, headers);

                    if (callback != null) callback.onSuccess(fileBytes);
                } catch (Throwable t) {
                    t.printStackTrace();
                    if (callback != null) {
                        if (t instanceof Exception) {
                            callback.onError((Exception) t);
                        } else {
                            callback.onError(new Exception(t));
                    }
                }
            }
            }
        });
    }

    public DownloadTask downloadFile(String peerId,
                                     String messageId,
                                     FileParams fileParams,
                                     String userToken,
                                     final File targetFile,
                                     final DownloadCallback callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("c", peerId);
        queryParams.put("m", messageId);

        if (fileParams != null) {
            fileParams.applyTo(queryParams);
        }

        final Map<String, String> headers = buildHeaders(userToken, false, false);
        final DownloadTask task = new DownloadTask(callback, mainHandler);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    restClient.downloadFile(instance.baseURL, instance.fileEndpoint, queryParams, headers, targetFile, task);

                    if (task.isCancelled()) {
                        deleteQuietly(targetFile);
                        postCancelled(callback);
                    } else {
                        postSuccess(callback, targetFile);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    deleteQuietly(targetFile);

                    if (task.isCancelled()) {
                        postCancelled(callback);
                    } else {
                        postError(callback, toException(t));
                    }
                }
            }
        });

        return task;
    }

    public void logout(String userToken, final ApiCallback<AuthResponse> callback) {
        logout(instance, userToken, callback);
    }

    public void logout(final Instance pickedInstance, String userToken, final ApiCallback<AuthResponse> callback) {
        final Map<String, String> queryParams = new HashMap<String, String>();
        queryParams.put("method", "logout");
        queryParams.put("v", pickedInstance.apiVersion);

        final Map<String, String> headers = buildHeaders(userToken, false, false);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String stringResponse = restClient.postString(pickedInstance.baseURL, pickedInstance.apiEndpoint, queryParams, headers);

                    Log.d(TAG, "Raw API Response: " + stringResponse);

                    JSONObject parsedJson = (JSONObject) cc.nnproject.json.JSON.get(stringResponse);
                    AuthResponse response = new AuthResponse(parsedJson);

                    if (callback != null) callback.onSuccess(response);
                } catch (Exception e) {
                    if (callback != null) callback.onError(e);
                }
            }
        });
    }

    private void postSuccess(final DownloadCallback callback, final File file) {
        if (callback == null) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(file);
            }
        });
    }

    private void postError(final DownloadCallback callback, final Exception error) {
        if (callback == null) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private void postCancelled(final DownloadCallback callback) {
        if (callback == null) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onCancelled();
            }
        });
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private static Exception toException(Throwable t) {
        if (t instanceof Exception) {
            return (Exception) t;
        }
        return new Exception(t);
    }
}