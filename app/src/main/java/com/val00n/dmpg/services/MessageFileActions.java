package com.val00n.dmpg.services;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.model.message.Message;
import java.io.File;

/**
 *  FIXME: genuinely, this thing shouldn't even exist
 *  TODO: it's a garbage class, so i HOPE i'll get rid of this ASAP
 */

public class MessageFileActions {

    public static void showSaveMenu(final Activity activity, final String peerId, final Message msg, final boolean withOpen) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (msg == null || msg.media == null) {
            return;
        }

        boolean document = isDocument(msg);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.file_actions_save_title);

        if (document) {
            String displayName = msg.media.name;

            if (displayName == null || displayName.trim().length() == 0) {
                displayName = getFileName(peerId, msg, false);
            }

            String sizeText = "";
            if (msg.media.size > 0) {
                sizeText = " (" + FileHelper.formatSize(msg.media.size) + ")";
            }

            builder.setMessage(activity.getString(R.string.file_actions_save_message, displayName, sizeText));

            builder.setPositiveButton(
                    R.string.action_save,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            saveMessage(activity, peerId, msg, true, false);
                        }
                    }
            );

            if (withOpen) {
                builder.setNeutralButton(
                        R.string.file_actions_save_and_open,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                saveMessage(activity, peerId, msg, true, true);
                            }
                        }
                );
            }

            builder.setNegativeButton(R.string.action_cancel, null);
        } else {
            builder.setMessage(R.string.file_actions_save_file_message);

            if (withOpen) {
                builder.setPositiveButton(
                        "Save",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                saveMessage(activity, peerId, msg, true, false);
                            }
                        }
                );

                builder.setNeutralButton(
                        R.string.file_actions_save_and_open,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                saveMessage(activity, peerId, msg, true, true);
                            }
                        }
                );
            } else {
                if (!msg.media.type.equals("video")) {
                    builder.setPositiveButton(
                            R.string.file_actions_save_original,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    saveMessage(activity, peerId, msg, true, false);
                                }
                            }
                    );

                    builder.setNeutralButton(
                            R.string.file_actions_save_cached,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    saveMessage(activity, peerId, msg, false, false);
                                }
                            }
                    );
                } else {
                    builder.setPositiveButton(
                            R.string.action_save,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    saveMessage(activity, peerId, msg, false, false);
                                }
                            }
                    );
                }
            }

            builder.setNegativeButton(R.string.action_cancel, null);
        }

        builder.show();
    }

    public static void saveMessage(final Activity activity, final String peer, final Message message, boolean original, final boolean openAfter) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        if (message == null || message.media == null) {
            return;
        }

        if (message.id <= 0) {
            Toast.makeText(activity, R.string.file_actions_cannot_save_pending, Toast.LENGTH_SHORT).show();
            return;
        }

        final String filename = getFileName(peer, message, !original);

        if (filename == null) {
            Toast.makeText(activity, R.string.file_actions_cannot_resolve_filename, Toast.LENGTH_SHORT).show();
            return;
        }

        File existing = findSavedFile(peer, message, !original);

        if (existing != null) {
            if (openAfter) {
                openFile(activity, existing, message.media.mime);
            } else {
                Toast.makeText(activity, activity.getString(R.string.file_actions_already_saved, existing.getName()), Toast.LENGTH_SHORT).show();
            }

            return;
        }

        if (!original) {
            boolean video = message.media != null && "video".equals(message.media.type);
            App.getCacheManager().saveCachedFileToMedia(peer, String.valueOf(message.id), filename, video, new CacheManager.SaveFileCallback() {
                        public void onSaved(File file) {
                            if (activity.isFinishing()) {
                                return;
                            }

                            if (file != null && file.exists() && file.length() > 0) {
                                if (openAfter) {
                                    openFile(activity, file, message.media.mime);
                                } else {
                                    Toast.makeText(activity, activity.getString(R.string.file_actions_saved_from_cache, file.getName()), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                downloadWithDialog(activity, peer, message, filename, openAfter);
                            }
                        }

                        public void onError(Exception error) {
                            if (activity.isFinishing()) {
                                return;
                            }

                            downloadWithDialog(activity, peer, message, filename, openAfter);
                        }
                    }
            );

            return;
        }

        downloadWithDialog(activity, peer, message, filename, openAfter);
    }

    public static File findSavedFile(String peer, Message message, boolean cache) {
        String filename = getFileName(peer, message, cache);

        if (filename == null) {
            return null;
        }

        File file = FileHelper.getCachedFile(filename, false);

        if (file != null && file.exists() && file.length() > 0) {
            return file;
        }

        return null;
    }

    public static String getFileName(String peer, Message message, boolean cache) {
        if (message == null || message.media == null) {
            return null;
        }

        if (isDocument(message)) {
            String name = FileHelper.sanitizeFileName(message.media.name);

            if (name != null && name.length() > 0) {
                return name;
            }

            return baseName(peer, message, cache) + getExtensionForMime(message.media.mime);
        }

        if (message.media.type != null) {
            return baseName(peer, message, cache) + getExtensionForType(message.media.type);
        }

        return baseName(peer, message, cache) + getExtensionForMime(message.media.mime);
    }

    public static void openFile(Context context, File file, String mime) {
        if (context == null) {
            return;
        }

        if (file == null || !file.exists() || file.length() == 0) {
            Toast.makeText(context, R.string.file_actions_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        if (Integer.parseInt(android.os.Build.VERSION.SDK) >= 9) {
            Api9Helper.setReadable(file);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(file);

        String openMime = mime != null ? mime : "*/*";

        intent.setDataAndType(uri, openMime);

        try {
            Intent chooser = Intent.createChooser(intent, context.getString(R.string.file_actions_open_with));

            if (!(context instanceof Activity)) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, R.string.file_actions_no_app_to_open, Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean openIfSaved(Context context, String peer, Message message, boolean cache) {
        File file = findSavedFile(peer, message, cache);

        if (file == null) {
            return false;
        }

        String mime = null;
        if (message != null && message.media != null) {
            mime = message.media.mime;
        }

        openFile(context, file, mime);
        return true;
    }

    private static void downloadWithDialog(final Activity activity, final String peer, final Message message, final String filename, final boolean openAfter) {
        if (activity == null || activity.isFinishing()) {
            return;
        }

        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setTitle(R.string.file_actions_saving_title);
        dialog.setMessage(activity.getString(R.string.file_actions_saving_message, filename));
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setIndeterminate(true);
        dialog.setCancelable(true);
        dialog.setMax(100);

        dialog.setButton2(
                activity.getString(R.string.action_cancel),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        d.cancel();
                    }
                }
        );

        final FileDownloader.Handle[] handleHolder = new FileDownloader.Handle[1];
        final boolean[] cancelledBeforeHandle = new boolean[1];

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface d) {
                cancelledBeforeHandle[0] = true;

                if (handleHolder[0] != null) {
                    handleHolder[0].cancel();
                }
            }
        });

        dialog.show();

        handleHolder[0] = App.getFileDownloader().download(
                peer,
                String.valueOf(message.id),
                null,
                filename,
                false,
                new FileDownloader.FileCallback() {
                    public void onProgress(long downloaded, long total) {
                        if (activity.isFinishing() || !dialog.isShowing()) {
                            return;
                        }

                        long knownTotal = total;

                        if (knownTotal <= 0 && message.media != null) {
                            knownTotal = message.media.size;
                        }

                        if (knownTotal > 0) {
                            dialog.setIndeterminate(false);

                            int percent = (int) ((downloaded * 100L) / knownTotal);

                            if (percent < 0) {
                                percent = 0;
                            }

                            if (percent > 100) {
                                percent = 100;
                            }

                            dialog.setProgress(percent);
                        } else {
                            dialog.setIndeterminate(true);
                        }
                    }

                    public void onSuccess(File file) {
                        if (activity.isFinishing()) {
                            return;
                        }

                        dismissQuietly(dialog);

                        if (openAfter) {
                            String mime = null;

                            if (message.media != null) {
                                mime = message.media.mime;
                            }

                            openFile(activity, file, mime);
                        } else {
                            Toast.makeText(activity, activity.getString(R.string.file_actions_saved, file.getName()), Toast.LENGTH_SHORT).show();
                        }
                    }

                    public void onError(Exception error) {
                        if (activity.isFinishing()) {
                            return;
                        }

                        dismissQuietly(dialog);

                        String messageText = activity.getString(R.string.file_actions_download_failed);

                        if (error != null && error.getMessage() != null) {
                            messageText = activity.getString(R.string.file_actions_download_failed_with_message, error.getMessage());
                        }

                        Toast.makeText(activity, messageText, Toast.LENGTH_SHORT).show();
                    }

                    public void onCancelled() {
                        if (activity.isFinishing()) {
                            return;
                        }

                        dismissQuietly(dialog);
                        Toast.makeText(activity, R.string.file_actions_download_cancelled, Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (cancelledBeforeHandle[0] && handleHolder[0] != null) {
            handleHolder[0].cancel();
        }
    }

    private static boolean isDocument(Message message) {
        if (message == null || message.media == null) {
            return false;
        }

        if ("document".equals(message.media.type)) {
            return true;
        }

        return message.media.type == null && message.media.name != null;
    }

    private static String baseName(String peer, Message message, boolean cache) {
        return "dreamygram_" + (cache ? "c_" : "") + safePart(peer) + "_" + stableId(message);
    }

    private static String stableId(Message message) {
        if (message.id > 0) {
            return String.valueOf(message.id);
        }

        return "local_" + Math.abs(message.id) + "_" + message.date;
    }

    private static String safePart(String s) {
        if (s == null || s.length() == 0) {
            return "unknown";
        }

        String out = s.replaceAll("[^a-zA-Z0-9_-]", "_");

        if (out.length() > 32) {
            out = out.substring(0, 32);
        }

        if (out.length() == 0) {
            return "unknown";
        }

        return out;
    }

    private static String getExtensionForType(String type) {
        if (type == null) {
            return ".bin";
        }

        if ("photo".equals(type)) {
            return ".jpg";
        }

        if ("video".equals(type)) {
            return ".mp4";
        }

        if ("sticker".equals(type)) {
            return ".webp";
        }

        return ".bin";
    }

    private static String getExtensionForMime(String mime) {
        if (mime == null || mime.length() == 0) {
            return ".bin";
        }

        if (mime.startsWith("image/")) {
            String subtype = mime.substring(6);

            if ("jpeg".equals(subtype)) {
                return ".jpg";
            }

            return "." + subtype;
        }

        if (mime.startsWith("video/")) {
            String subtype = mime.substring(6);

            if ("quicktime".equals(subtype)) {
                return ".mov";
            }

            return "." + subtype;
        }

        if (mime.startsWith("audio/")) {
            String subtype = mime.substring(6);

            if ("mpeg".equals(subtype)) {
                return ".mp3";
            }

            return "." + subtype;
        }

        if ("application/pdf".equals(mime)) {
            return ".pdf";
        }

        if ("application/zip".equals(mime)) {
            return ".zip";
        }

        if ("text/plain".equals(mime)) {
            return ".txt";
        }

        return ".bin";
    }

    private static void dismissQuietly(ProgressDialog dialog) {
        try {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Exception ignored) { }
    }
}