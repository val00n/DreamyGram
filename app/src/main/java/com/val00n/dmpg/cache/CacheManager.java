package com.val00n.dmpg.cache;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import com.val00n.dmpg.App;
import com.val00n.dmpg.model.CacheFile;
import com.val00n.dmpg.services.FileHelper;
import com.val00n.dmpg.services.MPGramService;
import com.val00n.dmpg.utils.BitmapUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CacheManager {

    private CacheDatabaseHelper dbHelper;
    private MPGramService mpgService;
    private FileHelper fileHelper;

    private final BitmapLruCache memoryCache = new BitmapLruCache();
    private final Map<String, ArrayList<AvatarCallback>> inFlight =
            new HashMap<String, ArrayList<AvatarCallback>>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final Object diskTrimLock = new Object();

    private static final long TTL_MILLIS = 10L * 24 * 60 * 60 * 1000; // TODO: unhardcode
    private static final long AVATAR_LIMIT = 32L * 1024 * 1024; // TODO: same

    public interface AvatarCallback {
        void onLoaded(Bitmap bitmap);
        void onError(Exception error);
    }

    public CacheManager(Context context) {
        dbHelper = new CacheDatabaseHelper(context.getApplicationContext());
        fileHelper = new FileHelper();
        mpgService = App.getMPGramService();
    }

    private void finishLoadedImage(final String peer, final Bitmap bitmap) {
        ArrayList<AvatarCallback> callbacks;
        synchronized (inFlight) {
            callbacks = inFlight.remove(peer);
        }
        if (callbacks == null) return;

        final ArrayList<AvatarCallback> finalCallbacks = callbacks;
        mainHandler.post(new Runnable() {
            public void run() {
                for (AvatarCallback cb : finalCallbacks) {
                    cb.onLoaded(bitmap);
                }
            }
        });
    }

    private void finishErrorImage(final String peer, final Exception error) {
        final ArrayList<AvatarCallback> callbacks;
        synchronized (inFlight) {
            callbacks = inFlight.remove(peer);
        }
        if (callbacks == null) return;

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (AvatarCallback cb : callbacks) {
                    cb.onError(error);
                }
            }
        });
    }

    public void loadAvatar(final String peer, final AvatarCallback callback) {
        final Bitmap cached = memoryCache.get(peer);
        if (cached != null) {
            deliverLoaded(callback, cached);
            return;
        }

        Bitmap cachedUnderLock;
        boolean owner = false;
        synchronized (inFlight) {
            cachedUnderLock = memoryCache.get(peer);
            if (cachedUnderLock == null) {
                ArrayList<AvatarCallback> queue = inFlight.get(peer);
                if (queue != null) {
                    queue.add(callback);
                    return;
                }

                queue = new ArrayList<AvatarCallback>();
                queue.add(callback);
                inFlight.put(peer, queue);
                owner = true;
            }
        }

        if (cachedUnderLock != null) {
            deliverLoaded(callback, cachedUnderLock);
            return;
        }

        if (owner) {
            ioExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        final CacheFile cacheFile = dbHelper.findAvatar(peer);
                        final long now = System.currentTimeMillis();
                        boolean fresh = cacheFile != null && (now - cacheFile.createdAt) <= TTL_MILLIS;

                        if (cacheFile != null) {
                            Bitmap bitmap = null;
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

                                if (fresh) {
                                    dbHelper.touch(cacheFile.id, now);
                                    finishLoadedImage(peer, bitmap);
                                    return;
                                }
                            } else {
                                dbHelper.deleteAvatar(peer);
                                fileHelper.deleteFile(cacheFile.filename, true);
                            }
                        }

                        String userToken = App.getSessionManager().loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl());
                        mpgService.getAvatar(peer, userToken, new MPGramService.ApiCallback<byte[]>() {
                            @Override
                            public void onSuccess(final byte[] imageBytes) {
                                if (imageBytes == null || imageBytes.length == 0) {
                                    finishErrorImage(peer, new Exception("Empty bitmap"));
                                    return;
                                }

                                try {
                                    final Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromByteArray(
                                            imageBytes, mpgService.AVATAR_SIZE, mpgService.AVATAR_SIZE, false);

                                    if (bitmap != null) {
                                        CacheFile freshAvatar = new CacheFile();
                                        freshAvatar.createdAt = System.currentTimeMillis();
                                        freshAvatar.lastUsed = freshAvatar.createdAt;
                                        freshAvatar.isAvatar = true;
                                        freshAvatar.peer = peer;
                                        freshAvatar.filename = "ava_" + peer;
                                        freshAvatar.size = imageBytes.length;

                                        if (fileHelper.saveFileBytes(freshAvatar.filename, imageBytes, true)) {
                                            dbHelper.saveAvatar(freshAvatar);
                                            trimAvatarDiskCache();
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

    private void deliverLoaded(final AvatarCallback callback, final Bitmap bitmap) {
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

    private void trimAvatarDiskCache() {
        synchronized (diskTrimLock) {
            long currentSize = dbHelper.getTotalFilesSize(true);

            while (currentSize > AVATAR_LIMIT) {
                CacheFile oldest = dbHelper.getOldestAvatarFile();
                if (oldest == null) {
                    break;
                }

                dbHelper.deleteFileById(oldest.id);
                fileHelper.deleteFile(oldest.filename, true);

                currentSize -= oldest.size;
            }
        }
    }
}
