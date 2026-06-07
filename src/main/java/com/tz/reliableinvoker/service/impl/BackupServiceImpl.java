package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.service.IBackupService;

import java.util.List;

/**
 * 默认备份服务实现
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 02:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class BackupServiceImpl implements IBackupService {

    /** 调用记录数据访问对象 */
    private final IInvocationRecordDao recordDao;

    public BackupServiceImpl(IInvocationRecordDao recordDao) {
        this.recordDao = recordDao;
    }

    @Override
    public void backup(List<InvocationRecord> records) {
        for (InvocationRecord r : records) {
            this.recordDao.deleteById(r.getId(), r.getScene());
        }
    }
}
