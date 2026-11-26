package app;

import api.ApiResponse;
import api.IdempotencyStore;
import api.TaskApi;
import event.AuditTrailListener;
import event.TaskEventType;
import model.TaskQuery;
import model.TaskRequest;
import parser.LlmClient;
import parser.ParserFactory;
import repository.InMemoryTaskRepository;
import service.TaskService;

public class TestRunner {
    interface Body { void run() throws Exception; }

    private static int passed;
    private static int failed;

    private static final String GOOD_TASK_JSON =
            "{\"title\":\"Pay rent\",\"description\":\"Pay rent monthly\","
                    + "\"dueDate\":\"2026-12-01\",\"recurrence\":null}";

    public static void main(String[] args) {
        crudTests();
        subtaskTests();
        queryTests();
        eventTests();
        aiTests();
        idempotencyTests();
        // round2Tests();   // uncomment in the second commit

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ---------------- round 1: the obvious checklist ----------------

    private static void crudTests() {
        test("createTask: valid request returns 201 with id 1", () -> {
            ApiResponse r = api().createTask(req("Pay rent", "Monthly rent"), null);
            status(201, r);
            equal(1, idOf(r));
        });
        test("createTask: applies defaults (not completed, MEDIUM, no due date)", () -> {
            ApiResponse r = api().createTask(req("Pay rent", "Monthly rent"), null);
            contains(r, "\"completed\":false");
            contains(r, "\"priority\":\"MEDIUM\"");
            contains(r, "\"dueDate\":null");
        });
        test("getTask: returns the created task", () -> {
            TaskApi api = api();
            api.createTask(req("Pay rent", "Monthly rent"), null);
            ApiResponse r = api.getTask(1);
            status(200, r);
            contains(r, "\"title\":\"Pay rent\"");
        });
        test("getTask: unknown id returns 404", () -> error(404, "NOT_FOUND", api().getTask(99)));
        test("updateTask: changes the task", () -> {
            TaskApi api = api();
            api.createTask(req("Pay rent", "Monthly rent"), null);
            ApiResponse r = api.updateTask(1, req("Pay rent", "Monthly rent", "true", null, "HIGH"));
            status(200, r);
            contains(r, "\"completed\":true");
            contains(r, "\"priority\":\"HIGH\"");
        });
        test("deleteTask: removes the task", () -> {
            TaskApi api = api();
            api.createTask(req("Pay rent", "Monthly rent"), null);
            status(200, api.deleteTask(1));
            error(404, "NOT_FOUND", api.getTask(1));
        });
        test("createTask: blank title returns 400", () ->
                error(400, "VALIDATION_ERROR", api().createTask(req("", "Monthly rent"), null)));
    }

    private static void subtaskTests() {
        test("createSubtask: returns 201 and links the parent", () -> {
            TaskApi api = api();
            api.createTask(req("Plan trip", "Plan the trip"), null);
            ApiResponse r = api.createSubtask(1, req("Book flights", "Book flights"));
            status(201, r);
            contains(r, "\"parentId\":1");
        });
        test("listSubtasks: returns only that parent's subtasks", () -> {
            TaskApi api = api();
            api.createTask(req("A", "A"), null);      // 1
            api.createTask(req("B", "B"), null);      // 2
            api.createSubtask(1, req("A1", "A1"));    // 3
            api.createSubtask(2, req("B1", "B1"));    // 4
            equal("3", ids(api.listSubtasks(1)));
        });
        test("deleteTask: deleting a parent deletes its subtasks", () -> {
            TaskApi api = api();
            api.createTask(req("A", "A"), null);      // 1
            api.createTask(req("B", "B"), null);      // 2
            api.createSubtask(1, req("A1", "A1"));    // 3
            api.deleteTask(1);
            error(404, "NOT_FOUND", api.getTask(3));
            status(200, api.getTask(2));
        });
    }

    private static void queryTests() {
        test("listTasks: filters by completed", () ->
                equal("1", ids(seeded().listTasks(new TaskQuery.Builder().completed(true).build()))));
        test("listTasks: sorts by dueDate ascending", () ->
                equal("3 2 1 4 5", ids(seeded().listTasks(sorted("dueDate", "asc")))));
        test("listTasks: search is case-insensitive", () ->
                equal("1 4", ids(seeded().listTasks(new TaskQuery.Builder().search("REPORT").build()))));
        test("listTasks: pagination returns the right page", () -> {
            ApiResponse r = seeded().listTasks(new TaskQuery.Builder().pageSize(2).page(2).build());
            equal("3 4", ids(r));
            contains(r, "\"totalPages\":3");
        });
    }

    private static void eventTests() {
        test("events: a listener receives CREATED", () -> {
            TaskService service = new TaskService(InMemoryTaskRepository.getInstance());
            AuditTrailListener audit = new AuditTrailListener();
            service.addListener(audit);
            new TaskApi(service).createTask(req("A", "A"), null);
            equal(1, audit.getCount(TaskEventType.CREATED));
        });
    }

    private static void aiTests() {
        test("createTaskFromText: fake LLM reply becomes a task", () -> {
            ApiResponse r = apiWith(new FakeLlmClient(GOOD_TASK_JSON)).createTaskFromText("Pay rent on the 1st", null);
            status(201, r);
            contains(r, "\"title\":\"Pay rent\"");
        });
        test("breakDownTask: fake LLM reply becomes subtasks", () -> {
            TaskApi api = apiWith(new FakeLlmClient("Book venue|Find a venue\nHire caterer|Compare caterers"));
            api.createTask(req("Plan wedding", "Venue and catering"), null);
            ApiResponse r = api.breakDownTask(1, null);
            status(201, r);
            equal("2 3", ids(r));
        });
        test("createTaskFromText: missing key returns 503", () -> {
            TaskApi noKey = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                    ParserFactory.createParser(null, null, false),
                    ParserFactory.createBreakdown(null, null, false)));
            error(503, "SERVICE_UNAVAILABLE", noKey.createTaskFromText("Pay rent", null));
        });
    }

    private static void idempotencyTests() {
        test("idempotency: same key and same request creates one task", () -> {
            TaskApi api = api();
            ApiResponse first = api.createTask(req("Pay rent", "Monthly rent"), "k1");
            ApiResponse retry = api.createTask(req("Pay rent", "Monthly rent"), "k1");
            equal(first.getBody(), retry.getBody());
            equal(1, total(api));
        });
    }

    // ---------------- helpers ----------------

    private static void test(String name, Body body) {
        InMemoryTaskRepository.getInstance().reset();
        IdempotencyStore.getInstance().clear();
        try {
            body.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("FAIL  " + name + "  ->  "
                    + (t instanceof AssertionError ? t.getMessage() : t.toString()));
        }
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual) {
        check(expected.equals(actual), "expected [" + expected + "] but was [" + actual + "]");
    }

    private static void status(int expected, ApiResponse r) {
        check(r.getStatus() == expected, "expected status " + expected + " but got " + r.getStatus() + " " + r.getBody());
    }

    private static void contains(ApiResponse r, String fragment) {
        check(r.getBody().contains(fragment), "expected body to contain " + fragment + " but was " + r.getBody());
    }

    private static void error(int expectedStatus, String expectedCode, ApiResponse r) {
        status(expectedStatus, r);
        String b = r.getBody();
        check(b.startsWith("{\"error\":{\"code\":\"" + expectedCode + "\",\"message\":\"") && b.contains("\"details\":["),
                "wrong error shape or code: " + b);
    }

    private static String ids(ApiResponse r) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(r.getBody());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(m.group(1));
        }
        return sb.toString();
    }

    private static int idOf(ApiResponse r) {
        String s = ids(r);
        return s.isEmpty() ? -1 : Integer.parseInt(s.split(" ")[0]);
    }

    private static int total(TaskApi api) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"total\":(\\d+)")
                .matcher(api.listTasks(null).getBody());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static TaskApi api() {
        return new TaskApi(new TaskService(InMemoryTaskRepository.getInstance()));
    }

    private static TaskApi apiWith(LlmClient client) {
        return new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                ParserFactory.createParser("test-key", client, false),
                ParserFactory.createBreakdown("test-key", client, false)));
    }

    private static TaskRequest req(String title, String description) {
        return new TaskRequest(title, description, null, null, null);
    }

    private static TaskRequest req(String title, String description, String completed, String dueDate, String priority) {
        return new TaskRequest(title, description, completed, dueDate, priority);
    }

    private static TaskQuery sorted(String sortBy, String order) {
        return new TaskQuery.Builder().sortBy(sortBy).order(order).build();
    }

    private static TaskApi seeded() {
        TaskApi api = api();
        api.createTask(req("Write report", "Quarterly report", "true", "2026-12-15", "HIGH"), null);          // 1
        api.createTask(req("Call dentist", "Book appointment", "false", "2026-12-05", "LOW"), null);          // 2
        api.createTask(req("Renew passport", "Passport renewal paperwork", "false", "2026-12-01", "HIGH"), null); // 3
        api.createTask(req("Buy gift", "Gift for the report party", "false", null, "MEDIUM"), null);          // 4
        api.createTask(req("Buy milk", "Buy milk", "false", null, "MEDIUM"), null);                          // 5
        return api;
    }
}