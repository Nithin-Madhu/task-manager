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