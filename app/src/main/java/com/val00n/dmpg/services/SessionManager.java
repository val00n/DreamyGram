package com.val00n.dmpg.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.val00n.dmpg.App;
import java.util.HashMap;
import java.util.Map;

import static android.content.ContentValues.TAG;

public class SessionManager {

    private static final String PREFS_NAME = "MPGRAM_SESSION";
    private final SharedPreferences prefs;
    private final InstanceManager instanceManager;
    private final Map<String, String> cache = new HashMap<String, String>();

    public SessionManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.instanceManager = App.getInstanceManager();
    }

    public void saveSession(String token) {
        saveSession(token, instanceManager.getSelectedInstanceBaseUrl());
    }

    public void saveSession(String token, String baseUrl) {
        if (token == null || token.length() == 0 || baseUrl == null || baseUrl.length() == 0) return;
        synchronized (cache) { cache.put(baseUrl, token); }
        prefs.edit().putString(baseUrl, token).commit();
    }

    public String loadSession(String baseUrl) {
        synchronized (cache) {
            String t = cache.get(baseUrl);
            if (t != null) return t;
        }
        String t = prefs.getString(baseUrl, null);
        if (t != null) synchronized (cache) { cache.put(baseUrl, t); }
        Log.d(TAG, "loadSession: " + t);
        return t;
    }

    public void clearSession(String baseUrl) {
        synchronized (cache) { cache.remove(baseUrl); }
        prefs.edit().remove(baseUrl).commit();
        App.selfId = null;
    }
}