--liquibase formatted sql

--changeset e-report:2026-08-14-033-worker-view-scope dbms:mysql,h2
--comment Let built-in work-order workers view every matching-type order covered by their organization grant, while action permissions remain relationship-scoped.
UPDATE role_permissions
SET scope_profile = 'GRANT_SCOPE'
WHERE scope_profile = 'CLAIMABLE_OR_PARTICIPANT'
  AND role_id IN (
      SELECT id
      FROM roles
      WHERE built_in = TRUE
        AND name IN (
            'ROLE_NETWORK_FAULT_WORKER',
            'ROLE_BOX_DOOR_OPEN_WORKER',
            'ROLE_DAMAGED_BOX_REPLACEMENT_WORKER'
        )
  )
  AND permission_id IN (
      SELECT id
      FROM permissions
      WHERE name IN (
          'work-order:network-fault:view',
          'work-order:box-door-open:view',
          'work-order:damaged-box-replacement:view'
      )
  );

--rollback UPDATE role_permissions SET scope_profile = 'CLAIMABLE_OR_PARTICIPANT' WHERE scope_profile = 'GRANT_SCOPE' AND role_id IN (SELECT id FROM roles WHERE built_in = TRUE AND name IN ('ROLE_NETWORK_FAULT_WORKER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER')) AND permission_id IN (SELECT id FROM permissions WHERE name IN ('work-order:network-fault:view','work-order:box-door-open:view','work-order:damaged-box-replacement:view'));

--changeset e-report:2026-08-14-033-worker-view-scope-assertion dbms:mysql,h2
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM role_permissions rule_row JOIN roles role_row ON role_row.id = rule_row.role_id JOIN permissions permission_row ON permission_row.id = rule_row.permission_id WHERE role_row.built_in = TRUE AND role_row.name IN ('ROLE_NETWORK_FAULT_WORKER','ROLE_BOX_DOOR_OPEN_WORKER','ROLE_DAMAGED_BOX_REPLACEMENT_WORKER') AND permission_row.name IN ('work-order:network-fault:view','work-order:box-door-open:view','work-order:damaged-box-replacement:view') AND rule_row.scope_profile <> 'GRANT_SCOPE'
--comment Halt startup if any built-in worker view rule did not migrate to organization grant scope.
SELECT 1;

--rollback SELECT 1;
