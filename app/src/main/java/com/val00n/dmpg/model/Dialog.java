package com.val00n.dmpg.model;

import android.graphics.Bitmap;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import cc.nnproject.json.JSONObject;

public class Dialog {
    public String id;
    public boolean pin;
    public int unread;
    public int mentions;
    public Message msg;
    public int readIn;
    public int readOut;
    public boolean silent;

    public User user; // if chat != null, user is last messaged user
    public Chat chat;

    public Bitmap cachedAvatar; // FIXME

    public Dialog(JSONObject json, User user, Chat chat) {
        if (json == null) return;

        id = json.getString("id");

        if (json.has("pin")) {
            pin = json.getBoolean("pin");
        }

        if (json.has("unread")) {
            unread = json.getInt("unread");
        }

        if (json.has("mentions")) {
            mentions = json.getInt("mentions");
        }

        if (json.has("msg")) {
            msg = new Message(json.getObject("msg"));
        }

        this.user = user;
        this.chat = chat;
    }

    public String getDisplayName() {
        if (id.contains("-") && chat != null) {
            return chat.t;
        } else if (user != null) {
            return user.getDisplayName();
        } else {
            return null;
        }
    }

    public boolean hasAvatar() {
        if (id.contains("-") && chat != null) {
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
            if (id.contains("-") && user != null) {
                return user.getDisplayName() + ": " + msg.text;
            } else {
                return msg.text;
            }
        }

        if (msg.media != null && msg.media.type != null) {
            if (msg.media.type.equals("photo")) {
                return "Photo";
            } else if (msg.media.type.equals("sticker")) {
                return "Sticker";
            } else if (msg.media.type.equals("document")) {
                return "Attachment";
            } else if (msg.media.type.equals("webpage")) {
                return "Link";
            } else if (msg.media.type.equals("geo")) {
                return "Location";
            } else if (msg.media.type.equals("poll")) {
                return "Poll";
            } else {
                return "Attachment";
            }
        } else if (msg.act != null) {
            return msg.act.action.replaceAll("_", " ");
        }

        return "";
    }

    public String getDateTimeString() {
        if (msg != null && msg.date > 0) {
            Date date = new Date(msg.date * 1000);
            Date dateNow = new Date();

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            String dateStr = simpleDateFormat.format(date);
            String nowStr = simpleDateFormat.format(dateNow);

            if (dateStr.equals(nowStr)) {
                simpleDateFormat = new SimpleDateFormat("HH:mm");
            } else {
                simpleDateFormat = new SimpleDateFormat("MM.dd HH:mm");
            }

            return simpleDateFormat.format(date);
        }

        return "XX:XX";
    }

    public static final Comparator<Dialog> PINNED_FIRST = new Comparator<Dialog>() {
        @Override
        public int compare(Dialog a, Dialog b) {
            if (a.pin == b.pin) return 0;
            return a.pin ? -1 : 1;
        }
    };
}
