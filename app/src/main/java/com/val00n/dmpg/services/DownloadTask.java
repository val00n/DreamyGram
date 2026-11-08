package com.val00n.dmpg.services;

import android.os.Handler;

import com.val00n.dmpg.network.HttpClient;

public class DownloadTask implements HttpClient.FileDownloadListener {
    private volatile boolean cancelled = false;
    private volatile java.net.HttpURLConnection connection;
    private final MPGramService.DownloadCallback callback;
    private final Handler handler;
    private long lastProgressTime = 0;

    DownloadTask(MPGramService.DownloadCallback callback, Handler handler) {
        this.callback = callback;
        this.handler = handler;
    }

    public void cancel() {
        cancelled = true;

        java.net.HttpURLConnection c = connection;
        if (c != null) {
            c.disconnect();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void onConnectionOpened(java.net.HttpURLConnection connection) {
        this.connection = connection;

        if (cancelled && connection != null) {
            connection.disconnect();
        }
    }

    @Override
    public void onProgress(final long downloaded, final long total) {
        long now = System.currentTimeMillis();

        if (total > 0 && downloaded < total && now - lastProgressTime < 100) {
            return;
        }

        lastProgressTime = now;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!cancelled && callback != null) {
                    callback.onProgress(downloaded, total);
                }
            }
        });
    }
}
