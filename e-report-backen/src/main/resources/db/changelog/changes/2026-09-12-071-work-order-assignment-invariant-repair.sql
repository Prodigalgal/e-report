--liquibase formatted sql

--changeset e-report:2026-09-12-071-repair-assignment-slots-postgresql dbms:postgresql
-- 仅修复 active_slot 与 state 不一致的历史行，保留原有 assignment 状态和审计字段。
UPDATE work_order_assignments
SET active_slot = NULL,
    ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP)
WHERE (active_slot = 'CURRENT' AND state <> 'ACCEPTED')
   OR (active_slot = 'PENDING' AND state <> 'PENDING_ACCEPTANCE');

-- 指针以 assignment 的有效槽位为准，先清除跨租户、跨工单或已失效的历史指针。
UPDATE fault_reports fr
SET current_assignment_id = NULL
WHERE fr.current_assignment_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.id = fr.current_assignment_id
        AND assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'ACCEPTED'
        AND assignment.active_slot = 'CURRENT'
  );

UPDATE fault_reports fr
SET pending_assignment_id = NULL
WHERE fr.pending_assignment_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.id = fr.pending_assignment_id
        AND assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'PENDING_ACCEPTANCE'
        AND assignment.active_slot = 'PENDING'
  );

UPDATE fault_reports fr
SET current_assignment_id = (
    SELECT assignment.id
    FROM work_order_assignments assignment
    WHERE assignment.tenant_id = fr.tenant_id
      AND assignment.work_order_id = fr.id
      AND assignment.state = 'ACCEPTED'
      AND assignment.active_slot = 'CURRENT'
    ORDER BY assignment.assigned_at DESC, assignment.id DESC
    LIMIT 1
)
WHERE fr.current_assignment_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'ACCEPTED'
        AND assignment.active_slot = 'CURRENT'
  );

UPDATE fault_reports fr
SET pending_assignment_id = (
    SELECT assignment.id
    FROM work_order_assignments assignment
    WHERE assignment.tenant_id = fr.tenant_id
      AND assignment.work_order_id = fr.id
      AND assignment.state = 'PENDING_ACCEPTANCE'
      AND assignment.active_slot = 'PENDING'
    ORDER BY assignment.assigned_at DESC, assignment.id DESC
    LIMIT 1
)
WHERE fr.pending_assignment_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'PENDING_ACCEPTANCE'
        AND assignment.active_slot = 'PENDING'
  );

ALTER TABLE work_order_assignments
    DROP CONSTRAINT IF EXISTS ck_work_order_assignment_slot_state;
ALTER TABLE work_order_assignments
    ADD CONSTRAINT ck_work_order_assignment_slot_state
        CHECK (active_slot IS NULL
            OR (active_slot = 'CURRENT' AND state = 'ACCEPTED')
            OR (active_slot = 'PENDING' AND state = 'PENDING_ACCEPTANCE'));

--rollback ALTER TABLE work_order_assignments DROP CONSTRAINT IF EXISTS ck_work_order_assignment_slot_state;

--changeset e-report:2026-09-12-071-repair-assignment-slots-h2 dbms:h2
UPDATE work_order_assignments
SET active_slot = NULL,
    ended_at = COALESCE(ended_at, CURRENT_TIMESTAMP)
WHERE (active_slot = 'CURRENT' AND state <> 'ACCEPTED')
   OR (active_slot = 'PENDING' AND state <> 'PENDING_ACCEPTANCE');

UPDATE fault_reports fr
SET current_assignment_id = NULL
WHERE fr.current_assignment_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.id = fr.current_assignment_id
        AND assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'ACCEPTED'
        AND assignment.active_slot = 'CURRENT'
  );

UPDATE fault_reports fr
SET pending_assignment_id = NULL
WHERE fr.pending_assignment_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.id = fr.pending_assignment_id
        AND assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'PENDING_ACCEPTANCE'
        AND assignment.active_slot = 'PENDING'
  );

UPDATE fault_reports fr
SET current_assignment_id = (
    SELECT assignment.id
    FROM work_order_assignments assignment
    WHERE assignment.tenant_id = fr.tenant_id
      AND assignment.work_order_id = fr.id
      AND assignment.state = 'ACCEPTED'
      AND assignment.active_slot = 'CURRENT'
    ORDER BY assignment.assigned_at DESC, assignment.id DESC
    LIMIT 1
)
WHERE fr.current_assignment_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'ACCEPTED'
        AND assignment.active_slot = 'CURRENT'
  );

UPDATE fault_reports fr
SET pending_assignment_id = (
    SELECT assignment.id
    FROM work_order_assignments assignment
    WHERE assignment.tenant_id = fr.tenant_id
      AND assignment.work_order_id = fr.id
      AND assignment.state = 'PENDING_ACCEPTANCE'
      AND assignment.active_slot = 'PENDING'
    ORDER BY assignment.assigned_at DESC, assignment.id DESC
    LIMIT 1
)
WHERE fr.pending_assignment_id IS NULL
  AND EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.tenant_id = fr.tenant_id
        AND assignment.work_order_id = fr.id
        AND assignment.state = 'PENDING_ACCEPTANCE'
        AND assignment.active_slot = 'PENDING'
  );

ALTER TABLE work_order_assignments
    DROP CONSTRAINT IF EXISTS ck_work_order_assignment_slot_state;
ALTER TABLE work_order_assignments
    ADD CONSTRAINT ck_work_order_assignment_slot_state
        CHECK (active_slot IS NULL
            OR (active_slot = 'CURRENT' AND state = 'ACCEPTED')
            OR (active_slot = 'PENDING' AND state = 'PENDING_ACCEPTANCE'));

--rollback ALTER TABLE work_order_assignments DROP CONSTRAINT IF EXISTS ck_work_order_assignment_slot_state;
