package api;

public class ApiResponse {
    private final int status;
    private final String body; // already-built JSON text

    public ApiResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() { return status; }
    public String getBody() { return body; }

    public String toJson() {
        return "{\"status\":" + status + ",\"body\":" + body + "}";
    }

    @Override
    public String toString() { return toJson(); }
}