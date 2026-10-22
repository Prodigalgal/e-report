package com.pro.ereportbacken.config;

import com.pro.ereportbacken.security.TenantExecutionContext;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

@Log4j2
final class TenantAwareDataSource extends DelegatingDataSource {

    private final TenantExecutionContext tenantExecutionContext;

    TenantAwareDataSource(DataSource targetDataSource, TenantExecutionContext tenantExecutionContext) {
        super(targetDataSource);
        this.tenantExecutionContext = tenantExecutionContext;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrap(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrap(super.getConnection(username, password));
    }

    private Connection wrap(Connection connection) throws SQLException {
        if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
            return connection;
        }
        TenantConnectionInvocationHandler handler = new TenantConnectionInvocationHandler(
                connection,
                tenantExecutionContext
        );
        return (Connection) Proxy.newProxyInstance(
                connection.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                handler
        );
    }

    private static final class TenantConnectionInvocationHandler implements java.lang.reflect.InvocationHandler {

        private static final String SET_TENANT_SQL = "SELECT set_config('app.tenant_id', ?, false)";

        private final Connection delegate;
        private final TenantExecutionContext tenantExecutionContext;
        private String appliedTenantId;
        private boolean closed;

        private TenantConnectionInvocationHandler(
                Connection delegate,
                TenantExecutionContext tenantExecutionContext) {
            this.delegate = delegate;
            this.tenantExecutionContext = tenantExecutionContext;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            String methodName = method.getName();
            if ("close".equals(methodName)) {
                close();
                return null;
            }
            if ("isClosed".equals(methodName) && closed) {
                return true;
            }
            if (createsStatement(methodName)) {
                applyCurrentTenant();
            }
            try {
                return method.invoke(delegate, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private boolean createsStatement(String methodName) {
            return "createStatement".equals(methodName)
                    || "prepareStatement".equals(methodName)
                    || "prepareCall".equals(methodName);
        }

        private void applyCurrentTenant() throws SQLException {
            String tenantId = tenantExecutionContext.currentTenantId()
                    .map(Object::toString)
                    .orElse("");
            if (Objects.equals(appliedTenantId, tenantId)) {
                return;
            }
            try (PreparedStatement statement = delegate.prepareStatement(SET_TENANT_SQL)) {
                statement.setString(1, tenantId);
                statement.execute();
            }
            appliedTenantId = tenantId;
        }

        private void close() throws SQLException {
            if (closed) return;
            try {
                if (appliedTenantId != null && !appliedTenantId.isEmpty() && !delegate.isClosed()) {
                    try (PreparedStatement statement = delegate.prepareStatement(SET_TENANT_SQL)) {
                        statement.setString(1, "");
                        statement.execute();
                    }
                }
            } catch (SQLException exception) {
                log.warn("Failed to clear PostgreSQL tenant session context before returning the connection.", exception);
            } finally {
                closed = true;
                delegate.close();
            }
        }
    }
}
