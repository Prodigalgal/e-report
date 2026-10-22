--liquibase formatted sql

--changeset ereport:2026-09-01-067-auth-session-pagination
-- 会话列表按账号与创建时间分页，避免高设备数账号把全量会话载入内存。
CREATE INDEX IF NOT EXISTS idx_auth_session_member_created_at
    ON auth_sessions (tenant_id, membership_id, created_at);

--rollback DROP INDEX IF EXISTS idx_auth_session_member_created_at;
