package com.val00n.dmpg.model;

import com.val00n.dmpg.utils.DateUtils;

import java.util.Date;
import cc.nnproject.json.JSONObject;

public class User {
    public String id;
    public String fn;
    public String ln;
    public String name; // public link (username)
    public boolean p; // has photo
    public boolean k; // is in contacts
    public boolean s; // is online
    public long w; // last seen
    public boolean a; // admin

    public User(JSONObject json) {
        if (json == null) return;

        id = json.getString("id");
        fn = json.getNullableString("fn");
        ln = json.getNullableString("ln");

        if (json.has("name")) {
            name = json.getString("name");
        }

        if (json.has("p")) {
            p = json.getBoolean("p");
        }

        if (json.has("k")) {
            k = json.getBoolean("k");
        }

        if (json.has("s")) {
            s = json.getBoolean("s");
        }

        if (json.has("w")) {
            w = json.getLong("w");
        }

        if (json.has("a")) {
            a = json.getBoolean("a");
        }
    }

    public String getDisplayName() {
        String result;
        if (fn != null && fn.length() > 0) {
            result = fn;

            if (ln != null && ln.length() > 0) {
                result += " " + ln; // FIXME: not sure that user can be with last name without first
            }
        } else {
            result = "@" + name;
        }

        return result;
    }

    public String getStatusText() {
        if (s) return "online";
        if (w > 0) {
            long now = System.currentTimeMillis();
            return DateUtils.isToday(w, now)
                    ? "last seen at " + DateUtils.formatTime(w)
                    : "last seen " + DateUtils.formatDateTime(w);
        }
        return null;
    }
}
