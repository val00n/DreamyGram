package com.val00n.dmpg.model.message;

import cc.nnproject.json.JSONObject;

public class MessageReply {

    public String replyId;
    public String peer;
    public String quote;
    public Message msg;
    public int top;

    public MessageReply(String placeholder) {
        quote = placeholder;
    }

    public MessageReply(JSONObject json) {
        if (json != null) {
            replyId = Integer.toString(json.getInt("id"));
            peer = json.getNullableString("peer");
            quote = json.getNullableString("quote");

            if (json.has("msg")) {
                msg = new Message(json.getObject("msg"));
            }

            if (json.has("top")) {
                top = json.getInt("top");
            }
        }
    }
}
