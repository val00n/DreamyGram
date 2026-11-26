package com.val00n.dmpg.cache;

import android.content.Context;
import android.content.SharedPreferences;
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
    private SharedPreferences sharedPreferences;

    private final BitmapLruCache memoryCache = new BitmapLruCache();
    private final Map<String, ArrayList<ImageCallback>> imageInFlight = new HashMap<String, ArrayList<ImageCallback>>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final Object diskTrimLock = new Object();
    private final java.util.Set<String> failedImageKeys = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    private static final long DEFAULT_AVATAR_TTL_MILLIS = 10L * 24 * 60 * 60 * 1000;
    private static final long DEFAULT_FILES_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000;
    private static final long DEFAULT_AVATAR_LIMIT = 16L * 1024 * 1024;
    private static final long DEFAULT_FILES_LIMIT = 32L * 1024 * 1024;
    private static final long DEFAULT_CHATS_LIMIT = 16L * 1024 * 1024;

    private static final long MAX_TTL_DAYS = 36500L;
    private static final long MAX_LIMIT_MB = 1048576L;

    private volatile File lastTempVideoFile;

    private volatile long avatarTtlMillis = DEFAULT_AVATAR_TTL_MILLIS;
    private volatile long filesTtlMillis = DEFAULT_FILES_TTL_MILLIS;
    private volatile long avatarLimit = DEFAULT_AVATAR_LIMIT;
    private volatile long filesLimit = DEFAULT_FILES_LIMIT;
    private volatile long chatsLimit = DEFAULT_CHATS_LIMIT;
    private volatile boolean storeAvatars = true;
    private volatile boolean storeMedia = true;
    private volatile boolean storeChats = true;

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
        sharedPreferences = context.getSharedPreferences("cache", Context.MODE_PRIVATE);
        loadSettingsFromPrefs();
    }

    private void loadSettingsFromPrefs() {
        long avatarsTtlDays = clamp(sharedPreferences.getLong("avatarsTTL", 10), 1L, MAX_TTL_DAYS);
        long mediaTtlDays = clamp(sharedPreferences.getLong("mediaTTL", 30), 1L, MAX_TTL_DAYS);
        long avatarsLimitMb = clamp(sharedPreferences.getLong("avatarsLimit", 16), 0L, MAX_LIMIT_MB);
        long mediaLimitMb = clamp(sharedPreferences.getLong("mediaLimit", 32), 0L, MAX_LIMIT_MB);
        long chatsLimitMb = clamp(sharedPreferences.getLong("chatsLimit", 16), 0L, MAX_LIMIT_MB);

        avatarTtlMillis = avatarsTtlDays * 24L * 60L * 60L * 1000L;
        filesTtlMillis = mediaTtlDays * 24L * 60L * 60L * 1000L;
        avatarLimit = avatarsLimitMb * 1024L * 1024L;
        filesLimit = mediaLimitMb * 1024L * 1024L;
        chatsLimit = chatsLimitMb * 1024L * 1024L;
        storeAvatars = sharedPreferences.getBoolean("storeAvatars", true);
        storeMedia = sharedPreferences.getBoolean("storeMedia", true);
        storeChats = sharedPreferences.getBoolean("storeChats", true);
    }

    private static long clamp(long val, long min, long max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }

    public void refreshSettings() {
        refreshSettings(null);
    }

    public void refreshSettings(final Runnable onDone) {
        final boolean oldStoreAvatars = storeAvatars;
        final boolean oldStoreMedia = storeMedia;
        final boolean oldStoreChats = storeChats;

        loadSettingsFromPrefs();

        final java.util.concurrent.atomic.AtomicInteger pending =
                new java.util.concurrent.atomic.AtomicInteger(3);

        final Runnable stepDone = new Runnable() {
            public void run() {
                if (pending.decrementAndGet() == 0 && onDone != null) {
                    mainHandler.post(onDone);
                }
            }
        };

        if (oldStoreAvatars && !storeAvatars) {
            wipeFilesCache(stepDone, true);
        } else if (storeAvatars) {
            ioExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        trimFilesDiskCache(true);
                    } finally {
                        stepDone.run();
                    }
                }
            });
        } else {
            stepDone.run();
        }

        if (oldStoreMedia && !storeMedia) {
            wipeFilesCache(stepDone, false);
        } else if (storeMedia) {
            ioExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        trimFilesDiskCache(false);
                    } finally {
                        stepDone.run();
                    }
                }
            });
        } else {
            stepDone.run();
        }

        if (oldStoreChats && !storeChats) {
            wipeChatsCache(stepDone);
        } else if (storeChats) {
            ioExecutor.execute(new Runnable() {
                public void run() {
                    try {
                        trimChatsDiskCache();
                    } finally {
                        stepDone.run();
                    }
                }
            });
        } else {
            stepDone.run();
        }
    }

    private void finishLoadedImage(final String key, final Bitmap bitmap) {
        ArrayList<ImageCallback> callbacks;
        synchronized (imageInFlight) {
            callbacks = imageInFlight.remove(key);
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
        final String key = "ava" + peer;

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
                    try {
                        final CacheFile cacheFile = dbHelper.findAvatar(peer);
                        final long now = System.currentTimeMillis();
                        boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= avatarTtlMillis;

                        final boolean allowNetwork = App.isNetworkAvailable() && App.getSettingsManager().getLoadAvatars();

                        if (cacheFile != null) {
                            Bitmap bitmap;
                            try {
                                bitmap = BitmapUtils.decodeSampledBitmapFromFile(
                                        fileHelper.getFullAppdataPath(cacheFile.filename),
                                        mpgService.AVATAR_SIZE, mpgService.AVATAR_SIZE, true);
                            } catch (OutOfMemoryError e) {
                                e.printStackTrace();
                                finishErrorImage(key, new Exception("OOM"));
                                return;
                            }

                            if (bitmap != null) {
                                memoryCache.put(key, bitmap);

                                if (fresh || !allowNetwork) {
                                    dbHelper.touch(cacheFile.id, now, false);
                                    finishLoadedImage(key, bitmap);
                                    return;
                                }
                            } else {
                                dbHelper.deleteAvatar(peer);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }

                        if (!allowNetwork) {
                            finishErrorImage(key, new Exception(
                                    App.isNetworkAvailable() ? "Avatars disabled in settings" : "Offline"));
                            return;
                        }

                        String userToken = App.getSessionManager().loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl());

                        if (!App.isNetworkAvailable()) {
                            finishErrorImage(key, new Exception("Offline"));
                            return;
                        }

                        mpgService.getAvatar(peer, userToken, new MPGramService.ApiCallback<byte[]>() {
                            @Override
                            public void onSuccess(final byte[] imageBytes) {
                                if (imageBytes == null || imageBytes.length == 0) {
                                    finishErrorImage(key, new Exception("Empty bitmap"));
                                    return;
                                }

                                try {
                                    final Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromByteArray(
                                            imageBytes, mpgService.AVATAR_SIZE, mpgService.AVATAR_SIZE, true);

                                    if (bitmap != null) {
                                        if (storeAvatars) {
                                            CacheFile freshAvatar = new CacheFile();
                                            freshAvatar.createdAt = System.currentTimeMillis();
                                            freshAvatar.lastUsed = freshAvatar.createdAt;
                                            freshAvatar.isAvatar = true;
                                            freshAvatar.peer = peer;
                                            freshAvatar.filename = key;
                                            freshAvatar.size = imageBytes.length;

                                            if (fileHelper.saveFileBytes(freshAvatar.filename, imageBytes, true)) {
                                                dbHelper.saveFile(freshAvatar);
                                                trimFilesDiskCache(true);
                                            }
                                        }

                                        memoryCache.put(key, bitmap);
                                        finishLoadedImage(key, bitmap);
                                    } else {
                                        finishErrorImage(key, new Exception("Fresh bitmap is null"));
                                    }
                                } catch (Exception e) {
                                    finishErrorImage(key, e);
                                    return;
                                }
                            }

                            @Override
                            public void onError(Exception error) {
                                error.printStackTrace();
                                finishErrorImage(key, error);
                            }
                        });
                    } catch (Exception e) {
                        finishErrorImage(key, e);
                    }
                }
            });
        }
    }

    public void loadImage(final String peer, final String messageId, final FileParams fileParams, final ImageCallback callback) {
        loadImage(peer, messageId, fileParams, false, callback);
    }

    public void loadImage(final String peer, final String messageId, final FileParams fileParams, final boolean forceNetwork, final ImageCallback callback) {
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
                        final boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= filesTtlMillis;

                        final boolean allowNetwork = App.isNetworkAvailable()
                                && (forceNetwork || App.getSettingsManager().getLoadMedia());

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

                                if (fresh || !allowNetwork) {
                                    dbHelper.touch(cacheFile.id, now, false);
                                    finishLoadedImage(key, bitmap);
                                    return;
                                }
                            } else {
                                dbHelper.deleteFileById(cacheFile.id);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }

                        if (!allowNetwork) {
                            if (fallbackBitmap != null) {
                                if (cacheFile != null) {
                                    dbHelper.touch(cacheFile.id, now, false);
                                }
                                finishLoadedImage(key, fallbackBitmap);
                            } else {
                                finishErrorImage(key, new Exception(App.isNetworkAvailable() ? "Media disabled in settings" : "Offline"));
                            }
                            return;
                        }

                        String userToken = App.getSessionManager().loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl());

                        if (!App.isNetworkAvailable()) {
                            if (fallbackBitmap != null) {
                                if (cacheFile != null) {
                                    dbHelper.touch(cacheFile.id, now, false);
                                }
                                finishLoadedImage(key, fallbackBitmap);
                            } else {
                                finishErrorImage(key, new Exception("Offline"));
                            }
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
                                        if (storeMedia) {
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
        loadVideo(peer, messageId, filename, false, callback);
    }

    public void loadVideo(final String peer, final String messageId, final String filename, final boolean forceNetwork, final VideoCallback callback) {
        String safe = FileHelper.sanitizeFileName(filename);
        if (safe == null || safe.length() == 0) {
            safe = "video.mp4";
        }
        final String safeName = "vid_" + peer + "_" + messageId + "_" + safe;

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final CacheFile cacheFile = dbHelper.findFile(peer, messageId, false);
                    final long now = System.currentTimeMillis();
                    final boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= filesTtlMillis;

                    final boolean allowNetwork = App.isNetworkAvailable()
                            && (forceNetwork || App.getSettingsManager().getLoadMedia());

                    if (cacheFile != null) {
                        File file = new File(fileHelper.getFullAppdataPath(cacheFile.filename));

                        if (file.exists() && file.length() > 0) {
                            if (fresh || !allowNetwork) {
                                dbHelper.touch(cacheFile.id, now, false);
                                postVideoLoaded(callback, file);
                                return;
                            } else {
                                dbHelper.deleteFileById(cacheFile.id);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }
                    }

                    if (!allowNetwork) {
                        postVideoError(callback, new Exception(App.isNetworkAvailable() ? "Media disabled in settings" : "Offline"));
                        return;
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
                                                if (storeMedia) {
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
                                                } else {
                                                    File prev = lastTempVideoFile;
                                                    lastTempVideoFile = file;
                                                    if (prev != null && !prev.equals(file) && prev.exists()) {
                                                        prev.delete();
                                                    }
                                                }

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
        if (!storeChats) return;
        String filename = "hist" + peer;
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
        if (!storeChats) return;
        fileHelper.saveFileText("dialogs", jsonString, true);
    }

    public String loadDialogs() {
        if (!FileHelper.isFileExist("dialogs", true)) return null;
        return fileHelper.loadFileText("dialogs", true);
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

                        String mimeType = FileHelper.getMimeType(dst.getName());
                        //FileHelper.scanFileForGallery(App.get(), dst.getAbsolutePath(), mimeType);

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

    private static String buildHistoryKey(String peer, String topic) {
        if (topic != null && topic.length() > 0) {
            return peer + "_" + topic;
        }
        return peer;
    }

    public void saveHistory(final String peer, final String topic, final long lastUsed, final String jsonString) {
        saveHistory(buildHistoryKey(peer, topic), lastUsed, jsonString);
    }

    public String loadHistory(final String peer, final String topic) {
        return loadHistory(buildHistoryKey(peer, topic));
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
            long limit = avatars ? avatarLimit : filesLimit;

            while (currentSize > limit) {
                CacheFile oldest = dbHelper.getOldestFile(avatars);
                if (oldest == null) break;

                dbHelper.deleteFileById(oldest.id);
                fileHelper.deleteFile(oldest.filename, true);
                currentSize -= oldest.size;
            }
        }
    }

    private void trimChatsDiskCache() {
        synchronized (diskTrimLock) {
            long currentSize = dbHelper.getHistorySize();

            while (currentSize > chatsLimit) {
                CacheHistory oldest = dbHelper.getOldestHistory();
                if (oldest == null) break;

                dbHelper.deleteHistoryById(oldest.id);
                fileHelper.deleteFile(oldest.filename, true);
                currentSize -= oldest.size;
            }
        }
    }

    public void wipeFilesCache(final Runnable onDone, final boolean avatars) {
        memoryCache.wipe(avatars);

        final String removeKey = avatars ? "ava" : "img";
        synchronized (imageInFlight) {
            java.util.Iterator<java.util.Map.Entry<String, java.util.ArrayList<ImageCallback>>> it =
                    imageInFlight.entrySet().iterator();

            while (it.hasNext()) {
                java.util.Map.Entry<String, java.util.ArrayList<ImageCallback>> entry = it.next();
                String key = entry.getKey();
                if (key != null && key.startsWith(removeKey)) {
                    it.remove();
                }
            }
        }

        if (!avatars) {
            failedImageKeys.clear();
        }

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (diskTrimLock) {
                    java.util.List<String> fileNames = null;
                    try {
                        fileNames = dbHelper.getFileNames(avatars);
                        dbHelper.clearFiles(avatars);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (fileNames != null) {
                        for (String name : fileNames) {
                            try {
                                fileHelper.deleteFile(name, true);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (onDone != null) {
                    mainHandler.post(onDone);
                }
            }
        });
    }

    public void wipeChatsCache(final Runnable onDone) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    dbHelper.clearHistory();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    File dir = new File(FileHelper.getAppDataDirectoryPath());
                    File[] files = dir.listFiles();

                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile() && file.getName().startsWith("hist")) {
                                file.delete();
                            }
                        }
                    }

                    fileHelper.deleteFile("dialogs", true);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (onDone != null) {
                    mainHandler.post(onDone);
                }
            }
        }, "cache-wipe").start();
    }

    public void wipeCache(final Runnable onDone) {
        wipeFilesCache(null, true);
        wipeFilesCache(null, false);
        wipeChatsCache(onDone);
    }

    public long getAvatarsSize() { return dbHelper.getFilesSize(true); }
    public long getMediaSize() { return dbHelper.getFilesSize(false); }
    public long getChatsSize() { return dbHelper.getHistorySize(); }
    public long getStorageSavedSize() {
        File mediaDir = new File(FileHelper.getMediaDirectoryPath());
        return getDirectorySize(mediaDir);
    }

    private long getDirectorySize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) size += f.length();
                else size += getDirectorySize(f);
            }
        }
        return size;
    }
}
