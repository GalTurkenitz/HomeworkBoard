package com.rabin.firebasefirestoreapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageUtils {

    // --------------------------------------------------
    // מגבלות
    // --------------------------------------------------

    // מסמך Firestore מוגבל ל-1MiB.
    // כאשר ממירים תמונה ל-Base64,
    // נפח המידע גדל בערך ב-33%.
    // לדוגמה:
    // 650,000 bytes של JPEG
    // יהפכו בערך ל-866,000 תווי Base64.
    // לכן אנחנו מגבילים את קובץ ה-JPEG
    // לפני ההמרה ל-Base64.
    private static final int MAX_IMAGE_BYTES = 650000;

    // הצלע הארוכה ביותר בתמונה תהיה עד 1280 פיקסלים.
    // הקטנת הממדים:
    // - מקטינה את נפח התמונה
    // - מקטינה את צריכת הזיכרון
    // - עוזרת לנו להישאר מתחת למגבלת Firestore
    private static final int MAX_DIMENSION = 1280;

    // --------------------------------------------------
    // 1. המרת Uri ל-Bitmap
    // --------------------------------------------------

    // כאשר בוחרים תמונה מהגלריה,
    // Android מחזיר לנו Uri.
    // הפעולה ממירה:
    // Uri
    // ↓
    // Bitmap
    // אם התמונה גדולה מאוד,
    // אנחנו מקטינים אותה כבר בזמן הטעינה
    // כדי לא להעמיס על הזיכרון.
    public static Bitmap convertUriToBitmap(Context context, Uri uri) throws IOException {
        if (uri == null) {
            return null;
        }

        ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);

        return ImageDecoder.decodeBitmap(source, new ImageDecoder.OnHeaderDecodedListener() {
            @Override
            public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                        ImageDecoder.Source source) {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();

                // מחשבים בכמה צריך להקטין את התמונה.
                // אם התמונה כבר קטנה מ-1280,
                // scale יהיה 1 ולא נקטין אותה.
                float scale = Math.min(1f, (float) MAX_DIMENSION / Math.max(width, height));

                int newWidth = Math.max(1, Math.round(width * scale));
                int newHeight = Math.max(1, Math.round(height * scale));

                decoder.setTargetSize(newWidth, newHeight);

                // מבטיח שנקבל Bitmap רגיל בזיכרון
                // שניתן לעבוד איתו ולצייר עליו.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            }
        });
    }

    // --------------------------------------------------
    // 2. המרת Bitmap ל-Base64
    // --------------------------------------------------

    // שלבי הפעולה:
    // Bitmap
    // ↓
    // כיווץ ל-JPEG
    // ↓
    // byte[]
    // ↓
    // Base64 String
    // Base64 מאפשר לשמור את התמונה
    // כמחרוזת בתוך Firestore.
    public static String convertBitmapToBase64(Bitmap bitmap) {
        byte[] bytes = compressImage(bitmap);

        if (bytes == null) {
            return null;
        }

        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    // --------------------------------------------------
    // 3. המרת Base64 ל-Bitmap
    // --------------------------------------------------

    // פעולה הפוכה:
    // Base64 String
    // ↓
    // byte[]
    // ↓
    // Bitmap
    public static Bitmap convertBase64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }

        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException error) {
            // המחרוזת אינה Base64 תקין
            return null;
        }
    }

    // --------------------------------------------------
    // 4. המרת ImageView ל-Bitmap
    // --------------------------------------------------

    // הפעולה מקבלת ImageView
    // ומחזירה Bitmap חדש.
    // אנחנו לוקחים את ה-Drawable
    // שנמצא בתוך ה-ImageView ומציירים אותו
    // על Bitmap חדש.
    // חשוב:
    // אם ה-ImageView משתמש ב-centerCrop,
    // אנחנו ממירים את ה-Drawable עצמו,
    // ולכן ייתכן שה-Bitmap יכיל גם חלקים
    // שאינם נראים כרגע על המסך.
    // JPEG אינו תומך בשקיפות,
    // ולכן אנחנו משתמשים ברקע לבן.
    public static Bitmap convertImageViewToBitmap(ImageView imageView) {
        if (imageView == null) {
            return null;
        }

        Drawable drawable = imageView.getDrawable();

        if (drawable == null) {
            return null;
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();

        if (width <= 0 || height <= 0) {
            return null;
        }

        // מגבילים גם כאן את הצלע הארוכה ל-1280
        float scale = Math.min(1f, (float) MAX_DIMENSION / Math.max(width, height));

        width = Math.max(1, Math.round(width * scale));
        height = Math.max(1, Math.round(height * scale));

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);

        // JPEG לא תומך בשקיפות,
        // לכן נשתמש ברקע לבן.
        canvas.drawColor(Color.WHITE);

        // שומרים את הגבולות המקוריים
        // של ה-Drawable.
        Rect oldBounds = new Rect(drawable.getBounds());

        try {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
        } finally {
            // מחזירים ל-Drawable
            // את הגבולות המקוריים שלו.
            drawable.setBounds(oldBounds);
        }

        return bitmap;
    }

    // --------------------------------------------------
    // 5. כיווץ תמונה לגודל המתאים ל-Firestore
    // --------------------------------------------------

    // הפעולה מקבלת Bitmap
    // ומחזירה מערך בתים של JPEG.
    // תהליך הכיווץ:
    // 1. מגבילים את הצלע הארוכה ל-1280 פיקסלים.
    // 2. מנסים JPEG באיכות 90%.
    // 3. אם התמונה עדיין גדולה מדי,
    //    מורידים איכות:
    //    90 → 80 → 70 → ... → 30
    // 4. אם גם באיכות 30 התמונה גדולה מדי,
    //    מקטינים את הממדים ב-20%.
    // 5. מנסים שוב את כל רמות האיכות.
    // 6. ממשיכים עד שנגיע לפחות
    //    מ-MAX_IMAGE_BYTES.
    // הפעולה אינה מוחקת את ה-Bitmap
    // המקורי שקיבלה.
    // היא משחררת רק Bitmaps זמניים
    // שהיא יצרה בעצמה.
    public static byte[] compressImage(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }

        Bitmap current = bitmap;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);

        // --------------------------------------------------
        // שלב 1:
        // הקטנת ממדים אם התמונה גדולה מ-1280
        // --------------------------------------------------
        if (longest > MAX_DIMENSION) {
            float scale = (float) MAX_DIMENSION / longest;

            int newWidth = Math.max(1, Math.round(width * scale));
            int newHeight = Math.max(1, Math.round(height * scale));

            current = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        }

        // --------------------------------------------------
        // טיפול בשקיפות
        // --------------------------------------------------

        // JPEG אינו תומך בשקיפות.
        // אם ה-Bitmap מכיל שקיפות,
        // ניצור Bitmap חדש עם רקע לבן.
        // RGB_565 משתמש בפחות זיכרון
        // מ-ARGB_8888.
        if (current.hasAlpha()) {
            Bitmap opaque = Bitmap.createBitmap(current.getWidth(), current.getHeight(),
                    Bitmap.Config.RGB_565);

            Canvas canvas = new Canvas(opaque);
            canvas.drawColor(Color.WHITE);
            canvas.drawBitmap(current, 0, 0, null);

            // משחררים רק Bitmap זמני
            // שאנחנו יצרנו.
            if (current != bitmap) {
                current.recycle();
            }

            current = opaque;
        }

        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        // --------------------------------------------------
        // שלב 2:
        // ניסיונות הכיווץ
        // --------------------------------------------------
        while (true) {

            // מנסים רמות איכות שונות:
            // 90, 80, 70 ... 30
            for (int quality = 90; quality >= 30; quality -= 10) {
                stream.reset();

                boolean success = current.compress(Bitmap.CompressFormat.JPEG, quality, stream);

                if (!success) {
                    if (current != bitmap) {
                        current.recycle();
                    }
                    return null;
                }

                // הצלחנו להגיע לנפח הרצוי
                if (stream.size() <= MAX_IMAGE_BYTES) {
                    byte[] result = stream.toByteArray();

                    if (current != bitmap) {
                        current.recycle();
                    }

                    return result;
                }
            }

            // --------------------------------------------------
            // גם איכות 30 עדיין גדולה מדי.
            // מקטינים את הממדים בעוד 20%
            // ומנסים שוב.
            // --------------------------------------------------
            int newWidth = Math.max(1, (int) (current.getWidth() * 0.8));
            int newHeight = Math.max(1, (int) (current.getHeight() * 0.8));

            // עצירה אם התמונה כבר נעשתה
            // קטנה מדי.
            if (newWidth < 100 || newHeight < 100
                    || (newWidth == current.getWidth() && newHeight == current.getHeight())) {
                if (current != bitmap) {
                    current.recycle();
                }
                return null;
            }

            Bitmap smaller = Bitmap.createScaledBitmap(current, newWidth, newHeight, true);

            if (current != bitmap) {
                current.recycle();
            }

            current = smaller;
        }
    }
}
