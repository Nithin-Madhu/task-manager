package app;

import parser.LlmClient;

public class FaultyLlmClient implements LlmClient {
    public static final String EMPTY_TITLE =
            "{\"title\":\"\",\"description\":\"d\",\"dueDate\":null,\"recurrence\":null}";
    public static final String IMPOSSIBLE_DATE =
            "{\"title\":\"T\",\"description\":\"d\",\"dueDate\":\"2026-02-30\",\"recurrence\":null}";
    public static final String MALFORMED = "Sure! Here is your task: title = pay rent";
    public static final String WRONG_TYPE =
            "{\"title\":42,\"description\":\"d\",\"dueDate\":null,\"recurrence\":null}";
    public static final String BREAKDOWN_EMPTY_TITLE = "Book flights|Find a flight\n|Missing title";
    public static final String BREAKDOWN_NO_PIPE = "Book flights|Find a flight\nBook hotel without a separator";

    private final String reply;   // null means "throw"
    private int calls;

    public FaultyLlmClient(String reply) { this.reply = reply; }

    public static FaultyLlmClient throwing() { return new FaultyLlmClient(null); }

    @Override
    public String complete(String prompt) {
        calls++;
        if (reply == null) throw new RuntimeException("simulated LLM timeout");
        return reply;
    }

    public int getCalls() { return calls; }
}