package com.devflow.service.impl;

import com.devflow.entity.DocumentIndexOutbox;
import com.devflow.mapper.DocumentIndexOutboxMapper;
import com.devflow.service.DocumentIndexOutboxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIndexOutboxWorkerTest {

    @Mock private DocumentIndexOutboxMapper outboxMapper;
    @Mock private DocumentIndexOutboxService outboxService;

    @Test
    void workerProcessesEveryDueRecordEvenWhenOnePersistenceUpdateFails() {
        DocumentIndexOutbox first = new DocumentIndexOutbox();
        first.setId("o1");
        DocumentIndexOutbox second = new DocumentIndexOutbox();
        second.setId("o2");
        when(outboxMapper.selectDue(any(LocalDateTime.class), eq(100))).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("database restart")).when(outboxService).process(first);
        DocumentIndexOutboxWorker worker = new DocumentIndexOutboxWorker(outboxMapper, outboxService, 100);

        worker.processDue();

        verify(outboxService).process(first);
        verify(outboxService).process(second);
    }

    @Test
    void scheduledEntryPointsCoverDeliveryAndDailyReconciliation() throws Exception {
        DocumentIndexOutboxWorker worker = new DocumentIndexOutboxWorker(outboxMapper, outboxService, 100);

        worker.reconcile();

        verify(outboxService).reconcile();
        assertThat(DocumentIndexOutboxWorker.class.getMethod("processDue").getAnnotation(Scheduled.class))
                .isNotNull();
        assertThat(DocumentIndexOutboxWorker.class.getMethod("reconcile").getAnnotation(Scheduled.class))
                .isNotNull();
    }
}
