package service;

import model.Priority;
import model.Task;

public class PriorityFilter implements TaskFilter {
    private final Priority wanted;

    public PriorityFilter(Priority wanted) { this.wanted = wanted; }

    @Override
    public boolean matches(Task task) { return task.getPriority() == wanted; }
}