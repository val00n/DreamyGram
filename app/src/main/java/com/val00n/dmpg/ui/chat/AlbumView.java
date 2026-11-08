package com.val00n.dmpg.ui.chat;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.val00n.dmpg.model.FileParams;
import com.val00n.dmpg.model.message.MessageRow;
import com.val00n.dmpg.ui.ImageViewBinder;

public class AlbumView extends ViewGroup {

    public interface Listener {
        void onMediaClick(MessageRow item, int index);
        boolean onMediaLongClick(MessageRow item, int index);
    }

    private MediaGroup group;
    private ImageViewBinder binder;
    private Listener listener;
    private int spacingPx;

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
        ImageView image;
        ImageView playOverlay;
    }

    public void setSpacingPx(int spacingPx) {
        this.spacingPx = spacingPx;
    }

    public void bind(MediaGroup group, ImageViewBinder binder, final Listener listener) {
        this.group = group;
        this.binder = binder;
        this.listener = listener;

        int count = group != null ? group.items.size() : 0;
        ensureChildren(count);

        if (group != null) {
            for (int i = 0; i < getChildCount(); i++) {
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
                        binder.bind(holder.image, imagePeer, item.messageId, FileParams.fileThumb());
                    } else {
                        binder.bind(holder.image, imagePeer, item.messageId, FileParams.preview());
                    }
                }
            }
        }

        requestLayout();
    }

    private void ensureChildren(int count) {
        while (getChildCount() > count) {
            removeViewAt(getChildCount() - 1);
        }

        while (getChildCount() < count) {
            addView(createImageView());
        }
    }

    private View createImageView() {
        Context context = getContext();
        FrameLayout frameLayout = new FrameLayout(context);

        ImageView imageView = new ImageView(context);
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
        if (group == null || group.items.isEmpty()) {
            setMeasuredDimension(0, 0);
            return;
        }

        int width = MeasureSpec.getSize(widthMeasureSpec);

        if (width <= 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }

        if (group.width != width || group.frames.size() != group.items.size()) {
            group.calculateLayout(width, spacingPx);
        }

        if (getChildCount() != group.items.size()) {
            ensureChildren(group.items.size());
        }

        for (int i = 0; i < getChildCount(); i++) {
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

        for (int i = 0; i < getChildCount(); i++) {
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