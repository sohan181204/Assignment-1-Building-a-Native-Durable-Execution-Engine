-- Durable Execution Engine Database Schema

-- Steps table for persistent step memoization
CREATE TABLE IF NOT EXISTS steps (
    workflow_id TEXT NOT NULL,
    step_key TEXT NOT NULL,
    step_name TEXT NOT NULL,
    sequence_id INTEGER NOT NULL,
    status TEXT CHECK(status IN ('RUNNING','COMPLETED','FAILED')) NOT NULL,
    error TEXT,
    output TEXT,
    retry_count INTEGER DEFAULT 0,
    next_retry_at INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workflow_id, step_key)
);

-- Index for fast step lookup
CREATE INDEX IF NOT EXISTS idx_steps_lookup
ON steps(workflow_id, step_key);

-- Workflows table for workflow-level state
CREATE TABLE IF NOT EXISTS workflows (
    workflow_id TEXT PRIMARY KEY,
    status TEXT CHECK(status IN ('RUNNING','CANCELLED','COMPLETED')) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

