package com.val00n.dmpg.cache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.val00n.dmpg.model.cache.CacheFile;
import com.val00n.dmpg.model.cache.CacheHistory;
import com.val00n.dmpg.services.FileHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CacheDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "cache.db";
    private static final int DATABASE_VERSION = 1;

    public CacheDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createFilesTable =
                "CREATE TABLE files (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "filename TEXT NOT NULL, " +
                        "peer TEXT NOT NULL, " +
                        "messageId TEXT, " +
                        "size INTEGER NOT NULL DEFAULT 0, " +
                        "isAvatar INTEGER NOT NULL DEFAULT 0, " +
                        "preview INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "lastUsed INTEGER NOT NULL DEFAULT 0" +
                        ")";

        db.execSQL(createFilesTable);
        db.execSQL("CREATE INDEX idx_files_lookup ON files(peer, isAvatar)");
        db.execSQL("CREATE INDEX idx_files_message ON files(peer, messageId, isAvatar, preview)");

        String createChatsTable =
                "CREATE TABLE chats (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "peer TEXT NOT NULL, " +
                        "lastUsed INTEGER NOT NULL DEFAULT 0, " +
                        "size INTEGER NOT NULL DEFAULT 0, " +
                        "filename TEXT NOT NULL)";

        db.execSQL(createChatsTable);

        String createPendingTable = // TODO
                "CREATE TABLE pending (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "peer TEXT NOT NULL, " +
                        "filename TEXT NOT NULL)";

        db.execSQL(createPendingTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS files");
        db.execSQL("DROP TABLE IF EXISTS chats");
        db.execSQL("DROP TABLE IF EXISTS pending");
        onCreate(db);
    }

    public CacheFile findAvatar(String peer) {
        SQLiteDatabase db = getReadableDatabase();

        CacheFile cacheFile = new CacheFile();
        Cursor cursor = null;
        try {
            cursor = db.query("files", new String[]{"_id","filename","peer","messageId","size","isAvatar","createdAt","lastUsed"},
                    "peer = ? AND isAvatar = 1",
                    new String[]{peer}, null, null, "createdAt DESC", "1");

            if (cursor.moveToFirst()) {
                cacheFile.id = cursor.getLong(cursor.getColumnIndex("_id"));
                cacheFile.filename = cursor.getString(cursor.getColumnIndex("filename"));
                cacheFile.peer = cursor.getString(cursor.getColumnIndex("peer"));
                cacheFile.messageId = cursor.getString(cursor.getColumnIndex("messageId"));
                cacheFile.size = cursor.getLong(cursor.getColumnIndex("size"));
                cacheFile.isAvatar = cursor.getInt(cursor.getColumnIndex("isAvatar")) > 0;
                cacheFile.createdAt = cursor.getLong(cursor.getColumnIndex("createdAt"));
                cacheFile.lastUsed = cursor.getLong(cursor.getColumnIndex("lastUsed"));

                return cacheFile;
            }

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public CacheFile findFile(String peer, String messageId, boolean preview) {
        SQLiteDatabase db = getReadableDatabase();

        CacheFile cacheFile = new CacheFile();
        Cursor cursor = null;
        try {
            cursor = db.query("files", new String[]{"_id","filename","peer","messageId","size","isAvatar","createdAt","lastUsed"},
                    "peer = ? AND messageId = ? AND isAvatar = 0 AND preview = ?",
                    new String[]{peer, messageId, preview ? "1" : "0"}, null, null, "createdAt DESC", "1");

            if (cursor.moveToFirst()) {
                cacheFile.id = cursor.getLong(cursor.getColumnIndex("_id"));
                cacheFile.filename = cursor.getString(cursor.getColumnIndex("filename"));
                cacheFile.peer = cursor.getString(cursor.getColumnIndex("peer"));
                cacheFile.messageId = cursor.getString(cursor.getColumnIndex("messageId"));
                cacheFile.size = cursor.getLong(cursor.getColumnIndex("size"));
                cacheFile.isAvatar = cursor.getInt(cursor.getColumnIndex("isAvatar")) > 0;
                cacheFile.createdAt = cursor.getLong(cursor.getColumnIndex("createdAt"));
                cacheFile.lastUsed = cursor.getLong(cursor.getColumnIndex("lastUsed"));

                return cacheFile;
            }

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public CacheHistory findHistory(String peer) {
        SQLiteDatabase db = getReadableDatabase();

        Cursor cursor = null;
        try {
            cursor = db.query("chats", new String[]{"_id", "peer", "filename", "size", "lastUsed"},
                    "peer = ?",
                    new String[]{peer}, null, null, null, "1");

            if (cursor.moveToFirst()) {
                CacheHistory cacheHistory = new CacheHistory();
                cacheHistory.id = cursor.getLong(cursor.getColumnIndex("_id"));
                cacheHistory.filename = cursor.getString(cursor.getColumnIndex("filename"));
                cacheHistory.size = cursor.getLong(cursor.getColumnIndex("size"));
                cacheHistory.lastUsed = cursor.getLong(cursor.getColumnIndex("lastUsed"));
                return cacheHistory;
            }

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void saveHistory(String peer, String filename, long lastUsed) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("peer", peer);
        values.put("filename", filename);
        values.put("lastUsed", lastUsed);

        File file = new File(FileHelper.getAppDataDirectoryPath(), filename);
        values.put("size", file.exists() ? file.length() : 0);

        CacheHistory existing = findHistory(peer);
        if (existing != null) {
            String whereClause = "peer = ?";
            String[] whereArgs = new String[] { peer };

            db.update("chats", values, whereClause, whereArgs);
        } else {
            db.insert("chats", null, values);
        }
    }

    public void saveFile(CacheFile cacheFile) {
        SQLiteDatabase db = getWritableDatabase();

        CacheFile existing;
        if (cacheFile.isAvatar) {
            existing = findAvatar(cacheFile.peer);
        } else {
            existing = findFile(cacheFile.peer, cacheFile.messageId, cacheFile.preview);
        }

        ContentValues values = new ContentValues();
        values.put("filename", cacheFile.filename);
        values.put("peer", cacheFile.peer);
        values.put("messageId", cacheFile.messageId);
        values.put("size", cacheFile.size);
        values.put("isAvatar", cacheFile.isAvatar);
        values.put("preview", cacheFile.preview);
        values.put("createdAt", cacheFile.createdAt);
        values.put("lastUsed", cacheFile.lastUsed);

        if (existing != null) {
            cacheFile.id = existing.id;

            String whereClause = "_id = ?";

            String[] whereArgs = new String[] { String.valueOf(existing.id) };

            db.update("files", values, whereClause, whereArgs);
        } else {
            cacheFile.id = db.insert("files", null, values);
        }
    }

    public void touch(long id, long lastUsed, boolean chats) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("lastUsed", lastUsed);

        String whereClause = "_id = ?";

        String[] whereArgs = new String[] { String.valueOf(id) };

        db.update(chats ? "chats" : "files", values, whereClause, whereArgs);
    }

    public void deleteAvatar(String peer) {
        getWritableDatabase().delete("files", "peer = ? AND isAvatar = 1", new String[]{peer});
    }

    public long getFilesSize(boolean avatars) {
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery(
                    "SELECT COALESCE(SUM(size), 0) FROM files" + (avatars ? " WHERE isAvatar = 1" : " WHERE isAvatar = 0"),
                    null
            );

            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }

            return 0L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public long getHistorySize() {
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery(
                    "SELECT COALESCE(SUM(size), 0) FROM chats",
                    null
            );

            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }

            return 0L;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public CacheHistory getOldestHistory() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query("chats",
                    new String[]{"_id", "peer", "filename", "size", "lastUsed"},
                    null, null, null, null, "lastUsed ASC", "1");
            if (cursor.moveToFirst()) {
                CacheHistory cacheHistory = new CacheHistory();
                cacheHistory.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                cacheHistory.peer = cursor.getString(cursor.getColumnIndexOrThrow("peer"));
                cacheHistory.filename = cursor.getString(cursor.getColumnIndexOrThrow("filename"));
                cacheHistory.size = cursor.getLong(cursor.getColumnIndexOrThrow("size"));
                cacheHistory.lastUsed = cursor.getLong(cursor.getColumnIndexOrThrow("lastUsed"));
                return cacheHistory;
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public CacheFile getOldestFile(boolean avatars) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query("files",
                    new String[]{"_id", "filename", "size"},
                    "isAvatar = " + (avatars ? "1" : "0"), null, null, null, "lastUsed ASC", "1");
            if (cursor.moveToFirst()) {
                CacheFile cacheFile = new CacheFile();
                cacheFile.id = cursor.getLong(cursor.getColumnIndex("_id"));
                cacheFile.filename = cursor.getString(cursor.getColumnIndex("filename"));
                cacheFile.size = cursor.getLong(cursor.getColumnIndex("size"));
                return cacheFile;
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<String> getFileNames(boolean avatars) {
        List<String> names = new ArrayList<String>();
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().query(
                    "files",
                    new String[]{"filename"},
                    "isAvatar = ?",
                    new String[]{avatars ? "1" : "0"},
                    null, null, null
            );
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndex("filename"));
                if (name != null) {
                    names.add(name);
                }
            }
            return names;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void deleteFileById(long id) {
        getWritableDatabase().delete("files", "_id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteHistoryById(long id) {
        getWritableDatabase().delete("chats", "_id = ?", new String[]{String.valueOf(id)});
    }

    public void clearFiles(boolean avatars) {
        getWritableDatabase().delete("files", "isAvatar = ?", new String[]{avatars ? "1" : "0"});
    }

    public void clearHistory() {
        getWritableDatabase().delete("chats", null, null);
    }

    public void clearPending() {
        getWritableDatabase().delete("pending", null, null);
    }
}
