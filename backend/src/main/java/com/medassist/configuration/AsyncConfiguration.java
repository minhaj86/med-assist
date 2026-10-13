package com.medassist.configuration;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Executor for background clinical work (assessment pipeline, knowledge ingestion). Uses virtual threads
 * with a concurrency limit and propagates the logging MDC (correlation id) and security context.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class AsyncConfiguration {

    public static final String CLINICAL_TASK_EXECUTOR = "clinicalTaskExecutor";

    @Bean(name = CLINICAL_TASK_EXECUTOR)
    SimpleAsyncTaskExecutor clinicalTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("clinical-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(32);
        executor.setTaskDecorator(contextPropagatingDecorator());
        return executor;
    }

    static TaskDecorator contextPropagatingDecorator() {
        return runnable -> {
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            SecurityContext securityContext = SecurityContextHolder.getContext();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                SecurityContext previousSecurity = SecurityContextHolder.getContext();
                SecurityContextHolder.setContext(securityContext);
                try {
                    runnable.run();
                } finally {
                    SecurityContextHolder.setContext(previousSecurity);
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
