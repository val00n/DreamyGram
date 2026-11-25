package com.val00n.dmpg.model.dialog;

import com.val00n.dmpg.App;
import com.val00n.dmpg.R;
import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.message.Message;
import com.val00n.dmpg.utils.DateUtils;
import cc.nnproject.json.JSONObject;

public class Dialog {
    public String peerId;
    public boolean pin;
    public int unread;
    public int mentions;
    public Message msg;
    public int readIn;
    public int readOut;
    public boolean silent;

    public User user; // if chat != null, user is last messaged user
    public Chat chat;

    public boolean isTyping = false; // not in API, ew

    public Dialog(JSONObject json, User user, Chat chat) {
        if (json == null) return;

        peerId = json.getString("id");

        if (json.has("pin")) {
            pin = json.getBoolean("pin");
        }

        if (json.has("unread")) {
            unread = json.getInt("unread");
        }

        if (json.has("mentions")) {
            mentions = json.getInt("mentions");
        }

        if (json.has("read_out")) {
            readOut = json.getInt("read_out");
        }

        if (json.has("msg")) {
            msg = new Message(json.getObject("msg"));
        }

        this.user = user;
        this.chat = chat;
    }

    public String getDisplayName(String selfId) {
        if (selfId != null && selfId.equals(peerId)) return "Saved Messages";

        if (peerId.startsWith("-") && chat != null) {
            return chat.t;
        } else if (user != null) {
            return user.getDisplayName();
        } else {
            return null;
        }
    }

    public boolean hasAvatar() {
        if (peerId.startsWith("-") && chat != null) {
            return chat.p;
        } else if (user != null) {
            return user.p;
        } else {
            return false;
        }
    }

    public String getSubtitle() {
        if (msg == null) {
            return "";
        }

        if (msg.text != null && msg.text.length() > 0) {
            if (peerId.startsWith("-") && user != null) {
                return user.getDisplayName() + ": " + msg.text;
            } else {
                return msg.text;
            }
        }

        if (msg.media != null && msg.media.type != null) {
            if (msg.media.type.equals("photo")) {
                return App.get().getString(R.string.dialog_media_photo);
            } else if (msg.media.type.equals("sticker")) {
                return App.get().getString(R.string.dialog_media_sticker);
            } else if (msg.media.type.equals("document")) {
                return App.get().getString(R.string.dialog_media_attachment);
            } else if (msg.media.type.equals("webpage")) {
                return App.get().getString(R.string.dialog_media_link);
            } else if (msg.media.type.equals("geo")) {
                return App.get().getString(R.string.dialog_media_location);
            } else if (msg.media.type.equals("poll")) {
                return App.get().getString(R.string.dialog_media_poll);
            } else {
                return App.get().getString(R.string.dialog_media_attachment);
            }
        } else if (msg.act != null) {
            return msg.act.action.replaceAll("_", " ");
        }

        return "";
    }

    public String getDateTimeString() {
        if (msg != null) {
            return DateUtils.formatChatTime(msg.date, true, System.currentTimeMillis());
        }

        return App.get().getString(R.string.layout_time_placeholder);
    }
}
