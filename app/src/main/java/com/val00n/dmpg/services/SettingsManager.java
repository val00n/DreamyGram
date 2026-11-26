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
    private static final String KEY_CHAT_STYLE = "chat_style";

    public static final String CHAT_STYLE_DEFAULT = "d";
    public static final String CHAT_STYLE_COMPACT = "c";
    public static final String CHAT_STYLE_MODERN = "m";

    private static final int DEFAULT_DIALOGS_COUNT = 25;
    private static final int DEFAULT_MESSAGES_COUNT = 30;
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

    public String getChatStyle() {
        String style = prefs.getString(KEY_CHAT_STYLE, CHAT_STYLE_DEFAULT);
        if (!CHAT_STYLE_DEFAULT.equals(style)
                && !CHAT_STYLE_COMPACT.equals(style)
                && !CHAT_STYLE_MODERN.equals(style)) {
            return CHAT_STYLE_DEFAULT;
        }
        return style;
    }

    public int getDialogsCount() {
        int val = prefs.getInt(KEY_DIALOGS_COUNT, DEFAULT_DIALOGS_COUNT);
        return val < 1 ? DEFAULT_DIALOGS_COUNT : val;
    }

    public void setDialogsCount(int value) {
        if (value < 1) value = 1;
        prefs.edit().putInt(KEY_DIALOGS_COUNT, value).commit();
    }

    public int getMessagesCount() {
        int val = prefs.getInt(KEY_MESSAGES_COUNT, DEFAULT_MESSAGES_COUNT);
        return val < 1 ? DEFAULT_MESSAGES_COUNT : val;
    }

    public void setMessagesCount(int value) {
        if (value < 1) value = 1;
        prefs.edit().putInt(KEY_MESSAGES_COUNT, value).commit();
    }

    public int getDialogPollMillis() {
        int secs = prefs.getInt(KEY_DIALOG_POLL_SECS, DEFAULT_DIALOG_POLL_SECS);
        if (secs < 1) secs = DEFAULT_DIALOG_POLL_SECS;
        return secs * 1000;
    }

    public void setDialogPollSecs(int secs) {
        if (secs < 1) secs = 1;
        prefs.edit().putInt(KEY_DIALOG_POLL_SECS, secs).commit();
    }

    public int getMessagesPollMillis() {
        int secs = prefs.getInt(KEY_MESSAGES_POLL_SECS, DEFAULT_MESSAGES_POLL_SECS);
        if (secs < 1) secs = DEFAULT_MESSAGES_POLL_SECS;
        return secs * 1000;
    }

    public void setMessagesPollSecs(int secs) {
        if (secs < 1) secs = 1;
        prefs.edit().putInt(KEY_MESSAGES_POLL_SECS, secs).commit();
    }

    public void setChatStyle(String style) {
        if (!CHAT_STYLE_DEFAULT.equals(style)
                && !CHAT_STYLE_COMPACT.equals(style)
                && !CHAT_STYLE_MODERN.equals(style)) {
            style = CHAT_STYLE_DEFAULT;
        }
        prefs.edit().putString(KEY_CHAT_STYLE, style).commit();
    }
}