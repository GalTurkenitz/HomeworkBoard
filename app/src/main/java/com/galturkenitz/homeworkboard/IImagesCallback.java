package com.galturkenitz.homeworkboard;

// מחזיר ל-Activity את שתי התמונות של משימה (כל אחת יכולה להיות null אם עוד לא נשמרה)
public interface IImagesCallback {
    void onImagesReceived(String galleryUrl, String base64Image);
}
