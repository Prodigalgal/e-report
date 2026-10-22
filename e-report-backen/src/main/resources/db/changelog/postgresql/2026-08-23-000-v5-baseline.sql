--liquibase formatted sql

--changeset e-report:2026-08-23-000-v5-postgresql-baseline dbms:postgresql
--comment PostgreSQL 5.0.9 canonical schema for atomic Grants, organization data boundaries, departments and work-order transfer.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
create table api_token_permissions (token_id uuid not null, permission_name varchar(100) not null, primary key (token_id, permission_name));
create table api_tokens (created_at timestamp(6) not null, expires_at timestamp(6), last_used_at timestamp(6), revoked_at timestamp(6), id uuid not null, membership_id uuid not null, tenant_id uuid not null, lookup_key varchar(22) not null unique, secret_hash varchar(64) not null unique, name varchar(80) not null, revoke_reason varchar(255), primary key (id));
create table app_config (last_updated_time timestamp(6) not null, id uuid not null, config_key varchar(100) not null unique, config_value text, description varchar(255), primary key (id));
create table app_releases (force_update boolean not null, min_supported_version_code integer not null, published boolean not null, version_code integer not null, created_at timestamp(6) not null, file_size bigint not null, published_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, platform varchar(20) not null check ((platform in ('ANDROID'))), channel varchar(50) not null, version_name varchar(50) not null, sha256 varchar(64) not null, content_type varchar(100) not null, runtime_version varchar(100) not null, original_file_name varchar(255) not null, release_notes TEXT, stored_file_name varchar(255) not null, primary key (id), constraint uk_app_releases_version unique (platform, channel, version_code));
create table attachments (file_size bigint not null, fault_report_id uuid not null, id uuid not null, response_id uuid, tenant_id uuid not null, attachment_stage varchar(20) check ((attachment_stage in ('REPORT','RESPONSE'))), file_name varchar(255) not null, file_path varchar(255) not null, file_type varchar(255) not null, stored_file_name varchar(255) not null, primary key (id));
create table auth_sessions (password_change_required boolean not null, persistent_login boolean not null, absolute_expires_at timestamp(6) not null, authorization_version bigint not null, created_at timestamp(6) not null, idle_expires_at timestamp(6) not null, last_seen_at timestamp(6) not null, refresh_rotated_at timestamp(6), revoked_at timestamp(6), id uuid not null, membership_id uuid not null, tenant_id uuid not null, trusted_device_id uuid, client_type varchar(20) not null check ((client_type in ('WEB','ANDROID'))), status varchar(20) not null check ((status in ('ACTIVE','REVOKED','EXPIRED','COMPROMISED'))), client_app_version varchar(50), ip_hash varchar(64) not null, previous_refresh_token_hash varchar(64), refresh_token_hash varchar(64) unique, user_agent_hash varchar(64) not null, web_fingerprint_hash varchar(64), web_secret_hash varchar(64) unique, client_device_name varchar(120), client_name varchar(120), client_os_version varchar(120), client_security_signals varchar(1000), revoke_reason varchar(255), web_environment_json TEXT, primary key (id));
create table authorization_change_plans (applied_at timestamp(6), created_at timestamp(6) not null, expected_authorization_version bigint not null, expires_at timestamp(6) not null, id uuid not null, operator_membership_id uuid not null, target_membership_id uuid not null, tenant_id uuid not null, status varchar(20) not null check ((status in ('READY','APPLIED','EXPIRED','REJECTED'))), request_digest varchar(64) not null, idempotency_key varchar(120), preview_json text not null, request_json text not null, primary key (id));
create table authorization_profile_permission_rules (sort_order integer not null, id uuid not null, permission_id uuid not null, profile_version_id uuid not null, tenant_id uuid not null, primary key (id), constraint uk_authorization_profile_rules_permission unique (profile_version_id, permission_id));
create table authorization_profile_versions (version_no integer not null, created_at timestamp(6) not null, published_at timestamp(6), created_by_membership_id uuid, id uuid not null, profile_id uuid not null, published_by_membership_id uuid, tenant_id uuid not null, status varchar(20) not null check ((status in ('DRAFT','PUBLISHED','RETIRED'))), change_summary varchar(500), primary key (id), constraint uk_authorization_profile_versions_number unique (profile_id, version_no));
create table authorization_profiles (active boolean not null, built_in boolean not null, sort_order integer not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, current_version_id uuid, id uuid not null, tenant_id uuid not null, code varchar(80) not null, system_key varchar(80), display_name varchar(100) not null, description varchar(500), primary key (id), constraint uk_authorization_profiles_tenant_code unique (tenant_id, code), constraint uk_authorization_profiles_tenant_system unique (tenant_id, system_key));
create table client_observation_events (client_occurred_at timestamp(6), received_at timestamp(6) not null, severity varchar(10) not null check ((severity in ('INFO','WARN','ERROR','FATAL'))), id uuid not null, org_unit_id uuid, platform varchar(16) not null check ((platform in ('WEB','ANDROID','SERVER'))), tenant_id uuid not null, user_id uuid, event_type varchar(20) not null check ((event_type in ('ERROR','WARNING','OPERATION','DIAGNOSTIC'))), outcome varchar(20) check ((outcome in ('SUCCESS','FAILURE','UNKNOWN'))), os_name varchar(30), category varchar(40) not null, app_build varchar(50), app_version varchar(50), network_type varchar(50), os_version varchar(50), client_event_id varchar(64) not null, installation_id varchar(64), session_id varchar(64), device_model varchar(100), event_name varchar(100) not null, manufacturer varchar(100), trace_id varchar(100), operation_name varchar(120), user_agent varchar(500), message varchar(2000), breadcrumbs_json TEXT, context_json TEXT, route varchar(255), stack_trace TEXT, primary key (id), constraint uk_client_observation_event_id unique (client_event_id));
create table command_idempotency_records (completed_at timestamp(6), created_at timestamp(6) not null, expires_at timestamp(6) not null, id uuid not null, membership_id uuid not null, tenant_id uuid not null, state varchar(20) not null, request_hash varchar(64) not null, idempotency_key varchar(120) not null, endpoint varchar(255) not null, response_body TEXT, primary key (id), constraint uk_command_idempotency_scope unique (tenant_id, membership_id, endpoint, idempotency_key));
create table daily_work_order_report_dispatches (failure_count integer not null, recipient_count integer not null, report_date date not null, success_count integer not null, work_order_count integer not null, completed_at timestamp(6), started_at timestamp(6) not null, id uuid not null, org_unit_id uuid not null, tenant_id uuid not null, status varchar(30) not null check ((status in ('PROCESSING','SENT','PARTIAL_FAILED','FAILED','SKIPPED_EMPTY','SKIPPED_NO_RECIPIENT'))), error_summary varchar(1000), primary key (id), constraint uk_daily_report_dispatch_scope_date unique (tenant_id, org_unit_id, report_date));
create table department_change_events (affected_users bigint not null, affected_work_orders bigint not null, created_at timestamp(6) not null, department_id uuid not null, id uuid not null, new_parent_id uuid, old_parent_id uuid, operator_membership_id uuid, org_unit_id uuid not null, target_department_id uuid, tenant_id uuid not null, operation varchar(20) not null check ((operation in ('MOVE','ENABLE','DISABLE','MERGE','DELETE'))), reason varchar(1000) not null, primary key (id));
create table departments (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, merged_into_department_id uuid, org_unit_id uuid not null, parent_id uuid, tenant_id uuid not null, status varchar(20) not null check ((status in ('ACTIVE','SUSPENDED','MERGED'))), code varchar(50) not null, name varchar(100) not null, path varchar(700) not null, primary key (id), constraint uk_departments_tenant_org_code unique (tenant_id, org_unit_id, code), constraint uk_departments_tenant_id unique (tenant_id, id));
create table fault_reports (box_quantity integer, replacement_completed boolean, replacement_required boolean, rework_count integer not null, sla_ever_breached boolean not null, accepted_at timestamp(6), cancelled_at timestamp(6), closed_at timestamp(6), closure_time timestamp(6), last_updated_time timestamp(6) not null, report_time timestamp(6) not null, resolution_time timestamp(6), sla_breached_at timestamp(6), sla_due_at timestamp(6), sla_paused_at timestamp(6), sla_resume_breached_at timestamp(6), sla_resume_due_at timestamp(6), submitted_at timestamp(6) not null, total_hold_seconds bigint not null, version bigint not null, closed_by_user_id uuid, current_assignment_id uuid, current_response_id uuid, id uuid not null, owner_org_id uuid not null, pending_assignment_id uuid, reported_by_user_id uuid not null, reporting_department_id uuid, reporting_org_id uuid, resolved_by_user_id uuid, responsible_department_id uuid, responsible_org_id uuid, tenant_id uuid not null, phone varchar(20) not null, priority varchar(20) not null check ((priority in ('URGENT','HIGH','ROUTINE','LOW'))), sla_resume_stage varchar(30) check ((sla_resume_stage in ('CLAIM','ASSIGNMENT_ACCEPTANCE','RESPONSE','VERIFICATION','REWORK','COMPLETED'))), sla_stage varchar(30) not null check ((sla_stage in ('CLAIM','ASSIGNMENT_ACCEPTANCE','RESPONSE','VERIFICATION','REWORK','COMPLETED'))), status_code varchar(40) not null, report_type varchar(50) check ((report_type in ('NETWORK_FAULT','BOX_DOOR_OPEN','DAMAGED_BOX_REPLACEMENT'))), status varchar(50), work_order_no varchar(64) not null, contact_person varchar(100) not null, gps_coordinates varchar(100), closure_reason text, location varchar(255) not null, reason text not null, replacement_result text, resolution_details text, resolution_method varchar(255), primary key (id), constraint uk_fault_reports_tenant_no unique (tenant_id, work_order_no));
create table file_transfer_upload_sessions (chunk_size bigint not null, created_at timestamp(6) not null, expires_at timestamp(6) not null, received_bytes bigint not null, total_size bigint not null, updated_at timestamp(6) not null, created_by_membership_id uuid, id uuid not null, tenant_id uuid not null, status varchar(20) not null check ((status in ('INITIATED','COMPLETED','PROCESSING','CLAIMED','ABORTED'))), purpose varchar(40) not null check ((purpose in ('WORK_ORDER_ATTACHMENT','APP_RELEASE','USER_IMPORT','USER_IMPORT_TEMPLATE','USER_IMPORT_ERROR','USER_EXPORT'))), actual_sha256 varchar(64), expected_sha256 varchar(64), content_type varchar(100) not null, created_by_username varchar(100) not null, original_file_name varchar(255) not null, stored_file_name varchar(255), temp_file_name varchar(255) not null, primary key (id));
create table org_app_config (last_updated_time timestamp(6) not null, id uuid not null, org_unit_id uuid not null, tenant_id uuid not null, config_key varchar(100) not null, config_value TEXT, description varchar(255), primary key (id), constraint uk_org_app_config_scope_key unique (tenant_id, org_unit_id, config_key));
create table org_units (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, parent_id uuid, tenant_id uuid not null, status varchar(20) not null check ((status in ('ACTIVE','SUSPENDED'))), unit_type varchar(30) not null check ((unit_type in ('CITY','COUNTY'))), code varchar(50) not null, name varchar(100) not null, path varchar(500) not null, primary key (id), constraint uk_org_units_tenant_code unique (tenant_id, code), constraint uk_org_units_tenant_id unique (tenant_id, id));
create table organization_move_events (created_at timestamp(6) not null, id uuid not null, new_parent_id uuid not null, old_parent_id uuid not null, operator_membership_id uuid, org_unit_id uuid not null, tenant_id uuid not null, new_path varchar(500) not null, old_path varchar(500) not null, reason varchar(1000) not null, primary key (id));
create table outbox_events (attempts integer not null, available_at timestamp(6) not null, created_at timestamp(6) not null, locked_at timestamp(6), processed_at timestamp(6), aggregate_id uuid not null, id uuid not null, tenant_id uuid not null, state varchar(20) not null, aggregate_type varchar(50) not null, event_type varchar(80) not null, last_error varchar(1000), payload TEXT not null, primary key (id));
create table permission_grants (customized boolean not null, grant_option boolean not null, expires_at timestamp(6), granted_at timestamp(6) not null, revoked_at timestamp(6), status_changed_at timestamp(6) not null, valid_from timestamp(6), granted_by_membership_id uuid, id uuid not null, membership_id uuid not null, parent_grant_id uuid, permission_id uuid not null, replaced_by_grant_id uuid, revoked_by_membership_id uuid, tenant_id uuid not null, status varchar(20) not null check ((status in ('ACTIVE','SUSPENDED_UPSTREAM','EXPIRED','REVOKED','REPLACED'))), issuer_type varchar(30) not null check ((issuer_type in ('SYSTEM_ROOT','SYSTEM_SELF_BASELINE','SYSTEM_BASELINE','USER','MIGRATION'))), reason varchar(1000), revoke_reason varchar(1000), primary key (id));
create table permissions (id uuid not null, display_name varchar(100), name varchar(100) not null unique, description varchar(255), primary key (id));
create table tenant_app_config (last_updated_time timestamp(6) not null, id uuid not null, tenant_id uuid not null, config_key varchar(100) not null, config_value TEXT, description varchar(255), primary key (id), constraint uk_tenant_app_config_key unique (tenant_id, config_key));
create table tenant_groups (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, status varchar(20) not null check ((status in ('ACTIVE','SUSPENDED'))), code varchar(50) not null unique, name varchar(100) not null, primary key (id));
create table tenants (created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, root_org_unit_id uuid, tenant_group_id uuid, status varchar(20) not null check ((status in ('ACTIVE','SUSPENDED'))), system_key varchar(40) unique check ((system_key in ('DEFAULT_TENANT'))), code varchar(50) not null unique, name varchar(100) not null, primary key (id));
create table trusted_devices (cpu_core_count integer, environment_schema_version integer, hardware_backed boolean not null, low_ram_device boolean, memory_mb_bucket integer, screen_density integer, screen_height integer, screen_width integer, sdk_int integer, storage_mb_bucket integer, first_seen_at timestamp(6) not null, last_seen_at timestamp(6) not null, revoked_at timestamp(6), id uuid not null, membership_id uuid not null, tenant_id uuid not null, app_build varchar(20), security_patch varchar(20), app_version varchar(30), status varchar(32) not null check ((status in ('TRUSTED','REAUTHENTICATION_REQUIRED','REVOKED'))), locale_tag varchar(50), os_release varchar(50), app_signature_sha256 varchar(64), build_fingerprint_hash varchar(64), environment_fingerprint_hash varchar(64), installation_id_hash varchar(64) not null, key_id varchar(64) not null, brand varchar(80), manufacturer varchar(80), soc_manufacturer varchar(80), time_zone varchar(80), board_name varchar(120), device_name varchar(120), hardware_name varchar(120), installer_package varchar(120), model varchar(120), product_name varchar(120), soc_model varchar(120), public_key_spki varchar(1024) not null, revoke_reason varchar(255), security_signals TEXT, supported_32_bit_abis varchar(255), supported_64_bit_abis varchar(255), supported_abis varchar(255), primary key (id));
create table user_transfer_job_rows (source_row_number integer not null, after_version bigint, before_version bigint, created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, job_id uuid not null, tenant_id uuid not null, execution_status varchar(20) not null check ((execution_status in ('PENDING','SUCCEEDED','FAILED','SKIPPED'))), validation_status varchar(20) not null check ((validation_status in ('VALID','INVALID'))), command_type varchar(30), subject_display varchar(32), sheet_name varchar(40) not null, subject_key_hash varchar(64), execution_message varchar(1000), field_diff_json varchar(65535), grant_diff_json varchar(65535), validation_errors_json varchar(65535), primary key (id), constraint uk_user_transfer_job_rows_position unique (job_id, sheet_name, source_row_number));
create table user_transfer_jobs (failed_rows integer not null, invalid_rows integer not null, processed_rows integer not null, succeeded_rows integer not null, total_rows integer not null, valid_rows integer not null, completed_at timestamp(6), created_at timestamp(6) not null, expires_at timestamp(6) not null, preview_expires_at timestamp(6), started_at timestamp(6), updated_at timestamp(6) not null, version bigint not null, created_by_membership_id uuid not null, error_file_id uuid, id uuid not null, input_file_id uuid, result_file_id uuid, tenant_id uuid not null, job_type varchar(20) not null check ((job_type in ('IMPORT','EXPORT'))), status varchar(30) not null check ((status in ('QUEUED','UPLOADED','VALIDATING','PREVIEW_READY','STALE','EXECUTING','COMPLETED','FAILED','CANCELLED'))), template_version varchar(30), catalog_digest varchar(64), input_sha256 varchar(64), preview_snapshot_digest varchar(64), request_hash varchar(64), user_snapshot_digest varchar(64), created_by_username varchar(100) not null, idempotency_key varchar(100), failure_message varchar(1000), summary_json varchar(65535), primary key (id), constraint uk_user_transfer_jobs_idempotency unique (tenant_id, created_by_membership_id, idempotency_key));
create table users (password_change_required boolean not null, authorization_version bigint not null, created_at timestamp(6) not null, password_changed_at timestamp(6), updated_at timestamp(6) not null, department_id uuid, id uuid not null, org_unit_id uuid, tenant_id uuid not null, phone varchar(20), status varchar(20) not null check ((status in ('ACTIVE','INACTIVE','DISABLED','LOCKED','DELETED'))), system_key varchar(40) unique check ((system_key in ('SUPER_ADMINISTRATOR'))), username varchar(50) not null, name varchar(100), name_pinyin_initials varchar(160), email varchar(254), name_pinyin varchar(640), password varchar(255) not null, primary key (id), constraint uk_users_phone unique (phone));
create table work_order_assignments (accepted_at timestamp(6), assigned_at timestamp(6) not null, ended_at timestamp(6), assigned_by_membership_id uuid not null, assignee_membership_id uuid not null, id uuid not null, responsible_department_id uuid, responsible_org_id uuid not null, tenant_id uuid not null, work_order_id uuid not null, active_slot varchar(20) check ((active_slot in ('CURRENT','PENDING'))), assignment_type varchar(20) not null check ((assignment_type in ('CLAIM','ASSIGN','REASSIGN','REWORK'))), state varchar(30) not null check ((state in ('PENDING_ACCEPTANCE','ACCEPTED','COMPLETED','REJECTED','REVOKED','CANCELLED'))), reason TEXT, rejection_reason TEXT, primary key (id), constraint uk_work_order_assignment_slot unique (tenant_id, work_order_id, active_slot));
create table work_order_department_participations (active boolean not null, ended_at timestamp(6), started_at timestamp(6) not null, created_by_membership_id uuid, department_id uuid, id uuid not null, tenant_id uuid not null, work_order_id uuid not null, relation_type varchar(20) not null check ((relation_type in ('REPORTING','RESPONSIBLE','VERIFICATION','COPIED'))), department_name_snapshot varchar(100) not null, reason varchar(1000), primary key (id));
create table work_order_events (occurred_at timestamp(6) not null, assignment_id uuid, id uuid not null, operator_membership_id uuid, response_id uuid, tenant_id uuid not null, verification_id uuid, work_order_id uuid not null, source varchar(30) not null check ((source in ('WEB','ANDROID','SYSTEM','PLATFORM_OVERRIDE'))), event_type varchar(40) not null check ((event_type in ('SUBMITTED','CLAIMED','ASSIGNED','ASSIGNMENT_ACCEPTED','ASSIGNMENT_REJECTED','REASSIGNED','RESPONSE_SUBMITTED','VERIFICATION_APPROVED','VERIFICATION_REJECTED','HELD','RESUMED','CANCELLED','ORGANIZATION_TRANSFER_REQUESTED','ORGANIZATION_TRANSFER_ACCEPTED','ORGANIZATION_TRANSFER_REJECTED','ORGANIZATION_TRANSFER_CANCELLED','SLA_BREACHED','MIGRATED'))), next_status_code varchar(40), previous_status_code varchar(40), operator_name_snapshot varchar(100), request_id varchar(100), metadata_json TEXT, reason TEXT, primary key (id));
create table work_order_material_usages (quantity integer not null, fault_report_id uuid not null, id uuid not null, response_id uuid, tenant_id uuid not null, category varchar(50) not null check ((category in ('OPTICAL_CABLE','JOINT_BOX','SPLITTER'))), specification varchar(50) not null, primary key (id));
create table work_order_organization_participations (active boolean not null, ended_at timestamp(6), started_at timestamp(6) not null, created_by_membership_id uuid, id uuid not null, org_unit_id uuid not null, tenant_id uuid not null, work_order_id uuid not null, relation_type varchar(32) not null check ((relation_type in ('REPORTING','OWNER','PREVIOUS_OWNER','PENDING_TRANSFER_TARGET'))), organization_name_snapshot varchar(100) not null, reason varchar(1000), primary key (id));
create table work_order_organization_transfers (decided_at timestamp(6), expected_work_order_version bigint not null, requested_at timestamp(6) not null, decided_by_membership_id uuid, id uuid not null, requested_by_membership_id uuid not null, source_org_unit_id uuid not null, target_org_unit_id uuid not null, tenant_id uuid not null, work_order_id uuid not null, status varchar(20) not null check ((status in ('PENDING','ACCEPTED','REJECTED','CANCELLED'))), decision_comment varchar(2000), request_reason varchar(2000) not null, primary key (id));
create table work_order_responses (attempt_no integer not null, replacement_completed boolean, submitted_at timestamp(6) not null, assignment_id uuid not null, id uuid not null, submitted_by_membership_id uuid not null, tenant_id uuid not null, work_order_id uuid not null, replacement_result TEXT, resolution_details TEXT not null, resolution_method varchar(255) not null, primary key (id), constraint uk_work_order_response_attempt unique (tenant_id, work_order_id, attempt_no));
create table work_order_sla_policies (assignment_acceptance_minutes integer not null, claim_minutes integer not null, max_single_hold_minutes integer not null, max_total_hold_minutes integer not null, pause_on_hold boolean not null, response_minutes integer not null, rework_minutes integer not null, verification_minutes integer not null, created_at timestamp(6) not null, updated_at timestamp(6) not null, id uuid not null, tenant_id uuid not null, priority varchar(20) not null check ((priority in ('URGENT','HIGH','ROUTINE','LOW'))), report_type varchar(50) not null check ((report_type in ('NETWORK_FAULT','BOX_DOOR_OPEN','DAMAGED_BOX_REPLACEMENT'))), primary key (id), constraint uk_sla_policy_tenant_type_priority unique (tenant_id, report_type, priority));
create table work_order_verifications (verification_override boolean not null, verified_at timestamp(6) not null, id uuid not null, response_id uuid not null, tenant_id uuid not null, verified_by_membership_id uuid not null, work_order_id uuid not null, decision varchar(20) not null check ((decision in ('APPROVED','REJECTED'))), comment TEXT, override_reason TEXT, primary key (id), constraint uk_work_order_verification_response unique (tenant_id, response_id));
create index idx_api_token_owner_created on api_tokens (tenant_id, membership_id, created_at);
create index idx_api_token_expiry on api_tokens (expires_at);
create index idx_app_releases_latest on app_releases (platform, channel, published, version_code);
create index idx_app_releases_channel_time on app_releases (platform, channel, published_at);
create index idx_attachments_fault_report_id on attachments (fault_report_id);
create index idx_attachments_fault_stored_name on attachments (fault_report_id, stored_file_name);
create index idx_auth_session_member_status on auth_sessions (tenant_id, membership_id, status);
create index idx_auth_session_expiry on auth_sessions (status, idle_expires_at, absolute_expires_at);
create index idx_authorization_plans_target on authorization_change_plans (tenant_id, target_membership_id, status, expires_at);
create index idx_authorization_plans_operator on authorization_change_plans (tenant_id, operator_membership_id, created_at);
create index idx_authorization_profile_rules_version on authorization_profile_permission_rules (tenant_id, profile_version_id, sort_order);
create index idx_authorization_profile_versions_status on authorization_profile_versions (tenant_id, profile_id, status);
create index idx_authorization_profiles_tenant_active on authorization_profiles (tenant_id, active, sort_order);
create index idx_observation_tenant_received on client_observation_events (tenant_id, received_at, id);
create index idx_observation_org_received on client_observation_events (tenant_id, org_unit_id, received_at, id);
create index idx_observation_severity_received on client_observation_events (tenant_id, severity, received_at);
create index idx_observation_category_received on client_observation_events (tenant_id, category, received_at);
create index idx_command_idempotency_expiry on command_idempotency_records (expires_at);
create index idx_daily_report_dispatch_status on daily_work_order_report_dispatches (tenant_id, status, report_date);
create index idx_department_events_department on department_change_events (tenant_id, department_id, created_at);
create index idx_department_events_org on department_change_events (tenant_id, org_unit_id, created_at);
create index idx_departments_tenant_org_path on departments (tenant_id, org_unit_id, path);
create index idx_departments_parent_status on departments (parent_id, status);
create index idx_fault_reports_type_status on fault_reports (report_type, status);
create index idx_fault_reports_type_status_time on fault_reports (report_type, status, report_time);
create index idx_fault_reports_type_time on fault_reports (report_type, report_time);
create index idx_fault_reports_reported_by_time on fault_reports (reported_by_user_id, report_time);
create index idx_fault_reports_status_time on fault_reports (status, report_time);
create index idx_fault_reports_report_time on fault_reports (report_time);
create index idx_fault_reports_tenant_type_status_time on fault_reports (tenant_id, report_type, status_code, submitted_at);
create index idx_fault_reports_tenant_reporting_status on fault_reports (tenant_id, reporting_org_id, status_code, submitted_at);
create index idx_fault_reports_tenant_responsible_status on fault_reports (tenant_id, responsible_org_id, status_code, submitted_at);
create index idx_fault_reports_tenant_reporter_time on fault_reports (tenant_id, reported_by_user_id, submitted_at);
create index idx_fault_reports_tenant_sla_due on fault_reports (tenant_id, sla_stage, sla_due_at);
create index idx_fault_reports_tenant_sla_breach on fault_reports (tenant_id, sla_ever_breached, status_code);
create index idx_file_transfer_user_status on file_transfer_upload_sessions (created_by_username, status);
create index idx_file_transfer_expires on file_transfer_upload_sessions (expires_at);
create index idx_org_app_config_scope on org_app_config (tenant_id, org_unit_id);
create index idx_org_units_tenant_path on org_units (tenant_id, path);
create index idx_organization_move_events_org on organization_move_events (tenant_id, org_unit_id, created_at);
create index idx_outbox_delivery on outbox_events (state, available_at, id);
create index idx_outbox_tenant_aggregate on outbox_events (tenant_id, aggregate_type, aggregate_id);
create index idx_permission_grants_member on permission_grants (tenant_id, membership_id, status, expires_at);
create index idx_permission_grants_permission on permission_grants (tenant_id, permission_id, status, expires_at);
create index idx_permission_grants_parent on permission_grants (parent_grant_id, status);
create index idx_tenants_group on tenants (tenant_group_id);
create index idx_trusted_device_key on trusted_devices (key_id);
create index idx_trusted_device_member_status on trusted_devices (tenant_id, membership_id, status);
create index idx_user_transfer_job_rows_page on user_transfer_job_rows (tenant_id, job_id, validation_status, source_row_number);
create index idx_user_transfer_jobs_owner on user_transfer_jobs (tenant_id, created_by_membership_id, created_at);
create index idx_user_transfer_jobs_status on user_transfer_jobs (status, updated_at);
create index idx_user_transfer_jobs_expires on user_transfer_jobs (expires_at);
create index idx_users_tenant_status on users (tenant_id, status);
create index idx_assignments_assignee_state on work_order_assignments (tenant_id, assignee_membership_id, state, assigned_at);
create index idx_work_order_dept_participation_order on work_order_department_participations (tenant_id, work_order_id, relation_type, active);
create index idx_work_order_dept_participation_department on work_order_department_participations (tenant_id, department_id, active);
create index idx_work_order_events_tenant_order_time on work_order_events (tenant_id, work_order_id, occurred_at);
create index idx_material_usage_fault_report_id on work_order_material_usages (fault_report_id);
create index idx_material_usage_category_spec on work_order_material_usages (category, specification);
create index idx_work_order_org_participation_order on work_order_organization_participations (tenant_id, work_order_id, relation_type, active);
create index idx_work_order_org_participation_org on work_order_organization_participations (tenant_id, org_unit_id, active);
create index idx_work_order_transfers_order_status on work_order_organization_transfers (tenant_id, work_order_id, status, requested_at);
create index idx_work_order_transfers_target_status on work_order_organization_transfers (tenant_id, target_org_unit_id, status, requested_at);
create index idx_sla_policy_tenant_type on work_order_sla_policies (tenant_id, report_type);

CREATE TABLE database_migration_runs (
    id uuid NOT NULL,
    status varchar(30) NOT NULL,
    source_database varchar(255) NOT NULL,
    target_database varchar(255) NOT NULL,
    started_at timestamp(6) without time zone NOT NULL,
    completed_at timestamp(6) without time zone,
    error_summary varchar(2000),
    CONSTRAINT pk_database_migration_runs PRIMARY KEY (id)
);
CREATE TABLE database_migration_table_results (
    id uuid NOT NULL,
    run_id uuid NOT NULL,
    table_name varchar(120) NOT NULL,
    phase varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    source_row_count bigint NOT NULL,
    target_row_count bigint NOT NULL,
    source_digest varchar(64) NOT NULL,
    target_digest varchar(64) NOT NULL,
    completed_at timestamp(6) without time zone NOT NULL,
    error_summary varchar(2000),
    CONSTRAINT pk_database_migration_table_results PRIMARY KEY (id),
    CONSTRAINT uk_database_migration_table_phase UNIQUE (run_id, table_name, phase),
    CONSTRAINT fk_database_migration_table_run FOREIGN KEY (run_id) REFERENCES database_migration_runs (id)
);
CREATE INDEX idx_database_migration_table_run ON database_migration_table_results (run_id, status, table_name);

CREATE TABLE legacy_migration_audit_records (
    id uuid NOT NULL,
    tenant_id uuid,
    source_table varchar(120) NOT NULL,
    source_key varchar(255) NOT NULL,
    payload_json jsonb NOT NULL,
    archived_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT pk_legacy_migration_audit_records PRIMARY KEY (id),
    CONSTRAINT uk_legacy_migration_audit_source UNIQUE (source_table, source_key)
);
CREATE INDEX idx_legacy_migration_audit_tenant ON legacy_migration_audit_records (tenant_id, source_table);
ALTER TABLE api_token_permissions ADD CONSTRAINT fk_api_token_permission_catalog FOREIGN KEY (permission_name) REFERENCES permissions (name);
ALTER TABLE api_token_permissions ADD CONSTRAINT fk_api_token_permission_token FOREIGN KEY (token_id) REFERENCES api_tokens (id) ON DELETE CASCADE;
ALTER TABLE api_tokens ADD CONSTRAINT fk_api_token_membership FOREIGN KEY (membership_id) REFERENCES users (id);
ALTER TABLE api_tokens ADD CONSTRAINT fk_api_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE attachments ADD CONSTRAINT fk_attachments_response FOREIGN KEY (response_id) REFERENCES work_order_responses (id);
ALTER TABLE attachments ADD CONSTRAINT fk_attachments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE attachments ADD CONSTRAINT fk_attachments_work_order FOREIGN KEY (fault_report_id) REFERENCES fault_reports (id);
ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_session_device FOREIGN KEY (trusted_device_id) REFERENCES trusted_devices (id);
ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_session_member FOREIGN KEY (membership_id) REFERENCES users (id);
ALTER TABLE auth_sessions ADD CONSTRAINT fk_auth_session_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE authorization_change_plans ADD CONSTRAINT fk_authorization_change_plans_operator FOREIGN KEY (operator_membership_id) REFERENCES users (id);
ALTER TABLE authorization_change_plans ADD CONSTRAINT fk_authorization_change_plans_target FOREIGN KEY (target_membership_id) REFERENCES users (id);
ALTER TABLE authorization_change_plans ADD CONSTRAINT fk_authorization_change_plans_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE authorization_profile_permission_rules ADD CONSTRAINT fk_authorization_profile_rules_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);
ALTER TABLE authorization_profile_permission_rules ADD CONSTRAINT fk_authorization_profile_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE authorization_profile_permission_rules ADD CONSTRAINT fk_authorization_profile_rules_version FOREIGN KEY (profile_version_id) REFERENCES authorization_profile_versions (id);
ALTER TABLE authorization_profile_versions ADD CONSTRAINT fk_authorization_profile_versions_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id);
ALTER TABLE authorization_profile_versions ADD CONSTRAINT fk_authorization_profile_versions_profile FOREIGN KEY (profile_id) REFERENCES authorization_profiles (id);
ALTER TABLE authorization_profile_versions ADD CONSTRAINT fk_authorization_profile_versions_publisher FOREIGN KEY (published_by_membership_id) REFERENCES users (id);
ALTER TABLE authorization_profile_versions ADD CONSTRAINT fk_authorization_profile_versions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE authorization_profiles ADD CONSTRAINT fk_authorization_profiles_current_version FOREIGN KEY (current_version_id) REFERENCES authorization_profile_versions (id);
ALTER TABLE authorization_profiles ADD CONSTRAINT fk_authorization_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE client_observation_events ADD CONSTRAINT fk_client_observation_event_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE client_observation_events ADD CONSTRAINT fk_client_observation_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE client_observation_events ADD CONSTRAINT fk_client_observation_event_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE command_idempotency_records ADD CONSTRAINT fk_command_idempotency_membership FOREIGN KEY (membership_id) REFERENCES users (id);
ALTER TABLE command_idempotency_records ADD CONSTRAINT fk_command_idempotency_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE daily_work_order_report_dispatches ADD CONSTRAINT fk_daily_report_dispatch_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE daily_work_order_report_dispatches ADD CONSTRAINT fk_daily_report_dispatch_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE department_change_events ADD CONSTRAINT fk_department_events_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE department_change_events ADD CONSTRAINT fk_department_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE departments ADD CONSTRAINT fk_departments_merged_into FOREIGN KEY (merged_into_department_id) REFERENCES departments (id);
ALTER TABLE departments ADD CONSTRAINT fk_departments_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE departments ADD CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments (id);
ALTER TABLE departments ADD CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_closer FOREIGN KEY (closed_by_user_id) REFERENCES users (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_current_assignment FOREIGN KEY (current_assignment_id) REFERENCES work_order_assignments (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_current_response FOREIGN KEY (current_response_id) REFERENCES work_order_responses (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_owner_org FOREIGN KEY (owner_org_id) REFERENCES org_units (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_pending_assignment FOREIGN KEY (pending_assignment_id) REFERENCES work_order_assignments (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_reporter FOREIGN KEY (reported_by_user_id) REFERENCES users (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_reporting_department FOREIGN KEY (reporting_department_id) REFERENCES departments (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_reporting_org FOREIGN KEY (reporting_org_id) REFERENCES org_units (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES users (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_responsible_department FOREIGN KEY (responsible_department_id) REFERENCES departments (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_responsible_org FOREIGN KEY (responsible_org_id) REFERENCES org_units (id);
ALTER TABLE fault_reports ADD CONSTRAINT fk_fault_reports_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE file_transfer_upload_sessions ADD CONSTRAINT fk_file_transfer_membership FOREIGN KEY (created_by_membership_id) REFERENCES users (id);
ALTER TABLE file_transfer_upload_sessions ADD CONSTRAINT fk_file_transfer_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE org_app_config ADD CONSTRAINT fk_org_app_config_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE org_app_config ADD CONSTRAINT fk_org_app_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE org_units ADD CONSTRAINT fk_org_units_parent FOREIGN KEY (parent_id) REFERENCES org_units (id);
ALTER TABLE org_units ADD CONSTRAINT fk_org_units_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE organization_move_events ADD CONSTRAINT fk_organization_move_events_new_parent FOREIGN KEY (new_parent_id) REFERENCES org_units (id);
ALTER TABLE organization_move_events ADD CONSTRAINT fk_organization_move_events_old_parent FOREIGN KEY (old_parent_id) REFERENCES org_units (id);
ALTER TABLE organization_move_events ADD CONSTRAINT fk_organization_move_events_operator FOREIGN KEY (operator_membership_id) REFERENCES users (id);
ALTER TABLE organization_move_events ADD CONSTRAINT fk_organization_move_events_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE organization_move_events ADD CONSTRAINT fk_organization_move_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE outbox_events ADD CONSTRAINT fk_outbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_grantor FOREIGN KEY (granted_by_membership_id) REFERENCES users (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_member FOREIGN KEY (membership_id) REFERENCES users (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_parent FOREIGN KEY (parent_grant_id) REFERENCES permission_grants (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_replacement FOREIGN KEY (replaced_by_grant_id) REFERENCES permission_grants (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_revoker FOREIGN KEY (revoked_by_membership_id) REFERENCES users (id);
ALTER TABLE permission_grants ADD CONSTRAINT fk_permission_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE tenant_app_config ADD CONSTRAINT fk_tenant_app_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_group FOREIGN KEY (tenant_group_id) REFERENCES tenant_groups (id);
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_root_org FOREIGN KEY (id, root_org_unit_id) REFERENCES org_units (tenant_id, id);
ALTER TABLE trusted_devices ADD CONSTRAINT fk_trusted_device_member FOREIGN KEY (membership_id) REFERENCES users (id);
ALTER TABLE trusted_devices ADD CONSTRAINT fk_trusted_device_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE user_transfer_job_rows ADD CONSTRAINT fk_user_transfer_job_rows_job FOREIGN KEY (job_id) REFERENCES user_transfer_jobs (id) ON DELETE CASCADE;
ALTER TABLE user_transfer_job_rows ADD CONSTRAINT fk_user_transfer_job_rows_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE user_transfer_jobs ADD CONSTRAINT fk_user_transfer_jobs_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id);
ALTER TABLE user_transfer_jobs ADD CONSTRAINT fk_user_transfer_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE users ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id);
ALTER TABLE users ADD CONSTRAINT fk_users_org_unit FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE users ADD CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_assignee FOREIGN KEY (assignee_membership_id) REFERENCES users (id);
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_assigner FOREIGN KEY (assigned_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_org FOREIGN KEY (responsible_org_id) REFERENCES org_units (id);
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_responsible_department FOREIGN KEY (responsible_department_id) REFERENCES departments (id);
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_assignments ADD CONSTRAINT fk_assignments_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_department_participations ADD CONSTRAINT fk_work_order_dept_participation_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_department_participations ADD CONSTRAINT fk_work_order_dept_participation_department FOREIGN KEY (department_id) REFERENCES departments (id);
ALTER TABLE work_order_department_participations ADD CONSTRAINT fk_work_order_dept_participation_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_department_participations ADD CONSTRAINT fk_work_order_dept_participation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_events ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_events ADD CONSTRAINT fk_events_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_material_usages ADD CONSTRAINT fk_material_usage_response FOREIGN KEY (response_id) REFERENCES work_order_responses (id);
ALTER TABLE work_order_material_usages ADD CONSTRAINT fk_material_usage_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_material_usages ADD CONSTRAINT fk_material_usage_work_order FOREIGN KEY (fault_report_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_organization_participations ADD CONSTRAINT fk_work_order_org_participation_creator FOREIGN KEY (created_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_organization_participations ADD CONSTRAINT fk_work_order_org_participation_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_organization_participations ADD CONSTRAINT fk_work_order_org_participation_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id);
ALTER TABLE work_order_organization_participations ADD CONSTRAINT fk_work_order_org_participation_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_organization_transfers ADD CONSTRAINT fk_work_order_transfer_decider FOREIGN KEY (decided_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_organization_transfers ADD CONSTRAINT fk_work_order_transfer_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_organization_transfers ADD CONSTRAINT fk_work_order_transfer_requester FOREIGN KEY (requested_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_organization_transfers ADD CONSTRAINT fk_work_order_transfer_source_org FOREIGN KEY (source_org_unit_id) REFERENCES org_units (id);
ALTER TABLE work_order_organization_transfers ADD CONSTRAINT fk_work_order_transfer_target_org FOREIGN KEY (target_org_unit_id) REFERENCES org_units (id);
ALTER TABLE work_order_organization_transfers ADD CONSTRAINT fk_work_order_transfer_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_responses ADD CONSTRAINT fk_responses_assignment FOREIGN KEY (assignment_id) REFERENCES work_order_assignments (id);
ALTER TABLE work_order_responses ADD CONSTRAINT fk_responses_submitter FOREIGN KEY (submitted_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_responses ADD CONSTRAINT fk_responses_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_responses ADD CONSTRAINT fk_responses_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);
ALTER TABLE work_order_sla_policies ADD CONSTRAINT fk_sla_policy_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_verifications ADD CONSTRAINT fk_verifications_response FOREIGN KEY (response_id) REFERENCES work_order_responses (id);
ALTER TABLE work_order_verifications ADD CONSTRAINT fk_verifications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id);
ALTER TABLE work_order_verifications ADD CONSTRAINT fk_verifications_verifier FOREIGN KEY (verified_by_membership_id) REFERENCES users (id);
ALTER TABLE work_order_verifications ADD CONSTRAINT fk_verifications_work_order FOREIGN KEY (work_order_id) REFERENCES fault_reports (id);

CREATE UNIQUE INDEX uk_users_username_normalized ON users (lower(username));
CREATE UNIQUE INDEX uk_users_email_normalized ON users (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX idx_users_tenant_name_pinyin_pattern ON users (tenant_id, (lower(name_pinyin)) text_pattern_ops);
CREATE INDEX idx_users_tenant_name_pinyin_initials_pattern ON users (tenant_id, (lower(name_pinyin_initials)) text_pattern_ops);
CREATE INDEX idx_permission_grants_active_member ON permission_grants (tenant_id, membership_id, permission_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_auth_sessions_active_member ON auth_sessions (tenant_id, membership_id, last_seen_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_outbox_events_ready ON outbox_events (available_at, id) WHERE state IN ('PENDING', 'FAILED');

--rollback DROP SCHEMA public CASCADE;
--rollback CREATE SCHEMA public;
