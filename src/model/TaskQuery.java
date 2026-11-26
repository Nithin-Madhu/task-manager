package model;

public class TaskQuery {
    private final Boolean completed;
    private final String priority;
    private final String search;
    private final String sortBy;
    private final String order;
    private final Integer page;
    private final Integer pageSize;

    private TaskQuery(Builder b) {
        this.completed = b.completed;
        this.priority = b.priority;
        this.search = b.search;
        this.sortBy = b.sortBy;
        this.order = b.order;
        this.page = b.page;
        this.pageSize = b.pageSize;
    }

    public Boolean getCompleted() { return completed; }
    public String getPriority() { return priority; }
    public String getSearch() { return search; }
    public String getSortBy() { return sortBy; }
    public String getOrder() { return order; }
    public Integer getPage() { return page; }
    public Integer getPageSize() { return pageSize; }

    public static class Builder {
        private Boolean completed;
        private String priority;
        private String search;
        private String sortBy;
        private String order;
        private Integer page;
        private Integer pageSize;

        public Builder completed(Boolean v) { this.completed = v; return this; }
        public Builder priority(String v) { this.priority = v; return this; }
        public Builder search(String v) { this.search = v; return this; }
        public Builder sortBy(String v) { this.sortBy = v; return this; }
        public Builder order(String v) { this.order = v; return this; }
        public Builder page(Integer v) { this.page = v; return this; }
        public Builder pageSize(Integer v) { this.pageSize = v; return this; }

        public TaskQuery build() { return new TaskQuery(this); }
    }
}