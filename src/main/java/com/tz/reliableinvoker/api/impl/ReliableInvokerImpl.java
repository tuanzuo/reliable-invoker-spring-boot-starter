package com.tz.reliableinvoker.api.impl;

import com.tz.reliableinvoker.api.IReliableInvoker;
import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ParamsTooLargeException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationRequest;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.service.IAsyncExecutor;
import com.tz.reliableinvoker.service.IRetryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 可靠调用执行器默认实现
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 02:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerImpl implements IReliableInvoker {

    private static final int MAX_PARAMS_BYTES = 64 * 1024;

    private final IInvocationRecordDao recordDao;
    private final IRetryService retryService;
    private final ReliableInvokerProperties properties;
    private final TaskExecutor defaultTaskExecutor;
    private final Map<String, TaskExecutor> sceneTaskExecutors;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReliableInvokerImpl(IInvocationRecordDao recordDao,
                                IRetryService retryService,
                                IAsyncExecutor asyncExecutor,
                                ReliableInvokerProperties properties,
                                TaskExecutor defaultTaskExecutor,
                                Map<String, TaskExecutor> sceneTaskExecutors) {
        this.recordDao = recordDao;
        this.retryService = retryService;
        this.properties = properties;
        this.defaultTaskExecutor = defaultTaskExecutor;
        this.sceneTaskExecutors = sceneTaskExecutors != null ? sceneTaskExecutors : new HashMap<>();
    }

    @Override
    public InvocationRecord execute(InvocationRequest<?> request) {
        String scene = request.getScene().name();
        String paramsJson = this.serializeParams(request.getParams());

        int maxRetry = this.resolveMaxRetry(request);
        int retryDelay = this.resolveRetryDelay(request);

        InvocationRecord record = this.buildRecord(request, scene, paramsJson, maxRetry, retryDelay);

        if (!request.isSaveRecord()) {
            this.invokeTarget(request, record);
            return record;
        }

        this.recordDao.save(record);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ReliableInvokerImpl.this.invokeTarget(request, record);
                }

                @Override
                public void afterCompletion(int status) {
                }

                @Override
                public void suspend() {
                }

                @Override
                public void resume() {
                }

                @Override
                public void flush() {
                }

                @Override
                public void beforeCommit(boolean readOnly) {
                }

                @Override
                public void beforeCompletion() {
                }
            });
        } else {
            this.invokeTarget(request, record);
        }

        return record;
    }

    private void invokeTarget(InvocationRequest<?> request, InvocationRecord record) {
        String sceneName = request.getScene().name();
        TaskExecutor executor = this.sceneTaskExecutors.getOrDefault(sceneName, this.defaultTaskExecutor);
        if (request.isAsync()) {
            executor.execute(() -> this.retryService.retry(record));
        } else {
            this.retryService.retry(record);
        }
    }

    private int resolveMaxRetry(InvocationRequest<?> request) {
        if (request.getMaxRetry() > 0) {
            return request.getMaxRetry();
        }
        ReliableInvokerProperties.SceneProperties sceneProperties =
                this.properties.getSceneProperties(request.getScene().name());
        if (sceneProperties != null && sceneProperties.getMaxRetry() != null && sceneProperties.getMaxRetry() > 0) {
            return sceneProperties.getMaxRetry();
        }
        return this.properties.getMaxRetry();
    }

    private int resolveRetryDelay(InvocationRequest<?> request) {
        if (request.getRetryDelay() > 0) {
            return request.getRetryDelay();
        }
        ReliableInvokerProperties.SceneProperties sceneProperties =
                this.properties.getSceneProperties(request.getScene().name());
        if (sceneProperties != null && sceneProperties.getDefaultDelay() != null
                && sceneProperties.getDefaultDelay() > 0) {
            return sceneProperties.getDefaultDelay();
        }
        return this.properties.getDefaultDelay();
    }

    private String serializeParams(Object params) {
        if (params == null) {
            return null;
        }
        try {
            String json = this.objectMapper.writeValueAsString(params);
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > MAX_PARAMS_BYTES) {
                throw new ParamsTooLargeException(bytes.length, MAX_PARAMS_BYTES);
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new com.tz.reliableinvoker.exception.ReliableInvokerException(
                    "Failed to serialize params to JSON", e);
        }
    }

    private InvocationRecord buildRecord(InvocationRequest<?> request, String scene,
                                         String paramsJson, int maxRetry, int retryDelay) {
        InvocationRecord record = new InvocationRecord();
        record.setSerialNo(UUID.randomUUID().toString());
        record.setScene(scene);
        record.setBeanName(request.getBeanName());
        record.setMethodName(request.getMethodName());
        record.setParams(paramsJson);
        record.setStatus(InvocationStatusEnum.PENDING.getCode());
        record.setRetryCount(0);
        record.setMaxRetryCount(maxRetry);
        record.setRetryDelay(retryDelay);
        record.setExecuteTime(LocalDateTime.now().plusNanos((long) retryDelay * 1000000));
        record.setRemark(request.getRemark());
        record.setCreateTime(LocalDateTime.now());
        return record;
    }
}
