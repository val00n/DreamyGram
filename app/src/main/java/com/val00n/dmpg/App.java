package com.val00n.dmpg;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.widget.Toast;

import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.network.HttpClient;
import com.val00n.dmpg.services.FileDownloader;
import com.val00n.dmpg.services.InstanceManager;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.services.SessionManager;
import com.val00n.dmpg.ui.StartupActivity;

import java.util.ArrayList;

public class App extends Application {

    private static App app;
    private static InstanceManager instanceManager;
    private static SessionManager sessionManager;
    private static CacheManager cacheManager;
    private static FileDownloader fileDownloader;
    private static MPGramService mpgService;
    private static ConnectivityManager cm;
    private static String[] sessionDeathMarkers;
    private static boolean lastNetworkState = false;

    public static volatile String selfId;
    public static volatile boolean sessionDead;

    private static final ArrayList<NetworkListener> networkListeners =
            new ArrayList<NetworkListener>();

    public interface NetworkListener {
        void onNetworkChanged(boolean available);
    }

    public static Context get() {
        return app;
    }

    public static InstanceManager getInstanceManager() {
        return instanceManager;
    }

    public static SessionManager getSessionManager() {
        return sessionManager;
    }

    public static CacheManager getCacheManager() {
        if (cacheManager == null) cacheManager = new CacheManager(app);
        return cacheManager;
    }

    public static FileDownloader getFileDownloader() {
        if (fileDownloader == null) {
            fileDownloader = new FileDownloader(getMPGramService());
        }
        return fileDownloader;
    }

    public static MPGramService getMPGramService() {
        if (mpgService == null) {
            mpgService = new MPGramService(4); // FIXME: 4 is just random number
        }
        return mpgService;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        app = this;
        instanceManager = new InstanceManager(this);
        sessionManager = new SessionManager(this);

        cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        lastNetworkState = isNetworkAvailable();

        registerReceiver(
                connectivityReceiver,
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        );

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

    public static boolean isSessionDead(String text, final Activity activity) {
        if (text == null) return false;
        if (sessionDead) return true;

        text = text.toLowerCase();

        for (int i = 0; i < sessionDeathMarkers.length; i++) {
            if (text.contains(sessionDeathMarkers[i])) {
                sessionManager.clearSession(instanceManager.getSelectedInstanceBaseUrl());
                sessionDead = true;

                getCacheManager().wipeCache(new Runnable() {
                    @Override
                    public void run() {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, "session has died", Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(activity, StartupActivity.class);
                                activity.startActivity(intent);
                                activity.finish();
                            }
                        });
                    }
                });

                return true;
            }
        }
        return false;
    }

    public static boolean isNetworkAvailable() {
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private static BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean available = isNetworkAvailable();

            if (available == lastNetworkState) return;
            lastNetworkState = available;

            ArrayList<NetworkListener> copy;
            synchronized (networkListeners) {
                copy = new ArrayList<NetworkListener>(networkListeners);
            }

            for (int i = 0; i < copy.size(); i++) {
                NetworkListener listener = copy.get(i);
                if (listener != null) {
                    listener.onNetworkChanged(available);
                }
            }
        }
    };

    public static void addNetworkListener(NetworkListener listener) {
        if (listener == null) return;
        synchronized (networkListeners) {
            networkListeners.add(listener);
        }
    }

    public static void removeNetworkListener(NetworkListener listener) {
        if (listener == null) return;
        synchronized (networkListeners) {
            networkListeners.remove(listener);
        }
    }
}