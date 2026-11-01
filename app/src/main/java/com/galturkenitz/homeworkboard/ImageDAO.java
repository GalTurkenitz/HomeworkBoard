package com.galturkenitz.homeworkboard;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

// שלב ג': גישה לתמונות של משימה ב-Firestore.
// התמונות נשמרות באוסף נפרד (homework_images) עם אותו מזהה כמו המשימה,
// כדי שהרשימה במסך הראשי לא תטען מחרוזות Base64 כבדות
public class ImageDAO {
    private static final String COLLECTION_NAME = "homework_images";
    private static final String TAG = "IMAGE_DAO";

    public static final String FIELD_BASE64 = "base64Image"; // התמונה עצמה כמחרוזת Base64

    private ListenerRegistration listenerRegistration;

    // שמירת שדה אחד בלי למחוק את השני (merge)
    private void saveField(String homeworkId, String field, String value, IFirestoreActionCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("homeworkId", homeworkId);
        data.put(field, value);

        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection(COLLECTION_NAME).document(homeworkId);

        docRef.set(data, SetOptions.merge()).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.d(TAG, "SUCCESSFUL WRITE: " + field);
                callback.onSuccess();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, e.getMessage());
                callback.onFailure(e.getMessage());
            }
        });
    }

    // שמירת התמונה כמחרוזת Base64 ישירות במסמך
    public void saveBase64Image(String homeworkId, String base64, IFirestoreActionCallback callback) {
        saveField(homeworkId, FIELD_BASE64, base64, callback);
    }

    // שליפה + האזנה לשינויים: המסך מתעדכן מיד אחרי שמירה (וגם אם מישהו אחר החליף תמונה)
    public void listenToImage(String homeworkId, IImagesCallback callback) {
        stopListening();
        DocumentReference docRef = FirebaseFirestore.getInstance()
                .collection(COLLECTION_NAME).document(homeworkId);

        listenerRegistration = docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    Log.d(TAG, error.getMessage());
                    return;
                }
                if (value == null || !value.exists()) {
                    callback.onImageReceived(null);
                    return;
                }
                callback.onImageReceived(value.getString(FIELD_BASE64));
            }
        });
    }

    public void stopListening() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }
}
