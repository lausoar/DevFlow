package com.devflow.config;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.Statement;

/** Prevents the pending V4 script from destroying workflow-specific status codes. */
@Component
public class V4PendingProtectionCallback implements Callback {
    private static final MigrationVersion V4 = MigrationVersion.fromVersion("4");
    private static final String INSERT_MARKERS = """
            INSERT IGNORE INTO schema_migration (name) VALUES
              ('2026-10-26-requirement-status-three-state'),
              ('2026-11-task-status-normalize')
            """;

    @Override
    public boolean supports(Event event, Context context) {
        if (event != Event.BEFORE_EACH_MIGRATE || context == null) {
            return false;
        }
        MigrationInfo info = context.getMigrationInfo();
        return info != null && V4.equals(info.getVersion());
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        if (!supports(event, context)) {
            return;
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.executeUpdate(INSERT_MARKERS);
        } catch (SQLException e) {
            throw new FlywayException("Could not install pending V4 status-protection markers", e);
        }
    }

    @Override
    public String getCallbackName() {
        return "v4PendingProtection";
    }
}
