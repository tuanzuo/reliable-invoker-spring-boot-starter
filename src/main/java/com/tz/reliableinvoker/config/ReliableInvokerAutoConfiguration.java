package com.tz.reliableinvoker.config;

import com.tz.reliableinvoker.api.IReliableInvoker;
import com.tz.reliableinvoker.api.IReliableInvokerTask;
import com.tz.reliableinvoker.api.impl.ReliableInvokerImpl;
import com.tz.reliableinvoker.api.impl.ReliableInvokerTaskImpl;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.dao.impl.JdbcTemplateInvocationRecordDaoImpl;
import com.tz.reliableinvoker.service.IAsyncExecutor;
import com.tz.reliableinvoker.service.IBackupService;
import com.tz.reliableinvoker.service.IRetryService;
import com.tz.reliableinvoker.service.impl.AsyncExecutorImpl;
import com.tz.reliableinvoker.service.impl.BackupServiceImpl;
import com.tz.reliableinvoker.service.impl.RetryServiceImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;

/**
 * 可靠调用自动配置类
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 02:00:00
 * @version 1.0.0-SNAPSHOT
 */
@Configuration
@EnableConfigurationProperties(ReliableInvokerProperties.class)
@ConditionalOnProperty(prefix = "reliable.invoker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReliableInvokerAutoConfiguration {

    @Bean("reliableInvokerTaskExecutor")
    @ConditionalOnMissingBean(name = "reliableInvokerTaskExecutor")
    public ThreadPoolTaskExecutor reliableInvokerTaskExecutor(ReliableInvokerProperties properties) {
        ReliableInvokerProperties.AsyncProperties async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getCorePoolSize());
        executor.setMaxPoolSize(async.getMaxPoolSize());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("reliable-invoker-");
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean(IAsyncExecutor.class)
    public IAsyncExecutor asyncExecutor(ThreadPoolTaskExecutor reliableInvokerTaskExecutor) {
        return new AsyncExecutorImpl(reliableInvokerTaskExecutor);
    }

    @Bean
    public Map<String, TaskExecutor> sceneTaskExecutors(ReliableInvokerProperties properties) {
        Map<String, TaskExecutor> executors = new HashMap<>();
        if (properties.getScenes() != null) {
            for (Map.Entry<String, ReliableInvokerProperties.SceneProperties> e : properties.getScenes().entrySet()) {
                if (e.getValue().getAsync() != null) {
                    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
                    ex.setCorePoolSize(e.getValue().getAsync().getCorePoolSize());
                    ex.setMaxPoolSize(e.getValue().getAsync().getMaxPoolSize());
                    ex.setQueueCapacity(e.getValue().getAsync().getQueueCapacity());
                    ex.setThreadNamePrefix("reliable-" + e.getKey() + "-");
                    ex.initialize();
                    executors.put(e.getKey(), ex);
                }
            }
        }
        return executors;
    }

    @Bean
    @ConditionalOnMissingBean(IReliableInvoker.class)
    public IReliableInvoker reliableInvoker(IInvocationRecordDao recordDao,
            IRetryService retryService,
            IAsyncExecutor asyncExecutor,
            ReliableInvokerProperties properties,
            @Qualifier("reliableInvokerTaskExecutor") TaskExecutor defaultTaskExecutor,
            Map<String, TaskExecutor> sceneTaskExecutors) {
        return new ReliableInvokerImpl(recordDao, retryService, asyncExecutor, properties,
                defaultTaskExecutor, sceneTaskExecutors);
    }

    @Bean
    @ConditionalOnMissingBean(IReliableInvokerTask.class)
    public IReliableInvokerTask reliableInvokerTask(IInvocationRecordDao recordDao, IRetryService retryService,
                                                    IBackupService backupService) {
        return new ReliableInvokerTaskImpl(recordDao, retryService, backupService);
    }

    @Bean
    @ConditionalOnMissingBean(IInvocationRecordDao.class)
    public IInvocationRecordDao invocationRecordDao(JdbcTemplate jdbcTemplate,
                                                    ReliableInvokerProperties properties) {
        return new JdbcTemplateInvocationRecordDaoImpl(jdbcTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IRetryService.class)
    public IRetryService retryService(IInvocationRecordDao recordDao, ApplicationContext applicationContext) {
        return new RetryServiceImpl(applicationContext, recordDao);
    }

    @Bean
    @ConditionalOnMissingBean(IBackupService.class)
    public IBackupService backupService(IInvocationRecordDao recordDao) {
        return new BackupServiceImpl(recordDao);
    }
}
