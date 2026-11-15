package com.val00n.dmpg.ui.chat;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.model.message.MessageRow;
import com.val00n.dmpg.ui.ImageViewBinder;
import java.util.ArrayList;

public class AlbumView extends ViewGroup {

    public interface Listener {
        void onMediaClick(MessageRow item, int index);
        boolean onMediaLongClick(MessageRow item, int index);
    }

    private MediaGroup group;
    private ImageViewBinder binder;
    private Listener listener;
    private int spacingPx;
    private int activeCount;

    public AlbumView(Context context) {
        super(context);
    }

    public AlbumView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AlbumView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    private static class ItemViewHolder {
        FixedImageView image;
        ImageView playOverlay;
    }

    private static class FixedImageView extends ImageView {
        private boolean ignoreRequestLayout;

        public FixedImageView(Context context) {
            super(context);
        }

        @Override
        public void requestLayout() {
            if (ignoreRequestLayout) return;
            super.requestLayout();
        }

        @Override
        public void setImageDrawable(Drawable drawable) {
            boolean suppress = getWidth() > 0 && getHeight() > 0;
            if (suppress) ignoreRequestLayout = true;
            try {
                super.setImageDrawable(drawable);
            } finally {
                ignoreRequestLayout = false;
            }
        }

        @Override
        public void setImageResource(int resId) {
            boolean suppress = getWidth() > 0 && getHeight() > 0;
            if (suppress) ignoreRequestLayout = true;
            try {
                super.setImageResource(resId);
            } finally {
                ignoreRequestLayout = false;
            }
        }
    }

    public void setSpacingPx(int spacingPx) {
        this.spacingPx = spacingPx;
    }

    public void bind(MediaGroup group, ImageViewBinder binder, final Listener listener) {
        MediaGroup prevGroup = this.group;
        int prevActiveCount = this.activeCount;

        this.group = group;
        this.binder = binder;
        this.listener = listener;

        int count = group != null ? group.items.size() : 0;
        this.activeCount = count;
        ensureChildren(count);

        if (group != null) {
            for (int i = 0; i < count; i++) {
                final MessageRow item = group.items.get(i);
                final int index = i;

                View childView = getChildAt(i);
                ItemViewHolder holder = (ItemViewHolder) childView.getTag();

                childView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (listener != null) {
                            listener.onMediaClick(item, index);
                        }
                    }
                });

                childView.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (listener != null) {
                            return listener.onMediaLongClick(item, index);
                        }
                        return false;
                    }
                });

                boolean isVideo = "video".equals(item.mediaType);
                if (holder.playOverlay != null) {
                    holder.playOverlay.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                }

                if (binder != null) {
                    String imagePeer = item.chatPeer != null ? item.chatPeer : item.peer;

                    if (isVideo) {
                        binder.bind(holder.image, imagePeer, item.messageId, item.thumb, FileParams.fileThumb());
                    } else {
                        binder.bind(holder.image, imagePeer, item.messageId, item.thumb, FileParams.preview());
                    }
                }
            }
        }

        boolean layoutChanged = prevActiveCount != count
                || prevGroup == null
                || group == null
                || !hasSameLayout(prevGroup, group);

        if (layoutChanged) {
            requestLayout();
        }
    }

    public void unbind() {
        this.group = null;
        this.activeCount = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.setVisibility(View.GONE);
            child.setOnClickListener(null);
            child.setOnLongClickListener(null);
            ItemViewHolder holder = (ItemViewHolder) child.getTag();
            holder.image.setTag(null);
            holder.image.setImageDrawable(null);
        }
    }

    private boolean hasSameLayout(MediaGroup a, MediaGroup b) {
        if (a == b) return true;
        int currentWidth = getWidth();
        if (currentWidth <= 0) return false;

        if (b.width != currentWidth || b.frames.size() != b.items.size()) {
            b.calculateLayout(currentWidth, spacingPx);
        }

        if (a.width != b.width || a.height != b.height) return false;
        ArrayList<Rect> fa = a.frames;
        ArrayList<Rect> fb = b.frames;
        if (fa.size() != fb.size()) return false;
        for (int i = 0; i < fa.size(); i++) {
            if (!fa.get(i).equals(fb.get(i))) return false;
        }
        return true;
    }

    private void ensureChildren(int count) {
        while (getChildCount() < count) {
            addView(createImageView());
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (i < count) {
                if (child.getVisibility() != View.VISIBLE) {
                    child.setVisibility(View.VISIBLE);
                }
            } else {
                if (child.getVisibility() != View.GONE) {
                    child.setVisibility(View.GONE);
                    child.setOnClickListener(null);
                    child.setOnLongClickListener(null);
                    ItemViewHolder holder = (ItemViewHolder) child.getTag();
                    holder.image.setTag(null);
                    holder.image.setImageDrawable(null);
                }
            }
        }
    }

    private View createImageView() {
        Context context = getContext();
        FrameLayout frameLayout = new FrameLayout(context);

        FixedImageView imageView = new FixedImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackgroundColor(0xFF000000);
        frameLayout.addView(imageView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ImageView playIcon = new ImageView(context);
        playIcon.setImageResource(android.R.drawable.ic_media_play);
        playIcon.setVisibility(View.GONE);

        int size = (int) (48 * context.getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(size, size);
        playParams.gravity = android.view.Gravity.CENTER;
        frameLayout.addView(playIcon, playParams);

        ItemViewHolder holder = new ItemViewHolder();
        holder.image = imageView;
        holder.playOverlay = playIcon;
        frameLayout.setTag(holder);

        frameLayout.setClickable(true);
        frameLayout.setLongClickable(true);
        frameLayout.setFocusable(false);
        frameLayout.setFocusableInTouchMode(false);

        return frameLayout;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (group == null || activeCount == 0) {
            setMeasuredDimension(0, 0);
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }

        if (group.width != width || group.frames.size() != activeCount) {
            group.calculateLayout(width, spacingPx);
        }

        for (int i = 0; i < activeCount; i++) {
            View child = getChildAt(i);
            Rect frame = group.frames.get(i);

            int childWidthSpec = MeasureSpec.makeMeasureSpec(frame.width(), MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(frame.height(), MeasureSpec.EXACTLY);
            child.measure(childWidthSpec, childHeightSpec);
        }

        setMeasuredDimension(width, group.height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (group == null) {
            return;
        }

        for (int i = 0; i < activeCount; i++) {
            View child = getChildAt(i);
            if (i >= group.frames.size()) {
                break;
            }

            Rect frame = group.frames.get(i);
            child.layout(frame.left, frame.top, frame.right, frame.bottom);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}