package service;

import exception.FieldIssue;
import exception.ValidationException;
import model.Priority;
import model.TaskQuery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class QueryPlanner {
    static final int DEFAULT_PAGE_SIZE = 10;

    static final class Plan {
        final List<TaskFilter> filters = new ArrayList<>();
        SortStrategy sort;          // null = keep id order
        int page = 1;
        int pageSize = DEFAULT_PAGE_SIZE;
    }


    private static final Map<String, Function<Boolean, SortStrategy>> SORTS = new HashMap<>();
    static {
        SORTS.put("createdAt", CreatedAtSort::new);
        SORTS.put("dueDate", DueDateSort::new);
    }

    private QueryPlanner() {}

    static Plan plan(TaskQuery q) {
        if (q == null) q = new TaskQuery.Builder().build();
        List<FieldIssue> issues = new ArrayList<>();
        Plan plan = new Plan();

        if (q.getCompleted() != null) {
            plan.filters.add(new CompletedFilter(q.getCompleted()));
        }
        if (q.getPriority() != null) {
            try {
                plan.filters.add(new PriorityFilter(Priority.valueOf(q.getPriority())));
            } catch (IllegalArgumentException e) {
                issues.add(new FieldIssue("priority", "must be LOW, MEDIUM, or HIGH"));
            }
        }
        if (q.getSearch() != null && !q.getSearch().trim().isEmpty()) {
            plan.filters.add(new SearchFilter(q.getSearch().trim()));
        }

        boolean ascending = true;
        if (q.getOrder() != null) {
            if (q.getOrder().equals("asc")) ascending = true;
            else if (q.getOrder().equals("desc")) ascending = false;
            else issues.add(new FieldIssue("order", "must be 'asc' or 'desc'"));
        }
        if (q.getSortBy() != null) {
            Function<Boolean, SortStrategy> maker = SORTS.get(q.getSortBy());
            if (maker == null) issues.add(new FieldIssue("sortBy", "must be createdAt or dueDate"));
            else plan.sort = maker.apply(ascending);
        }

        if (q.getPage() != null) {
            if (q.getPage() < 1) issues.add(new FieldIssue("page", "must be 1 or greater"));
            else plan.page = q.getPage();
        }
        if (q.getPageSize() != null) {
            if (q.getPageSize() < 1) issues.add(new FieldIssue("pageSize", "must be 1 or greater"));
            else plan.pageSize = q.getPageSize();
        }

        if (!issues.isEmpty()) throw new ValidationException(issues);
        return plan;
    }
}