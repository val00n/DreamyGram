package com.val00n.dmpg.model.message;

import cc.nnproject.json.JSONObject;

public class MessageForward {

    public String fromId;
    public long date;
    public int msgId;
    public String peer;
    public String fromName;

    public MessageForward(JSONObject json) {
        if (json != null) {
            fromId = json.getNullableString("from_id");
            peer = json.getNullableString("peer");
            fromName = json.getNullableString("from_name");

            if (json.has("date")) {
                date = json.getLong("date");
            }

            if (json.has("msg")) {
                msgId = json.getInt("msg");
            }
        }
    }
}

