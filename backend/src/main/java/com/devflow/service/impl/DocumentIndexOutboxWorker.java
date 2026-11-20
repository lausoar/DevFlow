package com.devflow.service.impl;

import com.devflow.entity.DocumentIndexOutbox;
import com.devflow.mapper.DocumentIndexOutboxMapper;
import com.devflow.service.DocumentIndexOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Scheduled delivery and reconciliation for the document index outbox. */
@Slf4j
@Component
public class DocumentIndexOutboxWorker {

    private final DocumentIndexOutboxMapper outboxMapper;
    private final DocumentIndexOutboxService outboxService;
    private final int batchSize;

    public DocumentIndexOutboxWorker(
            DocumentIndexOutboxMapper outboxMapper,
            DocumentIndexOutboxService outboxService,
            @Value("${ai.knowledge.outbox.batch-size:100}") int batchSize) {
        this.outboxMapper = outboxMapper;
        this.outboxService = outboxService;
        this.batchSize = Math.min(500, Math.max(1, batchSize));
    }

    @Scheduled(
            fixedDelayString = "${ai.knowledge.outbox.fixed-delay-ms:30000}",
            initialDelayString = "${ai.knowledge.outbox.initial-delay-ms:10000}")
    public void processDue() {
        List<DocumentIndexOutbox> due = outboxMapper.selectDue(LocalDateTime.now(), batchSize);
        for (DocumentIndexOutbox outbox : due) {
            try {
                outboxService.process(outbox);
            } catch (Exception e) {
                // A persistence failure must not starve the remaining records in this batch.
                log.error("文档索引发件箱处理异常: id={}, documentId={}, 原因={}",
                        outbox.getId(), outbox.getDocumentId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "${ai.knowledge.outbox.reconcile-cron:0 0 3 * * *}")
    public void reconcile() {
        try {
            outboxService.reconcile();
        } catch (Exception e) {
            log.error("文档索引每日对账失败，下一周期将继续重试: {}", e.getMessage());
        }
    }
}
