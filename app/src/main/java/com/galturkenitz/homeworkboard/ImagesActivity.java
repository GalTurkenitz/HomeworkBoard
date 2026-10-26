package com.galturkenitz.homeworkboard;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.rabin.firebasefirestoreapplication.ImageUtils;

import java.io.InputStream;

// שלב ג': עבודה עם תמונות בשתי דרכים שונות, לכל משימה
// דרך 1 - גלריה: בוחרים תמונה, שומרים ב-Firestore את ה-URL שלה, ומציגים אותה דרך ה-URL
// דרך 2 - Base64: ממירים את התמונה למחרוזת Base64, שומרים אותה ישירות במסמך, וממירים חזרה לתמונה להצגה
public class ImagesActivity extends BaseActivity {
    private ImageView ivGallery, ivBase64;
    private TextView tvGalleryUrl, tvBase64Info;
    private Button btnPickGallery, btnPickBase64, btnTakePhoto;

    private ImageDAO imageDAO;
    private String homeworkId;

    // דרך 1: OpenDocument מאפשר לשמור הרשאת קריאה קבועה ל-URL, כדי שאפשר יהיה להציג אותו גם אחר כך
    private final ActivityResultLauncher<String[]> galleryUrlLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        saveGalleryUrl(uri);
                    }
                }
            });

    // דרך 2: בוחרים תמונה כדי להמיר אותה ל-Base64
    private final ActivityResultLauncher<String> base64Launcher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        convertAndSaveBase64(uri);
                    }
                }
            });

    // צילום במצלמה: TakePicturePreview מחזיר Bitmap ישירות, בלי לשמור קובץ בגלריה
    // (התמונה שחוזרת קטנה ובאיכות נמוכה יותר - כמו שהמורה אמרה)
    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), new ActivityResultCallback<Bitmap>() {
                @Override
                public void onActivityResult(Bitmap bitmap) {
                    if (bitmap != null) {
                        compressAndSaveBitmap(bitmap);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreen(R.layout.activity_images, "תמונות למשימה", true);

        ivGallery = findViewById(R.id.ivGallery);
        ivBase64 = findViewById(R.id.ivBase64);
        tvGalleryUrl = findViewById(R.id.tvGalleryUrl);
        tvBase64Info = findViewById(R.id.tvBase64Info);
        btnPickGallery = findViewById(R.id.btnPickGallery);
        btnPickBase64 = findViewById(R.id.btnPickBase64);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);

        imageDAO = new ImageDAO();
        homeworkId = getIntent().getStringExtra("id");
        if (homeworkId == null) {
            finish();
            return;
        }

        btnPickGallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                galleryUrlLauncher.launch(new String[]{"image/*"});
            }
        });

        btnPickBase64.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                base64Launcher.launch("image/*");
            }
        });

        // מסך Base64 לפי מצגת 05.04 (צילום / גלריה -> שמירה -> קריאה)
        findViewById(R.id.btnOpenBase64Screen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(ImagesActivity.this, Base64ImageActivity.class);
                intent.putExtra("id", homeworkId);
                startActivity(intent);
            }
        });

        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    cameraLauncher.launch(null);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(ImagesActivity.this, "לא נמצאה אפליקציית מצלמה", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    // שליפת התמונות מ-Firestore והצגתן. המאזין מעדכן את המסך אחרי כל שמירה
    @Override
    protected void onStart() {
        super.onStart();
        imageDAO.listenToImages(homeworkId, new IImagesCallback() {
            @Override
            public void onImagesReceived(String galleryUrl, String base64Image) {
                showGalleryImage(galleryUrl);
                showBase64Image(base64Image);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        imageDAO.stopListening();
    }

    // ---------- דרך 1: גלריה + URL ----------

    private void saveGalleryUrl(Uri uri) {
        try {
            // בלי זה ההרשאה לקרוא את התמונה נעלמת כשסוגרים את המסך
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // יש ספקי תמונות שלא נותנים הרשאה קבועה - התמונה עדיין תוצג עכשיו
        }

        imageDAO.saveGalleryUrl(homeworkId, uri.toString(), new IFirestoreActionCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ImagesActivity.this, "שגיאה בשמירה: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
        Toast.makeText(this, "ה-URL של התמונה נשמר", Toast.LENGTH_SHORT).show();
    }

    // הצגת התמונה דרך ה-URL ששמור ב-Firestore
    private void showGalleryImage(String galleryUrl) {
        if (galleryUrl == null) {
            tvGalleryUrl.setText("עוד לא נבחרה תמונה");
            ivGallery.setImageDrawable(null);
            return;
        }
        tvGalleryUrl.setText("URL: " + galleryUrl);
        try {
            ivGallery.setImageURI(Uri.parse(galleryUrl));
        } catch (SecurityException e) {
            // ה-URL הוא של גלריה במכשיר אחר / בלי הרשאה
            ivGallery.setImageDrawable(null);
            tvGalleryUrl.setText("URL: " + galleryUrl + "\n(התמונה זמינה רק במכשיר שבחר אותה)");
        }
    }

    // ---------- דרך 2: Base64 ----------

    private void convertAndSaveBase64(Uri uri) {
        btnPickBase64.setEnabled(false);
        tvBase64Info.setText("ממיר את התמונה...");

        // דחיסה והמרה יכולות לקחת שנייה - עושים אותן ב-Thread נפרד כדי שהמסך לא ייתקע
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = decodeSampledBitmap(uri);
                String base64 = (bitmap == null) ? null : ImageUtils.convertBitmapToBase64(bitmap);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnPickBase64.setEnabled(true);
                        if (base64 == null) {
                            tvBase64Info.setText("");
                            Toast.makeText(ImagesActivity.this, "לא הצלחתי להמיר את התמונה", Toast.LENGTH_LONG).show();
                            return;
                        }
                        imageDAO.saveBase64Image(homeworkId, base64, new IFirestoreActionCallback() {
                            @Override
                            public void onSuccess() {
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                Toast.makeText(ImagesActivity.this, "שגיאה בשמירה: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                        Toast.makeText(ImagesActivity.this, "התמונה נשמרה כ-Base64", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // צילום -> כיווץ ל-JPEG (ImageUtils) -> Base64 -> שמירה במסמך ב-Firestore
    private void compressAndSaveBitmap(Bitmap bitmap) {
        String base64 = ImageUtils.convertBitmapToBase64(bitmap);
        if (base64 == null) {
            Toast.makeText(this, "לא הצלחתי לכווץ את התמונה", Toast.LENGTH_LONG).show();
            return;
        }
        imageDAO.saveBase64Image(homeworkId, base64, new IFirestoreActionCallback() {
            @Override
            public void onSuccess() {
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ImagesActivity.this, "שגיאה בשמירה: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
        Toast.makeText(this, "התמונה צולמה, כווצה ונשמרה", Toast.LENGTH_SHORT).show();
    }

    // המרה חזרה: מחרוזת Base64 מה-Firestore -> Bitmap -> ImageView
    private void showBase64Image(String base64Image) {
        if (base64Image == null) {
            tvBase64Info.setText("עוד לא נשמרה תמונה");
            ivBase64.setImageDrawable(null);
            return;
        }
        Bitmap bitmap = ImageUtils.convertBase64ToBitmap(base64Image);
        ivBase64.setImageBitmap(bitmap);
        tvBase64Info.setText("נשמר במסמך: " + base64Image.length() + " תווים");
    }

    // קריאת התמונה בגודל מוקטן, כדי לא לטעון לזיכרון תמונה ענקית מהמצלמה
    private Bitmap decodeSampledBitmap(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, options);
            if (in != null) {
                in.close();
            }

            int sampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / sampleSize > 2560) {
                sampleSize *= 2;
            }

            options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            in = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (in != null) {
                in.close();
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}
