package com.val00n.dmpg.model.message;

// not actually related to API

import com.val00n.dmpg.ui.chat.MediaGroup;

public class MessageRow {
    public int viewType;
    public Message message;

    public boolean grouped;
    public String title;
    public String content;
    public String quoteText;
    public String forwardCaption;
    public String dateText;
    public int statusRes;

    public String peer;
    public String chatPeer;
    public String messageId;
    public boolean hasAvatar;
    public boolean hasMedia;
    public int mediaWidth;
    public int mediaHeight;
    public boolean thumb;
    public boolean isPhoto;
    public boolean isVideo;
    public boolean isSticker;
    public boolean isFile;
    public String mediaType;
    public String mediaMime;
    public MediaGroup mediaGroup;
}
