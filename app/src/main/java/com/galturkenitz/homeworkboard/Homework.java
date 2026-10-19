package com.galturkenitz.homeworkboard;

// מחלקת המודל - Firestore דורש בנאי ריק + Getter/Setter לכל תכונה (בשביל toObject)
public class Homework {
    private String id, subject, title, description, dueDate, addedBy;
    private boolean done;

    public Homework() {}

    public Homework(String id, String subject, String title, String description,
                    String dueDate, String addedBy, boolean done) {
        this.id = id;
        this.subject = subject;
        this.title = title;
        this.description = description;
        this.dueDate = dueDate;
        this.addedBy = addedBy;
        this.done = done;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    // מה שמוצג בשורה ברשימה במסך הראשי
    @Override
    public String toString() {
        String status;
        if (done) {
            status = "✔ בוצע";
        } else if (DateUtils.isPast(dueDate)) {
            status = "⚠ באיחור";
        } else {
            status = "⏳ לא בוצע";
        }
        return subject + " | " + title + "\nלהגשה: " + dueDate + "   " + status;
    }

    // פירוט מלא - מוצג במסך פרטי המשימה
    public String toDetailsString() {
        return "מקצוע: " + subject
                + "\nמשימה: " + title
                + "\nפירוט: " + (description.isEmpty() ? "-" : description)
                + "\nתאריך הגשה: " + dueDate
                + "\nהוסיף/ה: " + addedBy
                + "\nסטטוס: " + (done ? "בוצע ✔" : "לא בוצע");
    }
}
