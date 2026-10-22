apiVersion: batch/v1
kind: Job
metadata:
  name: e-report-liquibase-${MIGRATION_ID}
  namespace: ${NAMESPACE}
  labels:
    app: e-report-liquibase
    app.kubernetes.io/version: "${RELEASE_TAG}"
spec:
  backoffLimit: 0
  activeDeadlineSeconds: 1800
  ttlSecondsAfterFinished: 86400
  template:
    metadata:
      labels:
        app: e-report-liquibase
        app.kubernetes.io/version: "${RELEASE_TAG}"
    spec:
      restartPolicy: Never
      containers:
        - name: liquibase
          image: localhost/e-report-backend:${RELEASE_TAG}
          imagePullPolicy: Never
          command:
            - java
            - -Dloader.main=com.pro.ereportbacken.migration.LiquibaseMigrationMain
            - -cp
            - /app/app.jar
            - org.springframework.boot.loader.launch.PropertiesLauncher
          env:
            - name: E_REPORT_MIGRATION_URL
              value: jdbc:mysql://e-mysql:3306/fault_reporter_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
            - name: E_REPORT_MIGRATION_USERNAME
              value: root
            - name: E_REPORT_MIGRATION_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: e-report-mysql
                  key: MYSQL_ROOT_PASSWORD
            - name: DEFAULT_TENANT_GROUP_CODE
              value: "${DEFAULT_TENANT_GROUP_CODE}"
            - name: DEFAULT_TENANT_GROUP_NAME
              value: "${DEFAULT_TENANT_GROUP_NAME}"
            - name: DEFAULT_TENANT_CODE
              value: "${DEFAULT_TENANT_CODE}"
            - name: DEFAULT_TENANT_NAME
              value: "${DEFAULT_TENANT_NAME}"
            - name: DEFAULT_TENANT_ROOT_ORG_NAME
              value: "${DEFAULT_TENANT_ROOT_ORG_NAME}"
            - name: DEFAULT_ADMIN_USERNAME
              valueFrom:
                secretKeyRef:
                  name: e-report-default-admin
                  key: DEFAULT_ADMIN_USERNAME
            - name: E_REPORT_MIGRATION_TAG
              value: "${RELEASE_TAG}"
          resources:
            requests:
              cpu: 50m
              memory: 128Mi
            limits:
              memory: 512Mi
