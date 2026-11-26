package app;

import api.ApiResponse;
import api.TaskApi;
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
        print("list", api.listTasks());
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
        print("BEFORE delete", api.listTasks());
        print("delete 2", api.deleteTask(2));
        print("AFTER delete", api.listTasks());
        print("get 4", api.getTask(4));

        TaskRequest rent = new TaskRequest("Pay rent", "Monthly rent", null, null, null);
        print("idem first", api.createTask(rent, "key-1"));
        print("idem retry", api.createTask(rent, "key-1"));
        print("idem different request", api.createTask(new TaskRequest("Other", "Other", null, null, null), "key-1"));
        print("idem invalid", api.createTask(new TaskRequest("", "", null, null, null), "key-2"));
        print("idem fixed, same key", api.createTask(rent, "key-2"));
        print("idem list", api.listTasks());
    }

    private static void print(String label, ApiResponse r) {
        System.out.println(label + " -> " + r);
    }

}
