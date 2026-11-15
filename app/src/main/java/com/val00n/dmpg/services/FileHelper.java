package com.val00n.dmpg.services;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import com.val00n.dmpg.App;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import static android.content.ContentValues.TAG;

public class FileHelper {

    private static final String CHARSET_NAME = "UTF-8";
    private static final int BUFFER_SIZE = 2048;
    private static final String MEDIA_DIRECTORY_NAME = "/DreamyGram/";
    private static final String APPDATA_DIRECTORY_NAME = "/dmpgAppData/";

    public boolean saveFileText(String fileName, String content, boolean isAppData) {
        if (content == null) return false;

        try {
            return saveFileBytes(fileName, content.getBytes(CHARSET_NAME), isAppData);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getFullAppdataPath(String fileName) {
        return new File(getAppDataDirectoryPath(), fileName).getAbsolutePath();
    }

    public boolean saveFileText(String fileName, String content, String directory) {
        if (content == null) return false;

        try {
            return saveFileBytes(fileName, content.getBytes(CHARSET_NAME), directory);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String loadFileText(String fileName, boolean isAppData) {
        byte[] data = loadFileBytes(fileName, isAppData);
        try {
            return data == null ? null : new String(data, CHARSET_NAME);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String loadFileText(String fileName, String directory) {
        try {
            byte[] data = loadFileBytes(fileName, directory);
            return data == null ? null : new String(data, CHARSET_NAME);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean saveFileBytes(String fileName, byte[] content, boolean isAppData) {
        if (content == null) return false;

        try {
            if (isAppData) {
                return saveFileBytes(fileName, content, getAppDataDirectoryPath());
            } else {
                return saveFileBytes(fileName, content, getMediaDirectoryPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean saveFileBytes(String fileName, byte[] content, String directory) {
        if (content == null || fileName == null || directory == null) return false;

        File folder = new File(directory);
        File finalFile = new File(folder, fileName);
        File parent = finalFile.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) return false;

        File tempFile = new File(parent, finalFile.getName() + "." + System.nanoTime() + ".tmp");
        FileOutputStream stream = null;

        try {
            stream = new FileOutputStream(tempFile);
            stream.write(content);
            stream.flush();
            stream.getFD().sync();
        } catch (IOException e) {
            e.printStackTrace();
            if (tempFile.exists()) tempFile.delete();
            return false;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        if (!tempFile.renameTo(finalFile)) {
            tempFile.delete();
            return false;
        }

        return true;
    }


    public byte[] loadFileBytes(String fileName, boolean isAppData) {
        if (isAppData) {
            return loadFileBytes(fileName, getAppDataDirectoryPath());
        } else {
            return loadFileBytes(fileName, getMediaDirectoryPath());
        }
    }

    public byte[] loadFileBytes(String fileName, String directory) {
        File file = new File(directory, fileName);

        if (!file.exists()) {
            return null;
        }

        FileInputStream fis = null;
        ByteArrayOutputStream bos = null;
        try {
            fis = new FileInputStream(file);
            bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean deleteFile(String fileName, boolean isAppData) {
        if (isAppData) {
            return deleteFile(fileName, getAppDataDirectoryPath());
        } else {
            return deleteFile(fileName, getMediaDirectoryPath());
        }
    }

    public boolean deleteFile(String fileName, String directory) {
        File file = new File(directory, fileName);
        if (file.exists()) {
            return file.delete();
        }

        return false;
    }

    public static Uri getFileUri(String fileName, boolean isAppData) {
        if (isAppData) {
            return getFileUri(fileName, getAppDataDirectoryPath());
        } else {
            return getFileUri(fileName, getMediaDirectoryPath());
        }
    }

    public static Uri getFileUri(String fineName, String directory) {
        File folder = new File(directory);
        File file = new File(folder, fineName);
        return Uri.fromFile(file);
    }

    public static boolean isFileExist(String fileName, boolean isAppData) {
        if (isAppData) {
            return isFileExist(fileName, getAppDataDirectoryPath());
        } else {
            return isFileExist(fileName, getMediaDirectoryPath());
        }
    }

    public static boolean isFileExist(String fileName, String directory) {
        File file = new File(directory, fileName);
        if (file.exists()) {
            Log.d(TAG, "isFileExist: SEARCH FOR (FOUND): " + file.getAbsolutePath());
            return true;
        }
        Log.d(TAG, "isFileExist: SEARCH FOR (NOT FOUND): " + file.getAbsolutePath());
        return false;
    }

    public static File getCachedFile(String filename, boolean isAppData) {
        if (isAppData) {
            return getCachedFile(filename, getAppDataDirectoryPath());
        } else {
            return getCachedFile(filename, getMediaDirectoryPath());
        }
    }

    public static File getCachedFile(String filename, String directory) {
        File file = new File(directory, filename);
        if (file.exists()) {
            return file;
        }
        return null;
    }

    public static String sanitizeFileName(String name) {
        if (name == null) {
            return null;
        }

        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }

        slash = name.lastIndexOf('\\');
        if (slash >= 0 && slash + 1 < name.length()) {
            name = name.substring(slash + 1);
        }

        name = name.trim();

        if (name.length() == 0) {
            return null;
        }

        return name;
    }

    public File createTempFileFor(String finalName, boolean isAppData) throws IOException {
        String safeName = sanitizeFileName(finalName);
        if (safeName == null) {
            safeName = "file";
        }

        String dirPath = isAppData ? getAppDataDirectoryPath() : getMediaDirectoryPath();
        File dir = new File(dirPath);

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory");
        }

        return new File(dir, safeName + "." + System.nanoTime() + ".tmp");
    }

    public File finishDownload(File tempFile, String finalName, boolean isAppData) throws IOException {
        if (tempFile == null || !tempFile.exists()) {
            throw new IOException("Temp file does not exist");
        }

        String safeName = sanitizeFileName(finalName);
        if (safeName == null) {
            safeName = "file";
        }

        String dirPath = isAppData ? getAppDataDirectoryPath() : getMediaDirectoryPath();
        File dir = new File(dirPath);

        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create directory");
        }

        File finalFile = new File(dir, safeName);

        if (finalFile.exists() && !finalFile.delete()) {
            throw new IOException("Cannot delete old file");
        }

        if (!tempFile.renameTo(finalFile)) {
            if (!copyFile(tempFile, finalFile)) {
                tempFile.delete();
                throw new IOException("Cannot move temp file");
            }

            tempFile.delete();
        }

        Log.d(TAG, "finishDownload: SAVED: " + finalFile.getAbsolutePath());

        return finalFile;
    }

    public boolean copyFile(File src, File dst) throws IOException {
        if (src == null || !src.exists()) {
            return false;
        }

        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        FileInputStream in = null;
        FileOutputStream out = null;

        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);

            byte[] buffer = new byte[8192];
            int read;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            out.flush();
            out.getFD().sync();

            return true;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) { }
            }

            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) { }
            }
        }
    }

    public static String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }

    public static String getAppDataDirectoryPath() { // hello gohoski :3
        return App.get().getFilesDir().getPath() + APPDATA_DIRECTORY_NAME;
    }

    public static String getMediaDirectoryPath() {
        String baseDir;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            baseDir = Environment.getExternalStorageDirectory().getPath();
        } else {
            baseDir = App.get().getFilesDir().getPath();
        }

        return baseDir + MEDIA_DIRECTORY_NAME;
    }
}