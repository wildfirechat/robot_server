package cn.wildfirechat.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Configuration
@EnableAsync
public class ExecutorConfig {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorConfig.class);

    @Bean("ragIngestExecutor")
    public Executor ragIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("rag-ingest-");
        executor.setRejectedExecutionHandler((runnable, pool) -> {
            LOG.error("知识库处理队列已满，拒绝任务, active={}, queue={}",
                    pool.getActiveCount(), pool.getQueue().size());
            throw new RejectedExecutionException("知识库处理队列已满，请稍后再试");
        });
        executor.initialize();
        return executor;
    }

    @Bean
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("robot-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler((runnable, pool) -> {
            LOG.error("机器人任务队列已满，拒绝任务, active={}, queue={}",
                    pool.getActiveCount(), pool.getQueue().size());
            throw new RejectedExecutionException("机器人繁忙，请稍后再试");
        });
        executor.initialize();
        return executor;
    }
}
