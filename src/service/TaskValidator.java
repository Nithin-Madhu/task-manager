package service;

import exception.FieldIssue;
import exception.ValidationException;
import model.Priority;
import model.Task;
import model.TaskRequest;

import java.util.ArrayList;
import java.util.List;

public final class TaskValidator {
    private TaskValidator() {}

    /* Throws ValidationException with every problem found. */
    public static Task validate(TaskRequest r) {
        List<FieldIssue> issues = new ArrayList<>();

        if (r == null) {
            issues.add(new FieldIssue("request", "must not be null"));
            throw new ValidationException(issues);
        }

        if (isBlank(r.getTitle())) {
            issues.add(new FieldIssue("title", "must not be empty"));
        }
        if (isBlank(r.getDescription())) {
            issues.add(new FieldIssue("description", "must not be empty"));
        }

        boolean completed = false;
        String c = r.getCompleted();
        if (c != null) {
            if (c.equals("true")) {
                completed = true;
            } else if (!c.equals("false")) {
                issues.add(new FieldIssue("completed", "must be 'true' or 'false'"));
            }
        }

        String due = r.getDueDate();
        if (due != null && !isRealDate(due)) {
            issues.add(new FieldIssue("dueDate", "must be a valid date in YYYY-MM-DD format"));
        }

        Priority priority = Priority.MEDIUM;
        String p = r.getPriority();
        if (p != null) {
            try {
                priority = Priority.valueOf(p);
            } catch (IllegalArgumentException e) {
                issues.add(new FieldIssue("priority", "must be LOW, MEDIUM, or HIGH"));
            }
        }

        if (!issues.isEmpty()) {
            throw new ValidationException(issues);
        }

        return new Task.Builder(r.getTitle(), r.getDescription(), completed)
                .dueDate(due)
                .priority(priority)
                .build();
    }

    public static boolean isRealDate(String s) {
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        int year = Integer.parseInt(s.substring(0, 4));
        int month = Integer.parseInt(s.substring(5, 7));
        int day = Integer.parseInt(s.substring(8, 10));
        if (month < 1 || month > 12 || day < 1) return false;
        return day <= daysInMonth(year, month);
    }

    private static int daysInMonth(int year, int month) {
        switch (month) {
            case 2:  return isLeap(year) ? 29 : 28;
            case 4: case 6: case 9: case 11: return 30;
            default: return 31;
        }
    }

    private static boolean isLeap(int year) {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}