package service;

import event.TaskEventListener;
import event.TaskEventType;
import exception.*;
import model.*;
import parser.*;
import repository.TaskRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TaskService {
    private final TaskRepository repository;

    private final TaskParser parser;
    private final BreakdownStrategy breakdown;

    public TaskService(TaskRepository repository) {
        this(repository, new RuleBasedTaskParser(), new RuleBasedBreakdownStrategy());
    }

    public TaskService(TaskRepository repository, TaskParser parser, BreakdownStrategy breakdown) {
        this.repository = repository;
        this.parser = parser;
        this.breakdown = breakdown;
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

    public Task createTaskFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new ValidationException(List.of(new FieldIssue("text", "must not be empty")));
        }
        ParsedTask parsed;
        try {
            parsed = parser.parse(text, LocalDate.now().toString());
        } catch (ApiException e) {
            throw e;                                   // keep 503 etc. as they are
        } catch (RuntimeException e) {
            throw new BadGatewayException("Task parser failed: " + reason(e));
        }
        if (parsed == null) {
            throw new BadGatewayException("Task parser returned nothing");
        }

        String description = parsed.getDescription();
        String recurrence = parsed.getRecurrence();
        if (description != null && recurrence != null && !recurrence.trim().isEmpty()) {
            description = description + " (repeats " + recurrence.trim() + ")";
        }

        Task task;
        try {
            task = TaskValidator.validate(new TaskRequest(
                    parsed.getTitle(), description, "false", parsed.getDueDate(), null));
        } catch (ValidationException e) {
            throw new BadGatewayException("Parser output was invalid: " + describe(e));
        }
        Task saved = repository.save(task);
        publish(TaskEventType.CREATED, saved);
        return saved;
    }

    public List<Task> breakDownTask(int id) {
        Task parent = getTask(id);                     // 404
        requireCanHaveSubtasks(parent);                // 400

        List<TaskRequest> items;
        try {
            items = breakdown.breakDown(parent);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadGatewayException("Breakdown failed: " + reason(e));
        }
        if (items == null || items.isEmpty()) {
            throw new BadGatewayException("Breakdown returned no subtasks");
        }

        List<Task> validated = new ArrayList<>();
        StringBuilder problems = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            try {
                validated.add(TaskValidator.validate(items.get(i)));
            } catch (ValidationException e) {
                if (problems.length() > 0) problems.append(" | ");
                problems.append("item ").append(i + 1).append(": ").append(describe(e));
            }
        }
        if (problems.length() > 0) {
            throw new BadGatewayException("Breakdown output was invalid: " + problems);
        }

        List<Task> saved = new ArrayList<>();
        for (Task t : validated) {
            Task s = repository.save(t.toBuilder().parentId(id).build());
            saved.add(s);
            publish(TaskEventType.CREATED, s);
        }
        return saved;
    }

    private static String describe(ValidationException e) {
        StringBuilder sb = new StringBuilder();
        for (FieldIssue i : e.getIssues()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(i.getField()).append(" ").append(i.getIssue());
        }
        return sb.toString();
    }

    private static String reason(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}