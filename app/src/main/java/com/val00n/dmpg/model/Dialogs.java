package com.val00n.dmpg.model;

import java.util.ArrayList;

public class Dialogs {
    public ArrayList<DialogItem> dialogItems;
    public int count; // total dialogs
    public String offsetPeer; // last dialog id
    public String offsetId; // last msg id (not user)
    public long offsetDate; // last msg timestamp

    public Dialogs(ArrayList<DialogItem> dialogItems) {
        this.dialogItems = dialogItems;
    }
}
