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
    //  public Markup markup; // TODO
    public long group; // TODO
    public long edit; // TODO
    public boolean silent; // TODO
    public boolean mention; // TODO
    //  public Comments comments; // TODO
    //  public Reacts reacts; // TODO

    public boolean dateSeparator; // ugly. THERE IS NO DATE SEPARATOR FLAG IN API
    public MessageState messageState; // same shit

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

            if (json.has("fwd")) {
                fwd = new MessageForward(json.getObject("fwd"));
            }

            if (json.has("reply")) {
                reply = new MessageReply(json.getObject("reply"));
            }

            if (json.has("media")) {
                media = new MessageMedia(json.getObject("media"));
            } else if (json.has("act")) {
                act = new Act(json.getObject("act"));
            }

            messageState = MessageState.MESSAGE_SENT;
        }
    }

    public static Message dateSeparator(long seconds) {
        Message m = new Message(null);
        m.date = seconds;
        m.dateSeparator = true;
        return m;
    }
}

