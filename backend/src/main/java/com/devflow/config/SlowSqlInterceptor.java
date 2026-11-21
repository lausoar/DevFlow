package com.devflow.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.concurrent.TimeUnit;

/**
 * 慢 SQL 日志：只记录语句 ID 与耗时，不记录参数。
 *
 * 生产上 SQL 日志要么全开（把手机号、聊天正文写进日志）要么全关（出问题无从下手）。
 * 这里取中间态：只在超过阈值时 WARN 一行，带 MappedStatement id 与 requestId
 * （由日志 MDC 自动附加），足够定位是哪条查询，又不泄露数据。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query",
                args = {java.sql.Statement.class, org.apache.ibatis.session.ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update",
                args = {java.sql.Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch",
                args = {java.sql.Statement.class}),
})
public class SlowSqlInterceptor implements Interceptor {

    private final long thresholdMillis;

    public SlowSqlInterceptor(@Value("${devflow.observability.slow-sql-ms:500}") long thresholdMillis) {
        this.thresholdMillis = Math.max(0, thresholdMillis);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (thresholdMillis <= 0) {
            return invocation.proceed();
        }
        long start = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            if (elapsedMs >= thresholdMillis) {
                log.warn("慢 SQL: {} 耗时 {}ms", statementId(invocation.getTarget()), elapsedMs);
            }
        }
    }

    private String statementId(Object target) {
        try {
            MetaObject meta = SystemMetaObject.forObject(target);
            MappedStatement statement = (MappedStatement) meta.getValue("delegate.mappedStatement");
            String id = statement.getId();
            return id.length() <= 200 ? id : id.substring(0, 200);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
