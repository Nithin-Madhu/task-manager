package service;

import model.Task;

import java.util.Locale;

public class SearchFilter implements TaskFilter {
    private final String text;

    public SearchFilter(String text) { this.text = text.toLowerCase(Locale.ROOT); }

    @Override
    public boolean matches(Task task) {
        return task.getTitle().toLowerCase(Locale.ROOT).contains(text)
                || task.getDescription().toLowerCase(Locale.ROOT).contains(text);
    }
}