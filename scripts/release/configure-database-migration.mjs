import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const patchPath = 'patches/e-report-database-migration.yaml';
const imagePattern = /^ghcr\.io\/prodigalgal\/e-report-backend@sha256:[a-f0-9]{64}$/;

export function databaseMigrationPatch(environment, image) {
  if (!['test', 'production'].includes(environment) || !imagePattern.test(image)) {
    throw new Error('Database migration requires a valid environment and immutable backend image');
  }
  return `apiVersion: apps/v1
kind: Deployment
metadata:
  name: e-report-backend
  namespace: ${environment === 'test' ? 'e-report-test' : 'e-report'}
spec:
  template:
    spec:
      initContainers:
        - name: database-migration
          image: ${image}
          imagePullPolicy: IfNotPresent
          command:
            - java
            - -Duser.timezone=UTC
            - -Dloader.main=com.pro.ereportbacken.migration.LiquibaseMigrationMain
            - -cp
            - /app/app.jar
            - org.springframework.boot.loader.launch.PropertiesLauncher
          env:
            - name: E_REPORT_MIGRATION_URL
              value: jdbc:postgresql://e-postgresql-v5:5432/fault_reporter_db
            - name: E_REPORT_MIGRATION_USERNAME
              value: e_report_migrator
            - name: E_REPORT_MIGRATION_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: e-report-postgresql
                  key: POSTGRES_PASSWORD
            - name: E_REPORT_MIGRATION_CHANGELOG
              value: db/changelog/postgresql/db.changelog-master.yaml
          resources:
            requests:
              cpu: 50m
              memory: 128Mi
            limits:
              cpu: '1'
              memory: 512Mi
          securityContext:
            allowPrivilegeEscalation: false
            capabilities:
              drop: [ALL]
            runAsNonRoot: true
            runAsUser: 10001
`;
}

export function addMigrationPatch(source) {
  const marker = `  - path: ${patchPath}`;
  const lines = source.split(/\r?\n/);
  if (lines.includes(marker)) return source;
  const index = lines.indexOf('patches:');
  if (index < 0) return `${source.trimEnd()}\npatches:\n${marker}\n`;
  const nextSection = lines.findIndex((line, position) => position > index && /^\S/.test(line) && !line.startsWith('#'));
  lines.splice(nextSection < 0 ? lines.length : nextSection, 0, marker);
  return `${lines.join('\n').trimEnd()}\n`;
}

export function configureDatabaseMigration(configRoot, environment, image) {
  const patch = databaseMigrationPatch(environment, image);
  const environmentRoot = resolve(configRoot, 'environments', environment);
  const kustomization = resolve(environmentRoot, 'kustomization.yaml');
  const source = readFileSync(kustomization, 'utf8');
  const updated = addMigrationPatch(source);
  mkdirSync(resolve(environmentRoot, 'patches'), { recursive: true });
  writeFileSync(resolve(environmentRoot, patchPath), patch);
  if (updated !== source) writeFileSync(kustomization, updated);
}
