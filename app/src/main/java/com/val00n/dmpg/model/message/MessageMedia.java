package com.val00n.dmpg.model.message;

import cc.nnproject.json.JSONObject;

public class MessageMedia {
    public String type;
    public boolean hide;
    public String id;
    public long date;

    // photo / video
    public int width;
    public int height;

    // document
    public String mime;
    public String name;
    public long size;
    public boolean thumb;
    public boolean audio;
    public boolean voice;
    public boolean wave;
    public int time;
    public String title;
    public String artist;

    public MessageMedia(JSONObject json) {
        if (json == null) return;
        type = json.getNullableString("type");
        if (json.has("hide") && json.getInt("hide") == 1) {
            hide = true;
        }
        if (type == null) return;

        switch (type) {
            case "photo":
                id = json.getNullableString("id");
                date = json.getLong("date", -1);
                width = json.getInt("w", -1);
                height = json.getInt("h", -1);
                break;

            case "document":
                id = json.getNullableString("id");
                date = json.getLong("date", -1);
                size = json.getLong("size", 0);
                mime = json.getNullableString("mime");
                name = json.getNullableString("name");
                thumb = json.getBoolean("thumb", false);
                audio = json.getBoolean("audio", false);
                voice = json.getBoolean("voice", false);
                wave = json.getBoolean("wave", false);
                time = json.getInt("time", 0);
                title = json.getNullableString("title");
                artist = json.getNullableString("artist");
                width = json.getInt("w", -1);
                height = json.getInt("h", -1);

                if (voice) {
                    type = "voice";
                } else if (audio) {
                    type = "audio";
                } else if (mime != null && mime.startsWith("video/")) {
                    type = "video"; // ultra shitty, but it works
                } else if (mime != null && (mime.equals("image/webp")
                        || mime.equals("video/webm")
                        || mime.equals("application/x-tgsticker"))) {
                    type = "sticker";
                } else {
                    type = "document";
                }
                break;
        }

        if (json.has("hide") && json.getInt("hide") == 1) {
            hide = true;
        }
    }

    public MessageMedia() { }

    public boolean isVisual() {
        return "photo".equals(type) || "video".equals(type) || "sticker".equals(type);
    }

    public boolean isPlayable() {
        return "video".equals(type) || "audio".equals(type) || "voice".equals(type);
    }
}
