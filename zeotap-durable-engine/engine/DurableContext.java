package engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Durable execution context for a workflow.
 * 
 * Holds:
 * - workflow ID for identification
 * - SQLite store for persistence
 * - Sequence manager for automatic step IDs
 * - Compensation stack for Saga pattern
 */
public class DurableContext {
    public final String workflowId;
    public final SQLiteStore store;
    public final SequenceManager sequence;

    // Saga compensation stack
    private final Deque<Runnable> compensationStack = new ArrayDeque<>();

    public DurableContext(String workflowId, SQLiteStore store) {
        this.workflowId = workflowId;
        this.store = store;
        this.sequence = new SequenceManager();
    }

    /**
     * Check if workflow has been cancelled.
     * 
     * @throws WorkflowCancelledException if workflow is cancelled
     */
    public void checkCancelled() {
        try {
            if (store.isWorkflowCancelled(workflowId)) {
                throw new WorkflowCancelledException(workflowId);
            }
        } catch (WorkflowCancelledException e) {
            throw e;
        } catch (Exception e) {
            // Log but don't block on check errors
        }
    }

    /**
     * Add a compensation action to the stack.
     */
    public void addCompensation(Runnable compensation) {
        compensationStack.push(compensation);
    }

    /**
     * Execute all compensations in reverse order (LIFO).
     */
    public void executeCompensations() {
        while (!compensationStack.isEmpty()) {
            try {
                compensationStack.pop().run();
            } catch (Exception e) {
                // Log but continue with other compensations
                System.err.println("Compensation failed: " + e.getMessage());
            }
        }
    }

    /**
     * Get compensation stack size.
     */
    public int getCompensationCount() {
        return compensationStack.size();
    }
}
