package examples.onboarding;

import engine.DurableContext;
import engine.Metrics;
import engine.RetryPolicy;
import engine.StepExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Employee Onboarding Workflow - Reference Implementation
 * 
 * Demonstrates:
 * - Sequential steps
 * - Parallel execution
 * - Retry policies
 * - Saga compensation
 * - Crash-safe durability
 */
public class EmployeeOnboardingWorkflow {

    private final DurableContext ctx;
    private final ExecutorService executor;

    public EmployeeOnboardingWorkflow(DurableContext ctx) {
        this.ctx = ctx;
        this.executor = Executors.newFixedThreadPool(2);
    }

    /**
     * Run the complete onboarding workflow.
     */
    public void run() throws Exception {

        // Step 1: Create employee (sequential)
        StepExecutor.step(ctx, "create-employee", () -> {
            System.out.println("Creating employee record...");
            simulateWork(100);
            return "EMP_CREATED";
        });

        // Steps 2 & 3: Parallel execution
        CompletableFuture<String> laptopFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return StepExecutor.step(
                        ctx,
                        "provision-laptop",
                        new com.fasterxml.jackson.core.type.TypeReference<String>() {
                        },
                        RetryPolicy.DEFAULT,
                        () -> {
                            System.out.println("Provisioning laptop...");
                            simulateWork(200);
                            return "LAPTOP_READY";
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);

        CompletableFuture<String> accessFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return StepExecutor.step(
                        ctx,
                        "grant-access",
                        new com.fasterxml.jackson.core.type.TypeReference<String>() {
                        },
                        RetryPolicy.DEFAULT,
                        () -> {
                            System.out.println("Granting system access...");
                            simulateWork(150);
                            return "ACCESS_GRANTED";
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);

        // Wait for both parallel steps
        CompletableFuture.allOf(laptopFuture, accessFuture).join();

        // Step 4: Send welcome email (sequential)
        StepExecutor.step(ctx, "send-welcome-email", () -> {
            System.out.println("Sending welcome email...");
            simulateWork(50);
            return "EMAIL_SENT";
        });

        executor.shutdown();
        System.out.println("Employee onboarding workflow completed successfully!");
    }

    /**
     * Run workflow with Saga compensation for rollback.
     */
    public void runWithCompensation() throws Exception {

        // Step 1: Create employee with compensation
        StepExecutor.sagaStep(ctx, "create-employee",
                () -> {
                    System.out.println("Creating employee record...");
                    simulateWork(100);
                    return "EMP_CREATED";
                },
                () -> System.out.println("Compensation: Deleting employee record..."));

        // Step 2: Provision laptop with compensation
        StepExecutor.sagaStep(ctx, "provision-laptop",
                RetryPolicy.DEFAULT,
                () -> {
                    System.out.println("Provisioning laptop...");
                    simulateWork(200);
                    return "LAPTOP_READY";
                },
                () -> System.out.println("Compensation: Returning laptop..."));

        // Step 3: Grant access with compensation
        StepExecutor.sagaStep(ctx, "grant-access",
                RetryPolicy.DEFAULT,
                () -> {
                    System.out.println("Granting system access...");
                    simulateWork(150);
                    return "ACCESS_GRANTED";
                },
                () -> System.out.println("Compensation: Revoking access..."));

        // Step 4: Send welcome email
        StepExecutor.step(ctx, "send-welcome-email", () -> {
            System.out.println("Sending welcome email...");
            simulateWork(50);
            return "EMAIL_SENT";
        });

        System.out.println("Employee onboarding workflow completed successfully!");
    }

    /**
     * Simulate work with sleep.
     */
    private void simulateWork(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
