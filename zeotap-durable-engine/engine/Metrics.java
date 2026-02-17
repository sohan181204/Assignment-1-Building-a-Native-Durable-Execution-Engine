package engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collection for the durable execution engine.
 * 
 * Tracks:
 * - Total steps executed
 * - Step failures
 * - Workflow restarts
 */
public class Metrics {

    public static final AtomicLong stepCount = new AtomicLong(0);
    public static final AtomicLong stepFailures = new AtomicLong(0);
    public static final AtomicLong workflowRestarts = new AtomicLong(0);
    public static final AtomicLong compensationCount = new AtomicLong(0);

    /**
     * Reset all metrics (useful for testing).
     */
    public static void reset() {
        stepCount.set(0);
        stepFailures.set(0);
        workflowRestarts.set(0);
        compensationCount.set(0);
    }

    /**
     * Get current metrics summary.
     */
    public static String summary() {
        return String.format(
                "Metrics: steps=%d, failures=%d, restarts=%d, compensations=%d",
                stepCount.get(),
                stepFailures.get(),
                workflowRestarts.get(),
                compensationCount.get());
    }
}
