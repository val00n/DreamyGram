package com.val00n.dmpg.model.message;

import cc.nnproject.json.JSONObject;

public class Message {
    public int id;
    public long date;
    public String text;
    public boolean out; // outbox
    public String peer_id;
    public String from_id;
    public MessageForward fwd;
    public MessageMedia media;
    public Act act;
    public MessageReply reply;
    public int topicId;
    //  public Markup markup; // TODO
    public long group;
    public long edit; // TODO
    public boolean silent; // TODO
    public boolean mention; // TODO
    //  public Comments comments; // TODO
    //  public Reacts reacts; // TODO

    public MessageState messageState; // local state, not from API

    public enum MessageState {
        MESSAGE_SENDING,
        MESSAGE_SENT,
        MESSAGE_WAS_READ,
        MESSAGE_ERROR
    }

    public Message(String text, int id, String peer_id) {
        this.text = text;
        this.id = id;
        this.peer_id = peer_id;
        messageState = MessageState.MESSAGE_SENDING;
    }

    public Message(JSONObject json) {
        if (json != null) {
            id = json.getInt("id");
            date = json.getLong("date", 0);

            if (json.has("text")) {
                text = json.getString("text", "");
            } else {
                text = "";
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

            if (json.has("fwd")) {
                fwd = new MessageForward(json.getObject("fwd"));
            }

            if (json.has("reply")) {
                JSONObject replyJson = json.getObject("reply");
                if (replyJson.has("top")) {
                    topicId = replyJson.getInt("top");
                }
                reply = new MessageReply(replyJson);
            }

            if (json.has("media")) {
                media = new MessageMedia(json.getObject("media"));
            } else if (json.has("act")) {
                act = new Act(json.getObject("act"));
            }

            if (json.has("group")) {
                group = json.getLong("group");
            }

            messageState = MessageState.MESSAGE_SENT;
        }
    }
}

