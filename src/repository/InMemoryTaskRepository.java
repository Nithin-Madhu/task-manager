package repository;

import model.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// using singleton & synchronized for
public class InMemoryTaskRepository implements TaskRepository {
    private static final InMemoryTaskRepository INSTANCE = new InMemoryTaskRepository();

    private final Map<Integer, Task> tasks = new LinkedHashMap<>();
    private int nextId = 1;

    private InMemoryTaskRepository() {}

    public static InMemoryTaskRepository getInstance() { return INSTANCE; }

    @Override
    public synchronized Task save(Task task) {
        Task toStore = (task.getId() == 0)
                ? task.toBuilder().id(nextId++).build()
                : task;
        tasks.put(toStore.getId(), toStore);
        return toStore;
    }

    @Override
    public synchronized Optional<Task> findById(int id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public synchronized List<Task> findAll() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public synchronized boolean delete(int id) {
        return tasks.remove(id) != null;
    }

    public synchronized void reset() {
        tasks.clear();
        nextId = 1;
    }
}