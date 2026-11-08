package com.val00n.dmpg.ui;

import android.graphics.Bitmap;
import android.widget.ImageView;
import com.val00n.dmpg.R;
import com.val00n.dmpg.cache.CacheManager;

public class AvatarViewBinder {
    private final CacheManager cacheManager;

    public AvatarViewBinder(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void bind(final ImageView view, final String peer, boolean hasAvatar) {
        view.setImageResource(R.drawable.avatar_placeholder);

        if (peer == null || peer.length() == 0 || !hasAvatar) {
            view.setTag(null);
            return;
        }
        view.setTag(peer);

        cacheManager.loadAvatar(peer, new CacheManager.ImageCallback() {
            @Override
            public void onLoaded(Bitmap bitmap) {
                if (!peer.equals(view.getTag())) return;

                if (bitmap != null) {
                    view.setImageBitmap(bitmap);
                } else {
                    view.setImageResource(R.drawable.avatar_placeholder);
                }
            }

            @Override
            public void onError(Exception error) {
                if (!peer.equals(view.getTag())) return;
                view.setImageResource(R.drawable.avatar_placeholder);
            }
        });
    }
}

