package api;

import model.PageResult;
import model.Task;

import java.util.List;

final class JsonUtil {
    private JsonUtil() {}

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String stringOrNull(String s) {
        return s == null ? "null" : "\"" + escape(s) + "\"";
    }

    static String toJson(Task t) {
        return "{\"id\":" + t.getId()
                + ",\"title\":" + stringOrNull(t.getTitle())
                + ",\"description\":" + stringOrNull(t.getDescription())
                + ",\"completed\":" + t.isCompleted()
                + ",\"dueDate\":" + stringOrNull(t.getDueDate())
                + ",\"priority\":\"" + t.getPriority() + "\""
                + ",\"parentId\":" + (t.getParentId() == null ? "null" : t.getParentId())
                + ",\"createdAt\":" + t.getCreatedAt() + "}";
    }

    static String toJson(List<Task> tasks) {
        StringBuilder sb = new StringBuilder("{\"tasks\":[");
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(tasks.get(i)));
        }
        return sb.append("]}").toString();
    }

    static String toJson(PageResult p) {
        StringBuilder sb = new StringBuilder("{\"tasks\":[");
        for (int i = 0; i < p.getTasks().size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(p.getTasks().get(i)));
        }
        return sb.append("],\"page\":").append(p.getPage())
                .append(",\"pageSize\":").append(p.getPageSize())
                .append(",\"total\":").append(p.getTotal())
                .append(",\"totalPages\":").append(p.getTotalPages())
                .append("}").toString();
    }
}