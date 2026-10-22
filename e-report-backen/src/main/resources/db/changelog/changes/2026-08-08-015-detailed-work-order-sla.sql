--liquibase formatted sql

--changeset e-report:2026-08-08-015-detailed-work-order-sla-mysql dbms:mysql
ALTER TABLE work_order_sla_policies ADD COLUMN assignment_acceptance_minutes INT;
ALTER TABLE work_order_sla_policies ADD COLUMN rework_minutes INT;
UPDATE work_order_sla_policies
SET assignment_acceptance_minutes = claim_minutes,
    rework_minutes = response_minutes;
ALTER TABLE work_order_sla_policies MODIFY COLUMN assignment_acceptance_minutes INT NOT NULL;
ALTER TABLE work_order_sla_policies MODIFY COLUMN rework_minutes INT NOT NULL;

ALTER TABLE fault_reports ADD COLUMN sla_resume_stage VARCHAR(30);
ALTER TABLE fault_reports ADD COLUMN sla_resume_due_at DATETIME(6);
ALTER TABLE fault_reports ADD COLUMN sla_resume_breached_at DATETIME(6);

UPDATE fault_reports
SET sla_stage = 'REWORK'
WHERE rework_count > 0
  AND sla_stage = 'RESPONSE'
  AND status_code IN ('IN_PROGRESS', 'ON_HOLD');

UPDATE fault_reports fr
JOIN work_order_assignments assignment ON assignment.id = fr.pending_assignment_id
JOIN work_order_sla_policies policy
  ON policy.tenant_id = fr.tenant_id
 AND policy.report_type = fr.report_type
 AND policy.priority = fr.priority
SET fr.sla_resume_stage = CASE
        WHEN fr.sla_stage IN ('CLAIM', 'RESPONSE', 'REWORK') THEN fr.sla_stage
        ELSE NULL
    END,
    fr.sla_resume_due_at = CASE
        WHEN fr.sla_stage IN ('CLAIM', 'RESPONSE', 'REWORK') THEN fr.sla_due_at
        ELSE NULL
    END,
    fr.sla_resume_breached_at = CASE
        WHEN fr.sla_stage IN ('CLAIM', 'RESPONSE', 'REWORK') THEN fr.sla_breached_at
        ELSE NULL
    END,
    fr.sla_stage = 'ASSIGNMENT_ACCEPTANCE',
    fr.sla_due_at = DATE_ADD(assignment.assigned_at, INTERVAL policy.assignment_acceptance_minutes MINUTE),
    fr.sla_paused_at = NULL,
    fr.sla_breached_at = NULL
WHERE assignment.state = 'PENDING_ACCEPTANCE';

--rollback UPDATE fault_reports SET sla_due_at = CASE WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' AND sla_resume_stage IS NOT NULL THEN sla_resume_due_at ELSE sla_due_at END, sla_breached_at = CASE WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' AND sla_resume_stage IS NOT NULL THEN sla_resume_breached_at ELSE sla_breached_at END, sla_stage = CASE WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' AND sla_resume_stage IS NOT NULL THEN CASE WHEN sla_resume_stage = 'REWORK' THEN 'RESPONSE' ELSE sla_resume_stage END WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' THEN CASE WHEN status_code = 'SUBMITTED' THEN 'CLAIM' ELSE 'RESPONSE' END WHEN sla_stage = 'REWORK' THEN 'RESPONSE' ELSE sla_stage END WHERE sla_stage IN ('ASSIGNMENT_ACCEPTANCE', 'REWORK');
--rollback ALTER TABLE fault_reports DROP COLUMN sla_resume_breached_at;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_resume_due_at;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_resume_stage;
--rollback ALTER TABLE work_order_sla_policies DROP COLUMN rework_minutes;
--rollback ALTER TABLE work_order_sla_policies DROP COLUMN assignment_acceptance_minutes;

--changeset e-report:2026-08-08-015-detailed-work-order-sla-h2 dbms:h2
ALTER TABLE work_order_sla_policies ADD COLUMN assignment_acceptance_minutes INT;
ALTER TABLE work_order_sla_policies ADD COLUMN rework_minutes INT;
UPDATE work_order_sla_policies
SET assignment_acceptance_minutes = claim_minutes,
    rework_minutes = response_minutes;
ALTER TABLE work_order_sla_policies ALTER COLUMN assignment_acceptance_minutes SET NOT NULL;
ALTER TABLE work_order_sla_policies ALTER COLUMN rework_minutes SET NOT NULL;

ALTER TABLE fault_reports ADD COLUMN sla_resume_stage VARCHAR(30);
ALTER TABLE fault_reports ADD COLUMN sla_resume_due_at TIMESTAMP(6);
ALTER TABLE fault_reports ADD COLUMN sla_resume_breached_at TIMESTAMP(6);

UPDATE fault_reports
SET sla_stage = 'REWORK'
WHERE rework_count > 0
  AND sla_stage = 'RESPONSE'
  AND status_code IN ('IN_PROGRESS', 'ON_HOLD');

UPDATE fault_reports
SET sla_resume_stage = CASE
        WHEN sla_stage IN ('CLAIM', 'RESPONSE', 'REWORK') THEN sla_stage
        ELSE NULL
    END,
    sla_resume_due_at = CASE
        WHEN sla_stage IN ('CLAIM', 'RESPONSE', 'REWORK') THEN sla_due_at
        ELSE NULL
    END,
    sla_resume_breached_at = CASE
        WHEN sla_stage IN ('CLAIM', 'RESPONSE', 'REWORK') THEN sla_breached_at
        ELSE NULL
    END,
    sla_stage = 'ASSIGNMENT_ACCEPTANCE',
    sla_due_at = (
        SELECT DATEADD('MINUTE', policy.assignment_acceptance_minutes, assignment.assigned_at)
        FROM work_order_assignments assignment
        JOIN work_order_sla_policies policy
          ON policy.tenant_id = fault_reports.tenant_id
         AND policy.report_type = fault_reports.report_type
         AND policy.priority = fault_reports.priority
        WHERE assignment.id = fault_reports.pending_assignment_id
          AND assignment.state = 'PENDING_ACCEPTANCE'
    ),
    sla_paused_at = NULL,
    sla_breached_at = NULL
WHERE pending_assignment_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM work_order_assignments assignment
      WHERE assignment.id = fault_reports.pending_assignment_id
        AND assignment.state = 'PENDING_ACCEPTANCE'
  );

--rollback UPDATE fault_reports SET sla_due_at = CASE WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' AND sla_resume_stage IS NOT NULL THEN sla_resume_due_at ELSE sla_due_at END, sla_breached_at = CASE WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' AND sla_resume_stage IS NOT NULL THEN sla_resume_breached_at ELSE sla_breached_at END, sla_stage = CASE WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' AND sla_resume_stage IS NOT NULL THEN CASE WHEN sla_resume_stage = 'REWORK' THEN 'RESPONSE' ELSE sla_resume_stage END WHEN sla_stage = 'ASSIGNMENT_ACCEPTANCE' THEN CASE WHEN status_code = 'SUBMITTED' THEN 'CLAIM' ELSE 'RESPONSE' END WHEN sla_stage = 'REWORK' THEN 'RESPONSE' ELSE sla_stage END WHERE sla_stage IN ('ASSIGNMENT_ACCEPTANCE', 'REWORK');
--rollback ALTER TABLE fault_reports DROP COLUMN sla_resume_breached_at;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_resume_due_at;
--rollback ALTER TABLE fault_reports DROP COLUMN sla_resume_stage;
--rollback ALTER TABLE work_order_sla_policies DROP COLUMN rework_minutes;
--rollback ALTER TABLE work_order_sla_policies DROP COLUMN assignment_acceptance_minutes;
