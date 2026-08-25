package com.val00n.dmpg;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import com.val00n.dmpg.network.HttpClient;
import com.val00n.dmpg.services.InstanceManager;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;

public class App extends Application {

    private static App app;
    private static InstanceManager instanceManager;
    private static SessionManager sessionManager;
    private static ConnectivityManager cm;
    private static MPGramService mpgService;

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
            mpgService = new MPGramService();
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

        HttpClient.setUserAgent("DMPG/" + BuildConfig.VERSION_NAME + " (Android SDK " + Build.VERSION.RELEASE + "; " + Build.MODEL + ')');
    }

    public static boolean isNetworkAvailable() {
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }
}