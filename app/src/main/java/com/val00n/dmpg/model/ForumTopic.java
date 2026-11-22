package com.val00n.dmpg.model;

import cc.nnproject.json.JSONObject;

public class ForumTopic {
    public int id;
    public boolean closed;
    public boolean pinned;
    public long date;
    public int top; // last message ID
    public int unread;
    public int readMaxId;
    public String title;

    public ForumTopic(JSONObject json) {
        if (json == null) return;

        id = json.getInt("id");
        closed = json.getBoolean("closed");
        pinned = json.getBoolean("pinned");
        date = json.getLong("date");
        top = json.getInt("top");
        unread = json.getInt("unread");
        readMaxId = json.getInt("read_max_id");

        if (json.has("title")) {
            title = json.getString("title");
        }
    }
}
