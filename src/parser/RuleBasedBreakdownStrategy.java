package parser;

import model.Task;
import model.TaskRequest;

import java.util.ArrayList;
import java.util.List;

public class RuleBasedBreakdownStrategy implements BreakdownStrategy {
    @Override
    public List<TaskRequest> breakDown(Task task) {
        List<TaskRequest> result = new ArrayList<>();
        for (String part : task.getDescription().split("(?i)\\s*,\\s*|\\s+and\\s+")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String title = Character.toUpperCase(p.charAt(0)) + p.substring(1);
            result.add(new TaskRequest(title, title, null, null, null));
        }
        return result;
    }
}