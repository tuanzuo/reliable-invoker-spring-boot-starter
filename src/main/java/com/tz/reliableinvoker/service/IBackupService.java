package com.tz.reliableinvoker.service;

import com.tz.reliableinvoker.model.InvocationRecord;

import java.util.List;

/**
 * 备份服务接口
 * <p>
 * 对已完成或超过重试上限的调用记录进行归档备份。
 * 备份完成后，原记录可从主表中安全删除。
 * </p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public interface IBackupService {

    /**
     * 批量备份调用记录
     *
     * @param records 待备份的调用记录列表
     */
    void backup(List<InvocationRecord> records);
}
