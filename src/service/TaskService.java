package service;

import exception.NotFoundException;
import model.Task;
import model.TaskRequest;
import repository.TaskRepository;

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

    public void deleteTask(int id) {
        getTask(id);
        repository.delete(id);
    }
}