package com.val00n.dmpg.cache;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.val00n.dmpg.model.CacheFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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
                        "size INTEGER NOT NULL DEFAULT 0, " +
                        "isAvatar INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt INTEGER NOT NULL DEFAULT 0, " +
                        "lastUsed INTEGER NOT NULL DEFAULT 0" +
                        ")";

        db.execSQL(createFilesTable);
        db.execSQL("CREATE INDEX idx_files_lookup ON files(peer, isAvatar)");

        String createChatsTable =
                "CREATE TABLE chats (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "peer TEXT NOT NULL, " +
                        "filename TEXT NOT NULL)";

        db.execSQL(createChatsTable);

        String createPendingTable =
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
            cursor = db.query("files", new String[]{"_id","filename","peer","size","isAvatar","createdAt","lastUsed"},
                    "peer = ? AND isAvatar = 1",
                    new String[]{peer}, null, null, "createdAt DESC", "1");

            if (cursor.moveToFirst()) {
                cacheFile.id = cursor.getLong(cursor.getColumnIndex("_id"));
                cacheFile.filename = cursor.getString(cursor.getColumnIndex("filename"));
                cacheFile.peer = cursor.getString(cursor.getColumnIndex("peer"));
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

    public void saveAvatar(CacheFile cacheFile) {
        SQLiteDatabase db = getWritableDatabase();

        CacheFile existing = findAvatar(cacheFile.peer);

        ContentValues values = new ContentValues();
        values.put("filename", cacheFile.filename);
        values.put("peer", cacheFile.peer);
        values.put("size", cacheFile.size);
        values.put("isAvatar", cacheFile.isAvatar);
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

    public void touch(long id, long lastUsed) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("lastUsed", lastUsed);

        String whereClause = "_id = ?";

        String[] whereArgs = new String[] { String.valueOf(id) };

        db.update("files", values, whereClause, whereArgs);
    }

    public void deleteAvatar(String peer) {
        getWritableDatabase().delete("files", "peer = ? AND isAvatar = 1", new String[]{peer});
    }

    public long getTotalFilesSize(boolean avatarsOnly) {
        Cursor cursor = null;
        try {
            cursor = getReadableDatabase().rawQuery(
                    "SELECT COALESCE(SUM(size), 0) FROM files" + (avatarsOnly ? " WHERE isAvatar = 1" : ""),
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

    public CacheFile getOldestAvatarFile() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query("files",
                    new String[]{"_id", "filename", "size"},
                    "isAvatar = 1", null, null, null, "lastUsed ASC", "1");
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

    public void deleteFileById(long id) {
        getWritableDatabase().delete("files", "_id = ?", new String[]{String.valueOf(id)});
    }

    public void clearFiles() {
        getWritableDatabase().delete("files", null, null);
    }
}
