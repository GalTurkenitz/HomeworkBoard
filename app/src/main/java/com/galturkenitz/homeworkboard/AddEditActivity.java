package com.galturkenitz.homeworkboard;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Calendar;

// מסך הוספה / עריכה של משימה.
// אם הגיע "id" ב-Intent - מצב עריכה (קוראים את המשימה ומעדכנים אותה), אחרת - משימה חדשה
public class AddEditActivity extends AppCompatActivity {
    private Spinner spinnerSubject;
    private EditText etTitle, etDescription, etAddedBy;
    private Button btnDate, btnSave;
    private TextView tvScreenTitle;

    private FirestoreDAO fsDAO;
    private String editId = null;       // null = משימה חדשה
    private String selectedDate = null;  // dd/MM/yyyy
    private boolean currentDone = false; // בעריכה שומרים את הסטטוס הקיים

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_edit);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
            return insets;
        });
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        spinnerSubject = findViewById(R.id.spinnerSubject);
        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etAddedBy = findViewById(R.id.etAddedBy);
        btnDate = findViewById(R.id.btnDate);
        btnSave = findViewById(R.id.btnSave);
        tvScreenTitle = findViewById(R.id.tvScreenTitle);

        fsDAO = new FirestoreDAO();

        ArrayAdapter<CharSequence> subjectAdapter = ArrayAdapter.createFromResource(this,
                R.array.subjects, android.R.layout.simple_spinner_item);
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(subjectAdapter);

        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDatePicker();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveHomework();
            }
        });

        editId = getIntent().getStringExtra("id");
        if (editId != null) {
            setTitle("עריכת משימה");
            tvScreenTitle.setText("עריכת משימה");
            loadHomeworkForEdit();
        } else {
            setTitle("משימה חדשה");
        }
    }

    // חלק 5: במצב עריכה - קוראים את המשימה לפי המזהה וממלאים את השדות
    private void loadHomeworkForEdit() {
        fsDAO.readDocumentById(editId, new IFirestoreCallback() {
            @Override
            public void onDataReceived(ArrayList<Homework> homeworkList) {
                if (homeworkList.isEmpty()) {
                    Toast.makeText(AddEditActivity.this, "המשימה לא נמצאה (אולי נמחקה)", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                Homework homework = homeworkList.get(0);
                String[] subjects = getResources().getStringArray(R.array.subjects);
                for (int i = 0; i < subjects.length; i++) {
                    if (subjects[i].equals(homework.getSubject())) {
                        spinnerSubject.setSelection(i);
                    }
                }
                etTitle.setText(homework.getTitle());
                etDescription.setText(homework.getDescription());
                etAddedBy.setText(homework.getAddedBy());
                selectedDate = homework.getDueDate();
                btnDate.setText("תאריך הגשה: " + selectedDate);
                currentDone = homework.isDone();
            }
        });
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                selectedDate = DateUtils.format(dayOfMonth, month, year);
                btnDate.setText("תאריך הגשה: " + selectedDate);
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    // חלק 10: בדיקת קלט לפני שמירה, כדי שהאפליקציה לא תקרוס ולא יישמרו נתונים חסרים
    private boolean isInputValid() {
        if (etTitle.getText().toString().trim().isEmpty()) {
            etTitle.setError("חובה לכתוב מה המשימה");
            return false;
        }
        if (etAddedBy.getText().toString().trim().isEmpty()) {
            etAddedBy.setError("חובה לכתוב את השם שלך");
            return false;
        }
        if (selectedDate == null) {
            Toast.makeText(this, "חובה לבחור תאריך הגשה", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (editId == null && DateUtils.isPast(selectedDate)) {
            Toast.makeText(this, "תאריך ההגשה כבר עבר", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void saveHomework() {
        if (!isInputValid()) {
            return;
        }
        btnSave.setEnabled(false); // מונע שמירה כפולה בלחיצה מהירה

        String id = (editId != null) ? editId : fsDAO.generateNewId();

        fsDAO.createDocument(id,
                spinnerSubject.getSelectedItem().toString(),
                etTitle.getText().toString().trim(),
                etDescription.getText().toString().trim(),
                selectedDate,
                etAddedBy.getText().toString().trim(),
                currentDone,
                new IFirestoreActionCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(AddEditActivity.this,
                                editId != null ? "המשימה עודכנה" : "המשימה נוספה", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        btnSave.setEnabled(true);
                        Toast.makeText(AddEditActivity.this, "שגיאה בשמירה: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // חץ החזרה בסרגל העליון
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
