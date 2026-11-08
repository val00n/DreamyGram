package com.val00n.dmpg.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import static android.content.ContentValues.TAG;

public class BitmapUtils {
    public static Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight, boolean RGB565) {
        if (filePath == null) return null;

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        if (reqHeight < 0 || reqWidth < 0) {
            reqHeight = options.outHeight;
            reqWidth = options.outWidth;
        }

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;

        if (RGB565) {
            options.inPreferredConfig = Bitmap.Config.RGB_565;
        } else {
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        }

        try {
            return BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM caught while decoding file, retrying with doubled sample size", e);
            options.inSampleSize *= 2;
            try {
                return BitmapFactory.decodeFile(filePath, options);
            } catch (OutOfMemoryError e2) {
                e2.printStackTrace();
                return null;
            }
        }
    }

    public static Bitmap decodeSampledBitmapFromByteArray(byte[] data, int reqWidth, int reqHeight, boolean RGB565) {
        if (data == null || data.length == 0) return null;

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        if (reqHeight < 0 || reqWidth < 0) {
            reqHeight = options.outHeight;
            reqWidth = options.outWidth;
        }

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;

        if (RGB565) {
            options.inPreferredConfig = Bitmap.Config.RGB_565;
        } else {
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        }

        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "OOM caught while decoding byte array, retrying", e);
            options.inSampleSize *= 2;
            try {
                return BitmapFactory.decodeByteArray(data, 0, data.length, options);
            } catch (OutOfMemoryError e2) {
                e2.printStackTrace();
                return null;
            }
        }
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
