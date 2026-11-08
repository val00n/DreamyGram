package com.val00n.dmpg.ui;

import android.graphics.Bitmap;
import android.util.Log;
import android.widget.ImageView;

import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;
import com.val00n.dmpg.model.FileParams;

import static android.content.ContentValues.TAG;

public class ImageViewBinder {
    private final CacheManager cacheManager;

    public ImageViewBinder(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void bind(final ImageView view, final String peer, final String messageId, final FileParams fileParams) {
        view.setImageResource(0);

        if (peer == null || peer.length() == 0 || messageId == null || messageId.length() == 0) {
            view.setTag(null);
            return;
        }
        view.setTag(messageId);

        cacheManager.loadImage(peer, messageId, fileParams, new CacheManager.ImageCallback() {
            @Override
            public void onLoaded(Bitmap bitmap) {
                if (!messageId.equals(view.getTag())) return;

                if (bitmap != null) {
                    view.setImageBitmap(bitmap);
                } else {
                    view.setImageResource(android.R.drawable.ic_menu_report_image); // TODO
                }
            }

            @Override
            public void onError(Exception error) {
                if (!messageId.equals(view.getTag())) return;
                view.setImageResource(android.R.drawable.ic_menu_report_image); // TODO
            }
        });
    }
}

