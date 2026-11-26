package api;

import exception.ApiException;
import exception.FieldIssue;
import exception.ValidationException;

import java.util.List;

public final class ErrorResponseFactory {
    private ErrorResponseFactory() {}

    public static ApiResponse from(ApiException e) {
        List<FieldIssue> issues = (e instanceof ValidationException)
                ? ((ValidationException) e).getIssues()
                : List.of();
        return build(e.getStatus(), e.getCode(), e.getMessage(), issues);
    }

    public static ApiResponse internalError() {
        return build(500, "INTERNAL_ERROR", "Unexpected error", List.of());
    }

    private static ApiResponse build(int status, String code, String message,
                                     List<FieldIssue> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"error\":{")
                .append("\"code\":\"").append(escape(code)).append("\",")
                .append("\"message\":\"").append(escape(message)).append("\",")
                .append("\"details\":[");
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) sb.append(",");
            FieldIssue f = issues.get(i);
            sb.append("{\"field\":\"").append(escape(f.getField()))
                    .append("\",\"issue\":\"").append(escape(f.getIssue())).append("\"}");
        }
        sb.append("]}}");
        return new ApiResponse(status, sb.toString());
    }

    private static String escape(String s) {
        return JsonUtil.escape(s);
    }
}