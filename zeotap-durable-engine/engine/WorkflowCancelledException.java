package engine;

/**
 * Exception thrown when attempting to execute a step in a cancelled workflow.
 */
public class WorkflowCancelledException extends RuntimeException {

    private final String workflowId;

    public WorkflowCancelledException(String workflowId) {
        super("Workflow cancelled: " + workflowId);
        this.workflowId = workflowId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
