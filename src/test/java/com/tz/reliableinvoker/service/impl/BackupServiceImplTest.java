package com.tz.reliableinvoker.service.impl;

import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.model.InvocationRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * BackupServiceImpl 单元测试
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class BackupServiceImplTest {

    @Test
    void testBackupDeletesRecords() {
        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        BackupServiceImpl backupService = new BackupServiceImpl(recordDao);

        InvocationRecord r1 = new InvocationRecord();
        r1.setId(1L);
        r1.setScene("ORDER");

        InvocationRecord r2 = new InvocationRecord();
        r2.setId(2L);
        r2.setScene("ORDER");

        backupService.backup(Arrays.asList(r1, r2));

        verify(recordDao).deleteById(1L, "ORDER");
        verify(recordDao).deleteById(2L, "ORDER");
    }

    @Test
    void testBackupEmptyListDoesNothing() {
        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        BackupServiceImpl backupService = new BackupServiceImpl(recordDao);

        backupService.backup(Collections.emptyList());

        verifyNoInteractions(recordDao);
    }

    @Test
    void testBackupDifferentScenes() {
        IInvocationRecordDao recordDao = mock(IInvocationRecordDao.class);
        BackupServiceImpl backupService = new BackupServiceImpl(recordDao);

        InvocationRecord r1 = new InvocationRecord();
        r1.setId(1L);
        r1.setScene("ORDER");

        InvocationRecord r2 = new InvocationRecord();
        r2.setId(2L);
        r2.setScene("PAY");

        backupService.backup(Arrays.asList(r1, r2));

        verify(recordDao).deleteById(1L, "ORDER");
        verify(recordDao).deleteById(2L, "PAY");
    }
}
