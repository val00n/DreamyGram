package com.val00n.dmpg.model;

import java.util.ArrayList;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class DialogResponse {
    public int totalCount;
    public ArrayList<Dialog> dialogs;
    public String offsetPeer; // last dialog id
    public String offsetId; // last msg id (not user)
    public long offsetDate; // last msg timestamp

    public DialogResponse(JSONObject json) {
        totalCount = json.getInt("count", 0);

        JSONObject users = null;
        if (json.has("users")) {
            users = json.getNullableObject("users");
        }

        JSONObject chats = null;
        if (json.has("chats")) {
            chats = json.getNullableObject("chats");
        }

        dialogs = new ArrayList<>();

        String dialogId;
        JSONObject dialog, msg;
        JSONArray dialogsRaw;
        User user;
        Chat chat;

        if (json.has("dialogs")) {
            dialogsRaw = json.getArray("dialogs");

            for (int i = 0; i < dialogsRaw.size(); i++) {
                dialog = dialogsRaw.getObject(i);
                dialogId = dialog.getString("id");

                if (dialogId.startsWith("-")) {
                    chat = chats == null ? null : new Chat(chats.getNullableObject(dialogId));

                    if (dialog.has("msg")) {
                        msg = dialog.getObject("msg");

                        if (msg.has("from_id")) {
                            if (msg.getString("from_id").startsWith("-")) {
                                dialogs.add(new Dialog(dialogsRaw.getObject(i), null, chat));
                            } else {
                                user = users == null ? null : new User(users.getNullableObject(msg.getString("from_id")));
                                dialogs.add(new Dialog(dialogsRaw.getObject(i), user, chat));
                            }
                        } else {
                            dialogs.add(new Dialog(dialogsRaw.getObject(i), null, chat));
                        }

                    } else {
                        dialogs.add(new Dialog(dialogsRaw.getNullableObject(i), null, chat));
                    }

                } else {
                    user = users == null ? null : new User(users.getNullableObject(dialogId));
                    dialogs.add(new Dialog(dialogsRaw.getObject(i), user, null));
                }
            }

            if (dialogs.size() > 0) {
                Dialog latestDialog = dialogs.get(dialogs.size() - 1);

                offsetPeer = latestDialog.id;
                if (latestDialog.msg != null) {
                    offsetDate = latestDialog.msg.date;
                    offsetId = String.valueOf(latestDialog.msg.id);
                }
            }
        }
    }
}
