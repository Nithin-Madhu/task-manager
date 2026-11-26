package service;

import event.TaskEventListener;
import event.TaskEventType;
import exception.FieldIssue;
import exception.NotFoundException;
import exception.ValidationException;
import model.*;
import repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TaskService {
    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    private final List<TaskEventListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(TaskEventListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(TaskEventListener listener) {
        listeners.remove(listener);
    }

    private void publish(String eventType, Task task) {
        for (TaskEventListener l : listeners) {
            try {
                l.onEvent(eventType, task);
            } catch (RuntimeException e) {
                System.err.println("Listener failed: " + e.getMessage());
            }
        }
    }

    public Task createTask(TaskRequest request) {
        Task saved = repository.save(TaskValidator.validate(request));
        publish(TaskEventType.CREATED, saved);
        return saved;
    }

    public Task getTask(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    public Task updateTask(int id, TaskRequest request) {
        Task existing = getTask(id);
        Task validated = TaskValidator.validate(request);
        Task saved = repository.save(validated.toBuilder()
                .id(existing.getId())
                .createdAt(existing.getCreatedAt())
                .parentId(existing.getParentId())
                .build());
        publish(TaskEventType.UPDATED, saved);
        if (!existing.isCompleted() && saved.isCompleted()) {
            publish(TaskEventType.COMPLETED, saved);
        }
        return saved;
    }

    public Task createSubtask(int parentId, TaskRequest request) {
        Task parent = getTask(parentId);
        requireCanHaveSubtasks(parent);
        Task validated = TaskValidator.validate(request);
        Task saved = repository.save(validated.toBuilder().parentId(parentId).build());
        publish(TaskEventType.CREATED, saved);
        return saved;
    }

    public List<Task> listSubtasks(int parentId) {
        getTask(parentId);
        List<Task> result = new ArrayList<>();
        for (Task t : repository.findAll()) {
            if (t.getParentId() != null && t.getParentId().equals(parentId)) {
                result.add(t);
            }
        }
        return result;
    }

    public void requireCanHaveSubtasks(Task parent) {
        if (parent.getParentId() != null) {
            throw new ValidationException(List.of(new FieldIssue(
                    "parentId", "task " + parent.getId() +
                    " is a subtask and cannot have subtasks")));
        }
    }

    public void deleteTask(int id) {
        Task existing = getTask(id);
        for (Task sub : listSubtasks(id)) {
            repository.delete(sub.getId());
            publish(TaskEventType.DELETED, sub);
        }
        repository.delete(id);
        publish(TaskEventType.DELETED, existing);
    }

    public PageResult listTasks(TaskQuery query) {
        QueryPlanner.Plan plan = QueryPlanner.plan(query);

        List<Task> matched = new ArrayList<>();
        for (Task t : repository.findAll()) {
            boolean ok = true;
            for (TaskFilter f : plan.filters) {
                if (!f.matches(t)) { ok = false; break; }
            }
            if (ok) matched.add(t);
        }
        if (plan.sort != null) matched = plan.sort.sort(matched);

        int total = matched.size();
        int totalPages = (int) ((total + (long) plan.pageSize - 1) / plan.pageSize);
        long from = (long) (plan.page - 1) * plan.pageSize;
        List<Task> items = (from >= total)
                ? new ArrayList<>()
                : new ArrayList<>(matched.subList((int) from, (int) Math.min(total, from + plan.pageSize)));
        return new PageResult(items, plan.page, plan.pageSize, total, totalPages);
    }

    public List<Task> listByPriority(String level) {
        Priority wanted;
        try {
            wanted = Priority.valueOf(level);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException(List.of(
                    new FieldIssue("priority", "must be LOW, MEDIUM, or HIGH")));
        }
        List<Task> result = new ArrayList<>();
        for (Task t : repository.findAll()) {
            if (t.getPriority() == wanted) result.add(t);
        }
        return result;
    }
}