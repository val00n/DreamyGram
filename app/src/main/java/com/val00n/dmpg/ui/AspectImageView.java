package com.val00n.dmpg.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

public class AspectImageView extends ImageView {

    private int maxHeightPx = 512;
    private float maxRatio = 0f;
    private int srcW, srcH;
    private boolean square;

    public AspectImageView(Context c) { super(c); }
    public AspectImageView(Context c, AttributeSet a) { super(c, a); }
    public AspectImageView(Context c, AttributeSet a, int d) { super(c, a, d); }

    public void setMaxHeightPx(int px) {
        int v = px > 0 ? px : Integer.MAX_VALUE;
        if (v != maxHeightPx) { maxHeightPx = v; requestLayout(); }
    }

    public void setMaxRatio(float r) {
        if (r != maxRatio) { maxRatio = r; requestLayout(); }
    }

    public void setSourceSize(int w, int h) {
        if (w == srcW && h == srcH) return;
        srcW = w; srcH = h;
        requestLayout();
    }

    public void square(boolean square) {
        this.square = square;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int wMode = MeasureSpec.getMode(widthSpec);
        int wSize = MeasureSpec.getSize(widthSpec);

        if (wMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }

        int w = wSize;

        if (square) {
            setMeasuredDimension(w, w);
            return;
        }

        int dw = srcW, dh = srcH;
        if (dw <= 0 || dh <= 0) {
            Drawable d = getDrawable();
            if (d != null) { dw = d.getIntrinsicWidth(); dh = d.getIntrinsicHeight(); }
        }

        if (dw <= 0 || dh <= 0) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }

        int h = (int) ((long) w * dh / dw);

        if (maxRatio > 0f) h = Math.min(h, (int) (w * maxRatio));
        h = Math.min(h, maxHeightPx);

        int hMode = MeasureSpec.getMode(heightSpec);
        int hSize = MeasureSpec.getSize(heightSpec);
        if (hMode == MeasureSpec.EXACTLY)  h = hSize;
        else if (hMode == MeasureSpec.AT_MOST) h = Math.min(h, hSize);

        setMeasuredDimension(w, Math.max(h, 1));
    }
}