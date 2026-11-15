package com.val00n.dmpg.model;

import com.val00n.dmpg.model.message.Message;
import java.util.ArrayList;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class Update {
    public int update_id;
    public boolean exact;
    public int max_id;
    public String from_id;
    public String user_id;
    public String channel_id;
    public String chat_id;
    public String peer;
    public JSONObject action; // lame
    public JSONObject status; // lame
    public String type; // a.k.a "_"
    public Message msg;
    public ArrayList<String> messages;

    public Update(JSONObject json) {
        JSONObject u = json.getObject("update");
        update_id = json.getInt("update_id", -1);
        exact = json.getBoolean("exact", false);

        type = u.getNullableString("_");
        if (u.has("message")) {
            msg = new Message(u.getObject("message"));
        }

        if (u.has("max_id")) {
            max_id = u.getInt("max_id");
        }

        if (u.has("from_id")) {
            from_id = u.getString("from_id");
        }

        if (u.has("user_id")) {
            user_id = u.getString("user_id");
        }

        if (u.has("channel_id")) {
            channel_id = u.getString("channel_id");
        }

        if (u.has("chat_id")) {
            chat_id = u.getString("chat_id");
        }

        if (u.has("peer")) {
            peer = u.getString("peer");
        }

        if (u.has("action")) {
            action = u.getObject("action");
        }

        if (u.has("status")) {
            status = u.getObject("status");
        }

        if (u.has("messages")) {
            JSONArray a = u.getArray("messages");
            messages = new ArrayList<String>();

            for (int i = 0; i < a.size(); i++) {
                messages.add(a.getString(i));
            }
        }
    }

    public String getPeerId() {
        if (peer != null) return peer;
        if (channel_id != null) return channel_id;
        if (chat_id != null) return chat_id;
        if (user_id != null) return user_id;
        if (msg != null) return msg.peer_id;
        return null;
    }
}
