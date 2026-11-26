package api;

/**
 * @param body already-built JSON text
 */
public record ApiResponse(int status, String body) {

    public String toJson() {
        return "{\"status\":" + status + ",\"body\":" + body + "}";
    }

    @Override
    public String toString() {
        return toJson();
    }
}