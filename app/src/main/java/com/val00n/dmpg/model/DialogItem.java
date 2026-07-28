package com.val00n.dmpg.model;

import android.graphics.Bitmap;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DialogItem {
    public String id;
    public String displayName;
    public String lastMessageText;
    public String lastMessageUser;
    public String lastMessageId;
    public String mediaType;
    public String userAction;
    public long lastMessageTimestamp; // unixTime
    public int unreadMessages;
    public int mentionsCount;
    public boolean isPinned;
    public boolean hasAvatar;
    public boolean isBot; // TODO
    public boolean isOnline; // TODO
    public boolean isChatOrChannel;
    public boolean isSticker;
    public Bitmap cachedAvatar; // HUGE FUCKING TODO!!!! DO GLOBAL CACHING OR SOME SHIT!!!

    public DialogItem() {
        this.unreadMessages = 0;
        this.mentionsCount = 0;
        this.isPinned = false;
        this.isBot = false;
        this.hasAvatar = false;
        this.isChatOrChannel = false;
        this.mediaType = null;
        this.cachedAvatar = null;
    }

    public String getSubtitle() {
        if (isSticker) {
            return "Sticker";
        }

        if (lastMessageText != null && lastMessageText.length() > 0) {
            if (id.contains("-") && lastMessageUser != null && lastMessageUser.length() > 0) {
                return lastMessageUser + ": " + (lastMessageText != null ? lastMessageText : "");
            } else {
                return lastMessageText;
            }
        }

        if (mediaType != null) {
            if (mediaType.equals("photo")) {
                return "Photo";
            } else if (mediaType.equals("document")) {
                return "Attachment";
            } else if (mediaType.equals("webpage")) {
                return "Link";
            } else if (mediaType.equals("geo")) {
                return "Location";
            } else if (mediaType.equals("poll")) {
                return "Poll";
            } else {
                return "Attachment";
            }
        }

        if (userAction != null && userAction.length() > 0) {
            return userAction.replaceAll("_", " ");
        }

        return "";
    }

    public String getDateTimeString() {
        if (lastMessageTimestamp > 0) {
            Date date = new Date(lastMessageTimestamp);
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

        return "XX:XX"; // FIXME
    }
}
