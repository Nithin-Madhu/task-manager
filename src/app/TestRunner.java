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
            equal(first.body(), retry.body());
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
        check(r.status() == expected, "expected status " + expected + " but got " + r.status() + " " + r.body());
    }

    private static void contains(ApiResponse r, String fragment) {
        check(r.body().contains(fragment), "expected body to contain " + fragment + " but was " + r.body());
    }

    private static void error(int expectedStatus, String expectedCode, ApiResponse r) {
        status(expectedStatus, r);
        String b = r.body();
        check(b.startsWith("{\"error\":{\"code\":\"" + expectedCode + "\",\"message\":\"") && b.contains("\"details\":["),
                "wrong error shape or code: " + b);
    }

    private static String ids(ApiResponse r) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(r.body());
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
                .matcher(api.listTasks(null).body());
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

    // ---------------- round 2: gaps found in the critique ----------------

    private static String createdAtOf(ApiResponse r) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"createdAt\":(\\d+)").matcher(r.body());
        return m.find() ? m.group(1) : "";
    }

    private static void round2Tests() {
        // --- unknown ids on every operation ---
        test("updateTask: unknown id returns 404", () -> error(404, "NOT_FOUND", api().updateTask(99, req("A", "A"))));
        test("deleteTask: unknown id returns 404", () -> error(404, "NOT_FOUND", api().deleteTask(99)));
        test("createSubtask: unknown parent returns 404", () -> error(404, "NOT_FOUND", api().createSubtask(99, req("A", "A"))));
        test("listSubtasks: unknown parent returns 404", () -> error(404, "NOT_FOUND", api().listSubtasks(99)));
        test("breakDownTask: unknown id returns 404", () -> error(404, "NOT_FOUND", api().breakDownTask(99, null)));
        test("updateTask: unknown id is checked before the body (404 beats 400)", () ->
                error(404, "NOT_FOUND", api().updateTask(99, req("", ""))));

        // --- validation ---
        test("createTask: null request returns 400", () ->
                error(400, "VALIDATION_ERROR", api().createTask(null, null)));
        test("createTask: whitespace-only title returns 400", () -> {
            ApiResponse r = api().createTask(req("   ", "Monthly rent"), null);
            error(400, "VALIDATION_ERROR", r);
            contains(r, "\"field\":\"title\"");
        });
        test("createTask: every problem is reported in one response", () -> {
            ApiResponse r = api().createTask(req("", "", "yes", "2026-02-30", "URGENT"), null);
            error(400, "VALIDATION_ERROR", r);
            for (String f : new String[]{"title", "description", "completed", "dueDate", "priority"}) {
                contains(r, "\"field\":\"" + f + "\"");
            }
        });
        test("dueDate: impossible or badly formatted dates are rejected", () -> {
            for (String d : new String[]{"2026-02-30", "2026-13-01", "2026-00-10", "2026-04-31", "2026-1-1", "26-01-01", "tomorrow", ""}) {
                error(400, "VALIDATION_ERROR", api().createTask(req("A", "A", null, d, null), null));
            }
        });
        test("dueDate: leap years (2028-02-29 and 2000-02-29 ok; 2027-02-29 and 2100-02-29 rejected)", () -> {
            status(201, api().createTask(req("A", "A", null, "2028-02-29", null), null));
            status(201, api().createTask(req("A", "A", null, "2000-02-29", null), null));
            error(400, "VALIDATION_ERROR", api().createTask(req("A", "A", null, "2027-02-29", null), null));
            error(400, "VALIDATION_ERROR", api().createTask(req("A", "A", null, "2100-02-29", null), null));
        });
        test("priority and completed are case-sensitive", () -> {
            error(400, "VALIDATION_ERROR", api().createTask(req("A", "A", null, null, "high"), null));
            error(400, "VALIDATION_ERROR", api().createTask(req("A", "A", "TRUE", null, null), null));
            status(201, api().createTask(req("A", "A", "true", null, "HIGH"), null));
        });
        test("updateTask: keeps id, createdAt and parentId", () -> {
            TaskApi api = api();
            api.createTask(req("P", "P"), null);
            ApiResponse sub = api.createSubtask(1, req("S", "S"));
            ApiResponse upd = api.updateTask(2, req("S2", "S2"));
            contains(upd, "\"parentId\":1");
            check(!createdAtOf(sub).isEmpty() && createdAtOf(sub).equals(createdAtOf(upd)), "createdAt changed");
        });

        // --- subtasks ---
        test("createSubtask: a subtask cannot have subtasks (400)", () -> {
            TaskApi api = api();
            api.createTask(req("P", "P"), null);
            api.createSubtask(1, req("S", "S"));
            ApiResponse r = api.createSubtask(2, req("X", "X"));
            error(400, "VALIDATION_ERROR", r);
            contains(r, "\"field\":\"parentId\"");
        });
        test("createSubtask: invalid body returns 400 and saves nothing", () -> {
            TaskApi api = api();
            api.createTask(req("P", "P"), null);
            error(400, "VALIDATION_ERROR", api.createSubtask(1, req("", "")));
            equal("", ids(api.listSubtasks(1)));
        });
        test("deleteTask: deleting a subtask leaves the parent", () -> {
            TaskApi api = api();
            api.createTask(req("P", "P"), null);
            api.createSubtask(1, req("S", "S"));
            status(200, api.deleteTask(2));
            status(200, api.getTask(1));
            equal("", ids(api.listSubtasks(1)));
        });

        // --- idempotency ---
        test("idempotency: same key with a different request returns 422", () -> {
            TaskApi api = api();
            api.createTask(req("A", "A"), "k");
            error(422, "CONFLICT", api.createTask(req("B", "B"), "k"));
            equal(1, total(api));
        });
        test("idempotency: a failed request is not stored, so the key works after fixing it", () -> {
            TaskApi api = api();
            error(400, "VALIDATION_ERROR", api.createTask(req("", ""), "k"));
            status(201, api.createTask(req("A", "A"), "k"));
            equal(1, total(api));
        });
        test("idempotency: a null or blank key gives no protection", () -> {
            TaskApi api = api();
            api.createTask(req("A", "A"), null);
            api.createTask(req("A", "A"), null);
            api.createTask(req("A", "A"), "  ");
            equal(3, total(api));
        });
        test("idempotency: a key is tied to one operation", () -> {
            TaskApi api = apiWith(new FakeLlmClient(GOOD_TASK_JSON));
            api.createTask(req("Pay rent", "Pay rent monthly"), "k");
            error(422, "CONFLICT", api.createTaskFromText("Pay rent", "k"));
        });
        test("idempotency: a retried createTaskFromText never calls the LLM twice", () -> {
            FakeLlmClient fake = new FakeLlmClient(GOOD_TASK_JSON);
            TaskApi api = apiWith(fake);
            api.createTaskFromText("Pay rent", "k");
            api.createTaskFromText("Pay rent", "k");
            equal(1, fake.getCalls());
            equal(1, total(api));
        });
        test("idempotency: a retried breakDownTask never calls the LLM twice or duplicates subtasks", () -> {
            FakeLlmClient fake = new FakeLlmClient("A|a\nB|b");
            TaskApi api = apiWith(fake);
            api.createTask(req("Parent", "Parent"), null);
            api.breakDownTask(1, "k");
            api.breakDownTask(1, "k");
            equal(1, fake.getCalls());
            equal("2 3", ids(api.listSubtasks(1)));
        });
        test("idempotency: a failed LLM call is not stored, so a retry can succeed", () -> {
            error(502, "BAD_GATEWAY", apiWith(FaultyLlmClient.throwing()).createTaskFromText("Pay rent", "k"));
            status(201, apiWith(new FakeLlmClient(GOOD_TASK_JSON)).createTaskFromText("Pay rent", "k"));
        });

        // --- AI failures ---
        test("createTaskFromText: bad LLM output returns 502 and saves nothing", () -> {
            TaskApi probe = api();
            String[] replies = {FaultyLlmClient.EMPTY_TITLE, FaultyLlmClient.IMPOSSIBLE_DATE,
                    FaultyLlmClient.MALFORMED, FaultyLlmClient.WRONG_TYPE};
            for (String reply : replies) {
                error(502, "BAD_GATEWAY", apiWith(new FaultyLlmClient(reply)).createTaskFromText("anything", null));
            }
            error(502, "BAD_GATEWAY", apiWith(new FakeLlmClient(null)).createTaskFromText("anything", null));
            error(502, "BAD_GATEWAY", apiWith(FaultyLlmClient.throwing()).createTaskFromText("anything", null));
            equal(0, total(probe));
        });
        test("createTaskFromText: blank text returns 400 without calling the LLM", () -> {
            FakeLlmClient fake = new FakeLlmClient(GOOD_TASK_JSON);
            error(400, "VALIDATION_ERROR", apiWith(fake).createTaskFromText("   ", null));
            equal(0, fake.getCalls());
        });
        test("createTaskFromText: JSON wrapped in a markdown fence is accepted", () ->
                status(201, apiWith(new FakeLlmClient("```json\n" + GOOD_TASK_JSON + "\n```")).createTaskFromText("x", null)));
        test("createTaskFromText: the prompt contains today's date and the user's text", () -> {
            FakeLlmClient fake = new FakeLlmClient(GOOD_TASK_JSON);
            apiWith(fake).createTaskFromText("Water the plants", null);
            check(fake.getLastPrompt().contains(java.time.LocalDate.now().toString()), "prompt lacks today's date");
            check(fake.getLastPrompt().contains("Water the plants"), "prompt lacks the user's text");
        });
        test("breakDownTask: bad, empty or failing LLM replies return 502 and save nothing", () -> {
            TaskApi probe = api();
            probe.createTask(req("Parent", "Parent"), null);
            LlmClient[] clients = {
                    new FaultyLlmClient(FaultyLlmClient.BREAKDOWN_EMPTY_TITLE),
                    new FaultyLlmClient(FaultyLlmClient.BREAKDOWN_NO_PIPE),
                    new FakeLlmClient(""), new FakeLlmClient(null), FaultyLlmClient.throwing()};
            for (LlmClient c : clients) {
                error(502, "BAD_GATEWAY", apiWith(c).breakDownTask(1, null));
            }
            equal("", ids(probe.listSubtasks(1)));
        });
        test("breakDownTask: a subtask cannot be broken down (400)", () -> {
            TaskApi api = apiWith(new FakeLlmClient("A|a"));
            api.createTask(req("P", "P"), null);
            api.createSubtask(1, req("S", "S"));
            error(400, "VALIDATION_ERROR", api.breakDownTask(2, null));
        });
        test("missing LLM key returns 503 for both AI operations and saves nothing", () -> {
            TaskApi api = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                    ParserFactory.createParser(null, null, false),
                    ParserFactory.createBreakdown(null, null, false)));
            api.createTask(req("P", "P"), null);
            error(503, "SERVICE_UNAVAILABLE", api.breakDownTask(1, null));
            error(503, "SERVICE_UNAVAILABLE", api.createTaskFromText("x", null));
            equal(1, total(api));
        });
        test("offline mode: rule-based parsers work without a key", () -> {
            TaskApi api = new TaskApi(new TaskService(InMemoryTaskRepository.getInstance(),
                    ParserFactory.createParser(null, null, true),
                    ParserFactory.createBreakdown(null, null, true)));
            ApiResponse r = api.createTaskFromText("Remind me to pay rent on the 1st of every month", null);
            status(201, r);
            contains(r, "\"title\":\"Pay rent\"");
        });

        // --- queries ---
        test("listTasks: a page beyond the last returns an empty list, not an error", () -> {
            ApiResponse r = seeded().listTasks(new TaskQuery.Builder().pageSize(2).page(9).build());
            status(200, r);
            equal("", ids(r));
            contains(r, "\"total\":5");
            contains(r, "\"totalPages\":3");
        });
        test("listTasks: invalid query values return 400 listing every problem", () -> {
            ApiResponse r = api().listTasks(new TaskQuery.Builder()
                    .priority("URGENT").sortBy("title").order("up").page(0).pageSize(-1).build());
            error(400, "VALIDATION_ERROR", r);
            for (String f : new String[]{"priority", "sortBy", "order", "page", "pageSize"}) {
                contains(r, "\"field\":\"" + f + "\"");
            }
        });
        test("listTasks: tasks without a dueDate sort last in both directions", () -> {
            TaskApi api = seeded();
            equal("3 2 1 4 5", ids(api.listTasks(sorted("dueDate", "asc"))));
            equal("1 2 3 4 5", ids(api.listTasks(sorted("dueDate", "desc"))));
        });
        test("listTasks: createdAt ties are broken by id, so the order is stable", () -> {
            TaskApi api = seeded();
            equal("1 2 3 4 5", ids(api.listTasks(sorted("createdAt", "asc"))));
            equal("5 4 3 2 1", ids(api.listTasks(sorted("createdAt", "desc"))));
        });
        test("listTasks: filter, search, sort and pagination work in one call", () -> {
            ApiResponse r = seeded().listTasks(new TaskQuery.Builder()
                    .completed(false).search("buy").sortBy("dueDate").order("asc").pageSize(1).page(2).build());
            equal("5", ids(r));
            contains(r, "\"total\":2");
            contains(r, "\"totalPages\":2");
        });
        test("listTasks: no matches gives an empty list, and a blank search is ignored", () -> {
            TaskApi api = seeded();
            equal("", ids(api.listTasks(new TaskQuery.Builder().priority("LOW").completed(true).build())));
            equal("1 2 3 4 5", ids(api.listTasks(new TaskQuery.Builder().search("   ").build())));
        });
        test("listByPriority: invalid or null level returns 400, valid level returns matches", () -> {
            TaskApi api = seeded();
            error(400, "VALIDATION_ERROR", api.listByPriority("urgent"));
            error(400, "VALIDATION_ERROR", api.listByPriority(null));
            equal("1 3", ids(api.listByPriority("HIGH")));
        });

        // --- events ---
        test("events: removing a listener stops its notifications", () -> {
            TaskService service = new TaskService(InMemoryTaskRepository.getInstance());
            AuditTrailListener audit = new AuditTrailListener();
            service.addListener(audit);
            TaskApi api = new TaskApi(service);
            api.createTask(req("A", "A"), null);
            service.removeListener(audit);
            api.createTask(req("B", "B"), null);
            equal(1, audit.getCount(TaskEventType.CREATED));
        });
        test("events: COMPLETED fires only when a task becomes completed", () -> {
            TaskService service = new TaskService(InMemoryTaskRepository.getInstance());
            AuditTrailListener audit = new AuditTrailListener();
            service.addListener(audit);
            TaskApi api = new TaskApi(service);
            api.createTask(req("A", "A"), null);
            api.updateTask(1, req("A", "A", "true", null, null));
            api.updateTask(1, req("A", "A", "true", null, null));
            equal(1, audit.getCount(TaskEventType.COMPLETED));
            equal(2, audit.getCount(TaskEventType.UPDATED));
        });
        test("events: failed operations and idempotent retries publish nothing", () -> {
            TaskService service = new TaskService(InMemoryTaskRepository.getInstance());
            AuditTrailListener audit = new AuditTrailListener();
            service.addListener(audit);
            TaskApi api = new TaskApi(service);
            api.createTask(req("", ""), null);
            api.getTask(99);
            api.updateTask(99, req("A", "A"));
            api.createTask(req("A", "A"), "k");
            api.createTask(req("A", "A"), "k");
            equal("{CREATED=1}", audit.getCounts().toString());
        });
        test("events: deleting a parent publishes DELETED for each subtask and the parent", () -> {
            TaskService service = new TaskService(InMemoryTaskRepository.getInstance());
            AuditTrailListener audit = new AuditTrailListener();
            service.addListener(audit);
            TaskApi api = new TaskApi(service);
            api.createTask(req("P", "P"), null);
            api.createSubtask(1, req("S1", "S1"));
            api.createSubtask(1, req("S2", "S2"));
            api.deleteTask(1);
            equal(3, audit.getCount(TaskEventType.DELETED));
        });
        test("events: a listener that throws does not break the operation", () -> {
            TaskService service = new TaskService(InMemoryTaskRepository.getInstance());
            AuditTrailListener audit = new AuditTrailListener();
            service.addListener((type, task) -> { throw new RuntimeException("boom"); });
            service.addListener(audit);
            status(201, new TaskApi(service).createTask(req("A", "A"), null));
            equal(1, audit.getCount(TaskEventType.CREATED));
        });
    }
}