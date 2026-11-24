package com.val00n.dmpg.model.search;

import com.val00n.dmpg.model.Chat;
import com.val00n.dmpg.model.User;
import com.val00n.dmpg.model.dialog.Dialog;

public class SearchResult {
    public String peerId;
    public String title;
    public String username;
    public boolean hasAvatar;
    public int readOut;

    public User user;
    public Chat chat;
    public Dialog dialog;

    public SearchResult(User user) {
        this.user = user;

        peerId = user.id;
        title = buildUserTitle(user);
        username = user.name;
        hasAvatar = user.p;
    }

    public SearchResult(Chat chat) {
        this.chat = chat;

        peerId = chat.id;
        title = chat.t != null ? chat.t : chat.id;
        username = chat.name;
        hasAvatar = chat.p;
    }

    public SearchResult(Dialog dialog, String selfId) {
        this.dialog = dialog;

        peerId = dialog.peerId;
        title = dialog.getDisplayName(selfId);
        if (title == null) {
            title = dialog.peerId;
        }

        username = dialog.chat != null
                ? dialog.chat.name
                : (dialog.user != null ? dialog.user.name : null);
        hasAvatar = dialog.hasAvatar();
        readOut = dialog.readOut;
    }

    public String getSubtitle() {
        if (dialog != null) {
            return dialog.getSubtitle();
        }

        if (username != null && username.length() > 0) {
            return "@" + username;
        }

        return "";
    }

    private static String buildUserTitle(User user) {
        if (user.fn != null && user.fn.length() > 0) {
            if (user.ln != null && user.ln.length() > 0) {
                return user.fn + " " + user.ln;
            }
            return user.fn;
        }

        if (user.name != null && user.name.length() > 0) {
            return "@" + user.name;
        }

        return user.id;
    }
}
