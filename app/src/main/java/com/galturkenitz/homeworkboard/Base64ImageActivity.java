package com.galturkenitz.homeworkboard;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.rabin.firebasefirestoreapplication.ImageUtils;

import java.io.IOException;

// מצגת 05.04: כתיבה וקריאה של תמונות ב-Firestore באמצעות Base64
// צילום מוקטן או בחירה מהגלריה -> כיווץ + Base64 (ImageUtils) -> Firestore (FirestoreImageDAO) -> ובחזרה
// המסך מנהל רק את ממשק המשתמש, המצלמה והגלריה
public class Base64ImageActivity extends BaseActivity {
    private ImageView ivBase64Image;
    private Bitmap selectedBitmap;

    private FirestoreImageDAO firestoreImageDAO;
    private String firestoreImageFileName;

    private ActivityResultLauncher<Void> cameraPreviewLauncher;
    private ActivityResultLauncher<String> galleryImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreen(R.layout.activity_base64_image, "תמונה ב-Base64", true);

        ivBase64Image = findViewById(R.id.ivBase64Image);
        firestoreImageDAO = new FirestoreImageDAO();

        // שם הקובץ ב-Firestore = מזהה המשימה, כך שלכל משימה יש תמונה משלה.
        // אחרי סיבוב מסך לוקחים אותו מה-Bundle
        if (savedInstanceState != null) {
            firestoreImageFileName = savedInstanceState.getString("firestoreImageFileName");
        } else {
            firestoreImageFileName = getIntent().getStringExtra("id");
        }
        if (firestoreImageFileName == null) {
            finish();
            return;
        }

        // 1. צילום תמונה מוקטנת (מחזיר Bitmap ישירות, לא נשמר בגלריה)
        cameraPreviewLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                new ActivityResultCallback<Bitmap>() {
                    @Override
                    public void onActivityResult(Bitmap bitmap) {
                        if (bitmap == null) {
                            showToastMessage("הצילום בוטל");
                            return;
                        }
                        selectedBitmap = bitmap;
                        ivBase64Image.setImageBitmap(bitmap);
                    }
                });

        findViewById(R.id.btnTakeBase64Photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cameraPreviewLauncher.launch(null);
            }
        });

        // 2. בחירה רגילה מהגלריה (מחזירה Uri שמומר ל-Bitmap)
        galleryImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri == null) {
                            showToastMessage("לא נבחרה תמונה");
                            return;
                        }
                        try {
                            Bitmap bitmap = ImageUtils.convertUriToBitmap(Base64ImageActivity.this, uri);
                            if (bitmap == null) {
                                showToastMessage("לא ניתן לטעון את התמונה");
                                return;
                            }
                            selectedBitmap = bitmap;
                            ivBase64Image.setImageBitmap(bitmap);
                        } catch (IOException | RuntimeException error) {
                            showToastMessage("שגיאה: " + error.getMessage());
                        }
                    }
                });

        findViewById(R.id.btnPickBase64Gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                galleryImageLauncher.launch("image/*");
            }
        });

        findViewById(R.id.btnSaveBase64).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveImageViewAsBase64ToFirestore();
            }
        });

        findViewById(R.id.btnReadBase64).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                readFromFirestore();
            }
        });

        // כשנכנסים למסך - שולפים את התמונה ששמורה למשימה (אם יש)
        if (savedInstanceState == null) {
            readFromFirestore();
        }
    }

    // שמירת Bitmap או ImageView ל-Firestore
    private void saveBitmapAsBase64ToFirestore(Bitmap bitmap) {
        if (bitmap == null) {
            showToastMessage("צלמו או בחרו תמונה תחילה");
            return;
        }

        String base64 = ImageUtils.convertBitmapToBase64(bitmap);
        if (base64 == null) {
            showToastMessage("לא ניתן להמיר את התמונה ל-Base64");
            return;
        }

        firestoreImageDAO.saveToFirestore(firestoreImageFileName, base64, new IFirestoreImageCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showToastMessage("התמונה נשמרה ב-Firestore");
            }

            @Override
            public void onFailure(Exception error) {
                showToastMessage("שמירה נכשלה: " + error.getMessage());
            }
        });
    }

    private void saveImageViewAsBase64ToFirestore() {
        Bitmap bitmap = ImageUtils.convertImageViewToBitmap(ivBase64Image);
        if (bitmap == null) {
            showToastMessage("אין תמונה לשמירה ב-ImageView");
            return;
        }
        saveBitmapAsBase64ToFirestore(bitmap);
    }

    // קריאה מ-Firestore והצגה ב-ImageView
    private void readFromFirestore() {
        firestoreImageDAO.readFromFirestore(firestoreImageFileName, new IFirestoreImageCallback<String>() {
            @Override
            public void onSuccess(String base64) {
                Bitmap bitmap = ImageUtils.convertBase64ToBitmap(base64);
                if (bitmap == null) {
                    showToastMessage("לא ניתן לפענח את התמונה");
                    return;
                }
                selectedBitmap = bitmap;
                ivBase64Image.setImageBitmap(bitmap);
                showToastMessage("התמונה נטענה מ-Firestore");
            }

            @Override
            public void onFailure(Exception error) {
                showToastMessage("קריאה נכשלה: " + error.getMessage());
            }
        });
    }

    // שמירת שם הקובץ בסיבוב מסך
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("firestoreImageFileName", firestoreImageFileName);
        super.onSaveInstanceState(outState);
    }

    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
