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
| listTasks() | 200, `{"tasks":[...]}` | none yet (changes in Step 7) |
| updateTask(id, request) | 200, the updated task | 404, 400 |
| deleteTask(id) | 200, `{"deleted":true,"id":N}` | 404 |

- `createdAt`, `id` and `parentId` can't be changed through a request; update keeps the original values.

## Open decisions

- `completed`: if null it becomes "false". 
If added, must be "true" or "false";
anything else returns 400.

- `priority`: when null defaults to MEDIUM; 
otherwise would LOW, MEDIUM, or HIGH.
- 
- `dueDate`: null means no due date; 
otherwise must be a real YYYY-MM-DD date. 
An empty string is invalid.

- `updateTask`: an unknown ID returns 404 before input is validated.