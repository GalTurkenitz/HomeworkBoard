package com.galturkenitz.homeworkboard;

// חלק 10: טיפול בשגיאות - מחזיר ל-Activity אם פעולת כתיבה/עדכון/מחיקה הצליחה,
// כדי שה-Activity (ולא ה-DAO) תציג Toast למשתמש
public interface IFirestoreActionCallback {
    void onSuccess();

    void onFailure(String errorMessage);
}
