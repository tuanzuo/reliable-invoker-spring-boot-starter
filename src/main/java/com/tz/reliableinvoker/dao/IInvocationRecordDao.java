package com.tz.reliableinvoker.dao;

import com.tz.reliableinvoker.model.BackupQueryRequest;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.RetryQueryRequest;

import java.util.List;

/**
 * 调用记录数据访问接口
 * <p>
 * 定义对调用记录表的 CRUD 及查询操作。
 * 实现层可自由选择存储介质（关系型数据库、NoSQL 等）。
 * </p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public interface IInvocationRecordDao {

    /**
     * 保存一条调用记录
     *
     * @param record 待保存的调用记录
     * @return 保存成功后的记录（包含自动生成的主键 ID）
     */
    InvocationRecord save(InvocationRecord record);

    /**
     * 更新调用记录的状态
     *
     * @param id    记录主键
     * @param status 新状态码
     * @param scene 业务场景标识
     */
    void updateStatus(Long id, Integer status, String scene);

    /**
     * 根据主键删除记录
     *
     * @param id    记录主键
     * @param scene 业务场景标识
     */
    void deleteById(Long id, String scene);

    /**
     * 根据序列号查询调用记录
     *
     * @param serialNo 调用序列号
     * @param scene    业务场景标识
     * @return 匹配的调用记录，未找到时返回 {@code null}
     */
    InvocationRecord findBySerialNo(String serialNo, String scene);

    /**
     * 查询待重试的调用记录
     *
     * @param request 重试查询条件
     * @return 符合重试条件的记录列表
     */
    List<InvocationRecord> findForRetry(RetryQueryRequest request);

    /**
     * 查询待备份的调用记录
     *
     * @param request 备份查询条件
     * @return 符合备份条件的记录列表
     */
    List<InvocationRecord> findForBackup(BackupQueryRequest request);
}
