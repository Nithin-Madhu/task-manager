package parser;

import java.time.LocalDate;
import java.util.List;

public class RuleBasedTaskParser implements TaskParser {
    private static final List<String> MARKERS = List.of(" on the ", " every ", " tomorrow", " today");

    @Override
    public ParsedTask parse(String text, String today) {
        String original = (text == null) ? "" : text.trim();
        String lower = original.toLowerCase();
        LocalDate base = LocalDate.parse(today);

        String dueDate = null;
        if (lower.contains("tomorrow")) {
            dueDate = base.plusDays(1).toString();
        } else if (lower.contains("today")) {
            dueDate = base.toString();
        } else {
            int day = findOrdinalDay(lower);
            if (day > 0) dueDate = nextOccurrence(base, day).toString();
        }

        String recurrence = null;
        if (lower.contains("every day") || lower.contains("daily")) recurrence = "daily";
        else if (lower.contains("every week") || lower.contains("weekly")) recurrence = "weekly";
        else if (lower.contains("every month") || lower.contains("monthly")) recurrence = "monthly";

        return new ParsedTask(extractTitle(original, lower), original, dueDate, recurrence);
    }

    private String extractTitle(String original, String lower) {
        String working = original;
        String workingLower = lower;
        String prefix = "remind me to ";
        if (workingLower.startsWith(prefix)) {
            working = working.substring(prefix.length());
            workingLower = workingLower.substring(prefix.length());
        }
        int cut = working.length();
        for (String m : MARKERS) {
            int i = workingLower.indexOf(m);
            if (i > 0 && i < cut) cut = i;
        }
        String title = working.substring(0, cut).trim();
        if (title.isEmpty()) return "";
        return Character.toUpperCase(title.charAt(0)) + title.substring(1);
    }

    private int findOrdinalDay(String lower) {
        for (String word : lower.split("\\s+")) {
            String w = word.replaceAll("[^a-z0-9]", "");
            if (w.length() < 3) continue;
            String suffix = w.substring(w.length() - 2);
            if (!(suffix.equals("st") || suffix.equals("nd")
                    || suffix.equals("rd") || suffix.equals("th"))) continue;
            String digits = w.substring(0, w.length() - 2);
            if (digits.length() > 2 || !allDigits(digits)) continue;
            int d = Integer.parseInt(digits);
            if (d >= 1 && d <= 31) return d;
        }
        return 0;
    }

    private boolean allDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private LocalDate nextOccurrence(LocalDate base, int day) {
        LocalDate candidate = withDayClamped(base, day);
        if (candidate.isBefore(base)) {
            candidate = withDayClamped(base.withDayOfMonth(1).plusMonths(1), day);
        }
        return candidate;
    }

    private LocalDate withDayClamped(LocalDate month, int day) {
        return month.withDayOfMonth(Math.min(day, month.lengthOfMonth()));
    }
}