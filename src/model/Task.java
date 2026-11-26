package model;

import java.util.Objects;

public final class Task {
    private final int id;
    private final String title;
    private final String description;
    private final boolean completed;
    private final String dueDate;
    private final Priority priority;
    private final Integer parentId;
    private final long createdAt;

    private Task(Builder b) {
        this.id = b.id;
        this.title = b.title;
        this.description = b.description;
        this.completed = b.completed;
        this.dueDate = b.dueDate;
        this.priority = b.priority;
        this.parentId = b.parentId;
        this.createdAt = b.createdAt;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isCompleted() { return completed; }
    public String getDueDate() { return dueDate; }
    public Priority getPriority() { return priority; }
    public Integer getParentId() { return parentId; }
    public long getCreatedAt() { return createdAt; }

    public Builder toBuilder() {
        return new Builder(title, description, completed)
                .id(id).dueDate(dueDate).priority(priority)
                .parentId(parentId).createdAt(createdAt);
    }

    public static class Builder {
        private int id;                       // 0 = not assigned yet
        private final String title;
        private final String description;
        private final boolean completed;
        private String dueDate;
        private Priority priority = Priority.MEDIUM;
        private Integer parentId;
        private long createdAt = System.currentTimeMillis();

        public Builder(String title, String description, boolean completed) {
            this.title = Objects.requireNonNull(title);
            this.description = Objects.requireNonNull(description);
            this.completed = completed;
        }

        public Builder id(int id) { this.id = id; return this; }
        public Builder dueDate(String dueDate) { this.dueDate = dueDate; return this; }
        public Builder priority(Priority p) { this.priority = (p == null) ? Priority.MEDIUM : p; return this; }
        public Builder parentId(Integer parentId) { this.parentId = parentId; return this; }
        public Builder createdAt(long createdAt) { this.createdAt = createdAt; return this; }

        public Task build() { return new Task(this); }
    }
}