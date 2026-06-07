package com.tz.reliableinvoker.config;

import com.tz.reliableinvoker.api.IReliableInvoker;
import com.tz.reliableinvoker.api.IReliableInvokerTask;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.service.IAsyncExecutor;
import com.tz.reliableinvoker.service.IBackupService;
import com.tz.reliableinvoker.service.IRetryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReliableInvokerAutoConfiguration 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 14:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class,
                    ReliableInvokerAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "reliable.invoker.enabled=true"
            );

    @Test
    void testAutoConfigurationCreatesBeans() {
        runner.run(new org.springframework.boot.test.context.runner.ContextConsumer<org.springframework.boot.test.context.assertj.AssertableApplicationContext>() {
            @Override
            public void accept(org.springframework.boot.test.context.assertj.AssertableApplicationContext ctx) {
                assertThat(ctx.getBean(IReliableInvoker.class)).isNotNull();
                assertThat(ctx.getBean(IReliableInvokerTask.class)).isNotNull();
                assertThat(ctx.getBean(IInvocationRecordDao.class)).isNotNull();
                assertThat(ctx.getBean(IRetryService.class)).isNotNull();
                assertThat(ctx.getBean(IBackupService.class)).isNotNull();
                assertThat(ctx.getBean(IAsyncExecutor.class)).isNotNull();
            }
        });
    }

    @Test
    void testDisabled() {
        runner.withPropertyValues("reliable.invoker.enabled=false")
                .run(new org.springframework.boot.test.context.runner.ContextConsumer<org.springframework.boot.test.context.assertj.AssertableApplicationContext>() {
                    @Override
                    public void accept(org.springframework.boot.test.context.assertj.AssertableApplicationContext ctx) {
                        assertThat(ctx.getBeansOfType(IReliableInvoker.class)).isEmpty();
                    }
                });
    }
}
