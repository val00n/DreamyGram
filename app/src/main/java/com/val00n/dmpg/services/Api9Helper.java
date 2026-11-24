package com.val00n.dmpg.services;

import java.io.File;

public class Api9Helper {
    public static void setReadable(File file) {
        if (file != null) {
            file.setReadable(true, false);
        }
    }
}