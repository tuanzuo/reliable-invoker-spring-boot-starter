package com.tz.reliableinvoker.api.impl;

import com.tz.reliableinvoker.api.IReliableInvokerTask;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.model.BackupQueryRequest;
import com.tz.reliableinvoker.model.BackupRequest;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.InvocationStatusEnum;
import com.tz.reliableinvoker.model.RetryQueryRequest;
import com.tz.reliableinvoker.model.RetryRequest;
import com.tz.reliableinvoker.service.IBackupService;
import com.tz.reliableinvoker.service.IRetryService;

import java.util.List;

/**
 * 任务管理默认实现（重试/备份）
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 02:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class ReliableInvokerTaskImpl implements IReliableInvokerTask {

    private final IInvocationRecordDao recordDao;
    private final IRetryService retryService;
    private final IBackupService backupService;

    public ReliableInvokerTaskImpl(IInvocationRecordDao recordDao, IRetryService retryService,
                                   IBackupService backupService) {
        this.recordDao = recordDao;
        this.retryService = retryService;
        this.backupService = backupService;
    }

    @Override
    public int retry(RetryRequest<?> request) {
        String scene = request.getScene().name();

        RetryQueryRequest query = new RetryQueryRequest();
        query.setScene(scene);
        query.setStatusList(request.getStatusList());
        query.setShardTotal(request.getShardTotal());
        query.setShardIndex(request.getShardIndex());
        query.setLimit(request.getLimit());

        List<InvocationRecord> records = this.recordDao.findForRetry(query);

        for (InvocationRecord record : records) {
            int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
            int maxRetryCount = record.getMaxRetryCount() != null ? record.getMaxRetryCount() : 0;
            if (retryCount >= maxRetryCount) {
                this.recordDao.updateStatus(record.getId(), InvocationStatusEnum.FAILED.getCode(), record.getScene());
            } else {
                this.retryService.retry(record);
            }
        }

        return records.size();
    }

    @Override
    public int backup(BackupRequest<?> request) {
        String scene = request.getScene().name();

        BackupQueryRequest query = new BackupQueryRequest();
        query.setScene(scene);
        query.setStatusList(request.getStatusList());
        query.setDays(request.getDays());
        query.setShardTotal(request.getShardTotal());
        query.setShardIndex(request.getShardIndex());
        query.setLimit(request.getLimit());

        List<InvocationRecord> records = this.recordDao.findForBackup(query);

        if (!records.isEmpty()) {
            this.backupService.backup(records);
        }

        return records.size();
    }
}
