package com.val00n.dmpg.model;

import java.util.ArrayList;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class ForumTopicResponse {
    public String peer;
    public int readOut;
    public ArrayList<ForumTopic> topics;

    public ForumTopicResponse(JSONObject json) {
        topics = new ArrayList<>();

        if (json.has("res")) {
            JSONArray rawTopics = json.getArray("res");

            for (int i = 0; i < rawTopics.size(); i++) {
                topics.add(new ForumTopic(rawTopics.getObject(i)));
            }
        }
    }
}
