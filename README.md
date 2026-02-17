# Durable Execution Engine (Java)

## Overview

This project implements a native durable execution engine in Java, inspired by industry workflow systems such as Temporal, Cadence, DBOS, and Azure Durable Functions.

The engine enables developers to write normal, idiomatic Java code (using loops, conditionals, and concurrency) while gaining durability guarantees. A workflow can be interrupted at any point (process crash, JVM exit, power loss) and, upon restart, resume execution without re-executing previously completed side effects.

Durability is achieved through explicit step memoization backed by a relational database (SQLite).

## Core Concepts

### Step Primitive

All side-effecting operations are wrapped in a `step()` call.

### Memoization

Each step's result is persisted in SQLite and reused on restart.

### Crash Recovery

On restart, workflow logic is replayed but completed steps are skipped.

### Concurrency

Parallel steps are supported safely using Java concurrency primitives with synchronized database writes.

### Saga Compensation

Optional compensating actions allow partial rollback of long-running workflows.

## Architecture

```
┌─────────────────────┐
│  CLI / App Runner   │
│  (start / resume)  │
└─────────┬───────────┘
          ↓
┌─────────────────────┐
│ Workflow Runner     │
│ - workflow_id       │
│ - context restore   │
└─────────┬───────────┘
          ↓
┌──────────────────────────────────────┐
│ DurableContext                       │
│ - workflowId                         │
│ - sequence manager                   │
│ - compensation stack                 │
│ - database handle                    │
└─────────┬────────────────────────────┘
          ↓
┌──────────────────────────────────────┐
│ StepExecutor                         │
│ - step memoization                   │
│ - retry & exponential backoff        │
│ - transaction safety                 │
│ - zombie-step protection             │
│ - metrics & tracing                  │
└─────────┬────────────────────────────┘
          ↓
┌─────────────────────┐
│ SQLite Persistence  │
│ - steps table       │
│ - workflows table   │
└─────────────────────┘
```

## Step Execution Model

Each step follows a durable lifecycle:

```
NOT_STARTED → RUNNING → COMPLETED
                ↓
              FAILED (retryable)
```

- A step is first marked RUNNING
- On successful execution, it is marked COMPLETED with serialized output
- If the process crashes before completion, the step may remain in RUNNING state
- Such steps are treated as zombie steps and are safely retried on restart

## Sequence ID Handling

Each step is assigned an automatic sequence ID using an atomic counter. The final step key is:

```
stepName#sequenceId
```

This ensures:

- Loop-safe execution
- Branch-safe execution
- No manual step identifiers required
- Stable step identification across workflow restarts

Parallel steps may complete in non-deterministic order, but step keys remain stable, ensuring correct memoization.

## Zombie Step Handling

Steps marked RUNNING but not COMPLETED are considered safe to retry. This engine intentionally provides at-least-once execution semantics, consistent with industry workflow engines.

External side effects are assumed to be idempotent or protected using unique request identifiers.

## Enterprise-Grade Enhancements

- **Type-safe deserialization** using Jackson TypeReference<T>
- **Structured exception hierarchy**
  - StepExecutionException
  - WorkflowException
  - WorkflowCancelledException
- **Retry with exponential backoff**, persisted across crashes
- **Workflow cancellation**, persisted and checked before every step
- **Saga compensation** for partial rollback of long-running workflows
- **Metrics & tracing** (step count, failures, latency)

## Example Workflow: Employee Onboarding

The sample workflow demonstrates real-world orchestration:

1. Create employee record (sequential)
2. Provision laptop (parallel)
3. Grant system access (parallel)
4. Send welcome email (sequential)

This example proves:

- Sequential and parallel execution
- Durable memoization
- Safe replay after crash

## Running the Application

### Using Maven

```bash
mvn compile exec:java \
  -Dexec.mainClass="app.Main" \
  -Dexec.args="employee-1"
```

### Using the Fat JAR

```bash
java -jar target/durable-execution-engine-1.0.0.jar demo-workflow --db demo.db
```

### Demonstrating Durability

```bash
# First run (executes all steps)
java -jar target/durable-execution-engine-1.0.0.jar demo-workflow --db demo.db

# Second run (skips completed steps)
java -jar target/durable-execution-engine-1.0.0.jar demo-workflow --db demo.db
```

## Testing

```bash
mvn test
```

Unit tests validate:

- Step memoization
- Crash recovery behavior
- Parallel execution safety

## Database Schema

```sql
CREATE TABLE steps (
    workflow_id TEXT NOT NULL,
    step_key TEXT NOT NULL,
    step_name TEXT NOT NULL,
    sequence_id INTEGER NOT NULL,
    status TEXT CHECK(status IN ('RUNNING','COMPLETED','FAILED')),
    output TEXT,
    error TEXT,
    retry_count INTEGER DEFAULT 0,
    next_retry_at INTEGER,
    PRIMARY KEY (workflow_id, step_key)
);

CREATE TABLE workflows (
    workflow_id TEXT PRIMARY KEY,
    status TEXT CHECK(status IN ('RUNNING','CANCELLED','COMPLETED'))
);
```

## Deterministic Execution Model

Unlike replay-based engines such as Temporal, this system does not re-execute workflow logic from the beginning. Instead:

1. Workflow code is re-run normally
2. Completed steps return persisted outputs
3. Remaining steps continue execution

As long as workflow logic remains deterministic outside of step boundaries, the engine guarantees correct and safe recovery after crashes.

This design trades deterministic replay for explicit step memoization, resulting in a simpler, debuggable, and SQLite-friendly execution model.

## Design Inspirations

This engine combines ideas from:

- **Temporal / Cadence** — durable workflow semantics
- **DBOS** — database-backed execution
- **Saga Pattern** — compensating transactions
- **SQLite** — lightweight relational durability

