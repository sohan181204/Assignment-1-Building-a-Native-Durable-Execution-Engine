package engine;

/**
 * Retry policy for step execution.
 * Supports exponential backoff for resilient failure handling.
 */
public class RetryPolicy {
    public final int maxAttempts;
    public final long initialBackoffMs;

    public RetryPolicy(int maxAttempts, long initialBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * Calculate backoff time for a given attempt using exponential strategy.
     * 
     * @param attempt the attempt number (1-indexed)
     * @return backoff time in milliseconds
     */
    public long backoffForAttempt(int attempt) {
        return initialBackoffMs * (1L << (attempt - 1));
    }

    /**
     * Default retry policy: 3 attempts with 1 second initial backoff.
     */
    public static final RetryPolicy DEFAULT = new RetryPolicy(3, 1000);

    /**
     * Aggressive retry policy: 5 attempts with 500ms initial backoff.
     */
    public static final RetryPolicy AGGRESSIVE = new RetryPolicy(5, 500);

    /**
     * No retry policy.
     */
    public static final RetryPolicy NONE = new RetryPolicy(1, 0);
}
