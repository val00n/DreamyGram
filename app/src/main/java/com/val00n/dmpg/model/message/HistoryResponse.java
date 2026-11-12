package com.val00n.dmpg.model.message;

import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class HistoryResponse {
    public int totalCount;
    public ArrayList<Message> messages;
    public Map<String, User> users;
    public Map<String, Chat> chats;
    public String offsetId; // last msg id (not user)
    public long offsetDate; // last msg timestamp

    public HistoryResponse(JSONObject json) {
        totalCount = json.getInt("count", 0);

        JSONObject rawUsers = json.getNullableObject("users");

        if (rawUsers != null) {
            users = new HashMap<String, User>();
            JSONArray userKeys = rawUsers.keysAsArray();

            for (int i = 0; i < userKeys.size(); i++) {
                String id = userKeys.getString(i);
                users.put(id, new User(rawUsers.getNullableObject(id)));
            }
        }

        JSONObject rawChats = json.getNullableObject("chats");

        if (rawChats != null) {
            chats = new HashMap<String, Chat>();
            JSONArray chatKeys = rawChats.keysAsArray();

            for (int i = 0; i < chatKeys.size(); i++) {
                String id = chatKeys.getString(i);
                chats.put(id, new Chat(rawChats.getNullableObject(id)));
            }
        }

        messages = new ArrayList<>();

        if (json.has("messages")) {
            JSONArray rawMessages = json.getArray("messages");

            for (int i = 0; i < rawMessages.size(); i++) {
                messages.add(new Message(rawMessages.getObject(i)));
            }
        }

        if (messages != null && !messages.isEmpty()) {
            Message lastMsg = messages.get(messages.size() - 1);
            if (lastMsg.id != 0) {
                offsetId = String.valueOf(lastMsg.id);
            }

            if (lastMsg.date > 0) {
                offsetDate = lastMsg.date;
            }
        }
    }

    public void add(HistoryResponse result) {
        messages.addAll(0, result.messages);
        offsetId = result.offsetId;
        offsetDate = result.offsetDate;

        if (result.users != null) {
            if (users == null) {
                users = new HashMap<>();
            }

            users.putAll(result.users);
        }

        if (result.chats != null) {
            if (chats == null) {
                chats = new HashMap<>();
            }

            chats.putAll(result.chats);
        }

    }
}
