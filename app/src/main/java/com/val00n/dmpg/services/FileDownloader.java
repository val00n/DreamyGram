package com.val00n.dmpg.services;

import android.os.Handler;
import android.os.Looper;
import com.val00n.dmpg.App;
import com.val00n.dmpg.model.FileParams;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FileDownloader {

    public interface FileCallback {
        void onProgress(long downloaded, long total);
        void onSuccess(File file);
        void onError(Exception error);
        void onCancelled();
    }

    public interface Handle {
        void cancel();
    }

    private static class FileRequest {
        final String peer;
        final String messageId;
        final FileParams params;
        final File targetFile;
        final ArrayList<FileCallback> callbacks = new ArrayList<FileCallback>();
        DownloadTask task;
        boolean finished;

        FileRequest(String peer, String messageId, FileParams params, File targetFile) {
            this.peer = peer;
            this.messageId = messageId;
            this.params = params;
            this.targetFile = targetFile;
        }
    }

    private final MPGramService service;
    private final FileHelper fileHelper;
    private final Handler mainHandler;
    private final Map<String, FileRequest> inFlight = new HashMap<String, FileRequest>();

    public FileDownloader(MPGramService service) {
        this.service = service;
        this.fileHelper = new FileHelper();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public Handle download(String peer, String messageId, FileParams fileParams, String finalName, boolean isAppData, FileCallback cb) {
        String safeName = FileHelper.sanitizeFileName(finalName);
        if (safeName == null) {
            safeName = "file";
        }
        final String finalSafeName = safeName;

        String dirPath = isAppData ? FileHelper.getAppDataDirectoryPath() : FileHelper.getMediaDirectoryPath();
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        final File target = new File(dir, finalSafeName);

        if (target.exists() && target.length() > 0) {
            postSuccess(cb, target);
            return noopHandle();
        }

        final String key = buildKey(peer, messageId, fileParams);
        final FileRequest req;
        synchronized (inFlight) {
            FileRequest existing = (FileRequest) inFlight.get(key);
            if (existing == null) {
                existing = new FileRequest(peer, messageId, fileParams, target);
                inFlight.put(key, existing);
            }
            if (cb != null) {
                existing.callbacks.add(cb);
            }
            req = existing;
        }

        if (req.task != null) {
            return handleFor(req);
        }

        String token = App.getSessionManager()
                .loadSession(App.getInstanceManager().getSelectedInstanceBaseUrl());
        File temp;
        try {
            temp = fileHelper.createTempFileFor(finalSafeName, isAppData);
        } catch (IOException e) {
            finishError(req, e);
            return noopHandle();
        } catch (Exception e) {
            finishError(req, e);
            return noopHandle();
        }

        final File tempFinal = temp;
        final boolean finalIsAppData = isAppData;
        DownloadTask task = service.downloadFile(peer, messageId, fileParams, token, temp,
                new MPGramService.DownloadCallback() {
                    public void onProgress(long d, long t) {
                        ArrayList<FileCallback> cbs = snapshot(req);
                        for (int i = 0; i < cbs.size(); i++) {
                            postProgress((FileCallback) cbs.get(i), d, t);
                        }
                    }

                    public void onSuccess(File f) {
                        try {
                            File finalFile = fileHelper.finishDownload(tempFinal, finalSafeName, finalIsAppData);

                            if (!finalIsAppData) {
                                String mimeType = FileHelper.getMimeType(finalFile.getName());
                                //FileHelper.scanFileForGallery(App.get(), finalFile.getAbsolutePath(), mimeType);
                            }

                            finishSuccess(req, finalFile);
                        } catch (IOException e) {
                            tempFinal.delete();
                            finishError(req, e);
                        } catch (Exception e) {
                            tempFinal.delete();
                            finishError(req, e);
                        }
                    }
                    public void onError(Exception e) {
                        tempFinal.delete();
                        finishError(req, e);
                    }
                    public void onCancelled() {
                        tempFinal.delete();
                        finishCancelled(req);
                    }
                });

        synchronized (inFlight) {
            if (req.finished) {
                task.cancel();
            } else {
                req.task = task;
            }
        }
        return handleFor(req);
    }

    private String buildKey(String peer, String messageId, FileParams fileParams) {
        StringBuilder sb = new StringBuilder();
        if (peer != null) sb.append(peer);
        sb.append("-");
        if (messageId != null) sb.append(messageId);
        sb.append("-");
        if (fileParams != null) {
            sb.append(String.valueOf(fileParams.hashCode()));
        }
        return sb.toString();
    }

    private void finishSuccess(FileRequest req, File file) {
        ArrayList<FileCallback> list;
        synchronized (inFlight) {
            inFlight.remove(buildKey(req.peer, req.messageId, req.params));
            list = new ArrayList<FileCallback>(req.callbacks);
            req.finished = true;
            req.callbacks.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            postSuccess((FileCallback) list.get(i), file);
        }
    }

    private void finishError(FileRequest req, Exception e) {
        ArrayList<FileCallback> list;
        synchronized (inFlight) {
            inFlight.remove(buildKey(req.peer, req.messageId, req.params));
            list = new ArrayList<FileCallback>(req.callbacks);
            req.finished = true;
            req.callbacks.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            postError((FileCallback) list.get(i), e);
        }
    }

    private void finishCancelled(FileRequest req) {
        ArrayList<FileCallback> list;
        synchronized (inFlight) {
            inFlight.remove(buildKey(req.peer, req.messageId, req.params));
            list = new ArrayList<FileCallback>(req.callbacks);
            req.finished = true;
            req.callbacks.clear();
        }
        for (int i = 0; i < list.size(); i++) {
            postCancelled((FileCallback) list.get(i));
        }
    }

    private ArrayList<FileCallback> snapshot(FileRequest req) {
        synchronized (inFlight) {
            return new ArrayList<FileCallback>(req.callbacks);
        }
    }

    private Handle handleFor(final FileRequest req) {
        return new Handle() {
            public void cancel() {
                DownloadTask t;
                synchronized (inFlight) {
                    t = req.task;
                }
                if (t != null) {
                    t.cancel();
                }
            }
        };
    }

    private Handle noopHandle() {
        return new Handle() {
            public void cancel() { }
        };
    }

    private void postSuccess(final FileCallback cb, final File f) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            public void run() {
                cb.onSuccess(f);
            }
        });
    }

    private void postError(final FileCallback cb, final Exception e) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            public void run() {
                cb.onError(e);
            }
        });
    }

    private void postProgress(final FileCallback cb, final long d, final long t) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            public void run() {
                cb.onProgress(d, t);
            }
        });
    }

    private void postCancelled(final FileCallback cb) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            public void run() {
                cb.onCancelled();
            }
        });
    }
}