package com.galturkenitz.homeworkboard;

import java.util.ArrayList;

// חלק 5: ממשק Callback - מאפשר ל-DAO להחזיר מידע ל-Activity בלי להכיר את רכיבי ה-UI
public interface IFirestoreCallback {
    void onDataReceived(ArrayList<Homework> homeworkList);
}
