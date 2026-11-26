package model;

import java.util.List;

public class PageResult {
    private final List<Task> tasks;
    private final int page;
    private final int pageSize;
    private final int total;
    private final int totalPages;

    public PageResult(List<Task> tasks, int page, int pageSize, int total, int totalPages) {
        this.tasks = tasks;
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
        this.totalPages = totalPages;
    }

    public List<Task> getTasks() { return tasks; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public int getTotal() { return total; }
    public int getTotalPages() { return totalPages; }
}