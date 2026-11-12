package com.val00n.dmpg.utils;

import com.val00n.dmpg.model.dialog.Dialog;
import java.util.Comparator;

public class SortUtils {
    public static final Comparator<Dialog> PINNED_FIRST = new Comparator<Dialog>() {
        @Override
        public int compare(Dialog a, Dialog b) {
            if (a.pin == b.pin) return 0;
            return a.pin ? -1 : 1;
        }
    };
}
