package service;

import model.Task;

import java.util.ArrayList;
import java.util.List;

public class DueDateSort implements SortStrategy {
    private final boolean ascending;

    public DueDateSort(boolean ascending) { this.ascending = ascending; }

    @Override
    public List<Task> sort(List<Task> tasks) {
        List<Task> copy = new ArrayList<>(tasks);
        copy.sort((a, b) -> {
            String x = a.getDueDate();
            String y = b.getDueDate();
            int compare = Integer.compare(a.getId(), b.getId());
            if (x == null && y == null) return compare;
            if (x == null) return 1;      // no due date always goes last
            if (y == null) return -1;
            int c = x.compareTo(y);       // YYYY-MM-DD sorts correctly as text
            if (c == 0) c = compare;
            return ascending ? c : -c;
        });
        return copy;
    }
}