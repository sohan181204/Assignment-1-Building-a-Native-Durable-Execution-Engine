package app;

import engine.DurableContext;
import engine.Metrics;
import engine.SQLiteStore;
import examples.onboarding.EmployeeOnboardingWorkflow;

/**
 * CLI Runner for Durable Workflow Engine.
 * 
 * Usage:
 * java Main <workflow-id> [crash-after-step]
 * 
 * Examples:
 * java Main employee-1 - Run workflow normally
 * java Main employee-1 crash - Run and crash after all steps
 * java Main employee-1 2 - Crash after step 2
 * java Main employee-1 resume - Resume a crashed workflow
 */
public class Main {

    public static void main(String[] args) {

        String workflowId = "employee-1";
        int crashAfterStep = -1;
        boolean resumeMode = false;

        // Parse arguments
        if (args.length > 0) {
            workflowId = args[0];
        }
        if (args.length > 1) {
            if ("crash".equals(args[1])) {
                crashAfterStep = Integer.MAX_VALUE;
            } else if ("resume".equals(args[1])) {
                resumeMode = true;
            } else {
                try {
                    crashAfterStep = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid argument: " + args[1]);
                    System.err.println("Usage: java Main <workflow-id> [crash-after-step]");
                    System.exit(1);
                }
            }
        }

        String dbPath = "workflow.db";

        try {
            SQLiteStore store = new SQLiteStore(dbPath);
            DurableContext ctx = new DurableContext(workflowId, store);

            // Initialize workflow
            store.upsertWorkflow(workflowId, "RUNNING");

            EmployeeOnboardingWorkflow workflow = new EmployeeOnboardingWorkflow(ctx);

            System.out.println("=== Durable Workflow Engine ===");
            System.out.println("Workflow ID: " + workflowId);
            System.out.println("Database: " + dbPath);
            System.out.println("================================");

            // Check if resuming - count completed steps
            if (resumeMode) {
                var completedSteps = store.getCompletedSteps(workflowId);
                System.out.println("Resuming workflow with " + completedSteps.size() + " completed steps");
                Metrics.workflowRestarts.incrementAndGet();
            }

            // Execute workflow
            if (crashAfterStep >= 0) {
                // Simulate crash at specific step
                executeWithCrashSimulation(ctx, workflow, crashAfterStep);
            } else {
                workflow.run();
            }

            // Mark workflow completed
            store.upsertWorkflow(workflowId, "COMPLETED");

            System.out.println("\n=== Workflow Summary ===");
            System.out.println(Metrics.summary());
            System.out.println("========================");

            store.close();

        } catch (Exception e) {
            System.err.println("Workflow failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Execute workflow with crash simulation at specified step.
     */
    private static void executeWithCrashSimulation(
            DurableContext ctx,
            EmployeeOnboardingWorkflow workflow,
            int crashAfterStep) throws Exception {

        System.out.println("CRASH SIMULATION: Will crash after step " + crashAfterStep);
        System.out.println();

        int stepCounter = 0;

        // Step 1: create-employee
        stepCounter++;
        if (stepCounter == crashAfterStep) {
            System.out.println(">>> CRASH after step " + stepCounter + " (create-employee)");
            ctx.store.upsertWorkflow(ctx.workflowId, "RUNNING");
            System.exit(1);
        }

        engine.StepExecutor.step(ctx, "create-employee", () -> {
            System.out.println("[Step 1] Creating employee record...");
            Thread.sleep(50);
            return "EMP_CREATED";
        });

        // Parallel steps (count as 2 more steps)
        stepCounter++; // provision-laptop
        if (stepCounter == crashAfterStep) {
            System.out.println(">>> CRASH after step " + stepCounter + " (provision-laptop)");
            ctx.store.upsertWorkflow(ctx.workflowId, "RUNNING");
            System.exit(1);
        }

        engine.StepExecutor.step(ctx, "provision-laptop", () -> {
            System.out.println("[Step 2a] Provisioning laptop...");
            Thread.sleep(50);
            return "LAPTOP_READY";
        });

        stepCounter++; // grant-access
        if (stepCounter == crashAfterStep) {
            System.out.println(">>> CRASH after step " + stepCounter + " (grant-access)");
            ctx.store.upsertWorkflow(ctx.workflowId, "RUNNING");
            System.exit(1);
        }

        engine.StepExecutor.step(ctx, "grant-access", () -> {
            System.out.println("[Step 2b] Granting system access...");
            Thread.sleep(50);
            return "ACCESS_GRANTED";
        });

        // Step 4: send-welcome-email
        stepCounter++;
        if (stepCounter == crashAfterStep) {
            System.out.println(">>> CRASH after step " + stepCounter + " (send-welcome-email)");
            ctx.store.upsertWorkflow(ctx.workflowId, "RUNNING");
            System.exit(1);
        }

        engine.StepExecutor.step(ctx, "send-welcome-email", () -> {
            System.out.println("[Step 3] Sending welcome email...");
            Thread.sleep(50);
            return "EMAIL_SENT";
        });

        System.out.println("\nWorkflow completed (no crash triggered)");
    }
}
