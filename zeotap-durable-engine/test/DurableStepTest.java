package test;

import engine.*;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the durable execution engine.
 * These tests verify the core functionality works correctly.
 */
public class DurableStepTest {

    /**
     * Test that steps execute and return results.
     */
    @Test
    void stepShouldExecuteAndReturnResult() throws Exception {
        Path db = Files.createTempFile("workflow-test", ".db");
        SQLiteStore store = new SQLiteStore(db.toString());
        DurableContext ctx = new DurableContext("test-workflow-1", store);

        String result = StepExecutor.step(
                ctx,
                "test-step",
                new TypeReference<String>() {
                },
                () -> "test-result");

        assertEquals("test-result", result);
        store.close();
    }

    /**
     * Test that steps are memoized across workflow restarts.
     * This simulates the crash-recovery scenario.
     */
    @Test
    void stepShouldMemoizeAcrossWorkflowRestarts() throws Exception {
        Path db = Files.createTempFile("workflow-test", ".db");

        // First workflow execution
        {
            SQLiteStore store = new SQLiteStore(db.toString());
            DurableContext ctx = new DurableContext("test-workflow-same", store);

            AtomicInteger counter = new AtomicInteger(0);

            String result = StepExecutor.step(
                    ctx,
                    "expensive-operation",
                    new TypeReference<String>() {
                    },
                    () -> {
                        counter.incrementAndGet();
                        return "computed-result";
                    });

            assertEquals("computed-result", result);
            assertEquals(1, counter.get());
            store.close();
        }

        // Simulate restart - new context, same workflow ID
        {
            SQLiteStore store = new SQLiteStore(db.toString());
            DurableContext ctx = new DurableContext("test-workflow-same", store);

            AtomicInteger counter = new AtomicInteger(0);

            String result = StepExecutor.step(
                    ctx,
                    "expensive-operation",
                    new TypeReference<String>() {
                    },
                    () -> {
                        counter.incrementAndGet();
                        return "computed-result";
                    });

            // Should return cached result, not re-execute
            assertEquals("computed-result", result);
            assertEquals(0, counter.get()); // Function was NOT called
            store.close();
        }
    }

    /**
     * Test that different step names get different results.
     */
    @Test
    void differentStepNamesShouldExecuteSeparately() throws Exception {
        Path db = Files.createTempFile("workflow-test", ".db");
        SQLiteStore store = new SQLiteStore(db.toString());
        DurableContext ctx = new DurableContext("test-workflow-3", store);

        String result1 = StepExecutor.step(
                ctx,
                "step-a",
                new TypeReference<String>() {
                },
                () -> "result-a");

        String result2 = StepExecutor.step(
                ctx,
                "step-b",
                new TypeReference<String>() {
                },
                () -> "result-b");

        assertEquals("result-a", result1);
        assertEquals("result-b", result2);

        store.close();
    }

    /**
     * Test parallel step execution.
     */
    @Test
    void parallelStepsShouldExecuteSafely() throws Exception {
        Path db = Files.createTempFile("workflow-test", ".db");
        SQLiteStore store = new SQLiteStore(db.toString());
        DurableContext ctx = new DurableContext("test-workflow-4", store);

        assertDoesNotThrow(() -> {
            java.util.concurrent.CompletableFuture.allOf(
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            StepExecutor.step(ctx, "parallel-1", new TypeReference<String>() {
                            }, () -> "ok1");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }),
                    java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            StepExecutor.step(ctx, "parallel-2", new TypeReference<String>() {
                            }, () -> "ok2");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })).join();
        });

        store.close();
    }

    /**
     * Test Saga compensation is executed on failure.
     */
    @Test
    void sagaStepShouldExecuteCompensationOnFailure() throws Exception {
        Path db = Files.createTempFile("workflow-test", ".db");
        SQLiteStore store = new SQLiteStore(db.toString());
        DurableContext ctx = new DurableContext("test-workflow-5", store);

        AtomicInteger compensationCalled = new AtomicInteger(0);

        // First create a successful step
        StepExecutor.sagaStep(
                ctx,
                "setup",
                new TypeReference<String>() {
                },
                () -> "setup-done",
                () -> compensationCalled.incrementAndGet());

        // Then try a step that will fail - should trigger compensation
        assertThrows(Exception.class, () -> {
            StepExecutor.sagaStep(
                    ctx,
                    "fail-step",
                    new TypeReference<String>() {
                    },
                    () -> {
                        throw new RuntimeException("Intentional failure");
                    },
                    () -> compensationCalled.incrementAndGet());
        });

        // Compensation should have been called (at least once from rollback)
        assertTrue(compensationCalled.get() >= 1);

        store.close();
    }

    /**
     * Test workflow cancellation.
     */
    @Test
    void workflowShouldSupportCancellation() throws Exception {
        Path db = Files.createTempFile("workflow-test", ".db");
        SQLiteStore store = new SQLiteStore(db.toString());
        DurableContext ctx = new DurableContext("test-workflow-6", store);

        // Cancel the workflow
        store.cancelWorkflow(ctx.workflowId);

        // Attempting to execute a step should throw WorkflowCancelledException
        assertThrows(WorkflowCancelledException.class, () -> {
            StepExecutor.step(
                    ctx,
                    "any-step",
                    new TypeReference<String>() {
                    },
                    () -> "result");
        });

        store.close();
    }
}
