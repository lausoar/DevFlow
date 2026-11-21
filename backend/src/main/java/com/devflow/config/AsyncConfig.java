package com.devflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 * 用于文档知识库同步、聊天 @AI 回复等旁路任务，避免阻塞主请求
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-async-");
        // 请求 ID 跨线程传播：AI 回复/索引是在 ai-async-* 上执行的，
        // 不复制 MDC 的话，用户提供的 X-Request-Id 到异步日志里就断了。
        executor.setTaskDecorator(runnable -> {
            java.util.Map<String, String> context = org.slf4j.MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    org.slf4j.MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    org.slf4j.MDC.clear();
                }
            };
        });
        // 优雅停机：等待在途的 AI 回复/索引任务收尾，避免消息写到一半被 kill
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
