package com.val00n.dmpg.ui.chat;

import android.graphics.Rect;

import com.val00n.dmpg.model.message.MessageRow;

import java.util.ArrayList;

public class MediaGroup {
    public ArrayList<MessageRow> items = new ArrayList<MessageRow>();

    public ArrayList<Rect> frames = new ArrayList<Rect>();
    public int width;
    public int height;

    public void calculateLayout(int targetWidth, int spacingPx) {
        frames.clear();

        if (targetWidth <= 0) {
            width = 0;
            height = 0;
            return;
        }

        width = targetWidth;

        if (items.isEmpty()) {
            height = 0;
            return;
        }

        int minRowHeight = Math.max(96, targetWidth / 5);
        int maxRowHeight = Math.max(minRowHeight, (int) (targetWidth * 0.68f));

        int y = 0;

        for (int i = 0; i < items.size(); i += 2) {
            int count = Math.min(2, items.size() - i);

            float[] aspects = new float[count];
            float sumAspect = 0;

            for (int k = 0; k < count; k++) {
                aspects[k] = getAspect(items.get(i + k));
                sumAspect += aspects[k];
            }

            int gaps = spacingPx * (count - 1);
            float availableWidth = targetWidth - gaps;

            if (availableWidth <= 0) {
                availableWidth = 1;
            }

            int rowHeight = Math.round(availableWidth / sumAspect);

            if (rowHeight < minRowHeight) {
                rowHeight = minRowHeight;
            }

            if (rowHeight > maxRowHeight) {
                rowHeight = maxRowHeight;
            }

            float rawTotal = 0;
            float[] rawWidths = new float[count];

            for (int k = 0; k < count; k++) {
                rawWidths[k] = rowHeight * aspects[k];
                rawTotal += rawWidths[k];
            }

            float scale = rawTotal > 0 ? availableWidth / rawTotal : 1f;

            int x = 0;
            int usedWidth = 0;

            for (int k = 0; k < count; k++) {
                int w;

                if (k == count - 1) {
                    w = targetWidth - usedWidth;
                } else {
                    w = Math.round(rawWidths[k] * scale);
                }

                if (w < 1) {
                    w = 1;
                }

                frames.add(new Rect(x, y, x + w, y + rowHeight));

                x += w + spacingPx;
                usedWidth += w + spacingPx;
            }

            y += rowHeight + spacingPx;
        }

        height = Math.max(0, y - spacingPx);
    }

    private float getAspect(MessageRow item) {
        if (item == null || item.mediaWidth <= 0 || item.mediaHeight <= 0) {
            return 1f;
        }

        float aspect = (float) item.mediaWidth / (float) item.mediaHeight;

        if (aspect < 0.2f) {
            aspect = 0.2f;
        }

        if (aspect > 5f) {
            aspect = 5f;
        }

        return aspect;
    }
}
