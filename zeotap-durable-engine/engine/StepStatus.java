package engine;

/**
 * Enum representing the possible states of a step in the workflow.
 * 
 * NOT_STARTED - Step has not been executed yet
 * RUNNING - Step is currently executing (crash here = zombie step)
 * COMPLETED - Step completed successfully, output is memoized
 * FAILED - Step failed, can be retried
 */
public enum StepStatus {
    RUNNING,
    COMPLETED,
    FAILED
}

