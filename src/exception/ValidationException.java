package exception;

import java.util.List;

public class ValidationException extends ApiException {
    private final List<FieldIssue> issues;

    public ValidationException(List<FieldIssue> issues) {
        super(400, "VALIDATION_ERROR", "Validation failed");
        this.issues = List.copyOf(issues);
    }

    public List<FieldIssue> getIssues() { return issues; }
}