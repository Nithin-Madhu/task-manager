# API CONTRACT

## ERROR RESPONSE

Every error from TaskApi uses  `code` and `message` 
that are strings.

    {
      "error": {
        "code": "VALIDATION_ERROR",
        "message": "Validation failed",
        "details": [{ "field": "title", "issue": "must not be empty" }]
      }
    }

`details` is an empty list for every error except validation errors.

| Status | Code | Meaning |
|---|---|---|
| 400 | VALIDATION_ERROR | Invalid input |
| 404 | NOT_FOUND | Unknown ID |
| 422 | CONFLICT | Idempotency key reused with a different request |
| 500 | INTERNAL_ERROR | Unexpected error |
| 502 | BAD_GATEWAY | LLM call failed or returned invalid output |
| 503 | SERVICE_UNAVAILABLE | LLM_API_KEY missing |

## Operations

| Operation | Success | Errors |
|---|---|---|
| createTask(request, idempotencyKey) | 201, the task | 400 |
| getTask(id) | 200, the task | 404 |
| listTasks(query) | 200, `{"tasks":[...],"page":N,"pageSize":N,"total":N,"totalPages":N}` | 400 invalid query |
| listByPriority(level) | 200, `{"tasks":[...]}` (not paged) | 400 invalid level |
| updateTask(id, request) | 200, the updated task | 404, 400 |
| deleteTask(id) | 200, `{"deleted":true,"id":N}` | 404 |
| createTaskFromText(text, idempotencyKey) | 201, the task | 400 blank text, 422 key reused with different text, 502 parser failed or produced invalid output, 503 LLM key missing (LLM mode only) |
| breakDownTask(id, idempotencyKey) | 201, `{"tasks":[...]}` the new subtasks | 404 unknown id, 400 task is a subtask, 422 key conflict, 502 breakdown failed or any item invalid (nothing saved) |
| createSubtask(parentId, request) | 201, the subtask | 404 unknown parent, 400 parent is a subtask or invalid input |
| listSubtasks(parentId) | 200, `{"tasks":[...]}` | 404 unknown parent |
- `createdAt`, `id` and `parentId` can't be changed through a request; update keeps the original values.

## Open decisions

- `completed`: if null it becomes "false". 
If added, must be "true" or "false";
anything else returns 400.

- `priority`: when null defaults to MEDIUM; 
otherwise would LOW, MEDIUM, or HIGH.

- `dueDate`: null means no due date; 
otherwise must be a real YYYY-MM-DD date. 
An empty string is invalid.

- `updateTask`: an unknown ID returns 404 before input is validated.

- A subtask cannot have subtasks: createSubtask on a subtask returns 400 (VALIDATION_ERROR, field `parentId`).
- Checks run in this order for createSubtask: parent exists (404), parent is top-level (400), request valid (400).
- listSubtasks on an unknown ID returns 404, not an empty list.

- createTaskFromText: parser output is validated with the same rules as createTask. Any failure is 502 and nothing is saved. "today" is the server date.
- A recurrence found by the parser is appended to the description as "(repeats monthly)"; tasks have no recurrence field.
- breakDownTask validates every item before saving any. Calling it again with a different key creates more subtasks.

- createTaskFromText and breakDownTask return 503 (SERVICE_UNAVAILABLE) when LLM mode is selected and LLM_API_KEY is missing. Offline mode uses the rule-based classes instead.
- LLM output of the wrong type (for example a numeric title) returns 502, and nothing is saved.

## Idempotency

`createTask` accepts an optional `idempotencyKey` (null or blank = no idempotency).
- Same key, same request: the original response is returned and nothing new is created.
- Same key, different request: 422 CONFLICT.
- Only successful (2xx) responses are remembered, so a failed request can be corrected and retried with the same key.
- A key is tied to one operation; reusing it on a different operation is also 422.

## Query rules (listTasks)

All fields are optional. Defaults: page 1, pageSize 10, no sorting (creation order).
- `completed`: true returns only completed tasks, false only open ones.
- `priority`: LOW, MEDIUM, or HIGH (case-sensitive).
- `search`: case-insensitive match on title or description; blank is ignored.
- `sortBy`: createdAt or dueDate. `order`: "asc" (default) or "desc". Ties break by id in the same direction.
- Tasks with no dueDate sort last in both directions.
- `page` and `pageSize` must be 1 or greater. A page past the last returns 200 with an empty list.
- Results include subtasks.
- Invalid values return 400 and every problem is listed in `details`.