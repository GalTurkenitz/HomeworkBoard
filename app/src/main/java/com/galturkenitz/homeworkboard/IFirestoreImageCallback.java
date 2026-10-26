package com.galturkenitz.homeworkboard;

// מצגת 05.04: Callback גנרי - תקשורת רשת לוקחת זמן, אי אפשר לעשות return מהענן.
// המסך מתעדכן רק כשהתשובה חוזרת (אחרת ה-Main UI Thread נתקע ונקבל ANR)
public interface IFirestoreImageCallback<T> {
    void onSuccess(T result);

    void onFailure(Exception error);
}
