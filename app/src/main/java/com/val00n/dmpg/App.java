package com.val00n.dmpg;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.widget.Toast;
import com.val00n.dmpg.network.HttpClient;
import com.val00n.dmpg.services.InstanceManager;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.startup.StartupActivity;

public class App extends Application {

    private static App app;
    private static InstanceManager instanceManager;
    private static SessionManager sessionManager;
    private static ConnectivityManager cm;
    private static MPGramService mpgService;
    private static String[] sessionDeathMarkers;
    public static volatile String selfId;
    public static volatile boolean sessionDead;

    public static Context get() {
        return app;
    }

    public static InstanceManager getInstanceManager() {
        return instanceManager;
    }

    public static SessionManager getSessionManager() {
        return sessionManager;
    }

    public static MPGramService getMPGramService() {
        if (mpgService == null) {
            mpgService = new MPGramService(3);
        }
        return mpgService;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        app = this;
        instanceManager = new InstanceManager(this);
        sessionManager = new SessionManager(this);
        cm = (ConnectivityManager) this.getSystemService(this.CONNECTIVITY_SERVICE);

        instanceManager.loadInstances();

        sessionDeathMarkers = new String[] {
                "401",
                "unauthorized",
                "invalid authorization",
                "session_revoked",
                "authentication",
                "auth_key"
        };

        HttpClient.setUserAgent("DMPG/" + BuildConfig.VERSION_NAME + " (Android SDK " + Build.VERSION.RELEASE + "; " + Build.MODEL + ')');
    }

    public static boolean isNetworkAvailable() {
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    public static boolean isSessionDead(String text, final Activity activity) {
        if (text == null) return false;
        if (sessionDead) return true;

        text = text.toLowerCase();

        for (int i = 0; i < sessionDeathMarkers.length; i++) {
            if (text.contains(sessionDeathMarkers[i])) {
                sessionManager.clearSession(instanceManager.getSelectedInstanceBaseUrl());
                sessionDead = true;

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "session has died", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(activity, StartupActivity.class);
                        activity.startActivity(intent);
                        activity.finish();
                    }
                });
                return true;
            }
        }
        return false;
    }
}