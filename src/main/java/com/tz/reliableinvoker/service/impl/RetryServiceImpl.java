package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.exception.ExecutionException;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.service.IRetryService;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认重试执行服务实现
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 02:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class RetryServiceImpl implements IRetryService {

    /** Spring上下文 */
    private final ApplicationContext applicationContext;

    /** 调用记录数据访问对象 */
    private final IInvocationRecordDao recordDao;

    /** JSON解析器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RetryServiceImpl(ApplicationContext applicationContext, IInvocationRecordDao recordDao) {
        this.applicationContext = applicationContext;
        this.recordDao = recordDao;
    }

    @Override
    public void retry(InvocationRecord record) {
        Integer retryCount = record.getRetryCount();
        try {
            Object bean = this.applicationContext.getBean(record.getBeanName());
            Class<?> beanClass = bean.getClass();
            String paramsJson = record.getParams();
            Object[] args;
            Method method;
            if (paramsJson == null || paramsJson.isEmpty()) {
                args = new Object[0];
                method = beanClass.getMethod(record.getMethodName());
            } else {
                List<Object> paramList = this.parseParams(paramsJson);
                args = paramList.toArray(new Object[0]);
                Class<?>[] paramTypes = new Class<?>[args.length];
                for (int i = 0; i < args.length; i++) {
                    paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
                }
                method = this.findMethod(beanClass, record.getMethodName(), paramTypes);
            }
            method.invoke(bean, args);
            this.recordDao.updateStatus(record.getId(), InvocationStatusEnum.SUCCESS.getCode(), null, record.getScene());
        } catch (Exception e) {
            int newRetryCount = (retryCount != null ? retryCount : 0) + 1;
            Integer maxRetryCount = record.getMaxRetryCount();
            int maxCount = maxRetryCount != null ? maxRetryCount : 0;
            Integer retryDelay = record.getRetryDelay();
            LocalDateTime nextExecuteTime = LocalDateTime.now().plusNanos((long) (retryDelay != null ? retryDelay : 0) * 1000000L);
            this.recordDao.updateStatus(record.getId(),
                    newRetryCount >= maxCount ? InvocationStatusEnum.FAILED.getCode() : InvocationStatusEnum.PENDING.getCode(),
                    nextExecuteTime, record.getScene());
            throw new ExecutionException("Retry failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解析参数JSON为对象列表
     */
    private List<Object> parseParams(String paramsJson) throws Exception {
        paramsJson = paramsJson.trim();
        if (paramsJson.startsWith("[")) {
            JavaType listType = this.objectMapper.getTypeFactory().constructCollectionType(List.class, Object.class);
            return this.objectMapper.readValue(paramsJson, listType);
        }
        List<Object> result = new ArrayList<Object>();
        result.add(this.objectMapper.readValue(paramsJson, Object.class));
        return result;
    }

    /**
     * 在类及其父类/接口中查找匹配的方法
     */
    private Method findMethod(Class<?> beanClass, String methodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        try {
            return beanClass.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            for (Method m : beanClass.getMethods()) {
                if (!m.getName().equals(methodName) || m.getParameterTypes().length != paramTypes.length) {
                    continue;
                }
                Class<?>[] types = m.getParameterTypes();
                boolean match = true;
                for (int i = 0; i < types.length; i++) {
                    if (!types[i].isAssignableFrom(paramTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return m;
                }
            }
            throw e;
        }
    }
}
