# TODO: Durable Workflow Engine Implementation

## Phase 1: Core Infrastructure ✅
- [x] Create project structure and directories
- [x] Create pom.xml with dependencies
- [x] Create db/schema.sql with database schema
- [x] Create engine/StepRecord.java
- [x] Create engine/StepStatus.java enum
- [x] Create engine/SequenceManager.java
- [x] Create engine/SQLiteStore.java
- [x] Create engine/DurableContext.java

## Phase 2: Core Execution Engine ✅
- [x] Create engine/StepExecutor.java (core logic)
- [x] Create engine/StepExecutionException.java
- [x] Create engine/WorkflowException.java

## Phase 3: Enterprise Features ✅
- [x] Create engine/RetryPolicy.java
- [x] Create engine/WorkflowCancelledException.java
- [x] Create engine/SagaStep.java
- [x] Create engine/Metrics.java

## Phase 4: Example & CLI ✅
- [x] Create examples/onboarding/EmployeeOnboardingWorkflow.java
- [x] Create app/Main.java with crash simulation

## Phase 5: Tests ✅
- [x] Create test/DurableStepTest.java

## Phase 6: Documentation ✅
- [x] Create README.md
- [x] Create Prompts.txt

## Phase 7: Build & Test ✅
- [x] Compile the project
- [x] Run tests (6 tests passing)
- [x] Demonstrate crash simulation (works correctly)
- [x] Demonstrate resume after crash (works correctly)

