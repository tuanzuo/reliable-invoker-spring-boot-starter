package com.tz.reliableinvoker.api;

import com.tz.reliableinvoker.model.BackupRequest;
import com.tz.reliableinvoker.model.RetryRequest;

/**
 * 任务管理接口（重试/备份）
 * <p>
 * 对已持久化的调用记录进行二次处理，包括失败重试和数据备份。
 * 通常由定时任务或消息监听器触发，不直接面向业务调用方。
 * </p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public interface IReliableInvokerTask {

    /**
     * 对指定请求执行重试
     * <p>
     * 根据{@link RetryRequest}中携带的序列号定位持久化记录，
     * 重新发起方法调用，并更新执行状态。
     * </p>
     *
     * @param request 重试请求，必须包含有效的序列号
     * @return 受影响的行数（通常为 1）
     */
    int retry(RetryRequest<?> request);

    /**
     * 对指定请求执行备份
     * <p>
     * 将{@link BackupRequest}中指定的记录数据写入备份存储，
     * 备份成功后原记录可被安全清理。
     * </p>
     *
     * @param request 备份请求，包含待备份的调用记录标识
     * @return 受影响的行数
     */
    int backup(BackupRequest<?> request);
}
