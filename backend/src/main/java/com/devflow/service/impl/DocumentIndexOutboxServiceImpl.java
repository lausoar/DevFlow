package com.devflow.service.impl;

import com.devflow.entity.Document;
import com.devflow.entity.DocumentIndexOutbox;
import com.devflow.mapper.DocumentIndexOutboxMapper;
import com.devflow.service.AiKnowledgeSyncService;
import com.devflow.service.DocumentIndexOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
public class DocumentIndexOutboxServiceImpl implements DocumentIndexOutboxService {

    private static final long MAX_BACKOFF_SECONDS = 3600;
    private static final LocalDateTime FALLBACK_DOCUMENT_VERSION_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final DocumentIndexOutboxMapper outboxMapper;
    private final AiKnowledgeSyncService aiKnowledgeSyncService;
    private final int maxAttempts;
    private final long baseBackoffSeconds;

    public DocumentIndexOutboxServiceImpl(
            DocumentIndexOutboxMapper outboxMapper,
            AiKnowledgeSyncService aiKnowledgeSyncService,
            @Value("${ai.knowledge.outbox.max-attempts:6}") int maxAttempts,
            @Value("${ai.knowledge.outbox.base-backoff-seconds:30}") long baseBackoffSeconds) {
        this.outboxMapper = outboxMapper;
        this.aiKnowledgeSyncService = aiKnowledgeSyncService;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffSeconds = Math.min(MAX_BACKOFF_SECONDS, Math.max(1, baseBackoffSeconds));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public DocumentIndexOutbox enqueue(Document document) {
        DocumentIndexOutbox outbox = newOutbox(document, false);
        if (outbox == null) {
            return null;
        }
        return outboxMapper.insertIfAbsent(outbox) > 0 ? outbox : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public DocumentIndexOutbox enqueueDeletion(Document document) {
        if (document == null || !StringUtils.hasText(document.getId())) {
            return null;
        }
        Document persisted = outboxMapper.selectDocumentIncludingDeleted(document.getId());
        DocumentIndexOutbox outbox = newOutbox(persisted != null ? persisted : document, true);
        return outboxMapper.insertIfAbsent(outbox) > 0 ? outbox : null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void process(DocumentIndexOutbox outbox) {
        if (outbox == null || !StringUtils.hasText(outbox.getId())
                || DONE.equals(outbox.getStatus()) || FAILED.equals(outbox.getStatus())) {
            return;
        }

        int attempts = (outbox.getAttempts() == null ? 0 : outbox.getAttempts()) + 1;
        outbox.setAttempts(attempts);
        outbox.setUpdatedAt(now());
        try {
            execute(outbox);
            outbox.setStatus(DONE);
            outbox.setNextAttemptAt(null);
            outbox.setLastError(null);
            outboxMapper.updateState(outbox);
            log.info("文档索引同步成功: documentId={}, operation={}, attempts={}",
                    outbox.getDocumentId(), outbox.getOperation(), attempts);
        } catch (Exception e) {
            outbox.setLastError(errorMessage(e));
            if (attempts >= maxAttempts) {
                outbox.setStatus(FAILED);
                outbox.setNextAttemptAt(null);
                log.error("文档索引同步达到最大重试次数: documentId={}, attempts={}, 原因={}",
                        outbox.getDocumentId(), attempts, outbox.getLastError());
            } else {
                long backoff = backoffSeconds(attempts);
                outbox.setStatus(RETRY);
                outbox.setNextAttemptAt(now().plusSeconds(backoff));
                log.warn("文档索引同步失败，等待重试: documentId={}, attempts={}, delaySeconds={}, 原因={}",
                        outbox.getDocumentId(), attempts, backoff, outbox.getLastError());
            }
            outboxMapper.updateState(outbox);
        }
    }

    @Override
    public void reconcile() {
        for (Document document : outboxMapper.selectAllDocumentsIncludingDeleted()) {
            DocumentIndexOutbox outbox = newOutbox(document,
                    document != null && document.getDeleted() != null && document.getDeleted() == 1);
            if (outbox == null) {
                continue;
            }
            if (outboxMapper.insertIfAbsent(outbox) == 0) {
                // PENDING/RETRY/DONE stay untouched; only a FAILED version is made due again.
                outboxMapper.requeueFailed(outbox.getDocumentId(), outbox.getOperation(),
                        outbox.getDocumentUpdatedAt(), outbox.getNextAttemptAt(), outbox.getUpdatedAt());
            }
        }
    }

    private void execute(DocumentIndexOutbox outbox) {
        if (DELETE.equals(outbox.getOperation())) {
            aiKnowledgeSyncService.deleteDocumentSync(
                    outbox.getDocumentId(), outbox.getProjectId(), outbox.getEnterpriseId());
            return;
        }
        if (!UPSERT.equals(outbox.getOperation())) {
            throw new IllegalArgumentException("Unknown document index operation: " + outbox.getOperation());
        }
        aiKnowledgeSyncService.indexDocumentSync(toDocument(outbox));
    }

    private Document toDocument(DocumentIndexOutbox outbox) {
        Document document = new Document();
        document.setId(outbox.getDocumentId());
        document.setProjectId(outbox.getProjectId());
        document.setEnterpriseId(outbox.getEnterpriseId());
        document.setTitle(outbox.getTitle());
        document.setContent(outbox.getContent());
        document.setType(outbox.getDocType());
        document.setVersion(outbox.getDocumentVersion());
        document.setUpdatedAt(outbox.getDocumentUpdatedAt());
        return document;
    }

    private DocumentIndexOutbox newOutbox(Document document, boolean forceDelete) {
        if (document == null || !StringUtils.hasText(document.getId())) {
            return null;
        }
        LocalDateTime now = now();
        LocalDateTime documentUpdatedAt;
        if (document.getUpdatedAt() != null) {
            documentUpdatedAt = document.getUpdatedAt().truncatedTo(ChronoUnit.MILLIS);
        } else if (document.getCreatedAt() != null) {
            documentUpdatedAt = document.getCreatedAt().truncatedTo(ChronoUnit.MILLIS);
        } else {
            // Legacy null timestamps must not produce fresh duplicate work every day.
            documentUpdatedAt = FALLBACK_DOCUMENT_VERSION_TIME;
        }

        DocumentIndexOutbox outbox = new DocumentIndexOutbox();
        outbox.setId(UUID.randomUUID().toString().replace("-", ""));
        outbox.setDocumentId(document.getId());
        outbox.setProjectId(document.getProjectId());
        outbox.setEnterpriseId(document.getEnterpriseId());
        outbox.setOperation(forceDelete || isDeleted(document) || !StringUtils.hasText(document.getContent())
                ? DELETE : UPSERT);
        outbox.setTitle(document.getTitle());
        outbox.setContent(document.getContent());
        outbox.setDocType(StringUtils.hasText(document.getType()) ? document.getType() : "DOC");
        outbox.setDocumentVersion(document.getVersion() != null ? document.getVersion() : 0);
        outbox.setDocumentUpdatedAt(documentUpdatedAt);
        outbox.setStatus(PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        return outbox;
    }

    private boolean isDeleted(Document document) {
        return document.getDeleted() != null && document.getDeleted() == 1;
    }

    private long backoffSeconds(int attempts) {
        int shift = Math.min(Math.max(0, attempts - 1), 20);
        return Math.min(MAX_BACKOFF_SECONDS, baseBackoffSeconds * (1L << shift));
    }

    private String errorMessage(Exception e) {
        String message = e.getClass().getSimpleName()
                + (StringUtils.hasText(e.getMessage()) ? ": " + e.getMessage() : "");
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
