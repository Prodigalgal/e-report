--liquibase formatted sql

--changeset e-report:2026-08-20-053-authorization-v3-schema-mysql dbms:mysql
--comment Authorization V3 separates business access, delegation, account control and privileged approval.
CREATE TABLE management_domains (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    parent_id BINARY(16),
    org_unit_id BINARY(16) NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_management_domains PRIMARY KEY (id),
    CONSTRAINT uk_management_domains_tenant_org UNIQUE (tenant_id, org_unit_id),
    CONSTRAINT fk_management_domains_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_management_domains_parent FOREIGN KEY (parent_id) REFERENCES management_domains (id),
    CONSTRAINT fk_management_domains_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_management_domains_tenant_parent ON management_domains (tenant_id, parent_id, status);
CREATE INDEX idx_management_domains_tenant_org ON management_domains (tenant_id, org_unit_id, status);

CREATE TABLE subject_protections (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    management_domain_id BINARY(16) NOT NULL,
    protection_class VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_subject_protections PRIMARY KEY (id),
    CONSTRAINT uk_subject_protections_membership UNIQUE (tenant_id, membership_id),
    CONSTRAINT fk_subject_protections_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_subject_protections_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_subject_protections_domain FOREIGN KEY (management_domain_id) REFERENCES management_domains (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_subject_protections_domain ON subject_protections (tenant_id, management_domain_id, protection_class);

CREATE TABLE authority_actor_assignments (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    membership_id BINARY(16) NOT NULL,
    management_domain_id BINARY(16) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    valid_from DATETIME(6),
    expires_at DATETIME(6),
    status VARCHAR(20) NOT NULL,
    issued_by_authority_id BINARY(16),
    issued_by_membership_id BINARY(16),
    issued_at DATETIME(6) NOT NULL,
    reason VARCHAR(1000),
    CONSTRAINT pk_authority_actor_assignments PRIMARY KEY (id),
    CONSTRAINT fk_authority_actor_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authority_actor_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_authority_actor_domain FOREIGN KEY (management_domain_id) REFERENCES management_domains (id),
    CONSTRAINT fk_authority_actor_issuer_domain FOREIGN KEY (issued_by_authority_id) REFERENCES management_domains (id),
    CONSTRAINT fk_authority_actor_issuer_member FOREIGN KEY (issued_by_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_authority_actor_member ON authority_actor_assignments (tenant_id, membership_id, status, expires_at);
CREATE INDEX idx_authority_actor_domain ON authority_actor_assignments (tenant_id, management_domain_id, status);

CREATE TABLE delegation_grants (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    holder_membership_id BINARY(16) NOT NULL,
    authority_actor_assignment_id BINARY(16) NOT NULL,
    permission_id BINARY(16) NOT NULL,
    maximum_data_scope_id BINARY(16) NOT NULL,
    relation_ceiling VARCHAR(40) NOT NULL,
    valid_from DATETIME(6),
    expires_at DATETIME(6),
    may_subdelegate BOOLEAN NOT NULL DEFAULT FALSE,
    parent_delegation_grant_id BINARY(16),
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_by_authority_id BINARY(16),
    issued_by_membership_id BINARY(16),
    issued_at DATETIME(6) NOT NULL,
    reason VARCHAR(1000),
    CONSTRAINT pk_delegation_grants PRIMARY KEY (id),
    CONSTRAINT fk_delegation_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_delegation_grants_holder FOREIGN KEY (holder_membership_id) REFERENCES users (id),
    CONSTRAINT fk_delegation_grants_actor FOREIGN KEY (authority_actor_assignment_id) REFERENCES authority_actor_assignments (id),
    CONSTRAINT fk_delegation_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_delegation_grants_scope FOREIGN KEY (maximum_data_scope_id) REFERENCES authorization_data_scopes (id),
    CONSTRAINT fk_delegation_grants_parent FOREIGN KEY (parent_delegation_grant_id) REFERENCES delegation_grants (id),
    CONSTRAINT fk_delegation_grants_issuer_domain FOREIGN KEY (issued_by_authority_id) REFERENCES management_domains (id),
    CONSTRAINT fk_delegation_grants_issuer_member FOREIGN KEY (issued_by_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_delegation_grants_holder ON delegation_grants (tenant_id, holder_membership_id, status, expires_at);
CREATE INDEX idx_delegation_grants_permission ON delegation_grants (tenant_id, permission_id, status, expires_at);
CREATE INDEX idx_delegation_grants_parent ON delegation_grants (parent_delegation_grant_id, status);

CREATE TABLE privileged_action_requests (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    requester_membership_id BINARY(16) NOT NULL,
    requester_authority_id BINARY(16),
    target_membership_id BINARY(16),
    action VARCHAR(50) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    required_approvals INT NOT NULL DEFAULT 1,
    expected_security_version BIGINT,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    applied_at DATETIME(6),
    CONSTRAINT pk_privileged_action_requests PRIMARY KEY (id),
    CONSTRAINT fk_privileged_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_privileged_requests_requester FOREIGN KEY (requester_membership_id) REFERENCES users (id),
    CONSTRAINT fk_privileged_requests_authority FOREIGN KEY (requester_authority_id) REFERENCES management_domains (id),
    CONSTRAINT fk_privileged_requests_target FOREIGN KEY (target_membership_id) REFERENCES users (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_privileged_requests_status ON privileged_action_requests (tenant_id, status, expires_at);
CREATE INDEX idx_privileged_requests_target ON privileged_action_requests (tenant_id, target_membership_id, status);

CREATE TABLE privileged_action_approvals (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    request_id BINARY(16) NOT NULL,
    approver_membership_id BINARY(16) NOT NULL,
    approver_authority_id BINARY(16) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    decision_reason VARCHAR(1000),
    decided_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_privileged_action_approvals PRIMARY KEY (id),
    CONSTRAINT uk_privileged_approval_requester UNIQUE (request_id, approver_membership_id),
    CONSTRAINT fk_privileged_approvals_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_privileged_approvals_request FOREIGN KEY (request_id) REFERENCES privileged_action_requests (id),
    CONSTRAINT fk_privileged_approvals_approver FOREIGN KEY (approver_membership_id) REFERENCES users (id),
    CONSTRAINT fk_privileged_approvals_authority FOREIGN KEY (approver_authority_id) REFERENCES management_domains (id)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE INDEX idx_privileged_approvals_request ON privileged_action_approvals (tenant_id, request_id, decision);

--changeset e-report:2026-08-20-053-authorization-v3-schema-h2 dbms:h2
--comment H2 equivalent for Authorization V3 control-plane schema.
CREATE TABLE management_domains (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    parent_id UUID,
    org_unit_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT DEFAULT 1 NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_management_domains_tenant_org UNIQUE (tenant_id, org_unit_id),
    CONSTRAINT fk_management_domains_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_management_domains_parent FOREIGN KEY (parent_id) REFERENCES management_domains (id),
    CONSTRAINT fk_management_domains_org FOREIGN KEY (org_unit_id) REFERENCES org_units (id)
);
CREATE INDEX idx_management_domains_tenant_parent ON management_domains (tenant_id, parent_id, status);
CREATE INDEX idx_management_domains_tenant_org ON management_domains (tenant_id, org_unit_id, status);
CREATE TABLE subject_protections (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    management_domain_id UUID NOT NULL,
    protection_class VARCHAR(30) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    reason VARCHAR(1000),
    version BIGINT DEFAULT 1 NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_subject_protections_membership UNIQUE (tenant_id, membership_id),
    CONSTRAINT fk_subject_protections_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_subject_protections_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_subject_protections_domain FOREIGN KEY (management_domain_id) REFERENCES management_domains (id)
);
CREATE INDEX idx_subject_protections_domain ON subject_protections (tenant_id, management_domain_id, protection_class);
CREATE TABLE authority_actor_assignments (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    membership_id UUID NOT NULL,
    management_domain_id UUID NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    valid_from TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    status VARCHAR(20) NOT NULL,
    issued_by_authority_id UUID,
    issued_by_membership_id UUID,
    issued_at TIMESTAMP(6) NOT NULL,
    reason VARCHAR(1000),
    CONSTRAINT fk_authority_actor_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_authority_actor_member FOREIGN KEY (membership_id) REFERENCES users (id),
    CONSTRAINT fk_authority_actor_domain FOREIGN KEY (management_domain_id) REFERENCES management_domains (id),
    CONSTRAINT fk_authority_actor_issuer_domain FOREIGN KEY (issued_by_authority_id) REFERENCES management_domains (id),
    CONSTRAINT fk_authority_actor_issuer_member FOREIGN KEY (issued_by_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_authority_actor_member ON authority_actor_assignments (tenant_id, membership_id, status, expires_at);
CREATE INDEX idx_authority_actor_domain ON authority_actor_assignments (tenant_id, management_domain_id, status);
CREATE TABLE delegation_grants (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    holder_membership_id UUID NOT NULL,
    authority_actor_assignment_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    maximum_data_scope_id UUID NOT NULL,
    relation_ceiling VARCHAR(40) NOT NULL,
    valid_from TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    may_subdelegate BOOLEAN DEFAULT FALSE NOT NULL,
    parent_delegation_grant_id UUID,
    source_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    issued_by_authority_id UUID,
    issued_by_membership_id UUID,
    issued_at TIMESTAMP(6) NOT NULL,
    reason VARCHAR(1000),
    CONSTRAINT fk_delegation_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_delegation_grants_holder FOREIGN KEY (holder_membership_id) REFERENCES users (id),
    CONSTRAINT fk_delegation_grants_actor FOREIGN KEY (authority_actor_assignment_id) REFERENCES authority_actor_assignments (id),
    CONSTRAINT fk_delegation_grants_permission FOREIGN KEY (permission_id) REFERENCES permissions (id),
    CONSTRAINT fk_delegation_grants_scope FOREIGN KEY (maximum_data_scope_id) REFERENCES authorization_data_scopes (id),
    CONSTRAINT fk_delegation_grants_parent FOREIGN KEY (parent_delegation_grant_id) REFERENCES delegation_grants (id),
    CONSTRAINT fk_delegation_grants_issuer_domain FOREIGN KEY (issued_by_authority_id) REFERENCES management_domains (id),
    CONSTRAINT fk_delegation_grants_issuer_member FOREIGN KEY (issued_by_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_delegation_grants_holder ON delegation_grants (tenant_id, holder_membership_id, status, expires_at);
CREATE INDEX idx_delegation_grants_permission ON delegation_grants (tenant_id, permission_id, status, expires_at);
CREATE INDEX idx_delegation_grants_parent ON delegation_grants (parent_delegation_grant_id, status);
CREATE TABLE privileged_action_requests (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    requester_membership_id UUID NOT NULL,
    requester_authority_id UUID,
    target_membership_id UUID,
    action VARCHAR(50) NOT NULL,
    payload_json CLOB NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    required_approvals INT DEFAULT 1 NOT NULL,
    expected_security_version BIGINT,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    applied_at TIMESTAMP(6),
    CONSTRAINT fk_privileged_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_privileged_requests_requester FOREIGN KEY (requester_membership_id) REFERENCES users (id),
    CONSTRAINT fk_privileged_requests_authority FOREIGN KEY (requester_authority_id) REFERENCES management_domains (id),
    CONSTRAINT fk_privileged_requests_target FOREIGN KEY (target_membership_id) REFERENCES users (id)
);
CREATE INDEX idx_privileged_requests_status ON privileged_action_requests (tenant_id, status, expires_at);
CREATE INDEX idx_privileged_requests_target ON privileged_action_requests (tenant_id, target_membership_id, status);
CREATE TABLE privileged_action_approvals (
    id UUID NOT NULL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    request_id UUID NOT NULL,
    approver_membership_id UUID NOT NULL,
    approver_authority_id UUID NOT NULL,
    decision VARCHAR(20) NOT NULL,
    decision_reason VARCHAR(1000),
    decided_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_privileged_approval_requester UNIQUE (request_id, approver_membership_id),
    CONSTRAINT fk_privileged_approvals_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_privileged_approvals_request FOREIGN KEY (request_id) REFERENCES privileged_action_requests (id),
    CONSTRAINT fk_privileged_approvals_approver FOREIGN KEY (approver_membership_id) REFERENCES users (id),
    CONSTRAINT fk_privileged_approvals_authority FOREIGN KEY (approver_authority_id) REFERENCES management_domains (id)
);
CREATE INDEX idx_privileged_approvals_request ON privileged_action_approvals (tenant_id, request_id, decision);

--changeset e-report:2026-08-20-053-authorization-v3-permissions-mysql dbms:mysql
--comment Seed V3 control-plane permissions before fixed-catalog startup validation.
INSERT INTO permissions (id, name, display_name, description)
SELECT UUID_TO_BIN(UUID(), 1), seed.name, seed.display_name, seed.description
FROM (
    SELECT 'authorization:delegation:view' name, '委托授权查看' display_name, '查看管理范围内账号的显式转授权上限' description
    UNION ALL SELECT 'authorization:delegation:grant', '委托授权授予', '授予不超过自身上游委托上限的转授权能力'
    UNION ALL SELECT 'authorization:delegation:revoke', '委托授权撤销', '撤销管理范围内账号的转授权能力'
    UNION ALL SELECT 'authorization:protection:view', '账号保护查看', '查看账号所属管理域和保护级别'
    UNION ALL SELECT 'authorization:protection:manage', '账号保护调整', '调整受控账号的保护管理域和保护级别'
    UNION ALL SELECT 'authorization:domain-owner:replace', '默认管理员更换', '通过上级管理域更换组织默认管理员'
    UNION ALL SELECT 'authorization:privileged-action:approve', '高风险授权审批', '审批由其他管理员发起的转授权、账号保护和默认管理员变更'
) seed
WHERE NOT EXISTS (SELECT 1 FROM permissions current_permission WHERE current_permission.name = seed.name);

--changeset e-report:2026-08-20-053-authorization-v3-permissions-h2 dbms:h2
--comment H2 V3 control-plane permission seed.
INSERT INTO permissions (id, name, display_name, description)
SELECT RANDOM_UUID(), seed.name, seed.display_name, seed.description
FROM (VALUES
    ('authorization:delegation:view', '委托授权查看', '查看管理范围内账号的显式转授权上限'),
    ('authorization:delegation:grant', '委托授权授予', '授予不超过自身上游委托上限的转授权能力'),
    ('authorization:delegation:revoke', '委托授权撤销', '撤销管理范围内账号的转授权能力'),
    ('authorization:protection:view', '账号保护查看', '查看账号所属管理域和保护级别'),
    ('authorization:protection:manage', '账号保护调整', '调整受控账号的保护管理域和保护级别'),
    ('authorization:domain-owner:replace', '默认管理员更换', '通过上级管理域更换组织默认管理员'),
    ('authorization:privileged-action:approve', '高风险授权审批', '审批由其他管理员发起的转授权、账号保护和默认管理员变更')
) seed(name, display_name, description)
WHERE NOT EXISTS (SELECT 1 FROM permissions current_permission WHERE current_permission.name = seed.name);

--changeset e-report:2026-08-20-053-authorization-v3-backfill-mysql dbms:mysql
--comment Build management domains and protected subjects from the organization tree without hard-coded organization codes.
INSERT INTO management_domains (id, tenant_id, parent_id, org_unit_id, type, status, version)
SELECT UUID_TO_BIN(UUID(), 1), org.tenant_id, NULL, org.id,
       CASE WHEN org.parent_id IS NULL THEN 'TENANT_ROOT' ELSE 'ORGANIZATION' END,
       'ACTIVE', 1
FROM org_units org
WHERE NOT EXISTS (
    SELECT 1 FROM management_domains existing
    WHERE existing.tenant_id = org.tenant_id AND existing.org_unit_id = org.id
);
UPDATE management_domains child
JOIN org_units org ON org.tenant_id = child.tenant_id AND org.id = child.org_unit_id
JOIN management_domains parent ON parent.tenant_id = org.tenant_id AND parent.org_unit_id = org.parent_id
SET child.parent_id = parent.id
WHERE org.parent_id IS NOT NULL;
INSERT INTO subject_protections (
    id, tenant_id, membership_id, management_domain_id, protection_class, source_type, reason, version)
SELECT UUID_TO_BIN(UUID(), 1), usr.tenant_id, usr.id, domain.id,
       CASE WHEN usr.system_key = 'SUPER_ADMINISTRATOR' THEN 'SYSTEM_ROOT'
            WHEN org.default_administrator_user_id = usr.id THEN 'DOMAIN_OWNER'
            ELSE 'STANDARD' END,
       'MIGRATION', '3.0.0 权限控制平面初始化', 1
FROM users usr
JOIN management_domains domain ON domain.tenant_id = usr.tenant_id AND domain.org_unit_id = usr.org_unit_id
JOIN org_units org ON org.tenant_id = usr.tenant_id AND org.id = usr.org_unit_id
WHERE NOT EXISTS (
    SELECT 1 FROM subject_protections existing
    WHERE existing.tenant_id = usr.tenant_id AND existing.membership_id = usr.id
);
UPDATE subject_protections protection
JOIN (
    SELECT DISTINCT permission_grant.tenant_id, permission_grant.membership_id
    FROM permission_grants permission_grant
    JOIN permissions permission ON permission.id = permission_grant.permission_id
    WHERE permission_grant.status = 'ACTIVE'
      AND permission.name IN (
          'user:authorization:grant', 'user:authorization:adjust', 'user:authorization:revoke',
          'user:manage:password:reset', 'user:manage:status:activate',
          'user:manage:status:deactivate', 'user:manage:status:lock', 'user:manage:status:unlock'
      )
) privileged ON privileged.tenant_id = protection.tenant_id
             AND privileged.membership_id = protection.membership_id
SET protection.protection_class = 'ADMINISTRATIVE',
    protection.reason = '3.0.0 根据现有高风险管理权限提升保护级别'
WHERE protection.protection_class = 'STANDARD';
INSERT INTO authority_actor_assignments (
    id, tenant_id, membership_id, management_domain_id, actor_role,
    valid_from, expires_at, status, issued_by_authority_id, issued_by_membership_id, issued_at, reason)
SELECT UUID_TO_BIN(UUID(), 1), org.tenant_id, org.default_administrator_user_id, domain.id, 'OWNER',
       CURRENT_TIMESTAMP(6), NULL, 'ACTIVE', parent.id, NULL, CURRENT_TIMESTAMP(6),
       '3.0.0 组织默认管理员迁移'
FROM org_units org
JOIN management_domains domain ON domain.tenant_id = org.tenant_id AND domain.org_unit_id = org.id
LEFT JOIN management_domains parent ON parent.id = domain.parent_id
WHERE org.default_administrator_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM authority_actor_assignments existing
      WHERE existing.tenant_id = org.tenant_id
        AND existing.membership_id = org.default_administrator_user_id
        AND existing.management_domain_id = domain.id
        AND existing.actor_role = 'OWNER'
        AND existing.status = 'ACTIVE'
  );
INSERT INTO authority_actor_assignments (
    id, tenant_id, membership_id, management_domain_id, actor_role,
    valid_from, expires_at, status, issued_by_authority_id, issued_by_membership_id, issued_at, reason)
SELECT UUID_TO_BIN(UUID(), 1), usr.tenant_id, usr.id, domain.id, 'ADMINISTRATOR',
       CURRENT_TIMESTAMP(6), NULL, 'ACTIVE', domain.parent_id, NULL, CURRENT_TIMESTAMP(6),
       '3.0.0 根据现有管理权限迁移受托管理员'
FROM users usr
JOIN management_domains domain ON domain.tenant_id = usr.tenant_id AND domain.org_unit_id = usr.org_unit_id
JOIN subject_protections protection ON protection.tenant_id = usr.tenant_id AND protection.membership_id = usr.id
WHERE protection.protection_class = 'ADMINISTRATIVE'
  AND NOT EXISTS (
      SELECT 1 FROM authority_actor_assignments existing
      WHERE existing.tenant_id = usr.tenant_id
        AND existing.membership_id = usr.id
        AND existing.management_domain_id = domain.id
        AND existing.actor_role = 'ADMINISTRATOR'
        AND existing.status = 'ACTIVE'
  );
INSERT INTO delegation_grants (
    id, tenant_id, holder_membership_id, authority_actor_assignment_id, permission_id,
    maximum_data_scope_id, relation_ceiling, valid_from, expires_at, may_subdelegate,
    parent_delegation_grant_id, source_type, status, issued_by_authority_id, issued_by_membership_id, issued_at, reason)
SELECT UUID_TO_BIN(UUID(), 1), permission_grant.tenant_id, actor.membership_id, actor.id, permission_grant.permission_id,
       permission_grant.data_scope_id, permission_grant.relation_constraint, permission_grant.valid_from, permission_grant.expires_at, TRUE,
       NULL, 'MIGRATION_BASELINE', 'ACTIVE', actor.management_domain_id, NULL, CURRENT_TIMESTAMP(6),
       '3.0.0 默认管理员显式委托上限迁移'
FROM authority_actor_assignments actor
JOIN permission_grants permission_grant ON permission_grant.tenant_id = actor.tenant_id
                                       AND permission_grant.membership_id = actor.membership_id
                                       AND permission_grant.status = 'ACTIVE'
JOIN permissions permission ON permission.id = permission_grant.permission_id
WHERE actor.status = 'ACTIVE' AND actor.actor_role = 'OWNER'
  AND permission.name NOT LIKE 'app-update:%'
  AND NOT EXISTS (
      SELECT 1 FROM delegation_grants existing
      WHERE existing.tenant_id = actor.tenant_id
        AND existing.authority_actor_assignment_id = actor.id
        AND existing.permission_id = permission_grant.permission_id
        AND existing.source_type = 'MIGRATION_BASELINE'
        AND existing.status = 'ACTIVE'
  );

--changeset e-report:2026-08-20-053-authorization-v3-backfill-h2 dbms:h2
--comment H2 backfill for management domains and protected subjects.
INSERT INTO management_domains (id, tenant_id, parent_id, org_unit_id, type, status, version)
SELECT RANDOM_UUID(), org.tenant_id, NULL, org.id,
       CASE WHEN org.parent_id IS NULL THEN 'TENANT_ROOT' ELSE 'ORGANIZATION' END,
       'ACTIVE', 1
FROM org_units org
WHERE NOT EXISTS (
    SELECT 1 FROM management_domains existing
    WHERE existing.tenant_id = org.tenant_id AND existing.org_unit_id = org.id
);
UPDATE management_domains child
SET parent_id = (
    SELECT parent.id
    FROM org_units org
    JOIN management_domains parent ON parent.tenant_id = org.tenant_id AND parent.org_unit_id = org.parent_id
    WHERE org.tenant_id = child.tenant_id AND org.id = child.org_unit_id
)
WHERE EXISTS (SELECT 1 FROM org_units org WHERE org.tenant_id = child.tenant_id
              AND org.id = child.org_unit_id AND org.parent_id IS NOT NULL);
INSERT INTO subject_protections (
    id, tenant_id, membership_id, management_domain_id, protection_class, source_type, reason, version)
SELECT RANDOM_UUID(), usr.tenant_id, usr.id, domain.id,
       CASE WHEN usr.system_key = 'SUPER_ADMINISTRATOR' THEN 'SYSTEM_ROOT'
            WHEN org.default_administrator_user_id = usr.id THEN 'DOMAIN_OWNER'
            ELSE 'STANDARD' END,
       'MIGRATION', '3.0.0 权限控制平面初始化', 1
FROM users usr
JOIN management_domains domain ON domain.tenant_id = usr.tenant_id AND domain.org_unit_id = usr.org_unit_id
JOIN org_units org ON org.tenant_id = usr.tenant_id AND org.id = usr.org_unit_id
WHERE NOT EXISTS (
    SELECT 1 FROM subject_protections existing
    WHERE existing.tenant_id = usr.tenant_id AND existing.membership_id = usr.id
);
UPDATE subject_protections protection
SET protection_class = 'ADMINISTRATIVE', reason = '3.0.0 根据现有高风险管理权限提升保护级别'
WHERE protection_class = 'STANDARD'
  AND EXISTS (
      SELECT 1 FROM permission_grants permission_grant
      JOIN permissions permission ON permission.id = permission_grant.permission_id
      WHERE permission_grant.tenant_id = protection.tenant_id AND permission_grant.membership_id = protection.membership_id
        AND permission_grant.status = 'ACTIVE'
        AND permission.name IN (
            'user:authorization:grant', 'user:authorization:adjust', 'user:authorization:revoke',
            'user:manage:password:reset', 'user:manage:status:activate',
            'user:manage:status:deactivate', 'user:manage:status:lock', 'user:manage:status:unlock'
        )
  );
INSERT INTO authority_actor_assignments (
    id, tenant_id, membership_id, management_domain_id, actor_role,
    valid_from, expires_at, status, issued_by_authority_id, issued_by_membership_id, issued_at, reason)
SELECT RANDOM_UUID(), org.tenant_id, org.default_administrator_user_id, domain.id, 'OWNER',
       CURRENT_TIMESTAMP, NULL, 'ACTIVE', domain.parent_id, NULL, CURRENT_TIMESTAMP,
       '3.0.0 组织默认管理员迁移'
FROM org_units org
JOIN management_domains domain ON domain.tenant_id = org.tenant_id AND domain.org_unit_id = org.id
WHERE org.default_administrator_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM authority_actor_assignments existing
      WHERE existing.tenant_id = org.tenant_id
        AND existing.membership_id = org.default_administrator_user_id
        AND existing.management_domain_id = domain.id
        AND existing.actor_role = 'OWNER'
        AND existing.status = 'ACTIVE'
  );
INSERT INTO authority_actor_assignments (
    id, tenant_id, membership_id, management_domain_id, actor_role,
    valid_from, expires_at, status, issued_by_authority_id, issued_by_membership_id, issued_at, reason)
SELECT RANDOM_UUID(), usr.tenant_id, usr.id, domain.id, 'ADMINISTRATOR',
       CURRENT_TIMESTAMP, NULL, 'ACTIVE', domain.parent_id, NULL, CURRENT_TIMESTAMP,
       '3.0.0 根据现有管理权限迁移受托管理员'
FROM users usr
JOIN management_domains domain ON domain.tenant_id = usr.tenant_id AND domain.org_unit_id = usr.org_unit_id
JOIN subject_protections protection ON protection.tenant_id = usr.tenant_id AND protection.membership_id = usr.id
WHERE protection.protection_class = 'ADMINISTRATIVE'
  AND NOT EXISTS (
      SELECT 1 FROM authority_actor_assignments existing
      WHERE existing.tenant_id = usr.tenant_id
        AND existing.membership_id = usr.id
        AND existing.management_domain_id = domain.id
        AND existing.actor_role = 'ADMINISTRATOR'
        AND existing.status = 'ACTIVE'
  );
INSERT INTO delegation_grants (
    id, tenant_id, holder_membership_id, authority_actor_assignment_id, permission_id,
    maximum_data_scope_id, relation_ceiling, valid_from, expires_at, may_subdelegate,
    parent_delegation_grant_id, source_type, status, issued_by_authority_id, issued_by_membership_id, issued_at, reason)
SELECT RANDOM_UUID(), permission_grant.tenant_id, actor.membership_id, actor.id, permission_grant.permission_id,
       permission_grant.data_scope_id, permission_grant.relation_constraint, permission_grant.valid_from, permission_grant.expires_at, TRUE,
       NULL, 'MIGRATION_BASELINE', 'ACTIVE', actor.management_domain_id, NULL, CURRENT_TIMESTAMP,
       '3.0.0 默认管理员显式委托上限迁移'
FROM authority_actor_assignments actor
JOIN permission_grants permission_grant ON permission_grant.tenant_id = actor.tenant_id
                                       AND permission_grant.membership_id = actor.membership_id
                                       AND permission_grant.status = 'ACTIVE'
JOIN permissions permission ON permission.id = permission_grant.permission_id
WHERE actor.status = 'ACTIVE' AND actor.actor_role = 'OWNER'
  AND permission.name NOT LIKE 'app-update:%'
  AND NOT EXISTS (
      SELECT 1 FROM delegation_grants existing
      WHERE existing.tenant_id = actor.tenant_id
        AND existing.authority_actor_assignment_id = actor.id
        AND existing.permission_id = permission_grant.permission_id
        AND existing.source_type = 'MIGRATION_BASELINE'
        AND existing.status = 'ACTIVE'
  );

--changeset e-report:2026-08-20-053-authorization-v3-preconditions dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM users u LEFT JOIN subject_protections p ON p.tenant_id = u.tenant_id AND p.membership_id = u.id WHERE p.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM org_units o LEFT JOIN management_domains d ON d.tenant_id = o.tenant_id AND d.org_unit_id = o.id WHERE d.id IS NULL
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM authority_actor_assignments a LEFT JOIN management_domains d ON d.id = a.management_domain_id WHERE d.id IS NULL
--comment Authorization V3 migration must not leave users, organizations or authority actors without control-plane facts.
SELECT 1;

--rollback DROP TABLE privileged_action_approvals;
--rollback DROP TABLE privileged_action_requests;
--rollback DROP TABLE delegation_grants;
--rollback DROP TABLE authority_actor_assignments;
--rollback DROP TABLE subject_protections;
--rollback DROP TABLE management_domains;
