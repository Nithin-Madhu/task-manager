package app;

import parser.LlmClient;

public class FakeLlmClient implements LlmClient {
    private final String reply;
    private int calls;
    private String lastPrompt;

    public FakeLlmClient(String reply) { this.reply = reply; }

    @Override
    public String complete(String prompt) {
        calls++;
        lastPrompt = prompt;
        return reply;
    }

    public int getCalls() { return calls; }
    public String getLastPrompt() { return lastPrompt; }
}