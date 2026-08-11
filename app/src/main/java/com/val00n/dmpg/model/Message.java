package com.val00n.dmpg.model;

import cc.nnproject.json.JSONObject;

public class Message {
    public String id;
    public long date;
    public String text;
    public boolean out; // outbox
    public String peer_id;
    public String from_id;
    //  public Forward fwd; // TODO
    public MessageMedia media;
    public Act act;
    //  public Reply reply; // TODO
    //  public Markup markup; // TODO
    public long group; // TODO
    public long edit; // TODO
    public boolean silent; // TODO
    public boolean mention; // TODO
    //  public Comments comments; // TODO
    //  public Reacts reacts; // TODO

    public Message(JSONObject json) {
        id = json.getString("id");
        date = json.getLong("date", 0);

        if (json.has("text")) {
            text = json.getString("text");
        }

        if (json.has("out")) {
            out = json.getBoolean("out");
        }

        if (json.has("peer_id")) {
            peer_id = json.getString("peer_id");
        }

        if (json.has("from_id")) {
            from_id = json.getString("from_id");
        }

        if (json.has("media")) {
            media = new MessageMedia(json.getObject("media"));
        } else if (json.has("act")) {
            act = new Act(json.getObject("act"));
        }
    }
}

class MessageMedia {
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
        }
    }
}

class Act {
    public String action;
    public String user; // TODO
    public String t; // TODO

    public Act(JSONObject json) {
        action = json.getString("_", "unknown action");

        if (json.has("user")) {
            user = json.getNullableString("user");
        }

        if (json.has("t")) {
            t = json.getNullableString("t");
        }
    }
}
