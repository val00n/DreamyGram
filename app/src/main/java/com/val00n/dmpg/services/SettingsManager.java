package com.val00n.dmpg.services;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {

    private static final String PREFS_NAME = "app_settings";

    private static final String KEY_DIALOGS_COUNT = "dialogs_count";
    private static final String KEY_MESSAGES_COUNT = "messages_count";
    private static final String KEY_DIALOG_POLL_SECS = "dialog_poll_secs";
    private static final String KEY_MESSAGES_POLL_SECS = "messages_poll_secs";
    private static final String KEY_LOAD_AVATARS = "load_avatars";
    private static final String KEY_LOAD_MEDIA = "load_media";
    private static final String KEY_DIALOG_POLL_ENABLED = "dialog_poll_enabled";
    private static final String KEY_CHAT_POLL_ENABLED = "chat_poll_enabled";

    private static final int DEFAULT_DIALOGS_COUNT = 25;
    private static final int DEFAULT_MESSAGES_COUNT = 50;
    private static final int DEFAULT_DIALOG_POLL_SECS = 3;
    private static final int DEFAULT_MESSAGES_POLL_SECS = 3;
    private static final boolean DEFAULT_LOAD_AVATARS = true;
    private static final boolean DEFAULT_LOAD_MEDIA = true;
    private static final boolean DEFAULT_DIALOG_POLL_ENABLED = true;
    private static final boolean DEFAULT_CHAT_POLL_ENABLED = true;

    private final SharedPreferences prefs;

    private volatile boolean loadAvatars;
    private volatile boolean loadMedia;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadAvatars = prefs.getBoolean(KEY_LOAD_AVATARS, DEFAULT_LOAD_AVATARS);
        loadMedia = prefs.getBoolean(KEY_LOAD_MEDIA, DEFAULT_LOAD_MEDIA);
    }

    public boolean getLoadAvatars() {
        return loadAvatars;
    }

    public void setLoadAvatars(boolean value) {
        loadAvatars = value;
        prefs.edit().putBoolean(KEY_LOAD_AVATARS, value).commit();
    }

    public boolean getLoadMedia() {
        return loadMedia;
    }

    public void setLoadMedia(boolean value) {
        loadMedia = value;
        prefs.edit().putBoolean(KEY_LOAD_MEDIA, value).commit();
    }

    public int getDialogsCount() {
        return prefs.getInt(KEY_DIALOGS_COUNT, DEFAULT_DIALOGS_COUNT);
    }

    public void setDialogsCount(int value) {
        prefs.edit().putInt(KEY_DIALOGS_COUNT, value).commit();
    }

    public int getMessagesCount() {
        return prefs.getInt(KEY_MESSAGES_COUNT, DEFAULT_MESSAGES_COUNT);
    }

    public void setMessagesCount(int value) {
        prefs.edit().putInt(KEY_MESSAGES_COUNT, value).commit();
    }

    public int getDialogPollMillis() {
        return prefs.getInt(KEY_DIALOG_POLL_SECS, DEFAULT_DIALOG_POLL_SECS) * 1000;
    }

    public void setDialogPollSecs(int secs) {
        prefs.edit().putInt(KEY_DIALOG_POLL_SECS, secs).commit();
    }

    public int getMessagesPollMillis() {
        return prefs.getInt(KEY_MESSAGES_POLL_SECS, DEFAULT_MESSAGES_POLL_SECS) * 1000;
    }

    public void setMessagesPollSecs(int secs) {
        prefs.edit().putInt(KEY_MESSAGES_POLL_SECS, secs).commit();
    }

    public boolean isDialogPollEnabled() {
        return prefs.getBoolean(KEY_DIALOG_POLL_ENABLED, DEFAULT_DIALOG_POLL_ENABLED);
    }

    public void setDialogPollEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_DIALOG_POLL_ENABLED, value).commit();
    }

    public boolean isChatPollEnabled() {
        return prefs.getBoolean(KEY_CHAT_POLL_ENABLED, DEFAULT_CHAT_POLL_ENABLED);
    }

    public void setChatPollEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_CHAT_POLL_ENABLED, value).commit();
    }
}