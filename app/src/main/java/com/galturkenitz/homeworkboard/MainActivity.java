package com.galturkenitz.homeworkboard;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

// מסך ראשי: רשימת כל שיעורי הבית של הכיתה, מתעדכנת בזמן אמת + סינון לפי מקצוע
public class MainActivity extends BaseActivity {
    private Spinner spinnerFilter;
    private CheckBox cbOnlyNotDone;
    private ListView lvHomework;
    private TextView tvSummary;
    private Button btnAdd;

    private FirestoreDAO fsDAO;
    private ArrayList<Homework> allHomework = new ArrayList<>();   // כל המשימות (מהמאזין)
    private ArrayList<Homework> shownHomework = new ArrayList<>(); // מה שמוצג אחרי סינון
    private ArrayAdapter<Homework> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupScreen(R.layout.activity_main, getString(R.string.app_name), false);

        spinnerFilter = findViewById(R.id.spinnerFilter);
        cbOnlyNotDone = findViewById(R.id.cbOnlyNotDone);
        lvHomework = findViewById(R.id.lvHomework);
        tvSummary = findViewById(R.id.tvSummary);
        btnAdd = findViewById(R.id.btnAdd);

        fsDAO = new FirestoreDAO();

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, shownHomework);
        lvHomework.setAdapter(adapter);

        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(this,
                R.array.subjects_filter, android.R.layout.simple_spinner_item);
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(filterAdapter);

        // שינוי מקצוע בסינון -> שאילתה חדשה
        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refreshList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        cbOnlyNotDone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                refreshList();
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, AddEditActivity.class));
            }
        });

        // לחיצה על משימה -> מסך הפרטים שלה
        lvHomework.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(MainActivity.this, DetailsActivity.class);
                intent.putExtra("id", shownHomework.get(position).getId());
                startActivity(intent);
            }
        });

        // לחיצה ארוכה -> סימון מהיר כבוצע / לא בוצע
        lvHomework.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showToggleDoneDialog(shownHomework.get(position));
                return true;
            }
        });
    }

    // חלק 9: מפעילים את המאזין ב-onStart ומכבים ב-onStop
    @Override
    protected void onStart() {
        super.onStart();
        fsDAO.listenToAllDocumentsChange(new IFirestoreCallback() {
            @Override
            public void onDataReceived(ArrayList<Homework> homeworkList) {
                allHomework = homeworkList;
                refreshList();
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        fsDAO.stopListening();
    }

    // מציג את הרשימה לפי הסינון שנבחר.
    // בלי סינון - מציגים את מה שהגיע מהמאזין. עם סינון - שאילתת where (חלק 6)
    private void refreshList() {
        String subject = getSelectedSubject();
        boolean onlyNotDone = cbOnlyNotDone.isChecked();

        if (subject == null && !onlyNotDone) {
            showList(allHomework);
        } else {
            fsDAO.readFilteredDocuments(subject, onlyNotDone, new IFirestoreCallback() {
                @Override
                public void onDataReceived(ArrayList<Homework> homeworkList) {
                    showList(homeworkList);
                }
            });
        }
    }

    // null = "כל המקצועות"
    private String getSelectedSubject() {
        if (spinnerFilter.getSelectedItemPosition() == 0) {
            return null;
        }
        return spinnerFilter.getSelectedItem().toString();
    }

    private void showList(ArrayList<Homework> homeworkList) {
        // מיון לפי תאריך הגשה - הקרוב ביותר ראשון
        Collections.sort(homeworkList, new Comparator<Homework>() {
            @Override
            public int compare(Homework h1, Homework h2) {
                return DateUtils.toSortKey(h1.getDueDate()).compareTo(DateUtils.toSortKey(h2.getDueDate()));
            }
        });

        shownHomework.clear();
        shownHomework.addAll(homeworkList);
        adapter.notifyDataSetChanged();

        int doneCount = 0;
        for (Homework homework : homeworkList) {
            if (homework.isDone()) {
                doneCount++;
            }
        }
        if (homeworkList.isEmpty()) {
            tvSummary.setText("אין משימות להצגה");
        } else {
            tvSummary.setText("סה\"כ " + homeworkList.size() + " משימות, " + doneCount + " בוצעו");
        }
    }

    private void showToggleDoneDialog(Homework homework) {
        boolean newDone = !homework.isDone();
        String question = newDone ? "לסמן את \"" + homework.getTitle() + "\" כבוצעה?"
                : "לסמן את \"" + homework.getTitle() + "\" כלא בוצעה?";

        new AlertDialog.Builder(this)
                .setTitle("עדכון סטטוס")
                .setMessage(question)
                .setPositiveButton("כן", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fsDAO.updateDone(homework.getId(), newDone, new IFirestoreActionCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(MainActivity.this, "הסטטוס עודכן", Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(String errorMessage) {
                                Toast.makeText(MainActivity.this, "שגיאה: " + errorMessage, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    // תפריט אפשרויות
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menuAdd) {
            startActivity(new Intent(this, AddEditActivity.class));
            return true;
        } else if (id == R.id.menuHelp) {
            new AlertDialog.Builder(this)
                    .setTitle("עזרה")
                    .setMessage("• לחיצה על משימה - פרטים, עריכה ומחיקה\n"
                            + "• לחיצה ארוכה - סימון מהיר כבוצע\n"
                            + "• אפשר לסנן לפי מקצוע ולהציג רק מה שלא בוצע\n"
                            + "• הרשימה משותפת לכל הכיתה ומתעדכנת בזמן אמת")
                    .setPositiveButton("הבנתי", null)
                    .show();
            return true;
        } else if (id == R.id.menuAbout) {
            new AlertDialog.Builder(this)
                    .setTitle("אודות")
                    .setMessage("לוח שיעורי בית כיתתי\nפרויקט ביניים - הנדסת תוכנה י\"ב\nAndroid + Cloud Firestore")
                    .setPositiveButton("סגור", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
