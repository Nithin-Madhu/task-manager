package parser;

import exception.ServiceUnavailableException;

public final class ParserFactory {
    private static final String UNAVAILABLE =
            "LLM is not available: set the LLM_API_KEY environment variable";

    private ParserFactory() {}

    public static TaskParser createParser(String apiKey, LlmClient client, boolean allowOffline) {
        if (hasKey(apiKey) && client != null) return new LlmTaskParser(client);
        if (allowOffline) return new RuleBasedTaskParser();
        return (text, today) -> { throw new ServiceUnavailableException(UNAVAILABLE); };
    }

    public static BreakdownStrategy createBreakdown(String apiKey, LlmClient client, boolean allowOffline) {
        if (hasKey(apiKey) && client != null) return new LlmBreakdownStrategy(client);
        if (allowOffline) return new RuleBasedBreakdownStrategy();
        return task -> { throw new ServiceUnavailableException(UNAVAILABLE); };
    }

    private static boolean hasKey(String key) {
        return key != null && !key.trim().isEmpty();
    }
}