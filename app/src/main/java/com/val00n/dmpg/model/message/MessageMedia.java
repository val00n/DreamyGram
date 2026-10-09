package com.val00n.dmpg.model.message;

import cc.nnproject.json.JSONObject;

public class MessageMedia {
    public String type;
    public boolean hide;

    public String id;
    public long date;

    // Document
    public String mime;

    // TODO
    // TODO type specific fields
    // TODO

    public MessageMedia(JSONObject json) {
        if (json == null) return;

        type = json.getNullableString("type");

        if (json.has("hide") && json.getInt("hide") == 1) {
            hide = true;
        }

        if (json.has("mime")) {
            mime = json.getString("mime");

            if ((mime.equals("image/webp") || mime.equals("video/webm") || mime.equals("application/x-tgsticker"))) {
                type = "sticker"; // possible FIXME???
            }
        } else {
            mime = null;
        }
    }
}
