package com.galturkenitz.homeworkboard;

import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
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

// תמונה למשימה: צילום במצלמה או בחירה מהגלריה.
// מיד אחרי הצילום/הבחירה - בלי לשאול - התמונה מומרת ל-Base64 ונשמרת ישירות במסמך ב-Firestore,
// ומשם היא נשלפת, מומרת חזרה ל-Bitmap ומוצגת
public class ImagesActivity extends BaseActivity {
    private ImageView ivBase64;
    private TextView tvBase64Info;
    private Button btnTakePhoto, btnPickImage;

    private ImageDAO imageDAO;
    private String homeworkId;

    // 1. צילום במצלמה: TakePicturePreview מחזיר Bitmap ישירות, בלי לשמור קובץ בגלריה
    private final ActivityResultLauncher<Void> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), new ActivityResultCallback<Bitmap>() {
                @Override
                public void onActivityResult(Bitmap bitmap) {
                    if (bitmap == null) {
                        showToastMessage("הצילום בוטל");
                        return;
                    }
                    convertAndSave(bitmap, null);
                }
            });

    // 2. בחירה מהגלריה: מחזירה Uri שמומר ל-Bitmap
    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri == null) {
                        showToastMessage("לא נבחרה תמונה");
                        return;
                    }
                    convertAndSave(null, uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreen(R.layout.activity_images, "תמונה למשימה", true);

        ivBase64 = findViewById(R.id.ivBase64);
        tvBase64Info = findViewById(R.id.tvBase64Info);
        btnTakePhoto = findViewById(R.id.btnTakePhoto);
        btnPickImage = findViewById(R.id.btnPickImage);

        imageDAO = new ImageDAO();
        homeworkId = getIntent().getStringExtra("id");
        if (homeworkId == null) {
            finish();
            return;
        }

        btnTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    cameraLauncher.launch(null);
                } catch (ActivityNotFoundException e) {
                    showToastMessage("לא נמצאה אפליקציית מצלמה");
                }
            }
        });

        btnPickImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                galleryLauncher.launch("image/*");
            }
        });
    }

    // שליפת התמונה מ-Firestore והצגתה. המאזין מעדכן את המסך מיד אחרי כל שמירה
    @Override
    protected void onStart() {
        super.onStart();
        imageDAO.listenToImage(homeworkId, new IImagesCallback() {
            @Override
            public void onImageReceived(String base64Image) {
                showBase64Image(base64Image);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        imageDAO.stopListening();
    }

    // Bitmap (מהמצלמה) או Uri (מהגלריה) -> כיווץ -> Base64 -> שמירה במסמך ב-Firestore.
    // הטעינה והכיווץ יכולים לקחת שנייה, לכן הם רצים ב-Thread נפרד כדי שהמסך לא ייתקע
    private void convertAndSave(final Bitmap cameraBitmap, final Uri galleryUri) {
        setButtonsEnabled(false);
        tvBase64Info.setText("ממיר ושומר את התמונה...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                String base64 = null;
                try {
                    Bitmap bitmap = cameraBitmap;
                    if (bitmap == null) {
                        bitmap = ImageUtils.convertUriToBitmap(ImagesActivity.this, galleryUri);
                    }
                    if (bitmap != null) {
                        base64 = ImageUtils.convertBitmapToBase64(bitmap);
                    }
                } catch (Exception e) {
                    base64 = null;
                }

                final String result = base64;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        saveBase64(result);
                    }
                });
            }
        }).start();
    }

    private void saveBase64(String base64) {
        if (base64 == null) {
            setButtonsEnabled(true);
            tvBase64Info.setText("");
            showToastMessage("לא הצלחתי להמיר את התמונה");
            return;
        }

        imageDAO.saveBase64Image(homeworkId, base64, new IFirestoreActionCallback() {
            @Override
            public void onSuccess() {
                setButtonsEnabled(true);
                showToastMessage("התמונה נשמרה ב-Firestore");
            }

            @Override
            public void onFailure(String errorMessage) {
                setButtonsEnabled(true);
                showToastMessage("שגיאה בשמירה: " + errorMessage);
            }
        });
    }

    // המרה חזרה: מחרוזת Base64 מה-Firestore -> Bitmap -> ImageView
    private void showBase64Image(String base64Image) {
        if (base64Image == null) {
            tvBase64Info.setText("עוד לא נשמרה תמונה");
            ivBase64.setImageDrawable(null);
            return;
        }
        Bitmap bitmap = ImageUtils.convertBase64ToBitmap(base64Image);
        if (bitmap == null) {
            tvBase64Info.setText("לא ניתן לפענח את התמונה");
            ivBase64.setImageDrawable(null);
            return;
        }
        ivBase64.setImageBitmap(bitmap);
        tvBase64Info.setText("נשמר במסמך: " + base64Image.length() + " תווים");
    }

    private void setButtonsEnabled(boolean enabled) {
        btnTakePhoto.setEnabled(enabled);
        btnPickImage.setEnabled(enabled);
    }

    private void showToastMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
