package com.val00n.dmpg.services;

import android.net.Uri;
import android.os.Environment;
import com.val00n.dmpg.App;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileHelper {

    private static final String CHARSET_NAME = "UTF-8";
    private static final int BUFFER_SIZE = 2048;
    private static final String MEDIA_DIRECTORY_NAME = "/dmpg/";
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

    public Uri getFileUri(String fileName, boolean isAppData) {
        if (isAppData) {
            return getFileUri(fileName, getAppDataDirectoryPath());
        } else {
            return getFileUri(fileName, getMediaDirectoryPath());
        }
    }

    public Uri getFileUri(String fineName, String directory) {
        File folder = new File(directory);
        File file = new File(folder, fineName);
        return Uri.fromFile(file);
    }

    public boolean isFileExist(String fileName, boolean isAppData) {
        if (isAppData) {
            return isFileExist(fileName, getAppDataDirectoryPath());
        } else {
            return isFileExist(fileName, getMediaDirectoryPath());
        }
    }

    public boolean isFileExist(String fileName, String directory) {
        File file = new File(directory, fileName);
        if (file.exists()) {
            return true;
        }
        return false;
    }

    private String getAppDataDirectoryPath() { // hello gohoski :3
        return App.get().getFilesDir().getPath() + APPDATA_DIRECTORY_NAME;
    }

    private String getMediaDirectoryPath() {
        String baseDir;

        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            baseDir = Environment.getExternalStorageDirectory().getPath();
        } else {
            baseDir = App.get().getFilesDir().getPath();
        }

        return baseDir + MEDIA_DIRECTORY_NAME;
    }
}