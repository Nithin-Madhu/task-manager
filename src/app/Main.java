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
    }

    private static void print(String label, ApiResponse r) {
        System.out.println(label + " -> " + r);
    }
}
