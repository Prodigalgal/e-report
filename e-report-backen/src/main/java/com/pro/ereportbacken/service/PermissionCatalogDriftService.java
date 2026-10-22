package com.pro.ereportbacken.service;

import com.pro.ereportbacken.time.UtcTime;
import com.pro.ereportbacken.model.dto.PermissionCatalogDriftDTO;
import com.pro.ereportbacken.security.PermissionCatalog;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Log4j2
public class PermissionCatalogDriftService {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicReference<PermissionCatalogDriftDTO> latest = new AtomicReference<>(healthySnapshot());
    private final AtomicReference<String> lastLoggedFingerprint = new AtomicReference<>("");

    public PermissionCatalogDriftService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PermissionCatalogDriftDTO scanAndRequireCompleteCatalog() {
        PermissionCatalogDriftDTO snapshot = scan();
        if (!snapshot.missingDatabasePermissions().isEmpty()) {
            throw new IllegalStateException("权限目录数据库缺少当前代码声明的权限："
                    + String.join(", ", snapshot.missingDatabasePermissions()));
        }
        return snapshot;
    }

    public PermissionCatalogDriftDTO current() {
        return latest.get();
    }

    public PermissionCatalogDriftDTO scan() {
        List<DatabasePermissionReferences> databasePermissions = jdbcTemplate.query("""
                SELECT permission_row.name,
                       (SELECT COUNT(*) FROM authorization_profile_permission_rules profile_rule
                         WHERE profile_rule.permission_id = permission_row.id) AS profile_rule_references,
                       (SELECT COUNT(*) FROM permission_grants permission_grant
                         WHERE permission_grant.permission_id = permission_row.id) AS permission_grant_references,
                       (SELECT COUNT(*) FROM api_token_permissions token_permission
                         WHERE token_permission.permission_name = permission_row.name) AS api_token_references
                FROM permissions permission_row
                ORDER BY permission_row.name
                """, (resultSet, rowNumber) -> new DatabasePermissionReferences(
                resultSet.getString("name"),
                resultSet.getLong("profile_rule_references"),
                resultSet.getLong("permission_grant_references"),
                resultSet.getLong("api_token_references")
        ));

        Set<String> databaseNames = new HashSet<>();
        List<PermissionCatalogDriftDTO.DatabaseOnlyPermission> databaseOnly = new ArrayList<>();
        for (DatabasePermissionReferences permission : databasePermissions) {
            databaseNames.add(permission.permissionName());
            if (!PermissionCatalog.contains(permission.permissionName())) {
                long totalReferences = permission.profileRuleReferences()
                        + permission.permissionGrantReferences()
                        + permission.apiTokenReferences();
                databaseOnly.add(new PermissionCatalogDriftDTO.DatabaseOnlyPermission(
                        permission.permissionName(), permission.profileRuleReferences(),
                        permission.permissionGrantReferences(), permission.apiTokenReferences(), totalReferences));
            }
        }
        databaseOnly.sort(Comparator.comparing(
                PermissionCatalogDriftDTO.DatabaseOnlyPermission::permissionName,
                String.CASE_INSENSITIVE_ORDER));

        List<String> missingDatabase = PermissionCatalog.names().stream()
                .filter(permissionName -> !databaseNames.contains(permissionName))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        boolean referencedRetiredPermission = databaseOnly.stream()
                .anyMatch(permission -> permission.totalReferences() > 0);
        PermissionCatalogDriftDTO.Status status = !missingDatabase.isEmpty() || referencedRetiredPermission
                ? PermissionCatalogDriftDTO.Status.CRITICAL
                : databaseOnly.isEmpty()
                ? PermissionCatalogDriftDTO.Status.HEALTHY
                : PermissionCatalogDriftDTO.Status.WARNING;
        PermissionCatalogDriftDTO snapshot = new PermissionCatalogDriftDTO(
                UtcTime.now(), status, missingDatabase.size() + databaseOnly.size(),
                missingDatabase, databaseOnly);
        latest.set(snapshot);
        logChangedSnapshot(snapshot);
        return snapshot;
    }

    @Scheduled(
            initialDelayString = "${app.permission-catalog-drift.initial-delay-ms:300000}",
            fixedDelayString = "${app.permission-catalog-drift.scan-delay-ms:300000}"
    )
    public void scheduledScan() {
        try {
            scan();
        } catch (RuntimeException exception) {
            log.error("Permission catalog drift scan failed.", exception);
        }
    }

    private void logChangedSnapshot(PermissionCatalogDriftDTO snapshot) {
        String fingerprint = snapshot.status() + "|" + snapshot.missingDatabasePermissions()
                + "|" + snapshot.databaseOnlyPermissions();
        if (fingerprint.equals(lastLoggedFingerprint.getAndSet(fingerprint))) {
            return;
        }
        if (snapshot.status() == PermissionCatalogDriftDTO.Status.HEALTHY) {
            log.info("Permission catalog consistency restored. permissionCatalogDriftCount=0");
            return;
        }
        String message = "Permission catalog drift detected. status={}, permissionCatalogDriftCount={}, "
                + "missingDatabasePermissions={}, databaseOnlyPermissions={}";
        if (snapshot.status() == PermissionCatalogDriftDTO.Status.CRITICAL) {
            log.error(message, snapshot.status(), snapshot.permissionCatalogDriftCount(),
                    snapshot.missingDatabasePermissions(), snapshot.databaseOnlyPermissions());
        } else {
            log.warn(message, snapshot.status(), snapshot.permissionCatalogDriftCount(),
                    snapshot.missingDatabasePermissions(), snapshot.databaseOnlyPermissions());
        }
    }

    private static PermissionCatalogDriftDTO healthySnapshot() {
        return new PermissionCatalogDriftDTO(
                LocalDateTime.MIN, PermissionCatalogDriftDTO.Status.HEALTHY, 0, List.of(), List.of());
    }

    private record DatabasePermissionReferences(
            String permissionName,
            long profileRuleReferences,
            long permissionGrantReferences,
            long apiTokenReferences
    ) {
    }
}
