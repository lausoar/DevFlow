package com.devflow.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class V4PendingProtectionCallbackTest {
    private final V4PendingProtectionCallback callback = new V4PendingProtectionCallback();

    @Test
    void supportsOnlyBeforeEachMigrateForVersion4() {
        Context v4 = contextWithVersion("4");
        assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, v4)).isTrue();
        assertThat(callback.supports(Event.AFTER_EACH_MIGRATE, v4)).isFalse();
        assertThat(callback.supports(Event.BEFORE_MIGRATE, v4)).isFalse();
        assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, contextWithVersion("3"))).isFalse();
        assertThat(callback.supports(Event.BEFORE_EACH_MIGRATE, contextWithVersion("5"))).isFalse();
    }

    @Test
    void handleInsertsBothGuardsImmediatelyBeforeV4() throws Exception {
        Context context = contextWithVersion("4");
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        callback.handle(Event.BEFORE_EACH_MIGRATE, context);

        verify(statement).executeUpdate("""
                INSERT IGNORE INTO schema_migration (name) VALUES
                  ('2026-10-26-requirement-status-three-state'),
                  ('2026-11-task-status-normalize')
                """);
        verify(statement).close();
    }

    @Test
    void handleDoesNotTouchTheDatabaseForOtherMigrations() {
        Context context = contextWithVersion("7");
        Connection connection = mock(Connection.class);
        when(context.getConnection()).thenReturn(connection);

        callback.handle(Event.BEFORE_EACH_MIGRATE, context);

        verifyNoInteractions(connection);
    }

    @Test
    void databaseErrorsFailTheMigration() throws Exception {
        Context context = contextWithVersion("4");
        Connection connection = mock(Connection.class);
        when(context.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenThrow(new SQLException("fixture failure"));

        assertThatThrownBy(() -> callback.handle(Event.BEFORE_EACH_MIGRATE, context))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("V4")
                .hasCauseInstanceOf(SQLException.class);
    }

    private static Context contextWithVersion(String version) {
        Context context = mock(Context.class);
        MigrationInfo info = mock(MigrationInfo.class);
        when(context.getMigrationInfo()).thenReturn(info);
        when(info.getVersion()).thenReturn(MigrationVersion.fromVersion(version));
        return context;
    }
}
