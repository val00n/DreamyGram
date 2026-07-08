package com.val00n.dmpg.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.val00n.dmpg.App;

import static android.content.ContentValues.TAG;

public class SessionManager {

    private static final String PREFS_NAME = "MPGRAM_SESSION";
    private final SharedPreferences prefs;
    private final InstanceManager instanceManager;

    public SessionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.instanceManager = App.getInstanceManager();
    }

    public void saveSession(String token) {
        saveSession(token, instanceManager.getSelectedInstanceBaseUrl());
    }

    public void saveSession(String token, String baseUrl) {
        if (token == null || token.length() == 0 || baseUrl == null || baseUrl.length() == 0) return;
        prefs.edit().putString(baseUrl, token).commit();
    }

    public String loadSession(String baseUrl) {
        Log.d(TAG, "SESSION load: trying to fetch: " + baseUrl);
        return prefs.getString(baseUrl, null);
    }

    public void clearSession(String baseUrl) {
        prefs.edit().remove(baseUrl).commit();
    }
}