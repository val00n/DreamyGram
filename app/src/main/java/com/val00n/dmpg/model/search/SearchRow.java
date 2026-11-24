package com.val00n.dmpg.model.search;

public class SearchRow {
    public static final int TYPE_GLOBAL = 0;
    public static final int TYPE_SEPARATOR = 1;
    public static final int TYPE_LOCAL = 2;
    public static final int TYPE_EMPTY = 3;

    public int type;
    public SearchResult result;
    public String unreadBadge;
}
