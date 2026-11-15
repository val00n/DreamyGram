package com.val00n.dmpg.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import com.val00n.dmpg.App;
import com.val00n.dmpg.model.cache.CacheFile;
import com.val00n.dmpg.model.cache.CacheHistory;
import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.services.FileDownloader;
import com.val00n.dmpg.services.FileHelper;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.utils.BitmapUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheManager {

    private CacheDatabaseHelper dbHelper;
    private MPGramService mpgService;
    private FileHelper fileHelper;

    private final BitmapLruCache memoryCache = new BitmapLruCache();
    private final Map<String, ArrayList<ImageCallback>> imageInFlight = new HashMap<String, ArrayList<ImageCallback>>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final Object diskTrimLock = new Object();
    private final java.util.Set<String> failedImageKeys = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static final long AVATAR_TTL_MILLIS = 10L * 24 * 60 * 60 * 1000; // TODO: unhardcode
    private static final long FILES_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000; // TODO: unhardcode

    private static final long AVATAR_LIMIT = 16L * 1024 * 1024; // TODO: same
    private static final long FILES_LIMIT = 32L * 1024 * 1024; // TODO: same
    private static final long CHATS_LIMIT = 16L * 1024 * 1024; // TODO: same

    public interface ImageCallback {
        void onLoaded(Bitmap bitmap);
        void onError(Exception error);
    }

    public interface VideoCallback {
        void onLoaded(File file);
        void onError(Exception error);
    }

    public interface SaveFileCallback {
        void onSaved(File file);
        void onError(Exception error);
    }

    public CacheManager(Context context) {
        dbHelper = new CacheDatabaseHelper(context.getApplicationContext());
        fileHelper = new FileHelper();
        mpgService = App.getMPGramService();
    }

    private void finishLoadedImage(final String peer, final Bitmap bitmap) {
        ArrayList<ImageCallback> callbacks;
        synchronized (imageInFlight) {
            callbacks = imageInFlight.remove(peer);
        }
        if (callbacks == null) return;

        final ArrayList<ImageCallback> finalCallbacks = callbacks;
        mainHandler.post(new Runnable() {
            public void run() {
                for (ImageCallback cb : finalCallbacks) {
                    cb.onLoaded(bitmap);
                }
            }
        });
    }

    private void finishErrorImage(final String key, final Exception error) {
        final ArrayList<ImageCallback> callbacks;
        synchronized (imageInFlight) {
            callbacks = imageInFlight.remove(key);
        }
        if (callbacks == null) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (ImageCallback cb : callbacks) {
                    cb.onError(error);
                }
            }
        });
    }

    public void loadAvatar(final String peer, final ImageCallback callback) {
        final Bitmap cached = memoryCache.get(peer);
        if (cached != null) {
            deliverLoadedImage(callback, cached);
            return;
        }

        Bitmap cachedUnderLock;
        boolean owner = false;
        synchronized (imageInFlight) {
            cachedUnderLock = memoryCache.get(peer);
            if (cachedUnderLock == null) {
                ArrayList<ImageCallback> queue = imageInFlight.get(peer);
                if (queue != null) {
                    queue.add(callback);
                    return;
                }

                queue = new ArrayList<ImageCallback>();
                queue.add(callback);
                imageInFlight.put(peer, queue);
                owner = true;
            }
        }

        if (cachedUnderLock != null) {
            deliverLoadedImage(callback, cachedUnderLock);
            return;
        }

        if (owner) {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final CacheFile cacheFile = dbHelper.findAvatar(peer);
                        final long now = System.currentTimeMillis();
                        boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= AVATAR_TTL_MILLIS;

                        if (cacheFile != null) {
                            Bitmap bitmap;
                            try {
                                bitmap = BitmapUtils.decodeSampledBitmapFromFile(
                                        fileHelper.getFullAppdataPath(cacheFile.filename),
                                        mpgService.AVATAR_SIZE, mpgService.AVATAR_SIZE, true);
                            } catch (OutOfMemoryError e) {
                                e.printStackTrace();
                                finishErrorImage(peer, new Exception("OOM"));
                                return;
                            }

                            if (bitmap != null) {
                                memoryCache.put(peer, bitmap);

                                if (fresh || !App.isNetworkAvailable()) {
                                    dbHelper.touch(cacheFile.id, now, false);
                                    finishLoadedImage(peer, bitmap);
                                    return;
                                }
                            } else {
                                dbHelper.deleteAvatar(peer);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }

                        String userToken = App.getSessionManager().loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl());

                        if (!App.isNetworkAvailable()) {
                            finishErrorImage(peer, new Exception("Offline"));
                            return;
                        }

                        mpgService.getAvatar(peer, userToken, new MPGramService.ApiCallback<byte[]>() {
                            @Override
                            public void onSuccess(final byte[] imageBytes) {
                                if (imageBytes == null || imageBytes.length == 0) {
                                    finishErrorImage(peer, new Exception("Empty bitmap"));
                                    return;
                                }

                                try {
                                    final Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromByteArray(
                                            imageBytes, mpgService.AVATAR_SIZE, mpgService.AVATAR_SIZE, true);

                                    if (bitmap != null) {
                                        CacheFile freshAvatar = new CacheFile();
                                        freshAvatar.createdAt = System.currentTimeMillis();
                                        freshAvatar.lastUsed = freshAvatar.createdAt;
                                        freshAvatar.isAvatar = true;
                                        freshAvatar.peer = peer;
                                        freshAvatar.filename = "ava_" + peer;
                                        freshAvatar.size = imageBytes.length;

                                        if (fileHelper.saveFileBytes(freshAvatar.filename, imageBytes, true)) {
                                            dbHelper.saveFile(freshAvatar);
                                            trimFilesDiskCache(true);
                                        }

                                        memoryCache.put(peer, bitmap);
                                        finishLoadedImage(peer, bitmap);
                                    } else {
                                        finishErrorImage(peer, new Exception("Fresh bitmap is null"));
                                    }
                                } catch (Exception e) {
                                    finishErrorImage(peer, e);
                                    return;
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                error.printStackTrace();
                                finishErrorImage(peer, error);
                            }
                        });
                    } catch (Exception e) {
                        finishErrorImage(peer, e);
                    }
                }
            });
        }
    }

    public void loadImage(final String peer, final String messageId, final FileParams fileParams, final ImageCallback callback) {
        final boolean preview = fileParams != null && !fileParams.equals(FileParams.original());
        final String key = "img_" + peer + "_" + messageId + (preview ? "_p" : "");

        if (failedImageKeys.contains(key)) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onError(new Exception("Cached image error"));
                }
            });
            return;
        }

        final Bitmap cached = memoryCache.get(key);
        if (cached != null) {
            deliverLoadedImage(callback, cached);
            return;
        }
        Bitmap cachedUnderLock;
        boolean owner = false;
        synchronized (imageInFlight) {
            cachedUnderLock = memoryCache.get(key);
            if (cachedUnderLock == null) {
                ArrayList<ImageCallback> queue = imageInFlight.get(key);
                if (queue != null) {
                    queue.add(callback);
                    return;
                }
                queue = new ArrayList<ImageCallback>();
                queue.add(callback);
                imageInFlight.put(key, queue);
                owner = true;
            }
        }
        if (cachedUnderLock != null) {
            deliverLoadedImage(callback, cachedUnderLock);
            return;
        }
        if (owner) {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Bitmap fallbackBitmap = null;
                    try {
                        final CacheFile cacheFile = dbHelper.findFile(peer, messageId, preview);
                        final long now = System.currentTimeMillis();
                        final boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= FILES_TTL_MILLIS;

                        if (cacheFile != null) {
                            Bitmap bitmap;
                            try {
                                bitmap = BitmapUtils.decodeSampledBitmapFromFile(
                                        fileHelper.getFullAppdataPath(cacheFile.filename),
                                        -1, -1, true);
                            } catch (OutOfMemoryError e) {
                                e.printStackTrace();
                                finishErrorImage(key, new Exception("OOM"));
                                return;
                            }

                            if (bitmap != null) {
                                fallbackBitmap = bitmap;
                                memoryCache.put(key, bitmap);

                                if (fresh || !App.isNetworkAvailable()) {
                                    dbHelper.touch(cacheFile.id, now, false);
                                    finishLoadedImage(key, bitmap);
                                    return;
                                }
                            } else {
                                dbHelper.deleteFileById(cacheFile.id);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }

                        String userToken = App.getSessionManager().loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl());

                        if (!App.isNetworkAvailable()) {
                            if (fallbackBitmap != null) finishLoadedImage(key, fallbackBitmap);
                            else finishErrorImage(key, new Exception("Offline"));
                            return;
                        }
                        final Bitmap finalFallback = fallbackBitmap;

                        mpgService.getFile(peer, messageId, fileParams, userToken, new MPGramService.ApiCallback<byte[]>() {
                            @Override
                            public void onSuccess(final byte[] imageBytes) {
                                if (imageBytes == null || imageBytes.length == 0) {
                                    if (finalFallback != null) finishLoadedImage(key, finalFallback);
                                    else finishErrorImage(key, new Exception("Empty bitmap"));
                                    return;
                                }

                                try {
                                    final Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromByteArray(
                                            imageBytes, -1, -1, true);

                                    if (bitmap != null) {
                                        CacheFile freshFile = new CacheFile();
                                        freshFile.createdAt = System.currentTimeMillis();
                                        freshFile.lastUsed = freshFile.createdAt;
                                        freshFile.isAvatar = false;
                                        freshFile.preview = preview;
                                        freshFile.peer = peer;
                                        freshFile.messageId = messageId;
                                        freshFile.filename = preview ? "preview_" + UUID.randomUUID().toString() : UUID.randomUUID().toString();
                                        freshFile.size = imageBytes.length;

                                        if (fileHelper.saveFileBytes(freshFile.filename, imageBytes, true)) {
                                            dbHelper.saveFile(freshFile);
                                            trimFilesDiskCache(false);
                                        }

                                        memoryCache.put(key, bitmap);
                                        finishLoadedImage(key, bitmap);
                                    } else {
                                        if (finalFallback != null) finishLoadedImage(key, finalFallback);
                                        else finishErrorImage(key, new Exception("Fresh bitmap is null"));
                                    }
                                } catch (Exception e) {
                                    if (finalFallback != null) finishLoadedImage(key, finalFallback);
                                    else finishErrorImage(key, e);
                                    return;
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                if (error != null && error.getMessage() != null && error.getMessage().contains("HTTP 500")) {
                                    failedImageKeys.add(key);
                                }

                                if (finalFallback != null) finishLoadedImage(key, finalFallback);
                                else finishErrorImage(key, error);
                            }
                        });
                    } catch (Exception e) {
                        finishErrorImage(key, e);
                    }
                }
            });
        }
    }

    public void loadVideo(final String peer, final String messageId, final String filename, final VideoCallback callback) {
        final String safeName = "vid_" + peer + "_" + messageId + "_" + FileHelper.sanitizeFileName(filename);

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final CacheFile cacheFile = dbHelper.findFile(peer, messageId, false);
                    final long now = System.currentTimeMillis();
                    final boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= FILES_TTL_MILLIS;

                    if (cacheFile != null) {
                        File file = new File(fileHelper.getFullAppdataPath(cacheFile.filename));

                        if (file.exists() && file.length() > 0) {
                            if (fresh || !App.isNetworkAvailable()) {
                                dbHelper.touch(cacheFile.id, now, false);
                                postVideoLoaded(callback, file);
                                return;
                            } else {
                                dbHelper.deleteFileById(cacheFile.id);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }
                    }

                    if (!App.isNetworkAvailable()) {
                        postVideoError(callback, new Exception("Offline"));
                        return;
                    }

                    App.getFileDownloader().download(peer, messageId, null, safeName, true, new FileDownloader.FileCallback() {
                                @Override
                                public void onProgress(long downloaded, long total) {
                                    // TODO?
                                }

                                @Override
                                public void onSuccess(final File file) {
                                    ioExecutor.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                CacheFile freshVideo = new CacheFile();
                                                freshVideo.createdAt = System.currentTimeMillis();
                                                freshVideo.lastUsed = freshVideo.createdAt;
                                                freshVideo.isAvatar = false;
                                                freshVideo.preview = false;
                                                freshVideo.peer = peer;
                                                freshVideo.messageId = messageId;
                                                freshVideo.filename = safeName;
                                                freshVideo.size = file.length();

                                                dbHelper.saveFile(freshVideo);
                                                trimFilesDiskCache(false);

                                                postVideoLoaded(callback, file);
                                            } catch (Exception e) {
                                                postVideoError(callback, e);
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onError(final Exception error) {
                                    postVideoError(callback, error);
                                }

                                @Override
                                public void onCancelled() {
                                    postVideoError(callback, new Exception("Cancelled"));
                                }
                            }
                    );
                } catch (Exception e) {
                    postVideoError(callback, e);
                }
            }
        });
    }

    public void saveHistory(final String peer, final long lastUsed, final String jsonString) {
        String filename = "hist_" + peer;
        fileHelper.saveFileText(filename, jsonString, true);
        dbHelper.saveHistory(peer, filename, lastUsed);
    }

    public String loadHistory(final String peer) {
        CacheHistory history = dbHelper.findHistory(peer);
        if (history != null) {
            dbHelper.touch(history.id, System.currentTimeMillis(), true);
            trimChatsDiskCache();
            return fileHelper.loadFileText(history.filename, true);
        } else {
            return null;
        }
    }

    public void saveDialogs(final String jsonString) {
        fileHelper.saveFileText("dialogs", jsonString, true); // bruh
    }

    public String loadDialogs() {
        if (!FileHelper.isFileExist("dialogs", true)) return null;
        return fileHelper.loadFileText("dialogs", true); // bruh 2
    }

    public boolean hasCachedDialogs() {
        File file = new File(fileHelper.getFullAppdataPath("dialogs"));
        return file.exists() && file.length() > 0;
    }

    public void saveCachedFileToMedia(final String peer, final String messageId, final String targetName, final boolean preferNonPreview, final SaveFileCallback callback) {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    CacheFile cacheFile = preferNonPreview
                            ? dbHelper.findFile(peer, messageId, false)
                            : dbHelper.findFile(peer, messageId, true);

                    if (cacheFile == null) {
                        cacheFile = preferNonPreview
                                ? dbHelper.findFile(peer, messageId, true)
                                : dbHelper.findFile(peer, messageId, false);
                    }

                    if (cacheFile == null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(new Exception("No cached file"));
                            }
                        });
                        return;
                    }

                    File src = new File(fileHelper.getFullAppdataPath(cacheFile.filename));

                    if (!src.exists() || src.length() == 0) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(new Exception("Cache file missing on disk"));
                            }
                        });
                        return;
                    }

                    String safeName = FileHelper.sanitizeFileName(targetName);
                    if (safeName == null) safeName = cacheFile.filename;

                    final File dst = new File(FileHelper.getMediaDirectoryPath(), safeName);

                    if (fileHelper.copyFile(src, dst)) {
                        dbHelper.touch(cacheFile.id, System.currentTimeMillis(), false);
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSaved(dst);
                            }
                        });
                    } else {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(new Exception("Copy failed"));
                            }
                        });
                    }
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        });
    }

    private void postVideoLoaded(final VideoCallback callback, final File file) {
        if (callback == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onLoaded(file);
            }
        });
    }

    private void postVideoError(final VideoCallback callback, final Exception error) {
        if (callback == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private void deliverLoadedImage(final ImageCallback callback, final Bitmap bitmap) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onLoaded(bitmap);
        } else {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onLoaded(bitmap);
                }
            });
        }
    }

    private void trimFilesDiskCache(boolean avatars) {
        synchronized (diskTrimLock) {
            long currentSize = dbHelper.getFilesSize(avatars);

            while (currentSize > (avatars ? AVATAR_LIMIT : FILES_LIMIT)) {
                CacheFile oldest = dbHelper.getOldestFile(avatars);
                if (oldest == null) {
                    break;
                }

                dbHelper.deleteFileById(oldest.id);
                fileHelper.deleteFile(oldest.filename, true);

                currentSize -= oldest.size;
            }
        }
    }

    private void trimChatsDiskCache() {
        synchronized (diskTrimLock) {
            long currentSize = dbHelper.getHistorySize();

            while (currentSize > CHATS_LIMIT) {
                CacheHistory oldest = dbHelper.getOldestHistory();
                if (oldest == null) {
                    break;
                }

                dbHelper.deleteHistoryById(oldest.id);
                fileHelper.deleteFile(oldest.filename, true);

                currentSize -= oldest.size;
            }
        }
    }

    public void wipeCache(final Runnable onDone) {
        memoryCache.trimToSize(0);

        synchronized (imageInFlight) {
            imageInFlight.clear();
        }

        failedImageKeys.clear();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    dbHelper.clearFiles();
                    dbHelper.clearHistory();
                    dbHelper.clearPending();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    File dir = new File(FileHelper.getAppDataDirectoryPath());
                    File[] files = dir.listFiles();

                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile() && !"instances.json".equals(file.getName())) {
                                file.delete();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (onDone != null) {
                    mainHandler.post(onDone);
                }
            }
        }, "cache-wipe").start();
    }
}
