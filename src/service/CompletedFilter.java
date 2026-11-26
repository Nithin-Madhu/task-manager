package service;

import model.Task;

public class CompletedFilter implements TaskFilter {
    private final boolean wanted;

    public CompletedFilter(boolean wanted) { this.wanted = wanted; }

    @Override
    public boolean matches(Task task) { return task.isCompleted() == wanted; }
}