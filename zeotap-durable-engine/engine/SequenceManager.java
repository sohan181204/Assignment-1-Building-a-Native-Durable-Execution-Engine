package engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages automatic sequence ID generation for steps.
 * 
 * This ensures:
 * - Loop-safe step execution
 * - Branch-safe step execution
 * - No manual step IDs required
 * - Deterministic step ordering
 * 
 * Uses atomic counter for thread-safety.
 */
public class SequenceManager {
    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Get the next sequence ID.
     * 
     * @return monotonic increasing sequence number
     */
    public long next() {
        return counter.incrementAndGet();
    }

    /**
     * Reset the counter (useful for testing).
     */
    public void reset() {
        counter.set(0);
    }

    /**
     * Get current sequence value without incrementing.
     */
    public long current() {
        return counter.get();
    }
}
