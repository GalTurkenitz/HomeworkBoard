package com.galturkenitz.homeworkboard;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FirestoreDAO {  //Data Access Object
    private static final String COLLECTION_NAME = "homework";
    private static final String TAG = "DAO_FIREBASE";

    // חלק 9: שומרים את המאזין כדי שנוכל לכבות אותו ב-onStop
    private ListenerRegistration listenerRegistration;

    // יוצר מזהה חדש למסמך (Firestore מגריל מזהה ייחודי)
    public String generateNewId() {
        return FirebaseFirestore.getInstance().collection(COLLECTION_NAME).document().getId();
    }

    // חלק 4: כתיבה למסד הנתונים (CREATE) - משמש גם לעריכה מלאה (SET דורס את כל המסמך)
    public void createDocument(String id, String subject, String title, String description,
                               String dueDate, String addedBy, boolean done,
                               IFirestoreActionCallback callback) {
        Map<String, Object> homeworkRecord = new HashMap<>();
        homeworkRecord.put("id", id);
        homeworkRecord.put("subject", subject);
        homeworkRecord.put("title", title);
        homeworkRecord.put("description", description);
        homeworkRecord.put("dueDate", dueDate);
        homeworkRecord.put("addedBy", addedBy);
        homeworkRecord.put("done", done);

        DocumentReference docRef = FirebaseFirestore.getInstance().
                collection(COLLECTION_NAME).document(id);

        docRef.set(homeworkRecord).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.d(TAG, "SUCCESSFUL WRITE!");
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

    // חלק 5: קריאת מסמך בודד לפי המזהה שלו (READ)
    public void readDocumentById(String docID, IFirestoreCallback callback) {
        DocumentReference docRef = FirebaseFirestore.getInstance().
                collection(COLLECTION_NAME).document(docID);

        docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                Homework homework = documentSnapshot.toObject(Homework.class);
                ArrayList<Homework> homeworkList = new ArrayList<>();
                if (homework != null) {
                    homeworkList.add(homework);
                }
                callback.onDataReceived(homeworkList);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, e.getMessage());
                callback.onDataReceived(new ArrayList<>());
            }
        });
    }

    // חלק 6: קריאה מרובת מסמכים עם סינון (where + Filter)
    // subject == null -> כל המקצועות. onlyNotDone -> רק משימות שעוד לא בוצעו
    public void readFilteredDocuments(String subject, boolean onlyNotDone, IFirestoreCallback callback) {
        CollectionReference colRef = FirebaseFirestore.getInstance().collection(COLLECTION_NAME);
        Query query;

        if (subject != null && onlyNotDone) {
            query = colRef.where(Filter.and(
                    Filter.equalTo("subject", subject),
                    Filter.equalTo("done", false)));
        } else if (subject != null) {
            query = colRef.where(Filter.equalTo("subject", subject));
        } else if (onlyNotDone) {
            query = colRef.whereEqualTo("done", false);
        } else {
            query = colRef;
        }

        query.get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
            @Override
            public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                ArrayList<Homework> homeworkList = new ArrayList<>();
                for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                    homeworkList.add(doc.toObject(Homework.class));
                }
                callback.onDataReceived(homeworkList);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, e.getMessage());
                callback.onDataReceived(new ArrayList<>());
            }
        });
    }

    // חלק 7: עדכון שדה בודד (UPDATE) - סימון משימה כבוצעה / לא בוצעה
    public void updateDone(String docID, boolean newDone, IFirestoreActionCallback callback) {
        DocumentReference docRef = FirebaseFirestore.getInstance().
                collection(COLLECTION_NAME).document(docID);

        docRef.update("done", newDone).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.d(TAG, "SUCCESSFUL UPDATE!");
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

    // חלק 8: מחיקת מסמך (DELETE)
    public void deleteDocument(String docID, IFirestoreActionCallback callback) {
        DocumentReference docRef = FirebaseFirestore.getInstance().
                collection(COLLECTION_NAME).document(docID);

        docRef.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.d(TAG, "SUCCESSFUL DELETE!");
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

    // חלק 9: האזנה לכל האוסף בזמן אמת - כל שינוי של תלמיד אחר מגיע מיד לכל המכשירים
    public void listenToAllDocumentsChange(IFirestoreCallback callback) {
        stopListening(); // מונע האזנה כפולה (חלק 10)
        CollectionReference colRef = FirebaseFirestore.getInstance().collection(COLLECTION_NAME);

        listenerRegistration = colRef.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    Log.d(TAG, error.getMessage());
                    return;
                }
                if (value == null) {
                    return;
                }
                for (DocumentChange change : value.getDocumentChanges()) {
                    Homework homework = change.getDocument().toObject(Homework.class);
                    Log.d(TAG, change.getType() + ": " + homework.getTitle());
                }
                ArrayList<Homework> homeworkList = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    homeworkList.add(doc.toObject(Homework.class));
                }
                callback.onDataReceived(homeworkList);
            }
        });
    }

    // חלק 9: האזנה למסמך בודד - מסך הפרטים מתעדכן אם מישהו אחר שינה/מחק את המשימה
    public void listenToADocumentChange(String docID, IFirestoreCallback callback) {
        stopListening();
        DocumentReference docRef = FirebaseFirestore.getInstance().
                collection(COLLECTION_NAME).document(docID);

        listenerRegistration = docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if (error != null) {
                    Log.d(TAG, error.getMessage());
                    return;
                }
                ArrayList<Homework> homeworkList = new ArrayList<>();
                if (value != null && value.exists()) {
                    homeworkList.add(value.toObject(Homework.class));
                }
                callback.onDataReceived(homeworkList); // רשימה ריקה = המשימה נמחקה
            }
        });
    }

    // חלק 9: כיבוי המאזין
    public void stopListening() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }
}
