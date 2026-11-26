package service;

import exception.FieldIssue;
import exception.NotFoundException;
import exception.ValidationException;
import model.*;
import repository.TaskRepository;

import java.util.ArrayList;
import java.util.List;

public class TaskService {
    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Task createTask(TaskRequest request) {
        Task task = TaskValidator.validate(request);
        return repository.save(task);
    }

    public Task getTask(int id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Task " + id + " not found"));
    }

    public Task updateTask(int id, TaskRequest request) {
        Task existing = getTask(id);                       // 404 first
        Task validated = TaskValidator.validate(request);  // then 400
        Task updated = validated.toBuilder()
                .id(existing.getId())
                .createdAt(existing.getCreatedAt())
                .parentId(existing.getParentId())
                .build();
        return repository.save(updated);
    }

    public Task createSubtask(int parentId, TaskRequest request) {
        Task parent = getTask(parentId);
        requireCanHaveSubtasks(parent);
        Task validated = TaskValidator.validate(request);
        return repository.save(validated.toBuilder().parentId(parentId).build());
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
        getTask(id);
        for (Task sub : listSubtasks(id)) {
            repository.delete(sub.getId());
        }
        repository.delete(id);
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