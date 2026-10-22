--liquibase formatted sql

--changeset e-report:2026-08-10-019-remove-production-test-data dbms:mysql runInTransaction:true
--comment Remove historical write-E2E data from the reserved e2e/appqa/video-smoke namespaces after production backup.
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users WHERE (username LIKE 'e2e-%' OR username LIKE 'appqa-%' OR username LIKE 'video-smoke-%') AND status <> 'DELETED'
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM fault_reports f JOIN users reporter ON reporter.id = f.reported_by_user_id WHERE (reporter.username LIKE 'e2e-%' OR reporter.username LIKE 'appqa-%' OR reporter.username LIKE 'video-smoke-%') AND f.status_code NOT IN ('CLOSED', 'CANCELLED')
--precondition-sql-check expectedResult:0 SELECT COUNT(DISTINCT f.id) FROM fault_reports f WHERE NOT EXISTS (SELECT 1 FROM users reporter WHERE reporter.id = f.reported_by_user_id AND (reporter.username LIKE 'e2e-%' OR reporter.username LIKE 'appqa-%' OR reporter.username LIKE 'video-smoke-%')) AND (EXISTS (SELECT 1 FROM users participant WHERE participant.id IN (f.resolved_by_user_id, f.closed_by_user_id) AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_assignments a JOIN users participant ON participant.id IN (a.assignee_membership_id, a.assigned_by_membership_id) WHERE a.work_order_id = f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_responses r JOIN users participant ON participant.id = r.submitted_by_membership_id WHERE r.work_order_id = f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_verifications v JOIN users participant ON participant.id = v.verified_by_membership_id WHERE v.work_order_id = f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')) OR EXISTS (SELECT 1 FROM work_order_events e JOIN users participant ON participant.id = e.operator_membership_id WHERE e.work_order_id = f.id AND (participant.username LIKE 'e2e-%' OR participant.username LIKE 'appqa-%' OR participant.username LIKE 'video-smoke-%')))

CREATE TEMPORARY TABLE tmp_v1039_test_users (
    id BINARY(16) NOT NULL,
    username VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmp_v1039_test_username (username)
) ENGINE=MEMORY;

INSERT INTO tmp_v1039_test_users (id, username)
SELECT id, username
FROM users
WHERE username LIKE 'e2e-%'
   OR username LIKE 'appqa-%'
   OR username LIKE 'video-smoke-%';

CREATE TEMPORARY TABLE tmp_v1039_test_orders (
    id BINARY(16) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=MEMORY;

INSERT INTO tmp_v1039_test_orders (id)
SELECT f.id
FROM fault_reports f
JOIN tmp_v1039_test_users reporter ON reporter.id = f.reported_by_user_id;

CREATE TEMPORARY TABLE tmp_v1039_test_role_bundles (
    id BINARY(16) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=MEMORY;

INSERT INTO tmp_v1039_test_role_bundles (id)
SELECT id
FROM role_bundles
WHERE built_in = 0
  AND display_name LIKE 'E2E-%';

DELETE pending
FROM wecom_pending_materials pending
JOIN tmp_v1039_test_orders test_order ON test_order.id = pending.work_order_id;

DELETE pending
FROM wecom_pending_materials pending
JOIN external_command_records command_record ON command_record.id = pending.command_record_id
JOIN tmp_v1039_test_orders test_order ON test_order.id = command_record.work_order_id;

DELETE command_record
FROM external_command_records command_record
JOIN tmp_v1039_test_orders test_order ON test_order.id = command_record.work_order_id;

UPDATE fault_reports report
JOIN tmp_v1039_test_orders test_order ON test_order.id = report.id
SET report.current_assignment_id = NULL,
    report.pending_assignment_id = NULL,
    report.current_response_id = NULL;

DELETE target_event
FROM work_order_events target_event
JOIN tmp_v1039_test_orders test_order ON test_order.id = target_event.work_order_id;

DELETE outbox
FROM outbox_events outbox
JOIN tmp_v1039_test_orders test_order ON test_order.id = outbox.aggregate_id
WHERE outbox.aggregate_type = 'WORK_ORDER';

DELETE attachment
FROM attachments attachment
JOIN tmp_v1039_test_orders test_order ON test_order.id = attachment.fault_report_id;

DELETE material
FROM work_order_material_usages material
JOIN tmp_v1039_test_orders test_order ON test_order.id = material.fault_report_id;

DELETE verification
FROM work_order_verifications verification
JOIN tmp_v1039_test_orders test_order ON test_order.id = verification.work_order_id;

DELETE response
FROM work_order_responses response
JOIN tmp_v1039_test_orders test_order ON test_order.id = response.work_order_id;

DELETE assignment
FROM work_order_assignments assignment
JOIN tmp_v1039_test_orders test_order ON test_order.id = assignment.work_order_id;

DELETE report
FROM fault_reports report
JOIN tmp_v1039_test_orders test_order ON test_order.id = report.id;

DELETE observation
FROM client_observation_events observation
JOIN tmp_v1039_test_users test_user ON test_user.id = observation.user_id;

DELETE idempotency
FROM command_idempotency_records idempotency
JOIN tmp_v1039_test_users test_user ON test_user.id = idempotency.membership_id;

DELETE binding
FROM external_user_bindings binding
JOIN tmp_v1039_test_users test_user ON test_user.id = binding.local_user_id;

DELETE upload_session
FROM file_transfer_upload_sessions upload_session
JOIN tmp_v1039_test_users test_user
  ON test_user.id = upload_session.created_by_membership_id
  OR test_user.username = upload_session.created_by_username;

DELETE role_grant
FROM membership_role_grants role_grant
JOIN tmp_v1039_test_users test_user ON test_user.id = role_grant.membership_id;

DELETE user_role
FROM user_roles user_role
JOIN tmp_v1039_test_users test_user ON test_user.id = user_role.user_id;

DELETE bundle_role
FROM role_bundle_roles bundle_role
JOIN tmp_v1039_test_role_bundles test_bundle ON test_bundle.id = bundle_role.role_bundle_id;

DELETE bundle
FROM role_bundles bundle
JOIN tmp_v1039_test_role_bundles test_bundle ON test_bundle.id = bundle.id;

DELETE target_user
FROM users target_user
JOIN tmp_v1039_test_users test_user ON test_user.id = target_user.id;

DROP TEMPORARY TABLE tmp_v1039_test_role_bundles;
DROP TEMPORARY TABLE tmp_v1039_test_orders;
DROP TEMPORARY TABLE tmp_v1039_test_users;
