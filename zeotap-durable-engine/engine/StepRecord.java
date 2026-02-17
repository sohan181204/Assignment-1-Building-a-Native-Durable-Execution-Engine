package engine;

/**
 * Represents a single step record in the durable execution workflow.
 * This is stored in SQLite for persistence across crashes.
 */
public class StepRecord {
    public final String workflowId;
    public final String stepKey;
    public final String stepName;
    public final long sequenceId;
    public final String status;
    public final String output;
    public final String error;
    public final int retryCount;
    public final Long nextRetryAt;

    public StepRecord(String workflowId, String stepKey, String stepName, 
                      long sequenceId, String status, String output, 
                      String error, int retryCount, Long nextRetryAt) {
        this.workflowId = workflowId;
        this.stepKey = stepKey;
        this.stepName = stepName;
        this.sequenceId = sequenceId;
        this.status = status;
        this.output = output;
        this.error = error;
        this.retryCount = retryCount;
        this.nextRetryAt = nextRetryAt;
    }

    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }

    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean canRetry() {
        return isFailed() && nextRetryAt != null && nextRetryAt <= System.currentTimeMillis();
    }
}

