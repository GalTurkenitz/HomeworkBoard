package com.galturkenitz.homeworkboard;

// מחזיר ל-Activity את תמונת ה-Base64 של המשימה (null אם עוד לא נשמרה)
public interface IImagesCallback {
    void onImageReceived(String base64Image);
}
