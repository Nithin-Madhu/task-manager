package app;

import api.ApiResponse;
import api.TaskApi;
import model.TaskQuery;
import model.TaskRequest;
import repository.InMemoryTaskRepository;
import service.TaskService;

public class Main {

    public static void main(String[] args) {

        System.out.println("Task Manager started");

        TaskApi api = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance()));

        print("create valid", api.createTask(new TaskRequest("Pay rent", "Monthly rent", null, "2026-12-01", "HIGH"), null));
        print("create invalid", api.createTask(new TaskRequest("", "", "yes", "2026-02-30", "URGENT"), null));
        print("create null request", api.createTask(null, null));
        print("get 1", api.getTask(1));
        print("get 99", api.getTask(99));
        print("update 1", api.updateTask(1, new TaskRequest("Pay rent", "Monthly rent", "true", "2026-12-01", "LOW")));
        print("update 99", api.updateTask(99, new TaskRequest("x", "y", null, null, null)));

        print("delete 1", api.deleteTask(1));
        print("get 1 after delete", api.getTask(1));

        // sub task creation
        print("create parent", api.createTask(new TaskRequest("Plan trip", "Plan the trip", null, null, null), null));
        print("create other", api.createTask(new TaskRequest("Buy milk", "Buy milk", null, null, null), null));
        print("subtask of 2", api.createSubtask(2, new TaskRequest("Book flights", "Book flights", null, null, null)));
        print("subtask of 2", api.createSubtask(2, new TaskRequest("Book hotel", "Book hotel", null, null, null)));
        print("subtask of subtask 4", api.createSubtask(4, new TaskRequest("x", "y", null, null, null)));
        print("subtask of 99", api.createSubtask(99, new TaskRequest("x", "y", null, null, null)));
        print("invalid subtask", api.createSubtask(2, new TaskRequest("", "", null, null, null)));
        print("list subtasks of 2", api.listSubtasks(2));
        print("list subtasks of 99", api.listSubtasks(99));
        print("delete 2", api.deleteTask(2));
        print("get 4", api.getTask(4));


        api.createTask(new TaskRequest("Write report", "Quarterly report", "true", "2026-12-15", "HIGH"), null);
        api.createTask(new TaskRequest("Call dentist", "Book appointment", "false", "2026-12-05", "LOW"), null);
        api.createTask(new TaskRequest("Renew passport", "Passport renewal paperwork", "false", "2026-12-01", "HIGH"), null);
        api.createTask(new TaskRequest("Buy gift", "Gift for the report party", "false", null, "MEDIUM"), null);

        printIds("completed=true", api.listTasks(new TaskQuery.Builder().completed(true).build()));
        printIds("priority=HIGH", api.listTasks(new TaskQuery.Builder().priority("HIGH").build()));
        printIds("search=REPORT", api.listTasks(new TaskQuery.Builder().search("REPORT").build()));
        printIds("dueDate asc", api.listTasks(new TaskQuery.Builder().sortBy("dueDate").order("asc").build()));
        printIds("dueDate desc", api.listTasks(new TaskQuery.Builder().sortBy("dueDate").order("desc").build()));
        printIds("createdAt desc", api.listTasks(new TaskQuery.Builder().sortBy("createdAt").order("desc").build()));

        TaskQuery.Builder combo = new TaskQuery.Builder().completed(false).sortBy("dueDate").order("asc").pageSize(2);
        printIds("combined page 1", api.listTasks(combo.page(1).build()));
        printIds("combined page 2", api.listTasks(combo.page(2).build()));
        printIds("combined page 4", api.listTasks(combo.page(4).build()));

        print("bad query", api.listTasks(new TaskQuery.Builder()
                .priority("URGENT").sortBy("title").order("up").page(0).pageSize(-1).build()));
        printIds("listByPriority HIGH", api.listByPriority("HIGH"));
        print("listByPriority urgent", api.listByPriority("urgent"));
    }

    private static void print(String label, ApiResponse r) {
        System.out.println(label + " -> " + r);
    }

    private static void printIds(String label, ApiResponse r) {
        if (r.getStatus() != 200) { print(label, r); return; }
        String body = r.getBody();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
        StringBuilder ids = new StringBuilder();
        while (m.find()) ids.append(m.group(1)).append(' ');
        int cut = body.indexOf("\"page\":");
        System.out.println(label + " -> 200, ids [" + ids.toString().trim() + "] "
                + (cut >= 0 ? body.substring(cut) : ""));
    }
}
