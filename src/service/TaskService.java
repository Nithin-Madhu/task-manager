package service;

import exception.FieldIssue;
import exception.NotFoundException;
import exception.ValidationException;
import model.Task;
import model.TaskRequest;
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

    public List<Task> listAll() {
        return repository.findAll();
    }
}