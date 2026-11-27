# Task Manager

A task manager written in plain java 25, with no frameworks & no external libraries.
Turns a sentence into tasks, divides a large task into subtasks. Supporting safe retries (idempotency),
validation with a single shape, filtering, sorting & pagination. This service works like a REST API
without the framework. Each operation is a method in TaskApi which returns ApiResponse having a JSON body
with status code.

## Package structure
### Package	Contents

| Package | classes          |
|---------|------------------|
| model     | Task (immutable, Builder), Priority, TaskRequest (raw String input), TaskQuery (Builder), PageResult |
|  repository | TaskRepository (interface), InMemoryTaskRepository (Singleton) |
| service | TaskService (business logic), TaskValidator, QueryPlanner, TaskFilter with CompletedFilter, PriorityFilter, SearchFilter, and SortStrategy with CreatedAtSort, DueDateSort |
| parser | TaskParser with RuleBasedTaskParser, LlmTaskParser; BreakdownStrategy with RuleBasedBreakdownStrategy, LlmBreakdownStrategy; ParsedTask, ParserFactory, LlmClient, SimpleJson |
| event | TaskEventListener, TaskEventType, ConsoleLogListener, AuditTrailListener |
| exception | ApiException and its subclasses ValidationException (400), NotFoundException (404), ConflictException (422), BadGatewayException (502), ServiceUnavailableException (503); FieldIssue |
| api | TaskApi, ApiResponse, ErrorResponseFactory, IdempotencyStore (Singleton), JsonUtil |
| app | Main (demo), TestRunner (PASS/FAIL checks), FakeLlmClient, FaultyLlmClient |


## Setup

You need JDK 17 or newer.
From the project root, in Windows Command Prompt:

    mkdir out
    javac -d out src\exception\*.java src\model\*.java src\repository\*.java src\parser\*.java src\event\*.java src\service\*.java src\api\*.java src\app\*.java
    java -cp out app.Main
    java -cp out app.TestRunner

Main prints a demo of every operation. 
TestRunner prints PASS or FAIL for each test and exits with a non-zero code if any test fails. 
Neither needs an API key nor an internet connection.

## API operations

All operations are methods on TaskApi. Every error uses the same shape.

Operation	Parameters	Success	Errors
createTask(request, idempotencyKey)	TaskRequest: title, description (required); completed ("true"/"false"), dueDate (YYYY-MM-DD), priority (LOW/MEDIUM/HIGH) optional. Key optional.	201, the task	400 invalid input, 422 key reused with a different request
getTask(id)	task id	200, the task	404 unknown id
listTasks(query)	TaskQuery, all fields optional: completed, priority, search, sortBy (createdAt/dueDate), order (asc/desc), page, pageSize. null means defaults (page 1, size 10).	200, {"tasks":[...],"page","pageSize","total","totalPages"}	400 invalid query value
updateTask(id, request)	task id, TaskRequest	200, the updated task	404 unknown id, 400 invalid input
deleteTask(id)	task id	200, {"deleted":true,"id":N}; also deletes the task's subtasks	404 unknown id
createTaskFromText(text, idempotencyKey)	plain-English text, key optional	201, the task	400 blank text, 422 key conflict, 502 parser failed or produced invalid output, 503 LLM key missing
createSubtask(parentId, request)	parent id, TaskRequest	201, the subtask	404 unknown parent, 400 parent is itself a subtask or input invalid
listSubtasks(parentId)	parent id	200, {"tasks":[...]}	404 unknown parent
breakDownTask(id, idempotencyKey)	task id, key optional	201, {"tasks":[...]} the new subtasks	404 unknown id, 400 task is a subtask, 422 key conflict, 502 breakdown failed or any item invalid (nothing saved), 503 LLM key missing
listByPriority(level)	"LOW", "MEDIUM" or "HIGH"	200, {"tasks":[...]} (not paged)	400 invalid level
Error shape

Every error returned by TaskApi looks like this. code and message are always strings, and details is empty except for validation errors.

    json
        {
        "error": {
        "code": "VALIDATION_ERROR",
        "message": "Validation failed",
        "details": [{ "field": "title", "issue": "must not be empty" }]
        }
    }

| Status | Code | Meaning |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid input |
| 404 | NOT_FOUND | Unknown ID |
| 422 | CONFLICT | Idempotency key reused with a different request |
| 500 | INTERNAL_ERROR | Unexpected error |
| 502 | BAD_GATEWAY | LLM call failed or returned invalid output |
| 503 | SERVICE_UNAVAILABLE | LLM_API_KEY missing |

Examples

Each example shows the call and the ApiResponse it printed in Main.

1. createTask

api.createTask(new TaskRequest("Pay rent", "Monthly rent", null, "2026-12-01", "HIGH"), null)

    {
        "status":201,
        "body":{
            "id":1,
            "title":"Pay rent",
            "description":"Monthly rent",
            "completed":false,
            "dueDate":"2026-12-01",
            "priority":"HIGH",
            "parentId":null,
            "createdAt":1795695564471
        }
    }


createTask, validation error (all problems reported together)


api.createTask(new TaskRequest("", "", "yes", "2026-02-30", "URGENT"), null)

    {
    "status":400,
    "body":{
        "error":{
            "code":"VALIDATION_ERROR",
            "message":"Validation failed",
            "details":
                [ {"field":"title","issue":"must not be empty"},
                {"field":"description","issue":"must not be empty"},
                {"field":"completed","issue":"must be 'true' or 'false'"},
                {"field":"dueDate","issue":"must be a valid date in YYYY-MM-DD format"},
                {"field":"priority","issue":"must be LOW, MEDIUM, or HIGH"}]
            }
        }
    }

2. getTask

api.getTask(1)

    {
    "status":200,
    "body":{
        "id":1, 
        "title":"Pay rent",
        "description":"Monthly rent",
        "completed":false,
        "dueDate":"2026-12-01",
        "priority":"HIGH",
        "parentId":null,
        "createdAt":1795695564471
        }
    }

Unknown id (api.getTask(99)):


    {
    "status":404,
    "body": {
        "error":{
            "code":"NOT_FOUND",
            "message":"Task 99 not found",
            "details":[]
            }
        }
    }

listTasks

api.listTasks(new TaskQuery.Builder().completed(false).sortBy("dueDate").order("asc").pageSize(2).page(1).build())

    {
    "status":200,
    "body":{
        "tasks":[{"id":3,"title":"Renew passport", ...}],
        "page":1,
        "pageSize":2,
        "total":4,
        "totalPages":2
        }
    }


updateTask

api.updateTask(1, new TaskRequest("Pay rent", "Monthly rent", "true", "2026-12-01", "LOW"))

    {
    "status":200,
    "body":{
        "id":1,
        "title":"Pay rent",
        "description":"Monthly rent",
        "completed":true,
        "dueDate":"2026-12-01",
        "priority":"LOW",
        "parentId":null,
        "createdAt":1795695564471
        }
    }

deleteTask

api.deleteTask(1)

    {
    "status":200,
    "body":{
        "deleted":true,
        "id":1
        }
    }

createSubtask

api.createSubtask(1, new TaskRequest("Book flights", "Book flights", null, null, null))

    {
    "status":201,
    "body":{
        "id":3,
        "title":"Book flights",
        "description":"Book flights",
        "completed":false,
        "dueDate":null,
        "priority":"MEDIUM",
        "parentId":1,
        "createdAt":1795695564493
        }
    }

A subtask cannot have subtasks


    {
    "status":400,
    "body":{
        "error":{
            "code":"VALIDATION_ERROR",
            "message":"Validation failed",
            "details":[{"field":"parentId","issue":"task 3 is a subtask and cannot have subtasks"}]
            }
        }
    }

listSubtasks

api.listSubtasks(2)

    {
    "status":200,
    "body":{
        "tasks":[
            {"id":4,
            "title":"Book flights",
            "description":"Book flights",
            "completed":false,
            "dueDate":null,
            "priority":"MEDIUM",
            "parentId":2,
            "createdAt":1795694430366},
            {"id":5,
            "title":"Book hotel",
            "description":"Book hotel",
            "completed":false,
            "dueDate":null,
            "priority":"MEDIUM",
            "parentId":2,
            "createdAt":1795694430366}
            ]
        }
    }

listByPriority


api.listByPriority("urgent")

    {
    "status":400,
    "body":{
        "error":{
            "code":"VALIDATION_ERROR",
            "message":"Validation failed",
            "details":[{"field":"priority","issue":"must be LOW, MEDIUM, or HIGH"}]
            }
        }
    }


createTaskFromText

api.createTaskFromText("Remind me to pay rent on the 1st of every month", "text-1")

    {
    "status":201,
    "body":{
        "id":1,
        "title":"Pay rent",
        "description":"Remind me to pay rent on the 1st of every month (repeats monthly)",
        "completed":false,
        "dueDate":"2026-12-01",
        "priority":"MEDIUM",
        "parentId":null,
        "createdAt":1795695564531
        }
    }


breakDownTask


api.breakDownTask(2, "bd-1")

    {
    "status":201,
    "body":{
        "tasks":[
            {"id":3,"title":"Book flights","description":"Book flights","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":2,"createdAt":1795695564535},
            {"id":4,"title":"Book hotel","description":"Book hotel","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":2,"createdAt":1795695564535},
            {"id":5,"title":"Pack bags","description":"Pack bags","completed":false,"dueDate":null,"priority":"MEDIUM","parentId":2,"createdAt":1795695564535}
            ]
        }
    }

Idempotency conflict (422)


    {
    "status":422,
    "body":{
        "error":{
            "code":"CONFLICT",
            "message":"Idempotency key was already used with a different request",
            "details":[]}
        }
    }

Bad model output (502): the model returned an empty title, so nothing was saved.


    {
    "status":502,
    "body":{
        "error":{"code":"BAD_GATEWAY",
        "message":"Parser output was invalid: title must not be empty",
        "details":[]
            }
        }
    }

Missing key (503)

    {
    "status":503,
    "body":{
        "error":{
            "code":"SERVICE_UNAVAILABLE",
            "message":"LLM is not available: set the LLM_API_KEY environment variable",
            "details":[]
            }
        }
    }

## Design patterns used

| Pattern   | classes |
|-----------| --- |
| Builder	  |    Task.Builder (title, description, completed required; the rest optional) and TaskQuery.Builder |
| Singleton |	InMemoryTaskRepository and IdempotencyStore. Both are created when the class loads, so they are thread-safe, and their methods are synchronized |
| Factory   |	    ErrorResponseFactory.from(ApiException) builds the error shape in one place. ParserFactory chooses the LLM parser, the rule-based parser, or a parser that reports 503 |
| Strategy  |	TaskParser (rule-based or LLM), BreakdownStrategy (rule-based or LLM), SortStrategy (CreatedAtSort, DueDateSort). TaskService does not know which one it has |
| Observer  |	TaskService is the subject. TaskEventListener implementations (ConsoleLogListener, AuditTrailListener) are notified after a task is created, updated, completed, or deleted |
