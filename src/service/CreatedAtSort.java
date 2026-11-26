package service;

import model.Task;

import java.util.ArrayList;
import java.util.List;

public class CreatedAtSort implements SortStrategy {
    private final boolean ascending;

    public CreatedAtSort(boolean ascending) { this.ascending = ascending; }

    @Override
    public List<Task> sort(List<Task> tasks) {
        List<Task> copy = new ArrayList<>(tasks);
        copy.sort((a, b) -> {
            int c = Long.compare(a.getCreatedAt(), b.getCreatedAt());
            if (c == 0) c = Integer.compare(a.getId(), b.getId());   // tie-break
            return ascending ? c : -c;
        });
        return copy;
    }
}