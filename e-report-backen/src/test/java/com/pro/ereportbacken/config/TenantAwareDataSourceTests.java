package com.pro.ereportbacken.config;

import com.pro.ereportbacken.security.TenantExecutionContext;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAwareDataSourceTests {

    @Test
    void appliesAndClearsPostgreSqlTenantContextAroundBorrowedConnection() throws Exception {
        DataSource delegateDataSource = mock(DataSource.class);
        Connection delegateConnection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        PreparedStatement contextStatement = mock(PreparedStatement.class);
        Statement applicationStatement = mock(Statement.class);
        when(delegateDataSource.getConnection()).thenReturn(delegateConnection);
        when(delegateConnection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(delegateConnection.prepareStatement(anyString())).thenReturn(contextStatement);
        when(delegateConnection.createStatement()).thenReturn(applicationStatement);

        TenantExecutionContext executionContext = new TenantExecutionContext();
        Connection connection = new TenantAwareDataSource(delegateDataSource, executionContext).getConnection();
        UUID tenantId = com.pro.ereportbacken.TestIds.uuid(73);
        executionContext.runInTenant(tenantId, () -> {
            try {
                assertThat(connection.createStatement()).isSameAs(applicationStatement);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
        connection.close();

        var ordered = inOrder(contextStatement, delegateConnection);
        ordered.verify(contextStatement).setString(1, tenantId.toString());
        ordered.verify(contextStatement).execute();
        ordered.verify(delegateConnection).createStatement();
        ordered.verify(contextStatement).setString(1, "");
        ordered.verify(contextStatement).execute();
        ordered.verify(delegateConnection).close();
    }

    @Test
    void leavesNonPostgreSqlConnectionsUntouched() throws Exception {
        DataSource delegateDataSource = mock(DataSource.class);
        Connection delegateConnection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(delegateDataSource.getConnection()).thenReturn(delegateConnection);
        when(delegateConnection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");

        Connection connection = new TenantAwareDataSource(
                delegateDataSource,
                new TenantExecutionContext()
        ).getConnection();

        assertThat(connection).isSameAs(delegateConnection);
        verify(delegateConnection).getMetaData();
    }
}
