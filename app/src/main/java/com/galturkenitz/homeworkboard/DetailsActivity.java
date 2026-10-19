package com.galturkenitz.homeworkboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

// מסך פרטי משימה: מציג את המשימה בזמן אמת, מאפשר לסמן כבוצעה, לערוך ולמחוק
public class DetailsActivity extends AppCompatActivity {
    private TextView tvDetails;
    private Button btnToggleDone, btnEdit, btnDelete;

    private FirestoreDAO fsDAO;
    private String homeworkId;
    private Homework currentHomework;
    private boolean deletedByMe = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_details);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        setTitle("פרטי משימה");

        tvDetails = findViewById(R.id.tvDetails);
        btnToggleDone = findViewById(R.id.btnToggleDone);
        btnEdit = findViewById(R.id.btnEdit);
        btnDelete = findViewById(R.id.btnDelete);

        fsDAO = new FirestoreDAO();
        homeworkId = getIntent().getStringExtra("id");
        if (homeworkId == null) {
            finish();
            return;
        }

        btnToggleDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleDone();
            }
        });

        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DetailsActivity.this, AddEditActivity.class);
                intent.putExtra("id", homeworkId);
                startActivity(intent);
            }
        });

        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDeleteDialog();
            }
        });
    }

    // חלק 9: האזנה למסמך הבודד - אם מישהו אחר עדכן את המשימה, המסך מתעדכן מיד
    @Override
    protected void onStart() {
        super.onStart();
        fsDAO.listenToADocumentChange(homeworkId, new IFirestoreCallback() {
            @Override
            public void onDataReceived(ArrayList<Homework> homeworkList) {
                if (homeworkList.isEmpty()) {
                    if (!deletedByMe) {
                        Toast.makeText(DetailsActivity.this, "המשימה נמחקה", Toast.LENGTH_SHORT).show();
                    }
                    finish();
                    return;
                }
                currentHomework = homeworkList.get(0);
                tvDetails.setText(currentHomework.toDetailsString());
                btnToggleDone.setText(currentHomework.isDone() ? "סמן כלא בוצע" : "סמן כבוצע ✔");
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        fsDAO.stopListening();
    }

    // חלק 7: UPDATE של שדה בודד
    private void toggleDone() {
        if (currentHomework == null) {
            return;
        }
        fsDAO.updateDone(homeworkId, !currentHomework.isDone(), new IFirestoreActionCallback() {
            @Override
            public void onSuccess() {
                // אין צורך לעדכן את המסך - המאזין יעשה את זה
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(DetailsActivity.this, "שגיאה: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    // חלק 8: DELETE, עם דיאלוג אישור
    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("מחיקת משימה")
                .setMessage("למחוק את המשימה לכל הכיתה? אי אפשר לבטל את זה.")
                .setPositiveButton("מחק", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deletedByMe = true;
                        fsDAO.deleteDocument(homeworkId, new IFirestoreActionCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(DetailsActivity.this, "המשימה נמחקה", Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                deletedByMe = false;
                                Toast.makeText(DetailsActivity.this, "שגיאה במחיקה: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
