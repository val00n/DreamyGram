package com.val00n.dmpg.model.message;

import cc.nnproject.json.JSONObject;

public class Act {
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
