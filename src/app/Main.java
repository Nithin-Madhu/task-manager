package app;

import api.ApiResponse;
import api.IdempotencyStore;
import api.TaskApi;
import event.AuditTrailListener;
import event.ConsoleLogListener;
import model.Task;
import model.TaskQuery;
import model.TaskRequest;
import parser.LlmClient;
import parser.ParserFactory;
import repository.InMemoryTaskRepository;
import service.TaskService;

public class Main {

    public static void main(String[] args) {
        System.out.println("Task Manager started");
        String key = System.getenv("LLM_API_KEY");
        System.out.println(key == null || key.trim().isEmpty()
                ? "LLM_API_KEY is not set: AI operations use the offline parsers"
                : "LLM_API_KEY is set");

        demoCrud();
        demoSubtasks();
        demoIdempotency();
        demoQueries();
        demoEvents();
        demoAi();
    }

    // ---------- wiring ----------

    // Swap point: return the instructor's LlmClient here once you have it.
    private static LlmClient realClient(String apiKey) {
        return null;
    }

    private static TaskService newService() {
        String key = System.getenv("LLM_API_KEY");
        LlmClient client = realClient(key);
        return new TaskService(InMemoryTaskRepository.getInstance(),
                ParserFactory.createParser(key, client, true),
                ParserFactory.createBreakdown(key, client, true));
    }

    private static TaskApi apiWith(LlmClient client) {
        return new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                ParserFactory.createParser("test-key", client, false),
                ParserFactory.createBreakdown("test-key", client, false)));
    }

    private static void section(String title) {
        System.out.println("\n=== " + title + " ===");
        InMemoryTaskRepository.getInstance().reset();
        IdempotencyStore.getInstance().clear();
    }

    // ---------- demos ----------

    private static void demoCrud() {
        section("CRUD and validation");
        TaskApi api = new TaskApi(newService());
        print("create valid", api.createTask(new TaskRequest("Pay rent", "Monthly rent", null, "2026-12-01", "HIGH"), null));
        print("create invalid", api.createTask(new TaskRequest("", "", "yes", "2026-02-30", "URGENT"), null));
        print("create null request", api.createTask(null, null));
        print("get 1", api.getTask(1));
        print("get 99", api.getTask(99));
        print("update 1", api.updateTask(1, new TaskRequest("Pay rent", "Monthly rent", "true", "2026-12-01", "LOW")));
        print("update 99", api.updateTask(99, new TaskRequest("x", "y", null, null, null)));
        printIds("list", api.listTasks(null));
        print("delete 1", api.deleteTask(1));
        print("delete 1 again", api.deleteTask(1));
        print("get 1 after delete", api.getTask(1));
    }

    private static void demoSubtasks() {
        section("Subtasks and cascade delete");
        TaskApi api = new TaskApi(newService());
        api.createTask(new TaskRequest("Plan trip", "Plan the trip", null, null, null), null);   // 1
        api.createTask(new TaskRequest("Buy milk", "Buy milk", null, null, null), null);         // 2
        print("subtask of 1", api.createSubtask(1, new TaskRequest("Book flights", "Book flights", null, null, null))); // 3
        print("subtask of 1", api.createSubtask(1, new TaskRequest("Book hotel", "Book hotel", null, null, null)));     // 4
        print("subtask of subtask 3", api.createSubtask(3, new TaskRequest("x", "y", null, null, null)));
        print("subtask of 99", api.createSubtask(99, new TaskRequest("x", "y", null, null, null)));
        print("invalid subtask", api.createSubtask(1, new TaskRequest("", "", null, null, null)));
        printIds("list subtasks of 1", api.listSubtasks(1));
        print("list subtasks of 99", api.listSubtasks(99));
        dump("repository BEFORE delete");
        print("delete 1", api.deleteTask(1));
        dump("repository AFTER delete");
        print("get 3", api.getTask(3));
    }

    private static void demoIdempotency() {
        section("Idempotency");
        TaskApi api = new TaskApi(newService());
        TaskRequest rent = new TaskRequest("Pay rent", "Monthly rent", null, null, null);
        print("key-1, first call", api.createTask(rent, "key-1"));
        print("key-1, retry", api.createTask(rent, "key-1"));
        print("key-1, different request", api.createTask(new TaskRequest("Other", "Other", null, null, null), "key-1"));
        print("key-2, invalid request", api.createTask(new TaskRequest("", "", null, null, null), "key-2"));
        print("key-2, corrected request", api.createTask(new TaskRequest("Buy milk", "Buy milk", null, null, null), "key-2"));
        dump("repository after all calls");
    }

    private static void demoQueries() {
        section("Filtering, search, sorting, pagination");
        TaskApi api = new TaskApi(newService());
        api.createTask(new TaskRequest("Write report", "Quarterly report", "true", "2026-12-15", "HIGH"), null);       // 1
        api.createTask(new TaskRequest("Call dentist", "Book appointment", "false", "2026-12-05", "LOW"), null);      // 2
        api.createTask(new TaskRequest("Renew passport", "Passport renewal paperwork", "false", "2026-12-01", "HIGH"), null); // 3
        api.createTask(new TaskRequest("Buy gift", "Gift for the report party", "false", null, "MEDIUM"), null);      // 4
        api.createTask(new TaskRequest("Buy milk", "Buy milk", "false", null, "MEDIUM"), null);                       // 5

        printIds("completed=true", api.listTasks(new TaskQuery.Builder().completed(true).build()));
        printIds("priority=HIGH", api.listTasks(new TaskQuery.Builder().priority("HIGH").build()));
        printIds("search=REPORT", api.listTasks(new TaskQuery.Builder().search("REPORT").build()));
        printIds("dueDate asc", api.listTasks(new TaskQuery.Builder().sortBy("dueDate").order("asc").build()));
        printIds("dueDate desc", api.listTasks(new TaskQuery.Builder().sortBy("dueDate").order("desc").build()));
        printIds("createdAt desc", api.listTasks(new TaskQuery.Builder().sortBy("createdAt").order("desc").build()));

        TaskQuery.Builder combo = new TaskQuery.Builder().completed(false).sortBy("dueDate").order("asc").pageSize(2);
        printIds("combined page 1", api.listTasks(combo.page(1).build()));
        printIds("combined page 2", api.listTasks(combo.page(2).build()));
        printIds("combined page 3", api.listTasks(combo.page(3).build()));

        print("bad query", api.listTasks(new TaskQuery.Builder()
                .priority("URGENT").sortBy("title").order("up").page(0).pageSize(-1).build()));
        printIds("listByPriority HIGH", api.listByPriority("HIGH"));
        print("listByPriority urgent", api.listByPriority("urgent"));
    }

    private static void demoEvents() {
        section("Events (Observer)");
        TaskService service = newService();
        TaskApi api = new TaskApi(service);
        ConsoleLogListener console = new ConsoleLogListener();
        AuditTrailListener audit = new AuditTrailListener();
        service.addListener(console);
        service.addListener(audit);

        ApiResponse created = api.createTask(new TaskRequest("Event demo", "Event demo", null, null, null), null);
        print("create", created);
        int id = idOf(created);
        print("complete", api.updateTask(id, new TaskRequest("Event demo", "Event demo", "true", null, null)));
        print("delete", api.deleteTask(id));
        System.out.println("audit counts: " + audit.getCounts());

        service.removeListener(console);
        print("create after removing console listener",
                api.createTask(new TaskRequest("After removal", "After removal", null, null, null), null));
        System.out.println("audit counts: " + audit.getCounts());
    }

    private static void demoAi() {
        section("AI operations");

        // 1. Offline parsers (used when there is no real LLM key and client)
        TaskApi offline = new TaskApi(newService());
        String rentText = "Remind me to pay rent on the 1st of every month";
        print("offline: text", offline.createTaskFromText(rentText, "text-1"));                      // 1
        print("offline: text retry", offline.createTaskFromText(rentText, "text-1"));
        print("offline: text, same key, other text", offline.createTaskFromText("Call mom tomorrow", "text-1"));
        int tripId = idOf(offline.createTask(new TaskRequest("Plan trip",
                "Book flights, book hotel and pack bags", null, null, null), null));                  // 2
        print("offline: breakdown", offline.breakDownTask(tripId, "bd-1"));                           // 3,4,5
        print("offline: breakdown retry", offline.breakDownTask(tripId, "bd-1"));
        printIds("offline: subtasks", offline.listSubtasks(tripId));
        print("offline: breakdown of a subtask", offline.breakDownTask(tripId + 1, null));

        // 2. Missing key, offline not allowed -> 503
        TaskApi noKey = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                ParserFactory.createParser(null, null, false),
                ParserFactory.createBreakdown(null, null, false)));
        print("no key: text", noKey.createTaskFromText("Pay rent", null));
        print("no key: breakdown", noKey.breakDownTask(tripId, null));

        // 3. Fake LLM
        FakeLlmClient parseFake = new FakeLlmClient(
                "{\"title\":\"Pay rent\",\"description\":\"Pay rent on the 1st of every month\","
                        + "\"dueDate\":\"2026-12-01\",\"recurrence\":\"monthly\"}");
        TaskApi llmApi = apiWith(parseFake);
        print("fake llm: text", llmApi.createTaskFromText(rentText, "llm-1"));                        // 6
        print("fake llm: retry", llmApi.createTaskFromText(rentText, "llm-1"));
        System.out.println("fake llm calls: " + parseFake.getCalls());
        System.out.println("prompt sent:\n" + parseFake.getLastPrompt());

        // 4. Faulty LLM output on creation
        int before = total(llmApi);
        String[] badReplies = {FaultyLlmClient.EMPTY_TITLE, FaultyLlmClient.IMPOSSIBLE_DATE,
                FaultyLlmClient.MALFORMED, FaultyLlmClient.WRONG_TYPE};
        for (String reply : badReplies) {
            print("faulty: text", apiWith(new FaultyLlmClient(reply)).createTaskFromText("anything", null));
        }
        print("faulty: text, client throws", apiWith(FaultyLlmClient.throwing()).createTaskFromText("anything", null));
        System.out.println("tasks before / after bad outputs: " + before + " / " + total(llmApi));

        // 5. Fake and faulty LLM on breakdown
        int weddingId = idOf(llmApi.createTask(new TaskRequest("Plan wedding", "Venue and catering", null, null, null), null)); // 7
        FakeLlmClient breakFake = new FakeLlmClient("Book venue|Find and book a venue\nHire caterer|Compare caterers");
        TaskApi breakApi = apiWith(breakFake);
        print("fake llm: breakdown", breakApi.breakDownTask(weddingId, "llm-bd-1"));                  // 8,9
        print("fake llm: breakdown retry", breakApi.breakDownTask(weddingId, "llm-bd-1"));
        System.out.println("fake llm breakdown calls: " + breakFake.getCalls());

        int dinnerId = idOf(llmApi.createTask(new TaskRequest("Dinner", "Cook dinner", null, null, null), null));               // 10
        print("faulty: breakdown, empty title", apiWith(new FaultyLlmClient(FaultyLlmClient.BREAKDOWN_EMPTY_TITLE)).breakDownTask(dinnerId, null));
        print("faulty: breakdown, no separator", apiWith(new FaultyLlmClient(FaultyLlmClient.BREAKDOWN_NO_PIPE)).breakDownTask(dinnerId, null));
        print("faulty: breakdown, client throws", apiWith(FaultyLlmClient.throwing()).breakDownTask(dinnerId, null));
        printIds("subtasks of dinner", llmApi.listSubtasks(dinnerId));
    }

    // ---------- helpers ----------

    private static void print(String label, ApiResponse r) {
        System.out.println(label + " -> " + r);
    }

    private static void printIds(String label, ApiResponse r) {
        if (r.status() != 200) { print(label, r); return; }
        String body = r.body();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
        StringBuilder ids = new StringBuilder();
        while (m.find()) ids.append(m.group(1)).append(' ');
        int cut = body.indexOf("\"page\":");
        System.out.println(label + " -> 200, ids [" + ids.toString().trim() + "] "
                + (cut >= 0 ? body.substring(cut) : ""));
    }

    private static void dump(String label) {
        StringBuilder sb = new StringBuilder();
        for (Task t : InMemoryTaskRepository.getInstance().findAll()) {
            sb.append("\n  #").append(t.getId()).append(" ").append(t.getTitle());
            if (t.getParentId() != null) sb.append(" (subtask of #").append(t.getParentId()).append(")");
        }
        System.out.println(label + ":" + (sb.length() == 0 ? " (empty)" : sb));
    }

    private static int idOf(ApiResponse r) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(r.body());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static int total(TaskApi api) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"total\":(\\d+)")
                .matcher(api.listTasks(null).body());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }


}