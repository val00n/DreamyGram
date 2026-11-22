package com.val00n.dmpg.model;

import cc.nnproject.json.JSONObject;

public class Chat {
    public String id;
    public String t; // title
    public String name; // public link
    public boolean p; // has avatar
    public boolean c; // if broadcast channel
    public boolean l; // is left
    public boolean f; // is forum

    public Chat(JSONObject json) {
        if (json == null) return;

        id = json.getString("id");
        t = json.getString("t");

        if (json.has("name")) {
            name = json.getString("name");
        }

        if (json.has("p")) {
            p = json.getBoolean("p");
        }

        if (json.has("c")) {
            c = json.getBoolean("c");
        }

        if (json.has("l")) {
            l = json.getBoolean("l");
        }

        if (json.has("f")) {
            f = json.getBoolean("f");
        }
    }
}
