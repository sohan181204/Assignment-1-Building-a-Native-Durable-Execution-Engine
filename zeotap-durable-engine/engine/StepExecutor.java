package engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core step executor for durable workflows.
 * 
 * Features:
 * - Automatic step memoization
 * - Type-safe deserialization
 * - Retry with exponential backoff
 * - Saga compensation support
 * - Metrics & tracing
 * - Zombie step detection
 */
public class StepExecutor {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Metrics
    public static final AtomicLong stepCount = new AtomicLong(0);
    public static final AtomicLong stepFailures = new AtomicLong(0);

    /**
     * Execute a step with type inference.
     */
    public static <T> T step(
            DurableContext ctx,
            String name,
            Callable<T> fn) throws Exception {
        return step(ctx, name, new TypeReference<T>() {
        }, null, fn);
    }

    /**
     * Execute a step with explicit type.
     */
    public static <T> T step(
            DurableContext ctx,
            String name,
            TypeReference<T> type,
            Callable<T> fn) throws Exception {
        return step(ctx, name, type, null, fn);
    }

    /**
     * Execute a step with retry policy.
     */
    public static <T> T step(
            DurableContext ctx,
            String name,
            TypeReference<T> type,
            RetryPolicy retryPolicy,
            Callable<T> fn) throws Exception {

        // Check for workflow cancellation before executing
        ctx.checkCancelled();

        long seq = ctx.sequence.next();
        String stepKey = name + "#" + seq;

        long startTime = System.nanoTime();

        try {
            // 1. Lookup existing step result
            StepRecord record = ctx.store.find(ctx.workflowId, stepKey);

            // If completed, return cached result (memoization)
            if (record != null && record.isCompleted()) {
                System.out.println("[STEP] Skipping completed step: " + stepKey);
                return mapper.readValue(record.output, type);
            }

            // Handle retry scenario
            if (record != null && record.canRetry() && retryPolicy != null) {
                int attempt = record.retryCount + 1;
                if (attempt > retryPolicy.maxAttempts) {
                    throw new StepExecutionException("Retry limit exceeded for: " + stepKey);
                }
                System.out.println("[STEP] Retrying step: " + stepKey + " (attempt " + attempt + ")");
            }

            // 2. Mark step as RUNNING (start execution)
            ctx.store.markRunning(ctx.workflowId, stepKey, name, seq);
            System.out.println("[STEP] Executing: " + stepKey);

            // 3. Execute the step function
            T result = fn.call();

            // 4. Commit result atomically
            String serialized = mapper.writeValueAsString(result);
            ctx.store.markCompleted(ctx.workflowId, stepKey, serialized);

            // Update metrics
            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
            stepCount.incrementAndGet();
            System.out.println("[STEP] Completed: " + stepKey + " in " + latencyMs + "ms");

            return result;

        } catch (Exception e) {
            // Handle failure with retry policy
            stepFailures.incrementAndGet();

            if (retryPolicy != null) {
                handleRetry(ctx, stepKey, name, seq, e, retryPolicy);
            } else {
                ctx.store.markFailed(ctx.workflowId, stepKey, e.getMessage());
            }

            throw new StepExecutionException("Step failed: " + stepKey, e);
        }
    }

    /**
     * Handle retry with exponential backoff.
     */
    private static void handleRetry(
            DurableContext ctx,
            String stepKey,
            String name,
            long seq,
            Exception e,
            RetryPolicy policy) throws Exception {
        try {
            StepRecord record = ctx.store.find(ctx.workflowId, stepKey);
            int attempt = (record != null) ? record.retryCount + 1 : 1;

            if (attempt >= policy.maxAttempts) {
                ctx.store.markFailed(ctx.workflowId, stepKey, "Retry limit exceeded: " + e.getMessage());
                throw new StepExecutionException("Retry limit exceeded for: " + stepKey, e);
            }

            long nextRetryAt = System.currentTimeMillis() + policy.backoffForAttempt(attempt);
            ctx.store.markFailed(ctx.workflowId, stepKey, e.getMessage(), attempt, nextRetryAt);

            System.out.println("[STEP] Scheduled retry for: " + stepKey + " at " + nextRetryAt);
        } catch (StepExecutionException see) {
            throw see;
        } catch (Exception ee) {
            ctx.store.markFailed(ctx.workflowId, stepKey, ee.getMessage());
            throw new StepExecutionException("Step failed: " + stepKey, ee);
        }
    }

    /**
     * Execute a Saga step with compensation.
     */
    public static <T> T sagaStep(
            DurableContext ctx,
            String name,
            Callable<T> action,
            Runnable compensation) throws Exception {
        return sagaStep(ctx, name, new TypeReference<T>() {
        }, null, action, compensation);
    }

    /**
     * Execute a Saga step with compensation and type.
     */
    public static <T> T sagaStep(
            DurableContext ctx,
            String name,
            TypeReference<T> type,
            Callable<T> action,
            Runnable compensation) throws Exception {
        return sagaStep(ctx, name, type, null, action, compensation);
    }

    /**
     * Execute a Saga step with compensation and retry policy (no explicit type).
     */
    public static <T> T sagaStep(
            DurableContext ctx,
            String name,
            RetryPolicy retryPolicy,
            Callable<T> action,
            Runnable compensation) throws Exception {
        return sagaStep(ctx, name, new TypeReference<T>() {
        }, retryPolicy, action, compensation);
    }

    /**
     * Execute a Saga step with compensation, type, and retry policy.
     */
    public static <T> T sagaStep(
            DurableContext ctx,
            String name,
            TypeReference<T> type,
            RetryPolicy retryPolicy,
            Callable<T> action,
            Runnable compensation) throws Exception {

        try {
            T result = step(ctx, name, type, retryPolicy, action);
            // Add compensation to stack on success
            ctx.addCompensation(compensation);
            return result;
        } catch (Exception e) {
            // Execute compensations in reverse order on failure
            System.out.println("[SAGA] Rolling back due to failure in: " + name);
            ctx.executeCompensations();
            throw e;
        }
    }
}
