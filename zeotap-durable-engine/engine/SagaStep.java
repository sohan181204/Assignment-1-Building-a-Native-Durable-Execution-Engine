package engine;

import java.util.concurrent.Callable;

/**
 * Represents a Saga step with action and compensation.
 * 
 * Used for implementing the Saga pattern where each step has
 * an associated compensation action for rollback.
 * 
 * @param <T> the return type of the action
 */
public class SagaStep<T> {
    public final Callable<T> action;
    public final Runnable compensation;

    public SagaStep(Callable<T> action, Runnable compensation) {
        this.action = action;
        this.compensation = compensation;
    }

    /**
     * Create a Saga step from lambdas.
     */
    public static <T> SagaStep<T> of(Callable<T> action, Runnable compensation) {
        return new SagaStep<>(action, compensation);
    }
}
