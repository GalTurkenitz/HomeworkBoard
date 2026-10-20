package com.rabin.firebasefirestoreapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;

public class ImageUtils {

    // מסמך Firestore מוגבל ל-1MiB.
    // 650,000 בתים יהפכו לכ-866,668 תווי Base64.
    private static final int MAX_IMAGE_BYTES = 650000;

    // הצלע הארוכה ביותר בתמונה שתישמר.
    private static final int MAX_DIMENSION = 1280;

    // Bitmap -> JPEG דחוס -> byte[] -> Base64
    public static String convertBitmapToString(Bitmap bitmap) {

        byte[] bytes = compressImage(bitmap);

        if (bytes == null) return null;

        return Base64.encodeToString(
                bytes, Base64.NO_WRAP);
    }

    // Base64 -> byte[] -> פתיחת JPEG -> Bitmap
    public static Bitmap convertBase64StringToBitmap(
            String base64) {

        if (base64 == null || base64.isEmpty()) return null;

        try {
            byte[] bytes = Base64.decode(
                    base64, Base64.DEFAULT);

            return BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ציור ה-Drawable המוגדר ב-ImageView
    // על Bitmap חדש.
    // ייתכן הבדל בין כל ה-Drawable לחלק
    // הנראה במצב centerCrop.
    public static Bitmap convertImageViewToBitmap(
            ImageView imageView) {

        if (imageView == null) return null;

        Drawable drawable = imageView.getDrawable();

        if (drawable == null) return null;

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) return null;

        float scale = Math.min(
                1f,
                (float) MAX_DIMENSION / Math.max(width, height));

        width = Math.max(1, Math.round(width * scale));
        height = Math.max(1, Math.round(height * scale));

        Bitmap result = Bitmap.createBitmap(
                width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);

        canvas.drawColor(Color.WHITE);

        Rect oldBounds = new Rect(drawable.getBounds());

        try {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);

        } finally {
            drawable.setBounds(oldBounds);
        }

        return result;
    }

    // הפקת JPEG קטן מספיק לשמירה ב-Firestore.
    // תחילה מקטינים ממדים ומנסים איכות 90 עד 30.
    // אם עדיין גדול מדי, מקטינים את הממדים ב-20%.
    public static byte[] compressImage(Bitmap bitmap) {

        if (bitmap == null || bitmap.isRecycled()) return null;

        Bitmap current = bitmap;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);

        if (longest > MAX_DIMENSION) {

            float scale = (float) MAX_DIMENSION / longest;

            current = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)),
                    true);
        }

        // המרת שקיפות לרקע לבן
        if (current.hasAlpha()) {

            Bitmap opaque = Bitmap.createBitmap(
                    current.getWidth(),
                    current.getHeight(),
                    Bitmap.Config.RGB_565);

            Canvas canvas = new Canvas(opaque);

            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(current, 0, 0, null);

            if (current != bitmap) current.recycle();

            current = opaque;
        }

        ByteArrayOutputStream stream =
                new ByteArrayOutputStream();

        while (true) {

            // ניסיונות דחיסה ברמות איכות שונות
            for (int quality = 90;
                 quality >= 30;
                 quality -= 10) {

                stream.reset();

                boolean ok = current.compress(
                        Bitmap.CompressFormat.JPEG,
                        quality,
                        stream);

                if (!ok) {
                    if (current != bitmap) current.recycle();
                    return null;
                }

                if (stream.size() <= MAX_IMAGE_BYTES) {

                    byte[] result = stream.toByteArray();

                    if (current != bitmap) current.recycle();

                    return result;
                }
            }

            // גם איכות 30 גדולה מדי.
            // מקטינים שוב את הממדים.
            int newWidth = Math.max(
                    1,
                    (int) (current.getWidth() * 0.8));

            int newHeight = Math.max(
                    1,
                    (int) (current.getHeight() * 0.8));

            // תנאי עצירה
            if (newWidth < 100 || newHeight < 100
                    || (newWidth == current.getWidth()
                    && newHeight == current.getHeight())) {

                if (current != bitmap) current.recycle();

                return null;
            }

            Bitmap smaller = Bitmap.createScaledBitmap(
                    current, newWidth, newHeight, true);

            if (current != bitmap) current.recycle();

            current = smaller;
        }
    }
}