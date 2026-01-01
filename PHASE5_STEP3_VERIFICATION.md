# Phase 5 – Step 3: Human-in-the-Loop (HITL) Verification

## Status: COMPLETED

This document verifies the implementation of Phase 5 – Step 3: Human-in-the-Loop (HITL) support for workflows.

---

## Implementation Summary

### Components Added/Modified

1. **StepDefinition.java** - Extended to support `HUMAN_APPROVAL` step type with:
   - `approvalPrompt` - Text shown to human
   - `allowedActions` - List of allowed decisions (e.g., ["APPROVE", "REJECT"])
   - `timeoutPolicy` - WAIT or FAIL
   - `timeoutMs` - Optional timeout in milliseconds

2. **ApprovalService.java** (NEW) - Manages human approvals:
   - Tracks pending approvals in memory and database
   - Records approval decisions in audit trail
   - Ensures idempotent approval resolution
   - Loads pending approvals on startup

3. **WorkflowEngine.java** - Extended to:
   - Detect and pause at `HUMAN_APPROVAL` steps
   - Resume workflow after approval resolution
   - Handle approval steps during workflow resumption

4. **DatabaseManager.java** - Added `workflow_approval` table for audit trail

5. **KernelMain.java** - Added IPC commands:
   - `RESOLVE_APPROVAL` - Resolve a pending approval
   - `GET_PENDING_APPROVALS` - List all pending approvals

6. **WorkflowPersistenceService.java** - Added methods:
   - `pauseWorkflowForApproval()` - Mark workflow as paused waiting for approval
   - `getWorkflowId()` - Get workflow ID for an execution
   - `loadWorkflowDefinition()` - Load workflow definition from database

---

## Example Workflow JSON with HUMAN_APPROVAL Step

```json
{
  "workflow_id": "approval-workflow-001",
  "name": "Data Processing with Approval",
  "version": "1.0",
  "steps": [
    {
      "step_id": "step1",
      "type": "PYTHON_TASK",
      "input_mapping": {
        "operation": "process_data",
        "data": "${initial.data}"
      },
      "retry_policy": {
        "max_attempts": 3,
        "backoff_ms": 1000
      },
      "on_failure": "FAIL"
    },
    {
      "step_id": "approval_step",
      "type": "HUMAN_APPROVAL",
      "prompt": "Please review the processed data and approve or reject the results. Data summary: ${step1.summary}",
      "allowed_actions": ["APPROVE", "REJECT"],
      "timeout_policy": "WAIT",
      "on_failure": "FAIL"
    },
    {
      "step_id": "step2",
      "type": "GO_API_CALL",
      "input_mapping": {
        "operation": "send_notification",
        "message": "Data processing approved and completed"
      },
      "retry_policy": {
        "max_attempts": 2,
        "backoff_ms": 500
      },
      "on_failure": "SKIP"
    }
  ]
}
```

**Key Features:**
- Step 1: Python task processes data
- Step 2: HUMAN_APPROVAL step pauses workflow and waits for human decision
- Step 3: Go API call sends notification (only if approval granted)

---

## Example Approval IPC Payload

### RESOLVE_APPROVAL Command

**Request:**
```json
{
  "id": "msg-123",
  "type": "RESOLVE_APPROVAL",
  "payload": {
    "execution_id": "exec-abc-123",
    "step_id": "approval_step",
    "decision": "APPROVE",
    "actor_id": "user-john-doe",
    "comment": "Data looks good, approved for production"
  },
  "timestamp": 1704067200000
}
```

**Response (Success):**
```json
{
  "id": "msg-123",
  "success": true,
  "result": {
    "execution_id": "exec-abc-123",
    "step_id": "approval_step",
    "decision": "APPROVE",
    "resumed": true
  }
}
```

**Response (Error - Already Resolved):**
```json
{
  "id": "msg-123",
  "success": false,
  "error": {
    "code": "APPROVAL_ERROR",
    "message": "Approval not found or already resolved"
  }
}
```

### GET_PENDING_APPROVALS Command

**Request:**
```json
{
  "id": "msg-124",
  "type": "GET_PENDING_APPROVALS",
  "payload": {},
  "timestamp": 1704067200000
}
```

**Response:**
```json
{
  "id": "msg-124",
  "success": true,
  "result": {
    "pending_approvals": [
      {
        "execution_id": "exec-abc-123",
        "step_id": "approval_step",
        "prompt": "Please review the processed data and approve or reject the results. Data summary: 1000 records processed",
        "allowed_actions": ["APPROVE", "REJECT"],
        "requested_at": 1704067100000
      }
    ]
  }
}
```

---

## Example Audit Record

### Database: workflow_approval Table

**Schema:**
```sql
CREATE TABLE workflow_approval (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    execution_id TEXT NOT NULL,
    step_id TEXT NOT NULL,
    prompt TEXT NOT NULL,
    allowed_actions TEXT NOT NULL,
    decision TEXT,
    actor_id TEXT,
    comment TEXT,
    requested_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    resolved_at DATETIME
);
```

**Example Record (Pending):**
```
id: 1
execution_id: exec-abc-123
step_id: approval_step
prompt: Please review the processed data and approve or reject the results. Data summary: 1000 records processed
allowed_actions: APPROVE,REJECT
decision: NULL
actor_id: NULL
comment: NULL
requested_at: 2024-01-01 10:00:00
resolved_at: NULL
```

**Example Record (Resolved - Approved):**
```
id: 1
execution_id: exec-abc-123
step_id: approval_step
prompt: Please review the processed data and approve or reject the results. Data summary: 1000 records processed
allowed_actions: APPROVE,REJECT
decision: APPROVE
actor_id: user-john-doe
comment: Data looks good, approved for production
requested_at: 2024-01-01 10:00:00
resolved_at: 2024-01-01 10:15:30
```

**Example Record (Resolved - Rejected):**
```
id: 2
execution_id: exec-xyz-456
step_id: approval_step
prompt: Please review the processed data and approve or reject the results. Data summary: 500 records processed
allowed_actions: APPROVE,REJECT
decision: REJECT
actor_id: user-jane-smith
comment: Data quality issues detected, needs reprocessing
requested_at: 2024-01-01 11:00:00
resolved_at: 2024-01-01 11:20:15
```

---

## Workflow Execution Flow

### Scenario 1: Approval Granted

1. **Workflow starts** → Step 1 executes (Python task)
2. **Step 1 completes** → Step 2 (HUMAN_APPROVAL) starts
3. **Workflow pauses** → Status: `paused_waiting_for_approval`
4. **Approval requested** → Recorded in `workflow_approval` table
5. **Human approves** → IPC `RESOLVE_APPROVAL` with `decision: "APPROVE"`
6. **Workflow resumes** → Step 2 marked completed, Step 3 executes
7. **Workflow completes** → Status: `completed`

### Scenario 2: Approval Rejected

1. **Workflow starts** → Step 1 executes (Python task)
2. **Step 1 completes** → Step 2 (HUMAN_APPROVAL) starts
3. **Workflow pauses** → Status: `paused_waiting_for_approval`
4. **Approval requested** → Recorded in `workflow_approval` table
5. **Human rejects** → IPC `RESOLVE_APPROVAL` with `decision: "REJECT"`
6. **Workflow fails** → Status: `failed`, no further steps execute

### Scenario 3: Kernel Restart During Approval

1. **Workflow paused** → Status: `paused_waiting_for_approval` in database
2. **Kernel restarts** → `loadPendingApprovals()` loads pending approvals
3. **Approval still pending** → Workflow remains paused
4. **Human approves** → IPC `RESOLVE_APPROVAL` resolves and resumes workflow

---

## Success Criteria Verification

✅ **Workflow pauses correctly at approval step**
- Workflow status changes to `paused_waiting_for_approval`
- No further steps execute until approval resolved

✅ **Manual approval via IPC resumes workflow**
- `RESOLVE_APPROVAL` IPC command successfully resolves approval
- Workflow resumes from next step after approval

✅ **Rejection fails workflow deterministically**
- Rejection marks workflow as `failed`
- No further steps execute after rejection

✅ **Approval decisions are audited and persisted**
- All approval requests recorded in `workflow_approval` table
- All decisions include `actor_id`, `comment`, and `resolved_at` timestamp
- Audit records are immutable (no updates after resolution)

✅ **Restart does NOT lose approval state**
- Pending approvals loaded from database on startup
- Workflow remains paused until approval resolved

✅ **No AI or automation makes decisions**
- All approvals require explicit human input via IPC
- No automatic approval rules implemented
- System enforces and records, humans decide

---

## Final Verdict

**Phase 5 – Step 3: COMPLETED (HUMAN-IN-THE-LOOP)**

All functional requirements met:
- ✅ HITL step type (HUMAN_APPROVAL) implemented
- ✅ Workflow pause & resume logic working
- ✅ Manual decision input via IPC (RESOLVE_APPROVAL)
- ✅ Audit trail persistence in SQLite
- ✅ Deterministic failure handling (REJECT = FAIL)
- ✅ Resumption after restart supported
- ✅ No AI decision-making (humans decide, system enforces)

---

## Notes

- Timeout policy (WAIT/FAIL) is defined but timeout enforcement is not yet implemented in this phase (acceptable per scope)
- Approval decisions are irreversible (by design - audit trail integrity)
- Multiple approvals per workflow are supported (each step can be HUMAN_APPROVAL)
- Approval service is idempotent (resolving same approval twice returns false, no side effects)

