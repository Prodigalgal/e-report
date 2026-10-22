--liquibase formatted sql

--changeset e-report:2026-08-06-003-legacy-assignments dbms:mysql,h2
INSERT INTO work_order_assignments (
    tenant_id,
    work_order_id,
    assignee_membership_id,
    responsible_org_id,
    assigned_by_membership_id,
    assignment_type,
    state,
    active_slot,
    reason,
    assigned_at,
    accepted_at,
    ended_at
)
SELECT
    f.tenant_id,
    f.id,
    COALESCE(f.resolved_by_user_id, f.reported_by_user_id),
    f.responsible_org_id,
    COALESCE(f.resolved_by_user_id, f.reported_by_user_id),
    'CLAIM',
    'COMPLETED',
    NULL,
    '历史三状态工单迁移生成',
    f.report_time,
    f.report_time,
    COALESCE(f.resolution_time, f.last_updated_time)
FROM fault_reports f
WHERE f.status_code IN ('PENDING_VERIFICATION', 'CLOSED')
  AND COALESCE(f.resolved_by_user_id, f.reported_by_user_id) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM work_order_assignments a
      WHERE a.tenant_id = f.tenant_id AND a.work_order_id = f.id
  );

--rollback DELETE FROM work_order_assignments WHERE reason = '历史三状态工单迁移生成';

--changeset e-report:2026-08-06-003-legacy-responses dbms:mysql,h2
INSERT INTO work_order_responses (
    tenant_id,
    work_order_id,
    assignment_id,
    attempt_no,
    submitted_by_membership_id,
    resolution_details,
    resolution_method,
    replacement_completed,
    replacement_result,
    submitted_at
)
SELECT
    f.tenant_id,
    f.id,
    a.id,
    1,
    a.assignee_membership_id,
    COALESCE(f.resolution_details, '历史回单详情未记录'),
    COALESCE(f.resolution_method, '历史回单方式未记录'),
    f.replacement_completed,
    f.replacement_result,
    COALESCE(f.resolution_time, f.last_updated_time)
FROM fault_reports f
JOIN work_order_assignments a
  ON a.tenant_id = f.tenant_id AND a.work_order_id = f.id AND a.state = 'COMPLETED'
WHERE f.status_code IN ('PENDING_VERIFICATION', 'CLOSED')
  AND NOT EXISTS (
      SELECT 1 FROM work_order_responses r
      WHERE r.tenant_id = f.tenant_id AND r.work_order_id = f.id
  );

UPDATE fault_reports f
SET current_response_id = (
    SELECT MIN(r.id) FROM work_order_responses r
    WHERE r.tenant_id = f.tenant_id AND r.work_order_id = f.id
)
WHERE f.status_code IN ('PENDING_VERIFICATION', 'CLOSED')
  AND f.current_response_id IS NULL;

UPDATE attachments a
SET response_id = (
    SELECT f.current_response_id FROM fault_reports f
    WHERE f.tenant_id = a.tenant_id AND f.id = a.fault_report_id
)
WHERE a.attachment_stage = 'RESPONSE' AND a.response_id IS NULL;

UPDATE work_order_material_usages m
SET response_id = (
    SELECT f.current_response_id FROM fault_reports f
    WHERE f.tenant_id = m.tenant_id AND f.id = m.fault_report_id
)
WHERE m.response_id IS NULL;

--rollback UPDATE work_order_material_usages SET response_id = NULL WHERE EXISTS (SELECT 1 FROM work_order_responses r JOIN work_order_assignments a ON a.id = r.assignment_id WHERE r.id = work_order_material_usages.response_id AND a.reason = '历史三状态工单迁移生成');
--rollback UPDATE attachments SET response_id = NULL WHERE EXISTS (SELECT 1 FROM work_order_responses r JOIN work_order_assignments a ON a.id = r.assignment_id WHERE r.id = attachments.response_id AND a.reason = '历史三状态工单迁移生成');
--rollback UPDATE fault_reports SET current_response_id = NULL WHERE EXISTS (SELECT 1 FROM work_order_responses r JOIN work_order_assignments a ON a.id = r.assignment_id WHERE r.id = fault_reports.current_response_id AND a.reason = '历史三状态工单迁移生成');
--rollback DELETE FROM work_order_responses WHERE EXISTS (SELECT 1 FROM work_order_assignments a WHERE a.id = work_order_responses.assignment_id AND a.reason = '历史三状态工单迁移生成');

--changeset e-report:2026-08-06-003-legacy-verifications dbms:mysql,h2
INSERT INTO work_order_verifications (
    tenant_id,
    work_order_id,
    response_id,
    verified_by_membership_id,
    decision,
    comment,
    verification_override,
    override_reason,
    verified_at
)
SELECT
    f.tenant_id,
    f.id,
    f.current_response_id,
    COALESCE(f.closed_by_user_id, f.resolved_by_user_id, f.reported_by_user_id),
    'APPROVED',
    f.closure_reason,
    FALSE,
    NULL,
    COALESCE(f.closure_time, f.last_updated_time)
FROM fault_reports f
WHERE f.status_code = 'CLOSED'
  AND f.current_response_id IS NOT NULL
  AND COALESCE(f.closed_by_user_id, f.resolved_by_user_id, f.reported_by_user_id) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM work_order_verifications v
      WHERE v.tenant_id = f.tenant_id AND v.response_id = f.current_response_id
  );

--rollback DELETE FROM work_order_verifications WHERE EXISTS (SELECT 1 FROM work_order_responses r JOIN work_order_assignments a ON a.id = r.assignment_id WHERE r.id = work_order_verifications.response_id AND a.reason = '历史三状态工单迁移生成');

--changeset e-report:2026-08-06-003-migration-events dbms:mysql,h2
INSERT INTO work_order_events (
    tenant_id,
    work_order_id,
    event_type,
    previous_status_code,
    next_status_code,
    operator_membership_id,
    operator_name_snapshot,
    source,
    reason,
    occurred_at
)
SELECT
    f.tenant_id,
    f.id,
    'MIGRATED',
    NULL,
    f.status_code,
    COALESCE(f.closed_by_user_id, f.resolved_by_user_id, f.reported_by_user_id),
    'SYSTEM',
    'SYSTEM',
    '从历史三状态模型迁移',
    CURRENT_TIMESTAMP
FROM fault_reports f
WHERE NOT EXISTS (
    SELECT 1 FROM work_order_events e
    WHERE e.tenant_id = f.tenant_id AND e.work_order_id = f.id AND e.event_type = 'MIGRATED'
);

--rollback DELETE FROM work_order_events WHERE event_type = 'MIGRATED' AND reason = '从历史三状态模型迁移';
