package engine;

/**
 * Exception thrown when a step execution fails.
 */
public class StepExecutionException extends RuntimeException {

    public StepExecutionException(String message) {
        super(message);
    }

    public StepExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
