package com.val00n.dmpg.services;

import android.net.Uri;
import android.os.Environment;
import com.val00n.dmpg.App;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class FileHelper {

    private final String MEDIA_DIRECTORY_NAME = "/dmpg/";
    private final String APPDATA_DIRECTORY_NAME = "/dmpgAppData/";

    public boolean saveFile(String fileName, String content, boolean isAppData) {
        if (isAppData) {
            return saveFile(fileName, content, getAppDataDirectoryPath());
        } else {
            return saveFile(fileName, content, getMediaDirectoryPath());
        }
    }

    public boolean saveFile(String fileName, String content, String directory) {
        File folder = new File(directory);
        File file = new File(folder, fileName);
        String fullFileText = content;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        OutputStreamWriter outputStreamWriter = null;
        try {
            outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            outputStreamWriter.write(fullFileText);
            outputStreamWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (outputStreamWriter != null) {
                try { outputStreamWriter.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        return true;
    }

    public String loadFile(String fileName, boolean isAppData) {
        if (isAppData) {
            return loadFile(fileName, getAppDataDirectoryPath());
        } else {
            return loadFile(fileName, getMediaDirectoryPath());
        }
    }

    public String loadFile(String fileName, String directory) {
        File file = new File(directory, fileName);
        String fullFileText = null;

        FileInputStream input = null;
        BufferedReader reader = null;
        InputStreamReader inputStreamReader = null;
        try {
            input = new FileInputStream(file);
            inputStreamReader = new InputStreamReader(input, "UTF-8");

            reader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }

            fullFileText = stringBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException e) { e.printStackTrace(); }
            }
        }

        return fullFileText;
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