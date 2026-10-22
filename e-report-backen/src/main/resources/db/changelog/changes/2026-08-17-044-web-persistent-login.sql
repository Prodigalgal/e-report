--liquibase formatted sql

--changeset e-report:2026-08-17-044-web-persistent-login dbms:mysql,h2
--comment Record whether a WEB session may survive browser restarts and preserve existing persistent sessions.
ALTER TABLE auth_sessions ADD COLUMN persistent_login BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE auth_sessions SET persistent_login = TRUE WHERE client_type = 'WEB';

--rollback ALTER TABLE auth_sessions DROP COLUMN persistent_login;
