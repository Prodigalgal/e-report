param(
    [Parameter(Mandatory = $true)]
    [string]$EntitySchema,
    [Parameter(Mandatory = $true)]
    [string]$LegacySchemaMetadata,
    [Parameter(Mandatory = $true)]
    [string]$Output
)

$ErrorActionPreference = 'Stop'

$entityLines = Get-Content -LiteralPath $EntitySchema
$tableAndIndexLines = $entityLines | Where-Object {
    $_ -notmatch '^alter table if exists .+ add constraint .+ foreign key '
}

$tableAndIndexLines = $tableAndIndexLines | ForEach-Object {
    $_ -replace ', constraint uk_users_username unique \(username\)', '' `
       -replace ', constraint uk_users_email unique \(email\)', ''
}

$foreignKeyRows = Get-Content -LiteralPath $LegacySchemaMetadata |
    Where-Object { $_ -like 'FOREIGN_KEY|*' } |
    ForEach-Object {
        $parts = $_ -split '\|'
        [pscustomobject]@{
            Table = $parts[1]
            Name = $parts[2]
            Column = $parts[3]
            ReferencedTable = $parts[4]
            ReferencedColumn = $parts[5]
            DeleteAction = $parts[7]
        }
    }

$foreignKeys = $foreignKeyRows |
    Group-Object Table, Name |
    ForEach-Object {
        $rows = @($_.Group)
        $columns = ($rows | ForEach-Object Column) -join ', '
        $referencedColumns = ($rows | ForEach-Object ReferencedColumn) -join ', '
        $deleteClause = if ($rows[0].DeleteAction -and $rows[0].DeleteAction -ne 'NO ACTION') {
            " ON DELETE $($rows[0].DeleteAction)"
        } else {
            ''
        }
        "ALTER TABLE $($rows[0].Table) ADD CONSTRAINT $($rows[0].Name) FOREIGN KEY ($columns) REFERENCES $($rows[0].ReferencedTable) ($referencedColumns)$deleteClause;"
    } |
    Sort-Object

$header = @(
    '--liquibase formatted sql',
    '',
    '--changeset e-report:2026-08-23-000-v5-postgresql-baseline dbms:postgresql',
    '--comment PostgreSQL 5.0.9 canonical schema for atomic Grants, organization data boundaries, departments and work-order transfer.',
    "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
)

$auditTable = @(
    '',
    'CREATE TABLE database_migration_runs (',
    '    id uuid NOT NULL,',
    '    status varchar(30) NOT NULL,',
    '    source_database varchar(255) NOT NULL,',
    '    target_database varchar(255) NOT NULL,',
    '    started_at timestamp(6) without time zone NOT NULL,',
    '    completed_at timestamp(6) without time zone,',
    '    error_summary varchar(2000),',
    '    CONSTRAINT pk_database_migration_runs PRIMARY KEY (id)',
    ');',
    'CREATE TABLE database_migration_table_results (',
    '    id uuid NOT NULL,',
    '    run_id uuid NOT NULL,',
    '    table_name varchar(120) NOT NULL,',
    '    phase varchar(30) NOT NULL,',
    '    status varchar(30) NOT NULL,',
    '    source_row_count bigint NOT NULL,',
    '    target_row_count bigint NOT NULL,',
    '    source_digest varchar(64) NOT NULL,',
    '    target_digest varchar(64) NOT NULL,',
    '    completed_at timestamp(6) without time zone NOT NULL,',
    '    error_summary varchar(2000),',
    '    CONSTRAINT pk_database_migration_table_results PRIMARY KEY (id),',
    '    CONSTRAINT uk_database_migration_table_phase UNIQUE (run_id, table_name, phase),',
    '    CONSTRAINT fk_database_migration_table_run FOREIGN KEY (run_id) REFERENCES database_migration_runs (id)',
    ');',
    'CREATE INDEX idx_database_migration_table_run ON database_migration_table_results (run_id, status, table_name);',
    '',
    'CREATE TABLE legacy_migration_audit_records (',
    '    id uuid NOT NULL,',
    '    tenant_id uuid,',
    '    source_table varchar(120) NOT NULL,',
    '    source_key varchar(255) NOT NULL,',
    '    payload_json jsonb NOT NULL,',
    '    archived_at timestamp(6) without time zone NOT NULL,',
    '    CONSTRAINT pk_legacy_migration_audit_records PRIMARY KEY (id),',
    '    CONSTRAINT uk_legacy_migration_audit_source UNIQUE (source_table, source_key)',
    ');',
    'CREATE INDEX idx_legacy_migration_audit_tenant ON legacy_migration_audit_records (tenant_id, source_table);'
)

$postgresqlIndexes = @(
    '',
    'CREATE UNIQUE INDEX uk_users_username_normalized ON users (lower(username));',
    'CREATE UNIQUE INDEX uk_users_email_normalized ON users (lower(email)) WHERE email IS NOT NULL;',
    'CREATE INDEX idx_users_tenant_name_pinyin_pattern ON users (tenant_id, (lower(name_pinyin)) text_pattern_ops);',
    'CREATE INDEX idx_users_tenant_name_pinyin_initials_pattern ON users (tenant_id, (lower(name_pinyin_initials)) text_pattern_ops);',
    "CREATE INDEX idx_permission_grants_active_member ON permission_grants (tenant_id, membership_id, permission_id) WHERE status = 'ACTIVE';",
    "CREATE INDEX idx_auth_sessions_active_member ON auth_sessions (tenant_id, membership_id, last_seen_at) WHERE status = 'ACTIVE';",
    "CREATE INDEX idx_outbox_events_ready ON outbox_events (available_at, id) WHERE state IN ('PENDING', 'FAILED');"
)

$footer = @(
    '',
    '--rollback DROP SCHEMA public CASCADE;',
    '--rollback CREATE SCHEMA public;'
)

$content = @($header + $tableAndIndexLines + $auditTable + $foreignKeys + $postgresqlIndexes + $footer)
$target = [System.IO.Path]::GetFullPath($Output)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($target)) | Out-Null
[System.IO.File]::WriteAllLines($target, $content, [System.Text.UTF8Encoding]::new($false))
