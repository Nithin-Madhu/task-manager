package api;

import exception.ConflictException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class IdempotencyStore {
    private static final IdempotencyStore INSTANCE = new IdempotencyStore();

    private static class Record {
        final String fingerprint;
        final ApiResponse response;

        Record(String fingerprint, ApiResponse response) {
            this.fingerprint = fingerprint;
            this.response = response;
        }
    }

    private final Map<String, Record> records = new HashMap<>();

    private IdempotencyStore() {}

    public static IdempotencyStore getInstance() { return INSTANCE; }

    public synchronized ApiResponse execute(String key, String fingerprint, Supplier<ApiResponse> action) {
        if (key == null || key.trim().isEmpty()) {
            return action.get();
        }
        Record existing = records.get(key);
        if (existing != null) {
            if (existing.fingerprint.equals(fingerprint)) {
                return existing.response;
            }
            return ErrorResponseFactory.from(
                    new ConflictException("Idempotency key was already used with a different request"));
        }
        ApiResponse response = action.get();
        if (response.status() >= 200 && response.status() < 300) {
            records.put(key, new Record(fingerprint, response));
        }
        return response;
    }

    public synchronized void clear() {
        records.clear();
    }
}