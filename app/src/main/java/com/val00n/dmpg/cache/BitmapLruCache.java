package com.val00n.dmpg.cache;

import android.graphics.Bitmap;

public class BitmapLruCache extends LruCache<String, Bitmap> {

    private static final int MAX_CACHE_SIZE = (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 32 * 1024 * 1024); // FIXME UNHARDCODE?

    public BitmapLruCache() {
        super(MAX_CACHE_SIZE);
    }

    @Override
    public int sizeOf(Bitmap value) {
        if (value == null) return 0;
        return value.getRowBytes() * value.getHeight();
    }

    public void wipe(boolean avatars) {
        if (avatars) {
            removeByPrefix("ava");
        } else {
            removeByPrefix("img");
        }
    }
}
