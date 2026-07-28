package com.val00n.dmpg.services;

import com.val00n.dmpg.model.DialogItem;
import com.val00n.dmpg.model.Dialogs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

public class DialogsParser {
    public static ArrayList<DialogItem> parse(JSONObject root) {
        ArrayList<DialogItem> result = new ArrayList<DialogItem>();

        if (!root.has("dialogs") || !root.has("users") || !root.has("chats")) {
           return result;
        }

        JSONArray dialogs = root.getArray("dialogs");
        JSONObject chats = root.getObject("chats");
        JSONObject users = root.getObject("users");
        JSONObject currentDialog, msg;
        DialogItem parsedDialogItem;
        for (int i = 0; i < dialogs.size(); i++) {
            parsedDialogItem = new DialogItem();
            currentDialog = dialogs.getObject(i);

            if (currentDialog.has("pin") && currentDialog.getBoolean("pin") == true) {
                parsedDialogItem.isPinned = true;
            }

            if (currentDialog.has("unread")) {
                parsedDialogItem.unreadMessages = currentDialog.getInt("unread", 0);
            }

            if (currentDialog.has("mentions")) {
                parsedDialogItem.unreadMessages = currentDialog.getInt("mentions", 0);
            }

            if (currentDialog.has("msg")) {
                msg = currentDialog.getObject("msg");

                parsedDialogItem.lastMessageText = msg.getString("text", "");
                parsedDialogItem.lastMessageTimestamp = msg.getLong("date", 0) * 1000;
                parsedDialogItem.lastMessageId = msg.getString("id", "");

                if (msg.has("media")) {
                    JSONObject media = msg.getNullableObject("media");
                    if (media != null) {
                        parsedDialogItem.mediaType = media.getNullableString("type");

                        if ("document".equals(parsedDialogItem.mediaType)) {
                            String mime = media.getNullableString("mime");
                            if (mime != null && (mime.equals("image/webp") || mime.equals("video/webm") || mime.equals("application/x-tgsticker"))) {
                                parsedDialogItem.isSticker = true;
                            }
                        }
                    }
                }

                if (msg.has("act")) {
                    parsedDialogItem.userAction = msg.getNullableString("act");
                }

                parsedDialogItem.id = currentDialog.getString("id");
                if (parsedDialogItem.id.contains("-")) {
                    parsedDialogItem.isChatOrChannel = true;
                    if (chats.has(parsedDialogItem.id)) { // negative id == chat
                        JSONObject currentChat = chats.getObject(parsedDialogItem.id);
                        parsedDialogItem.displayName = currentChat.getString("t");

                        String fromId = msg.getNullableString("from_id");
                        if (fromId != null && users.has(fromId)) {
                            JSONObject fromUser = users.getObject(fromId);

                            parsedDialogItem.lastMessageUser = fromUser.getString("fn");

                            if (fromUser.getNullableString("ln") != null) {
                                parsedDialogItem.lastMessageUser += ' ' + fromUser.getString("ln");
                            }
                        }

                        parsedDialogItem.hasAvatar = currentChat.getBoolean("p", false);
                    }
                } else {
                    if (users.has(parsedDialogItem.id)) { // positive id == user
                        JSONObject currentUser = users.getObject(parsedDialogItem.id);
                        parsedDialogItem.displayName = currentUser.getString("fn");
                        if (currentUser.getNullableString("ln") != null) {
                            parsedDialogItem.displayName += ' ' + currentUser.getString("ln");
                        }

                        parsedDialogItem.hasAvatar = currentUser.getBoolean("p", false);
                    }
                }
            }

            result.add(parsedDialogItem);
        }

        Collections.sort(result, new Comparator<DialogItem>() {
            @Override
            public int compare(DialogItem a, DialogItem b) {
                return Boolean.valueOf(b.isPinned).compareTo(Boolean.valueOf(a.isPinned));
            }
        });

        return result;
    }
}